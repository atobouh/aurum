# Aurum

A Fabric 1.20.1 client mod with its own rendering pipeline on top of Sodium 0.5.13: golden-hour sky, warm
light, shadows, atmospheric haze, clouds and reflective water. Its performance target is 60 FPS at 1080p on an
Intel UHD 620; the current cloud environment cannot verify that target.

Experimental lighting build: **0.6.2-alpha.1**. Terrain and ten supported model shader variants share a surface-light evaluator. A bounded static visibility field supports up to 32 sources with exact receiver falloff, plus a neutral local bounce estimate. When LambDynamicLights is installed, static voxel replacement is paused for a consistent propagated-light compatibility path. This alpha does not complete dynamic voxel lighting, cave fog/AO or the full lighting plan.

- [Download Aurum 0.6.2-alpha.1](https://raw.githubusercontent.com/atobouh/aurum/codex/dist/aurum-0.6.2-alpha.1.jar)
- [Download the illustrated lighting report (ZIP)](https://raw.githubusercontent.com/atobouh/aurum/codex/dist/aurum-0.6.2-alpha.1-lighting-report.zip) — extract all, then open `Aurum-lighting-report/index.html`; all images and videos work offline.
- [Single-file HTML report source](docs/reports/aurum-0.6.2-alpha.1-lighting-review.html) — GitHub shows this as raw text; download it before opening in a browser.
- [Implementation and remaining gates](docs/research/LIGHTING_IMPLEMENTATION_062_ALPHA1.md)
- [Excalibur-inspired depth research (HTML report ZIP)](https://raw.githubusercontent.com/atobouh/aurum/codex/dist/aurum-excalibur-relief-research.zip) — LabPBR-off baseline tests and engineering proposal; no new renderer implementation.

Stable build: **0.6.1**. It corrects excessive daytime canopy darkening and adds diffuse fill beneath cloud shadows,
following the user's High / LabPBR-off footage. The 0.6.0 features include cached, occluded coloured block light, cloud shadows and varying wind, a selectable calm-lake water
treatment, bounded sharpening, canopy depth and a Voxel Crisp shadow/ray option. Existing sky, relief,
reflections, bloom and flare remain. Photon, Complementary, Rethinking Voxels and Derivative Main are the visual references;
the matched captures still show a quality gap. Derivative captures use a clearly labelled community fork.
The user reports stable 60 FPS on Intel UHD, High, 12 chunks and roughly 200 mods; this new build still needs
measurement on that machine.

- [Download Aurum 0.6.1](https://raw.githubusercontent.com/atobouh/aurum/codex/dist/aurum-0.6.1.jar)
- [Daylight correction: evidence and compatibility tests](docs/reports/aurum-0.6.1-daylight-review.html)
- [Natural scenery with the regasm datapack stack](docs/reports/aurum-regasm-natural-scenery-review.html)
- [Visual report with matched reference captures](docs/reports/aurum-0.6.0-visual-review.html)
- [Direct source review and testing procedure](docs/research/LOCAL_LIGHT_CLOUD_REVIEW_060.md)

## Build

Needs **JDK 21** (the mod itself runs on the game's Java 17).

```powershell
./gradlew build
```

The jar lands in `build/libs/`.

Check sky integration and voxel visibility independently of Minecraft:

```bash
JAVA_HOME=/path/to/jdk21 python3 tools/check_sky_model.py
JAVA_HOME=/path/to/jdk21 python3 tools/check_voxel_lighting.py
```

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

Lighting work: [source research and diagnostic review](docs/reports/aurum-lighting-unification-research.html) — investigation and development tooling; the lighting clash is not fixed yet.
