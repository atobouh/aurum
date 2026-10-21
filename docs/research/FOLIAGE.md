# Research: waving plants and leaves

Requested by the user on 2026-10-21: "moving grass and stuff, a smooth premium animation". Not in the original
spec; added as feature 12 and built in Phase 1 because it needs the same per-sprite material data.

## What makes plant motion look premium rather than cheap

Cheap: every plant runs the same sine wave, in sync, at constant speed. It reads as a loop.
Premium comes from layering and coherence:

1. **Main bend + detail flutter** (Crytek, GPU Gems 3 ch. 16). The whole plant leans along the wind
   (low frequency, larger amplitude); a second, faster, small wobble rides on top for leaf tremor.
2. **Anchoring.** Only the top of a plant moves; the base stays fixed in the ground (height-scaled bend).
3. **Length preservation.** Crytek renormalises the bent position to the original length so plants arc instead
   of stretching sideways. Cheap version: lower the tip by the square of its sideways offset.
4. **Phase variation by world position** so neighbours are related but not identical
   (`phase = dot(worldPos, k)` in Crytek's listing). No per-plant random pops.
5. **Gusts that travel.** A low-frequency noise field scrolls across the world along the wind direction, so a
   brighter patch of motion rolls over a field like real wind. This is the single most "premium" cue.
   A commercial foliage shader describes the same thing as global turbulence with noise tiling and angle
   spreading, to avoid repetition.
6. **Weather.** Calm in clear weather, stronger in rain, strongest in thunder; transitions over seconds.
   Windblown (below) does the same.
7. **Smooth waves, cheaply.** Crytek uses triangle waves smoothed with `3x^2 - 2x^3` instead of `sin`, four
   at a time, with "good frequencies" 1.975, 0.793, 0.375, 0.193 (not multiples of each other, so the
   sum never visibly repeats).

## Prior art in Minecraft

- Shader packs (BSL, Complementary and others) ship waving plants as a standard toggle, usually two-layer
  sinusoidal sway with top vertices free and bottom anchored, and per-group intensity/speed sliders.
- **Windblown** (Fabric 1.20.1, MIT): grass, ferns, flowers, crops, leaves, vines, kelp and seagrass sway on the
  GPU; gusts roll across the land; rain and thunder strengthen wind; identifies plants "by how they're built",
  so modded plants work without setup; supports Sodium; turns itself off when a shader pack is active.
  **It edits the same Sodium terrain shader Aurum replaces, so the two cannot run together.** Aurum warns if
  it is installed. We did not read its code; the techniques above come from public articles.

## Aurum's design

Inputs available in Sodium 0.5.13's vertex shader (see `docs/SODIUM_NOTES.md`): no block id, no normal, but
the texture coordinate and, per vertex, the sign of the texture-edge bias. That sign tells whether a vertex is
on the top or bottom edge of its sprite, which for upright plant textures is the top or bottom of the plant.

- **Which vertices move:** a material id per sprite, baked at resource reload into the alpha channel of the
  normal atlas (one vertex texture read, only in the cutout pass where plants and leaves live; solid terrain
  pays nothing). Ids come from block classes and tags (flowers, saplings, crops, leaves, vines, tall plants,
  seagrass/kelp) **plus a geometric rule**: a cutout block whose model is made of diagonal quads is a plant.
  The geometric rule is what makes modded plants in a 200-mod pack sway without a list.
- **Motion:** wind direction drifts slowly (CPU). Gust strength comes from a baked, tiling noise texture
  scrolled along the wind (one more vertex texture read). Main bend = lean (gust) + smoothed-triangle
  oscillation; detail flutter on leaves and plant tips. Tip lowered by the square of the offset.
- **Per material:** short plants: top moves, base anchored. Tall plants: lower half's top moves half,
  upper half's bottom moves half and its top fully (the two halves stay joined). Leaves: whole block flutters
  gently, offset depends only on world position so shared corners never crack. Vines: small flutter.
  Water plants: slower, larger sway.
- **Cost limits:** off past ~48 blocks (fades out), only in the cutout program, wind uniforms computed once
  per frame in Java. Strength and speed sliders plus a toggle.

## Sources

- [GPU Gems 3, ch. 16: Vegetation Procedural Animation and Shading in Crysis](https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch16.html)
- [GPU Gems 3, ch. 6: GPU-Generated Procedural Wind Animations for Trees](https://developer.nvidia.com/gpugems/gpugems3/part-i-geometry/chapter-6-gpu-generated-procedural-wind-animations-trees)
- [Autodesk Stingray shader nodes, Animation category](https://help.autodesk.com/cloudhelp/ENU/Stingray-Help/shaders_ref/cat_Animation.html)
- [Fab foliage shader listing (gusts, global turbulence)](https://www.fab.com/listings/1a592c1a-49c8-4a95-bf2e-674b95b239f0)
- [Windblown on CurseForge](https://www.curseforge.com/minecraft/mc-mods/windblown)
