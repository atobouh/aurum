# Lighting audit (2026-10-25)

Claude took over lighting from Codex at the user's request. This audits the merged system on `main` at 0.7.1.

## Method

- Matched renders (forest canopy, forest in morning sun, under trees, village at night) using Codex's diagnostic
  views: `/aurumdev lighting direct|sky|ao|coverage|block`.
- Foliage metrics compared with the user's reference shots: linear-luminance spread and saturation of sunlit vs
  shaded leaf pixels.

## What works (keep)

- **One lighting function for terrain and ten vanilla model shaders.** These cover entities, block entities,
  armour and held items, so mobs and chests now light like the world. Each shader is fingerprint-checked, with
  a contract test in the build.
- **Removed the camera-dependent "bounce" tint.** It was a screen average that shifted colours as you turned.
- **Cloud shadows, and leaves letting some light through.**
- **Sky light per face direction.** Vertical faces get the sky in their own direction.
- **Debug views and automated checks.** They make lighting work measurable.

## Findings

### 1. Sunlit leaves lack saturation and brightness (the "vibrance" gap)

| | Light spread p90/p10 | Saturation, lit leaves | Saturation, shaded leaves | Lit brightness p90 |
| --- | --- | --- | --- | --- |
| References (Photon, Nostalgia) | 10–32 | 0.35–0.52 (Photon 0.51–0.52) | 0.44–0.71 | 0.17–0.25 |
| Aurum 0.7.1 | 10–28 | 0.31–0.44 | 0.49–0.55 | 0.17–0.20 |

- Contrast between light and shade is already in range. What's missing is colour and brightness where the sun
  hits leaves.
- Causes in the shader:
  - Leaf cubes use a flat wrap: `dot * 0.82 + 0.18` with a raised normal. That lights every leaf face almost the
    same.
  - Light passing through leaves is grey: `max(shadow, 0.04..0.14)`. Real leaves pass and scatter
    yellow-green light (roughly albedo²), and that is where the references' glowing greens come from.
  - The global grade (saturation 88) and the distance haze then take more colour out of mid-distance canopy.

### 2. Voxel torch lighting is almost never visible

- The voxel field is 16 blocks across, and its result is trusted only within about 6 blocks of the camera.
- Everywhere else, vanilla's lightmap drives block light.
- In the night village render, the coverage view is red (fallback) over the whole frame.
- The cost is real even so:
  - a CPU bake with a 1 ms per-frame budget;
  - up to 32 light evaluations per pixel inside the field;
  - eight extra texture bands.
- It needs a decision: widen it, or make the vanilla-lightmap path look good everywhere and keep voxels as a
  close-range extra (proposed).

### 3. Open items from Codex's own report

- Held or moving lights only work through the LambDynamicLights compatibility mode.
- Cave air and darkness are not rebuilt.
- Distant sky gaps in caves have an unknown cause.
- Mobs do not cast shadows from torches.

### 4. Not measured

- No Intel UHD 620 frame times for any of the lighting work.

## Plan

1. **Daylight foliage:**
   - albedo-tinted light through leaves and a warm, scattering lit side;
   - less flat wrap, so crowns get bright sunlit tops and deep interiors.
   - Target: lit-leaf saturation ≥ 0.48 and p90 around 0.21 in matched scenes.
2. **Sun and sky balance:** a slightly warmer, stronger sun and less sky fill by day, retuned with the 0.7.1 grade.
3. **Block light everywhere:** a warm, smooth, direction-aware torch falloff from the lightmap. Then decide what
   the voxel field is for.
4. **Caves:** air, darkness and the gap investigation.
5. **Moving lights:** a proper source adapter.
6. **Performance:** measure on the user's laptop (F8) with voxel lighting on and off.
