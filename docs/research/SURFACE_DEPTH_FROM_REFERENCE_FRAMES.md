# Surface depth from the supplied reference frames

Aurum `codex` · Research and engineering proposal · 26 October 2026

## Answer

**Yes: Aurum can produce this class of recessed, raised, sharply lit surface detail without requiring a LabPBR resource pack. Excalibur is a practical target.** Aurum must generate its own surface description and render it consistently on terrain and block entities. An ordinary pack can supply the colour art; the mod supplies generated height, fine normals and material response.

**No: an exact, artist-correct AVPBR result cannot be guaranteed automatically for every arbitrary texture.** Colour does not uniquely specify shape. Material profiles, structure detection, conservative fallbacks and optional overrides are needed. “Works with ordinary packs” is a credible goal; “recovers every artist's intended geometry from colour alone” is not.

The three screenshots supplied in chat are now the visual acceptance targets. This research analyses those stills directly. Full trailer playback remains incomplete; it is not needed to describe the visible target, and no additional video frames are claimed. No production rendering code or mod JAR is changed in this phase.

## What the three shots are asking Aurum to do

| Supplied shot | Observed target | Required response |
| --- | --- | --- |
| Cabin and Ender chest | Relief in the wooden surroundings and floor; clean chest/lid/latch edges; warm light separates small surfaces | Detail on terrain **and chest textures**, coherent response to local lights, restrained contact darkening and preserved emission semantics |
| Cut log | Stepped rings, bark channels, bright small edges and shaded recesses | Structured height, fine bevel/slope normals, view-dependent relief and height occlusion; not a global brightness emboss |
| Crafting table and coloured floor | Recessed grid and panel borders; fine floor grain; readable block edges | Selective top/side profiles, shallow fabric detail, stable filtering and suitable material roughness |

A still image does not prove which parts use parallax versus real geometry, normal mapping or painted shading. The chest and latch already have model geometry; some apparent edge depth can come from that. The pack author's documented settings and inspected material maps independently establish that height-based parallax and fine normal data are central to the reference workflow.

Soft backgrounds, bloom and warm lighting also affect the impression. They do not replace surface depth. The proposal preserves clear gameplay detail and does not depend on adding depth of field or retuning the entire sky/colour grade.

## What is verified about the reference

The official YouTube player response identifies the linked video as **“Minecraft Looks Amazing With This Texture Pack” by iambeen**, 254 seconds. Its description lists **AVPBR Retextured** and **NostalgiaVX**, with Nostalgia 5.0 also recommended. It lists an RTX 2080 SUPER system. Excalibur is a separate pack the user wants supported, not the pack identified in that trailer.

AVPBR's official project page embeds this exact video and recommends, for Nostalgia/Kappa:

- parallax occlusion mapping enabled;
- **64+ POM samples**;
- **32+ self-shadow samples**;
- **slope-normal calculation enabled**;
- texture AO and material reflections.

These are author recommendations, not verified settings for each supplied screenshot. The public NostalgiaVX description describes combined light-propagation-volume and path-traced lighting. Matching the material-depth component does not require reproducing that whole lighting system on Intel UHD.

### Measured material data

The official public **AVPBR Retextured R5 demo**, Modrinth version `HC44ldY7`, was downloaded and its published SHA-512 checked. This is a later public reference sample, not an established match to the trailer's exact revision. No paid download, shader code or restricted full pack was accessed. Its textures are not redistributed in Aurum or this report.

The ZIP has 2,513 `_n` and 2,563 `_s` images. Counts alone do not establish useful relief on every material. Five relevant maps were measured using **opaque colour texels only**, excluding unused transparent skin space:

| Material | Colour / normal size | Vanilla colour size | Discrete height values | Brightness–height correlation |
| --- | --- | --- | ---: | ---: |
| Oak cut face | 128×128 | 16×16 | 3 | −0.709 |
| Oak bark | 128×128 | 16×16 | 6 | +0.990 |
| Crafting-table top | 128×128 | 16×16 | 2 | +0.147 |
| Blue wool | 128×128 | 16×16 | 6 | +0.938 |
| Ender-chest skin | 512×512 | 64×64 | 6 | −0.013 |

The pack's colours are constant within each 8×8 group corresponding to a vanilla texture pixel in these samples. Yet about **68–77%** of the log, table and wool groups contain varying normal directions. This is extra surface shaping **inside an otherwise flat colour pixel**, consistent with fine bevel/slope detail. Increasing colour resolution alone would not add it.

The ender-chest height also varies independently of colour: approximately 35% of its opaque texels share an RGB colour with other texels assigned a different height. That rules out exact reconstruction by a colour-value-only lookup. Spatial/material context can help, but cannot universally recover missing authored information.

The opposite polarity of bark and cut wood is especially important: globally treating brighter pixels as higher fails one of those materials; globally reversing it fails the other. The crafting-table grid needs selected structure, not exaggerated depth on every painted detail.

## Which techniques produce the effect

| Technique | Contribution | Limit |
| --- | --- | --- |
| Fine generated normal / bevel mapping | Small edges turn toward light and catch highlights; cheap compared with a view march | Does not itself move texture features with the camera or produce occlusion |
| Parallax occlusion or relief mapping | Shifts the visible surface lookup by depth, producing recesses and view-dependent overlap | A height-field approximation; not general new mesh geometry or correct silhouettes from every angle |
| Texel-column traversal with side normals | Preserves small, stepped walls and pixel-art edges | Requires stable, meaningful height data; noisy guesses produce spikes and stripes |
| Height self-shadow and cavity shading | Grooves block direct light; recesses receive less fill | Needs correct source direction and a controlled budget; cavity shading is not full GI |
| Roughness and specular filtering | Makes edge highlights readable without turning everything into polished plastic | Brightness is not a reliable metal/roughness classifier |
| Actual model geometry | Supports silhouettes, lid motion, gaps and existing model trim | Extruding every texel throughout the world adds substantial geometry and rebuild cost |

The recommended approach is **fine bevel normals + shallow structured relief + coherent lighting**, with bounded near-field parallax. Sharpening is the final readability adjustment. A bigger unsharp-mask value cannot create those missing surfaces.

## What must change in Aurum

### 1. Prove material coverage, including Excalibur

The existing baseline showed almost no POM-only change on its Excalibur overview board, despite generated normals visibly working. That is an unresolved coverage result, not a passed depth test. First record the actual sprite or texture ID, chart bounds, height range, POM eligibility and active path for each target face.

The pinned Excalibur 1.20-compatible sample has no conventional `_n`/`_s` maps. It includes a **48×48 oak-plank atlas**, outside the current aligned power-of-two POM shortcut. Its crafting-table variants use 16×16 textures, and its Ender-chest skin is 64×64. The 48×48 incompatibility does **not** explain every inactive Excalibur material.

Use explicit texture-region metadata instead of assuming a 16/32/64/128 tile derived from UV position. Some atlas sprites contain multiple cropped faces. Relief must remain within the correct **face chart**, not merely somewhere within the whole atlas image. Preserve rotated/mirrored UVs, tiling intent and authored models. Check the existing sixteen-sampler GL 3.3 layout before choosing metadata storage; do not silently add a GL 4.x requirement.

### 2. Generate structure and fine normals from one surface

At resource reload, analyse texture-local edges/regions and known material class. Generate bounded height and derive compatible shading normals, with fine bevel shaping inside coarse art pixels. Keep geometry, albedo, normals and roughness conceptually separate.

Suggested profiles are cut wood, bark, planks, masonry, fabric and conservative generic solids. A profile uses the current pack's structure and colour, not copied AVPBR texture data. Block identity is a useful prior but not sufficient: a pack can redesign that block completely. Confidence limits and a per-material opt-out must protect painted motifs and uncertain textures.

Compare a limited higher-detail normal cache with analytic near-field bevel shading before choosing the implementation. Native-resolution height cells can preserve the stepped art while fine normals shape their edges. Do not assume a full 8× atlas upscale is necessary or affordable.

### 3. Extend the material system to chests

`model_light.glsl` currently computes model colour from geometric normals and shared diffuse lighting. It does not sample generated detail maps, perform model POM or evaluate the terrain material/specular response. `AtlasBaker` handles the terrain atlas, not chest skins. Therefore a terrain-only improvement cannot finish the first supplied shot.

Add a bounded cache for ordinary block-entity textures and suitable UV-chart metadata. Use existing UVs and per-face bounds; keep lid/base/latch charts separate, and bind material data correctly when normal and Ender chests share a shader program. Preserve alpha, overlays, full-bright exceptions and native inventory behaviour. The lid's detail must follow its UVs and transformed part normals while opening and closing.

Terrain and models should call one material/surface evaluator; their asset caches may differ. Unknown modded renderers need an explicit fallback. Do not deform mobs or invent relief across arbitrary skin seams by default.

### 4. Light the small surfaces coherently

The current shared evaluator uses the detailed normal for sun/moon illumination but passes the **geometric** normal into local block-light evaluation. POM's height shadow follows `u_LightDir`, not each local lamp. That misses precisely the warm indoor relief visible in the chest and crafting-table shots.

Keep separate geometric and shading normals: geometric normals for robust visibility offsets and hemisphere safety, detailed normals for local light response. Passing a detail normal blindly into the cache offset would risk visibility errors. Test bounded local height occlusion for a dominant nearby source, not a 32-source height march at every pixel.

The current LambDynamicLights compatibility path supplies propagated light coordinates, not general per-source directions. Accurate small shadows from moving held lights require a source adapter or a clearly limited fallback. A scalar light value cannot recover where its source is. Keep green emission and latch geometry separate from relief; coloured paint is not automatically an emissive material.

### 5. Make the detail stable and sharp in motion

Match colour/height/normal LOD decisions. Use gradual distance, projected-texel and grazing-angle fades; avoid the existing hard footprint cutoff becoming a visible switch. Keep data attached to the material/UV chart, and filter/renormalize normals appropriately. Use specular anti-aliasing or roughness limits where fine highlights would otherwise sparkle.

Compare sharpening 0 and the current 65 at the same camera. Preserve original colour and texture grouping. Reject edge halos, ringing, crawling grain and any “crisp” mode that becomes unstable when moving.

## Can it work with any resource pack?

| Pack or asset | Credible support level |
| --- | --- |
| Ordinary opaque vanilla-style packs | Broad automatic support for generated normals, bevels and bounded relief |
| Excalibur | Feasible; explicitly handle its alternate textures, 48×48 atlas, custom model charts and chest skins; validate this pinned release first |
| Packs with arbitrary art/material redesigns | Safe generic detail and optional profiles; exact intended height is not inferable in every case |
| Existing LabPBR packs with LabPBR disabled | Ignore external maps and use the same internal-generation path; do not silently re-enable authored data |
| Non-square, cropped, rotated or animated textures | Require appropriate chart/animation handling; otherwise use a safe fallback |
| Transparent foliage, glass, emissive patches and unknown entity renderers | Material-specific handling; universal opaque depth extrusion is inappropriate |
| Packs that replace core shaders | Compatibility must be tested; current fingerprinted adapters deliberately retain modified pack shaders |

The result should preserve each pack's art style, not make every pack look like AVPBR. Existing 3D Excalibur models remain valuable and should be respected.

## Performance approach for Intel UHD

Use a reload-time bake and a bounded material cache; avoid AI inference or mesh rebuilding every frame. Keep the baseline inexpensive and concentrate the view march on nearby visible opaque surfaces. Evaluate lower, adaptive traversal budgets before increasing them. The reference author's 64+/32+ settings are not the default budget for Aurum.

For scale: two 1024×1024 RGBA8 atlases with mips are about **10.67 MiB**. Increasing both dimensions by 8 would make those two atlases about **682.67 MiB**, before the colour atlas and staging memory. A 512×512 two-map chest cache is about **2.67 MiB**, while native 64×64 maps are about **42.7 KiB**. These are calculated examples, not measured target-device costs. Bound cache memory, check base-atlas as well as optional upscale allocations, and compare alternatives on the actual GPU.

The reference video's RTX 2080 SUPER and NostalgiaVX lighting are not evidence of Intel UHD performance. The goal is to reproduce the material-depth impression within Aurum's budget, then measure GPU frame times and 1% lows in the user's world/profile. Preserve the reported 60-FPS target rather than declaring it achieved from software-renderer tests.

## Implementation and acceptance sequence

1. **Diagnostic gate:** log target face coverage and prove flat / normals-only / relief changes on both vanilla and Excalibur. Resolve the inactive Excalibur control first.
2. **Known-surface gate:** render a generated, deliberately specified log-ring/table surface to verify traversal, bevel normals and small shadows independently of height inference. Label this a technical test, not Minecraft reference parity.
3. **Generation gate:** produce profiles and one coherent normal/height description. Include false-depth controls—flat paint, inverted wood polarity, gravel noise, low-contrast material and an emissive patch.
4. **Chest gate:** support the Ender-chest material path, multiple skin bindings and lid motion, with the same surface evaluator.
5. **Lighting/motion gate:** noon, sunset, moon, one/multiple static lamps, moving held source; slow camera translation, orbit and grazing views. Test documented compatibility fallback separately from directional source support.
6. **Quality/performance gate:** compare captured results at identical settings, then measure the actual Intel UHD machine. Do not ship a new default just because one still looks attractive.

Recreate all three supplied situations in a disposable save: a candle-lit wood cabin with opening chests; two exposed log faces outdoors; and a crafting table over blue/yellow fabric with local lighting. Use LabPBR off throughout. Keep exposure, grade, time, FOV and camera fixed between feature toggles. Include Excalibur custom models, atlas boundaries, a resource reload and a distant/grazing transition. The current previous board captures remain baseline evidence, not new prototype results.

## Evidence and provenance

- User-supplied cabin/chest, cut-log and crafting-table/floor stills in this conversation: direct visual targets. Supporting creator images in the HTML are separately labelled and are not represented as these trailer frames.
- [AVPBR official project and recommended settings](https://modrinth.com/resourcepack/avpbr-retextured) and [project API](https://api.modrinth.com/v2/project/avpbr-retextured).
- [Official public R5 version](https://api.modrinth.com/v2/version/HC44ldY7); ZIP SHA-256 `97856bf1bb271dd3b6a84d5cb4764a6b35ff598269a4243cad6b6106aa52a1f1`. Raw textures are not bundled with the report.
- [NostalgiaVX public author page](https://rre36.com/nostalgiavx): documents its combined lighting approach, not an inspected private shader implementation.
- [LabPBR material standard](https://shaderlabs.org/wiki/LabPBR_Material_Standard): `_n` stores normals, material AO and height; `_s` stores specular/material information. These are data conventions, not the only possible way for Aurum to obtain surface data.
- [Parallax mapping explanation](https://learnopengl.com/Advanced-Lighting/Parallax-Mapping): distinguishes normal detail, height-based parallax and true displacement, including outline and grazing-angle limits.
- Aurum source: `AtlasBaker.java`, `pbr.glsl`, `lighting.glsl`, `surface_light.glsl`, `model_light.glsl` and the core shader adapter. Renderer source remains at `b8f5a5b`; no implementation claims are made by this research.

The material measurements are reproducible with `tools/research/surface_depth_material_audit.py`. The report includes statistics, charts, source references and earlier clearly labelled baseline captures. It does not redistribute copyrighted material textures or claim to have rendered a new premium-depth implementation.
