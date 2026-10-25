# Research: temporal anti-aliasing (0.7.3)

The user said, after 0.7.2: "nothing is visibly better". Native-scale crops of the references against ours showed
that the main difference is not colour.

- Our frames are pixel-sharp: leaves show every texel speckle and edges are stair-stepped.
- Nostalgia and Photon resolve leaves into soft, solid blocks with smooth edges. Both use temporal
  anti-aliasing. Two of the Photon shots also use resource packs (Xekr Squareful, RainbowPixel), which explains
  part of their plush leaves.

## Technique (standard, written for Aurum)

1. **Jitter.** `GameRenderer.getBasicProjectionMatrix` gets a sub-pixel clip-space offset each frame from Halton
   (2,3), 8 samples. The hand is never jittered.
2. **Resolve.** It is one full-screen pass after the composite (`post/taa.fsh`). Per pixel:
   - Reproject with depth, using the closest depth in a 3×3 window so edges follow the foreground. The camera
     delta is in blocks, and the previous projection is the unjittered one. Sky reprojects by rotation only.
   - Fetch history with a 5-tap Catmull-Rom, so repeated resampling does not blur it.
   - Clip the history in YCoCg to mean ± 0.9σ, bounded by the 3×3 min/max.
   - Blend 15 % new frame when still, up to 35 % when moving fast, luminance-weighted to avoid flicker.
   - Water (in front of the opaque copy) keeps at least 50 % of the new frame, so its animated pixel lines stay
     sharp.
   - Sharpen against the 3×3 average, clamped to the neighbourhood ("TAA sharpening", default 50).
3. **History.** Two RGBA16F full-resolution textures, ping-ponged. History resets on resize, a world change, a
   teleport (more than 8 blocks), or TAA being switched off.

## Tuning

| Render | Settings | Result |
| --- | --- | --- |
| lf4 | 10 % new frame, 1.25σ, sharpen 0.175 | leaves smeared to flat green; water lines lost |
| lf5 | 15 % new frame, 0.9σ, sharpen 0.5, water rule | leaf texture back, smooth edges, water lines kept |

## Known limits

- No motion vectors for moving entities or waving plants. The tight clip limits ghosting, but some softness
  remains while they move.
- Cost: about 28 texture reads per pixel in one pass. On an Intel UHD 620 at 1080p this is estimated at
  1–1.5 ms; it needs the user's F8 numbers. "Anti-aliasing (TAA)" in F7 turns it off.
