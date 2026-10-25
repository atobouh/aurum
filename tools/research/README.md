# Excalibur surface-depth research

This phase records the existing renderer and investigates general texture relief. It does not change production shaders or publish a mod JAR. The trailer could not be accessed; do not claim it was watched.

`excal_relief_audit.py` inspects a local official pack ZIP and executes the exact generated-height method extracted from Aurum's Java source. It emits counts, texture-reference metadata and numerical measurements, without copying pack assets into the repository. It requires JDK 21 and Pillow:

```bash
JAVA_HOME=/path/to/jdk21 python3 tools/research/excal_relief_audit.py \
  --pack /path/to/Excalibur_V1.20.zip \
  --minecraft-client /path/to/minecraft-client.jar \
  --out /path/to/evidence
```

The pinned reference is Modrinth version `yVpdNmnj`; download through its official version API and check the SHA-256 recorded in the research document. The resource pack is not redistributed here.

`excal_capture_baseline.py vanilla|excal` uses the development-only lighting bridge and alters the disposable **Aurum Lighting Audit** save. It constructs a new detail board near `-1234 200 4118`, changes time/weather/inventory, and stops the client after each profile. Do not run against an important save. See the shared bridge instructions in `../lighting_integration/README.md` for the 1280×720 desktop and tooling requirements. Set `AURUM_EXCAL_EVIDENCE` for the capture output directory.

Back up `run/config/aurum.json` and `run/options.txt`. Before starting either client, use `labPbr=false` and `pomGenerated=true` so the resource-reload bake has the intended inputs. Run the vanilla profile without external packs. After the client closes, select the local Excalibur pack in resource-pack options, restart and run the Excalibur profile after the player has entered the world. Restore the original config/options and pack selection when finished. Only the disposable fixture should be changed.

The report generator additionally takes official gallery downloads/project metadata and the manually reviewed `baseline-checks.json` from the evidence directory:

```bash
python3 tools/render_excal_research.py --evidence /path/to/evidence
```

Its primary deliverable is `dist/aurum-excalibur-relief-research.zip`: extract all and open `Aurum-Excalibur-research/index.html`. It includes four attributed gallery references, eleven original Minecraft captures and evidence JSON. The current Excalibur overview POM control is unresolved; a settings flag or an almost-zero pixel difference is not proof that relief works.
