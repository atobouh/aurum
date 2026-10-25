package dev.impeccable.aurum.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.render.surface.SurfaceLighting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Development-only file requests for deterministic lighting tests; world commands require the disposable save. */
public final class LightingTestBridge {
    private static final Gson JSON=new Gson();
    private static final Path DIR=FabricLoader.getInstance().getGameDir().resolve("debug/aurum-test-bridge");
    private LightingTestBridge() {}
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            Path request=DIR.resolve("request.json");
            if(!Files.isRegularFile(request))return;
            String id="unknown";
            try {
                var input=JSON.fromJson(Files.readString(request),JsonObject.class);Files.delete(request);
                id=input.get("id").getAsString();String value=input.get("command").getAsString();
                var server=client.getServer();
                if(server==null||!server.getSaveProperties().getLevelName().equals("Aurum Lighting Audit"))
                    throw new IllegalStateException("Lighting test bridge requires the disposable Aurum Lighting Audit save");
                client.setScreen(null);client.options.pauseOnLostFocus=false;
                switch(value) {
                    case "aurumdev reload" -> { AurumConfig.load();ack(id,"reloaded",null); }
                    case "aurumdev lighting snapshot" -> ack(id,LightingDebug.snapshot().getFileName().toString(),null);
                    case "aurumdev lightrecord start" -> {LightingMotionRecorder.start();ack(id,"started",null);}
                    case "aurumdev lightrecord stop" -> ack(id,LightingMotionRecorder.stop().getFileName().toString(),null);
                    case "aurumdev lightmotion start" -> {LightingMotionRecorder.motion(true);ack(id,"started",null);}
                    case "aurumdev lightmotion stop" -> {LightingMotionRecorder.motion(false);ack(id,"stopped",null);}
                    case "hud hidden" -> {client.options.hudHidden=true;ack(id,"hidden",null);}
                    case "hud visible" -> {client.options.hudHidden=false;ack(id,"visible",null);}
                    case "stop client" -> {ack(id,"stopping",null);client.scheduleStop();}
                    default -> {
                        if(value.startsWith("aurumdev lighting ")) {
                            LightingDebug.select(LightingDebug.View.valueOf(value.substring("aurumdev lighting ".length()).toUpperCase(Locale.ROOT)));
                            ack(id,"selected",null);
                        } else if(value.startsWith("aurumdev ")) throw new IllegalArgumentException("Unsupported test command");
                        else {
                            String requestId=id;var playerId=client.player.getUuid();
                            server.execute(()->{
                                try {
                                    var player=server.getPlayerManager().getPlayer(playerId);
                                    if(player==null)throw new IllegalStateException("No local test player");
                                    int result=server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),value);
                                    ack(requestId,Integer.toString(result),null);
                                } catch(Exception e) {ack(requestId,null,e.toString());}
                            });
                        }
                    }
                }
            } catch(Exception e) {ack(id,null,e.toString());}
        });
    }
    private static void ack(String id,String result,String error) {
        try {
            Files.createDirectories(DIR);var data=new LinkedHashMap<String,Object>();
            data.put("id",id);data.put("result",result);data.put("error",error);data.put("frame",SurfaceLighting.frame());
            Files.writeString(DIR.resolve("response.json"),JSON.toJson(data));
        } catch(Exception e) {dev.impeccable.aurum.AurumMod.LOGGER.error("Lighting bridge response failed",e);}
    }
}
