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
