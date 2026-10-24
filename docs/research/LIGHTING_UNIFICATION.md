# One lighting system: research and engineering contract

Research date: 24 October 2026. Baseline: Aurum 0.6.1, source d10830d; scenery evidence 05557d1.
Primary reference: [Rethinking Voxels](https://github.com/gri573/rethinking-voxels/tree/d76c831da62b695d1b30d30bb218aba2116fd55e), pinned d76c831da62b695d1b30d30bb218aba2116fd55e.
This phase concerns lighting only. No new water, sky, cloud-shape or sharpening feature is proposed.
The research checkout is read-only; no reference implementation or assets are copied into Aurum.

## Finding

The central defect is inconsistent ownership of illumination. This is more specific than “not enough GI.”
Aurum shades Sodium terrain in its own linear-light pipeline and tone maps it, while vanilla-rendered
entities, block entities and held objects retain the Minecraft lightmap and directional model. A zombie head
is a block entity; a furnace's body is terrain. Those neighbours do not currently share a lighting evaluator.
Entity shadows only make entities cast a shadow; they do not make those entities receive Aurum lighting.

Inside terrain, the local voxel atlas replaces propagated block light on eligible collision-box faces. Cards,
leaves, emitters, unbaked cells and edges can retain propagated light. The atlas contains only direct visibility
from at most sixteen selected static sources, without indirect block-light transport. Thus the same source can
produce a dark shadowed terrain face beside a brighter fallback object. This is an architectural explanation
supported by source; it is not a pixel-level reproduction of the user's furnace/cavern footage.

The user's black/speckled dripstone and pale snow are separate observations. Voxel replacement, generated
relief/self-shadow, contact AO, material paths and fog must be isolated before assigning their exact causes.
The screenshots alone do not establish that voxel lighting causes either defect.

## What the reference actually does

Read the pinned files at the linked commit, not changing upstream HEAD:

| Reference source | Observed mechanism | Lesson for Aurum |
|---|---|---|
| `shaders/lib/lighting/mainLighting.glsl`, `DoLighting` | One material-aware composition routine called by terrain, block entities, entities, hand, textured and water programs. | Share illumination and display conventions; differences should be explicit material responses. |
| Same file, voxel-based lighting and combine sections | Computes voxel block light; optional normal/depth-validated per-pixel lookup; cache fallback; blends toward lightmap at voxel-volume boundary. Emissive/high-light cases get explicit protection. | Coverage, invalid data and emitter exceptions belong to the lighting contract. Do not treat every black sample as trustworthy darkness. |
| Same file, GI section | Reads indirect irradiance separately from block light, applies a configurable ambient minimum, and falls back at the volume boundary. | Direct source visibility is not indirect illumination. A wall can lose a direct lamp without losing all received light. |
| `shaders/lib/vx/irradianceCache.glsl` | Distinct reads for indirect irradiance, surface block light and volumetric block light, with range checks. | Air and surfaces need related source data but different sampling/transport responses. A surface atlas is not an air field. |
| `shaders/program/shadowcomp1.glsl` | Compute-based source selection/tracing and irradiance-cache updates; optional GI processing. | Stable scene-space storage and explicit updates matter as much as the colour formula. |
| `shaders/lib/atmospherics/volumetricBlocklight.glsl` and `fog/coloredLightFog.glsl` | Local block-light samples can illuminate air. | Avoid a camera-centred brown veil unrelated to visible lamps. |
| `shaders/shaders.properties` | Requires custom images, SSBOs and compute shaders; large 3D allocations vary by profile. | Reference quality does not establish the cost of porting its machinery to Intel UHD. |

Rethinking Voxels is a useful design reference, not a physically exact implementation to duplicate. Its
main-lighting routine includes artistic curves, minimum light, special cases and AO. Sharing one function
also does not prove identical response for every material. What matters is explicit, deliberate treatment.
Its current selected branch is r0.1-beta9 / singlesample-softshadows-v2, not a claim about every release.

## Aurum's current paths

| Receiver or effect | Current light source and response | Gap |
|---|---|---|
| Solid Sodium terrain | Sun/moon, directional sky, local voxel replacement or lightmap fallback, minimum light; Aurum tone map. | Direct-only local light can replace propagated illumination with zero; local AO is followed by composite AO. |
| Plant cards | Terrain sky/sun with upward normal and vanilla block-light curve; voxel explicitly bypassed. | Adjacent card and stone can disagree on hue and occlusion. |
| Leaves | Terrain lighting with leaf sky/AO corrections; tagged leaves have no collision receiver in bake. | Usually fallback local lighting beside voxel-lit trunks/ground. |
| Static emitters | Baker skips emitting receivers; terrain lightmap fallback. | Emission and illumination are not separately represented by the local field. |
| Entities / block entities / held items | Minecraft core shaders/lightmap; common final post effects. | No Aurum surface-light evaluator or its tone map; casting shadows does not fix reception. |
| Water body | Independent sky/direct terms; local light uses linear decoded level times 0.5. | Different falloff from solid terrain's level/(4−3 level), no shared local field. Keep water appearance redesign out of this phase. |
| Air / cave fog | Depth, sky radiance, sun shadow march, camera enclosure and density; screen-average bounce tint. | No local block-light field. Camera enclosure changes an entire view; local dust remains separate from distance extinction. |
| Contact AO | Post pass multiplies composed display colour before fog. | Dims direct, local, emissive and indirect contributions together; not one source-aware occlusion policy. |
| “Bounce glow” | Previous-frame near-screen average used as ambient/fog tint. | View-dependent colour feedback, not spatial indirect irradiance or conserved bounced energy. |

### Concrete cache and compatibility risks

1. The snapshot keeps sixteen static sources. The baker marks evaluated faces valid even if selected sources
   contribute zero. A source omitted by the cap is not represented by a residual term or lower confidence.
2. LambDynamicLights is present in the documented scenery subset. Its moving-source changes can enter terrain
   light coordinates, but the static bake only scans `BlockState.getLuminance()`. Replacing that combined
   lightmap value can discard moving-light illumination. This is a source-backed risk, not yet a controlled
   LambDynamicLights conflict reproduction.
3. The receiver atlas encodes the outermost collision-box plane per axis/UV. Collision geometry differs from
   render geometry for stairs, fences, dripstone, decorative and modded models. Invalid locations need a
   conservative fallback; atlas black is not universally valid surface lighting.
4. The 16³ field follows the camera in four-block steps, with a two-block edge blend. It can publish an older
   revision while a new revision is needed; ordinary edits do not cancel an in-flight generation. No temporal
   radiance blend exists at publication. Measure stale light and source-selection changes in motion.
5. At detail 4, the double-buffered RGBA16F atlas allocation is 6 MiB total (1024×384×8×2). At detail 8 it is
   24 MiB (1024×1536×8×2). Snapshot, CPU float result and staging add memory. Upload alone takes six or
   twenty-four update calls at sixty-four rows per call, after snapshot and bake; these are not measured frame times.
6. Cave appearance must be checked with generated relief and post AO independently disabled, especially on
   thin dripstone. Never label the supplied black fragments “fixed” from a finite-output test.

## Engineering contract

All supported receiver paths must evaluate the same named components in a defined linear-light space:

`reflected = albedo × (directSunMoon + indirectSky + directLocal + indirectLocal + accessibilityFloor)`

`outgoing = reflected + materialEmission + materialSpecular`

Material normals, transmission and roughness alter the response; they do not introduce a second lamp system.
Units are Aurum's documented relative radiance scale, not a claim of absolute photometry. Each source must be
owned exactly once. Sun visibility, sky accessibility, local-light visibility and contact occlusion remain
separate quantities. Apply one exposure/white-balance/display convention to supported opaque materials.
A fully linear scene target is the long-term goal; until every path supports it, conversion boundaries must
be explicit and tested. A new HDR buffer alone would not correct vanilla objects still shaded differently.

### Source accounting and fallback

- Retain source identity: world emitter ID/state, position, colour, intensity and revision; distinguish static,
  moving and held sources. Infer colour from registry identity only as an explicitly documented fallback.
- Separate static and dynamic light contributions before any replacement. The combined Minecraft lightmap
  cannot be inverted to recover independent sources. Do not subtract guessed dynamic energy from it.
- Choose one authority per contribution. Either Aurum owns a dynamic source through a compatible adapter,
  or that source's supported fallback remains authoritative. Do not sum two evaluations of the same lamp.
- Keep direct and indirect local illumination separate. Conservative low-frequency propagation is a candidate
  for bounded indirect fill; it must respect blockers and preserve hue. Do not rename a brightness floor “GI.”
- Store confidence/coverage separately from radiance. An unavailable, stale, truncated or geometrically
  unsupported sample is not evidence of zero light. Saturated source sets must retain residual support or
  fall back consistently. Validate both positive light and actual dark occluded surfaces.
- Use shared spatial probes for arbitrary receivers, with surface detail as an optional refinement. A surface
  atlas cannot be sampled as if it were a general entity/air volume. Preserve complete surface samples or
  fall back rather than letting empty tiles create geometry-dependent black patches.
- Anchor probes in world space, publish coherent snapshots, and blend radiance from matched world locations.
  Teleports/disconnects/dimension changes invalidate data immediately. Ordinary edits need bounded stale-light
  lifetime, not indefinite retention or an unbounded rebake storm.

### Cavern first

Start in a neutral stone cave: no direct sun, no sky access, one warm source. Establish receiver agreement,
falloff, occlusion and minimum readable shade with fog, bloom, grade, relief and post AO bypassed. Then test
mixed warm/soul lights, a furnace, heads, chest, moving mob and held torch at the same source distances.
Add indirect fill only after direct source ownership is proven. Add AO to the intended components, then air
extinction/scattering driven by that same lighting state. Darkness should come from missing incident light;
fog must not manufacture light in a sealed unlit chamber or make a nearby torch paint the whole cavern brown.

A source-aware air field is a later integration gate, not justification to boost current cave fog. Sunbeams
must remain separate from block-light air scattering and require a positive aperture/closed-aperture control.

## Ordered implementation gates

| Gate | Engineering work | Required evidence before moving on |
|---|---|---|
| 0 — diagnosis (this change) | Development-only contribution views, cache/scene snapshot; source/path audit and reproducible scenarios. No beauty tuning or new release. | Runtime shader compile, view switching, reset, snapshot and unchanged normal mode. Explicit unsupported-path marking. |
| 1 — common surface evaluation | Shared light structures/units and display convention; adapters for terrain, block entities, entities and hand. Preserve overlays, cutouts, emissive/fullbright and special dimensions. | Neutral receiver board, source-facing vs back faces, night/noon; same lamp gives compatible colour/brightness across paths. |
| 2 — coherent local transport | Shared probes, source completeness, static/dynamic ownership, direct + bounded indirect support, coherent cache publication. | Walls/slabs/stairs/dripstone; 0/1/16/17+ sources; mixed lights; held/moving light; removal and volume crossings in motion. |
| 3 — cavern integration | Apply AO by contribution, integrate local light into air with bounded extinction. Remove screen-average tint as an indirect-light substitute in supported paths. | Sealed unlit cave, torch cave, lava, water, daylight entrance, thin geometry; fog off/on and opening sealed/unsealed. |
| 4 — outdoor regression and cost | Same system in snow, forest, village, sunset/night/rain; reference comparisons in the same test scene. | Natural generated scenery plus modded receivers. Intel UHD frame-time/memory measurements before promoting defaults. |

Do not ship an intermediate “unified lighting” release with only terrain changed. The supplied 0.6.1 JAR
remains the last released build; diagnostic work is a development candidate, not a new visual-quality claim.

## Acceptance matrix and measurements

Each capture records source/config identity, camera, world/time, mod profile, light mode, generated-relief state,
fog/AO state, cache revision/origin/readiness and actual screenshot. Capture clips as well as stills.

| Scene | Controls | Failure sought |
|---|---|---|
| Neutral cave receiver board | Stone, furnace body, head, chest, entity, plant and held object; one source then none | Path mismatch, unexplained glow, double source energy, colour disagreement |
| Occlusion corridor | Wall, aperture, top/bottom slab, stairs, fence, dripstone | Leaks, unsupported geometry blackening, shadow detach, loss of indirect support |
| Source stress | 1 / 16 / 17 / 32 static lights; warm + soul; remove all | Source-cap blackout, hue discontinuity, stale cache |
| Moving/held light | Dynamic-light mod off/on; turn, walk, drop/pick up source | Disappearing light, doubled light, frame-dependent ownership |
| Cache movement | Slow walk, sprint, four-block recenter, leave field, teleport, edit and reconnect | Popping, camera-following shadows, stale world data |
| Cave depth and opening | Unlit, torch, lava, daylight aperture; fog and AO isolated | Brown veil, fog light without source, disappearance of thin models |
| Snow and forest | Noon/sunset/night, cloud shadow on/off, same exposure | Clipped snow, crushed canopy, coloured fog replacing surface detail |

Record raw component values and representative neutral patches; texture hue alone is not irradiance.
Compare direct tests against the production baker and shader math, not a separate approximate model.
Provisional target-device gate: median, p95 and p99 frame time, edit spikes and memory against 0.6.1 in the
same save/settings. The user's 60 FPS target is ~16.7 ms per frame, not a measured spare GPU budget.
No numeric performance pass or parity claim is possible on this host's llvmpipe renderer.

## Evidence boundaries

The earlier Rethinking Voxels and Aurum screenshots are historical real game captures, with different renderer
and profile conditions. They illustrate the reference and the limits of the old fixture; they are not a new
matched cavern comparison. User footage is the problem evidence, not an exact locally reproduced save.
The Regasm scenery save uses the documented subset, not the full user installation. No new reference cave
capture or target-GPU run has been performed for this research stage. Build and diagnostic success do not
constitute visual acceptance or proof that the reported clash has been fixed.

## Diagnostic implementation in this candidate

The command and shader instrumentation is now implemented, with zero selected mode in release clients.
`LightingDebug` captures the scene and current cache state. `VisualLab` exposes the development-only commands;
`TerrainUniforms` and `PostPass` upload the mode; terrain isolation returns the actual production terms and
composite isolation bypasses Aurum post effects. Minecraft overlays/vignette and core-shaded objects remain.
`VoxelLighting.debugState()` reports selected/candidate counts, revisions, origins, readiness and job stage.
The existing light evaluation, source ranking, bake radiance and published 0.6.1 JAR are unchanged.
The normal-mode refactor only moves the existing voxel variable into diagnostic scope; it does not change
its calculation. Existing geometry checks and the new seven-behaviour characterization run the production
baker. The latter intentionally demonstrates current gaps rather than asserting visual parity.

The constructed room has two intentional emitters (torch and lit furnace); snapshots also count native
emitters outside the sealed room. It is a smoke test of diagnostic paths under the scenery mod subset,
not a matched natural cave, full dynamic-light test, source-stress run or completed integration gate.

## Source index (pinned)

- [RV shared material lighting](https://github.com/gri573/rethinking-voxels/blob/d76c831da62b695d1b30d30bb218aba2116fd55e/shaders/lib/lighting/mainLighting.glsl#L480): voxel reads, GI, fallback and final composition.
- [RV cache sampling](https://github.com/gri573/rethinking-voxels/blob/d76c831da62b695d1b30d30bb218aba2116fd55e/shaders/lib/vx/irradianceCache.glsl): surface, indirect and air access.
- [RV compute update](https://github.com/gri573/rethinking-voxels/blob/d76c831da62b695d1b30d30bb218aba2116fd55e/shaders/program/shadowcomp1.glsl): source processing and cache writes.
- [RV requirements and image sizes](https://github.com/gri573/rethinking-voxels/blob/d76c831da62b695d1b30d30bb218aba2116fd55e/shaders/shaders.properties#L213): required GPU facilities and profile allocations.
- [RV local volumetric light](https://github.com/gri573/rethinking-voxels/blob/d76c831da62b695d1b30d30bb218aba2116fd55e/shaders/lib/atmospherics/volumetricBlocklight.glsl): air-field use.
- [Aurum baseline terrain composition](https://github.com/atobouh/aurum/blob/d10830d/src/main/resources/assets/aurum/shaders/include/lighting.glsl): current replacement and AO factors.
- [Aurum baseline baker](https://github.com/atobouh/aurum/blob/d10830d/src/main/java/dev/impeccable/aurum/bake/VoxelLightBaker.java): receiver coverage, direct-only transport and validity.
- [Aurum baseline cache and source selection](https://github.com/atobouh/aurum/blob/d10830d/src/main/java/dev/impeccable/aurum/render/VoxelLighting.java): static snapshot, source cap and publication.
- [Aurum baseline composite](https://github.com/atobouh/aurum/blob/d10830d/src/main/resources/assets/aurum/shaders/post/composite.fsh): whole-image AO and air composition.

## Candidate transport design to evaluate at Gate 2

Keep the OpenGL 3.3 baseline initially. Evaluate a small CPU-updated scene-space probe field sampled by every
surface adapter, with direct visibility and low-frequency indirect support stored separately. Existing face
samples may refine contact shadows only when render/receiver geometry agrees. They must not be the only
local-light representation used by one material path. Use confidence to blend a consistent fallback on all
paths when data is incomplete; do not silently add the full vanilla lightmap over occluded direct light.
A dynamic-source adapter and an explicit unsupported-source policy are prerequisites for taking ownership
of the combined block-light channel. If the mod API cannot provide that information, keep an explicitly
labelled compatibility mode until it can; do not promise exact separation from an opaque scalar lightmap.

Do not select probe dimensions, propagation iterations or High defaults from a reference screenshot. First
measure storage, worker time, snapshot cost, upload cost and shader samples against the current atlas on the
target device. A CPU volume can also cause edit spikes or latency; “no compute shader” does not mean free.
The cache should expose a dirty revision and published revision, source completeness, per-sample confidence,
source category coverage and bounded refresh latency. Capture performance counters alongside motion footage.

Proposed numerical gates for the shared evaluator: identical linear inputs on a neutral receiver should
match across supported adapters within float tolerance before display encoding; missing/invalid data must
not be interpreted as valid black; a zero-source sealed scene must have zero source terms apart from its
explicit accessibility floor/emission. These are invariants, not aesthetic tuning targets. Visual falloff,
shadow softness, indirect colour and cavern readability still require side-by-side images and movement.

## Verification completed for this research candidate

- Gradle build passed on JDK 21 (Java 17 release target). This is a diagnostic candidate; no version bump.
- Live llvmpipe OpenGL 3.3 client compiled all fourteen Aurum programs and installed the Sodium terrain
  replacement. Every contribution/path/normal/AO mode was switched and captured, then normal mode restored.
- Eleven current original 1280×720 captures have matching snapshot data. Every capture reports eye skylight
  zero, seven selected static candidates, matching published/current cache revisions and a ready field.
  Seven candidates do not mean seven lamps inside the room; the snapshot includes the surrounding world.
- Disconnect/reconnect reset verified: selected COVERAGE before exit, requested/active OFF in the subsequent
  snapshot after rejoining. Test configuration restored afterwards; only the disposable save received fixtures.
- Existing production-baker geometry/finite-output controls passed. Seven additional current-behaviour
  characterizations passed, including the positive control and the reproduced missing-light cases.
- Fourteen embedded original PNG hashes, dimensions and integrity verified (three historical, eleven current).
  Chromium decoded all images; image zoom, evidence disclosure, desktop and 390-pixel mobile layout passed.
- Released JAR unchanged: SHA-256 `c90fcd0b6072452deddb64b0ea7d2c57a9ea80788e3621a1c3905e0d177105ee`.

Not completed: common surface evaluator, dynamic-source ownership, indirect local transport, source-aware
cave air, natural-cavern integration acceptance, matched new RV cavern comparison or Intel UHD profiling.
Those remain engineering gates, not implied successes from this tooling verification.
