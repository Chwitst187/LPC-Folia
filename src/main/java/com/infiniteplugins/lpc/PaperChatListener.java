package com.infiniteplugins.lpc;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class PaperChatListener implements Listener {

	private final LPC plugin;

	PaperChatListener(final LPC plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChat(final AsyncChatEvent event) {
		// A ChatRenderer is invoked once for every viewer. Build the formatted
		// component only once so integrations such as Oraxen glyph handling are
		// not executed repeatedly for the same chat message.
		final Component formattedMessage = plugin.formatPaperMessage(event.getPlayer(), event.message());
		event.renderer((source, sourceDisplayName, message, viewer) -> formattedMessage);
	}
}
