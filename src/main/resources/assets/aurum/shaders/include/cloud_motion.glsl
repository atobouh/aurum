// Smooth pseudo-random wind bands. Entire groups accelerate and slow coherently, with no frame-random
// jitter or extra noise lookup in the cloud march. The 256-cell and 128-block periods divide CPU wraps.
float aurum_windSeed(float band) {
    float p = fract(mod(band, 4.0) * 0.1031 + 0.173);
    p *= p + 33.33;
    return fract(p * (p + p));
}
vec2 aurum_cloudCells(vec2 cells, float drift, float variation) {
    if (variation < 0.001) return cells;
    float band = cells.y / 64.0;
    float f = fract(band);
    f = f * f * (3.0 - 2.0 * f);
    float seed = mix(aurum_windSeed(floor(band)), aurum_windSeed(floor(band) + 1.0), f);
    float phase = fract(drift / 128.0 + seed);
    float travel = 1.0 - abs(phase * 2.0 - 1.0);
    float wave = travel * travel * (3.0 - 2.0 * travel) * 2.0 - 1.0;
    // At maximum variation, local forward speed stays between 0.297x and 1.703x the base wind.
    cells.x += variation * 1.25 * wave;
    return cells;
}
