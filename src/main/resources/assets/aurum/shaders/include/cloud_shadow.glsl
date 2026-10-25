#import <aurum:include/cloud_motion.glsl>
uniform sampler2D u_CloudSdf;
uniform vec4 u_CloudShadow; // cloud centre height relative to camera, strength, drift, wind variation
uniform vec4 u_CloudLayout; // camera+wind offset in cells, shape shrink, resource-pack layout

float aurum_cloudShadow(vec3 pos) {
    float strength = u_CloudShadow.y * smoothstep(0.05, 0.22, u_SunDirW.y);
    if (strength < 0.001 || pos.y > u_CloudShadow.x) return 1.0;
    vec2 projected = pos.xz + u_SunDirW.xz * ((u_CloudShadow.x - pos.y) / max(u_SunDirW.y, 0.05));
    vec2 cells = aurum_cloudCells(u_CloudLayout.xy + projected / 12.0, u_CloudShadow.z, u_CloudShadow.w);
    vec3 shape = texture(u_CloudSdf, cells / 256.0).rgb;
    float sdf = u_CloudLayout.w < 0.5 ? shape.r : shape.b;
    // A broad penumbra at ground level is distinct from crisp nearby block shadows.
    float coverage = 1.0 - smoothstep(-0.12, 0.42, sdf + u_CloudLayout.z);
    return 1.0 - strength * coverage;
}
