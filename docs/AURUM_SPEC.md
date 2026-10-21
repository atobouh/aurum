# Aurum — UHD 620 Rendering Mod: Build Spec

Oct 21, 2026 · @Dyes

## Mission

Build **Aurum** (working name, the user may rename it): a Fabric 1.20.1 client mod that replaces Minecraft's look with its own hand-written rendering pipeline. It must be more beautiful than Luxium and as fast or faster on an Intel UHD 620 laptop, holding **60 FPS or more at 1080p**.

Aurum is a mod, not an Iris shader pack. Owning the pipeline lets us cache, skip, merge and lower the resolution of work, which a shader pack cannot do. Every effect must be beautiful **and** cheap. Effects that are cheap by nature stay simple. Engineering effort goes into effects that are heavy by default (shadows, water, clouds, POM).

**How the agent uses this document**

- Read it fully before writing code. Sections 2 (Requirements) and 7 (Performance rules) are binding. Sections 4 (Research) and 6 (Feature specs) explain the *why* and the intended technique.
- Work phase by phase (section 8). Each phase ends in a buildable jar plus a short test note for the user.
- You cannot run Minecraft or see the screen. The user is the test rig: they report FPS and screenshots. Never claim an effect looks right or hits a frame rate without their report.
- When this spec and reality disagree (an API differs, a version is wrong), trust what you verified in source code, note the change in `docs/DECISIONS.md`, and keep going. Only stop for a decision that cannot be undone and could reasonably go either way.
- The user is a solo developer, direct and correction-oriented. Be concise, be honest about limits, and confirm plans before large rewrites.

## Requirements

The signature look is **golden hour**: an orange sunrise and sunset sky, warm light, long shadows, soft haze and a sun flare. Everything else supports it.

**Must have (in the user's words where possible)**

| Feature | What the user wants | Priority |
| --- | --- | --- |
| Sunrise and sunset sky | Orange sky, beautiful transitions; the thing they love most | P0 |
| Lighting and colour | Vibrant, "pop", blocks that look detailed and 3D-ish | P0 |
| Badlands-style glow | Warm ground that seems to light the air and shadows around it | P0 |
| Shadows | Real sun shadows, smart about what they update | P0 |
| Fog | Subtle, well done, only when it is needed | P0 |
| Water | Proper reflections, soft gentle animation, no crazy waves | P0 |
| Reflective ground | Ground that looks reflective and detailed (sheen, wet look in rain) | P1 |
| Lens flare | Wanted explicitly | P1 |
| God rays | Wanted | P1 |
| Clouds | Nice clouds | P1 |
| POM (carved depth) | Only on the High tier | P2 |
| Settings screen | Many options and sliders; the user dislikes how few Luxium has | P0 |

**Out of scope, never build:** depth of field, motion blur, chromatic aberration, a fancy or crazy night sky, frame generation or interpolation (Luxium's TFR), and screen-space reflections.

**Day length:** the user wants longer days and nights. Do **not** build this. Use the companion mod Custom Time Cycle (see Research). Aurum's sun and sky must read the world time each frame, so they follow any day length automatically.

**Optimise by what is seen:** nothing far away should cost anything. Full quality near the player, cheaper mid-range, and fog hides the far edge. The user plays at a render distance of about 10 chunks.

**Tiers:** Low, Medium and High presets plus per-effect toggles and sliders. Low must hold 60 FPS on the UHD 620. High may trade FPS for POM and a larger shadow map.

## Platform and versions

Target **Minecraft 1.20.1 on Fabric with Sodium 0.5.x**, OpenGL 3.2 core, Java 17. That version is frozen, so the mod never breaks from game updates.

| Item | Value | Notes |
| --- | --- | --- |
| Minecraft | 1.20.1 | The user already plays Fabric 1.20.1 with about 200 mods |
| Loader | Fabric Loader + Fabric API (1.20.1 line) | Agent: pick the latest 1.20.1-compatible versions from fabricmc.net and record them |
| Build | Fabric Loom (Gradle), Yarn mappings for 1.20.1 | Use the official Fabric example mod template for 1.20.1 as the starting point |
| Java | 17 | Required by 1.20.1 |
| Renderer | Sodium 0.5.x, pinned to one exact version | Sodium owns terrain rendering; its internal shaders change without notice, so pin and inject deliberately |
| Conflicts | Iris, Oculus, Luxium, other shader or render-pipeline mods | Declare `breaks` or `conflicts` in fabric.mod.json and show a clear message |
| GPU | Intel UHD 620 (24 EU, no dedicated VRAM, shares system RAM) | Also tested by Luxium's author; known baseline of 60+ FPS at 1600×900 with Luxium |
| Machine | HP ProBook 640 G5, 16 GB RAM, Windows, PowerShell, WSL2 available | User allocates 10 GB to Java; recommend 6 to 8 GB |
| Screen | Treat as 1920×1080 until the user confirms |  |

**Why not newer versions:** Mojang is moving Minecraft Java from OpenGL to Vulkan. Version 26.2 already ships a Vulkan backend, and Mojang warned that OpenGL rendering mods will need much more work to update. Veil (the main Fabric rendering library) only supports 1.21.1+, so we write our own small framebuffer and post-processing layer.

## Research findings

The "pop" in popular shader videos mostly comes from **generated normals**, not LabPBR resource packs. That makes it cheap enough for us if we bake it once at load.

**Reference shader packs (OptiFine/Iris), studied for technique only**

| Pack | What it is praised for | Lesson for Aurum |
| --- | --- | --- |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) (72M downloads) | Reworked vanilla feel; Integrated PBR: *Generated Normals* (bump detail from texture colour differences) and *Coated Textures* (colour-driven noise) | The pop needs no resource pack. Complementary enables generated normals only on Vanilla/RTX-ish, Ultra and Extreme, off on Potato, Low and Medium, so per-pixel generation costs real FPS ([settings](https://shadersmods.com/complementary-shaders/)) |
| [BSL](https://www.curseforge.com/minecraft/shaders/bsl-shaders/wiki/Configuration-Guide) | Warm, attractive lighting; realistic water; volumetric light | Warm grade is a big part of the appeal |
| [Photon](https://modrinth.com/shader/photon-shader) | Clouds, clear fresh water, sharp image, colour balance; variable-penumbra shadows | Clarity over blur; clouds matter |
| Sildur's Vibrant | Noticeable god rays | God rays are a selling point |
| [MakeUp Ultra Fast](https://github.com/javiergcim/MakeUpUltraFast) | Built for low-spec, tested on Intel; author lists shadows as one of the most resource-intensive effects | Shadows are where the budget goes |
| [Saturus](https://github.com/WhyFenceCode/Saturus) | Lightweight, custom penumbra shadows, custom overworld fog | Lightweight packs still do shadows and fog |
| [Luxium](https://www.curseforge.com/minecraft/mc-mods/luxium) (the bar to beat) | Mod, not a pack; caches, skips and merges passes; reduced-resolution effects; LSR upscaling; TFR frame generation (author admits artifacts); overworld only | We copy the *ideas* (caching, pass merging, low-res effects) and reject frame generation. Custom licence: read nothing from its code |

**Techniques and specs**

- [LabPBR 1.3](https://shaderlabs.org/wiki/LabPBR_Materials): `_n` texture = normal XY, material AO, height in alpha (0 = 25% depth); `_s` = smoothness, F0, porosity/SSS, emission. Support it when a pack provides it.
- Normals baked at resource reload already exist in the wild: [MaterialMaps](https://modrinth.com/mod/materialmaps) derives normal maps from height maps during resource reload. That validates our bake-once approach.
- [PomPom](https://modrinth.com/mod/pompom): a Fabric mod that exists just to give POM depth without a shader pack, which shows demand. POM is GPU-heavy; disabling it at distance is standard ([notes](https://online.ts2009.com/mediaWiki/index.php/Parallax_mapping)).
- Shadows: [shaderLABS tutorial](https://github.com/shaderLABS/Shadow-Tutorial). Shadow mapping draws the world twice; distortion gives detail near the player; bias chosen from the normal and the distortion factor prevents shadow acne.
- God rays: [GPU Gems 3, chapter 13](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-13-volumetric-light-scattering-post-process), a radial blur from the sun over an occlusion mask. Disappears when the sun is behind the camera.
- Fog: Inigo Quilez exponential fog with sun-tinted colour ([code](https://git.gay/kaneraZh/lygia/src/branch/cpp_bundle/lighting/raymarch/fog.glsl)); exact height fog ([analytic density](https://blog.demofox.org/2014/06/22/analytic-fog-density/)).
- Water: scrolling dual normals, depth-based colour and foam, Fresnel ([breakdown](https://threadreaderapp.com/thread/995757451000590336.html), [80.lv](https://80.lv/articles/how-to-build-stylized-water-shader-design-implementation-for-nimue/)).
- Clouds: fBm noise layers; fewer octaves and 2D noise are the biggest speedups; pre-baked noise beats computed noise ([noise costs](https://smithery.ai/skills/Bbeierle12/shader-noise)).
- Tonemapping: ACES skews saturated highlights toward yellow or magenta; AgX desaturates to white and keeps richness, with a "Punchy" variant ([AgX looks](https://www.fab.com/listings/29a328ed-3413-41ea-8f46-308cddc19f87)).
- Intel risk: Intel OpenGL drivers have documented float-handling bugs with Minecraft shaders ([2026 report](https://community.intel.com/t5/Developing-Games-on-Intel/OpenGL-procedurally-generated-night-nebula-shader-malfunctioning/m-p/1753640)).

**Existing mods: companion or reference**

Rule: a mod that also redraws the sky, clouds or water fights Aurum over the same render code. Only non-rendering mods run alongside; rendering mods are references, studied only where the licence allows.

| Mod | 1.20.1 Fabric | Licence | Decision |
| --- | --- | --- | --- |
| [Custom Time Cycle](https://modrinth.com/mod/customtimecycle) | Yes | LGPL-3.0 | **Companion.** Day/night length without changing tick speed, includes a villager iron-golem fix |
| [Better Clouds](https://github.com/Qendolin/better-clouds) | Fabric; verify 1.20.1 | MPL-2.0 | Reference for blocky volumetric clouds at vanilla cost |
| [WaveWater](https://www.curseforge.com/minecraft/mc-mods/wavewater) | Yes | MIT | Reference for waves, shore wash, caustics; credit if code is borrowed |
| [AquaMirage](https://www.modpackindex.com/mod/208733/aquamirage-water-shader) | Yes | Not stated | Skip (SSR, unclear licence). Borrow the *idea* of rain puddles only |
| [Celestial](https://www.curseforge.com/minecraft/mc-mods/celestial/files/5631958) | Up to 1.20.4 | MIT | Reference for data-driven sky objects |
| [Better Skies](https://www.modpackindex.com/mod/155323/better-skies) | Resource pack | Not stated | Skip: 512px+ sky textures cost shared RAM |
| [Chroma](https://modrinth.com/project/e4Y0w1Wb) | 1.21.11 only | All rights reserved | Skip; build our own lens flare |
| [Shine: VFX](https://www.curseforge.com/minecraft/mc-mods/shine-vfx/files/8143677) | 1.21+ only | All rights reserved | Idea only: bloom from chosen sources via masks |
| [Better Fog](https://www.curseforge.com/minecraft/mc-mods/better-fog/files/8440949) | Yes | All rights reserved | Not needed; Aurum fog replaces it |
| [Particulate](https://modrinth.com/project/nIGZuP7R) | 1.21.5+ | LGPL-3.0 | Ambient particles do not conflict; find a 1.20.1 equivalent for the modpack |

**Not verified:** pack screenshots were not viewed (text-only research), and Luxium's comment section requires sign-in. Cost estimates in this doc are engineering estimates until the user measures them.

## Architecture

A Java frame director decides each frame what to rebuild. GPU passes then run top to bottom, reading slow-changing data from caches instead of recomputing it.

**Frame pipeline (text version of the diagram in the shared doc)**

```text
Frame director (Java, once per frame): sun angle, fog density, which caches to rebuild
  |
  1. Shadow map ............ <- Terrain shadow cache (rebuilt on threshold)
  |                             cached terrain layer + nearby entities, updated lazily
  2. Sky and clouds ........ <- Sky lookup texture (rebuilt when the sun moves)
  |                             one lookup read per pixel, clouds at 1/2 res
  3. Terrain (Sodium) ...... <- Baked normal atlas (built on resource reload)
  |                             lighting, baked normals, shadows, sheen
  4. Water + translucents .. <- Noise textures (baked at startup)
  |                             sky reflection, depth colour, foam, sun glint
  5. Downsample chain           1x1 average for bounce glow, bloom 1/4 -> 1/16
  6. God-ray mask               radial blur at 1/4 res, skipped if sun off-screen
  7. ONE combined post pass     the only extra full-screen pass: fog, rays, bounce, bloom, grade
  8. Lens flare                 sprites, sun occlusion read one frame late
```

Pass 7 is the only extra full-screen pass at full resolution on top of vanilla's own rendering; everything soft runs at reduced resolution.

**Module layout (proposed; adjust after reading Sodium's source)**

```text
dev.impeccable.aurum
  AurumMod              entry point, config load, conflict checks
  config/               AurumConfig, presets (Low/Medium/High), settings screen
  director/             FrameDirector, FogDirector, SunState
  render/               Pipeline, Framebuffers, ShaderManager, GpuTimers
  render/passes/        ShadowPass, SkyPass, CloudPass, WaterPass,
                        DownsamplePass, GodRayPass, PostPass, FlarePass
  bake/                 NormalAtlasBaker, NoiseBaker
  mixin/                Sodium terrain hooks, WorldRenderer hooks,
                        sky and cloud replacement
assets/aurum/shaders/   one folder per pass, include/ for shared GLSL
                        (common, sky, fog, lighting)
docs/                   SODIUM_NOTES.md, DECISIONS.md, TESTING.md
```

## Feature specs

Each feature lists its technique, the trick that makes it cheap, and what changes by tier. "Invention" marks ideas that go beyond the reference packs.

### 1. Sky and golden hour (P0)

- **Technique:** a sky colour lookup texture, about 128×64, indexed by (sun elevation, angle between view and sun). Rebuild it only when sun elevation changes past a threshold or the weather changes, on the GPU or the CPU. Drawing the sky is then one texture read per pixel.
- **Look:** a deep orange-red band at the horizon toward the sun at dawn and dusk, fading to blue-violet away from it; a warm glow around the sun disc; a clean blue noon. Night stays simple: dark blue gradient, vanilla stars, moon. No fancy night sky.
- **Inputs:** world time from the client world each frame, so Custom Time Cycle's slower days just work. Rain lowers saturation and pulls toward grey.
- **Tiers:** the same on all tiers (it is nearly free).

### 2. Lighting, colour grade and "pop" (P0)

- **Lighting** is computed inside Sodium's terrain shader (forward shading, no G-buffer): sun or moon directional light × shadow, sky ambient tinted by the sky lookup, vanilla block light warmed slightly, and vanilla AO kept and boosted a little.
- **Baked generated normals (Invention):** at resource reload, build a normal atlas parallel to the block atlas from each texture's luminance differences, with edge-aware filtering so pixel-art edges stay crisp. Also bake a small "coating" noise channel. In game this costs one extra texture read. It re-bakes when the resource packs change.
- **LabPBR support (Medium+):** if a loaded pack provides `_n` and `_s` textures, use them instead of the generated ones.
- **Grade (updated 2026-10-21):** a hue-preserving filmic tonemap with daylight white balance, then Oklab saturation/vibrance/contrast. AgX available as an option. The first AgX "punchy" version shifted everything yellow; see `docs/research/LOOK.md`.
- **Tiers:** Low = baked normals at reduced strength; Medium = full strength + LabPBR; High = adds POM (feature 10).

### 3. Badlands glow (P0)

- **Bounce light (Invention):** each frame, downsample the scene to a tiny mip chain (1×1 at the end) and read the average colour of what surrounds the player. Tint shadow-side ambient and fog toward it, so red terracotta gives warm red shadows and snow gives blue ones. Nearly free.
- **Bloom:** threshold only bright pixels (sun, lava, glowing blocks, strong highlights), blur down a 1/4 → 1/16 resolution chain, and add back. Keep it subtle; no glowing haze over the whole screen.
- **Sheen:** a small built-in material table (sand, red sand, terracotta, polished stones, ice, glass) gives a soft specular highlight that is strongest at low sun.
- **Tiers:** bounce on all tiers; bloom on Medium+.

### 4. Shadows (P0, the heaviest feature)

- **Base:** a sun or moon shadow map with distortion toward the player and bias from the normal and distortion factor, plus 4-tap soft filtering. Size 1024 (Low), 1536 (Medium), 2048 (High).
- **Distance:** 4 to 6 chunks around the player, separate from render distance. Past that, sky ambient only, with a smooth fade.
- **Lazy updates (Invention):** split into a cached terrain layer and a dynamic layer. Re-render terrain only when the sun angle moves past a threshold, the player moves several blocks, or nearby chunks change. Re-render the dynamic layer only for the player and mobs within about 16 blocks.
- **Cheap casters:** grass, flowers, torches and other small decorations never cast shadows. Leaves do. Mobs further than 16 blocks get a soft blob shadow under their feet.
- **Time-slicing:** when a full re-render is needed, spread it over several frames to avoid FPS spikes.

### 5. Fog (P0)

- **Technique:** Inigo Quilez exponential distance fog plus optional exact height fog for valleys. Colour comes from the sky lookup, warmer when looking toward the sun.
- **Fog director (Invention):** Java-side logic reads time of day, weather, biome and the player's skylight level, and outputs one smoothed density value each frame. Density is near zero by default and rises only at dawn and dusk, in rain, in caves and in swamp-type biomes. It fades over seconds and never pops.
- **Far edge:** a light distance fade hides the render-distance edge on every tier.

### 6. Water (P0)

- **Surface:** two scrolling wave normal textures at slow speed, plus very small vertex waves on Medium+. Soft and gentle, no storm waves.
- **Colour:** depth below the surface picks between shallow (clear, teal) and deep (dark blue) colours, with soft foam where the gap is small (shorelines, around blocks).
- **Reflections (Invention):** evaluate our own sky and cloud functions in the reflected direction, so the water reflects the real sunset and clouds. No screen-space reflections, so trees and buildings do not reflect. Add Fresnel and a sharp sun glint.
- **Underwater:** clearer than vanilla, tinted fog.

### 7. Reflective ground and rain puddles (P1)

- Smooth materials reuse the water's sky reflection at low strength.
- **Rain puddles:** when it rains, upward-facing surfaces that are exposed to the sky slowly become wet (darker, glossier) and puddles appear in a noise pattern, reflecting the sky. They dry out over minutes after the rain stops.

### 8. Lens flare (P1)

- 6 to 8 soft glow sprites placed along the line from the sun's screen position through the screen centre, drawn as additive quads.
- **Occlusion (Invention):** read a single depth texel at the sun's screen position, via a 1×1 copy or occlusion query read one frame later so the GPU never stalls. Fade the flare when terrain blocks the sun.
- Strongest near the horizon at dawn and dusk, subtle at noon, off when the sun is off-screen, below the horizon or in rain.

### 9. God rays and clouds (P1)

- **God rays:** radial blur over a sky-occlusion mask at 1/4 resolution with about 12 samples; skip when the sun is off-screen. Strength follows the fog director, so rays appear in haze and stay subtle at clear noon.
- **Clouds (direction updated 2026-10-21):** the user prefers stylized, pretty, layered clouds (Story Mode-like) over realism; see `docs/research/LOOK.md`. Original plan: a 2D cloud layer at a fixed altitude from a baked tiling noise texture, 3 to 4 octaves, at 1/2 resolution. **Fake thickness (Invention):** sample density a second time offset toward the sun and darken where it is thicker, which gives lit rims and shaded bellies. Coverage follows weather. Optional later: a blocky volumetric mode studied from Better Clouds (MPL-2.0).

### 10. POM, High tier only (P2)

- Parallax occlusion mapping with the LabPBR height map, or a baked height map from the luminance when no pack is loaded. Only within about 12 blocks, with step count reduced by distance and view angle, fading out smoothly.

### 11. Settings screen (P0)

- In-game settings screen with Low, Medium and High presets, a toggle and strength slider per effect, shadow size and distance, fog strength, flare strength, bloom strength, saturation and contrast, and a "show FPS cost" debug overlay with per-pass GPU timer queries.

### 12. Waving plants and leaves (P1, added 2026-10-21 at the user's request)

- Grass, flowers, crops, tall plants, leaves, vines and water plants sway with a smooth, premium motion:
  anchored bases, a main bend plus detail flutter, gusts that roll across the land, stronger in rain and thunder.
- Built in Phase 1 alongside the terrain shader. Research and design: `docs/research/FOLIAGE.md`.
- **Tiers:** on for all tiers (it is a few vertex instructions in the cutout pass only); amplitude and speed sliders.

## Performance budget and rules

The frame budget is **16.6 ms at 60 FPS**, and vanilla Sodium terrain already uses much of it on a UHD 620. Every effect must earn its milliseconds.

**Starting budget on Low at 1080p (estimates; replace with GPU timer measurements)**

| Pass | Target GPU time |
| --- | --- |
| Terrain + lighting + baked normals | Vanilla cost + about 1.5 ms |
| Shadow map (amortised with lazy updates) | 2.5 ms or less on average, no single frame over 5 ms |
| Sky lookup + sky draw | 0.3 ms or less |
| Clouds at 1/2 resolution | 1 ms or less |
| Water extras | 1 ms or less |
| Combined post pass (fog + god rays + bounce + grade) | 1.5 ms or less |
| Bloom chain | 0.8 ms or less (Medium+) |
| Lens flare + occlusion read | 0.2 ms or less |

**Rules**

1. **One full-screen post pass.** Merge fog, god-ray composite, bounce tint, tonemap and grade into one fragment shader. Each extra full-screen pass on a UHD 620 costs real bandwidth through shared RAM.
2. **Lower the resolution of soft effects:** god rays 1/4, clouds 1/2, bloom 1/4 → 1/16. Upsample with a cheap bilinear filter.
3. **Cache what changes slowly:** sky lookup, terrain shadow layer, baked normals, noise textures. Rebuild on threshold, never every frame.
4. **Distance-limit everything:** shadows 4–6 chunks, entity shadows 16 blocks, POM 12 blocks, vertex waves near only.
5. **Skip work that is not visible:** god rays and flare off when the sun is off-screen or below the horizon; no shadow work at night beyond a dimmed moon pass.
6. **Time-slice expensive rebuilds** across frames; never spike.
7. **Logic belongs in Java, maths in GLSL.** The fog director, sun position and thresholds run on the CPU once per frame and are sent as uniforms.
8. **Never stall the GPU.** No synchronous reads; occlusion and averages are read one frame late through buffers or queries.
9. **Conservative GLSL** for Intel drivers: `#version 150`, plain float maths, no clever precision tricks, avoid large dynamic loops, no driver-specific extensions. Test compile on startup and fall back to vanilla with a clear chat message if a shader fails.
10. **Measure, don't guess:** a debug overlay with per-pass GPU timer queries (`GL_TIME_ELAPSED`). Ship nothing whose cost has not been measured on the user's machine.

## Roadmap

Six phases, each ending in a jar the user tests. A phase starts only after the previous gate passes on the user's machine.

**Phase 0: Foundation.** Gradle/Loom project, mod entry point, config file + settings screen skeleton, Sodium version check, Iris/Luxium conflict message, shader loader with compile-on-startup and vanilla fallback, GPU timer overlay.
*Gate:* the game launches with ~200 mods, the settings screen opens, the overlay shows per-pass times, and vanilla visuals are unchanged.

**Phase 1: Golden hour core.** Sky lookup + sky draw, lighting in Sodium terrain shader, baked generated normals, AgX grade, fog director + fog, bounce glow, combined post pass, waving plants and leaves.
*Gate:* 60+ FPS on Low at render distance 10; sunrise and sunset screenshots approved by the user; no visible pop when fog changes; Badlands show warm bounce; plants and leaves sway smoothly with no cracks.

**Phase 2: Shadows.** Shadow map with distortion and bias, soft filtering, cached terrain layer, dynamic layer for nearby entities, blob shadows, time-sliced rebuild.
*Gate:* no shadow acne or peter-panning in the user's test scenes; FPS on Low within 10% of Phase 1; no frame-time spikes over 33 ms when moving.

**Phase 3: Water and wet ground.** Water surface, depth colour, foam, sky reflections, sun glint, underwater fog, material sheen, rain puddles.
*Gate:* user approves calm water at sunset; 60+ FPS on Low looking across an ocean.

**Phase 4: Sky extras.** Lens flare with occlusion, god rays, 2D lit clouds, bloom chain.
*Gate:* flare fades correctly behind trees and mountains; no FPS loss on Low when the sun is off-screen.

**Phase 5: High tier and polish.** POM, LabPBR support, preset tuning, compatibility pass across the user's mod list, README, release jar.
*Gate:* the user's full modpack runs without crashes for an hour of play; Low holds 60 FPS; High is visibly better.

**Test kit to ship with every jar:** a short `TESTING.md` naming the scenes to check (a sunrise over plains, Badlands at sunset, an ocean at noon, a forest at dusk, a cave), the F3 FPS reading to report, and which overlay numbers to copy.

## Agent working rules

The agent works unattended, so every phase must leave the repo buildable and documented.

**Before coding**

- [ ] Verify exact versions of Fabric Loader, Fabric API, Yarn mappings and Loom for 1.20.1 from fabricmc.net and record them in `gradle.properties`.
- [ ] Pin one Sodium 0.5.x version for 1.20.1. Read its source at that tag for: where terrain shaders are loaded, how the chunk render pass is set up, and any shader-injection API. Write findings to `docs/SODIUM_NOTES.md` before writing mixins.
- [ ] Check whether Fabric's Maven is reachable from your environment. If it is not, write everything anyway and give the user a one-line PowerShell build command (`./gradlew build`); the jar lands in `build/libs/`.

**While coding**

- Package root `dev.impeccable.aurum` (the user operates as Impeccable); mod id `aurum`. Both are placeholders until the user names the mod.
- Shaders live in `src/main/resources/assets/aurum/shaders/` with shared code in `include/` files.
- Every mixin is narrow, documented with its reason, and has `require = 1` so a broken injection fails loudly at startup, not silently in game.
- Every effect has a config flag, a strength value and a GPU timer.
- Log decisions that differ from this spec in `docs/DECISIONS.md` with one line of reason.
- Commit at the end of each logical step with a clear message.

**Licensing**

- Do not read or copy code from Luxium, Complementary, BSL or any "all rights reserved" or custom-licence project. Techniques described in public articles are fine.
- MIT (WaveWater, Celestial) and MPL-2.0 (Better Clouds) code may be studied; if any code is adapted, keep the licence header and credit it in `CREDITS.md`.
- Aurum's own licence: ask the user; default to all rights reserved until they choose.

**Hand-off to the user after each phase**

- The jar (or the build command), `TESTING.md` for that phase, and a three-line summary: what changed, what to look at, what numbers to send back.
- Never report an effect as working or fast without the user's report.

## Risks and open questions

Shadows inside Sodium are the biggest technical risk; the user's test loop is the biggest schedule risk.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Rendering shadow casters through Sodium's chunk data is hard | Phase 2 slips | Study Sodium source first; fallback = render nearby chunks into the shadow map with vanilla's block renderer at reduced distance |
| Mixin conflicts with the user's ~200 mods | Crashes | Narrow mixins, `require = 1`, a compatibility pass in Phase 5, clear crash messages |
| Intel driver GLSL bugs | Black screens or broken effects | Conservative GLSL, compile check at startup, vanilla fallback |
| The agent cannot see the game | Wrong look shipped | Phases gated on the user's screenshots and FPS |
| Fabric Maven unreachable from the cloud | No jar from the agent | The user builds locally with one command |
| Day-length mod changes time in a way the sky does not expect | Sun jumps | Read world time each frame; smooth sun angle over a few frames |

**Open questions for the user**

- [ ] Final mod name (Aurum is a placeholder).
- [ ] Screen resolution (assumed 1920×1080).
- [ ] Licence for Aurum: private, open source, or sold later?
- [ ] Exact Sodium version currently in the user's modpack.
- [ ] Does the user want screenshots from Luxium as a "beat this" reference set?

## Sources

Pages opened during research, October 2026.

- [Luxium on CurseForge](https://www.curseforge.com/minecraft/mc-mods/luxium)
- [Complementary Reimagined (Modrinth)](https://modrinth.com/shader/complementary-reimagined) · [Complementary settings and profiles](https://shadersmods.com/complementary-shaders/) · [Complementary v4.4 changelog](https://www.curseforge.com/minecraft/shaders/complementary-unbound/files/3685089)
- [Photon (Modrinth)](https://modrinth.com/shader/photon-shader) · [Photon review](https://anvilpacks.com/shaders/photon-shaders/)
- [BSL (CurseForge)](https://www.curseforge.com/minecraft/shaders/bsl-shaders/wiki/Configuration-Guide)
- [MakeUp Ultra Fast](https://github.com/javiergcim/MakeUpUltraFast) · [Saturus](https://github.com/WhyFenceCode/Saturus)
- [Best shaders 2026 (Craftdex)](https://craftdex.net/de/best/best-minecraft-shaders) · [Best shaders 2026 (GDLauncher)](https://gdlauncher.com/blog/best-shaders-2026) · [TechRadar](https://www.techradar.com/how-to/best-minecraft-shaders)
- [LabPBR Material Standard](https://shaderlabs.org/wiki/LabPBR_Materials) · [MaterialMaps](https://modrinth.com/mod/materialmaps) · [PomPom](https://modrinth.com/mod/pompom)
- [shaderLABS Shadow Tutorial](https://github.com/shaderLABS/Shadow-Tutorial) · [GPU Gems 3, ch. 13](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-13-volumetric-light-scattering-post-process)
- [Inigo Quilez fog (lygia)](https://git.gay/kaneraZh/lygia/src/branch/cpp_bundle/lighting/raymarch/fog.glsl) · [Analytic fog density](https://blog.demofox.org/2014/06/22/analytic-fog-density/)
- [Water shader breakdown](https://threadreaderapp.com/thread/995757451000590336.html) · [Stylized water (80.lv)](https://80.lv/articles/how-to-build-stylized-water-shader-design-implementation-for-nimue/)
- [Shader noise costs](https://smithery.ai/skills/Bbeierle12/shader-noise) · [AgX looks](https://www.fab.com/listings/29a328ed-3413-41ea-8f46-308cddc19f87)
- [Intel OpenGL driver bug report](https://community.intel.com/t5/Developing-Games-on-Intel/OpenGL-procedurally-generated-night-nebula-shader-malfunctioning/m-p/1753640)
- [Minecraft Java moving to Vulkan (Neowin)](https://www.neowin.net/news/minecraft-java-edition-is-upgrading-to-vulkan-but-its-not-great-news-for-mods/) · [NeoForge 26.2 primer](https://docs.neoforged.net/primer/docs/26.2/) · [Veil](https://github.com/foundrymc/veil)
- Mods: [Custom Time Cycle](https://modrinth.com/mod/customtimecycle) · [Better Clouds](https://github.com/Qendolin/better-clouds) · [WaveWater](https://www.curseforge.com/minecraft/mc-mods/wavewater) · [AquaMirage](https://www.modpackindex.com/mod/208733/aquamirage-water-shader) · [Celestial](https://www.curseforge.com/minecraft/mc-mods/celestial/files/5631958) · [Better Skies](https://www.modpackindex.com/mod/155323/better-skies) · [Chroma](https://modrinth.com/project/e4Y0w1Wb) · [Shine: VFX](https://www.curseforge.com/minecraft/mc-mods/shine-vfx/files/8143677) · [Better Fog](https://www.curseforge.com/minecraft/mc-mods/better-fog/files/8440949) · [Particulate](https://modrinth.com/project/nIGZuP7R)
