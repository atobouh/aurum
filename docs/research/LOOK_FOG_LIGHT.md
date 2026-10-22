# Research: fog, sunrise and daylight shadow (screenshot-based, 0.3.4)

Requested by the user after 0.3.3:

- Clouds are locked: "already very good".
- Before water, fix three things: the fog, the sunrise/sunset, and how sunlight and shadow wrap the world in
  everyday play ("the way the shadow embraces the world", as in Bliss).

**Method:**

- We judged the user's own screenshots and our headless renders side by side with Complementary 0, 5, 6 and 11,
  Bliss 1, 5 and 9, Photon 1 and Solas 2, and measured them with `tools/stats.py`.
- The test world is reset (weather, test structures) before every look-lab run. One run was misread because
  rain and a test wall were left in the world.

## Finding 1: our fog veiled everything, starting at the player's feet

| | Contrast | Darkest 5 % | Dark % |
| --- | --- | --- | --- |
| User's 0.3.3 shots (dawn, overcast) | 0.10-0.13 | 0.26-0.32 | 0-1 |
| Complementary 0 / 6 / 11, Bliss 1 / 5 | 0.16-0.30 | 0.12-0.20 | 0-23 |

**Causes:**

- Fog started at 0 blocks: a fence 30 blocks away was about 40% fogged.
- Rain added distance fog of 0.006 per block plus height fog of 0.02 per block hugging terrain near sea level.
- Fog could reach 100%, so silhouettes vanished.
- The fog colour took the golden-hour sky's pink almost unchanged: "pink soup".

**What the references do:**

- Near terrain stays crisp, and haze lives in the distance.
- The haze is luminous: warm toward the sun, cooler away from it.
- Overcast stays bright and clear (Bliss 1).

**Fix (0.3.4):**

- A clear zone of 32 blocks (20 in rain and swamps).
- Densities roughly halved:
  - golden hour 0.0008 per block,
  - rain 0.0025,
  - golden-hour height fog 0.003, rain height fog 0.006.
- Fog capped at 88%.
- Haze 30% desaturated, with its warmth coming from the sunlight scattered toward the sun.

**Result:**

- Dawn facing the sun: contrast 0.28, darks 37%, warmth +0.08 (Complementary 0: 0.30 / 23% / +0.16).
- Golden lab: contrast 0.24-0.30, with no veil.

## Finding 2: daylight was dark, cold-blue and oversaturated

| Daylight | L | Warmth (R-B) | Saturation | Foliage R/G |
| --- | --- | --- | --- | --- |
| Aurum 0.3.3 (forest, plains) | 0.43 | -0.12 to -0.21 | 0.39-0.46 | 0.58 |
| Bliss 5, Complementary 5, Photon 1 | 0.55-0.61 | +0.00 to +0.08 | 0.19-0.30 | 0.91-0.95 |

**Fixes:**

- Exposure +13%.
- A slightly warm sun at every hour.
- Shade takes only 65% of the sky's blue.
- Default saturation 92%.
- A daytime sky grade that is paler and lighter, faded out at golden hour (it washed out sunsets).
- Overcast and rain allowed more exposure.

**Result:**

- Brightness 0.43 to 0.45-0.47, and warmth -0.12 to -0.09.
- Rain at noon: L 0.33 to 0.42.

**The remaining gap is water.** The flat vanilla-blue water fills half of our test frames, while the references'
water mirrors a pale sky. That is Phase 3, next.

## Finding 3: the shadow that "embraces" the world is contact shading

- Bliss 1 and Complementary 5 darken where surfaces meet and under cover: under tree crowns, along walls, in
  crevices.
- Aurum had only vanilla's per-corner ambient occlusion.

**Fix:**

- Screen-space ambient occlusion: 8 hemisphere samples about 1.8 blocks wide, at half resolution, out to 64
  blocks.
- Applied before the haze, with the same edge-aware upscale as the volumetric light, so silhouettes never get
  halos.
- Shaded leaves keep 22% of direct light (was 35%), so tree crowns get darker interiors.

**Result:** forest interior darks 11% to 14%, contrast 0.17 to 0.20, within the references' 0.16-0.28.

Also fixed: the volumetric light near the sun is capped, because it made a hazy blob over nearby trees.
