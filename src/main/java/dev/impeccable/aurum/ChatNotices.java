package dev.impeccable.aurum;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Messages raised before a world is loaded (startup problems) and shown in chat once the player joins. */
public final class ChatNotices {
    private static final List<Text> pending = new ArrayList<>();

    private ChatNotices() {}

    public static synchronized void queue(Text message) {
        pending.add(message);
    }

    /** Called when the player joins a world. Notices stay queued so they show again on every join. */
    public static synchronized void deliver(MinecraftClient client) {
        if (client.player == null) return;
        for (Text t : pending) client.player.sendMessage(t, false);
    }
}
