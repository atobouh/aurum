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
