package dev.impeccable.aurum.bake;

import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns a motion material to block sprites (see include/wind.glsl). Known vanilla classes and tags first, then
 * a geometric rule so modded plants work without a list: a cutout block with no collision whose model is made of
 * diagonal quads is a plant.
 */
public final class MaterialClassifier {
    public static final int NONE = 0, PLANT = 1, TALL_LOWER = 2, TALL_UPPER = 3, LEAVES = 4, VINE = 5,
            WATER_PLANT = 6, CROP = 7, WATER_COLUMN = 8;

    private MaterialClassifier() {}

    public static Map<Identifier, Integer> classify() {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<Identifier, Integer> out = new HashMap<>();
        Random random = Random.create(42L);
        int[] counts = new int[9];

        java.util.List<String> moddedLeaves = new java.util.ArrayList<>();
        for (Block block : Registries.BLOCK) {
            List<BlockState> states = block.getStateManager().getStates();
            // Crops have one sprite per age; most other blocks only need a few states.
            int limit = Math.min(states.size(), 32);
            for (int s = 0; s < limit; s++) {
                BlockState state = states.get(s);
                int material = materialFor(block, state);
                if (material == NONE) continue;
                if (material == LEAVES && s == 0) {
                    Identifier id = Registries.BLOCK.getId(block);
                    if (!id.getNamespace().equals("minecraft")) moddedLeaves.add(id.toString());
                }
                BakedModel model;
                try {
                    model = client.getBlockRenderManager().getModel(state);
                } catch (RuntimeException e) {
                    continue;
                }
                if (material == -1) {
                    // Unknown block: decide from geometry.
                    material = looksLikePlant(model, state, random) ? PLANT : NONE;
                    if (material == NONE) continue;
                }
                for (BakedQuad quad : quads(model, state, random)) {
                    Identifier sprite = quad.getSprite().getContents().getId();
                    if (out.putIfAbsent(sprite, material) == null) counts[material]++;
                }
            }
        }
        if (!moddedLeaves.isEmpty()) {
            dev.impeccable.aurum.AurumMod.LOGGER.info("Modded blocks treated as leaves ({}): {}", moddedLeaves.size(),
                    String.join(", ", moddedLeaves.subList(0, Math.min(40, moddedLeaves.size()))));
        }
        dev.impeccable.aurum.AurumMod.LOGGER.info("Material map: {} plant, {} tall, {} leaves, {} vine, {} water, {} crop sprites",
                counts[PLANT], counts[TALL_LOWER] + counts[TALL_UPPER], counts[LEAVES], counts[VINE],
                counts[WATER_PLANT] + counts[WATER_COLUMN], counts[CROP]);
        return out;
    }

    /** NONE for blocks that never move, -1 for "check the geometry", otherwise the material. */
    private static int materialFor(Block block, BlockState state) {
        if (block instanceof LeavesBlock || state.isIn(BlockTags.LEAVES)) return LEAVES;
        if (looksLikeLeaves(block, state)) return LEAVES;
        if (block instanceof AbstractFireBlock || block instanceof TorchBlock || block instanceof SugarCaneBlock
                || block instanceof BambooBlock || block instanceof LilyPadBlock || block instanceof FlowerPotBlock) return NONE;
        if (block instanceof KelpBlock || block instanceof KelpPlantBlock) return WATER_COLUMN;
        if (block instanceof SeagrassBlock || block instanceof TallSeagrassBlock) return WATER_PLANT;
        if (block instanceof VineBlock || block instanceof AbstractPlantPartBlock || block instanceof GlowLichenBlock
                || block instanceof HangingRootsBlock) return VINE;
        if (block instanceof TallPlantBlock && state.contains(TallPlantBlock.HALF)) {
            return state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER ? TALL_LOWER : TALL_UPPER;
        }
        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SweetBerryBushBlock) return CROP;
        if (block instanceof PlantBlock) {
            return isCutout(state) ? PLANT : NONE;
        }
        if (isCutout(state) && hasNoCollision(state)) return -1;
        return NONE;
    }

    /**
     * Modded trees often use their own leaf blocks that are neither LeavesBlock nor tagged. Catch them by name
     * plus shape: a cutout block called leaves/leaf/foliage/needles that is a full cube.
     */
    private static boolean looksLikeLeaves(Block block, BlockState state) {
        String path = Registries.BLOCK.getId(block).getPath();
        boolean named = path.contains("leaves") || path.contains("leaf") || path.contains("foliage") || path.contains("needles");
        if (!named || path.contains("pile") || path.contains("carpet") || path.contains("litter")) return false;
        if (!isCutout(state)) return false;
        try {
            return state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isCutout(BlockState state) {
        RenderLayer layer = RenderLayers.getBlockLayer(state);
        return layer == RenderLayer.getCutout() || layer == RenderLayer.getCutoutMipped();
    }

    private static boolean hasNoCollision(BlockState state) {
        try {
            return state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when at least half the quads are diagonal (cross-shaped models like grass and flowers). */
    private static boolean looksLikePlant(BakedModel model, BlockState state, Random random) {
        List<BakedQuad> quads = quads(model, state, random);
        if (quads.isEmpty()) return false;
        int diagonal = 0;
        for (BakedQuad q : quads) {
            int[] v = q.getVertexData();
            int stride = v.length / 4;
            float ax = Float.intBitsToFloat(v[0]), ay = Float.intBitsToFloat(v[1]), az = Float.intBitsToFloat(v[2]);
            float bx = Float.intBitsToFloat(v[stride]), by = Float.intBitsToFloat(v[stride + 1]), bz = Float.intBitsToFloat(v[stride + 2]);
            float cx = Float.intBitsToFloat(v[stride * 2]), cy = Float.intBitsToFloat(v[stride * 2 + 1]), cz = Float.intBitsToFloat(v[stride * 2 + 2]);
            float ux = bx - ax, uy = by - ay, uz = bz - az, wx = cx - ax, wy = cy - ay, wz = cz - az;
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) continue;
            nx /= len;
            nz /= len;
            ny /= len;
            if (Math.abs(nx) > 0.3f && Math.abs(nz) > 0.3f && Math.abs(ny) < 0.5f) diagonal++;
        }
        return diagonal * 2 >= quads.size();
    }

    private static List<BakedQuad> quads(BakedModel model, BlockState state, Random random) {
        java.util.ArrayList<BakedQuad> list = new java.util.ArrayList<>();
        random.setSeed(42L);
        list.addAll(model.getQuads(state, null, random));
        for (Direction d : Direction.values()) {
            random.setSeed(42L);
            list.addAll(model.getQuads(state, d, random));
        }
        return list;
    }
}
