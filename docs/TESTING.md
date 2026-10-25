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
