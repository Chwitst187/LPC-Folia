package com.infiniteplugins.lpc;

import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LPC extends JavaPlugin implements Listener {

	private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
	private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile("&x(&[A-Fa-f0-9]){6}");

	private LuckPerms luckPerms;
	private final TextFormatter textFormatter = new TextFormatter();
	private boolean folia;


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
		this.folia = classExists("io.papermc.paper.threadedregions.RegionizedServer");

		try {
			Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
			getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
		} catch (ClassNotFoundException ignored) {
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
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (args.length == 1 && "reload".equals(args[0]) && sender.hasPermission("lpc.reload")) {
			reloadConfig();
			sender.sendMessage(colorize("&aLPC has been reloaded."));
			return true;
		}

		if (args.length == 1 && "clear".equals(args[0]) && sender.hasPermission("lpc.clearchat")) {
			final String clearMessage = getConfig().getString("clear-chat-message", "&7Chat has been cleared by a staff member.");
			if (folia) {
				FoliaSupport.clearChat(this, clearMessage);
				return true;
			}
			for (final Player player : getServer().getOnlinePlayers()) {
				for (int i = 0; i < 100; i++) {
					player.sendMessage("");
				}
			}
			getServer().broadcastMessage(colorize(clearMessage));
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
			final String usernameColor = debugMeta.getMetaValue("username-color");
			final String messageColor = debugMeta.getMetaValue("message-color");
			sender.sendMessage(colorize("&7Username-color: &f" + (usernameColor != null ? usernameColor : "&cnone")));
			sender.sendMessage(colorize("&7Message-color: &f" + (messageColor != null ? messageColor : "&cnone")));
			sender.sendMessage(colorize("&7Group format: &f" + (getConfig().getString("group-formats." + debugMeta.getPrimaryGroup()) != null ? "group-formats." + debugMeta.getPrimaryGroup() : "chat-format (default)")));
			sender.sendMessage(colorize("&7PAPI: &f" + (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? "&ahooked" : "&cnot found")));
			sender.sendMessage(colorize("&7Has lpc.colorcodes: &f" + target.hasPermission("lpc.colorcodes")));
			sender.sendMessage(colorize("&7Has lpc.rgbcodes: &f" + target.hasPermission("lpc.rgbcodes")));
			return true;
		}

		return false;
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
		if (args.length == 1) {
			final String input = args[0].toLowerCase();
			final List<String> completions = new ArrayList<>();
			if (sender.hasPermission("lpc.reload") && "reload".startsWith(input)) completions.add("reload");
			if (sender.hasPermission("lpc.clearchat") && "clear".startsWith(input)) completions.add("clear");
			if (sender.hasPermission("lpc.debug") && "debug".startsWith(input)) completions.add("debug");
			return completions;
		}
		if (args.length == 2 && "debug".equals(args[0]) && sender.hasPermission("lpc.debug")) {
			return getServer().getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
					.collect(Collectors.toList());
		}
		return new ArrayList<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChat(final AsyncPlayerChatEvent event) {
		final String message = event.getMessage();
		final Player player = event.getPlayer();

		String format = buildFormat(player);
		String processedMessage = processMessage(player, message);

		event.setFormat(format.replace("{message}", processedMessage).replace("%", "%%"));
	}

	String buildFormat(final Player player) {
		return textFormatter.serializeLegacy(textFormatter.deserialize(buildRawFormat(player)));
	}

	String buildRawFormat(final Player player) {
		final CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
		final String group = metaData.getPrimaryGroup();

		String format = getConfig().getString(getConfig().getString("group-formats." + group) != null ? "group-formats." + group : "chat-format");
		if (format == null) {
			format = "{prefix}{name}&r: {message}";
		}

		final String prefix = metaData.getPrefix();
		final String suffix = metaData.getSuffix();
		final String usernameColor = metaData.getMetaValue("username-color");
		final String messageColor = metaData.getMetaValue("message-color");

		format = format
				.replace("{prefix}", prefix != null ? prefix : "")
				.replace("{suffix}", suffix != null ? suffix : "")
				.replace("{prefixes}", metaData.getPrefixes().keySet().stream().map(key -> metaData.getPrefixes().get(key)).collect(Collectors.joining()))
				.replace("{suffixes}", metaData.getSuffixes().keySet().stream().map(key -> metaData.getSuffixes().get(key)).collect(Collectors.joining()))
				.replace("{world}", player.getWorld().getName())
				.replace("{name}", player.getName())
				.replace("{displayname}", player.getDisplayName())
				.replace("{username-color}", usernameColor != null ? usernameColor : "")
				.replace("{message-color}", messageColor != null ? messageColor : "");

		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			format = PlaceholderAPI.setPlaceholders(player, format);
		}
		return format;
	}

	Component formatPaperMessage(final Player player, final Component originalMessage) {
		final String plainMessage = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
				.serialize(originalMessage);
		Component message = textFormatter.deserializeUserMessage(plainMessage,
				player.hasPermission("lpc.colorcodes"), player.hasPermission("lpc.rgbcodes"),
				player.hasPermission("lpc.minimessage"));
		if (player.hasPermission("lpc.emptylines")) {
			message = textFormatter.deserialize("<dark_gray>\u00bb</dark_gray> ")
					.append(message)
					.append(textFormatter.deserialize(" <dark_gray>\u00bb</dark_gray>"));
		}
		final String[] parts = buildRawFormat(player).split("\\{message}", -1);
		Component result = textFormatter.deserialize(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			result = result.append(message).append(textFormatter.deserialize(parts[i]));
		}
		return result;
	}

	Component formatComponent(final String text) {
		return textFormatter.deserialize(text);
	}

	String processMessage(final Player player, final String message) {
		String processed;
		if (player.hasPermission("lpc.colorcodes") && player.hasPermission("lpc.rgbcodes")) {
			processed = colorize(translateHexColorCodes(message));
		} else if (player.hasPermission("lpc.colorcodes")) {
			processed = colorize(stripHexCodes(message));
		} else if (player.hasPermission("lpc.rgbcodes")) {
			processed = stripColorCodes(translateHexColorCodes(message));
		} else {
			processed = stripColorCodes(stripHexCodes(message));
		}
		if (player.hasPermission("lpc.emptylines")) {
			processed = colorize("&8\u00bb ") + processed + colorize(" &8\u00bb");
		}
		return processed;
	}

	String colorize(final String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	String translateHexColorCodes(final String message) {
		final char colorChar = ChatColor.COLOR_CHAR;

		// Handle &#rrggbb format
		Matcher matcher = HEX_PATTERN.matcher(message);
		StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
		while (matcher.find()) {
			final String group = matcher.group(1);
			matcher.appendReplacement(buffer, colorChar + "x"
					+ colorChar + group.charAt(0) + colorChar + group.charAt(1)
					+ colorChar + group.charAt(2) + colorChar + group.charAt(3)
					+ colorChar + group.charAt(4) + colorChar + group.charAt(5));
		}
		String result = matcher.appendTail(buffer).toString();

		// Handle &x&r&r&g&g&b&b format (Bukkit-style)
		matcher = BUKKIT_HEX_PATTERN.matcher(result);
		buffer = new StringBuffer(result.length());
		while (matcher.find()) {
			matcher.appendReplacement(buffer, matcher.group().replace('&', colorChar));
		}
		return matcher.appendTail(buffer).toString();
	}

	String stripColorCodes(final String message) {
		return message.replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");
	}

	String stripHexCodes(final String message) {
		String result = message.replaceAll("&#[0-9a-fA-F]{6}", "");
		result = result.replaceAll("&x(&[0-9a-fA-F]){6}", "");
		return result;
	}

	private boolean classExists(final String name) {
		try {
			Class.forName(name);
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}
}
