package me.wikmor.lpc;

import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LPC extends JavaPlugin implements Listener {

	private LuckPerms luckPerms;
	
	@Override
	public void onEnable() {
		// Load an instance of 'LuckPerms' using the services manager.
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);

		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(final @NonNull CommandSender sender, final @NonNull Command command, final @NonNull String label, final String[] args) {
		if (args.length == 1 && "reload".equals(args[0])) {
			reloadConfig();

			sender.sendMessage(colorizeToSection("&aLPC has been reloaded."));
			return true;
		}

		return false;
	}

	@Override
	public List<String> onTabComplete(final @NonNull CommandSender sender, final @NonNull Command command, final @NonNull String alias, final String[] args) {
		if (args.length == 1)
			return Collections.singletonList("reload");

		return new ArrayList<>();
	}

	/**
	 * AsyncPlayerChatEvent is deprecated in newer APIs in favor of the component-based AsyncChatEvent.
	 * Migrating to AsyncChatEvent requires component-based formatting; keep this handler for
	 * compatibility and suppress the deprecation warning to avoid IDE noise.
	 */
	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChat(final AsyncPlayerChatEvent event) {
		final String message = event.getMessage();
		final Player player = event.getPlayer();

		// Try to get a LuckPerms User via the UserManager (preferred over the deprecated PlayerAdapter).
		CachedMetaData metaData;
		String group;
		String prefix = "";
		String suffix = "";
		String prefixesJoined = "";
		String suffixesJoined = "";
		String usernameColor = "";
		String messageColor = "";

		try {
			final UUID uuid = player.getUniqueId();
			final User user = this.luckPerms != null ? this.luckPerms.getUserManager().getUser(uuid) : null;

			if (user != null) {
				metaData = user.getCachedData().getMetaData();
				group = metaData.getPrimaryGroup();
				prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
				suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";
				try { prefixesJoined = String.join("", metaData.getPrefixes().values()); } catch (Exception ignored) {}
				try { suffixesJoined = String.join("", metaData.getSuffixes().values()); } catch (Exception ignored) {}
				usernameColor = metaData.getMetaValue("username-color") != null ? Objects.requireNonNull(metaData.getMetaValue("username-color")) : "";
				messageColor = metaData.getMetaValue("message-color") != null ? Objects.requireNonNull(metaData.getMetaValue("message-color")) : "";
			} else {
				group = "";
			}
		} catch (Exception ex) {
			group = "";
		}

		// build displayname string from Component API (avoid deprecated getDisplayName)
		final LegacyComponentSerializer ampSerializer = LegacyComponentSerializer.builder().character('&').hexColors().build();
		final String displayNameString = ampSerializer.serialize(player.displayName());

		String format = Objects.requireNonNull(getConfig().getString(getConfig().getString("group-formats." + group) != null ? "group-formats." + group : "chat-format"))
				.replace("{prefix}", prefix)
				.replace("{suffix}", suffix)
				.replace("{prefixes}", prefixesJoined)
				.replace("{suffixes}", suffixesJoined)
				.replace("{world}", player.getWorld().getName())
				.replace("{name}", player.getName())
				.replace("{displayname}", displayNameString)
				.replace("{username-color}", usernameColor)
				.replace("{message-color}", messageColor);

		format = ampSerializer.serialize(ampSerializer.deserialize(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? PlaceholderAPI.setPlaceholders(player, format) : format));

		String processedMessage;
		final boolean allowColor = player.hasPermission("lpc.colorcodes");
		final boolean allowRgb = player.hasPermission("lpc.rgbcodes");

		// Simplified logic: handle the four permission combinations explicitly.
		if (allowColor && allowRgb) {
			// keep both & color codes and hex sequences
			processedMessage = message;
		} else if (allowColor) {
			// keep & color codes, strip hex (&#rrggbb)
			processedMessage = message.replaceAll("&#[A-Fa-f0-9]{6}", "");
		} else if (allowRgb) {
			// strip & codes, keep hex sequences
			processedMessage = message.replaceAll("(?i)&[0-9A-FK-OR]", "");
		} else {
			// strip both
			processedMessage = message.replaceAll("(?i)&[0-9A-FK-OR]", "");
			processedMessage = processedMessage.replaceAll("&#[A-Fa-f0-9]{6}", "");
		}

		final String finalFormat = format.replace("{message}", processedMessage).replace("%", "%%");

		// convert legacy '&' + hex string to section-prefixed string and set format
		event.setFormat(colorizeToSection(finalFormat));
	}

	private String colorizeToSection(final String message) {
		if (message == null) return "";
		final LegacyComponentSerializer fromAmp = LegacyComponentSerializer.builder().character('&').hexColors().build();
		final LegacyComponentSerializer toSection = LegacyComponentSerializer.builder().character('§').hexColors().build();
		final Component comp = fromAmp.deserialize(message);
		return toSection.serialize(comp);
	}
}