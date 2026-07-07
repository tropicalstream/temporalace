package com.tropicalstream.temporalace.net

import android.content.Context
import com.tropicalstream.temporalace.game.BossDefs
import com.tropicalstream.temporalace.game.Eras
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Companion dashboard on the glasses. The org opens http://<ip>:8080 on a browser,
 * picks a level (1-30) and asset kind (music/voice/art), and drops files in — copied
 * as raw bytes with original extension (encapsulation preserved). It also stores the
 * Fish TTS api key + per-level model id, and serves a generation PROMPT pack per level
 * (music brief, boss portrait prompt, full taunt script) built from the game's era +
 * boss data, so the org's tools can generate matching assets.
 */
class CompanionServer(port: Int, private val ctx: Context) : NanoHTTPD(port) {

    private val base = File(ctx.getExternalFilesDir(null), "Levels")
    private val cfg = ctx.getSharedPreferences("temporalace_fish", Context.MODE_PRIVATE)
    private val audioExts = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
    private val imgExts = setOf("png", "jpg", "jpeg", "webp")

    override fun serve(session: IHTTPSession): Response = try {
        when {
            session.method == Method.POST && session.uri == "/upload" -> upload(session)
            session.method == Method.POST && session.uri == "/config" -> config(session)
            session.uri == "/config" -> json(configJson())
            session.uri == "/levels" -> json(levelsJson())
            session.uri == "/prompts" -> json(promptsJson((session.parameters["level"]?.firstOrNull() ?: "1").toIntOrNull() ?: 1))
            session.uri == "/" -> newFixedLengthResponse(Response.Status.OK, "text/html", PAGE)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    } catch (t: Throwable) {
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error: ${t.message}")
    }

    private fun dir(level: Int, kind: String): File =
        File(base, "%02d/%s".format(level.coerceIn(1, Eras.TOTAL), kind)).apply { mkdirs() }

    private fun upload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val level = (session.parameters["level"]?.firstOrNull() ?: "1").toIntOrNull() ?: 1
        val kind = (session.parameters["kind"]?.firstOrNull() ?: "music").let { if (it in setOf("music", "voice", "art")) it else "music" }
        var saved = 0
        for ((field, tmp) in files) {
            val name = session.parameters[field]?.firstOrNull() ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (kind == "art" && ext !in imgExts) continue
            if (kind != "art" && ext !in audioExts) continue
            File(tmp).copyTo(File(dir(level, kind), sanitize(name)), overwrite = true)
            saved++
        }
        return json("""{"ok":true,"saved":$saved}""")
    }

    private fun config(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        session.parseBody(body)
        session.parameters["apiKey"]?.firstOrNull()?.let { cfg.edit().putString("apiKey", it).apply() }
        val level = session.parameters["level"]?.firstOrNull()?.toIntOrNull()
        val model = session.parameters["modelId"]?.firstOrNull()
        if (level != null && model != null) cfg.edit().putString("model_$level", model).apply()
        return json("""{"ok":true}""")
    }

    private fun configJson(): String =
        JSONObject().put("apiKey", cfg.getString("apiKey", "") ?: "").toString()

    private fun levelsJson(): String {
        val arr = JSONArray()
        for (lvl in 1..Eras.TOTAL) {
            arr.put(JSONObject()
                .put("level", lvl).put("era", Eras.forLevel(lvl).name).put("boss", Eras.isBoss(lvl))
                .put("music", dir(lvl, "music").listFiles()?.isNotEmpty() == true)
                .put("voice", dir(lvl, "voice").listFiles()?.size ?: 0)
                .put("art", dir(lvl, "art").listFiles()?.isNotEmpty() == true)
                .put("modelId", cfg.getString("model_$lvl", "") ?: ""))
        }
        return arr.toString()
    }

    private fun promptsJson(level: Int): String {
        val era = Eras.forLevel(level)
        val bpm = 90 + Eras.eraIndex(level) * 12 + (if (Eras.isBoss(level)) 20 else 0)
        val o = JSONObject()
        o.put("level", level).put("era", era.name)
        o.put("musicBrief", "${era.name}, ${bpm} bpm, seamless 90s loop, ${moodFor(level)}; instrument palette fitting ${era.silhouette}")
        if (Eras.isBoss(level)) {
            val b = BossDefs.forLevel(level)
            o.put("bossName", b.name)
            o.put("portraitPrompt", "Boss portrait: ${b.name}, ${era.name} aviation, ${b.gimmick} gimmick, dominant hue ${b.hue.toInt()}°, neon-on-black, menacing, cinematic")
            val lines = JSONArray()
            lines.put(JSONObject().put("cue", "intro").put("text", b.intro))
            b.phases.forEachIndexed { i, p -> lines.put(JSONObject().put("cue", "phase${i + 1}").put("text", p.taunt)) }
            lines.put(JSONObject().put("cue", "defeat").put("text", b.defeat))
            o.put("taunts", lines)
            o.put("voiceNote", "Generate each line to mp3 with the level's Fish model id; upload to voice/. Personality: theatrical, era-appropriate menace.")
        }
        return o.toString()
    }

    private fun moodFor(level: Int): String = listOf(
        "grim propeller march", "tense naval swing", "driving jet-age funk",
        "dark ambient stealth pulse", "glitchy swarm techno", "euphoric neon synthwave"
    )[Eras.eraIndex(level)]

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._ ()\\-]"), "_").take(120).ifBlank { "asset" }

    private fun json(s: String): Response = newFixedLengthResponse(Response.Status.OK, "application/json", s)

    companion object {
        const val PORT = 8080
        fun deviceIp(): String? = try {
            var r: String? = null
            for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp || iface.isLoopback) continue
                for (a in Collections.list(iface.inetAddresses)) if (a is Inet4Address && a.isSiteLocalAddress) r = a.hostAddress
            }
            r
        } catch (t: Throwable) { null }

        private val PAGE = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Temporal Ace - Asset Studio</title>
<style>
 :root{--bg:#0b0d12;--card:#141821;--line:#232a36;--fg:#e7ecf3;--dim:#8b96a6;--acc:#22d3ee;--acc2:#a78bfa}
 *{box-sizing:border-box} body{margin:0;background:var(--bg);color:var(--fg);font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif}
 .wrap{max-width:720px;margin:0 auto;padding:24px 16px 60px}
 h1{font-size:22px;margin:0} h1 span{color:var(--acc)} .sub{color:var(--dim);font-size:13px;margin:4px 0 20px}
 .row{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-bottom:14px}
 select,input,button{background:var(--card);color:var(--fg);border:1px solid var(--line);border-radius:10px;padding:10px 12px;font-size:14px}
 button{cursor:pointer} button.acc{border-color:var(--acc);color:var(--acc)}
 .drop{border:2px dashed var(--line);border-radius:14px;padding:26px;text-align:center;background:var(--card);cursor:pointer}
 .drop.hot{border-color:var(--acc);background:#101722}
 .bar{height:4px;background:var(--line);border-radius:4px;overflow:hidden;margin-top:8px} .bar>div{height:100%;width:0;background:linear-gradient(90deg,var(--acc),var(--acc2))}
 pre{white-space:pre-wrap;background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;font-size:12px;color:#cbd5e1}
 .card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;margin-bottom:12px}
 .foot{color:var(--dim);font-size:12px;text-align:center;margin-top:26px}
 label{color:var(--dim);font-size:12px}
</style></head><body><div class="wrap">
 <h1>Temporal <span>Ace</span> - Asset Studio</h1>
 <div class="sub">Add per-level music, boss voice lines, and art. Files land on the glasses instantly.</div>
 <div class="row">
   <label>Level</label><select id="level"></select>
   <label>Kind</label><select id="kind"><option>music</option><option>voice</option><option>art</option></select>
   <button class="acc" onclick="showPrompts()">Show generation prompts</button>
 </div>
 <div id="drop" class="drop">Drop files here, or tap to choose<div id="prog"></div></div>
 <input id="file" type="file" multiple style="display:none">
 <pre id="prompts" style="display:none"></pre>
 <div class="card">
   <div class="row"><label>Fish API key</label><input id="apiKey" size="30" placeholder="fish api key"><button onclick="saveKey()">Save</button></div>
   <div class="row"><label>Fish model id (this level)</label><input id="modelId" size="24" placeholder="model id"><button onclick="saveModel()">Save</button></div>
 </div>
 <div id="status" class="card">loading...</div>
 <div class="foot">Temporal Ace companion - keep this tab open while uploading</div>
</div>
<script>
 var lv=document.getElementById('level'), kd=document.getElementById('kind'), drop=document.getElementById('drop'),
     input=document.getElementById('file'), prog=document.getElementById('prog'), st=document.getElementById('status');
 for(var i=1;i<=30;i++){ var o=document.createElement('option'); o.value=i; o.text='Level '+i; lv.appendChild(o); }
 drop.onclick=function(){ input.click(); };
 input.onchange=function(){ up(Array.prototype.slice.call(input.files)); input.value=''; };
 ;['dragenter','dragover'].forEach(function(e){ drop.addEventListener(e,function(ev){ev.preventDefault();drop.classList.add('hot');}); });
 ;['dragleave','drop'].forEach(function(e){ drop.addEventListener(e,function(ev){ev.preventDefault();drop.classList.remove('hot');}); });
 drop.addEventListener('drop',function(ev){ if(ev.dataTransfer&&ev.dataTransfer.files) up(Array.prototype.slice.call(ev.dataTransfer.files)); });
 function up(arr){ next(arr,0); }
 function next(arr,i){ if(i>=arr.length){ prog.innerHTML=''; load(); return; }
   var f=arr[i]; prog.innerHTML='<div style="margin-top:12px;font-size:12px">Uploading '+esc(f.name)+'</div><div class="bar"><div id="pb"></div></div>';
   var pb=document.getElementById('pb'); var fd=new FormData(); fd.append('file',f,f.name);
   var x=new XMLHttpRequest(); x.open('POST','/upload?level='+lv.value+'&kind='+kd.value);
   x.upload.onprogress=function(e){ if(e.lengthComputable) pb.style.width=Math.round(e.loaded/e.total*100)+'%'; };
   x.onload=function(){ next(arr,i+1); }; x.onerror=function(){ next(arr,i+1); }; x.send(fd); }
 function saveKey(){ var fd=new FormData(); fd.append('apiKey',document.getElementById('apiKey').value); fetch('/config',{method:'POST',body:fd}).then(load); }
 function saveModel(){ var fd=new FormData(); fd.append('level',lv.value); fd.append('modelId',document.getElementById('modelId').value); fetch('/config',{method:'POST',body:fd}).then(load); }
 function showPrompts(){ fetch('/prompts?level='+lv.value).then(function(r){return r.json();}).then(function(p){
   var el=document.getElementById('prompts'); el.style.display='block';
   var s='LEVEL '+p.level+' - '+p.era+'\n\nMUSIC BRIEF:\n'+p.musicBrief+'\n';
   if(p.taunts){ s+='\nBOSS: '+p.bossName+'\nPORTRAIT:\n'+p.portraitPrompt+'\n\nTAUNTS (generate each to mp3):\n';
     p.taunts.forEach(function(t){ s+='  ['+t.cue+'] '+t.text+'\n'; }); s+='\n'+p.voiceNote+'\n'; }
   el.textContent=s; }); }
 function load(){ fetch('/levels').then(function(r){return r.json();}).then(function(a){
   var l=a[parseInt(lv.value)-1]; if(!l) return;
   st.innerHTML='<b>Level '+l.level+'</b> - '+l.era+(l.boss?' (BOSS)':'')+'<br>music: '+(l.music?'yes':'no')+' · voice lines: '+l.voice+' · art: '+(l.art?'yes':'no')+'<br>model id: '+(l.modelId||'(none)'); }); }
 lv.onchange=load; kd.onchange=load; fetch('/config').then(function(r){return r.json();}).then(function(c){ document.getElementById('apiKey').value=c.apiKey||''; }); load();
</script></body></html>
""".trimIndent()
    }
}
