# Decisions

Where Aurum differs from `AURUM_SPEC.md`, or where the spec left a choice open. One line of reason each.

| Date | Decision | Reason |
| --- | --- | --- |
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
