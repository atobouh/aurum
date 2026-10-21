# Aurum

A Fabric 1.20.1 client mod with its own rendering pipeline on top of Sodium 0.5.13: golden-hour sky, warm
light, long shadows, soft haze and a sun flare, built to hold 60 FPS at 1080p on an Intel UHD 620.

Status: **Phase 0 (Foundation)**. Settings screen, GPU cost overlay, version and conflict checks, shader
loader. No visual changes yet. The full plan is `docs/AURUM_SPEC.md`.

## Build

Needs **JDK 21** (the mod itself runs on the game's Java 17).

```powershell
./gradlew build
```

The jar lands in `build/libs/`.

## Requirements

- Minecraft 1.20.1, Fabric Loader 0.15+, Fabric API
- Sodium 0.5.x (terrain effects target 0.5.13 exactly)
- Not compatible with Iris, Oculus, Luxium or other shader/render-pipeline mods
- Recommended companion: [Custom Time Cycle](https://modrinth.com/mod/customtimecycle) for longer days

## Keys

- **F7** open settings
- **F8** toggle the GPU cost overlay

## Docs

- `docs/AURUM_SPEC.md` the build spec (binding)
- `docs/TESTING.md` what to test for the current phase and which numbers to send back
- `docs/SODIUM_NOTES.md` how Sodium's terrain shaders work and where Aurum hooks in
- `docs/DECISIONS.md` where the build differs from the spec, and why
