# Baseline: Phase 0 on the user's machine

Measured 2026-10-21 by the user. HP ProBook 640 G5, Intel UHD 620, about 200 mods, Sodium 0.5.13.
**On battery, Windows "Balanced" power mode**: the user's deliberate worst case. Plugged in on max
performance they get 60+ FPS. Compare phases in the same power mode.

Window 925x530 (0.49 MP; 1080p is 4.2x the pixels). Render distance 10. Aurum draws nothing yet.

| Scene | Preset | FPS | Frame GPU | World | Sky | Terrain | Translucent | Other | HUD |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Savanna/river, sunset (sun +3.4°) | Low | 52 | 13.08 | 12.56 | 0.30 | 10.42 | 1.58 | 0.24 | 0.33 |
| Same spot (sun +9.8°) | High | 46 | 14.39 | 13.84 | 0.29 | 11.39 | 1.73 | 0.42 | 0.36 |

Terrain is solid and cutout together (Sodium draws both in one call). Clouds read 0.00 (clouds off in the
user's settings). Hand reads 0.00; it costs nothing measurable.

## What it means

- **GPU is not the bottleneck here.** 52 FPS is 19.2 ms per frame, but the GPU only works 13 ms of it. The
  remaining ~6 ms is CPU (mods, battery clocks). Aurum's GPU passes can use some of that idle time before
  they cost FPS.
- **Terrain is 80% of the GPU time.** Anything added to Sodium's terrain fragment shader is multiplied by
  the biggest item in the budget. Phase 1 terrain lighting must stay a handful of instructions plus one
  texture read; heavier work goes into the single post pass, which costs per screen pixel and is small at
  this window size.
- Sky costs 0.3 ms, so replacing it with a lookup-texture sky stays within the spec's 0.3 ms target.
- Preset Low and High read the same here (nothing differs yet); the gap is run-to-run noise.
