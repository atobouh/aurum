# Lighting, fog and shafts: source review and matched captures

Reviewed 23 October 2026. Implemented on `codex` for Aurum 0.5.9.

## Sources read

The reference trees were inspected directly. The implementation uses general rendering techniques and
Aurum's existing passes; it does not incorporate reference shader code or assets.

- Photon: `shaders/include/lighting/diffuse_lighting.glsl`,
  `lighting/colors/light_color.glsl`, `fog/overworld/analytic.glsl`,
  `fog/overworld/raymarched.glsl`, and `post_processing/tonemap_operators.glsl`, relative to `include/`.
  Source: <https://github.com/sixthsurge/photon>.
- Complementary Reimagined: `shaders/lib/colors/lightAndAmbientColors.glsl`, `colors/skyColors.glsl`,
  `lighting/mainLighting.glsl`, `atmospherics/fog/mainFog.glsl`, and
  `atmospherics/volumetricLight/volumetricLight.glsl`.
  Source: <https://github.com/ComplementaryDevelopment/ComplementaryReimagined>.

Photon explicitly separates direct light from sky fill, uses directional diffuse shaping and controlled
subsurface response, and integrates scattering with transmittance along the ray. Complementary coordinates
time-dependent surface, sky and shaft colours, traces shadows along the air path, and adjusts atmospheric
fog with distance and altitude. Their full pipelines contain considerably more functionality than this patch.

## Findings and resulting changes

1. Aurum's shadow strength faded between sun-direction Y values 0.08 and 0.20. At Y=0.10 it retained only
   about 7.4% of the map. The fade now ends at 0.08, retaining low-sun shadows; the virtual shadow direction
   clamp changes from Y=0.17 to 0.12, allowing roughly eight-object-height shadows while bounding their length.
2. Low-angle Lambert shading and broad sky fill compressed the difference between lit rough ground and
   shadow. The golden-hour light now has modestly stronger direct illumination and reduced sky fill, plus
   a restrained diffuse wrap on solid surfaces. Noon retains the original solid-surface cosine response.
3. Quadratically distributed volumetric samples were averaged equally. This overrepresented nearby segments.
   Each of the same ten taps now represents its segment length and front-to-back attenuation. No new shadow
   texture samples or render passes were added.
4. Fog and volumetric light were independent overlays, with the latter added to gamma-encoded scene colour.
   They now share one scattering source and opacity, with a common forward phase response. Composition uses
   display-linear light. Cave cover darkens ambient scattering while allowing directly lit openings to remain
   visible. This is a compatibility approximation, not a migration of the scene to full HDR.
5. Water's broad normal layers are restrained further so distant reflected silhouettes deform less; the
   separate fine glitter remains. The periodic coordinate corrections from 0.5.8 remain in place.
6. The depth-only terrain path discarded every small plant. Grass, flowers and crops now cast only near the
   shadow-map centre: eight blocks with 1024-pixel maps, sixteen blocks with larger maps. Distant small casters
   remain skipped. These shadows use the cached geometry pose and do not animate with every wind movement.

## Matched reference procedure

A separate Fabric client was created outside the repository, using Iris 1.7.6 and Sodium 0.5.13 on Minecraft
1.20.1. A copy of the Aurum test world and its options was used. All captures are 1280×720, default 70° FOV,
12-chunk render distance, gamma 0.5, and vanilla textures. Weather and day cycle were held constant.
The world seed is `-5138383364446607098`. Full Aurum settings and camera inputs are saved in
[`aurum-0.5.9-capture-manifest.json`](../reports/aurum-0.5.9-capture-manifest.json).

| Scene | Position | Yaw / pitch | World time |
| --- | --- | --- | --- |
| Village sunset | -219.5, 70, 35.5 | 90 / 8 | 12000 |
| Village noon | -219.5, 70, 35.5 | 90 / 8 | 6000 |
| Lake sunset | -191.5, 64, 35.5 | 90 / 8 | 12000 |
| Open ocean sunset | -1056.5, 67, 899.5 | 90 / 10 | 12000 |

Complementary used the source snapshot's HIGH profile without source changes. Photon used its high profile
with `SH_SKYLIGHT` disabled: the default compute program requested 36,860 bytes of shared memory against
llvmpipe's 32,768-byte limit. Photon source otherwise remained unchanged. These are source snapshots, not
claimed versioned releases. Pack-specific sun-path settings, clouds, animation and mobs differ. The comparisons
control terrain and camera framing, not every lighting input.

Pack SHA-256 values used for the reference client:

- Photon with SH skylight disabled: `30df7e0b1c3ae6d5498ba128f1db12ab9b00e8ae27b52bfa420a715332e1a162`
- Complementary source pack: `53729e87442ecabea3acf16d418014f11beda86c71cc8edd21d16551a5db68f6`

## Verification and limits

- Gradle builds and GLSL validation of terrain, composite and volumetric shaders pass.
- All 14 Aurum shader programs compile in the running Minecraft client on Mesa llvmpipe OpenGL 3.3.
- A half-lit, 60-block homogeneous ray has an analytic lit fraction of 0.500 without extinction. The new
  ten-tap integration gives 0.510; the previous unweighted calculation gave 0.300. With extinction 0.015 per
  block, the analytic result is 0.389 and the new approximation gives 0.399.
- The screenshots still show a gap in atmospheric richness, reflected detail, foliage light response and
  shaft definition. Full HDR composition, multiple scattering, a complete reflection environment and robust
  motion reconstruction remain architectural work.
- No Intel UHD GPU is available here. llvmpipe images and shader compilation do not establish UHD performance,
  native 1080p motion quality or reference parity.
