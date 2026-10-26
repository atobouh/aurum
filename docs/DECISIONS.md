# Decisions

Where Aurum differs from `AURUM_SPEC.md`, or where the spec left a choice open. One line of reason each.

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-10-24 | 0.6.1: sky-occluded diffuse fill under cloud shadows and less compounded daytime canopy attenuation; night/specular sky visibility retained | User's actual 0.6.0 High / LabPBR-off footage exposed dark land beneath a bright sky; controlled forest and seasonal foliage checks narrow the cause |
| 2026-10-21 | Loom **1.11.8** on Gradle **8.14.3**, not the newest Loom (1.19.2) | Loom 1.19 needs Gradle 9.8 and a Java 25 runtime; 1.11 runs on JDK 21, which is easier to install for local builds |
| 2026-10-21 | Building needs **JDK 21**; the jar targets Java 17 | Loom 1.11 runs on 21; `options.release = 17` keeps the mod loadable on the game's Java 17 |
| 2026-10-21 | Fabric Loader 0.20.1, Fabric API 0.92.12+1.20.1, Yarn 1.20.1+build.10 | Latest for 1.20.1 on fabricmc.net today |
| 2026-10-21 | Pinned Sodium **0.5.13**; fabric.mod.json accepts `>=0.5.8 <0.6` | The game still launches on other 0.5.x builds, but terrain hooks only switch on for 0.5.13 and the player is told why |
| 2026-10-21 | GPU timers use `GL_TIMESTAMP` queries, not `GL_TIME_ELAPSED` | Elapsed queries cannot nest; ours do (World contains Sky, Terrain...) |
| 2026-10-21 | Timers wrap call sites inside `WorldRenderer.render` / `GameRenderer` | Sodium `@Overwrite`s `renderLayer` and `renderClouds`; wrapping call sites works regardless of who owns the callee |
| 2026-10-21 | Settings screen uses vanilla `SimpleOption` + `OptionListWidget` | Native look, sliders and tooltips with no extra library; Mod Menu is optional |
| 2026-10-21 | Keys: **F7** settings, **F8** overlay (rebindable) | Unused by vanilla |
| 2026-10-21 | Strength settings are integer percentages | Readable sliders and JSON; 100% is the intended look |
| 2026-10-21 | Terrain normals will come from screen-space derivatives | Sodium 0.5.13's vertex format has no normals (see SODIUM_NOTES) |
| 2026-10-21 | Phase 0 is verified by launching the client headlessly (Xvfb + Mesa llvmpipe) | Catches crashes, mixin failures and shader compile errors before the user tests. Software GL says nothing about FPS or how Intel's driver behaves, so the user's report still gates each phase |
| 2026-10-21 | Licence: all rights reserved (placeholder) | Spec default until the user chooses |
| 2026-10-21 | Terrain hooks will switch on by checking that Sodium's shader lines match what Aurum edits, not by version string alone | Sodium's terrain shaders changed between 0.5.11 and 0.5.13, so the check is needed, but matching the actual code accepts any build where it is unchanged |
| 2026-10-21 | Overlay shows the window size | The user plays windowed; the size is needed to read FPS numbers |
| 2026-10-21 | Waving plants and leaves added to Phase 1 (spec feature 12) | User request; reuses the per-sprite material data Phase 1 builds anyway |
| 2026-10-21 | Terrain shader is replaced only if Sodium's six shader files match 0.5.13's SHA-256, and only after a test compile + link on the player's driver | Any matching Sodium build works; a mismatch or a driver error leaves vanilla terrain instead of crashing Sodium |
| 2026-10-21 | Sky table is indexed by (view elevation, horizontal angle to the sun) for the current sun elevation, not by (sun elevation, view-sun angle) | A 2D table keyed on sun elevation cannot represent zenith vs horizon; rebuilding per sun position costs nothing per frame |
| 2026-10-21 | Sky table built on the CPU on a background thread (single scattering, Rayleigh + Mie + ozone), ~27 ms per build, rebuilt when the sun moves 0.15 degrees | The user is CPU-bound but has spare cores; a worker thread costs no frame time, and the CPU copy gives terrain its light colours |
| 2026-10-21 | Skylight boosted 2.5x and horizon whitened at high sun | Single scattering misses multiple scattering: shade too dark, noon horizon yellow |
| 2026-10-21 | Tonemap (AgX-style, own curve) runs in the terrain and sky shaders; the post pass only grades | Minecraft's framebuffer is 8-bit; an HDR (RGBA16F) scene target would double bandwidth on the UHD 620 |
| 2026-10-21 | "Punchy" = AgX + 1.3x saturation after the curve | AgX greys colours by design; the user wants pop |
| 2026-10-21 | Fog lives in the combined post pass (depth-based), vanilla terrain fog is pushed out while Aurum fog is active | One pass fogs terrain, entities, particles and clouds consistently (spec rule 1) |
| 2026-10-21 | The hand gets its own depth texture, swapped in around vanilla's pre-hand depth clear | Keeps world depth for fog without a depth copy, and the hand is never fogged |
| 2026-10-21 | Render-distance edge fade uses horizontal distance | Chunks load in a cylinder; vanilla's terrain fog does the same |
| 2026-10-21 | Terrain normals: derivative face normal + baked per-texel slope map (luminance), cotangent frame from derivatives | No vertex normals or tangents in Sodium's format; works for every face orientation |
| 2026-10-21 | Plants use an upward, softly wrapped normal; leaves blend toward up | Cross-shaped plant quads lit by their real normals look harsh and stripy |
| 2026-10-21 | Material ids (which sprites move) come from block classes/tags plus a geometric rule (cutout, no collision, diagonal quads) | Makes modded plants sway in a 200-mod pack without a list |
| 2026-10-21 | Sugar cane, bamboo, lily pads, torches and fire never move | Columns would tear when each block anchors separately; the rest look wrong moving |
| 2026-10-21 | Default tonemap is now "Aurum Filmic" (hue-preserving curve on max channel + white balance); AgX kept as an option without extra saturation | User found 0.1.0 yellow and cheap; measured blue channel collapse to ~10% (see docs/research/LOOK.md) |
| 2026-10-21 | Grade (saturation, vibrance, contrast) done in Oklab | Scaling chroma in a perceptual space cannot shift hue |
| 2026-10-21 | Sky table axes changed to sin(a/2) and sqrt(sin e) | Removes asin/acos per pixel; sky measured 1.35 ms on the UHD 620 vs a 0.3 ms target |
| 2026-10-21 | Composite does one sky lookup and one tonemap for fog and edge fade | Post measured 2.7 ms vs a 1.5 ms target |
| 2026-10-21 | Clouds (Phase 4) will be stylized, layered 2D rather than realistic | User preference (Story Mode-like), see docs/research/LOOK.md |
| 2026-10-21 | Shadow casters are drawn with Sodium's own DefaultChunkRenderer.render, given Aurum's section lists, a virtual camera at the light, and the light's matrices | Reuses Sodium's GPU meshes (no CPU re-meshing; the user is CPU-bound); Sodium's face culling from the light camera keeps only light-facing faces |
| 2026-10-21 | The replaced terrain shader has a depth-only mode (u_AurumMode = 2) for the shadow pass | One program, one fingerprint check; plants (not leaves) are dropped there so grass never casts shadows |
| 2026-10-21 | Shadow map is double-buffered; rebuilds are sliced over 3 frames into the back map, then swapped | No frame-time spikes (spec rule 6) while the front map stays valid |
| 2026-10-21 | Rebuild triggers: light turned 0.23 degrees, player moved 4 blocks, or a chunk mesh within range was rebuilt (rate-limited to every 20 frames) | Lazy updates (spec feature 4) |
| 2026-10-21 | Shadow direction never drops below ~7 degrees; shadows fade out between ~3 and ~9 degrees of light elevation | An orthographic map cannot hold shadows that stretch to the horizon |
| 2026-10-21 | Entities get a separate, undistorted 512/1024 map within 16 blocks, redrawn every frame | Vanilla entity shaders cannot apply the terrain map's radial distortion; vanilla blob shadows are skipped only for those entities |
| 2026-10-21 | Leaves keep 35% of direct light when shadowed | Stand-in for light passing through leaves; fully shadowed crowns looked black at golden hour |
| 2026-10-21 | Below-horizon sky uses the sky ~2 degrees up (desaturated, darker); fog colour excludes the bright glow around the sun | The horizon row stretched below the horizon made a hard red band; fog toward the sun washed terrain pink-white (user feedback) |
| 2026-10-21 | Modded leaves detected by name (leaves/leaf/foliage/needles) + full cutout cube, and logged | User's custom-tree mod leaves did not sway |
| 2026-10-21 | Tone curve linear through mid-tones with a soft shoulder above 0.6 (replaces log-space filmic) | Measured: sunlit surfaces capped at ~0.62 on screen; references reach 0.7-0.9 (docs/research/LOOK_PHASE1_2.md) |
| 2026-10-21 | Intentional golden-hour grade (amber sun, peach shade, pink-lavender sky, sun glow in haze) | References are warm in every sunset shot; physics alone was near-neutral |
| 2026-10-21 | Shadow map cross-fade (3 maps) + 0.14 degree steps; shadows capped at ~10 degree elevation | User saw shadows step every second and stretch across the map |
| 2026-10-21 | Every phase starts with screenshot research and a measured look target | User requirement; Phases 1-2 missed the look without it |
| 2026-10-21 | Phase 4 (sky extras) built before Phase 3 (water) | User asked for clouds, golden hour, rays and bloom first; water research is ready for next |
| 2026-10-21 | Clouds take their shapes from Minecraft's `clouds.png` (baked to an exact distance field at 4x, outer corners rounded) and are sphere-traced as rounded slabs | Story Mode / Nostalgia look the user asked for; resource-pack cloud shapes keep working; no noise, so the clouds stay Minecraft-like |
| 2026-10-21 | Clouds are drawn at 1/2 resolution in the post stage, not in the sky pass, and applied in the composite where the ray reaches a cloud layer before the terrain (full-resolution analytic test) | Clouds must also cover terrain when seen from above; the analytic test keeps tree silhouettes sharp against half-resolution clouds |
| 2026-10-21 | Second, higher cloud layer: same shapes 2.6x larger, flat, soft, slower drift | "Layered" look (depth) for one texture read per pixel |
| 2026-10-21 | Vanilla clouds are skipped at the call site while Aurum clouds are on; the vanilla Clouds option set to Off turns Aurum's off too | One place to switch clouds off, as players expect |
| 2026-10-21 | Bloom is on in every preset, including Low (spec said Medium+) | Bloom is most of the measured golden-hour gap; the chain is 1/4 resolution and below (5 tiny passes). User can switch it off; gate on their GPU timer report |
| 2026-10-21 | Bloom works on the tonemapped 8-bit scene with a soft threshold at 0.85 | Aurum tonemaps per surface (see the Phase 1 decision on 8-bit targets); only the sun, lava and glare pass the threshold |
| 2026-10-21 | God rays: sky mask (depth, clouds) at 1/4 resolution + 16-sample jittered radial blur, added in the composite | GPU Gems 3 ch. 13; cloud alpha in the mask gives rays through cloud gaps |
| 2026-10-21 | Lens-flare occlusion is a 1x1 GPU pass (16 depth taps + cloud alpha, eased) read directly by the composite | No CPU readback at all, so nothing can stall; same effect as the spec's one-frame-late read |
| 2026-10-21 | Lens flare is procedural in the composite (halo, streak, 4 ghosts), subtle | References show little or no flare; avoids extra draw calls |
| 2026-10-21 | Composite uses texture units 3-5 for rays, bloom and flare | Minecraft's GlStateManager tracks only 12 units; terrain rebinds 3-5 before each of its draws |
| 2026-10-21 | Two cloud styles (Blocky = Nostalgia layered boxes, Soft = Better Clouds-like volume), one shader source compiled twice | User request after 0.3.0; a define keeps each variant's loop lean |
| 2026-10-21 | Flat high cloud sheet removed | User saw it as pixelated squares; depth now comes from a real second 3D layer (blocky) or smooth noise cirrus (soft) |
| 2026-10-21 | Blocky cloud edges: closest-approach anti-aliasing + edge re-steepening in the composite; optional full-resolution clouds (on in High) | Fixes "blurry/pixelated close-up" without paying full resolution on Low/Medium |
| 2026-10-21 | Cloud height slider (+0 to +160 blocks) | The user's world has high floating islands; vanilla's 192 can sit low there |
| 2026-10-21 | Volumetric light from the shadow map at 1/4 resolution (12 steps), added over terrain only; dims shadowed haze | User: rays "shy" vs references; the references' beams come from shadowed vs sunlit air, which a screen-space blur cannot make. Over the sky it double-counted the sky's own glow |
| 2026-10-21 | "Enclosed" factor from sky light at the eye (any height) drives cave/room beam strength | The cave factor only counts below y 70; rooms and overhangs need beams too |
| 2026-10-21 | Every test render is judged side by side with its matching reference shot | User rule: Aurum must look on par with the references, not like a cheap copy |
| 2026-10-21 | Blocky clouds: no bake-time rounding (slab rounding only, 2.5 blocks); coverage may only shrink in clear weather; clear patches by whole cells from a baked noise field | Side-by-side with Nostalgia: larger rounding made pills, growth made blobs, per-pixel fields tore edges |
| 2026-10-21 | Default cloud height +24 blocks (y 216); slider 0-160 | +80 made clouds tiny and sparse vs references; vanilla 192 looked low to the user |
| 2026-10-21 | Screen-space rays mostly over occluders (trees, terrain, clouds), cubed mask | Over open sky they washed the frame (look lab: darks 11% -> 2%) |
| 2026-10-21 | Volumetric light: broad phase, 0.16 density, full strength only where lit and shadowed air alternate | User saw no beams through tree gaps or into caves; the forward-only phase vanished from the side |
| 2026-10-22 | Screen-space god rays only over open sky; beams over terrain only from the shadow map | A 2D radial blur painted glow over hills hiding the sun (user) |
| 2026-10-22 | Volumetric light at 1/2 resolution with an edge-aware 3x3 upscale keyed on march length | Bilinear upscaling drew flaky white outlines on silhouettes (user) |
| 2026-10-22 | Aurum bakes its own cloud layout (rectangle clusters + second tier + puffs); resource pack layout optional | User found vanilla's streak layout ugly; rectangle clusters match the Story Mode look side by side |
| 2026-10-22 | Blocky clouds 14 blocks thick, 4.5-block rounding, rim glow, belly shading | Side-by-side volume comparison with Nostalgia (user) |
| 2026-10-22 | Cloud drift 3x vanilla, per-frame accumulation, speed slider | Movement was not apparent (user) |
| 2026-10-22 | Wind: noise-bent wavefronts plus per-plant hashed phase and strength | Grass moved in lockstep stripes (user) |
| 2026-10-22 | Fog clear zone (32 blocks, 20 in rain), halved golden/rain densities, 88% cap, 30% neutral haze | User: dawn fog was a flat pink soup; measured contrast 0.10-0.13 vs references 0.16-0.30 (docs/research/LOOK_FOG_LIGHT.md) |
| 2026-10-22 | Daylight: exposure +13%, warm sun at all hours, shade 35% less blue, saturation 92%, day-only sky grade | Measured: ours darker, cold blue and oversaturated vs Bliss/Complementary/Photon |
| 2026-10-22 | Screen-space ambient occlusion (half res, 8 samples, 1.8 blocks, edge-aware upscale), strength from the AO slider | "The way the shadow embraces the world" (user, Bliss): contact shading under cover |
| 2026-10-22 | Look lab resets weather and removes test structures before each run | A run was misread because rain and a test wall were left in the world |
| 2026-10-22 | Water style B (Crystal Clear), Photon as the reference | User choice from docs/research/WATER_PROPOSAL.md |
| 2026-10-22 | Opaque colour + depth copied before the translucent pass; water written opaque (alpha 1) over it | Clear water, refraction, absorption and shore edge need what lies under the surface |
| 2026-10-22 | Screen-space reflections of terrain in water on Medium/High (spec had SSR out of scope) | Accepted with the water proposal; Low keeps sky-only reflections |
| 2026-10-22 | Translucent program detected from Sodium's pass (reverse order) in the shader-interface mixin; water from a material id (9) in the atlas | One program, no new vertex data |
| 2026-10-22 | Ripple normal map bound on texture unit 12 with raw GL | GlStateManager tracks only 12 units and throws above them |
| 2026-10-22 | Volumetric light: realistic single-scattering density (1.5%/lit block clear air, x4 haze, x8 caves), small all-round phase, 1/4 resolution, 12 steps | User: blurry radial stripes and a foggy veil over nearby shadows; 0.4.0's density was ~10x too high |
| 2026-10-22 | Water glitter: separate glint normal from two very fine ripple layers on skewed integer grids | User: Photon's glitter sold the style; a single axis-aligned fine layer showed a dot grid |
| 2026-10-22 | Sun shape option: round or square (Minecraft-style, crisp core, square halo, reduced tight glow); screen rays start away from the sun's face | User reference: a square sun from a video |
| 2026-10-22 | Master on/off (F6 and settings) | User request; off = Sodium visuals with no Aurum passes, for A/B looks and FPS |
| 2026-10-22 | No underwater work | User: not wanted; underwater keeps the vanilla path |
| 2026-10-22 | Contact shadows: fixed 4x4 rotation pattern + matching 4x4 edge-aware average, partial weight for thin occluders, radius 1.4, strength 0.6 | User: dark smearing blotches on grass and mobs when moving (per-pixel random noise crawled) |
| 2026-10-22 | Square sun evaluated only within ~18 degrees of the sun, edge width capped | Unbounded fwidth at 90 degrees from the sun drew a dotted white line across the daytime sky (user) |
| 2026-10-22 | Settings screens see-through in game (narrow soft panel, no dirt list background) and do not pause | User: see changes live in the world while tweaking |
| 2026-10-22 | Look presets (Airy/Clear/Warm/Vivid) tuned against matched-biome scenes; sky tint axis golden..lavender, weighted toward golden hour, clouds follow | User: compare same biome / same scene, aim for the same feel |
| 2026-10-22 | LabPBR read from resource packs (_n, _s): pack normals and AO, GGX sun + sky reflections, metals, emission, parallax (Medium/High) | User: SPBR / Vanilla Mashup look (sharp, glossy, glowing ores, 3D blocks) |
| 2026-10-22 | PBR atlases built at the pack's map detail (up to 4x the block atlas, 200 MB budget) | Packs ship 64x normal/height maps over 16x colours; resampling to 16x erased the detail (user: "PBR isn't visible") |
| 2026-10-22 | Pack normals at full strength regardless of the generated-normal slider | Low preset scaled them to 45% |
| 2026-10-22 | Voxel parallax: 2D DDA over the height texels, wall normals, self-shadow toward the light; on all presets (Low 16 blocks, Medium 24, High 32) | Seraphic's trailer look: every pixel a tiny lit cube; smooth POM smeared the steps |
| 2026-10-22 | Parallax tile wrap via floor division (GLSL % is undefined for negatives) | Relief missing on faces depending on orientation/position (user) |
| 2026-10-22 | PBR reflections: roughness-aware Fresnel (exponent 4), sky + block-light reflections, x2, scaled by the Gloss (sheen) slider | Gloss invisible in 0.5.x (user) |
| 2026-10-22 | Generated relief (height from texel brightness, 16 % range) for opaque textures without pack height maps; option "3D for all textures", on | User: wood and many textures had no effect (Seraphic for 1.20.1 covers ~150 textures; modded blocks none) |
| 2026-10-23 | Direct Photon and Complementary source inspection for 0.5.9; original implementation of general lighting/scattering techniques | User explicitly requested reading both source trees, overriding the original spec's restriction on inspecting Complementary; no reference code or assets incorporated |
| 2026-10-23 | Separate Iris reference client with copied world, options and saved cameras | The environment can run and capture Minecraft on Mesa llvmpipe, superseding the original text-only limitation; software results do not establish Intel UHD performance |
| 2026-10-23 | Shadow fade completes at light Y=0.08, bounded low-sun direction Y=0.12, stronger direct/fill contrast | Matched reference scenes retain long, defined ground shadows at golden hour |
| 2026-10-23 | Small plants cast within 8 blocks at 1024 shadow resolution, 16 blocks above that | User requested reference-level shadow depth; a bounded near field restores local cast shadows while skipping far plants, replacing the original never-cast rule |
| 2026-10-23 | Ten volumetric samples weighted by represented segment and attenuation; fog and shafts share one display-linear composition | Equal weights biased quadratic sampling; independent gamma-space glow flattened shadows. Existing pass/sample counts remain, arithmetic and caster cost require UHD measurement |
| 2026-10-23 | Add Rethinking Voxels official r0.1-beta9 and clearly labelled Derivative Main community fork; retain DERCODE separately | User explicitly requested both shader references, local source copies and screenshots; original Derivative repository was not verified |
| 2026-10-23 | Quadratic 24/12 CPU sky integration, exact segment attenuation and height-baked diffuse second scattering | Numerical sky error and missing diffuse horizon light; preserve the existing GPU lookup and cloud style |
| 2026-10-23 | Directional moon sky and lower airglow; smaller ambient compensation and horizon replacement | A flat blue night gradient and strong painted horizon obscured atmospheric structure |
| 2026-10-23 | Nearby shadow filter radius halved; High volumetric samples increase from 10 to 16, Medium to 12 | User reports stable High performance on UHD and explicitly prioritizes shadow/light definition; Low retains 10 taps, candidate cost remains to be measured on target hardware |


## 0.6.0: bounded local voxel light and selectable crisp edges

The user explicitly requested coloured voxel block lighting and pixel-art shadows/rays. Local light
therefore uses a sliced snapshot, a background collision-box traversal and a bounded, double-buffered
surface atlas. It is local direct terrain light, not full GI. Natural shadows remain the default;
Voxel Crisp is optional because nearest coverage and extra visibility taps trade smooth motion and
ray cost for a deliberate pixel finish. No extra full-screen composite is added.

A software OpenGL client and disposable Terralith world are now available, superseding the original
specification's statement that the agent cannot run Minecraft or see its screen. Actual captures,
source reviews and geometry/finite-output checks establish local correctness only. The user's explicit
request to implement/build is the authority to deliver this candidate; Intel UHD frame-time acceptance
remains outstanding. Software-renderer FPS cannot establish the target GPU's budget.

See `research/LOCAL_LIGHT_CLOUD_REVIEW_060.md` for implementation limits, reference provenance and
the forest scattering and water-depth findings.

| 2026-10-25 | One water look, Complementary-style: vanilla texture lines drive normals and faint streaks, dark glassy mirror, terrain reflections on all presets; Photon glitter and caustics removed | User: "no two modes, just one mode"; done with overly realistic water (docs/research/WATER_ONE.md) |

## 0.5.3-water.2: shore line (2026-10-25)

- The user asked for the thin, light line that the Nostalgia reference shows where water meets land.
- The soft shore rim is replaced by that line.
  - It is a lit, near-white line wherever the vertical water depth to the block behind the surface drops to zero.
  - A faint halo sits around it.
  - Its width grows with distance, so it stays a few pixels wide.
  - Pixel-sized cells shimmer slowly, so the line reads as lapping water.
- It shows only on top faces, and not over open drops.

## 0.7.0: merged base (Claude water + Codex lighting), 2026-10-25

- The user asked for both branches to be merged into one base branch, `main`.
- Water uses the single Complementary-direction look (user decision). Codex's calm-lake style and its blend slider are removed from the settings. Their config fields stay so old configs still load, but nothing reads them.
- Water keeps Codex's shared lighting hooks:
  - local light comes from `aurum_localBlockLight`, the voxel field or its propagated fallback;
  - sun visibility includes cloud shadows;
  - reflected clouds come from the previous frame's cloud buffer.
- Config version is now 11. Steps 8–10 are Codex's; step 11 turns terrain reflections on for every preset.

## 0.7.1: post-processing pass (2026-10-25)

- See docs/research/POST_PROCESSING.md.
- New default grade:
  - exposure 110, saturation 88, vibrance 100, contrast 110;
  - temperature +12, sky saturation 70.
- Codex's sky clarity and sky temperature default to 0. They stay available as sliders. The other looks keep
  their earlier values (75 / −10).
- New "Distance haze" setting (aerial perspective), default 100. It lives in the composite, separate from the
  locked fog.
- Bloom threshold changed from 0.85 / 0.10 to 0.77 / 0.14.
- Config version is now 12. Only an untouched Aurum look is moved to the new values.

## 0.7.2: lighting step 1, leaves (2026-10-25)

- See docs/research/LIGHTING_AUDIT.md, finding 1.
- Leaf cubes:
  - less upward lift (0.30–0.12, was 0.45–0.20);
  - less wrap (`dot * 0.90 + 0.10`, was 0.82 + 0.18), so a crown has a sunlit side and a dark far side;
  - the grey backlight floor is lowered to 0.03–0.09.
- New coloured transmission for leaves and plants:
  - sunlight scattered through the leaf comes out as albedo × hue², a saturated yellow-green;
  - strength is 0.30 + 0.70 × backlight² for leaves and 0.35 for plants;
  - it follows shadow and cloud shadow, plus a faint 0.03 glow inside the canopy;
  - it costs one shadow lookup on back-facing leaves, with no new pass or texture.
- Measured on matched renders (lit-leaf saturation, lit p90 in linear light):

  | Scene | Before | After |
  | --- | --- | --- |
  | Under trees | 0.41, 0.128 | 0.48, 0.158 |
  | Noon canopy | 0.33, 0.176 | 0.41, 0.247 |

  The references sit at 0.35–0.52 and 0.17–0.25. Distant canopy stays a little less saturated because of the
  0.7.1 haze and grade.

## 0.7.3: temporal anti-aliasing (2026-10-25)

- See docs/research/TAA.md.
- TAA is on by default on every preset, because it is the clearest "premium" difference from the references.
- It can be turned off ("Anti-aliasing (TAA)"), and its sharpening is adjustable ("TAA sharpening", default 50).

## 0.7.4: user test fixes (2026-10-25)

- **PBR bleaching** (user screenshots with Seraphic: grass and stone washed white, pale patches in shallow
  water). The environment reflection reflected the full displayed sky, ×2, off any glossy texel.
  - The grazing Fresnel ceiling now follows smoothness².
  - The reflected light runs from the sky's average fill (rough) to the displayed sky (mirror), weighted by
    smoothness⁴.
  - The ×2 is gone.
  - Verified on a Seraphic reproduction: grass keeps its true colour.
- **Cloud shadow default 65 → 40.** Under soft clouds, 65 % left most of the land dark and dull (user images 2
  and 3). Config v13 moves only an untouched 65.

## 0.7.5: second round of user test fixes (2026-10-25)

- **Snow edges** (blurry dark lines since 0.0.1): vanilla's per-vertex occlusion is linear across a full face,
  and the AO boost deepened it. The occlusion amount is now squared (`occ² / 0.4`, never more than `occ`), so
  corners keep full strength and the darkening falls off within a fraction of the face.
- **Water "too strong":**
  - texture-normal strength 0.45 → 0.25;
  - line overlay 0.35 → 0.18;
  - skin veil 0.10 → 0.07.
- **Shore line:** its width is now about 1.5 px from `fwidth` of the vertical depth (was 0.07 + 0.003·distance).
  The old width became a wide pale band along far shores at low angles.

## 0.7.6: water strength between 0.7.4 and 0.7.5 (2026-10-26)

- The user found 0.7.4 "pretty strong" and 0.7.5 "the effect is just gone".
- New values: texture-normal strength 0.36, line overlay 0.28, skin veil 0.09.
- The thin shore line from 0.7.5 stays.
