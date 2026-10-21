# Research: water, wet ground and puddles (Phase 3)

Done 2026-10-21 while the user tested Phase 2. Method: the official Modrinth galleries of the most-downloaded
packs (Complementary Reimagined 72M, BSL 32M, Photon 29M, Solas 19M, Bliss 16M, MakeUp Ultra Fast 14M,
Sildur's Vibrant) were downloaded at full resolution and studied for the look only (no code read). A labelled
reference board was shared with the user privately; the images are the pack authors' and are not in this repo.

## What the screenshots show

| Pack | Water look |
| --- | --- |
| **BSL** | **Clear water.** The riverbed (gravel, clay, sand) is visible through it, darkening with depth. Reflections only at shallow angles. This is the single biggest difference from vanilla's flat opaque blue. |
| **MakeUp Ultra Fast** (built for Intel iGPUs) | Dark, slightly reflective lakes; sky mirrored; at night a moon glint path. Proves a good look is affordable on our class of GPU. |
| **Photon** | Teal, very clear shallows over sand; a field of tiny **sun glitter** sparkles on open sea; strong warm sunset reflections. |
| **Complementary** | Near-still, **mirror-like** water: trees, hills and the sun reflected perfectly (screen-space reflections). This is what makes its sunset shots look so expensive. |
| All | Water is never bright cyan. It's dark-to-mid, with colour coming from what's under it and what it reflects. Waves are small; calm water reads as premium. |

Clouds seen along the way (for Phase 4): Complementary's **soft, lit blocky clouds** (pink-lavender at sunset,
white with soft grey bellies at noon) are close to the "pretty, layered, not forced realism" look the user wants.

## Technique (public sources, no pack code)

- **Absorption by depth (Beer-Lambert):** light fades exponentially with the water column it crosses
  ([ocean rendering survey](https://arxiv.org/pdf/1109.6494)). Water column = scene depth behind the surface minus
  the surface depth. Shallow = nearly transparent; deep = dark teal-blue.
- **Shoreline foam:** from the same depth difference: a soft band where the column is very thin, broken up with noise.
- **Fresnel (Schlick):** dark looking straight down, mirror-like at grazing angles. Reflection weight follows it.
- **Sun glint:** a sharp specular lobe on the rippled normal; many small normal variations give Photon's glitter.
- **Puddles:** upward, sky-exposed surfaces darken and get glossy in rain, puddles form in a noise pattern, and
  evaporate over ~4 minutes; rain-drop ripple rings distort the reflection (pack descriptions:
  [DarkFantasy Visuals](https://www.curseforge.com/minecraft/shaders/dark-fantasy-visuals),
  [Rain and Water mod](https://www.curseforge.com/minecraft/mc-mods/rain-and-water),
  [Godot puddle shader](https://godotshaders.com/?p=11585)).
- **SSR cost:** half-resolution tracing and a low step count are the main levers; it's called expensive and not
  for mobile ([Unity docs](https://docs.unity3d.com/2018.3/Documentation/Manual/PostProcessing-ScreenSpaceReflection.html),
  [GameDev.net](https://gamedev.net/forums/topic/703353-screen-space-reflections-faster-than-projective-reflections)).

## Proposed Phase 3 design for Aurum

1. **Clear water.** Copy the depth buffer once before Sodium's translucent pass (one blit). In the water fragment
   shader: water column -> Beer-Lambert colour and alpha, so shallows show the bed and deep water goes dark teal.
   Biome water tint kept, but subtly. Water sprites get a material id in the existing atlas (no new data path).
2. **Gentle surface.** Two slow, crossing ripple normal maps baked at startup (no per-pixel noise maths). Tiny real
   vertex waves near the player on Medium/High only.
3. **Reflections: the real sky + sun glint + Fresnel.** Our sky table evaluated in the reflected direction gives
   the real sunset and (later) clouds in the water. Shadows already reach water; they will also kill the glint.
4. **Foam** along shores and around blocks from the water column.
5. **Underwater:** clearer than vanilla, blue-green tinted fog, warmer near the surface.
6. **Wet ground + puddles:** wetness rises in rain and dries over minutes (CPU timer). Sky-exposed upward faces
   darken and gain gloss; puddles from a noise mask grow with wetness and reflect the sky; small ripple rings in rain.
7. **Material sheen:** a small table (sand, red sand, terracotta, polished stones, ice, glass): soft highlight at low sun.

## Open decision for the user: terrain reflections in water (SSR)

The spec lists screen-space reflections as out of scope, so Aurum's water would reflect the sky and sun but
**not** trees, hills or buildings. The reference shots show that reflected terrain is a big part of the premium look
(Complementary's sunset lakes). A cheap version is possible: water pixels only, half resolution, ~12-16 steps,
sky fallback where the ray misses. Estimated 0.5-1.5 ms on the UHD 620 at the user's window, Medium/High only,
Low keeps sky-only. Needs the user's go-ahead because it reverses a spec decision.
