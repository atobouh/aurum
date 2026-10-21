// Sky radiance from the baked lookup table (see SkyLut.java for the layout).

uniform sampler2D u_SkyLut;

vec3 aurum_skyRadiance(vec3 dir, vec3 sunDir) {
    // Below the horizon there is ground, not sky: continue the horizon colour (like distant haze) and darken
    // it gently, instead of the dark short-path values the table holds there.
    float below = max(-dir.y, 0.0);
    float e = asin(clamp(max(dir.y, 0.0), 0.0, 1.0));
    float v = 0.5 + 0.5 * sign(e) * sqrt(abs(e) / (PI * 0.5));
    float a = 0.0;
    float lh = length(dir.xz);
    float ls = length(sunDir.xz);
    if (lh > 1e-4 && ls > 1e-4) {
        a = acos(clamp(dot(dir.xz / lh, sunDir.xz / ls), -1.0, 1.0));
    }
    // Stay in the sky half of the table: filtering across v = 0.5 would blend in below-horizon rows.
    v = max(v, 0.5 + 0.5 / 64.0);
    vec3 c = texture(u_SkyLut, vec2(a / PI, v)).rgb;
    // Below the horizon: the horizon haze, darker and less saturated with depth, so terrain fading into it
    // at the render-distance edge meets the same colour.
    float g = smoothstep(0.0, 0.4, below);
    vec3 ground = mix(c, vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), 0.4) * 0.6;
    return mix(c, ground, g);
}
