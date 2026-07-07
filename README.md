# Temporal Ace

A multi-era, strategy-first shoot-'em-up for the RayNeo X3 Pro AR glasses. Fly a
neon fighter through **six eras of aviation across 30 levels** — from WWI biplanes
to the far-future neon infinity — hopping through **portals** between eras. Your
ship **autoshoots**; you control only movement (temple pad). Grab a huge, balanced
arsenal of powerups, each with its own glow, and duel a boss with real personality
at the end of every era.

Built on the proven self-contained X3 stack (custom Canvas + dual-draw
`BinocularSbsLayout` + `TrackpadGestureEngine`); the only dependency is NanoHTTPD
for the companion server.

## Controls (right temple pad)

| Action | Gesture |
|---|---|
| **Move ship** | slide finger (continuous, the only control) |
| **Pause / confirm menu** | tap |
| **Bomb / back** | long-press / double-tap |
| **Menu navigate** | swipe ↑↓ (rows), ←→ (adjust settings) |

The left temple pad is left to the system (volume). Head-steer is an option in Settings.

## Highlights

- **30 levels, 6 eras** — WWI · WWII Pacific · Jet Age · Stealth Era · Drone Swarm · Neon Infinity. Historical eras use period palettes/silhouettes; future eras go full neon. Portals jump you between them.
- **~17 powerups, strategy-first** — WEAPON pickups (Spread, Rapid, Piercer, Seekers, Lance Beam, Wave) are mutually exclusive: grabbing one *replaces* your primary, so every drop is a build choice. Layer DEFENSE (shield, armor, phase, repair) and UTILITY (thrusters, option drones, magnet, time-dilate, score x2, overdrive, bombs). Each has a distinct hue → distinct particle signature.
- **Bosses with personality** — one boss per era (levels 5/10/…/30), each a named character with a Fish-TTS voice hook, scripted phase taunts, a phase state machine with composed bullet patterns, and a signature gimmick (WWII launches escort waves; the far-future **Chronos Prime** rewinds its own hull once). Sub-bosses escort on levels 3–4.
- **Frequent autosave** — snapshots every 5 s, on every level start, and **on `onPause`** (the sleep button fires that mid-wear). Accidentally leave? Pick **CONTINUE** on the title.
- **Synthesized SFX** — lasers, hits, explosions, portal shimmer, bombs — all generated in-process, no asset files.
- **Full settings** — steering source, sensitivity, invert-Y, SFX/music volume, subtitles, **reduce-flash** (photosensitivity), 3 colorblind modes, 30/60 fps cap, wipe save.

## Companion Asset Studio (add music, voice, art)

The glasses host a web dashboard. On a phone/computer on the same Wi-Fi, open the
address shown in-app (`http://<glasses-ip>:8080`). Pick a **level (1–30)** and a
**kind** (music / voice / art) and drag files in — copied as raw bytes with the
original extension (encoding preserved). Set your **Fish TTS api key + per-level
model id**, and hit **Show generation prompts** for a ready-made prompt pack per
level (music brief, boss portrait prompt, full taunt script) built from the game's
own era/boss data. Assets land in `Android/data/com.tropicalstream.temporalace(.debug)/files/Levels/NN/`.

## Install (full paths)

Build and sideload from a Mac/Linux shell with the glasses connected over ADB:

```bash
cd /Users/me/Projects/temporalace
./gradlew :app:assembleDebug
adb install -r /Users/me/Projects/temporalace/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tropicalstream.temporalace.debug/com.tropicalstream.temporalace.MainActivity
```

The built APK is at:
`/Users/me/Projects/temporalace/app/build/outputs/apk/debug/app-debug.apk`

To reach the companion studio, note the IP shown on the glasses and open
`http://<that-ip>:8080` in a browser on the same network.

## Notes / on-device tuning
- Difficulty, drop weights, and powerup magnitudes are data (`Eras.kt`, `Powerups.kt`, `Boss.kt`) — tune by feel on-glasses.
- OpenGL portal/boss-death effects are the planned v1.1 (see the handoff's GLTextureView recipe); v1 ships the Canvas neon versions, which read 90% as good.
- Fish-TTS voice playback: the companion generates line mp3s into each level's `voice/`; wiring runtime playback of those (ducking music −6 dB) is the next hook — v1 shows subtitles.
