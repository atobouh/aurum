# Water: one look (Complementary Reimagined direction)

User decision, 2026-10-25: "No two modes, just one mode." The reference is Complementary Reimagined's water, which
keeps the vanilla water texture's lines but adds realistic reflections, corners and edges. The user is "done with
the overly realistic water" (the Photon-style glitter and crystal clarity). Lighting is being reworked on Codex's
branch; this branch changes only the water surface.

## What the reference shows (screenshots only, no code read)

- **Dark, glassy water:** mostly a mirror of the shore (cliffs, trees, the sky), wavering only slightly.
- **The vanilla pixel lines** stay visible as faint light streaks.
- **Moderate clarity:** the bed shows near the shore, and depth goes dark green-teal.
- **No glitter field:** the sun gives one clean reflection.

## How Aurum does it (0.5.3-water.1)

- **Normals from the live water texture:** the current animation frame's luminance gradient, per texel and wrapped
  inside the sprite, tilts the surface gently (strength 0.45). Reflections break along the same lines.
  - A slow, broad swell is added on open water.
  - The pattern fades out between 12 and 48 blocks, so it never shimmers.
- **Texture skin:** only the texels brighter than their neighbours (the lines) are added on top, faintly, plus a
  10 % veil of the texture's colour.
- **Water colour:** the biome water colour, pulled 65 % toward a dark green-teal. Vanilla's saturated blue looked
  plastic next to real reflections.
- **Absorption:** 0.85 / 0.42 / 0.36 (red, green, blue) per block.
- **Reflections:** screen-space terrain reflections, falling back to the sky table, on every preset (config v8). The
  Fresnel term gets a mid-angle lift (+0.35), so the far half of a lake mirrors the shore.
- **Removed:** the Photon glitter, the strong caustics and the bright foam line. A soft shore rim (18 %) remains.

## Matched comparison

Same kind of scenes as Complementary gallery shots 0 and 1 and the user's reference video frame:

- **Matches:** shore reflections, the vanilla lines and the swamp water mood.
- **Still behind:**
  - The reference's mirror is darker and sharper close up.
  - Ours fades toward the sky colour where screen-space reflection finds no hit near the screen edges.
