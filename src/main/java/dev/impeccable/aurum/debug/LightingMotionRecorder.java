package dev.impeccable.aurum.debug;

import com.google.gson.GsonBuilder;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.render.GlInfo;
import dev.impeccable.aurum.render.VoxelLighting;
import dev.impeccable.aurum.render.surface.SurfaceLighting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded development recorder. No recording/pose manipulation is registered in a release environment. */
public final class LightingMotionRecorder {
    private static List<Map<String,Object>> frames;
    private static long start,previous;
    private static volatile boolean motion;
    private static int motionStart;
    private LightingMotionRecorder() {}
    public static void registerMotion() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(!motion) return;
            var world=server.getOverworld();if(world==null)return;
            double phase=(server.getTicks()-motionStart)*.05;
            for(var entity:world.getEntitiesByType(EntityType.PIG,e->e.getCommandTags().contains("aurum_light_receiver")))
                entity.refreshPositionAndAngles(-1250.5+2*Math.cos(phase),-43,4119.5+2*Math.sin(phase),(float)Math.toDegrees(-phase),0);
            for(var entity:world.getEntitiesByType(EntityType.ARMOR_STAND,e->e.getCommandTags().contains("aurum_light_source")))
                entity.refreshPositionAndAngles(-1250.5+2.5*Math.cos(-phase*.7),-43,4119.5+2.5*Math.sin(-phase*.7),(float)Math.toDegrees(phase*.7),0);
        });
    }
    public static void motion(boolean enabled) {
        var server=MinecraftClient.getInstance().getServer();
        if(server!=null)server.execute(()->{motionStart=server.getTicks();motion=enabled;});
    }
    public static void start() { frames=new ArrayList<>();start=previous=System.nanoTime(); }
    public static void reset() { frames=null;motion=false; }
    public static void sample() {
        if(frames==null||frames.size()>=7200)return;
        var client=MinecraftClient.getInstance();if(client.world==null)return;
        long now=System.nanoTime();var camera=client.gameRenderer.getCamera();var pos=camera.getPos();
        var row=new LinkedHashMap<String,Object>();
        row.put("elapsedMillis",(now-start)/1e6);row.put("frameMillis",(now-previous)/1e6);previous=now;
        row.put("camera",new double[]{pos.x,pos.y,pos.z,camera.getYaw(),camera.getPitch()});
        row.put("worldTime",client.world.getTimeOfDay());row.put("exposure",Atmosphere.exposure);
        row.put("direct",Atmosphere.lightColor.clone());row.put("localScale",Atmosphere.blockLight.clone());
        row.put("cache",VoxelLighting.debugState());row.put("surface",SurfaceLighting.debugState());
        var entities=new ArrayList<Map<String,Object>>();
        // Scoreboard tags are server-side state; observe nearby client entities instead of assuming tag sync.
        for(var entity:client.world.getEntities())if(entity.squaredDistanceTo(pos)<24*24 && entities.size()<64)
            entities.add(Map.of("id",entity.getId(),"type",EntityType.getId(entity.getType()).toString(),
                    "position",new double[]{entity.getX(),entity.getY(),entity.getZ()},"yaw",entity.getYaw()));
        row.put("observedEntities",entities);
        if(client.player!=null) row.put("mainHand",net.minecraft.registry.Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString());
        frames.add(row);
    }
    public static Path stop() throws IOException {
        if(frames==null)throw new IOException("No lighting recording is active");
        var data=new LinkedHashMap<String,Object>();
        data.put("renderer",GlInfo.renderer());data.put("gl",GlInfo.version());
        data.put("scope","Development telemetry, not an Intel UHD benchmark. Frame durations include software rendering and instrumentation. No source-to-photon latency measurement.");
        data.put("frameLimitReached",frames.size()>=7200);data.put("frames",frames);
        Path dir=FabricLoader.getInstance().getGameDir().resolve("debug/aurum-lighting");Files.createDirectories(dir);
        Path path=Files.createTempFile(dir,"motion-",".json");
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(data)+"\n");frames=null;
        return path;
    }
}
