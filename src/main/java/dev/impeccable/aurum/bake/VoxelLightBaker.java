package dev.impeccable.aurum.bake;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * World-independent local block-light bake. The client supplies an immutable snapshot of collision boxes
 * and emissive blocks. Rays walk those voxels on a worker; terrain later reads a small surface atlas.
 * There are no Minecraft or OpenGL calls here, including in the occlusion traversal.
 */
public final class VoxelLightBaker {
    public record Light(float x, float y, float z, int level, float red, float green, float blue) {}
    public record Input(int snapshotSide, int fieldSide, int margin, int detail, int[] shapeIds,
                        byte[] emission, List<float[]> shapes, List<Light> lights) {}
    public record Result(int width, int height, int detail, float[] rgba, int faces, long nanos) {}

    private static final int[] NX = {1, -1, 0, 0, 0, 0};
    private static final int[] NY = {0, 0, 1, -1, 0, 0};
    private static final int[] NZ = {0, 0, 0, 0, 1, -1};

    private VoxelLightBaker() {}

    public static Result bake(Input in, BooleanSupplier cancelled) {
        long start = System.nanoTime();
        int width = 1024, tilesPerRow = width / in.detail;
        int tiles = in.fieldSide * in.fieldSide * in.fieldSide * 6;
        int height = ((tiles + tilesPerRow - 1) / tilesPerRow) * in.detail;
        if (in.lights.isEmpty()) return new Result(width, height, in.detail, new float[0], 0, System.nanoTime() - start);
        float[] out = new float[width * height * 4];
        int faces = 0;
        for (int y = 0; y < in.fieldSide; y++) {
            for (int z = 0; z < in.fieldSide; z++) {
                for (int x = 0; x < in.fieldSide; x++) {
                    if (cancelled.getAsBoolean()) return null;
                    int bx = x + in.margin, by = y + in.margin, bz = z + in.margin;
                    int index = index(in.snapshotSide, bx, by, bz);
                    int shape = in.shapeIds[index];
                    // Air/cards keep vanilla lighting; emitters keep their own visible emission.
                    if (shape <= 0 || in.emission[index] > 0) continue;
                    float[] boxes = in.shapes.get(shape);
                    int block = (y * in.fieldSide + z) * in.fieldSide + x;
                    for (int face = 0; face < 6; face++) {
                        int neighbour = index(in.snapshotSide, bx + NX[face], by + NY[face], bz + NZ[face]);
                        if (shape == 1 && in.shapeIds[neighbour] == 1) continue;
                        faces++;
                        int tile = block * 6 + face;
                        int tx = (tile % tilesPerRow) * in.detail, ty = (tile / tilesPerRow) * in.detail;
                        for (int v = 0; v < in.detail; v++) {
                            for (int u = 0; u < in.detail; u++) {
                                float su = (u + 0.5f) / in.detail, sv = (v + 0.5f) / in.detail;
                                float plane = surface(boxes, face, su, sv);
                                if (Float.isNaN(plane)) continue;
                                float px = bx + (face < 2 ? plane : su);
                                float py = by + (face < 2 ? sv : face < 4 ? plane : sv);
                                float pz = bz + (face < 2 ? su : face < 4 ? sv : plane);
                                px += NX[face] * 0.015f;
                                py += NY[face] * 0.015f;
                                pz += NZ[face] * 0.015f;
                                float red = 0, green = 0, blue = 0;
                                for (Light light : in.lights) {
                                    float dx = light.x - px, dy = light.y - py, dz = light.z - pz;
                                    float d2 = dx * dx + dy * dy + dz * dz;
                                    if (d2 >= light.level * light.level || d2 < 1e-8f) continue;
                                    float distance = (float) Math.sqrt(d2);
                                    float cosine = (dx * NX[face] + dy * NY[face] + dz * NZ[face]) / distance;
                                    if (cosine <= 0 || !visible(in, px, py, pz, light)) continue;
                                    // Match Minecraft's broad light reach, with directional faces and real
                                    // visibility instead of unoccluded trilinear block-light interpolation.
                                    float level = Math.max((light.level - distance) / 15f, 0f);
                                    float intensity = level / (4f - 3f * level) * cosine;
                                    red += light.red * intensity;
                                    green += light.green * intensity;
                                    blue += light.blue * intensity;
                                }
                                float peak = Math.max(red, Math.max(green, blue));
                                float scale = peak > 1.5f ? 1.5f / peak : 1f;
                                int dst = ((ty + v) * width + tx + u) * 4;
                                out[dst] = red * scale;
                                out[dst + 1] = green * scale;
                                out[dst + 2] = blue * scale;
                                out[dst + 3] = 1f;
                            }
                        }
                    }
                }
            }
        }
        return new Result(width, height, in.detail, out, faces, System.nanoTime() - start);
    }

    /** Outermost solid surface at these face coordinates; supports partial collision boxes. */
    private static float surface(float[] boxes, int face, float u, float v) {
        float plane = (face & 1) == 0 ? -1f : 2f;
        boolean found = false;
        for (int i = 0; i < boxes.length; i += 6) {
            int axis = face / 2;
            int ua = axis == 0 ? 2 : 0;
            int va = axis == 1 ? 2 : 1;
            if (u < boxes[i + ua] || u > boxes[i + ua + 3]
                    || v < boxes[i + va] || v > boxes[i + va + 3]) continue;
            float value = boxes[i + axis + ((face & 1) == 0 ? 3 : 0)];
            plane = (face & 1) == 0 ? Math.max(plane, value) : Math.min(plane, value);
            found = true;
        }
        return found ? plane : Float.NaN;
    }

    public static int index(int side, int x, int y, int z) {
        return (y * side + z) * side + x;
    }

    /** Amanatides–Woo traversal plus ray/box intersections; the emitting cell does not shadow itself. */
    public static boolean visible(Input in, float px, float py, float pz, Light light) {
        float dx = light.x - px, dy = light.y - py, dz = light.z - pz;
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int ex = (int) Math.floor(light.x), ey = (int) Math.floor(light.y), ez = (int) Math.floor(light.z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tx = Math.abs(dx) < 1e-8f ? Float.POSITIVE_INFINITY : ((dx > 0 ? x + 1 : x) - px) / dx;
        float ty = Math.abs(dy) < 1e-8f ? Float.POSITIVE_INFINITY : ((dy > 0 ? y + 1 : y) - py) / dy;
        float tz = Math.abs(dz) < 1e-8f ? Float.POSITIVE_INFINITY : ((dz > 0 ? z + 1 : z) - pz) / dz;
        float ix = Math.abs(dx) < 1e-8f ? Float.POSITIVE_INFINITY : 1f / Math.abs(dx);
        float iy = Math.abs(dy) < 1e-8f ? Float.POSITIVE_INFINITY : 1f / Math.abs(dy);
        float iz = Math.abs(dz) < 1e-8f ? Float.POSITIVE_INFINITY : 1f / Math.abs(dz);
        for (int step = 0; step < in.snapshotSide * 3 + 3; step++) {
            if (x == ex && y == ey && z == ez) return true;
            if (x < 0 || y < 0 || z < 0 || x >= in.snapshotSide || y >= in.snapshotSide || z >= in.snapshotSide) return false;
            int shape = in.shapeIds[index(in.snapshotSide, x, y, z)];
            if (shape < 0) return false; // unloaded space must not invent a clear path
            if (shape > 0) {
                float[] boxes = in.shapes.get(shape);
                for (int i = 0; i < boxes.length; i += 6) {
                    if (intersects(px - x, py - y, pz - z, dx, dy, dz, boxes, i)) return false;
                }
            }
            if (tx <= ty && tx <= tz) { if (tx > 1) return true; x += sx; tx += ix; }
            else if (ty <= tz) { if (ty > 1) return true; y += sy; ty += iy; }
            else { if (tz > 1) return true; z += sz; tz += iz; }
        }
        return false;
    }

    private static boolean intersects(float px, float py, float pz, float dx, float dy, float dz, float[] b, int i) {
        float lo = 0.0001f, hi = 0.9999f;
        if (Math.abs(dx) < 1e-8f) { if (px < b[i] || px > b[i + 3]) return false; }
        else { float a = (b[i] - px) / dx, c = (b[i + 3] - px) / dx; lo = Math.max(lo, Math.min(a, c)); hi = Math.min(hi, Math.max(a, c)); }
        if (Math.abs(dy) < 1e-8f) { if (py < b[i + 1] || py > b[i + 4]) return false; }
        else { float a = (b[i + 1] - py) / dy, c = (b[i + 4] - py) / dy; lo = Math.max(lo, Math.min(a, c)); hi = Math.min(hi, Math.max(a, c)); }
        if (Math.abs(dz) < 1e-8f) { if (pz < b[i + 2] || pz > b[i + 5]) return false; }
        else { float a = (b[i + 2] - pz) / dz, c = (b[i + 5] - pz) / dz; lo = Math.max(lo, Math.min(a, c)); hi = Math.min(hi, Math.max(a, c)); }
        return hi >= lo;
    }
}
