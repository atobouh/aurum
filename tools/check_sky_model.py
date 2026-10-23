#!/usr/bin/env python3
"""Check sky integration convergence and finite LUT output using only Python and a JDK.

Run with JAVA_HOME set to the build JDK. The dense reference evaluates the same scattering equation
with 256 view and light segments; this measures quadrature convergence, not photographic accuracy.
"""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java_home = Path(os.environ.get("JAVA_HOME", "/usr")) / "bin"
source = root / "src/main/java/dev/impeccable/aurum/sky"
with tempfile.TemporaryDirectory(prefix="aurum-sky-check-") as directory:
    work = Path(directory)
    for name in ("SkyModel", "SkyLut"):
        text = (source / f"{name}.java").read_text().replace("package dev.impeccable.aurum.sky;", "")
        (work / f"{name}.java").write_text(text)
    dense = (work / "SkyModel.java").read_text().replace("SkyModel", "DenseSkyModel")
    dense = dense.replace("VIEW_STEPS = 24", "VIEW_STEPS = 256").replace("LIGHT_STEPS = 12", "LIGHT_STEPS = 256")
    assert "VIEW_STEPS = 256" in dense and "LIGHT_STEPS = 256" in dense
    (work / "DenseSkyModel.java").write_text(dense)
    (work / "Check.java").write_text(r'''
public class Check {
    public static void main(String[] args) {
        double[] actual = new double[3], reference = new double[3];
        double relativeError = 0;
        int count = 0;
        for (double sy : new double[]{1, .5, .12, .03, 0, -.05}) {
            for (double vy : new double[]{.005, .04, .2, .6, 1}) {
                for (double a : new double[]{0, 1.57, 3.14}) {
                    double h = Math.sqrt(1 - vy * vy), sx = Math.sqrt(1 - sy * sy);
                    SkyModel.radiance(h * Math.cos(a), vy, h * Math.sin(a), sx, sy, 0, actual);
                    DenseSkyModel.radiance(h * Math.cos(a), vy, h * Math.sin(a), sx, sy, 0, reference);
                    for (int c = 0; c < 3; c++) {
                        if (!Double.isFinite(actual[c]) || actual[c] < 0) throw new AssertionError("Radiance");
                        if (reference[c] > .0001) {
                            relativeError += Math.abs(actual[c] - reference[c]) / reference[c];
                            count++;
                        }
                    }
                }
            }
        }
        relativeError /= count;
        if (relativeError > .015) throw new AssertionError("Integration error: " + relativeError);
        System.out.printf("Single-scattering convergence: %.3f%% mean relative error / %d channels%n",
                relativeError * 100, count);
        int channels = 0;
        long start = System.nanoTime();
        for (float sy : new float[]{1, .5f, .12f, 0, -.05f, -.2f, -1}) {
            for (float rain : new float[]{0, 1}) {
                SkyLut lut = SkyLut.build(sy, rain, 0, 100, 100, 70);
                for (float[] values : new float[][]{lut.rgb, lut.sunColor, lut.sunDisc, lut.sunGlow,
                        lut.skyAmbient, lut.skySideToward, lut.skySideAway, lut.groundAmbient}) {
                    for (float value : values) {
                        if (!Float.isFinite(value) || value < 0) throw new AssertionError("LUT at " + sy);
                        channels++;
                    }
                }
            }
        }
        System.out.printf("Day/twilight/night + clear/rain: %d finite, nonnegative channels / 14 LUTs%n", channels);
        System.out.printf("LUT batch CPU time here: %.1f ms (not an Intel UHD benchmark)%n",
                (System.nanoTime() - start) / 1e6);
    }
}
''')
    subprocess.run([str(java_home / "javac"), *map(str, work.glob("*.java"))], check=True)
    subprocess.run([str(java_home / "java"), "-cp", str(work), "Check"], check=True)
