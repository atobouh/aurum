# Sodium notes

Pinned: **Sodium `mc1.20.1-0.5.13`** (Fabric), tag commit `1bcd392f`, published 2025-03-03. It is the newest
0.5.x build for 1.20.1. Read from source at that tag before writing any mixin.

## How terrain shaders are loaded

- `ShaderChunkRenderer.compileProgram(ChunkShaderOptions)` caches one `GlProgram` per options record and
  builds it with `createShader("blocks/block_layer_opaque", options)`. One shader pair serves all three passes
  (solid, cutout, translucent); the pass only changes `#define`s.
- `createShader` calls `ShaderLoader.loadShader(type, new Identifier("sodium", path + ".vsh"|".fsh"), constants)`.
- `ShaderLoader.getShaderSource(Identifier)` is a **static** method that reads
  `/assets/{ns}/shaders/{path}` with `Class.getResourceAsStream`: the **classpath**, not resource packs.
  `ShaderParser` then resolves `#import <ns:path>` lines through the same method and inserts
  `ShaderConstants` defines after line 1.
- Defines in play: `USE_FOG`, `USE_FOG_SMOOTH` (from `ChunkFogMode.SMOOTH`, always used),
  `USE_FRAGMENT_DISCARD` (cutout pass), `USE_VERTEX_COMPRESSION` (always).
- Shaders are `#version 330 core`. Anything Aurum injects into them must compile under 330 core.

**Injection plan (Phase 1).** Mixin on `ShaderLoader.getShaderSource` (`@Inject` at `RETURN`,
`cancellable`, `require = 1`) that rewrites the returned source for `sodium:blocks/block_layer_opaque.*`
only. Every other path is returned untouched. This catches the source after it is read but before
`#import` resolution, so Aurum can also add its own `#import <aurum:...>` lines; `ShaderLoader` resolves any
namespace from the classpath, so our files under `assets/aurum/shaders/` are found the same way.
Rewrites are anchored on exact lines; if an anchor is missing (a different Sodium build) the rewrite is
skipped, logged, and the pipeline stays vanilla for terrain.

## Uniforms and textures

- `ChunkShaderInterface(ShaderBindingContext, ChunkShaderOptions)` binds its uniforms in the constructor
  with `context.bindUniform(name, factory)`. Aurum can add fields to it with a mixin on the constructor
  (`@Inject` at `RETURN`) and bind extra uniforms the same way (sun direction, fog density, sky colours).
- `ChunkShaderInterface.setupState()` runs once per pass after the program is bound. It binds textures to
  units by `ChunkShaderTextureSlot.ordinal()` (0 = block atlas, 1 = lightmap). Aurum's extra textures
  (normal atlas, sky lookup, shadow map) go on units 2+ from a `setupState` `RETURN` inject.
- `GlProgram.bindUniform` throws if the uniform is missing, so injected uniforms must actually be used by
  the injected GLSL, or bound with a tolerant lookup.

## Render pass structure

- `DefaultChunkRenderer.render(matrices, commandList, renderLists, pass, camera)` is the draw loop:
  `begin(pass)` (compile/bind program, `setupState`), set projection and model-view, then one multi-draw
  per region with `u_RegionOffset` set per region. `end(pass)` unbinds.
- Passes: `DefaultTerrainRenderPasses.SOLID`, `CUTOUT` (fragment discard), `TRANSLUCENT` (reverse order).
- `WorldRenderer.renderLayer` and `renderClouds` are `@Overwrite`n by Sodium; `renderSky` gets a cancellable
  `HEAD` inject (skips the sky under thick fog). Aurum's timers therefore wrap the **call sites** in
  `WorldRenderer.render`, never those methods.

## Vertex data available to the terrain shader

`a_Position` (20-bit packed xyz), `a_Color` (vertex colour with AO in alpha), `a_TexCoord`,
`a_LightAndData` (block/sky light, material bits, draw id). **There are no vertex normals and no block id.**
Consequences:

- Face normals for lighting come from screen-space derivatives in the fragment shader:
  `normalize(cross(dFdx(viewPos), dFdy(viewPos)))`. Exact for flat block faces, nearly free.
- Material detection (water, leaves, sand...) is not possible from vertex data. Options for later phases:
  derive it from the atlas UV via a small sprite-class lookup texture baked at resource reload (preferred,
  no vertex format change), or extend the vertex format (invasive; avoid).
- Sky light is in `a_LightAndData.y`, which gives the "exposed to sky" test for puddles and cave fog for free.

## Shader injection API

Sodium 0.5.13 has **no public shader-injection API**. Iris hooks it with its own mixins. The source rewrite
above is the narrowest option.

## Dev environment quirk

Loom's Linux runtime ships LWJGL 3.3.2 and Sodium's pre-launch check demands 3.3.1. The dev run config
passes `-Dsodium.checks.issue2561=false`. Real launchers use 3.3.1 and need nothing.
