# Testing: Phase 2 (Shadows)

What's new in 0.2.0:
- **Sun and moon shadows** from terrain, trees and buildings, soft-edged, sharpest near you, fading out at the
  shadow distance (4 / 5 / 6 chunks on Low / Medium / High).
- **Moving shadows** for you and mobs within 16 blocks (Medium and High; F7 > Shadows > Entity shadows).
  Further mobs keep the vanilla round blob shadow.
- **Lazy shadow updates**: the shadow map is a cache, redrawn only when the sun has moved a little, you walked
  ~4 blocks, or blocks nearby changed, and each redraw is spread over 3 frames.
- **Leaves** cast dappled shadows and glow slightly when shaded. Modded leaf blocks are now detected too.
- **Sunset horizon**: softer, more natural orange glow, no hard red band, terrain no longer washed pink.

Test like before: **battery, Balanced**, render distance 10, your normal window.

## Checks

1. Walk under trees at midday: dappled shadows on the ground, no flickering speckles ("acne") on open ground.
2. Stand next to a wall or tree at golden hour: long shadows; their base should touch the object (no gap).
3. Walk or fly around: shadows should not crawl or pop; if you see a visible "jump" every second or so, tell me.
4. Place and break a block in the sun: its shadow should appear/disappear within a moment.
5. Third person (F5) on open ground: your own shadow (Medium/High).
6. Trees next to your house (custom tree mod): leaves should sway now. After launching, search
   `logs/latest.log` for `treated as leaves` and send me that line.

## Numbers to send back

Same scenes as Phase 1 if you can, at least: open plains at noon, a forest at golden hour, your base.
From the overlay (F8), avg column: **Frame (GPU), Shadow map, Terrain, Aurum post**, and the FPS.

- "Shadow map" is 0.00 most frames (the cache is reused); it rises briefly when the sun moves or you walk.
- Compare FPS with Phase 1 at the same spot: the gate is "within 10%".
- Watch for stutters when walking or flying; tell me if any.

## Settings to try

F7 > Shadows: map size (1024 / 1536 / 2048 / 3072), distance (2 to 8 chunks), softness, entity shadows.
