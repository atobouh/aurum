# Daylight correction after real-world footage — 0.6.1

The user supplied vanilla/Aurum captures of a modded settlement and confirmed Aurum 0.6.0,
High, LabPBR off. The Aurum frame has substantially darker foreground terrain and less readable
canopies alongside a bright sky and lake. Cameras and cloud states differ between the supplied
frames, so this is visual evidence, not a pixel-aligned benchmark.

## Reproduction scope

The supplied inventory reports 135 mods. It includes Nature’s Spirit 2.2.5, Fabric Seasons
2.4.2-BETA+1.20, TerraBlender 3.0.1.11, Fabric Seasons Terralith compatibility 1.0-2.3.7, and world
Terralith 2.5.4. These relevant vegetation/colour mods were installed in the disposable local
visual lab; the full user world/modpack, resource packs and configuration were not reproduced.
The original test save was backed up before adding the mods. No reference shader or test mod is
bundled in Aurum. Exact local JAR hashes are in visual-compatibility-061.json.

## Finding and change

Cloud attenuation removed up to 65% of direct sunlight without adding diffuse cloud fill.
A frozen-cloud forest test with that attenuation disabled raised ground display brightness
substantially. Separate foliage/AO and ambient-strength controls produced smaller changes.
Skylight was also squared before leaf-specific ambient attenuation and vertex AO, compounding
canopy darkening.

0.6.1 retains the 65% cloud-shadow control and direct-light/shadow-map paths. It adds a small,
skylight-occluded hemisphere fill proportional to intercepted sunlight (0.16), relaxes diffuse
skylight attenuation toward linear only in daylight (75% blend), and reduces the extra daytime
leaf penalty. Specular sky visibility and night-time ambient math remain as before. Zero skylight
still yields zero added sky/cloud fill. This is an artistic diffuse approximation, not traced
cloud scattering or global illumination. Biome and seasonal vertex colours remain authoritative.

The implementation changes only the existing terrain lighting include. There is no new pass,
texture fetch or allocation. That bounds the added work but does not establish Intel UHD FPS.

## Validation

See the standalone HTML report and capture manifest for native PNG evidence and limitations.
The existing close forest camera is deliberately retained, including its dark areas. The modded
canopy fixture contains red/orange maple, yellow aspen and a spruce control. Its baseline uses the
0.6.0 lighting include loaded in the same 0.6.1 test host; the after view restores the shipped
0.6.1 include. Resource reloads require time for light/mesh/shadow caches to settle.

This is a targeted correction, not evidence that Aurum now matches Photon, Complementary or
Rethinking Voxels, or a reproduction of every effect in the user's 135-mod installation.

Validation completed: JDK 21 Gradle build passed; expanded GLSL validated with glslangValidator;
the actual 0.6.1 client compiled 14 Aurum programs and installed the Sodium terrain replacement.
The sealed noon room retained its dark interior; Calm Lake retained visible bed and reflections.
Ten original PNG captures decoded in Chromium; zoom worked, no JS errors, no overflow at 390px.
Night mean absolute RGB difference was 0.065/255, with 99th percentile 1/255. Fixed forest ground
display luma moved 60.4 to 67.9/255, near-leaf luma 23.8 to 27.8, sampled sky 157.7 to 157.7.
These are local image checks, not measurements from the user's world or GPU.

Shipped JAR: 252755 bytes, SHA-256
`c90fcd0b6072452deddb64b0ea7d2c57a9ea80788e3621a1c3905e0d177105ee`.
ZIP integrity, version metadata and lighting-include identity against the source were checked.
