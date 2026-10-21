# Testing: Phase 4, round two (0.3.1)

New since 0.3.0:

- **Two cloud styles.** Switch in F7 > Flare, rays & clouds > Cloud style.
  - **Blocky:** dense rounded boxes in two layers at different heights (the Nostalgia look). The blurry,
    pixelated high sheet is gone, edges stay crisp up close, and the dark band under close clouds is gone.
  - **Soft:** fluffy, see-through cumulus with high wispy streaks (the Better Clouds look).
- **Full-resolution clouds** (option; on when you pick the High preset). Sharpest up close, at about 3-4x the
  cloud cost.
- **Cloud height** slider: +0 to +160 blocks above vanilla's height, for your floating islands.
- **Stronger god rays:**
  - New *volumetric light*: shadowed air stays dark and sunlit air glows, so beams appear through trees, past
    islands and into caves through openings, even with the sun off-screen.
  - The sun rays through cloud gaps reach further.

Note: you are on HIGH, but full-resolution clouds start **off** after updating, because the option is new.
Pick the High preset again in F7, or switch the option on, to try it.

## Checks

1. **Blocky, half resolution:** look at a cloud up close (fly to y 170 under one). Are the edges clean now?
   Then switch Full-resolution on and compare.
2. **Soft:** noon, golden hour, and flying through and above the clouds. Do they look grainy or noisy anywhere?
3. **God rays at golden hour, with the sun behind trees or your floating islands:** do you see beams now? Are
   they too strong, or still too shy?
4. **A cave with an opening to the sky, at daytime:** is there a beam of light?
5. **Noon in open land:** nothing should look hazier or more washed out than in 0.3.0.

## Numbers to send back (F8 overlay, avg)

**Clouds** and **Rays, flare, bloom** in each case:

- Blocky, half resolution
- Blocky, full resolution
- Soft, half resolution
- Soft, full resolution

Take them at noon looking up, plus once at golden hour facing the sun. Also send the FPS.
