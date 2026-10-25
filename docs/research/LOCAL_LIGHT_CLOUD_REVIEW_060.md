# Aurum 0.6.0: local voxel light and structured atmosphere

The user explicitly requested voxel-based block lighting, moving cloud shadows, variable cloud speeds,
a Complementary/Rethinking Voxels lake option, a clearer noon horizon, sharpening and a dedicated
occluded god-ray test. They subsequently requested a pixel-art option for rays and shadows.

## Implementation boundaries

The original local-light implementation snapshots a 40³ region in client-thread slices of up to 2,048
blocks and an approximately 1 ms budget (checked every 32 cells). An emissive-block priority queue
retains at most 16 nearby sources. A low-priority worker traverses the copied voxel collision boxes;
it never queries the live world or OpenGL. A 16³ receiver field stores coloured visibility on six
block faces at 4 or 8 samples across a face. Surface geometry, rather than a screen-space mask,
occludes these light rays. Slabs and other partial collision boxes participate.

A double-buffered RGBA16F atlas uploads 64 rows per frame. Terrain with nearby block light reads it
once; outside the field, on unsupported surfaces, and while no field is ready, vanilla block light
remains. Default 4-sample detail uses about 6 MiB for the two GPU atlases; 8 uses about 24 MiB. The
background float result additionally uses about 6/24 MiB of heap, plus a bounded 1 MiB upload buffer.
Empty-source fields avoid atlas allocation and uploads. Movement by four-block bins, nearby mesh
rebuilds and detail/warmth edits invalidate the cache. Publication is asynchronous, so block edits
can take multiple frames to appear.

This is local terrain lighting, not Rethinking Voxels' complete GI renderer. It does not add entity
or held-item coloured light, light bounces, coloured-glass transport, arbitrary emissive texture
sources, or unlimited world coverage. Source colour uses recognisable block identifiers and a warm
fallback; registered mod blocks with luminance participate without a vanilla-only source list.

Cloud shadows project the existing signed-distance cloud field along the sun direction. The same
coverage, resource-pack shape selection, wrapped offset and seeded wind deformation drive the visible
clouds and their footprints. They attenuate direct sunlight, water highlights/caustics and volumetric
sunlight; ambient skylight and moonlight retain their established treatment. Coherent wind bands have
smoothly changing speeds instead of independent per-frame randomness. They are not a particle-cloud
simulation. The bounded cubic motion replaces additional trigonometric/noise texture work in the march.

Noon clarity and temperature affect displayed sky, reflected sky and atmospheric colour. The LUT's
surface irradiance, sunlight and moonlight colour calculations are retained. Canopy depth preserves
leaf-face direction and reduces fill inside dense crowns; biome tint data is not replaced. The extra
swamp-biome haze is independently adjustable, with a less opaque default.

The lake selector interpolates broad/fine slope, distance calming, absorption, caustics, shore foam,
Fresnel adjustment and glitter. Aurum remains selectable; a 0–100% transition is exposed. No extra
reflection march or full-screen pass is added. Sharpening uses four scene-neighbour luma samples in
the existing composite, bounds the correction and preserves chromaticity. Sky, covered cloud pixels,
hand, bloom and shaft contributions are not the sharpening source.

## Voxel Crisp option

Sun/moon shadow receivers snap tangentially to a world-anchored 4/8/16-cell-per-block grid. A single
depth comparison at the map texel centre replaces PCF and hardware bilinear penumbrae. Sixteen cells
align with the default texture texels; changing camera position does not move the grid through the
world. Entity shadow reception uses the same receiver grid. Cached local light uses nearest samples
at its own 4/8-per-face detail. Map resolution still limits what occluder geometry can be resolved.

Volumetric visibility samples snap to the same world grid and use shorter uniform intervals with ordered 2×2 sample offsets rather than
screen-noise jitter. The composite reads the march's native 1/3-resolution cells without spatial or
bilinear blur. Sky-ray masks likewise use nearest native cells. Light coverage has bounded discrete
levels. This gives a deliberate pixel-art finish; it is not a claim of unlimited resolution or
alias-free motion. Natural remains available. Crisp rays use additional adaptive visibility taps (up to 32/40/48 outdoors,
48/64/80 under cover at Low/Medium/High) to resolve the geometry before nearest reconstruction.
Hard terrain shadows and the crisp upsampler use fewer texture reads, but the ray march does more
work; this is a quality/cost tradeoff to measure on Intel UHD. Cloud shadows keep their broad weather penumbra.

## Canopy scattering correction

The Terralith birch-taiga check exposed a second air problem: the old enclosure multiplier increased
extinction along the entire view ray. Standing beneath a tree consequently veiled a far forest as
though all of it were inside a dusty room. Clear-air extinction now stays low; the enclosure enhancement
decays over the first 32 blocks. The enhancement has a bounded integrated optical depth (0.144 at full enclosure); it remains long enough to reveal a room aperture instead of only the air directly beside the eye. The march weights visibility by that spatially varying density and
transmittance. The room-aperture check also uses a restrained extra sunlight phase gain under cover. Extra shaft opacity carries sunlight; sky-coloured ambient fog is weighted only by the
actual distance/height-fog contribution. A source-free sealed room therefore does not acquire a blue
ambient glow just because shafts are enabled. This preserves surface colour without a global grade.

## Why a dedicated ray fixture matters

The first enclosed-room test exposed bright sky-coloured fog in above-ground rooms. Cover was only
applied to underground fog, so room haze obscured the shafts. Fog source radiance now follows enclosure
as well as cave cover, while sunlight scattering remains controlled by shadow-map visibility. Enclosed
openings use 16/20/24 taps at 1024/1536/2048+ shadow resolution; outdoors keeps 10/12/16. Natural-mode
reconstruction tightens its spatial kernel under cover. The sealed-window control checks for glowing
air without an opening. Test fixtures and capture scripts live in `tools/visual_lab` and
`tools/capture_visual_lab.py`; install them only in a disposable world.

## References and provenance

Reference source identities and restrictions remain those in
`ATMOSPHERE_REFERENCE_REVIEW_0510.md` and `shader-reference-sources.json`. Public Photon, Complementary,
Rethinking Voxels and the explicitly labelled Derivative Main community fork were studied for
atmospheric structure, restrained water, directional foliage light and local-light occlusion. Their
code and assets are not included in Aurum. Rethinking Voxels' official gallery provides a separate
local-light target; it is not an Aurum render or a shared-scene comparison.

Terralith 2.5.4 for Fabric/Minecraft 1.20.x is a test-client dependency only. It is not bundled in the
mod. The user's complete approximately 200-mod world has not been supplied. Target-device acceptance
must use their reported stable-60-FPS Intel UHD baseline at High and 12 chunks; software OpenGL
captures do not establish Intel UHD performance or full shader-pack parity.

## Open-ocean edge

Ocean captures exposed the whole-section render boundary before the old fade reached full coverage.
The far transparent-surface fade finishes at 66% of the nominal radius, with the clear foreground
retained. The existing opaque-depth copy from before translucency identifies those nearer transparent
surfaces. Opaque trees and blocks retain their original fade range, so solving the ocean boundary does
not bleach distant foliage. The additional depth lookup is gated to the far zone. This trades distant
water contrast for a clean section boundary; it is not an increase in geometry coverage. This reuses the existing depth and composite. An auxiliary water
depth attachment was investigated and removed because it did not address that boundary; no additional
surface buffer is shipped. The lake treatment also lowers absorption and retains broader readable
ripples instead of settling into a nearly flat mirror.

## Settings and handoff checks

At automatic GUI scale in a 1280×720 window, the main settings page now uses compact vertical spacing
so the category buttons and footer remain accessible. Cycling choices return a value label rather
than duplicating their own caption, keeping Calm Lake and Voxel Crisp readable. The category pages
retain their existing vanilla scroll list.

The build produces Java 17 bytecode. Runtime validation uses the development client and software
OpenGL; the separate remapped JAR is checked for ZIP integrity, version metadata and class target.
The standalone HTML embeds the original PNGs and an actual 20-second cloud-motion recording, with
a source/capture manifest. Desktop and mobile browser checks cover image decoding, comparison
sliders, zoom, video metadata and horizontal overflow.
