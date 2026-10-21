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
