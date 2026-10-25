# Research: post-processing and tone (0.7.1)

User request, 2026-10-25: the "premium feel" that Photon and Nostalgia 5.0 share, with Nostalgia as the realistic
target for our performance budget. Post-processing and tone come before the cloud rework.

## References

There are nine user-supplied gallery shots, cropped and stored in the scratchpad as `refs_pp/`:

- Photon: canopy, autumn lake, forest toward the sun, jungle lake, golden meadow.
- Nostalgia 5.0: lake, block clouds, forest toward the sun.
- One dark mangrove shot.

We measured them with `tools/look.py` and read no pack code.

| Metric | References (daylight) | Aurum 0.7.0 (merged) | Aurum 0.7.1 |
| --- | --- | --- | --- |
| Saturation | 0.22–0.41 | 0.40–0.51 | 0.18–0.29 |
| Warmth (R−B) | −0.13 to +0.06 | −0.14 to −0.28 | −0.04 to −0.14 |
| Mean lightness | 0.41–0.56 | 0.39–0.55 | 0.44–0.64 |
| Sky band | 121–183 per channel, pale | blue near 200–237 | 144–190 / 151–242 |

## What makes the "premium" look, and what we did

1. **Calm colour.** Saturation is moderate and contrast comes from light and shadow, not chroma.
   - Saturation 100 → 88, vibrance 108 → 100, contrast 105 → 110.
2. **Neutral-warm light.** Highlights and clouds are white, not blue.
   - Temperature 0 → +12.
   - Exposure 100 → 110, because the references are brighter.
3. **Pale sky with a bright horizon.**
   - Sky saturation 100 → 70.
   - Codex's "sky clarity" and "sky temperature" settings (75 / −10) are set to 0 by default. Clarity pushed the
     low sky toward saturated blue, which is the opposite of the references.
4. **Distance haze (aerial perspective).** This is the main source of depth in the references: hills 150 blocks
   away read as pale blue-white. Daytime fog was about 2 % at 150 blocks.
   - A new layer in the composite fades distant land toward the bright horizon air.
   - It starts after 32 blocks, with density 0.0026 per block at "Distance haze" 100.
   - Density is averaged along the ray with a 50-block height falloff, so looking down from a hill stays clear.
   - It is warm toward a low sun, never more than 70 % (45 % at golden hour), and off in caves and under cover.
   - The fog that was locked earlier is unchanged.
5. **Soft glow.** The bloom threshold dropped from 0.85 / 0.10 to 0.77 / 0.14 (display scale), so bright sky and
   sunlit clouds glow gently.
   - 0.66 burned the horizon to white and was rejected.

## Not post-processing (left for later stages)

- **Foliage lighting.** The references have sunlit leaf tops and deep interiors; ours is flat mid-green. That
  belongs to the lighting stage, which is Codex's.
- **Cloud shading and layering.** This is the next stage.
- **Rain.** It is now brighter and mistier. Watch it in the user's game.

## Iterations

All iterations were rendered in the same scenes, stored in the scratchpad as `pp0` … `pp7` and `ppF`.

| Run | Change |
| --- | --- |
| pp1 | grade |
| pp2 | sky saturation |
| pp3 | aerial haze |
| pp4 | warmer and brighter |
| pp5 | height-integrated haze |
| pp6 | bloom at 0.66 (too strong) |
| pp7 | bloom at 0.77 |
| ppF | migrated original config |
