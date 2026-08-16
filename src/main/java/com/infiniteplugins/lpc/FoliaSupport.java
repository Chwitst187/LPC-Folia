package com.infiniteplugins.lpc;

import org.bukkit.entity.Player;

final class FoliaSupport {

	private FoliaSupport() {
	}

	static void clearChat(final LPC plugin, final String clearMessage) {
		for (final Player player : plugin.getServer().getOnlinePlayers()) {
			player.getScheduler().run(plugin, task -> {
				for (int i = 0; i < 100; i++) player.sendMessage("");
				player.sendMessage(plugin.formatComponent(clearMessage));
			}, null);
		}
	}
}
