package dev.impeccable.aurum.render;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.bake.VoxelLightBaker;
import dev.impeccable.aurum.config.AurumConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Bounded nearby voxel lighting: time-sliced client-world snapshot, background visibility bake, then a
 * double-buffered half-float atlas uploaded 64 rows at a time. No world access occurs on the worker.
 * The incident volume is 16 blocks across; propagated block light is the common fallback outside it.
 */
public final class VoxelLighting {
    public static final int SIDE = 16;
    private static final int SNAP = 56, MARGIN = (SNAP - SIDE) / 2;
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aurum voxel light baker"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t;
    });
    private static ClientWorld currentWorld;
    private static Snapshot collecting;
    private static CompletableFuture<Job> pending;
    private static Job uploading;
    private static FloatBuffer upload;
    private static int uploadRow;
    private static volatile long generation;
    private static long revision;
    private static int lastWarmth = -1;
    private static long bakedRevision = -1;
    private static final int[] textures = new int[2];
    private static int front, width, height, detail;
    private static int originX, originY, originZ;
    private static int desiredX, desiredY, desiredZ;
    private static boolean ready;
    private static boolean hasSources;
    private static int publishedCandidates, publishedSources, publishedFaces;
    private static long publishedBakeNanos;
    private static int lastUnloadedCells;
    private static final float[] lightPositions = new float[dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS*4], lightColors = new float[dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS*3];
    public static float[] lightPositions() { return lightPositions; }
    public static float[] lightColors() { return lightColors; }
    public static int lightCount() { return publishedSources; }

    private record Job(VoxelLightBaker.Result result, int x, int y, int z, long revision, long generation, int candidates, int sources, List<VoxelLightBaker.Light> lightData) {}
    private VoxelLighting() {}

    public static int texture() { return ready && hasSources ? textures[front] : 0; }
    public static boolean usable() { return ready && publishedCandidates <= dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS && !dynamicCompatibility() && AurumConfig.get().voxelLightingEnabled; }
    public static boolean dynamicCompatibility() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lambdynlights");
    }
    public static int probeSide() { return SIDE * (detail == 8 ? 2 : 1); }
    public static int detail() { return detail; }
    public static int width() { return width; }
    public static int originX() { return originX; }
    public static int originY() { return originY; }
    public static int originZ() { return originZ; }

    /** Diagnostic metadata only; called on demand on the client thread. */
    public static java.util.Map<String, Object> debugState() {
        var state = new java.util.LinkedHashMap<String, Object>();
        state.put("ready", ready); state.put("usable", usable()); state.put("hasSources", hasSources);
        state.put("revision", revision); state.put("publishedRevision", bakedRevision);
        state.put("generation", generation); state.put("collecting", collecting != null);
        state.put("baking", pending != null); state.put("uploading", uploading != null);
        state.put("lastSnapshotUnloadedCells", lastUnloadedCells);
        state.put("snapshotSide", SNAP);
        state.put("snapshotCellsCaptured", collecting == null ? 0 : collecting.cursor);
        state.put("uploadRow", uploading == null ? 0 : uploadRow);
        state.put("origin", new int[]{originX, originY, originZ});
        state.put("desiredOrigin", new int[]{desiredX, desiredY, desiredZ});
        state.put("fieldSide", SIDE); state.put("detail", detail);
        state.put("publishedCandidates", publishedCandidates); state.put("publishedSources", publishedSources);
        state.put("sourceCap", dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS); state.put("publishedProbes", publishedFaces);
        state.put("publishedBakeMillis", publishedBakeNanos / 1_000_000.0);
        state.put("dynamicCompatibilityFallback", dynamicCompatibility());
        state.put("sourceOverflowFallback", publishedCandidates > dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS);
        state.put("backend", "per-source visibility probes, exact receiver falloff/direction, bounded neutral single-bounce estimate");
        state.put("sourcePositionLevelsRelativeToOrigin", lightPositions.clone());
        state.put("sourceColors", lightColors.clone());
        state.put("sourceScope", "Static emitters; coherent propagated fallback when LambDynamicLights is installed or source budget overflows");
        return state;
    }

    private static void recordPublished(Job job) {
        publishedCandidates = job.candidates; publishedSources = job.sources;
        publishedFaces = job.result.faces(); publishedBakeNanos = job.result.nanos();
        java.util.Arrays.fill(lightPositions, 0); java.util.Arrays.fill(lightColors, 0);
        for (int i=0; i<job.lightData.size(); i++) {
            var light=job.lightData.get(i);
            lightPositions[i*4]=light.x()-MARGIN; lightPositions[i*4+1]=light.y()-MARGIN;
            lightPositions[i*4+2]=light.z()-MARGIN; lightPositions[i*4+3]=light.level();
            lightColors[i*3]=light.red(); lightColors[i*3+1]=light.green(); lightColors[i*3+2]=light.blue();
        }
    }

    public static void onSectionRebuilt(int x, int y, int z) {
        if (currentWorld == null) return;
        int wx = x * 16, wy = y * 16, wz = z * 16;
        if (wx + 16 >= desiredX - MARGIN && wx < desiredX + SIDE + MARGIN
                && wy + 16 >= desiredY - MARGIN && wy < desiredY + SIDE + MARGIN
                && wz + 16 >= desiredZ - MARGIN && wz < desiredZ + SIDE + MARGIN) { revision++; ready = false; }
    }

    public static void update(ClientWorld world, Vec3d camera, boolean enabled) {
        if (world != currentWorld || !enabled) {
            if (world != currentWorld || ready || collecting != null || uploading != null) {
                generation++; ready = false; collecting = null; uploading = null; bakedRevision = -1;
                if (upload != null) { MemoryUtil.memFree(upload); upload = null; }
            }
            currentWorld = enabled ? world : null;
            if (!enabled) return;
        }
        if (dynamicCompatibility()) { ready = false; return; }
        desiredX = (int) Math.floor((camera.x + 2) / 4) * 4 - SIDE / 2;
        desiredY = (int) Math.floor((camera.y + 2) / 4) * 4 - SIDE / 2;
        desiredZ = (int) Math.floor((camera.z + 2) / 4) * 4 - SIDE / 2;
        int requestedDetail = AurumConfig.get().voxelDetail;
        if (lastWarmth != AurumConfig.get().blockLightWarmth) { lastWarmth = AurumConfig.get().blockLightWarmth; revision++; }
        if (pending != null && pending.isDone()) {
            Job job = pending.getNow(null); pending = null;
            if (job != null && job.generation == generation && job.revision == revision && job.result != null) uploading = job;
        }
        if (collecting != null && collecting.revision != revision) collecting = null;
        if (uploading != null && uploading.revision != revision) {
            uploading = null;
            if (upload != null) { MemoryUtil.memFree(upload); upload = null; }
        }
        if (uploading != null) {
            if (uploading.result.rgba().length == 0) {
                originX = uploading.x; originY = uploading.y; originZ = uploading.z;
                recordPublished(uploading);
                detail = uploading.result.detail(); bakedRevision = uploading.revision;
                ready = true; hasSources = false; uploading = null;
                return;
            }
            uploadSlice();
            return;
        }
        if (pending != null) return;
        if (collecting == null && (!ready || originX != desiredX || originY != desiredY || originZ != desiredZ
                || detail != requestedDetail || bakedRevision != revision)) {
            collecting = new Snapshot(world, desiredX, desiredY, desiredZ, requestedDetail, revision, generation);
        }
        if (collecting == null) return;
        // Snapshot access is deliberately budgeted on the client thread; the worker only sees copied boxes.
        long deadline = System.nanoTime() + 1_000_000L;
        int count = 0;
        while (collecting.cursor < SNAP * SNAP * SNAP && count++ < 8192) {
            collecting.captureNext();
            if ((count & 31) == 0 && System.nanoTime() >= deadline) break;
        }
        if (collecting.cursor == SNAP * SNAP * SNAP) {
            Snapshot s = collecting; collecting = null;
            lastUnloadedCells = s.unloaded;
            if (s.unloaded > 0) { ready = false; return; }
            VoxelLightBaker.Input input = s.input();
            pending = CompletableFuture.supplyAsync(() -> new Job(
                    dev.impeccable.aurum.bake.IncidentLightBaker.bake(input, () -> generation != s.generation), s.x, s.y, s.z, s.revision, s.generation, s.candidates, input.lights().size(), input.lights()), WORKER)
                    .exceptionally(e -> { AurumMod.LOGGER.error("Voxel lighting bake failed; retaining vanilla fallback", e); return null; });
        }
    }

    private static void uploadSlice() {
        VoxelLightBaker.Result r = uploading.result;
        int back = 1 - front;
        int prev = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int timer = GpuTimers.begin(TimerSection.AURUM_VOXEL);
        GlUtil.resetUnpack();
        if (upload == null) {
            if (width != r.width() || height != r.height() || textures[back] == 0) {
                for (int i = 0; i < 2; i++) {
                    if (textures[i] == 0) textures[i] = GL11C.glGenTextures();
                    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[i]);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, 0x812F);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, 0x812F);
                    GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA16F, r.width(), r.height(),
                            0, GL11C.GL_RGBA, GL11C.GL_FLOAT, (FloatBuffer) null);
                }
                ready = false;
            }
            upload = MemoryUtil.memAllocFloat(r.width() * 64 * 4); uploadRow = 0;
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[back]);
        int rows = Math.min(64, r.height() - uploadRow);
        upload.clear();
        upload.put(r.rgba(), uploadRow * r.width() * 4, rows * r.width() * 4).flip();
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, uploadRow, r.width(), rows, GL11C.GL_RGBA, GL11C.GL_FLOAT, upload);
        uploadRow += rows;
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prev);
        GpuTimers.end(timer);
        if (uploadRow == r.height()) {
            MemoryUtil.memFree(upload); upload = null;
            front = back; width = r.width(); height = r.height(); detail = r.detail();
            originX = uploading.x; originY = uploading.y; originZ = uploading.z;
            recordPublished(uploading);
            bakedRevision = uploading.revision; ready = true; hasSources = true;
            AurumMod.LOGGER.debug("Voxel field ready: side={}, probes={}, detail={}, bake={} ms, origin=({}, {}, {})",
                    SIDE, r.faces(), detail, r.nanos() / 1_000_000L, originX, originY, originZ);
            uploading = null;
        }
    }

    private static final class Snapshot {
        final ClientWorld world;
        final int x, y, z, subdivisions;
        final long revision, generation;
        final int[] ids = new int[SNAP * SNAP * SNAP];
        final byte[] emission = new byte[ids.length];
        final List<float[]> shapes = new ArrayList<>();
        final PriorityQueue<VoxelLightBaker.Light> lights = new PriorityQueue<>(dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS + 1,
                Comparator.comparingDouble(Snapshot::lightScore).reversed());
        final IdentityHashMap<BlockState, float[]> colours = new IdentityHashMap<>();
        final float warmth = AurumConfig.get().blockLightWarmth / 100f;
        final IdentityHashMap<BlockState, Integer> shapeCache = new IdentityHashMap<>();
        final BlockPos.Mutable pos = new BlockPos.Mutable();
        int cursor, candidates, unloaded;
        Snapshot(ClientWorld world, int x, int y, int z, int subdivisions, long revision, long generation) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.subdivisions = subdivisions;
            this.revision = revision; this.generation = generation;
            shapes.add(new float[0]); shapes.add(new float[]{0, 0, 0, 1, 1, 1});
        }
        void captureNext() {
            int bx = cursor % SNAP, bz = (cursor / SNAP) % SNAP, by = cursor / (SNAP * SNAP);
            pos.set(x - MARGIN + bx, y - MARGIN + by, z - MARGIN + bz);
            if (!world.isChunkLoaded(pos)) { ids[cursor++] = -1; unloaded++; return; }
            BlockState state = world.getBlockState(pos);
            int level = state.getLuminance();
            Integer shape = shapeCache.get(state);
            String id = shape == null || level > 0 ? Registries.BLOCK.getId(state.getBlock()).getPath() : "";
            if (level > 0) emission[cursor] = (byte) level;
            if (level > 0 && dev.impeccable.aurum.bake.IncidentLightBaker.couldContribute(bx + .5f - MARGIN, by + .5f - MARGIN, bz + .5f - MARGIN, SIDE, level)) {
                candidates++;
                float[] colour = colours.get(state);
                if (colour == null) { colour = lightColour(id, warmth); colours.put(state, colour); }
                VoxelLightBaker.Light light = new VoxelLightBaker.Light(bx + .5f, by + .5f, bz + .5f,
                        level, colour[0], colour[1], colour[2]);
                if (lights.size() < dev.impeccable.aurum.bake.IncidentLightBaker.MAX_LIGHTS) lights.add(light);
                else if (lightScore(light) < lightScore(lights.peek())) { lights.poll(); lights.add(light); }
                emission[cursor] = (byte) level;
            }
            if (shape == null) {
                if (state.isAir() || state.isIn(BlockTags.LEAVES) || (id.contains("glass") && !id.contains("tinted_glass")) || state.isIn(BlockTags.ICE)) shape = 0;
                else if (state.isOpaqueFullCube(world, pos)) shape = 1;
                else {
                    List<Box> boxes = state.getCollisionShape(world, pos).getBoundingBoxes();
                    if (boxes.isEmpty()) shape = 0;
                    else {
                        float[] b = new float[boxes.size() * 6]; int i = 0;
                        for (Box box : boxes) {
                            b[i++] = (float) Math.max(0, box.minX); b[i++] = (float) Math.max(0, box.minY); b[i++] = (float) Math.max(0, box.minZ);
                            b[i++] = (float) Math.min(1, box.maxX); b[i++] = (float) Math.min(1, box.maxY); b[i++] = (float) Math.min(1, box.maxZ);
                        }
                        shape = shapes.size(); shapes.add(b);
                    }
                }
                shapeCache.put(state, shape);
            }
            ids[cursor++] = shape;
        }
        VoxelLightBaker.Input input() {
            // Maintain the bounded source set while collecting: no large sort on the render thread.
            return new VoxelLightBaker.Input(SNAP, SIDE, MARGIN, subdivisions, ids, emission,
                    List.copyOf(shapes), lights.stream().sorted(Comparator.comparingDouble(Snapshot::lightScore)).toList());
        }
        private static double lightScore(VoxelLightBaker.Light l) {
            double dx = l.x() - SNAP / 2.0, dy = l.y() - SNAP / 2.0, dz = l.z() - SNAP / 2.0;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) - l.level();
        }
    }

    private static float[] lightColour(String id, float warmth) {
        if (id.contains("redstone_lamp")) return new float[]{1f, .92f - .30f * warmth, .84f - .56f * warmth};
        if (id.contains("soul")) return new float[]{.24f, .72f, 1f};
        if (id.contains("redstone_torch")) return new float[]{1f, .13f, .035f};
        if (id.contains("lava") || id.equals("fire")) return new float[]{1f, .44f, .13f};
        if (id.contains("end_rod") || id.contains("sea_lantern")) return new float[]{.78f, .90f, 1f};
        if (id.contains("blue")) return new float[]{.22f, .40f, 1f};
        if (id.contains("purple") || id.contains("amethyst")) return new float[]{.68f, .30f, 1f};
        if (id.contains("green")) return new float[]{.32f, 1f, .30f};
        if (id.contains("red")) return new float[]{1f, .24f, .12f};
        return new float[]{1f, .92f - .30f * warmth, .84f - .56f * warmth};
    }
}
