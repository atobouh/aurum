# Research: look presets inspired by popular shader packs

Requested on 2026-10-22: "Each popular shader has a cool thing in terms of tones, colours and effects... a
preset guide... select the four most popular... we auto-tune tone, colouring and steps toward that shader. We are
not recreating the whole thing, we are tuning our mod to the matrix of that shader."

## Method

- We measured the official Modrinth gallery screenshots of Bliss, Photon, Complementary Reimagined and BSL with
  `scratchpad/tools/look.py`. No pack code was read.
- The metrics are lightness (mean, spread, darkest and brightest 5 %), saturation, warmth (mean red minus blue),
  and the average colour of the sky band.
- The shade and highlight tints from the same script were too skewed by green foliage in forest shots to use.
  They are read visually instead.

## Each pack's signature

| Pack | Daylight L | Daylight warmth | Saturation | Sky | Golden hour / sunset | Signature effects |
| --- | --- | --- | --- | --- | --- | --- |
| Bliss | 0.55-0.59 | 0.00 | 0.18-0.26 (lowest) | near-white / grey (170-213 on every channel) | soft, hazy | Heavy atmosphere and haze, big soft clouds, cinematic and airy |
| Photon | 0.52-0.59 | -0.02 to +0.05 | 0.23-0.37 | clean pale blue (149-182, blue highest) | extreme: warmth +0.49, saturation 0.75 | Clarity, crisp image, clear water, very warm sunsets |
| Complementary | 0.47-0.60 | +0.08 to +0.19 (warm even at noon) | 0.30-0.35 | lavender-blue by day, pink-lavender at golden hour | warm pink-lavender, +0.16 to +0.19 | Blocky lit clouds, warm amber shade |
| BSL | 0.37-0.48 (darkest) | -0.03 to -0.07 (cool) | 0.20-0.29 by day, 0.55 at sunset | deep saturated blue (114-126, 134-149, 164-179) | deep orange, +0.30 | Strong bloom and god rays, classic vivid look |

## Mapping onto Aurum

Four new look controls, in F7 > Colour grade:

- **Temperature** (-50 to 50): a camera-style warm or cool shift in the white balance.
- **Shade warmth** (-50 to 50): the tint of sky light in the shade.
- **Sky saturation** (50-160 %): from a pale, near-white sky to a deep, rich one. It applies to the sky, fog and
  cloud light, and to water reflections.
- **Sky tint** (-50 to 50): from teal to lavender-pink.

| Look | What it changes |
| --- | --- |
| **Aurum (golden hour)** | Defaults |
| **Airy (Bliss-inspired)** | Exposure 108, saturation 78, sky saturation 62, fog 170, bloom 140, rays 130, soft clouds at 66 % coverage, sunset 80, AO 65 |
| **Clear (Photon-inspired)** | Exposure 104, saturation 96, contrast 108, temperature -6, sky saturation 92 with a slight teal tint, sunset 145, horizon 135, fog 75, bloom 90, soft clouds at 45 % |
| **Warm (Complementary-inspired)** | Exposure 106, vibrance 108, temperature +14, shade warmth +22, sky tint +30 (lavender), sunset 115, blocky clouds at 55 % |
| **Vivid (BSL-inspired)** | Exposure 92, saturation 108, vibrance 118, temperature -10, shade warmth -12, sky saturation 128, sky tint -14, sunset 130, bloom 150, rays 150, blocky clouds |

**Rules for the presets:**

- A look preset only touches look values: colour, tone, atmosphere, clouds and effect strengths. Quality
  settings (shadows, resolution, reflections) are untouched, so every look works on Low.
- Switching looks starts from Aurum's defaults, so presets never stack.
- Editing any look value by hand marks the look as "Custom".
- Names describe the style; the pack names appear only as "inspired by".
