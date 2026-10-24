# Disposable visual fixtures

Install these functions as a datapack only in a disposable world. They replace blocks at the listed
coordinates. The original save is never the fixture target. Minecraft 1.20.1 uses pack format 15.

**Teleport to the fixture first, wait for its chunks to load, then run the function.** A `/fill` issued
before the destination is loaded can fail. After generation and edits, let light/mesh/shadow caches
settle and inspect the actual openings/blockers before capturing. Do not assume a fixed wait proves
readiness. F3+A rebuilds the client meshes when needed.

| Function | Initial teleport | Purpose |
| --- | --- | --- |
| `aurum_lab:beam_hall` | `/tp @s 520.5 203.5 525.5 125 5` | Three sun openings, blockers, then seal the windows for the control |
| `aurum_lab:voxel_room` | `/tp @s 568.5 202 509.5 180 24` | Warm/soul torches, solid cubes and partial slabs |
| `aurum_lab:detail_board` | `/tp @s 610.5 202 512.5 115 4` | Texture detail and a pillar shadow |
| `aurum_lab:cloud_plane` | `/tp @s 864.5 245 944.5 180 40` | Flat grass plane at Y200; raise cloud height to 80 for this fixture |
| `aurum_lab:modded_canopy` | `/tp @s 1026.5 204 1042.5 180 8` | Optional: requires Nature's Spirit 2.2.5. Red/orange maple, yellow aspen and spruce controls |

For the modded canopy at Y200, set cloud height to 160 so the cloud-to-ground separation resembles
ordinary low terrain with height 24. With Fabric Seasons installed, `/season set fall` followed by
`/time add 6000` selects fall at noon; `/time set 6000` would reset the season's absolute time.
Restore cloud height 24 before returning to ordinary terrain.

The beam function finishes at its general overview camera; teleport back to the initial camera for
the matched three-opening view. Keep time, weather, wave/plant motion settings and camera fixed for
feature toggles. Development clients can use `/aurumdev freezeclouds true` to hold cloud phase and
`/aurumdev reload` after editing the JSON config. These commands are absent in production.
