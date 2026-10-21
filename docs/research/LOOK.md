# Research: why Phase 1.0 looked cheap, and what premium Minecraft visuals do

Trigger: user feedback on 0.1.0 (2026-10-21). "The tone mapping is cheap, not premium. Everything is embraced in
a yellowish colour; the colour grading becomes poor." Same complaint they have about Luxium.

## Measured, not guessed

Sampled from the user's own screenshots (UHD 620, 0.1.0, noon):

| Spot | sRGB | Problem |
| --- | --- | --- |
| Plains grass, mid distance | (48%, 55%, **10%**) | Blue channel almost gone: neon yellow-green |
| Savanna ground | (68%, 53%, **18%**) | Same: everything pushed to yellow |

That is not the texture. Three causes stacked:

1. **AgX hue shift.** AgX applies its curve per RGB channel. As brightness rises, colours drift toward the
   secondaries (yellow, cyan, magenta): the "Notorious 6" effect. Greens drift to yellow. AgX's authors counter it
   with rotation and a preserve-hue control; our implementation had neither.
2. **Extra saturation after the curve** ("Punchy" x1.3) plus vibrance 120 amplified that yellow until blue clipped.
3. **No white balance.** The physical sun is warm (about 6.0 : 5.5 : 4.85 RGB at 60 degrees). Cameras and our eyes
   balance daylight to white; we didn't, so the whole frame carried the sun's tint.

## What the good-looking packs and screenshots have in common

From shader-pack documentation and reviews (vendor claims noted as such):

- **Shadows and ambient occlusion do most of the work.** Soft, filtered sun shadows plus contact darkening in
  corners and under overhangs are named again and again as why screenshots look rich. BSL is a screenshot
  favourite for its shadows, volumetric light and AO. This is Phase 2 for Aurum and is the biggest remaining gap.
- **Restrained, natural grading.** "Natural colour grading and exposure", "restrained bloom". One vendor says the
  stock ACES fit makes mid-grey too bright, which washes things out. None of the praised looks crank saturation.
- **Complementary colour contrast**: warm light against cool shade (orange and teal) reads as vibrant and
  high-contrast without oversaturating anything.
- **Light/shade ratio.** Flatness comes from shade being almost as bright as sunlight. The sun has to clearly
  win over sky light for blocks to read as 3D.

## Changes in 0.1.1

- **Aurum Filmic** (new default): white balance to daylight, then one filmic S-curve on the brightest channel,
  scaled back onto all three. Hue and saturation are preserved exactly; only the very brightest highlights
  roll toward white. AgX stays as an option (without the extra punch).
- **White balance** slider (default 100%): midday light is neutral; golden hour stays warm because the balance
  is fixed to daylight, like a camera set to "daylight".
- **Grade in Oklab**: saturation and vibrance scale chroma in a perceptual space, so they can't shift hue.
  Vibrance default 105 (was 120), contrast 100 (the curve provides it).
- **More 3D**: sky light compensation 2.5x -> 1.8x so the sun clearly wins; AO boost default 30 -> 50.
- **Less haze**: base clear-weather fog halved.
- **Leaves** sway a bit more (0.035 -> 0.05 blocks) so trees visibly breathe; grass unchanged (user: "perfect").
- Existing configs are migrated once (config version 2) to the new grade defaults.

Measured after the change, same garden scene: grass (37%, 58%, 28%) instead of (42%, 63%, 31%) with
a yellow cast in the user's world; blue channel intact.

## Clouds (Phase 4 direction, from the user)

Don't force realism. The user likes stylized, pretty, **layered** clouds (they mentioned Minecraft Story Mode's
clouds). Not necessarily 3D or occluding. Prior art: Nostalgia Shader has a Story-Mode-style cloud option;
Solstice has chunky box clouds with per-face lighting; Kaskada has soft, painterly clouds. Plan: two or three
2D layers with soft, painted edges, lit rims and shaded bellies from the sun direction, parallax between
layers. Research again before Phase 4.

## Sources

- [AgX: after tone mapping (pixls.us)](https://discuss.pixls.us/t/agx-after-tone-mapping/54892/3) and [discussion](https://discuss.pixls.us/t/agx-after-tone-mapping/54892)
- [AgX terminology thread (pixls.us)](https://discuss.pixls.us/t/agx-terminology-ui/53264?page=5)
- ["Color-accurate" tonemapping (pixls.us)](https://discuss.pixls.us/t/color-accurate-tonemapping/44640?page=2), [GraphicsProgramming blog PR on tonemapping](https://github.com/GraphicsProgramming/blog/pull/19/files), [Mantiuk et al., colour correction for tone mapping](https://www.cl.cam.ac.uk/~rkm38/pdfs/mantiuk09cctm.pdf)
- [Exposure: complementary colour grading](https://exposure.software/blog/2020/complimentary-color-grading/)
- [Cinematic Horizon Shaders](https://www.modpackindex.com/mod/175748/cinematic-horizon-shaders), [Sportskeeda: shaders for builders](https://sportskeeda.com/minecraft/10-best-shaders-minecraft-builders)
- [Unity colour grading docs](https://docs.unity3d.com/Packages/com.unity.postprocessing@2.0/manual/Color-Grading.html), [O3DE HDR colour grading](https://docs.o3de.org/docs/user-guide/components/reference/atom/hdr-color-grading/)
- Clouds: [Nostalgia Shader](https://www.curseforge.com/minecraft/shaders/nostalgia-shader/install), [Solstice](https://www.curseforge.com/minecraft/shaders/solstice), [Kaskada](https://www.curseforge.com/minecraft/shaders/kaskada-shaders), [Jooonah's Grove](https://www.curseforge.com/minecraft/shaders/jooonahs-grove/install)
- Oklab: Björn Ottosson, "A perceptual color space for image processing" (2020), public domain matrices.
