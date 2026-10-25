# Regasm natural scenery review — Aurum 0.6.1

The user identified **regasm** as the save shown in their footage. This review uses a fresh,
independent test world, **Aurum Regasm Scenery**, seed **−5138383364446607098**. It does not use
or reconstruct their actual save. No visual-lab functions, demonstration trees or placed structures
were used in this world.

The five world datapacks were enabled in the creation screen before first generation. Their
low-to-high priority is Geophilic 3.7 → Terralith 2.5.4 → Incendium 5.3.5 → Tidal Towns 2.0 →
More Mobs 1.5.11, above vanilla/Fabric and enabled built-in generation packs. The saved level.dat
and the game's `/datapack list enabled` output confirm the order. Terralith's old test mod JAR was
removed from this profile to avoid loading it twice.

`regasm-scenery-profile.json` records exact source URLs, versions, hashes and the **36 external
mod JARs** present during capture. Aurum, Fabric API, Sodium and Mod Menu come from the development
build. This includes Nature's Spirit, Seasons/Extras/Terralith compatibility, TerraBlender,
Oh The Trees You'll Grow, Lithostitched, Terrain Slabs, Galosphere, CTOV, Structory, Explorify,
Dungeons and Taverns, available Moog's content, Unnamed Sea/Desert, flowers, weather and wildlife.
Rendering companions include Chunks Fade In, ImmediatelyFast, LambDynamicLights, EMF and ETF.
This is a documented subset, **not all 135 user-listed mods**.

## Actual cameras and conditions

| Scene | Camera X Y Z / yaw pitch | Evidence |
| --- | --- | --- |
| Maple ground | 2583.5 111 3328.5 / 90 8 | Game biome predicate confirmed Nature's Spirit maple woodland |
| Maple panorama | 2560.5 137 3350.5 / −135 12 | Repeated after early incomplete terrain was rejected |
| Fir forest | −2760.5 69 1152.5 / 90 8 | Predicate and F3 confirm Nature's Spirit fir forest; naturally generated snow |
| Mixed hillside | −1247.5 103 4054.5 / 90 8 | Predicate and F3 confirm Nature's Spirit marsh; the point itself is dry |
| CTOV village | 1788.5 110 2295.5 / 135 15 | `/locate structure #ctov:village` found ctov:small/village_jungle_tree |
| Open ocean | −2200.5 78 4950.5 / 135 10 | `/locate structure joshie:village_ocean`; F3 deep-ocean biome |
| Inland water | −1258.5 66 4125.5 / 180 8 | F3 river; saved surface-water columns beside Terralith blooming valley |

High quality settings, LabPBR off, render/simulation distance 12, FOV 70, gamma 0.5. See individual
sidecars for all controls. Daylight and weather cycles are paused; explicit absolute times
retain the fall season: noon 1350000, sunset 1356000, midnight 1362000. Cloud phase is frozen for
comparisons. Water and entity animation continue. Aurum-disabled controls keep the same installed
mods; they do not uninstall Aurum or establish a pixel-identical vanilla frame.

The inland body was selected from actual saved chunk data: WORLD_SURFACE heightmaps and block-state
palettes identify surface water, rather than relying on a biome name. Nearby chunks at X/Z
−1240/4104 and −1224/4088 have 256 surface-water columns at Y62. This is a river/valley water case,
not a claim of a separately verified closed lake. Calm Lake refers to Aurum's selected water style.

## Findings

Natural canopies and generated buildings render with directional shading, and the test now covers
seasonal gold/red woodland, dark evergreen foliage, snow, olive ground and vivid jungle colours.
The wide views still show pale lower-sky transitions and reduced distant foliage contrast. The
fir sunset camera does not demonstrate strong volumetric shafts; Natural/Crisp screenshots must
not be presented as proof of premium natural god rays. The swamp-haze classifier currently checks
for `swamp` in a biome path and does not include Nature's Spirit `marsh`.

No product rendering changes were made for this review. These captures test the published 0.6.1
lighting source, whose identity against the shipped JAR was verified. Reference shader parity and
Intel UHD performance remain unverified. The standalone HTML contains the original screenshots,
including the rejected early panorama as a labelled diagnostic, and the capture manifest records
settings, sent commands and observed game-log feedback.

## Setup problems and boundaries

- A missing desktop JRE library poisoned AWT initialization and failed saved-world icon loading.
  The matching OpenJDK 21.0.12.1 desktop package was downloaded from Debian, SHA-256
  `6530f359a77cd001b84eb742d373e618376c0cc5822c541f6949a92c18944144` (209200 bytes), and its
  libawt_xawt.so, libjawt.so and libsplashscreen.so were added to the writable workspace JDK.
  UIManager and ImageIO then read the 64×64 icon with DISPLAY=:100 and headless=false.
- Startup still stalled with the added Lithium/ModernFix combination, including a trial disabling
  ModernFix's paper chunk patch. Both were excluded from final captures. This does not establish
  the cause or reproduce a failure on the user's machine.
- Logs report missing Wet Sand block/tag references, cascading sand-tag errors and a Wilder Flowers
  loot reference to an absent block. Ocean generation adds invalid hanging-entity and item-load
  errors; the originating structure template was not isolated. They did not stop the final session.
- Requested legacy mos 2.0.3, mss 2.0, mtr 1.0.3 and mmr 2.2.5 pins were not resolved through the
  accessible release catalogue. Different versions were not silently substituted.
- Other user mods, resource packs, actual mod configurations, their world/seed and Nether scenery
  are not covered. Software Mesa/llvmpipe OpenGL 3.3 at 1280×720 cannot measure Intel UHD acceptance.

Use the disposable scenery save with its documented dependency profile. The older Visual Lab save
requires its previous dependency profile; do not use safe mode to strip missing world data. For
new scenery teleports, inspect actual geometry and debug mesh queues; a fixed delay alone is not
readiness proof. Never reuse the early incomplete panorama as an accepted rendering comparison.

## Follow-up water-shadow isolation

The ocean sunset showed broad bands across the water. They remain with God Rays disabled and
disappear when surface Shadows are disabled, at fixed camera/time/cloud phase. The review includes
that control pair so surface shadows are not mislabelled as a successful volumetric-ray test.
An additional Voxel Crisp sample uses an earlier frozen phase with rays enabled and is labelled
as a style sample rather than an isolated one-factor comparison.

## Confirmed vertical-water regression

The small waterfall on the upper-right bank of the inland view is blue and readable with Aurum
disabled, nearly invisible with it enabled, and loses visibility again when re-enabled after the
control. That on/off/on repeat was captured after the scene settled. The waterfall exists; this is
loss of visual flow cue rather than a claim of failed generation. `aurum_shadeWater` does not use
its incoming animated water texture, and a thin vertical sheet can largely transmit the rock behind
it. This needs a dedicated vertical-flow treatment; no fix is claimed in this review.

## Artifact verification

The report embeds 31 original PNGs, including one labelled rejected panorama and one debug
readiness frame. Original file hashes, native 1280×720 dimensions, PNG integrity and embedded
image bytes were checked. Chromium verified all images decode, zoom and diagnostic disclosure
work, no JavaScript errors, and no horizontal overflow at 390px. The standalone setup/build check
passed in 12 seconds with five tasks up-to-date. The visual client was terminated after captures;
Gradle's runClient task consequently reported a terminated process, not a build/test failure.

Deferred mesh work remained in some debug views. Far-landmark differences therefore do not isolate
a single fog, edge-fade or culling cause, and these comparisons are not a complete certification of
distant mesh readiness. The waterfall on/off/on result concerns the same nearby feature after
repeated observation; it is recorded separately from that far-scene uncertainty.
