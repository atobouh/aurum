# Water: style proposal (for the user to choose)

Requested by the user on 2026-10-22: "Do the research, make me a proposal, and I will select what style I want,
or even multiple styles, depending on the level."

## Sources

- **Shader packs:** the Modrinth galleries of Complementary, BSL, Photon, Solas, Bliss, MakeUp Ultra Fast,
  Sildur's, Nostalgia, Oak, Earth, Low Water and Cartoon Dreams.
- **Resource packs:** Stylized Liquids, Water Caustics, Water Normals.
- **Mods:** Wakes.

We studied the screenshots for the look only and read no code. The labelled boards were sent to the user
privately and are not stored here, because they are the pack authors' images. Earlier technical notes are in
`WATER.md`.

## Four styles

Every style shares the basics:

- The real sky in the water: our sunset, sun and clouds.
- Fresnel: darker looking straight down, mirror-like at shallow angles.
- Soft foam along shores.
- Calm, slow ripples.
- Shadows on the water.

| | A. Mirror Lake | B. Crystal Clear | C. Storybook | D. Light Classic |
| --- | --- | --- | --- | --- |
| **Look** | Glassy, near-still; trees, hills and builds mirrored; warm sunset paths (Complementary, Sildur's, Photon sunset) | Clear: the riverbed shows through, turquoise shallows fade to deep teal, sun glitter (BSL, Photon, Oak) | Stylised and painted to match our blocky clouds: bold colour bands, pixel-art foam rims, soft stylised reflections (Stylized Liquids, Wakes, Nostalgia) | Dark, glossy, simple; sky, sun and moon paths only (MakeUp, built for Intel) |
| **What is reflected** | Terrain + sky (screen-space reflections) | Sky (+ terrain on Medium and up) | Sky, plus a soft, blurry terrain reflection | Sky only |
| **Transparency** | Moderate | High (depth-based colour) | Low to medium, banded by depth | Low |
| **Signature** | Perfect reflections at golden hour | See-through shallows, glitter | Pixel foam lines, painted waves | Cheapest, still premium |
| **Est. cost on UHD 620** | 1.0-2.0 ms | 0.6-1.2 ms (+SSR 0.8) | 0.5-1.0 ms | 0.3-0.5 ms |
| **Risk** | Screen-space reflection gaps at the screen edge (fall back to sky) | Needs a depth copy before the water pass | Has to look deliberate, not cheap; more iteration | Lowest |

## Add-ons (can be combined with any style)

1. **Caustics:** dancing light patterns on the bed in shallow water, and underwater. About 0.2 ms.
2. **Wakes and ripples:** rings around you, mobs and boats, plus splashes. Needs a small CPU-side ripple
   buffer. About 0.2 ms.
3. **Underwater:** clear blue-green view, light shafts from the surface, a warmer tint near the top.
   About 0.3 ms.
4. **Wind ripples:** gusts roughen the surface in patches, breaking the reflections up (uses the existing wind
   system).
5. **Rain:** ripple rings on water, wet glossy ground and puddles (from `WATER.md`). About 0.3 ms.
6. **Waves:** real vertex waves near the player on big water; the surface actually moves.

## Suggested setup (my recommendation)

- **Style choice in F7** (as with clouds), built in this order: B first (the biggest step up from vanilla and
  the most universal), then A as the "High" look, then D for Low.
- **Default per preset:** Low = D, Medium = B, High = A.
- **C** if you want water that matches the blocky clouds. It's the most "Aurum" option, but the one I'm least
  sure I can get to premium.
- **Add-ons:** 1, 3 and 4 by default; 2 and 5 as options.

## Decision (2026-10-22)

The user picked **B, Crystal Clear**, with **Photon as the reference** ("make it Photon-ish as much as possible").

## Built in 0.4.0

- **Clear water:**
  - The opaque scene (colour and depth) is copied before Sodium's translucent pass.
  - The water shader refracts that copy and tints it by the length of the ray inside the water.
  - Absorption per block is 0.80 / 0.24 / 0.16 (red, green, blue), so the shallows turn teal and deep water goes
    dark teal. The biome water colour nudges the tint.
- **Caustics:** a light network on the bed in the shallows, from two crossing ripple layers.
- **Ripples:** a tiling 128² slope texture baked at startup from 48 integer-frequency waves. It is sampled at 16,
  6 and 2 blocks per tile, scrolling in different directions. The finer layers fade with distance, and far water
  settles.
- **Reflections:**
  - The real sky from our sky table.
  - Fresnel, with a little extra at low angles.
  - Screen-space reflections of terrain (18 steps plus 4 refinement steps, faded at the screen edges) on Medium
    and High. Low reflects the sky only.
- **Sun glint:** a very tight lobe on the fine ripples, which gives the glitter path, plus a soft sheen. Hidden by
  shadows.
- **Shore edge:** a thin, broken bright line where the water is shallowest.

Side by side with Photon 3, 6 and 8 and Sildur's 3, the build matches:
- clear teal shallows with caustics;
- trees and cliffs mirrored;
- a sun path.

Remaining differences from the references:
- Photon's glitter is denser at high sun.
- Its sunset water is more orange where it reflects low sky. Ours reflects the higher lavender sky at the same
  angles, which is physically right but less dramatic.
- Clouds are not reflected yet; the clouds are drawn after the water.

Golden-hour look lab after water: p95 0.95-0.97 (was 0.80), warmth +0.03 to +0.11.
