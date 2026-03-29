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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LPC extends JavaPlugin implements Listener {
	private static final LegacyComponentSerializer AMP_SERIALIZER = LegacyComponentSerializer.builder().character('&').hexColors().build();
	private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder().character('§').hexColors().build();
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

	private LuckPerms luckPerms;


	@Override
	public void onEnable() {
		// Load an instance of 'LuckPerms' using the services manager.
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);
		if (this.luckPerms == null) {
			getLogger().severe("LuckPerms not found! LPC requires LuckPerms to function.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		saveDefaultConfig();

		boolean paperChat = false;
		try {
			Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
			paperChat = true;
		} catch (ClassNotFoundException ignored) {
		}

		if (paperChat) {
			getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
		} else {
			getServer().getPluginManager().registerEvents(this, this);
		}

		final String[] chatPlugins = {"EssentialsChat", "VentureChat", "HeroChat", "DeluxeChat", "ChatManager", "ChatEx", "UltraChat", "TownyChat"};
		for (final String pluginName : chatPlugins) {
			if (getServer().getPluginManager().isPluginEnabled(pluginName)) {
				getLogger().warning("Detected " + pluginName + " which may also format chat. To avoid message duplication, disable chat formatting in " + pluginName + ".");
			}
		}
	}

	@Override
	public boolean onCommand(final @NonNull CommandSender sender, final @NonNull Command command, final @NonNull String label, final String[] args) {
		if (args.length == 1 && "reload".equals(args[0])) {
			reloadConfig();

			sender.sendMessage(colorizeToSection("&aLPC has been reloaded."));
			return true;
		}

		if (args.length == 1 && "clear".equals(args[0]) && sender.hasPermission("lpc.clearchat")) {
			for (final Player player : getServer().getOnlinePlayers()) {
				for (int i = 0; i < 100; i++) {
					player.sendMessage("");
				}
			}
			final String clearMessage = getConfig().getString("clear-chat-message", "&7Chat has been cleared by a staff member.");
			final String formatted = colorize(clearMessage);
			getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
			getServer().getConsoleSender().sendMessage(formatted);
			return true;
		}

		if (args.length == 2 && "debug".equals(args[0]) && sender.hasPermission("lpc.debug")) {
			final Player target = getServer().getPlayer(args[1]);
			if (target == null) {
				sender.sendMessage(colorize("&cPlayer not found."));
				return true;
			}
			final CachedMetaData debugMeta = luckPerms.getPlayerAdapter(Player.class).getMetaData(target);
			sender.sendMessage(colorize("&6&lLPC Debug: &f" + target.getName()));
			sender.sendMessage(colorize("&7Primary Group: &f" + debugMeta.getPrimaryGroup()));
			sender.sendMessage(colorize("&7Prefix: &f" + (debugMeta.getPrefix() != null ? debugMeta.getPrefix() : "&cnone")));
			sender.sendMessage(colorize("&7Suffix: &f" + (debugMeta.getSuffix() != null ? debugMeta.getSuffix() : "&cnone")));
			sender.sendMessage(colorize("&7All Prefixes (by weight):"));
			debugMeta.getPrefixes().forEach((weight, prefix) ->
					sender.sendMessage(colorize("  &7[" + weight + "] &f" + prefix)));
			sender.sendMessage(colorize("&7All Suffixes (by weight):"));
			debugMeta.getSuffixes().forEach((weight, suffix) ->
					sender.sendMessage(colorize("  &7[" + weight + "] &f" + suffix)));
			sender.sendMessage(colorize("&7Username-color: &f" + (debugMeta.getMetaValue("username-color") != null ? debugMeta.getMetaValue("username-color") : "&cnone")));
			sender.sendMessage(colorize("&7Message-color: &f" + (debugMeta.getMetaValue("message-color") != null ? debugMeta.getMetaValue("message-color") : "&cnone")));
			sender.sendMessage(colorize("&7Group format: &f" + (getConfig().getString("group-formats." + debugMeta.getPrimaryGroup()) != null ? "group-formats." + debugMeta.getPrimaryGroup() : "chat-format (default)")));
			sender.sendMessage(colorize("&7PAPI: &f" + (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? "&ahooked" : "&cnot found")));
			sender.sendMessage(colorize("&7Has lpc.colorcodes: &f" + target.hasPermission("lpc.colorcodes")));
			sender.sendMessage(colorize("&7Has lpc.rgbcodes: &f" + target.hasPermission("lpc.rgbcodes")));
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
		final Player player = event.getPlayer();
		final String formatString = buildFormatString(player);
		final Component messageComponent = renderMessageComponent(player, event.getMessage());
		final Component rendered = renderFormatComponent(formatString, messageComponent);
		final String legacyRendered = SECTION_SERIALIZER.serialize(rendered).replace("%", "%%");
		event.setFormat(legacyRendered);
	}

	String buildFormatString(final Player player) {

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
		final String displayNameString = AMP_SERIALIZER.serialize(player.displayName());

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

		format = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? PlaceholderAPI.setPlaceholders(player, format) : format;
		return format;
	}

	Component renderMessageComponent(final Player player, final String rawMessage) {
		final boolean allowRgb = player.hasPermission("lpc.rgbcodes");
		final boolean allowColor = allowRgb || player.hasPermission("lpc.colorcodes");

		String message = rawMessage == null ? "" : rawMessage;
		if (!allowColor) {
			message = stripColorCodes(stripHexCodes(message));
		} else if (!allowRgb) {
			message = stripHexCodes(message);
		}
		message = stripMiniMessageTags(message);

		final Component component = allowColor ? AMP_SERIALIZER.deserialize(message) : Component.text(message);

		return stripInteractiveEvents(component);
	}

	Component renderFormatComponent(final String format, final Component messageComponent) {
		if (format == null) {
			return messageComponent;
		}

		final String placeholderToken = "{message}";
		if (containsMiniMessage(format)) {
			final String mmFormat = format.replace(placeholderToken, "<message>");
			try {
				return MINI_MESSAGE.deserialize(mmFormat, Placeholder.component("message", messageComponent));
			} catch (RuntimeException ex) {
				// fall through to legacy handling
			}
		}

		final String[] parts = format.split(Pattern.quote(placeholderToken), -1);
		Component combined = Component.empty();
		for (int i = 0; i < parts.length; i++) {
			combined = combined.append(deserializeChatComponent(parts[i]));
			if (i < parts.length - 1) {
				combined = combined.append(messageComponent);
			}
		}
		return combined;
	}

	private String colorize(final String message) {
		return colorizeToSection(message);
	}

	private String colorizeToSection(final String message) {
		if (message == null) return "";
		return SECTION_SERIALIZER.serialize(deserializeChatComponent(message));
	}

	Component deserializeChatComponent(final String message) {
		if (message == null || message.isEmpty()) {
			return Component.empty();
		}
		if (containsMiniMessage(message)) {
			try {
				return MINI_MESSAGE.deserialize(message);
			} catch (RuntimeException ignored) {
				return AMP_SERIALIZER.deserialize(message);
			}
		}
		return AMP_SERIALIZER.deserialize(message);
	}


	private Component stripInteractiveEvents(final Component component) {
		return component.children(component.children().stream().map(this::stripInteractiveEvents).collect(Collectors.toList()))
				.style(component.style().clickEvent(null).hoverEvent(null).insertion(null));
	}

	private String stripMiniMessageTags(final String input) {
		if (input == null || input.isEmpty()) return "";
		return input.replaceAll("<[^>]+>", "");
	}


	private boolean containsMiniMessage(final String message) {
		return message.contains("<") && message.contains(">");
	}

	String stripColorCodes(final String message) {
		return message.replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");
	}

	String stripHexCodes(final String message) {
		String result = message.replaceAll("&#[0-9a-fA-F]{6}", "");
		result = result.replaceAll("&x(&[0-9a-fA-F]){6}", "");
		return result;
	}
}
