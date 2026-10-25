# Excalibur-inspired surface depth without LabPBR

Research phase · Aurum `codex` · 25 October 2026 · existing renderer `b8f5a5b`.

## Conclusion

Aurum can give ordinary textures more readable, sharp surface depth without requiring a LabPBR pack. It already generates normals and height data from colour textures. The useful next step is better control of that inference, coherent lighting and stable sampling—not simply more depth or more sharpening.

Matching every texture to Excalibur automatically is not a credible guarantee. A colour image does not uniquely describe geometry. Excalibur also supplies authored pixel art and actual block models, which a texture-only effect cannot fully reconstruct.

This research changes no production rendering code and publishes no replacement mod JAR. The paired Minecraft images show the current renderer, not a proposed improvement.

## What was actually reviewed

- All six official Modrinth gallery images were downloaded and visually inspected. The illustrated report reproduces four attributed references. The promotional title artwork is not treated as a gameplay or POM measurement.
- The official Modrinth API supplies the gallery URLs even though the gallery web page is blocked here.
- The exact YouTube trailer `7L6GUlEeUNA` could not be played: the environment returns a proxy HTTP 403 for YouTube. No trailer frames, timestamps, motion behaviour, title or shader settings have been verified. Gallery images are not substitutes for watching that video.
- The API-listed Minecraft 1.20/1.20.1/1.20.2 download was inspected locally: Excalibur version ID `yVpdNmnj`, ZIP SHA-256 `ccf01766c56ad287071c795452ebdb6a33bf35a74f098d5191475cd479292d49`. Its `pack_format` is 15, but its description string says MC 1.21. That metadata inconsistency is recorded rather than silently choosing a different release. The gallery can depict another pack revision or additional rendering software.

## Where the reference's depth comes from

The windmill and gate pictures combine clear light/dark pixel groups, recessed-looking seams, trim, beams, fences and modelled details. Warm light against shaded surfaces makes these forms easier to read. The sandstone picture preserves distinct block borders and layered construction without making every colour pixel a spike. The night image demonstrates how selective light separates forms; it does not identify the renderer used.

These are visual observations, not proof of a particular parallax algorithm. The gallery does not disclose enough to identify its shader, PBR add-ons, exposure or post-processing settings.

The pinned ZIP contains:

| Inspected asset | Count |
| --- | ---: |
| PNG files | 3,242 |
| Conventional LabPBR `_n.png` maps | 0 |
| Conventional LabPBR `_s.png` maps | 0 |
| Shader files | 0 |
| Block-model JSON files | 823 |
| Models defining their own elements | 615 |
| Models with more than one element | 444 |

For example, its ladder defines four cuboids, bookshelf eight and rail six. This confirms genuine geometry in those examples; not all 823 models are additional geometry or full cubes. Authored models can make silhouettes, gaps and side faces that a surface normal map cannot create. No Excalibur pack assets or model definitions are bundled into Aurum.

## What Aurum already does, and why it falls short

1. **Generated normals already work with LabPBR off.** `AtlasBaker.bakeSprite` uses neighbouring colour luminance to estimate slopes. These are useful generic detail, but coloured paint, noise and painted shading can be mistaken for geometry.
2. **Generated height already works independently of LabPBR.** `bakeGeneratedHeight` uses per-sprite minimum/maximum luminance, then maps it to a shallow 18% height interval. The render binder enables it through `pomGenerated` even when `labPbr` is false. External `_n`/`_s` maps are not required. Internal generated normal/height textures are still used.
3. **The height and normal estimators are not one physical surface.** Height normalizes each sprite's contrast; normal slopes use raw contrast and an independent strength. Their response can disagree. Generated POM walls are deliberately softened to avoid the striping exposed in earlier work.
4. **The detail is not lit consistently by every source.** `surface_light.glsl` uses the detailed normal `n` for directional illumination but calls local block-light evaluation with geometric normal `ng`. POM's height self-shadow march follows only `u_LightDir`; it is not repeated toward torches or other lamps. Therefore better-looking daylight detail is not evidence of the same result under local night/cave light. Geometric normals must remain available for safe visibility offsets even when shading starts using detail normals.
5. **There is a stability boundary to investigate.** POM has a hard projected-footprint cutoff at 0.90, a 48-cell view traversal cap and up to 12 height-shadow samples. Distance depth already fades; footprint transitions do not use that same fade. Potential popping or shimmer is a test hypothesis, not a measured defect from these stills.
6. **“Any texture” has current exceptions.** Generated height skips transparent sprites, low-contrast images and non-solid classified materials. POM requires aligned square power-of-two atlas tiles in the supported size range. Animation, non-square sprites, UV rotation, unusual models and very noisy textures require separate treatment. The generated atlas is baked at resource reload, not updated for each animated colour frame.
7. **Sharpening is a separate tool.** It can increase local contrast but cannot add real silhouette geometry, view-dependent occlusion or correct missing depth. Turning it up to compensate risks halos and movement shimmer.

Relevant production files: `AtlasBaker.java` (normal generation around line 188; height around 239), `TerrainUniforms.java` (LabPBR/generated-relief separation around 174), `pbr.glsl` (traversal and shadow logic), `lighting.glsl` (detail normals) and `surface_light.glsl` (local receiver normal around 89).

## Experiments completed before new engineering

**Actual Java height bake:** the research harness extracts and compiles the unchanged production height method. It runs thirteen real colour textures—five vanilla, five conventional Excalibur base assets and three model-referenced Excalibur textures, including its 48×48 oak atlas—and four constructed controls. These samples do not cover every texture variant or prove runtime tile eligibility. No height-map algorithm is reimplemented for the test.

Eligible textures in these samples all span stored height bytes 209–255: approximately **0.03969 blocks**, or **0.635 of a 16× texture texel**, at the current 100% depth setting. That equality across different materials follows from contrast normalization; it does not establish that all should have that depth.

A completely flat-coloured control gets no relief. A sprite with a transparent texel gets no generated height. Two flat, painted squares with reversed brightness generate opposite depth even though both are physically flat by construction. These controls demonstrate false depth and polarity ambiguity. They are not evidence that height can be recovered universally from colour.

**Actual Minecraft baseline:** the documented modded test subset runs the current Aurum alpha on Mesa llvmpipe in the disposable `Aurum Lighting Audit` save. A separate elevated detail board holds stone bricks, oak planks, cobblestone and bookshelves. Both vanilla and the pinned Excalibur pack are tested with LabPBR disabled. Matched overview cameras compare:

- normals off, parallax off, sharpening 0;
- generated normals on, parallax off, sharpening 0;
- generated normals and parallax on, sharpening 0;
- the same with sharpening 65.

Close-up views use another explicitly recorded camera. An Excalibur frame with Aurum disabled is a geometry/art control; its lighting differs and it is not a matched lighting-quality comparison. Runtime atlas logs identify zero authored PBR sprites.

**A limitation exposed by the A/B:** on the Excalibur overview board, enabling POM after normals produces essentially no material-ROI change (mean absolute display-channel difference about 0.00001/255), while the corresponding vanilla comparison changes by about 0.576/255. Generated normals themselves do change both boards. This is not a passed Excalibur parallax-coverage test. The exact runtime gate responsible remains unresolved. Excalibur model references also include a 48×48 oak atlas, outside the current square power-of-two POM eligibility; this is one confirmed incompatibility, not a complete explanation for the other materials. Before tuning depth, instrument the actual used sprites, height range, UV mapping, flags and footprint cutoff to prove which path each face takes. Normal/height tests on base filenames cannot substitute for that runtime check.

Native background clouds were not frozen, so material comparisons exclude the sky. The report includes original captures, configuration snapshots and hashes. They do not demonstrate a new effect, trailer parity, motion stability or Intel UHD performance.

## Proposed engineering sequence

### 1. Make one conservative surface description

First diagnose the inactive Excalibur overview control at the actual sprite/face level; require a positive depth-only control before claiming coverage. Retain the reload-time bake, but derive generated normals and bounded relief from the same texture-local height estimate and physical scale. Separate broad structural regions from fine colour noise. Use robust statistics, edge-aware filtering and confidence limits instead of assigning every eligible sprite the same maximum relief. Keep opaque pixel boundaries crisp, preserve tile continuity and avoid depth from alpha borders. Default to subtle relief, allow opt-out and fall back to normals-only when evidence is weak.

Use material class as a prior where available—masonry seams, wood grain, rough stone—not as proof that the art encodes height. A dark painted stripe must remain a failure control. A custom resource pack can redefine a block's material appearance; block IDs alone are insufficient. Preserve the original albedo and do not add automatic metal/gloss/emission from brightness.

### 2. Give that surface a coherent light response

Use the detailed shading normal for sun, moon and local Lambert response while retaining geometric normals for shadow/visibility offsets and hemisphere safety. Keep source colours and exposure consistent with the shared evaluator. Prototype bounded local relief occlusion for the dominant nearby source only if measured affordable; do not add a height march for every one of 32 lamps. Compatibility with propagated dynamic lights needs an explicit fallback because it supplies no general per-source direction.

### 3. Stabilize the surface in motion

Use texture-local, repeatable data and matched colour/normal/height LOD decisions. Smoothly fade geometric relief as the projected texel approaches subpixel size or the view becomes too grazing; avoid abrupt sampler/iteration-budget changes. Validate every face orientation, rotated/mirrored UVs and tile boundary. Average/filter normals correctly at mip levels rather than amplifying high-frequency noise.

### 4. Add optional edge definition after those foundations pass

Evaluate subtle procedural bevel shading on verified suitable block faces. This can strengthen the sense of a block's edges but cannot change its actual silhouette. Respect authored Excalibur/modded models instead of trying to extrude every texture pixel into world geometry. A general per-pixel mesh extrusion is not the default plan for Intel UHD: geometry cost, internal faces, cracks and chunk rebuild work must be justified separately.

### 5. Tune sharpness last, then measure on target hardware

Keep the reload-time approach and reuse the existing normal/height atlases where possible. Both baseline runs allocated two 1024×1024 RGBA8 atlases: about 10.67 MiB with a full mip chain, excluding the colour atlas and staging memory. The current shader can perform up to 48 height traversals plus 12 directional self-shadow queries at eligible pixels; those bounds are not a frame-time measurement. Do not trade the Intel UHD target for more unbounded work.

Use controlled 0/65 comparisons and select the smallest amount that improves readability without halos or crawling. Keep full-colour detail and biome tint. Measure reload cost, atlas memory, GPU frame times and 1% lows at the user's resolution, render distance and mod profile. Do not call llvmpipe timing an Intel UHD benchmark.

## Acceptance matrix for the implementation

| Axis | Required cases | Failure to catch |
| --- | --- | --- |
| Surface | brick/mortar, planks/end grain, gravel, smooth painted blocks, ore motifs, modded stone | fake embossing, inverted cavities, noise spikes |
| Pack | vanilla, Excalibur, another ordinary 16× pack, 32/64×, partial authored PBR with LabPBR off | pack-specific tuning disguised as generality |
| Geometry | six face directions, stairs/slabs, rotated UVs, actual multi-cuboid models | seams, wrong normals, atlas bleeding, model distortion |
| Camera | fixed, slow translation, rotation, sub-texel motion, grazing angle, near/distant transitions | shimmer, crawling, sudden relief disappearance |
| Light | noon, low sun, moon, one torch, warm+soul overlap, moving held source | daylight-only pop, changing material identity, double light |
| Assets | animation, transparency, non-square sprites, resource reload and pack switch | stale height, black edges, unbounded allocations |
| Sharpness | 0/65, foliage and high-contrast edges, moving camera | halos, texture noise mistaken for 3D |
| Performance | same hardware/world/camera/settings, feature A/B, frame-time distribution | average-FPS-only claims hiding stalls |

Stage the prototype behind a separate experimental setting. Keep the existing appearance as a control until these gates pass. No reference-parity or performance claim is justified by the current research alone.

## Evidence and sources

- [User's trailer—playback still pending](https://youtu.be/7L6GUlEeUNA)
- [Official Excalibur gallery](https://modrinth.com/resourcepack/excal/gallery)
- [Official project API: gallery, description and licence](https://api.modrinth.com/v2/project/excal)
- [Pinned compatible version metadata](https://api.modrinth.com/v2/version/yVpdNmnj)
- [Rethinking Voxels generated-normal implementation](https://github.com/gri573/rethinking-voxels/blob/d76c831da62b695d1b30d30bb218aba2116fd55e/shaders/lib/materials/materialMethods/generatedNormals.glsl): inspected as a technique reference. It thresholds/clamps colour differences, bounds sprite samples and fades normal strength with mip detail. Aurum's reload-time bake can retain its different cost model; no reference code is copied.
- `tools/research/excal_relief_audit.py`: asset inventory and exact production height-method controls. Requires a JDK and Pillow; accepts local pack/client ZIP paths. The copyrighted resource pack itself is not redistributed with the report.

The material conclusions are supported by inspected assets, source and recorded baseline runs. The trailer review remains an explicit missing input.
