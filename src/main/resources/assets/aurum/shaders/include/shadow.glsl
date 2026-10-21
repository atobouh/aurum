// Sun/moon shadow map: radial distortion (more texels near the player), normal-offset bias, hardware-filtered
// soft edges, and a fade at the edge of the shadow range. Technique after the shaderLABS shadow tutorial.

uniform sampler2DShadow u_ShadowMap;
uniform mat4 u_ShadowMatrix;   // camera-relative world position -> light clip space (front map)
uniform vec4 u_ShadowParams;   // x distortion, y strength (0 = no map), z texel world size at the centre, w filter radius (texels)
uniform vec4 u_ShadowParams2;  // x 1 / resolution, y tap count (4 or 8), z depth bias

// Dynamic layer: players and mobs near the camera, undistorted, redrawn every frame.
uniform sampler2DShadow u_EntityShadowMap;
uniform mat4 u_EntityShadowMatrix;
uniform vec4 u_EntityShadowParams; // x enabled, y 1 / resolution

float aurum_entityShadow(vec3 pos, vec3 normal) {
    if (u_EntityShadowParams.x <= 0.0) return 1.0;
    vec4 c = u_EntityShadowMatrix * vec4(pos + normal * 0.04, 1.0);
    if (abs(c.x) > 0.98 || abs(c.y) > 0.98) return 1.0;
    vec3 sc = c.xyz * 0.5 + 0.5;
    sc.z -= 0.0002;
    float r = u_EntityShadowParams.y * 1.5;
    float s = texture(u_EntityShadowMap, sc + vec3(-r, -r, 0.0))
            + texture(u_EntityShadowMap, sc + vec3( r, -r, 0.0))
            + texture(u_EntityShadowMap, sc + vec3(-r,  r, 0.0))
            + texture(u_EntityShadowMap, sc + vec3( r,  r, 0.0));
    float edge = max(abs(c.x), abs(c.y));
    return mix(s * 0.25, 1.0, smoothstep(0.85, 0.98, edge));
}

vec2 aurum_shadowDistort(vec2 p) {
    float d = length(p);
    return p / (d * u_ShadowParams.x + (1.0 - u_ShadowParams.x));
}

// Returns x = lit fraction (1 = fully lit), y = how much the map covers this point (0 outside the range).
vec2 aurum_shadow(vec3 pos, vec3 normal) {
    if (u_ShadowParams.y <= 0.0) return vec2(1.0, 0.0);
    vec4 c = u_ShadowMatrix * vec4(pos, 1.0);
    float r = length(c.xy);
    if (r > 0.97) return vec2(1.0, 0.0);

    // Push the lookup out along the surface normal by about one texel at this distance (texels grow
    // away from the centre with the distortion). Prevents acne without the floating look of a big depth bias.
    float df = r * u_ShadowParams.x + (1.0 - u_ShadowParams.x);
    float texelWorld = u_ShadowParams.z * df * df / (1.0 - u_ShadowParams.x);
    c = u_ShadowMatrix * vec4(pos + normal * texelWorld * 1.6, 1.0);
    vec3 sc = vec3(aurum_shadowDistort(c.xy), c.z) * 0.5 + 0.5;
    sc.z -= u_ShadowParams2.z;

    float rad = u_ShadowParams.w * u_ShadowParams2.x;
    float s;
    if (u_ShadowParams2.y < 6.0) {
        s  = texture(u_ShadowMap, sc + vec3(-0.5, -0.5, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3( 0.5, -0.5, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3(-0.5,  0.5, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3( 0.5,  0.5, 0.0) * rad);
        s *= 0.25;
    } else {
        // Fixed 8-tap disk (golden angle spiral): smoother penumbra on Medium and High.
        s  = texture(u_ShadowMap, sc + vec3( 0.250,  0.000, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3(-0.317,  0.290, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3( 0.055, -0.553, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3( 0.431,  0.502, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3(-0.743, -0.150, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3( 0.734, -0.393, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3(-0.270,  0.866, 0.0) * rad);
        s += texture(u_ShadowMap, sc + vec3(-0.420, -0.880, 0.0) * rad);
        s *= 0.125;
    }
    s = min(s, aurum_entityShadow(pos, normal));
    float coverage = (1.0 - smoothstep(0.78, 0.96, r)) * u_ShadowParams.y;
    return vec2(s, coverage);
}
