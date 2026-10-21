// Waving plants and leaves. Design notes: docs/research/FOLIAGE.md.
// All spatial terms repeat every 256 blocks so the camera-modulo trick never causes a visible jump.

const float WIND_MAX_DIST = 48.0;

const int MAT_PLANT = 1;
const int MAT_TALL_LOWER = 2;
const int MAT_TALL_UPPER = 3;
const int MAT_LEAVES = 4;
const int MAT_VINE = 5;
const int MAT_WATER_PLANT = 6;
const int MAT_CROP = 7;
const int MAT_WATER_COLUMN = 8;

// Smoothed triangle wave in [-1, 1] (GPU Gems 3 ch. 16): cheaper than sin, same feel.
vec4 aurum_smoothTriangle(vec4 x) {
    vec4 t = abs(fract(x + 0.5) * 2.0 - 1.0);
    return (t * t * (3.0 - 2.0 * t)) * 2.0 - 1.0;
}

vec3 aurum_windOffset(int id, vec3 position, bool topVertex, float dist) {
    vec3 wp = position + u_CameraMod;
    vec2 dir = u_WindParams.xy;

    // Travelling gusts: bright patches of motion that roll across the land along the wind.
    float gust = textureLod(u_NoiseTex, (wp.xz - u_GustOffset) * (1.0 / 256.0), 0.0).r;
    gust = smoothstep(0.3, 0.85, gust);

    // Per-position phase: neighbours are related but never identical. Coefficients are n/256 so the
    // pattern repeats exactly every 256 blocks.
    float ph = dot(wp, vec3(37.0, 23.0, 61.0) * (1.0 / 256.0));
    vec4 w = aurum_smoothTriangle(u_WindPhase + vec4(ph, ph * 0.75, ph * 2.0, ph * 1.5));

    float strength = u_WindParams.z * (1.0 - smoothstep(WIND_MAX_DIST * 0.65, WIND_MAX_DIST, dist));

    // Main bend: lean with the gust, oscillate around the lean.
    float sway = w.x * 0.65 + w.y * 0.35;
    float bend = (0.12 + gust * 0.55) + sway * (0.30 + 0.45 * gust);
    // Detail flutter: small, fast, perpendicular-ish.
    float flutter = w.z * 0.6 + w.w * 0.4;

    float weight;
    float mainAmp;
    float detailAmp;
    if (id == MAT_LEAVES) {
        weight = 1.0; mainAmp = 0.05; detailAmp = 0.045;
    } else if (id == MAT_VINE) {
        weight = 1.0; mainAmp = 0.03; detailAmp = 0.04;
    } else if (id == MAT_WATER_COLUMN) {
        weight = 1.0; mainAmp = 0.10; detailAmp = 0.02;
    } else {
        if (id == MAT_TALL_LOWER) weight = topVertex ? 0.45 : 0.0;
        else if (id == MAT_TALL_UPPER) weight = topVertex ? 1.0 : 0.45;
        else weight = topVertex ? 1.0 : 0.0;
        mainAmp = id == MAT_CROP ? 0.13 : (id == MAT_WATER_PLANT ? 0.22 : 0.20);
        detailAmp = 0.025;
    }

    vec2 lean = dir * bend * mainAmp * weight;
    vec3 offset = vec3(lean.x, 0.0, lean.y);
    offset += vec3(-dir.y, 0.5, dir.x) * flutter * detailAmp * weight * (0.4 + 0.6 * gust);
    // Length preservation: the tip drops as it bends so plants arc instead of stretching.
    offset.y -= dot(lean, lean) * 1.5;
    return offset * strength;
}
