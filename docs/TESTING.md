# Testing: Phase 0 (Foundation)

Phase 0 changes **nothing you can see** in the world. It adds the settings screen, the GPU cost overlay, the
Sodium version check and the shader loader. The job of this test is: the game still starts with your full
modpack, and we get a baseline of what vanilla + Sodium costs on your UHD 620 before Aurum draws anything.

## Install

1. Put `aurum-0.0.1-phase0.jar` in your `mods` folder next to Sodium.
2. If Iris, Oculus or Luxium are installed the game will refuse to start and name the conflict. Remove them
   for testing (Aurum replaces the same pipeline).

## Checks

1. **The game launches** with your ~200 mods and reaches the title screen.
2. **Settings open:** press **F7** in game (or Mod Menu > Aurum > Configure). You should see a Preset button,
   an overlay toggle and ten category buttons. Open a few categories and move a slider. Close and reopen the
   game: your changes should still be there (`config/aurum.json`).
3. **Overlay:** press **F8**. A box appears top right with the GPU time of each stage. With F3 open it moves
   to the bottom right.
4. **Vanilla visuals are unchanged:** the world should look exactly as it does without Aurum.
5. **Chat on join:** you should see *no* `[Aurum]` messages. If one appears, copy it into your report.

## Scenes and numbers to send back

Render distance 10, your normal resolution, overlay on (F8). Stand still for 5 seconds, then take a
screenshot with **F3 + F8 both open** (F2) in each scene:

| Scene | What to look for |
| --- | --- |
| Plains at sunrise | Baseline FPS in open terrain |
| Badlands at sunset | Baseline with lots of terrain on screen |
| Ocean at noon | Translucent cost (water) |
| Forest at dusk | Cutout cost (leaves) |
| A cave | Low-sky case |

From each screenshot, the numbers I need are:

- **FPS** (top left of F3, and the FPS line in the overlay)
- Overlay **avg** column for: Frame (GPU), World, Sky, Terrain solid, Terrain cutout, Translucent, Clouds,
  Other, Hand, HUD

Also send:

- The GPU line at the top of the overlay (it should name the UHD 620).
- Your screen resolution and Sodium version (Mod Menu shows it).
- The lines containing `Aurum` from `logs/latest.log` (search for "Aurum").

## Known limits

- The overlay costs nothing while hidden. While shown, it adds ~20 tiny GPU queries per frame.
- If "GPU timers unsupported by this driver" appears, send the GPU line; FPS still works.
- Most settings do nothing yet. Their tooltips say which phase brings them.
