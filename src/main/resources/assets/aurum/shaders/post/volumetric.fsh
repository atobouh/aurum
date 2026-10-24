#version 150

// Volumetric light (1/3 resolution): how much of the air along each view ray is in sunlight, from the shadow
// map. Shadowed air (behind trees, under islands, inside caves) stays dark, sunlit air glows in the composite.
// Natural taps: 10/12/16 outdoors and 16/20/24 under cover. Voxel Crisp uses shorter, ordered intervals.

uniform vec4 u_PixelLight;
uniform vec3 u_RayWorldAnchor;
uniform vec3 u_SunDirW;
#import <aurum:include/cloud_shadow.glsl>

uniform sampler2D u_Depth;
uniform sampler2DShadow u_ShadowMap;
uniform mat4 u_ShadowMatrix;  // camera-relative world position -> light clip space
uniform mat4 u_InvProj;
uniform mat4 u_InvView;
uniform vec4 u_Params;        // x distortion, y range (blocks), z depth bias, w air extinction per block
uniform int u_SampleCount;
uniform float u_LocalDust; // local enclosure enhancement; exponentially decays over the first ten blocks

in vec2 v_Uv;
out vec4 fragColor;

float lit(vec3 pos) {
    if (u_PixelLight.x > 0.5) pos = (floor((pos + u_RayWorldAnchor) * u_PixelLight.y) + 0.5) / u_PixelLight.y - u_RayWorldAnchor;
    vec4 c = u_ShadowMatrix * vec4(pos, 1.0);
    float r = length(c.xy);
    if (r > 0.97) return aurum_cloudShadow(pos);
    vec2 d = c.xy / (r * u_Params.x + (1.0 - u_Params.x));
    vec3 sc = vec3(d, c.z) * 0.5 + 0.5 - vec3(0.0, 0.0, u_Params.z);
    if (u_PixelLight.x > 0.5) {
        vec2 size = vec2(textureSize(u_ShadowMap, 0));
        sc.xy = (floor(sc.xy * size) + 0.5) / size;
        return step(0.5, texture(u_ShadowMap, sc)) * aurum_cloudShadow(pos);
    }
    return texture(u_ShadowMap, sc) * aurum_cloudShadow(pos);
}

void main() {
    float depth = texture(u_Depth, v_Uv).r;
    vec4 vp = u_InvProj * vec4(v_Uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 view = vp.xyz / vp.w;
    float dist = depth < 1.0 ? length(view) : 1e9;
    vec3 dir = mat3(u_InvView) * normalize(view);
    float range = min(dist, u_Params.y);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    if (u_PixelLight.x > 0.5) {
        // Ordered 2x2 coverage samples distribute integration error without temporal noise or a blur kernel.
        ivec2 cell = ivec2(gl_FragCoord.xy) & ivec2(1);
        int bayer = cell.y == 0 ? (cell.x == 0 ? 0 : 2) : (cell.x == 0 ? 3 : 1);
        jitter = (float(bayer) + 0.5) * 0.25;
    }
    // Steps packed closer near the camera, where beams are largest on screen.
    float sum = 0.0, weightSum = 0.0;
    int steps = u_PixelLight.x > 0.5
            ? clamp(int(ceil(range * 2.5)), 16, clamp(u_SampleCount, 24, 80))
            : clamp(u_SampleCount, 10, 24);
    for (int i = 0; i < 80; i++) {
        if (i >= steps) break;
        float a = float(i) / float(steps);
        float b = float(i + 1) / float(steps);
        // Uniform, shorter intervals in Voxel Crisp mode keep distant aperture edges resolved too.
        float start = range * (u_PixelLight.x > 0.5 ? a : a * a);
        float end = range * (u_PixelLight.x > 0.5 ? b : b * b);
        float sampleDistance = mix(start, end, jitter);
        // Quadratic sampling concentrates taps near the camera, but each tap still represents its actual
        // segment of air. Front-to-back attenuation prevents distant light from overwhelming a nearby beam.
        float local = exp(-sampleDistance * 0.03125);
        float density = u_Params.w + u_LocalDust * local;
        float opticalDepth = sampleDistance * u_Params.w + u_LocalDust * 32.0 * (1.0 - local);
        float weight = (end - start) * density * exp(-opticalDepth);
        sum += lit(dir * sampleDistance) * weight;
        weightSum += weight;
    }
    // r: share of the ray (inside the shadow range) that is sunlit; g: that range as a fraction of the maximum.
    fragColor = vec4(sum / max(weightSum, 1e-5), range / u_Params.y, 0.0, 1.0);
}
