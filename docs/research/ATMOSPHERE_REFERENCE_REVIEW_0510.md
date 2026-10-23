# Atmosphere and sharpness: expanded shader references

23 October 2026 · `codex` · Aurum 0.5.10

## Reference identity and local copies

The reference set is Photon, Complementary Reimagined, Rethinking Voxels and Derivative Main.
The new source checkouts are outside the mod repository in `/workspace/shader-references`.
Exact upstream URLs, revisions and checkout paths are recorded in
[shader-reference-sources.json](shader-reference-sources.json).

- **Rethinking Voxels:** official `gri573/rethinking-voxels`, branch
  `singlesample-softshadows-v2`, revision `d76c831da62b695d1b30d30bb218aba2116fd55e`
  (r0.1-beta9). The repository's default main branch is an older 2024 snapshot, so it was not used for these captures.
- **Derivative Main — Voxy Edit:** community fork `fuzhulike/DerivativeMain-VoxyEdit-fuzhu`,
  revision `6546738ef49c03610526ca53751780978c073331`. This is explicitly a community adaptation,
  not a verified original Derivative release. The official CurseForge project page was inaccessible
  through this environment's network; no original public repository was verified.
- **DERCODE:** additional public Derivative rework `SyrexSkyLine/DERCODE`, revision
  `ce2b347365060e887cf0b917498544be7ea2dcfe`. Its README calls the repository a development/beta build.
  It is retained for source research; DERCODE is not relabelled as the original Derivative.

Reference source and assets remain in separate local checkouts. Aurum's distributed JAR does not incorporate them.

## Sources inspected

Rethinking Voxels: `shaders/lib/lighting/mainLighting.glsl`, `lighting/shadowSampling.glsl`,
`atmospherics/sky.glsl`, `atmospherics/fog/mainFog.glsl`,
`materials/materialMethods/generatedNormals.glsl`, and `misc/voxelization.glsl`.

Derivative Main community fork: `shaders/lib/Atmosphere/Atmosphere.glsl`,
`Atmosphere/Fogs.glsl`, `Atmosphere/VolumetricFog.glsl`, `Lighting/SunLighting.glsl`,
`Lighting/ShadowDistortion.glsl`, `Water/WaterWave.glsl`, and `Settings.glsl`.
DERCODE's README, settings and atmosphere file layout establish its separate lineage.
Photon and Complementary review details remain in [LIGHTING_SOURCE_REVIEW_059.md](LIGHTING_SOURCE_REVIEW_059.md).

## What the references add

Rethinking Voxels separates voxel-derived block lighting from conventional sunlight and sky lighting.
Its sharp local-light shadows are a spatial lighting feature. They are distinct from Aurum's existing
pixel-height relief, which makes texture pixels look like little cubes but does not trace torch shadows
through a voxel volume. That distinction matters for interiors and nighttime depth.

Its sky uses coordinated upper/middle/lower colours, sun-facing sunset blending, separate atmospheric
and border fog, and a clear foreground. Derivative's atmosphere includes precomputed scattering,
transmittance and sky illumination; its shadow path includes blocker search, variable filtering and
screen-space contact checks. Its water emphasizes coherent multi-scale waves with derivative-based
attenuation. These pipelines supply more than a saturation or sharpening adjustment.

## Implemented in 0.5.10

1. **Resolve dense lower air.** Replace 16 uniform view segments / 6 uniform light segments with
   24 / 12 quadratically spaced segments. Integrate view attenuation through each segment, instead of
   assigning scattering the attenuation of the segment's far end.
2. **Diffuse re-scattering.** Bake first-order mean incident radiance over 32 equal-area sphere directions
   at seven heights. Integrate an isotropic second-scattering source along each sky ray. The sky, water
   reflection and fog all read that table. This is a height-only approximation at the observer's sun
   elevation, not a full spatial or all-order atmospheric solution. Ground bounce is not included in it.
3. **Reduce painted horizon correction.** The former 70% replacement of the high-sun horizon becomes
   25%; the daylight saturation boost is reduced. The sky's irradiance compensation falls from 1.75
   to 1.35 because diffuse second scattering is now included explicitly.
4. **Moonlit atmosphere.** Night evaluates the same atmosphere toward the opposite light direction,
   with a low airglow floor and darker intensity than the former nearly uniform blue night gradient.
   Moon intensity remains artistic and fixed: phase is not included in the LUT inputs.
5. **Nearby shadow definition.** Terrain filtering narrows to half radius within 12 blocks, easing back
   to the previous radius at 48 blocks. Normal-offset bias follows that transition (1.1 versus 1.6 texels).
   Entity filtering narrows from 1.5 to 1 texel. This is distance-adaptive filtering, not blocker-based PCSS.
6. **Quality-scaled shafts.** Volumetric samples are 10 at 1024 shadow resolution, 12 at 1536, and 16 at
   2048 or above. The one-third-resolution target and existing pass count remain. High spends more samples
   on narrow lanes of light; Low retains its previous sample budget.
7. **Restrained water refinement.** More of the slope comes from the broad 8-block layer, less from
   the 4-/2-block layers. Fine glitter tilt is reduced. Reflection, refraction and the overall water style remain.

Cloud style and geometry were not redesigned. No global sharpening filter was added. Existing generated
normals, pixel relief and resource-pack PBR remain available.

## Verification

`JAVA_HOME=/workspace/jdk21 python tools/check_sky_model.py` checks the single-scattering quadrature
against a dense 256-by-256 reference over 90 rays. Mean relative channel error is **0.523%** over
262 channels above the small-radiance threshold. The 0.5.9 integration measured **11.30%** against that
same dense equation. This measures numerical integration accuracy, not resemblance to a photograph.

Fourteen full LUTs cover noon, low sun, twilight, night, clear weather and rain. All **344,358** checked
radiance/lighting channels are finite and nonnegative. The batch takes approximately 2–3 seconds on
this cloud CPU; individual tables are built asynchronously after initialization. The more accurate model
increases background CPU work. No free-performance claim follows from keeping GPU passes unchanged.

Gradle build and terrain/composite/volumetric GLSL validation pass. Runtime and capture results are
recorded in the standalone HTML report and capture manifest.

## Capture method and limits

Same copied world, seed `-5138383364446607098`, camera positions from the 0.5.9 manifest,
Minecraft 1.20.1, Sodium 0.5.13, Iris 1.7.6 for reference packs, 1280×720, FOV 70, gamma 0.5,
12-chunk render distance, vanilla textures, clear weather and a stopped day cycle.
Rethinking Voxels uses HIGH; Derivative Main community fork uses its source defaults.
Neither new reference pack was edited to make these captures. A chunk reload is used after initial
loading. Rethinking Voxels still shows far terrain/foliage gaps in this software/Iris client; its foreground
lighting and separately labelled official gallery are useful style evidence, not an artifact-free full-frame
benchmark. Animation, mobs, clouds,
reference sun paths and some sky settings differ. The images control terrain/camera/time, not every input.

Official Rethinking Voxels gallery images are separately labelled style examples with source links;
they are not same-world captures or evidence that Aurum implements voxel-traced local lighting.

The user reports stable **60 FPS on Intel UHD, High, 12 chunks, roughly 200 mods**. That is the reported
on-device baseline. Cloud software rendering validates compilation and appearance; it does not establish
0.5.10's frame rate on that machine. The exact Intel model, native resolution and frame-time distribution
were not supplied, so the report does not invent them.

## Remaining quality gap

Full HDR composition, coherent off-screen reflections, occluded coloured block lights and moving-camera
reconstruction remain larger architectural work. Sharper shadow filtering alone does not reproduce
Rethinking Voxels' voxel lighting. This release is a concrete atmosphere/sharpness iteration, not a claim
of visual parity with all four references.
