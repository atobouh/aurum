# Shared lighting implementation — 0.6.2-alpha.1

This is the first integrated implementation of the lighting plan, not completion of every acceptance gate.
Rethinking Voxels remains the design reference; no reference code or assets are bundled.
The stable 0.6.1 download remains unchanged. This build is an alpha for review and further testing.

## Implemented

- Sodium terrain and ten supported Minecraft model programs now call one surface-light evaluator and display
  transform. The programs cover ordinary solid/cutout/translucent entities, block entities, armour and common
  held-item paths. Raw vertex colour, model light coordinates, camera-relative world position and world normal
  are adapted explicitly. The original shader branch remains for inventory/UI, shadow depth draws, special
  dimensions/effects and Aurum OFF.
- Both stages of each supported core program are fingerprinted against Minecraft 1.20.1 before adaptation.
  A modified resource-pack shader pair remains its own shader. Cutout ordering, colour modulation, alpha and
  overlays retain the original variant's semantics. Explicit full-bright model draws retain their native branch and lightmap;
  diagnostic mode marks these orange. A flat integer full-bright flag avoids interpolated packed-light thresholds producing stripes across perspective triangles. Night vision uses the native-lighting bypass. Authored emissive eyes and blob-shadow shaders are excluded.
- The terrain-face-only local cache is replaced by a scene-space per-source visibility field. Every supported
  receiver evaluates falloff and normal response at its actual position. This avoids using collision-box faces
  as a moving model's receiver and avoids the torch-height floor error found in the first irradiance prototype.
- Static sources are combined in linear relative radiance without the previous pre-display peak cap of 1.5.
  Up to thirty-two sources have individual visibility channels. Overflow selects a coherent propagated fallback
  for terrain and models rather than silently replacing missing-source light with black.
- A separate local single-bounce estimate gathers nearby illuminated collision surfaces through six axis rays.
  It uses assumed neutral reflectance 0.35 and a bounded four-block gathering kernel. It is not albedo-aware
  daylight GI, general multiple-bounce transport or parity with Rethinking Voxels.
- Shader sampling distinguishes unavailable probes from valid darkness. Source transforms and visibility are
  published together. Obsolete revisions are rejected, edits invalidate ready data, and camera-relative source
  uniforms refer to the published world-space field origin. The fallback fade is centred continuously on the
  camera rather than jumping with the four-block cache origin. Outrunning a cache update remains a test case.
- Natural uses filtered visibility. Voxel Crisp snaps receiver-plane queries to the world grid and thresholds
  local visibility; local bounce remains filtered. This is a style option, not an alias-free or full-resolution
  shadow reconstruction.
- Water's local-light scattering term now uses the same source accounting and light curve. Water appearance,
  absorption, reflections and waves are not redesigned in this phase.
- The view-dependent screen-average “bounce” tint and its gather/blend passes have been removed from surface
  lighting and fog. The existing bounce control now scales the local cached estimate. This removes a source
  of camera-dependent colour feedback; it does not complete local-light fog transport.
- Shared model uniforms are uploaded once per program/world frame; subsequent model draws rebind shared
  samplers and the world-mode flag. This avoids repeating the complete terrain binder for every model draw.

## Dynamic-light compatibility is an explicit limitation

When LambDynamicLights is installed, the static voxel replacement is paused for both terrain and models.
The shared evaluator uses their propagated light coordinates instead. Thus a static-only cache does not
replace/discard the moving light contribution, and two independently evaluated versions of the same lamp are
not added together. Settings tooltips and diagnostic snapshots identify this policy.

This is a compatibility mode, not a completed dynamic-source adapter. Vanilla's per-model versus per-vertex
light-coordinate sampling still differs. It does not provide coloured, voxel-occluded moving lights, nor a
shared per-fragment spatial dynamic-light field. A proper source adapter remains required for that quality.
Do not present the modded compatibility run as proof that voxel lighting was active beside moving sources.

## Resource bounds (calculated, not a benchmark)

The world field remains sixteen blocks across. Detail 4 uses one-block visibility probes: a 256×144 RGBA16F
atlas, double-buffered, totals 576 KiB. Detail 8 uses half-block probes: 1024×288, double-buffered, totals
4.5 MiB. The previous surface atlases used 6/24 MiB respectively. The 56³ source/geometry snapshot covers the maximum fifteen-block direct reach plus the four-block
bounce gather around the field; unloaded snapshots are rejected. Candidate sources are filtered against
that influence bound before the thirty-two-source budget is applied. Snapshot access retains a one-millisecond
client-frame deadline, with up to 8192 cells per update. CPU snapshot, result and upload staging memory are additional. Detail 8 has eight times as many probes, so lower atlas memory does not establish
lower total frame cost. The default/high detail uploads take three/five 64-row update calls after baking.

Shaders evaluate up to thirty-two actual-source distances and Lambert terms and cache visibility reads in
four-source groups. This trades arithmetic and probe reads for consistent receivers; it requires target-device
measurement. Sun-shadow lookups are skipped when the skylight gate is identically zero. No new full-resolution light buffer or compute-shader requirement is introduced. Added samplers
remain within the existing sixteen-unit terrain layout.

Renderer source commit: `b8f5a5b17d64e37d45162109ae1d818dd45ce848`.

## Automated verification

`./gradlew build` includes `checkSurfaceLighting`: the production source adapters and include preprocessor
are checked for ten variants, paired fingerprints, retained original branches, light-coordinate conversion,
shared evaluation, cutout ordering, overlays and intentional exclusions.

`JAVA_HOME=/workspace/jdk21 python3 tools/check_incident_lighting.py` runs the production Java bake, loads
its output as RGBA16F and runs the production local-light GLSL on an EGL OpenGL context. It checks analytic
receiver lighting, dark back faces, the empty-air negative control, wall occlusion, the torch-height floor
regression, local bounce, mixed colours, sixteen/thirty-two-source linear HDR, emitter-centre lighting, matching world
samples/coverage after recentering, crisp mode, and half-block layout. Cancellation, empty-source allocation
and excess-input rejection are separate Java controls. This runs on llvmpipe, not Intel UHD.

`tools/check_surface_raster.py` additionally rasterizes the adapted production entity shader with perspective-varying triangles: 3,197 covered pixels must retain the flat full-bright branch.

The original geometry harness still tests the original ray/box traversal used by the new visibility baker.
It is not proof that the discarded terrain-face receiver design remains the active backend.

## Runtime verification and evidence

The illustrated report and accompanying evidence JSON distinguish the static-voxel profile from the
LambDynamicLights compatibility profile. Both use the disposable **Aurum Lighting Audit** copy of the
previous Regasm scenery test save; only that copy receives constructed fixtures. The natural scenery save
and original user-save inventory are not modified. This is the documented mod/datapack subset, not the full
user installation. Reference screenshots remain historical, not a newly matched RV cavern run.

Development-only `/aurumdev lightrecord start|stop` records timestamped camera, nearby client entities,
world time, exposure, cache origin/revision/source data and model bind counts. `/aurumdev lightmotion
start|stop` moves explicitly tagged test receivers/sources on server ticks. Nearby entities are observed
by type/ID/position because server scoreboard tags are not assumed to be synchronized to the client.
Recording is bounded; it is not enabled in release clients. Frame duration includes software rendering and
instrumentation and is not source-to-photon latency or a target-device performance measurement.

The runtime result table is generated from the actual capture snapshots and motion recordings. An early
irradiance-cache prototype and failed menu/controller runs are rejected evidence. They must not be counted
as passes for the corrected visibility implementation.

Replays and their safety constraints are documented in `tools/lighting_integration/README.md`.

The natural dripstone frames show distant sky/void gaps. The current evidence does not establish whether geometry, culling or world generation causes them. This scene is explicitly not a passed cavern acceptance case. Held light illuminates the nearby dripstone in compatibility mode, but the wider cave remains unresolved.

The test profile also logs missing wet-sand suspicious-sand references and dependent sand/carver tags, a wilderflowers loot-table parse error, audio initialization failure and network lookup errors. The effect of the data/tag errors on generated scenery has not been resolved; the run is not a clean full-modpack compatibility pass. The renderer did not log a shared-shader compile/link failure in these runs.

## Remaining gates

A full dynamic-source adapter, albedo-aware/daylight GI, source-aware local-light air scattering, contribution-
aware post AO, natural cavern/dripstone acceptance, moving-entity local shadow casting, complete mod compatibility and Intel UHD frame-time/
latency measurements remain open. The current post AO still acts on composed colour; the cave medium is
not rebuilt by this alpha. Existing waterfall visibility and distant sky/horizon issues are outside this
lighting milestone. Do not call this release “unified lighting complete” or claim reference parity.
