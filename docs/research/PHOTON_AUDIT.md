# Photon comparison: sky and water

Inspected 2026-10-23 on branch `codex`. Aurum: `fa1b5053d830e4d604945c252f8a7cccb9d7e3f5`.
Request: Photon is the beauty reference, especially sky and water; preserve Aurum's performance goal.

## Evidence and limits

Read [Photon's official source](https://github.com/sixthsurge/photon), main observed at
`e4013c392a0413188af3f3af3da54275dd424835`, and visually inspected its official Modrinth gallery
images 0, 3, 4, 8 and 9 (credited to Ruri, listed without a resource pack), plus its README rainbow image.
Gallery settings, game versions, camera zoom and worlds are not controlled. Current source does not necessarily
reproduce older gallery images. Images remain outside this repository.

This is a source audit and reference-image study, not a matched in-game comparison or GPU benchmark.
Earlier research claims about Aurum's appearance and timings are historical reports, not newly verified results.
No rendering code changed during this inspection. Photon permits source examination and learning; recommendations
below describe techniques for an original implementation, without importing its code or assets.

## What the reference actually shows

- **Sky, image 0:** orange/yellow light near the sun transitions into peach and muted cooler upper sky.
  Small distant cloud bands coexist with much larger cloud masses. Lit edges and darker interiors give volume.
- **Daytime clouds, image 8:** rounded irregular puffs, varied sizes, clear gaps and distinct bands of depth.
  Warm-white highlights and cool grey interiors create shape without making the whole sky bright white.
- **Water, image 3:** the bed remains legible through blue-teal water. The sun reflection is a dense field of
  irregular bright streaks, not a regular dot pattern or one smooth painted highlight.
- **Sunset, image 4:** dark foreground silhouettes contrast with the luminous sky and its warm reflection.
  The composition includes a very large apparent sun; this should not become a size target without matching FOV.
- **Night, image 9:** restrained dark water carries local light reflections. Aurum need not reproduce the aurora
  to achieve coherent night water.

## Confirmed implementation differences

| Area | Aurum now | Photon source | Implication |
| --- | --- | --- | --- |
| Atmospheric scattering | CPU single scattering: 16 view / 6 light samples; artistic palette corrections | Precomputed scattering LUT including multiple scattering, separate Mie contribution | Aurum has a useful cheap LUT architecture, but added colour shifts do not supply missing scattering behaviour |
| Sunset palette | `SkyLut` blends upper-sky chroma toward fixed peach/lavender ratios; `Atmosphere` separately warms sun and ambient light | Atmosphere LUT, directional phase functions, weather and later colour processing | Coordinate sky, cloud light and surface light before increasing sunset saturation |
| Clouds | Soft style: 18 view steps through warped 2D layout and height-dependent density; light attenuation from depth below local top | 3D Worley detail, directional light optical-depth samples, multiple-scattering approximation, several cloud families | Aurum's top-depth proxy cannot reproduce detailed side lighting and self-shadowing at low sun |
| Cloud reconstruction | Spatial tent filter for soft clouds; optional full resolution | Dedicated temporal reprojection/upscaling, accounting for cloud motion | Higher resolution alone does not solve volumetric stability; temporal reconstruction is a separate engineering task |
| Reflected sky | Water samples atmosphere LUT; direct sun handled by a separate glint; clouds composed later | Omnidirectional sky map containing sky/cloud content for reflections and lighting | Aurum cannot reflect its clouds through this path; adding them to a shared environment representation is a high-value change |
| Water waves | Three scrolling baked slope layers, plus two separate fine glint layers; amplitude fades with distance | Directional multi-frequency Gerstner height function, spatial amplitude variation, flow handling and grazing-angle normal damping | Aurum can retain baked textures while improving coherent motion and avoiding repetitive glitter |
| Water highlights | Sum of three high-power cosine lobes; glint tonemapped separately and added to already mapped water components | GGX/Smith specular response with a finite sun-disc approximation | Aurum's highlight energy and shape need review, rather than just higher sparkle intensity |
| Water colour | Beer-Lambert absorption with base coefficients `(0.80, 0.24, 0.16)`, biome modifier, fixed shallow tint and deep-colour blend | Base absorption `(0.39, 0.14, 0.07)` transformed to working colour space, biome adjustment and scattering integral | Aurum's base attenuation is stronger; calibrate visibility by actual depth, not raw coefficient copying across colour spaces |
| Caustics | Crossing height textures, weighted by sun elevation and vertex skylight; no direct shadow-map factor in this term | Water lighting/fog participates in a broader lighting pipeline | Aurum caustics can remain bright in outdoor cast shadows; test and gate direct illumination appropriately |
| Reflections of terrain | 18 growing world-space steps + 4 refinements against opaque depth, edge fade, current opaque colour | Depth tracing, reprojection to prior scene colour, fog treatment and sky fallback | Validate missed hits, false intersections and transitions before spending more ray steps |
| Colour composition | Sky/clouds mapped individually; water mixes mapped components in display-linear space; final fog and grade operate on display output | HDR scene processing followed by exposure and final tone mapping | Aurum loses highlight headroom early. A broader HDR migration may help but is larger than a water-tuning patch |

Aurum files inspected: `sky/SkyModel.java`, `sky/SkyLut.java`, `sky/SkyState.java`,
`director/Atmosphere.java`, `render/SkyExtras.java`, `render/WaterPass.java`, `render/PostPass.java`,
`render/terrain/TerrainUniforms.java`, `config/LookPreset.java`, and sky, cloud, water, composite and tonemap shaders.
Paths are relative to the Java package or shader directory, respectively.

Photon source anchors: `shaders/include/sky/atmosphere.glsl`, `shaders/include/sky/clouds/cumulus.glsl`,
`shaders/program/d0_sky_map.fsh`, `shaders/program/d2_clouds_upscaling.fsh`,
`shaders/include/surface/water_normal.glsl`, `shaders/include/lighting/specular_lighting.glsl`,
`shaders/include/fog/simple_fog.glsl`, `shaders/program/c19_color_grading.fsh`.

## Recommended implementation order

1. **Establish fixed comparison scenes.** Same seed, position, FOV, resource pack, weather and time in Aurum
   and Photon. Capture noon shallows, sunlit open water, a sunset lake, cloudy sky, and water under a roof or
   cliff shadow. Keep Low/Medium/High results distinct. Record F8 timings on the UHD 620.
2. **Water clarity and highlights.** Calibrate absorption/deep scattering and replace ad hoc highlight energy
   with a controlled water specular response. Keep the baked wave texture; improve motion and filtering only
   where comparisons show a problem. Verify shoreline refraction and caustics in shadow. This can be scoped
   before any large pipeline rewrite.
3. **Shared sky environment.** Prototype a small HDR directional map containing atmosphere and clouds, updated
   at a controlled cadence and sampled by water. Clouds in the reflection must use the same layout and time as
   visible clouds. Measure the map generation cost and check parallax near clouds. Reusing the existing screen
   cloud texture directly is insufficient: reflected directions may be off screen.
4. **Photon-oriented soft clouds.** Keep the optional blocky style. Give soft clouds independent low/high
   structure, more varied coverage, and sun-direction-aware attenuation. Try baked coarse light information
   or a few directional samples before adopting Photon's full cloud march. Add 3D detail only if the budget
   allows it. Do not promise Photon-quality clouds from palette changes alone.
5. **Atmosphere and stability.** Improve the precomputed sky model, reduce fixed lavender forcing, and blend
   LUT transitions (currently the texture swaps while terrain lighting is eased). Evaluate cloud temporal
   reconstruction with camera-cut, disocclusion and weather-change rejection. Consider HDR composition as its
   own measured change if early tone mapping still limits sun/water coherence.

## Performance and acceptance

Retain CPU/precomputed atmosphere work, baked water normals, early exits and quality tiers. Start environment-map
experiments small (for example 128x64), then choose resolution and refresh cadence from measured appearance
and cost. This is an experiment size, not a proven quality setting or performance guarantee.

Accept changes only after checking stationary views and motion: no cloud crawling, sparkle grids, shoreline
halos, sudden reflection holes, sky/cloud mismatch, or caustics shining in cast shadows. Compare image pairs
visually; global brightness and colour statistics alone cannot establish parity.

No FPS or millisecond claims are established by this audit. The existing `CLEAR` look changes colour/settings,
but retains the same rendering algorithms; it cannot by itself close the structural gaps above.
