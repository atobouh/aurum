# Aurum

A Fabric 1.20.1 client mod with its own rendering pipeline on top of Sodium 0.5.13: golden-hour sky, warm
light, shadows, atmospheric haze, clouds and reflective water. Its performance target is 60 FPS at 1080p on an
Intel UHD 620; the current cloud environment cannot verify that target.

Current build: **0.5.9**. It includes sky and terrain lighting, generated relief, shadows, fog and shafts,
reflective/refractive water, clouds, bloom and lens flare. Photon and Complementary remain the visual references;
the matched captures still show a quality gap.

- [Download Aurum 0.5.9](https://raw.githubusercontent.com/atobouh/aurum/codex/dist/aurum-0.5.9.jar)
- [Visual report with matched reference captures](docs/reports/aurum-0.5.9-matched-reference-review.html)
- [Direct source review and testing procedure](docs/research/LIGHTING_SOURCE_REVIEW_059.md)

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
