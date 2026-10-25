# Proposal: richer world colour and shade

Research and proposal for Aurum, 2026-10-23. No rendering changes are part of this proposal.
Photon and Complementary Reimagined are **visual references**; Aurum should keep its own identity and its
Intel UHD 620 performance target. The image links below point to the creators' official Modrinth galleries.
They are shown for review and are not copied into this repository.

## Visual reference board

### 1. Photon: clear daylight, readable shade

![Photon official gallery: daylight buildings, foliage and soft clouds](https://cdn.modrinth.com/data/lLqFfGNs/images/c841304867fb5121ebbd8bfd2d48e493ffd118ac.png)

[Photon gallery image 8](https://modrinth.com/shader/photon-shader/gallery) — The sky is pale, but the lit
faces, shaded walls and foliage interiors have different values. Whites are bright without turning the
entire frame white. The dark greens preserve detail. The reference image's build and settings are unknown.

### 2. Photon: sunset colour reaches the world

![Photon official gallery: warm sunset over dark landscape](https://cdn.modrinth.com/data/lLqFfGNs/images/38d736b56ff2e374efb8005249a98a7db8580816.png)

[Photon gallery image 0](https://modrinth.com/shader/photon-shader/gallery) — The low sun gives a strong
orange sky, peach clouds and warm exposed terrain, while the unlit valley stays much darker and cooler.
The contrast between warm light and cool shade creates much of the colour richness.

### 3. Complementary: contact depth in ordinary daylight

![Complementary official gallery: village in daylight](https://cdn.modrinth.com/data/HVnmMxH1/images/bfe0d5ba1b4f5a4f071921818b967f31e1d4422a.png)

[Complementary gallery image 5](https://modrinth.com/shader/complementary-reimagined/gallery) — A warm
lit roof sits beside cooler shaded stone; log faces, recesses, eaves and grass tufts retain local depth.
This is more than a global warm filter.

### 4. Complementary: warm peaks, dark structure

![Complementary official gallery: golden light across mountain and lake](https://cdn.modrinth.com/data/HVnmMxH1/images/35b1b4eb6a186297fe039995449d17608f510560.png)

[Complementary gallery image 6](https://modrinth.com/shader/complementary-reimagined/gallery) — Warm
light runs across selected slopes; adjoining slopes and the cave remain dark. The sky and water share
the peach light. The scene's colour comes from selective lighting and spatial contrast.

Aurum's existing [historical noon comparison](img/noon_before_after.jpg) and
[historical golden-hour comparison](img/golden_before_after.jpg) show progress through earlier versions,
but are **not** current captures or scene-matched comparisons against these packs. They cannot prove a
current visual gap or a performance result.

## What the code and previous research establish

Previous [daylight research](LOOK_FOG_LIGHT.md) measured an earlier Aurum frame at mean lightness 0.43,
versus 0.55–0.61 for selected reference images. After exposure and colour changes, the reported Aurum
range rose only to 0.45–0.47; warmth was still negative. Those numbers came from different images and
an older Aurum build, so use them to frame a hypothesis, not as current acceptance thresholds.

Current Aurum combines direct sun, a single sky ambient colour, a global ground ambient colour, a 1×1
frame-level bounce tint, vanilla block-light level and AO in `include/lighting.glsl`. The sun and ambient
colours are computed once per frame in `director/Atmosphere.java`. There is a real cached shadow map,
baked/generated normals, and an eight-sample half-resolution screen-space contact shadow. The contact
shadow multiplies the already mapped scene colour in `post/composite.fsh`; it has no way to distinguish
sunlight, skylight, and a nearby torch at that point. The global bounce tint does not know whether a
specific wall is beside red terracotta or green leaves. There is no cloud-shadow term in terrain lighting.

Photon's source uses a sky-light representation evaluated by direction, separates sun, sky and block light,
provides simple bounced light near shadowed surfaces, and has material-dependent subsurface light for
plants. More expensive options exist, but those are not required for this proposal. Complementary's
source explicitly blends separate noon, sunset, night and rain colours for sun and ambient light, then
applies directional and material-aware lighting. The gallery confirms the visual result, not the cost of
any one technique.

Source anchors: Aurum `src/main/resources/assets/aurum/shaders/include/lighting.glsl`,
`src/main/resources/assets/aurum/shaders/post/{ssao,composite}.fsh`,
`src/main/java/dev/impeccable/aurum/director/Atmosphere.java`,
`src/main/java/dev/impeccable/aurum/sky/SkyLut.java` and
`src/main/java/dev/impeccable/aurum/render/shadow/ShadowRenderer.java`;
[Photon diffuse lighting](https://github.com/sixthsurge/photon/blob/main/shaders/include/lighting/diffuse_lighting.glsl)
and [Complementary light/ambient colours](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/main/shaders/lib/colors/lightAndAmbientColors.glsl).
These are technique references only; copy no source or assets.

## Proposed look

**Daylight:** Photon-like clarity: bright warm-neutral sun, gently cool skylight, near-white highlights,
distinct shaded faces, no broad haze or saturation boost. Grass should remain green but not neon;
stone and wood should keep texture and face variation. **Golden hour:** Complementary-like separation:
amber direct sun on faces that see it, peach light near the horizon, cooler but readable shade, and
stronger silhouettes in backlight. **Rain:** lower direct contrast and richer local texture, not uniformly
dark or blue. Keep Aurum's golden-hour signature rather than turning every biome into the same palette.

| Priority | Change to prototype | Why it should help | Cost/risk to measure |
| --- | --- | --- | --- |
| 1 | Rebalance sun, sky and ground light by time and weather; tune exposure and highlight rolloff together | Makes bright faces and shaded faces carry distinct colour without a global tint | CPU-side tables/uniforms are cheap; check sky/terrain mismatch, clipping and night adaptation |
| 2 | Evaluate ambient sky by surface direction using a very small set of coefficients derived when the sky LUT updates | Side faces and undersides stop sharing almost the same ambient tint; uses existing sky work | A few per-fragment arithmetic operations in the expensive terrain pass; profile Low first |
| 3 | Apply contact occlusion mainly to diffuse ambient, with light-source awareness where feasible | Crevices gain depth without dirtying lit surfaces and torchlight | Needs care with existing vanilla AO, half-resolution SSAO and hand/foliage edges |
| 4 | Add restrained, material-aware foliage transmission and local bounce, especially near sunlit walls | Leaves and coloured terrain gain depth without raising whole-frame exposure | Avoid uniform glowing leaves or a costly per-pixel search; use existing material IDs and cached data |
| 5 | Optional low-frequency cloud shadow over terrain | Connects cloud pattern to world lighting and creates large-scale variation | Temporal stability; update with cloud drift and avoid an extra terrain texture fetch on Low |

Start with **1 and 2** as a single lighting prototype. Build **3 and 4** only after matched images show
their contribution. Treat **5** as an optional Medium/High experiment. This order targets the world
lighting complaint before expanding sky and water work.

## Review and acceptance before implementation

Use a fixed test world, coordinates, camera/FOV, time, weather, resource pack, gamma, resolution and preset.
Capture Aurum and the reference in analogous scenes: noon village/stone/foliage; forest shade; sun-facing and
sun-away sunset; rain; and a torchlit interior. Compare lit surfaces, shaded surfaces, local contacts, foliage,
highlight clipping, and biome colour separately. The gallery above defines the desired *relationships*
between them, not exact pixel colours across unrelated worlds.

For every candidate, compare the same Aurum scene before/after, plus F8 `Terrain`, `Aurum post`, `Frame
(GPU)` and FPS on the user's UHD 620 in the same power mode. Keep Low, Medium and High separate. Stop or
scale back a feature if its visible gain does not justify its measured cost. Until that capture and benchmark,
neither visual parity nor a 60 FPS result is established.

Implementation note (0.5.4): the first lighting prototype now tunes sun/sky colour, samples directional sky
ambient from the sky LUT, applies existing terrain AO primarily to ambient light, and adds restrained leaf
transmission. Cloud shadows and local colour bounce remain proposals. The build and a software OpenGL world
smoke test passed; matched reference captures and UHD 620 benchmarks are still needed to judge the result.
