package me.wikmor.lpc;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PaperChatListener implements Listener {

    private final LPC plugin;

    public PaperChatListener(final LPC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        final Player player = event.getPlayer();

        final String format = plugin.buildFormat(player);
        final String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        final String processedMessage = plugin.processMessage(player, rawMessage);
        final Component rendered = LegacyComponentSerializer.legacySection()
                .deserialize(format.replace("{message}", processedMessage));

        event.renderer((source, sourceDisplayName, msg, audience) -> rendered);
    }
}
