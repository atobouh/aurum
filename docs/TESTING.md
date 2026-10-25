# Shared lighting alpha — 0.6.2-alpha.1

The current implementation and limits are documented in [the implementation notes](research/LIGHTING_IMPLEMENTATION_062_ALPHA1.md), [the illustrated HTML review](reports/aurum-0.6.2-alpha.1-lighting-review.html), and [the evidence JSON](research/lighting-062-alpha1-evidence.json). Stable 0.6.1 remains separately available.

`./gradlew build` runs the ten-variant production shader adapter contract (145 assertions). Run `JAVA_HOME=/path/to/jdk21 python3 tools/check_incident_lighting.py` for the production Java/GLSL lighting controls and `python3 tools/check_surface_raster.py` for the perspective full-bright raster regression. The EGL checks need `moderngl` and `glcontext`.

[Development integration replay instructions](../tools/lighting_integration/README.md) cover camera/receiver motion, source edits, mixed lamps, exact source counts 0/1/16/17/32/33, full-bright and night vision, the LambDynamicLights fallback, outdoor time changes and continuous dusk footage. Evidence was captured on llvmpipe; target Intel UHD frame-time and latency gates remain open.

Supported models now use the shared evaluator and are marked cyan in `path`; explicit full-bright exceptions are orange. The static voxel replacement is paused when LambDynamicLights is installed. No voxel-occluded moving-source claim follows from the compatibility capture. Source-aware cave fog/AO and local moving-entity shadow casting remain open.

---

## Historical research candidate (before the alpha implementation)

# Lighting unification — research and diagnostic candidate

See [the source audit and engineering contract](research/LIGHTING_UNIFICATION.md) and
[the offline HTML review](reports/aurum-lighting-unification-research.html). This candidate adds
**development diagnostics**, not a lighting fix or a new release. `dist/aurum-0.6.1.jar` is unchanged.

Use a disposable copy of the Regasm Scenery profile. Do not place the new `lighting_contract.mcfunction`
fixture in the natural scenery save. Its room has a lit furnace, torch, stone, zombie head and chest;
other emitters can be present outside the room in the forty-block snapshot. Record actual source counts.
Disable generated relief, authored PBR, waving, clouds and screen-average bounce for this isolation run;
retain the same lighting/occlusion/fog settings between the views. A normal frame retains those post effects.

Development commands:

- `/aurumdev lighting off`: normal rendering.
- `/aurumdev lighting direct|sky|block|fallback|voxel`: actual terrain illumination terms before albedo;
  includes their respective terrain AO factors. `voxel` is the eligible raw sample, not indirect light or
  a confidence-weighted final contribution. `fallback` uses the current lightmap, not separated dynamic sources.
- `/aurumdev lighting coverage`: red fallback / green effective voxel replacement; blended edges are intermediate.
  This does not prove source completeness or geometry correctness.
- `/aurumdev lighting normal|ao|path`: geometry normal, terrain AO, or cyan terrain path. Water is purple.
  Entities, block entities and hand **remain core-shaded**, not relit or marked by the diagnostic.
- `/aurumdev lighting snapshot`: writes JSON under `run/debug/aurum-lighting/` with camera, eye light,
  target, configuration, mods, fog and voxel cache/source metadata. Pair it with the actual capture.

Aurum's composite is bypassed during isolation; Minecraft overlays/vignette and core-shaded objects remain.
Views are not persisted in settings, are reset on disconnect, and cannot be enabled in a release client.
Do not read their encoded colours as absolute physical irradiance. No shader/path coverage claims beyond
what is marked above. Verify switching and reset at runtime, and inspect both normal and restored frames.

Run `JAVA_HOME=/workspace/jdk21 python3 tools/check_lighting_contract.py` for seven **current-behaviour
characterizations** using the production baker. It reproduces valid dark back faces, omitted-source energy
and receiver coverage gaps; passing means those behaviours were reproduced, **not that they were fixed**.
The existing voxel checks remain the geometry/finite-output controls. The new source-set harness supplies
sixteen/seventeen inputs directly; it does not test runtime ranking or LambDynamicLights injection.

The research contract now makes camera motion, moving receivers, moving sources, overlapping lamps,
cache movement and continuous day/night transitions mandatory at each relevant integration gate. The
current still captures and command-time snapshots do not pass those tests; per-frame recording and
latency measurements remain required engineering work.

The engineering gates require a common surface evaluator across terrain/core paths, coherent direct/indirect
local transport and source ownership, then source-aware cave air and AO. Until those gates pass, do not
promote this diagnostic candidate as unified lighting or publish a new mod version.

# Testing: 0.6.1 — daylight readability after real footage

The expanded natural-scene review uses the separate **Aurum Regasm Scenery** save and the exact
profile in `research/regasm-scenery-profile.json`. Its five world datapacks were active before
first generation. See `research/REGASM_NATURAL_SCENERY_REVIEW.md` for cameras, exclusions, runtime
repairs and readiness checks; this covers a documented mod subset, not the full user installation.
Do not load the older Visual Lab under missing registry data or run fixture functions in the
natural scenery save. The two saves use different dependency profiles.

Reproduce High with LabPBR off, as confirmed by the user. The supplied inventory includes Nature's
Spirit, Fabric Seasons and its Terralith compatibility pack, not only Terralith. Use the optional
`modded_canopy` fixture with those local test dependencies; hashes are recorded in
`research/visual-compatibility-061.json`. Compare frozen-cloud forest views with cloud shadows on/off,
then old/new lighting at unchanged settings. Keep the dark canopy in frame. Repeat at midnight,
inside a sealed room at noon, and beside the lake. Respect seasonal colours rather than forcing
all foliage green. The full user world/modpack remains outside this reproduction.

Native captures and limits: `reports/aurum-0.6.1-daylight-review.html` and its capture manifest.
The terrain shader adds no new texture reads or passes; Intel UHD frame-time testing remains needed.

## Existing feature checks from 0.6.0

Build with JDK 21. Run `JAVA_HOME=/path/to/jdk21 python3 tools/check_voxel_lighting.py` and
`JAVA_HOME=/path/to/jdk21 python3 tools/check_sky_model.py`. The voxel fixtures check an opaque wall,
an aperture, both halves of a slab, unloaded cells, emitter self-occlusion, hue, cancellation,
determinism and a surface shadow. They run the production Java baker.

Use a disposable copy of the seed/world documented in the capture manifest. Install the four
base functions under `tools/visual_lab` in a development test datapack, not in a real save. The development
client alone registers `/aurumdev reload` and `/aurumdev freezeclouds true|false`, for reproducible
captures. Teleport first and wait for destination chunks before executing a fixture function. Inspect
actual openings, blockers and mesh/shadow readiness; a fixed delay alone is not proof of readiness.
`tools/capture_visual_lab.py` saves the original PNG plus configuration/command metadata.

- **Beam hall:** at time 11500, compare Natural / Voxel Crisp / God Rays off at the same camera and
  default ray strength 100. Seal all three windows and compare on/off again. Closed rooms must not
  develop an ambient glow. Repeat at 1024 and 2048 shadow resolution. Inspect motion for ordered
  patterns, stepping and shadow-map aliasing; pixel-art mode is not an alias-free reconstruction.
- **Local light:** warm torch and blue soul torch in the neutral room, 4/8 detail, Natural/Crisp and
  voxel off. Check coloured faces, cube and slab silhouettes. Place another occluder and wait for a
  new cached result. Move through the volume boundary; check fallback, and remove every light.
- **Terralith forest:** use the actual test dependency/version and biome listed in the manifest.
  Hold camera/time/cloud phase/wind fixed for cloud-shadow on/off and canopy-depth comparisons.
  Stand under leaves and look into the distance: canopy enclosure must not fog the entire forest.
  Then unfreeze clouds and record motion at the normal 100% speed.
- **Detail board:** compare sharpening 0/65, then Natural/Crisp shadow edges. Check texture hue,
  halos, generated relief and grid stability when moving the camera across a fraction of a block.
- **Sky/water:** repeat village noon/sunset/night. Compare the water transition at 0/50/100 with
  screen-space reflections enabled and at the existing lake camera. The 0% option preserves the
  original wave/glitter parameters; lighting/atmosphere improvements still apply to both styles.
- **Ocean:** clear noon / sunset / night / rain at the recorded camera, plus a submerged lake view.
  Inspect the chunk boundary and sun path. The wider far-transparent fade must not pale opaque trees
  in the village or forest; it reuses the before-translucent depth copy and adds no surface buffer.
- Check toggles, in-game category tooltips, disconnect/rejoin, and startup shader compilation.
  Aurum's added fragment samplers remain within the OpenGL 3.3 minimum limit. Sodium's atlas and
  lightmap bindings remain on units 0 and 1; cloud SDF uses 2, the local-light atlas 15.

The test client uses llvmpipe OpenGL 3.3, 1280×720, vanilla textures and 12 chunks. This does not measure
Intel UHD performance or the user's complete 200-mod setup. On the reported stable-60-FPS machine,
compare the same world, resolution and High settings with 0.5.10, then 0.6.0 Natural and Voxel Crisp.
Include walking, block edits and frame-time percentiles; a still frame does not test cache refresh cost.
Crisp shadows/reconstruction reduce filtering reads; crisp ray integration uses extra adaptive taps.

# Testing: 0.5.10 — atmosphere and expanded shader references

Run `JAVA_HOME=/path/to/jdk21 python3 tools/check_sky_model.py` for numerical convergence and finite
sky output across day, twilight, night, clear weather and rain. See
[ATMOSPHERE_REFERENCE_REVIEW_0510.md](research/ATMOSPHERE_REFERENCE_REVIEW_0510.md) for source identity,
local checkouts and limitations. Use the saved camera inputs in the new capture manifest.

- Compare noon, sunset and midnight in the village. Keep look settings fixed. The upper/lower sky should
  retain a gradient, night should stay dark, and warm block lights should remain legible.
- Walk past foliage and block corners within 12 blocks, then out to 48 blocks. Check tighter shadow
  filtering for acne, detached contacts, jaggies and movement shimmer on both 1024 and 2048 maps.
- Check thin shafts under trees on Low/Medium/High (10/12/16 volumetric taps); look for banding and leakage.
- At the lake and ocean, inspect broad waves, fine glitter, bank reflections and distant calming.
  Water normals/caustics must remain periodic at camera X/Z multiples of 256.
- Repeat in rain, a cave opening and the user's full modpack while moving. Dedicated rain/cave/motion
  visual acceptance remains open; numerical weather coverage is not a substitute for those gameplay checks.

The user-reported baseline is stable 60 FPS on Intel UHD, High, 12 chunks, roughly 200 mods. Measure this
candidate on the same machine/world/options. Increased CPU sky work and High's extra volumetric taps
must be included in that comparison; llvmpipe is not an Intel UHD performance benchmark.

# Testing: 0.5.9 — source-based lighting and integrated air

Use the saved coordinates and reference-client settings in
[LIGHTING_SOURCE_REVIEW_059.md](research/LIGHTING_SOURCE_REVIEW_059.md). Keep world, camera, FOV, time,
weather, resource pack and resolution fixed. Reference packs have their own cloud and sun-path defaults;
Photon's test disables SH skylight because its compute program exceeds the software GPU's shared-memory limit.

- At sunset, inspect the gold-lit ground, tree interiors, cast-shadow length and fog through gaps. Switch
  God Rays off/on: there should be no separate broad additive glow masking nearby texture and silhouettes.
- Nearby grass, flowers and crops now cast within 8 blocks with 1024-pixel shadow maps, or 16 blocks with
  larger maps. Inspect their cutoff while walking. Their cached shadows do not follow every wind movement.
- Walk toward and past a tree at 2–8 blocks. Check depth edges for haze bleeding, march banding and flicker.
- At the lake and open ocean, inspect reflected banks, fine glitter, distant calming and the chunk horizon.
  Cross X/Z multiples of 256 while watching the same water surface for normal or caustic phase jumps.
- At noon and midnight, check foliage colour, minimum light, warm block lights and unwanted sun scattering.
  Repeat in rain and in a cave with a sunlit opening; cave cover should darken ambient air without erasing
  directly lit shafts. Rain, cave and full motion acceptance still require dedicated verification.

Completed here: Gradle build, terrain/composite/volumetric GLSL validation, 14 runtime shader programs,
analytical integration checks, and stationary sunset, noon, lake, open-ocean and night images at 1280×720.
The development GPU is Mesa llvmpipe. Measure LOW and the current preset at native 1080p on Intel UHD,
including F8 GPU timings and moving-camera quality, before making performance or parity claims.

# Testing: 0.5.7 — colour, nearby shafts and close detail

Use the same world, camera, FOV, time, weather, resource pack, resolution and preset for 0.5.6 and 0.5.7.
At sunset, compare a clear patch of sky beside the sun and an actual cloud gap. The sky should keep an amber
gradient without a broad painted shaft in a clear patch; light shafts should follow cloud and terrain gaps.
Check the same view with God Rays off in F7 to separate sky radiance from shafts. With God Rays on, inspect a
nearby tree or wall edge at 2–8 blocks: the shadow-map beam should stay locally defined rather than spreading
across the adjacent surface. Move the camera slowly to check for stair stepping and shimmer.

At clear noon and sunset, compare grass, leaves, stone and water in the same viewpoint. Texture pixels and
silhouettes should remain legible; foliage should have bright exposed areas and darker interiors without a
uniform neon-green tint. On water, check the cloud reflection, horizon colour and sun glitter without losing
the shallow bed. Test rain and night for unwanted bright sky light. If an authored LabPBR pack is available,
repeat the close-texture check on its height maps and with generated relief off.

Record F8 **Terrain**, **Aurum post**, **Frame (GPU)** and FPS at 1080p on an Intel UHD 620 for LOW and the
current preset, including moving-camera checks. This development environment uses Mesa llvmpipe software GL
at 1280 × 720, so it verifies shader compilation and visual examples but cannot establish UHD performance.

# Testing: 0.5.6 — shared sky colour, cloud reflections and generated relief

Use the same world, camera, FOV, weather and resource pack for 0.5.5 and 0.5.6. On a fresh LOW
configuration, check that generated Pixel 3D relief is enabled at a 16-block distance. Existing custom
configurations should keep the user's POM preference; only the known unmodified old LOW default is migrated.

- At clear noon and sunset, compare the sky near and away from the sun and the colour of the same cloud shapes
  on open water. Water should show sky/cloud colour even with terrain reflections off; its offscreen cloud
  reflection will fall back to the sky colour. Rotate and move slowly to check for edge artefacts or lag.
- Look into a leaf crown at noon and sunset. Outer leaves should be brighter than the interior while individual
  texture pixels stay legible. Check rain and night for excessive darkening.
- Inspect gravel, stone bricks, logs and dirt from 1–16 blocks with generated relief, then with an authored
  LabPBR height map. Generated relief should add modest raised pixels without black/white striping or crawling.
  Authored maps should retain their stronger walls and self-shadow.
- Use F8 to record Terrain, Translucent, Aurum post and Frame (GPU), plus FPS, on an Intel UHD 620 at 1080p.
  Compare LOW with relief on/off and water reflections on/off. Report moving-camera shimmer as well as stills.

The cloud test machine runs Mesa llvmpipe at 854 × 480 and cannot validate UHD 620 frame time or 1080p
sharpness. It has no authored PBR pack, so the authored-height-map check remains for target hardware.

# Testing: 0.5.5 — sky, foliage, water and detail

Compare with 0.5.4 in the same world, camera, FOV, time, weather, resource pack and preset. Capture clear
noon, low-sun sky (toward and away from the sun), a forest canopy, a shallow riverbed, open-water sun glitter,
water under a bridge or cliff, and a close stone or log face with Pixel 3D enabled.

- The daylight sky should keep a blue-to-white gradient; golden hour should have warm light near the sun with a
  cooler upper sky, without a pink wash or a hard horizon stripe.
- Leaf crowns should show brighter exposed faces and darker interiors without neon greens or black patches.
- Sand and stone should stay legible through shallow water. Glitter should form a path instead of clipped white
  dots; caustics should disappear in a cast shadow and at night.
- Solid block texture pixels and the sidewalls of Pixel 3D relief should remain clear without crawling or
  ringing as the camera moves. Check rain and night for unwanted light.

Record F8 **Terrain**, **Aurum post**, **Frame (GPU)** and FPS on Low and High using the same UHD 620 power mode
as 0.5.4. The cloud build and software OpenGL smoke test cannot measure the target GPU cost.

# Testing: 0.5.4 — world lighting

This build changes terrain lighting. Test in the same world, camera position, FOV, weather, resource pack,
Minecraft brightness and power mode as 0.5.3. Use F6 to compare Aurum on/off; use Photon and Complementary
screenshots as style references, not as matched performance runs.

1. **Noon, mixed faces:** look at a village or cliff with sun-facing and away-facing walls. Sides should read
   differently, while stone and wood texture remains visible. Check straight-down noon sun for artifacts.
2. **Forest shade:** leaf crowns and the ground under them should retain detail without green neon, black
   patches or light leaking through a roof.
3. **Sunset, toward and away from the sun:** sunlit faces should be amber and shaded faces cooler but readable.
   Rotate the camera slowly; look for sky-light colour stepping or flicker on vertical walls.
4. **Rain and night:** make sure the directional shade does not create bright walls, clipped highlights or
   unwanted colour casts. Check a torchlit interior for dirty corners.

Please send paired screenshots from those scenes and the F8 **Terrain**, **Aurum post**, **Frame (GPU)** and FPS
readings for Low and High, compared with 0.5.3 in the same power mode. The development machine confirmed shader
compilation and visually checked one forest scene at noon and sunset using software OpenGL. It cannot establish
the look against Photon/Complementary or benchmark an Intel UHD 620.

# Testing: water, one look (0.5.3-water.1)

- Water now keeps the vanilla look (its pixel lines), dark and glassy, mirroring the shore, trees and sky, on every
  preset. Glitter and caustics are gone by design.
- Check: a lake at noon from the shore, a swamp, a river at sunset, looking straight down in shallows, and FPS
  (F8 "Translucent") next to a big lake.

# Testing: 0.5.3

- **3D for all textures** (F7 > POM, on): blocks your PBR pack has no height map for (wood, modded blocks) get
  pixel relief generated from their own texture. The pack's real maps still win where they exist.
- Note: the 1.20.1 build of Seraphic ("Elys PBR R1") only covers ~150 textures; the newer R4 covers ~1,900 and is
  much richer (it is marked incompatible on 1.20.1 but loads fine when you confirm).

# Testing: 0.5.2

- Pixel 3D now works on every face, in every direction (walls, floors, ceilings).
- Gloss: smooth blocks of a PBR pack reflect the sky outdoors and the lamp light indoors. Strength: F7 > Glow &
  bloom > Gloss strength (up to 200%).
- Not covered: chests, signs and other block entities (they are not terrain); a later pass.

# Testing: 0.5.1 (PBR, round two)

- Pack detail kept: 64x normal/height maps are no longer shrunk to 16x.
- **Pixel 3D (voxel parallax)** on every preset: each texture pixel of a LabPBR pack stands up as a tiny cube with
  lit/shaded sides and its own shadow (Seraphic, SPBR, Vanilla Mashup). F7 > POM: depth, distance.
- Check: grass, logs, dirt paths, stone bricks in sun, at noon and golden hour; FPS with POM on and off.

# Testing: 0.5.0 (looks + PBR)

- **Look** selector at the top of F7: Aurum, Airy (Bliss-inspired), Clear (Photon), Warm (Complementary), Vivid (BSL).
- **LabPBR packs now work** (SPBR, Vanilla Mashup PBR, SPBR Glowing Ore): sharp pack bumps, gloss and sky
  reflections, gold/iron metal, glowing ores, 3D blocks on Medium/High (F7 > POM: depth, distance).
  Note: SPBR Glowing Ore is labelled for 1.21 (pack format 45); Minecraft 1.20.1 marks it "incompatible" -
  enable it anyway when asked, it works.
- Check: diamond/redstone ore in a dark cave, a gold block in sun, stone bricks close up, a sunset per look.

# Testing: 0.4.2

- **Fixed:** the dark, smearing blotches on grass and mobs. That was the contact-shadow effect's noise; it is
  now stable (no crawling when you move) and gentler.
- **The dotted white line in the sky:** not reproduced here (Aurum clouds, both styles, facing the horizon).
  Please check:
  1. F7 > Flare, rays & clouds > Aurum clouds **off**: is the line still there?
  2. F6 (Aurum off): is it still there?
  3. Your previous resource pack: does it go away?
  Tell me which pack you switched to, and whether you have a cloud or sky mod.

# Testing: 0.4.1

Fixes and additions after your 0.4.0 report:

- **Foggy blur and radial stripes near you are gone.** The light-in-the-air effect now uses a realistic air
  density: faint in clear air, stronger in haze and golden hour, strong only for beams into caves and rooms.
  It is also cheaper (quarter resolution).
- **Glitter:** the sun now shatters into many sparkles on the water, as in Photon (best with a high sun,
  looking down at the water).
- **Square sun:** F7 > Sky > Sun shape: Round or Square.
- **Aurum on/off:** F6, or the top button in F7. Off is plain Sodium, handy for comparing looks and FPS.

Please check: the spots from your screenshots (sun behind the house and the hill), water at midday with the sun
in front of you, and the square sun at sunset. For FPS, compare F6 on and off in the same spot, and send the F8
numbers from both.

# Testing: water (0.4.0)

New in 0.4.0, Photon-style water:

- **Clear water:** you see the bed in the shallows (teal) and it fades to deep teal. There are light patterns
  (caustics) on shallow beds.
- **Reflections:** the real sky everywhere; trees, hills and builds on Medium and High (F7 > Water > Water
  reflections). Low reflects the sky only.
- **Surface:** calm ripples, a sun glitter path, and a soft bright line along shores.

Note: after updating, Low has terrain reflections off. Switch them on to try them.

## Checks

1. A lake or river at noon: shallows, deep water, sun path.
2. Lying low by the water with trees on the far shore: reflections. Do they flicker or break at the screen edges?
3. Sunset over water, facing the sun and facing away.
4. A swamp and an ocean (biome tint).
5. Water at night, and under a roof or in a cave (it should not reflect the bright sky there).
6. Flowing water and waterfalls.

## Numbers

From the F8 overlay: **Translucent** and **Frame (GPU)**, with terrain reflections on and off, looking across a
big lake. Plus the FPS.

# Testing: fog, sunrise and daylight (0.3.4)

New in 0.3.4 (fog, sunrise and daylight research, docs/research/LOOK_FOG_LIGHT.md):

- **Fog:** nothing is fogged within 32 blocks; the haze is thinner (much thinner in rain) and never a solid wall.
  It is more neutral, so no more pink or beige soup; it glows warm only toward the sun.
- **Daylight:** brighter and warmer, shade less blue, slightly less saturated, a paler noon sky. Rain days are
  brighter.
- **Contact shadows:** darker under trees, along walls and in corners (F7 > Lighting > AO boost sets the
  strength; 0 turns it off).
- **Golden hour:** shade stays warm.

Check: your 0.3.3 dawn spot and your overcast/rain spot, a forest at midday, and a house wall. Send the F8
numbers ("Rays, flare, bloom" now includes the contact-shadow pass).

# Testing: Phase 4, round four (0.3.3)

New in 0.3.3 (after your 0.3.2 report):

- **Rays:**
  - The flaky outlines are gone: the volumetric light is now half resolution with an edge-aware upscale.
  - The sun rays stay in the sky and never paint over a mountain or wall that hides the sun.
- **New cloud shapes:**
  - Aurum's own clusters of rounded boxes, thicker, with a second tier on the big ones.
  - Clear sky between groups.
  - Your resource pack's layout is still available: F7 > Flare, rays & clouds > Resource pack cloud shapes.
- **Clouds visibly drift:** 3x vanilla speed, with a Cloud speed slider.
- **Grass waves are irregular**, and every plant has its own rhythm.

Checks: the sun behind a hill at golden hour, trees against a bright sky, both cloud styles at noon and sunset,
and a grass field in wind.

## Round three notes (0.3.2)


New in 0.3.2 (after your 0.3.1 report):

- **Blocky clouds rebuilt against Nostalgia, side by side:**
  - crisp Minecraft boxes with soft corners, thicker;
  - bright side faces, lavender bellies, a lighter top layer;
  - distant clouds fade into haze, so depth reads through tone;
  - cloud clusters with clear sky between them.
- **Height:** clouds sit 24 blocks higher by default (slider 0-160).
- **Beams through tree gaps and into caves**, viewed from any side, not only when looking into the sun.
- **Sun rays** show as streaks across trees, terrain and clouds instead of a haze.

## Round two notes (0.3.1)


New since 0.3.0:

- **Two cloud styles.** Switch in F7 > Flare, rays & clouds > Cloud style.
  - **Blocky:** dense rounded boxes in two layers at different heights (the Nostalgia look). The blurry,
    pixelated high sheet is gone, edges stay crisp up close, and the dark band under close clouds is gone.
  - **Soft:** fluffy, see-through cumulus with high wispy streaks (the Better Clouds look).
- **Full-resolution clouds** (option; on when you pick the High preset). Sharpest up close, at about 3-4x the
  cloud cost.
- **Cloud height** slider: +0 to +160 blocks above vanilla's height, for your floating islands.
- **Stronger god rays:**
  - New *volumetric light*: shadowed air stays dark and sunlit air glows, so beams appear through trees, past
    islands and into caves through openings, even with the sun off-screen.
  - The sun rays through cloud gaps reach further.

Note: you are on HIGH, but full-resolution clouds start **off** after updating, because the option is new.
Pick the High preset again in F7, or switch the option on, to try it.

## Checks

1. **Blocky, half resolution:** look at a cloud up close (fly to y 170 under one). Are the edges clean now?
   Then switch Full-resolution on and compare.
2. **Soft:** noon, golden hour, and flying through and above the clouds. Do they look grainy or noisy anywhere?
3. **God rays at golden hour, with the sun behind trees or your floating islands:** do you see beams now? Are
   they too strong, or still too shy?
4. **A cave with an opening to the sky, at daytime:** is there a beam of light?
5. **Noon in open land:** nothing should look hazier or more washed out than in 0.3.0.

## Numbers to send back (F8 overlay, avg)

**Clouds** and **Rays, flare, bloom** in each case:

- Blocky, half resolution
- Blocky, full resolution
- Soft, half resolution
- Soft, full resolution

Take them at noon looking up, plus once at golden hour facing the sun. Also send the FPS.

## 0.5.3-water.2: shore line

- Look at a sandy beach, a dirt bank and a stone wall in the water, at noon and at sunset. A thin, light line should follow every edge where water meets a block.
- Up close it should be about one pixel line, and far away it should neither vanish nor flicker.
- Shallow sand under the water must not get a line. Only blocks that cross the surface do.

## 0.7.1: post-processing

- **Noon over a forest or a lake:**
  - Distant hills should fade into pale blue-white air while nearby blocks keep full colour.
  - The sky should be paler, with a bright horizon.
  - Clouds should be white, not blue-white.
- **Looking down from a mountain:** the valley below should stay clear. Only far distances should haze.
- **Golden hour toward the sun:** the haze should be warm, not grey.
- **Rain:** check it is not too white or misty. "Distance haze" in F7 → Fog can be lowered.
- **Settings:** an edited colour or another look must keep its values after the update.
- **FPS:** the haze costs a few instructions per pixel in the existing composite pass, with no new pass.

## 0.7.2: leaves

- **Forest in sun:**
  - crowns should show a bright, lively yellow-green on the sun side and darker insides;
  - leaves seen against the sun should glow green;
  - nothing should look emissive in the shade.
- **Night:** leaves must not glow, apart from faint moonlight.
- **Mobs and chests:** unchanged, because they don't use the leaf path.

## 0.7.3: TAA

- **Standing still:**
  - leaf crowns should look solid and smooth while keeping their texture;
  - block edges should have no stair-steps;
  - distant forest should not shimmer.
- **Moving and turning fast:** look for trails or ghosts behind mobs, the hand, or tree edges against the sky.
  Report any with a screenshot.
- **Water:** the vanilla lines should stay visible.
- **FPS:** compare F8 with TAA on and off.

## 0.7.4

- **With Seraphic and LabPBR on:**
  - grass, stone and paths should keep their colour (no white film);
  - shallow water should show its bed without pale rectangles;
  - polished blocks should still shine.
- **Under soft clouds:** the land should stay lit and colourful, with gentle cloud shadows.
