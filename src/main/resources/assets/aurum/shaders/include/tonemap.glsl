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
    // Exactly linear through the mid-tones (texture colours stay true, like vanilla), then a soft exponential
    // shoulder from 0.6 so the sun can make surfaces genuinely bright without clipping.
    const float SHOULDER = 0.6;
    float mappedPeak = peak <= SHOULDER ? peak
            : SHOULDER + (1.0 - SHOULDER) * (1.0 - exp(-(peak - SHOULDER) / (1.0 - SHOULDER)));
    // Same factor on all three channels: hue and saturation are preserved.
    vec3 mapped = color * (mappedPeak / peak);
    // Only the brightest highlights (sun, glare) roll toward white, as film and eyes do.
    mapped = mix(mapped, vec3(mappedPeak), smoothstep(0.8, 1.0, mappedPeak) * 0.6);
    return pow(clamp(mapped, 0.0, 1.0), vec3(1.0 / 2.2));
}

vec3 aurum_tonemap(vec3 hdr) {
    hdr *= u_ToneParams.y * u_WhiteBalance;
    if (u_ToneParams.x < 0.5) return aurum_filmic(hdr);
    if (u_ToneParams.x < 1.5) return aurum_agx(hdr, 1.25);
    return pow(clamp(hdr, 0.0, 1.0), vec3(1.0 / 2.2));
}
