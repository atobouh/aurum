// Display transforms, written for Aurum. Linear HDR in, display (sRGB-ish) out.
//
// Default "Aurum Filmic": white balance to daylight, then one filmic S-curve applied to the brightest channel
// and the result scaled back onto all three. That keeps every colour's hue exactly (no greens drifting to
// yellow, no blue channel collapsing), with a gentle roll toward white only for the very brightest highlights.
// "AgX" (per-channel, published AgX matrices + our own curve) is kept as an option.
//
// u_ToneParams: x = mode (0 Aurum Filmic, 1 AgX, 2 off), y = exposure multiplier
// u_WhiteBalance: per-channel gains that make midday sun + sky neutral white

uniform vec4 u_ToneParams;
uniform vec3 u_WhiteBalance;

const mat3 AGX_INSET = mat3(0.842479062, 0.042328242, 0.042375655,
                            0.078433600, 0.878468636, 0.078433600,
                            0.079223745, 0.079166127, 0.879142974);
const mat3 AGX_OUTSET = mat3(1.196879005, -0.052896852, -0.052971636,
                             -0.098020881, 1.151903130, -0.098043450,
                             -0.099029744, -0.098961177, 1.151073673);
const float LOG_MIN_EV = -12.47393;
const float LOG_MAX_EV = 4.026069;
const float LOG_MID = 0.6060605; // log-encoded position of 0.18 grey

float aurum_sigmoid(float x, float slope) {
    // Smooth S curve through (LOG_MID, 0.5) with the given slope there, normalised to hit 0 and 1 at the ends.
    float u0 = -LOG_MID * slope * 2.0;
    float u1 = (1.0 - LOG_MID) * slope * 2.0;
    float u = (x - LOG_MID) * slope * 2.0;
    float s = u / sqrt(1.0 + u * u);
    float s0 = u0 / sqrt(1.0 + u0 * u0);
    float s1 = u1 / sqrt(1.0 + u1 * u1);
    return (s - s0) / (s1 - s0);
}

float aurum_logEncode(float x) {
    return (clamp(log2(max(x, 1e-10)), LOG_MIN_EV, LOG_MAX_EV) - LOG_MIN_EV) / (LOG_MAX_EV - LOG_MIN_EV);
}

vec3 aurum_agx(vec3 color, float slope) {
    color = AGX_INSET * max(color, vec3(0.0));
    color = vec3(aurum_sigmoid(aurum_logEncode(color.r), slope),
                 aurum_sigmoid(aurum_logEncode(color.g), slope),
                 aurum_sigmoid(aurum_logEncode(color.b), slope));
    color = AGX_OUTSET * pow(color, vec3(2.2));
    return pow(clamp(color, 0.0, 1.0), vec3(1.0 / 2.2));
}

vec3 aurum_filmic(vec3 color) {
    color = max(color, vec3(0.0));
    float peak = max(color.r, max(color.g, color.b));
    if (peak <= 1e-8) return vec3(0.0);
    // Exactly linear through the mid-tones (texture colours stay true, like vanilla), then a soft shoulder from
    // 0.6 so the sun can make surfaces genuinely bright without clipping.
    const float SHOULDER = 0.6;
    // Long rational shoulder: very bright values (sun glow 2..15) still differ, so glows fade smoothly instead
    // of flattening into a plateau with a hard rim.
    float y = (peak - SHOULDER) / (1.0 - SHOULDER) * 0.6;
    float mappedPeak = peak <= SHOULDER ? peak : SHOULDER + (1.0 - SHOULDER) * (y / (1.0 + y));
    // Same factor on all three channels: hue and saturation are preserved.
    vec3 mapped = color * (mappedPeak / peak);
    // Only the brightest highlights (sun, glare) roll toward white, as film and eyes do.
    mapped = mix(mapped, vec3(mappedPeak), smoothstep(0.97, 1.0, mappedPeak) * 0.5);
    return pow(clamp(mapped, 0.0, 1.0), vec3(1.0 / 2.2));
}

// Lottes tonemap with Photon's parameters (Photon Shaders by SixthSurge, include/post_processing/tonemap_operators.glsl;
// licence in assets/aurum/licenses/PHOTON_LICENSE.txt). Per channel with contrast 1.5: deeper shadows, bright
// mid-tones and naturally saturated colour - the vibrance of Photon's frames (0.8.8). Then Photon's gentle output
// S-curve (gain 1.05 in sqrt space).
vec3 aurum_lottes(vec3 rgb) {
    const vec3 a = vec3(1.5);
    const vec3 d = vec3(0.91);
    const vec3 hdrMax = vec3(8.0);
    const vec3 midIn = vec3(0.26);
    const vec3 midOut = vec3(0.32);
    vec3 b = (-pow(midIn, a) + pow(hdrMax, a) * midOut) / ((pow(hdrMax, a * d) - pow(midIn, a * d)) * midOut);
    vec3 c = (pow(hdrMax, a * d) * pow(midIn, a) - pow(hdrMax, a) * pow(midIn, a * d) * midOut)
           / ((pow(hdrMax, a * d) - pow(midIn, a * d)) * midOut);
    // Photon works in Rec. 2020: the per-channel curve then saturates real-world colours far less than it
    // would in Rec. 709 (vivid, but jungle greens do not turn neon). Convert in, map, convert back.
    rgb = max(rgb, vec3(0.0));
    rgb = vec3(dot(rgb, vec3(0.6274, 0.3293, 0.0433)), dot(rgb, vec3(0.0691, 0.9195, 0.0114)),
               dot(rgb, vec3(0.0164, 0.0880, 0.8956)));
    vec3 x = clamp(pow(rgb, a) / (pow(rgb, a * d) * b + c), 0.0, 1.0);
    vec3 s = sqrt(x);
    vec3 g = 0.5 * pow(2.0 * mix(s, 1.0 - s, step(0.5, s)), vec3(1.05));
    s = mix(g, 1.0 - g, step(0.5, s));
    x = s * s;
    x = vec3(dot(x, vec3(1.6605, -0.5876, -0.0728)), dot(x, vec3(-0.1246, 1.1329, -0.0083)),
             dot(x, vec3(-0.0182, -0.1006, 1.1187)));
    return pow(clamp(x, 0.0, 1.0), vec3(1.0 / 2.2));
}

vec3 aurum_tonemap(vec3 hdr) {
    hdr *= u_ToneParams.y * u_WhiteBalance;
    if (u_ToneParams.x < 0.5) return aurum_filmic(hdr);
    if (u_ToneParams.x < 1.5) return aurum_agx(hdr, 1.25);
    if (u_ToneParams.x > 2.5) return aurum_lottes(hdr);
    return pow(clamp(hdr, 0.0, 1.0), vec3(1.0 / 2.2));
}
