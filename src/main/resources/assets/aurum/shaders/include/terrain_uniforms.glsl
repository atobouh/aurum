// Aurum uniforms shared by the replacement Sodium terrain vertex and fragment shaders.
// Everything here is set once per terrain pass from TerrainUniforms (Java); values change per frame, not per draw.

uniform int u_AurumMode;          // 0 = vanilla path (other dimensions, underwater, blindness), 1 = Aurum lighting

uniform vec3 u_LightDir;          // direction towards the sun (day) or moon (night), world space
uniform vec3 u_LightColor;        // direct light colour, linear HDR
uniform vec3 u_SkyAmbient;        // light from the sky dome, linear HDR
uniform vec3 u_GroundAmbient;     // light bounced up from the ground, linear HDR
uniform vec3 u_BlockLightColor;   // torch colour at full block light, linear HDR
uniform vec4 u_TerrainParams;     // x normal strength, y AO boost, z minimum light, w bounce strength

uniform sampler2D u_NormalTex;    // baked normal atlas: rg normal, b coating noise, a material id
uniform sampler2D u_BounceTex;    // 1x1 average colour around the player (previous frame), linear

// Wind (cutout pass only)
uniform vec4 u_WindPhase;         // four oscillator phases in [0,1), accumulated on the CPU
uniform vec4 u_WindParams;        // xy wind direction (xz plane), z strength (0 = off), w gust scroll scale
uniform vec2 u_GustOffset;        // gust field offset in blocks, already wrapped to the noise period
uniform vec3 u_CameraMod;         // camera position modulo 256 blocks, keeps world phases precise and seamless
uniform sampler2D u_NoiseTex;     // 64x64 tiling value noise, one texel = 4 blocks

// LabPBR (resource-pack material data, see AtlasBaker)
uniform sampler2D u_PbrTex;       // r smoothness, g F0 / metal, b emission, a height
uniform vec4 u_PbrParams;         // x specular on, y parallax depth (blocks, 0 = off), z parallax distance, w emission strength
uniform vec3 u_SunDirW;           // direction toward the sun, world space
uniform float u_PbrScale;         // normal/PBR atlas texels per block-atlas texel (pack detail, 1-4)
