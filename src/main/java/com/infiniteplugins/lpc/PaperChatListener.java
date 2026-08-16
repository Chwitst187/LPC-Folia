package com.infiniteplugins.lpc;

import io.papermc.paper.event.player.AsyncChatEvent;
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
		event.renderer((source, sourceDisplayName, message, viewer) ->
				plugin.formatPaperMessage(source, message));
	}
}
