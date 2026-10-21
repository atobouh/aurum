# Testing: Phase 1 (Golden hour core)

What's new: Aurum's own sky (sunrise, sunset, blue noon, simple night), sun and sky lighting on blocks,
generated normals (bump detail), fog that appears at dawn/dusk, in rain, caves and swamps, the Badlands
"bounce glow", the AgX Punchy colour grade, and **waving plants and leaves**.

Test like last time: **on battery, Balanced power mode**, render distance 10, your normal window. That way the
numbers compare directly with your Phase 0 baseline (`docs/BASELINE.md`).

## Install

Replace the Phase 0 jar with `aurum-0.1.0-phase1.jar`. Keep Sodium 0.5.13. If **Windblown** is installed,
remove it (it edits the same shader; Aurum has its own waving plants).

## Quick checks

1. The game starts. No `[Aurum]` warning appears in chat when you join a world.
2. The sky looks different from vanilla: a round sun instead of a square one.
3. Press **F7**. The status line at the bottom says `Pipeline: active, shaders compiled: 4`.
4. Grass, flowers and leaves move gently. Gusts should roll across a field in waves.
5. Hold an item: your hand and item are drawn normally (not fogged, not missing).
6. Go underwater and to the Nether: they should look like vanilla (Aurum only changes the overworld for now).

## Scenes (screenshot each with F3 + F8 open, stand still 5 seconds)

Use `/gamerule doDaylightCycle false` so the time stays put.

| Scene | Commands | What I want to see |
| --- | --- | --- |
| Plains, sunrise, facing east | `/locate biome minecraft:plains`, teleport, `/time set 23300`, face east | Orange-gold horizon, warm light on blocks |
| Badlands, sunset, facing west | `/locate biome minecraft:badlands`, teleport, `/time set 12450`, face west | Deep orange sky, warm haze, glowing terracotta |
| Ocean, noon | `/locate biome minecraft:ocean`, `/time set 6000` | Clean blue sky, nothing grey or orange at the horizon |
| Forest, dusk | `/locate biome minecraft:forest`, `/time set 13000` | Darkening blue, sensible brightness under the trees |
| A cave | Any cave | Dark, readable, no sky-coloured fog inside |
| Night (bonus) | `/time set 18000` | Dark navy sky, stars, moon, dim but visible ground |
| Rain (bonus) | `/weather rain` | Grey sky, more fog, plants move more |

## Numbers to send back

From each screenshot:
- **FPS**
- Overlay **avg** for: Frame (GPU), World, Sky, Terrain, Translucent, Hand, **Aurum post**, HUD

Phase 0 baseline for comparison (same savanna sunset spot): FPS 52, Frame 13.08, Terrain 10.42, Sky 0.30.

## Things to tell me about the look

- Sunrise/sunset: too orange, not orange enough, or right?
- Noon: is the sky blue enough? Is the grass too bright?
- Plants: too much movement, too little, too fast? (sliders: F7 > Plants & wind)
- Fog: too much, too little, or does it ever "pop"?
- Anything that looks broken: black blocks, flickering, stripes, missing faces.

## Sliders worth trying

F7 > Colour grade (Exposure, Saturation, Vibrance, Contrast, Tonemap), F7 > Sky (Sunset intensity, Horizon
warmth), F7 > Fog (strength), F7 > Lighting (Sun, Sky ambient, Normal strength, AO boost).

## Known limits in this phase

- No shadows yet (Phase 2). Sunlit and shaded sides of blocks differ, but trees don't cast shadows.
- Water is still vanilla water with Aurum lighting (Phase 3). Clouds are vanilla (Phase 4).
- I can only run the game with software rendering, so I have checked that everything works and roughly
  how it looks, but not the FPS. Your numbers decide whether Phase 1 passes.
