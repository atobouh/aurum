package dev.impeccable.aurum.render;

import dev.impeccable.aurum.config.AurumConfig;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * Per-section GPU times from {@code GL_TIMESTAMP} queries (ARB_timer_query, core in GL 3.3). Timestamps rather
 * than {@code GL_TIME_ELAPSED} because elapsed queries cannot nest and our sections do (World contains Sky).
 *
 * <p>Results are read {@value #SLOTS} frames later and only if the driver says they are ready, so the CPU never
 * waits on the GPU (spec rule 8). Unready frames are dropped and counted. When the overlay is off this class
 * issues no GL calls at all.
 */
public final class GpuTimers {
    private static final int SLOTS = 4;
    private static final int MAX_PAIRS = 96;
    private static final long WINDOW_NS = 500_000_000L;

    private static boolean supported;
    private static boolean active;
    private static int slot;

    private static final int[][] queries = new int[SLOTS][];
    private static final int[][] pairSection = new int[SLOTS][MAX_PAIRS];
    private static final boolean[][] pairEnded = new boolean[SLOTS][MAX_PAIRS];
    private static final int[] pairCount = new int[SLOTS];
    private static final int[] lastQuery = new int[SLOTS];

    private static final int N = TimerSection.VALUES.length;
    private static final double[] frameMs = new double[N];
    private static final double[] windowSum = new double[N];
    private static final double[] windowMax = new double[N];
    private static int windowFrames;
    private static long windowStart;
    private static int windowDropped;

    private static final float[] avgMs = new float[N];
    private static final float[] maxMs = new float[N];
    private static int dropped;

    private GpuTimers() {}

    public static void init() {
        supported = GL.getCapabilities().glQueryCounter != 0L;
    }

    public static boolean isSupported() {
        return supported;
    }

    public static void beginFrame() {
        boolean want = supported && AurumConfig.get().showOverlay;
        if (!want) {
            if (active) reset();
            active = false;
            return;
        }
        active = true;
        slot = (slot + 1) % SLOTS;
        collect(slot);
        pairCount[slot] = 0;
    }

    /** Starts a section. Returns a handle for {@link #end}, or -1 when timing is off. */
    public static int begin(TimerSection section) {
        if (!active) return -1;
        int p = pairCount[slot];
        if (p >= MAX_PAIRS) return -1;
        if (queries[slot] == null) {
            queries[slot] = new int[MAX_PAIRS * 2];
            GL15C.glGenQueries(queries[slot]);
        }
        int q = queries[slot][p * 2];
        GL33C.glQueryCounter(q, GL33C.GL_TIMESTAMP);
        pairSection[slot][p] = section.ordinal();
        pairEnded[slot][p] = false;
        pairCount[slot] = p + 1;
        return slot * MAX_PAIRS + p;
    }

    public static void end(int handle) {
        if (handle < 0 || !active) return;
        int s = handle / MAX_PAIRS, p = handle % MAX_PAIRS;
        if (s != slot) return; // frame boundary crossed; drop rather than mix frames
        int q = queries[s][p * 2 + 1];
        GL33C.glQueryCounter(q, GL33C.GL_TIMESTAMP);
        pairEnded[s][p] = true;
        lastQuery[s] = q;
    }

    private static void collect(int s) {
        int count = pairCount[s];
        if (count == 0 || queries[s] == null) return;
        pairCount[s] = 0;

        if (GL15C.glGetQueryObjecti(lastQuery[s], GL15C.GL_QUERY_RESULT_AVAILABLE) == GL15C.GL_FALSE) {
            windowDropped++;
            return;
        }

        java.util.Arrays.fill(frameMs, 0.0);
        for (int p = 0; p < count; p++) {
            if (!pairEnded[s][p]) continue;
            long t0 = GL33C.glGetQueryObjecti64(queries[s][p * 2], GL15C.GL_QUERY_RESULT);
            long t1 = GL33C.glGetQueryObjecti64(queries[s][p * 2 + 1], GL15C.GL_QUERY_RESULT);
            frameMs[pairSection[s][p]] += (t1 - t0) / 1.0e6;
        }
        for (int i = 0; i < N; i++) {
            windowSum[i] += frameMs[i];
            windowMax[i] = Math.max(windowMax[i], frameMs[i]);
        }
        windowFrames++;

        long now = System.nanoTime();
        if (windowStart == 0) windowStart = now;
        if (now - windowStart >= WINDOW_NS && windowFrames > 0) {
            for (int i = 0; i < N; i++) {
                avgMs[i] = (float) (windowSum[i] / windowFrames);
                maxMs[i] = (float) windowMax[i];
                windowSum[i] = 0;
                windowMax[i] = 0;
            }
            dropped = windowDropped;
            windowFrames = 0;
            windowDropped = 0;
            windowStart = now;
        }
    }

    private static void reset() {
        for (int s = 0; s < SLOTS; s++) {
            pairCount[s] = 0;
            if (queries[s] != null) {
                GL15C.glDeleteQueries(queries[s]);
                queries[s] = null;
            }
        }
        java.util.Arrays.fill(windowSum, 0);
        java.util.Arrays.fill(windowMax, 0);
        java.util.Arrays.fill(avgMs, 0);
        java.util.Arrays.fill(maxMs, 0);
        windowFrames = 0;
        windowStart = 0;
        windowDropped = 0;
        dropped = 0;
    }

    public static float avgMs(TimerSection s) {
        return avgMs[s.ordinal()];
    }

    public static float maxMs(TimerSection s) {
        return maxMs[s.ordinal()];
    }

    public static int droppedLastWindow() {
        return dropped;
    }
}
