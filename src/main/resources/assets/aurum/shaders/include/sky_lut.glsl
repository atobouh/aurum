// Sky radiance from the baked lookup table (see SkyLut.java for the layout). No inverse trig: one sqrt per axis.

uniform sampler2D u_SkyLut;

vec3 aurum_skyRadiance(vec3 dir, vec3 sunDir) {
    // Below the horizon there is ground, not sky: continue the horizon colour (like distant haze) and darken
    // it gently, instead of the dark short-path values the table holds there.
    float below = max(-dir.y, 0.0);
    // Stay in the sky half of the table: filtering across v = 0.5 would blend in below-horizon rows.
    float v = max(0.5 + 0.5 * sqrt(max(dir.y, 0.0)), 0.5 + 0.5 / 64.0);
    float cosA = 1.0;
    float lh = dot(dir.xz, dir.xz);
    float ls = dot(sunDir.xz, sunDir.xz);
    if (lh > 1e-8 && ls > 1e-8) cosA = dot(dir.xz, sunDir.xz) * inversesqrt(lh * ls);
    float u = sqrt(clamp(0.5 - 0.5 * cosA, 0.0, 1.0));
    vec3 c = texture(u_SkyLut, vec2(u, v)).rgb;
    // Below the horizon: the horizon haze, darker and less saturated with depth, so terrain fading into it
    // at the render-distance edge meets the same colour.
    float g = smoothstep(0.0, 0.4, below);
    vec3 ground = mix(c, vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), 0.4) * 0.6;
    return mix(c, ground, g);
}
