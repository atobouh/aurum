# Photon → Aurum pass (0.8.8–0.8.9)

The user decided that Photon is the reference for everything except water, and asked for one complete pass
before the next review.

- Source: Photon Shaders by SixthSurge, github.com/sixthsurge/photon.
- Licence: modification and redistribution of portions are allowed, except on monetised platforms (Modrinth,
  CurseForge) without written permission, and never for money. Aurum ships it in
  `assets/aurum/licenses/PHOTON_LICENSE.txt`.

| Photon module | What Aurum took | Where |
| --- | --- | --- |
| `post_processing/tonemap_operators.glsl`, `c19_color_grading` | Lottes tonemap (contrast 1.5, shoulder 0.91, white 8, mid 0.26 → 0.32), the output gain S-curve (1.05), and the Rec. 2020 working space for the per-channel curve; neutral grade | `include/tonemap.glsl`, config v14 |
| `fog/overworld/analytic.glsl`, `weather/fog.glsl` | Analytic two-medium air fog. Rayleigh: 0.0005 × (0.31, 0.67, 1), half-life 30 above sea + 30. Mie: 0.0001 at noon to 0.006 at golden hour, half-life 7 above sea + 7. Four-order multiple scattering with HG 0.7/0.3 lobes; the scene is attenuated and the scattered light added | `post/composite.fsh` |
| `lighting/diffuse_lighting.glsl` | Full sky fill in shade (0.8.5's extra darkening removed); bounced sunlight into shadow (0.033π × (1 − shadow) × sky⁴); lift(NoL, 0.25); leaf translucency takes the albedo once, not squared | `include/surface_light.glsl`, `include/lighting.glsl` |
| `sky/blocky_clouds.glsl` | Studied only. It is a volumetric ray march with eight-order scattering and temporal upscaling, too costly for the UHD 620 target. Aurum's blocky clouds instead get about one pixel of edge smoothing and less distance fade, against the user's "blurry" note | `post/clouds.fsh` |
| Sunset sky (follow-on fix) | The per-channel curve turned a bright orange sky peach-white, so the low golden sky toward the sun is darker (gain 0.42) and stays saturated orange | `include/sky_lut.glsl` |

## Not taken

- **Volumetric clouds and the realistic cloud system:** performance.
- **Photon's water:** the user keeps Aurum's water.
- **Rain fog:** Aurum's FogDirector still handles rain and caves.
