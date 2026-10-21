# Testing: Phase 4 (Sky extras)

What's new in 0.3.0:

- **Clouds in the Story Mode style.** They replace vanilla's clouds.
  - Your resource pack's cloud layout is kept, but shown as soft, rounded, puffy slabs.
  - A second, higher layer adds depth.
  - The colours follow the sky: lavender-white at noon, peach and pink at sunset, grey in rain.
  - They are lit from the sun's side and glow at the edges when the sun is behind them.
  - You can fly above them.
- **God rays.** Warm light shafts come through trees, terrain edges and gaps between clouds. They are strongest
  at golden hour and faint at noon.
- **Bloom.** The sun gets a big, soft glow, as in the reference packs (measured; see
  `docs/research/SKY_EXTRAS.md`). Lava and glowstone glow a little. It is now on in **every** preset, Low
  included.
- **Lens flare.** It is subtle: a warm halo, a short streak and faint coloured ghosts. It fades when trees,
  mountains or clouds cover the sun.

Test like before: **battery, Balanced**, render distance 10, your normal window.

## Checks

1. **Golden hour facing the sun** (`/time set 12100`, look west):
   - Is the sun glow closer to the reference packs now?
   - Is it too strong anywhere?
2. **Trees between you and a low sun:**
   - Do you see light shafts?
   - Do they move cleanly when you turn, or do they flicker or look grainy?
3. **Walk behind a tree or hill while facing the sun.** The flare should fade out smoothly, not pop.
4. **Clouds:**
   - Look at them while standing still for 20 seconds. Do the edges shimmer or crawl? Moving slowly is fine.
     Flickering is not.
   - Fly up through them (y 200+) and look down.
5. **Rain** (`/weather rain`): the clouds should thicken and turn grey.
6. **Vanilla Video Settings > Clouds: Off.** All clouds should disappear. Turn it back on and they return.
7. **A cloud resource pack, if you use one.** Its shapes should show in Aurum's style.

## Numbers to send back

From the overlay (F8), avg column: **Frame (GPU)**, **Aurum post (all passes)**, and under it **Clouds** and
**Rays, flare, bloom**, plus the FPS. Take them at:

- noon looking up at clouds,
- golden hour facing the sun,
- noon looking down at the ground (the clouds and rays should drop to almost 0 there).

Targets on your UHD 620: Clouds 1 ms or less; Rays, flare and bloom 0.5 ms or less. If one is over, tell me
the number and I'll make it cheaper.

## Settings to try

- **F7 > Flare, rays & clouds:** flare and god ray on/off and strength, Aurum clouds, cloud coverage (50% =
  vanilla), cloud shading.
- **F7 > Glow & bloom:** bloom on/off and strength.
