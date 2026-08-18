package com.infiniteplugins.lpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextFormatter {

	private static final Pattern LEGACY_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})|&x(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))");
	private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&([0-9a-fk-or])");
	private static final Pattern RESOURCE_PACK_TAG = Pattern.compile("(?i)</?(?:glyph|shift|font|effect)(?::(?:'[^']*'|\"[^\"]*\"|[^>])*)?>");
	private static final String[] NAMES = {
		"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
		"dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
	};
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
			.character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

	Component deserialize(final String input) {
		return MINI_MESSAGE.deserialize(legacyToMiniMessage(input == null ? "" : input));
	}

	Component deserializeUserMessage(final String input, final boolean legacy, final boolean rgb, final boolean miniMessage) {
		String value = stripResourcePackContent(input == null ? "" : input);
		if (!miniMessage) {
			value = MINI_MESSAGE.escapeTags(value);
		}
		if (rgb) {
			value = hexToMiniMessage(value);
		} else {
			value = LEGACY_HEX.matcher(value).replaceAll("");
		}
		if (legacy) {
			value = codesToMiniMessage(value);
		} else {
			value = LEGACY_CODE.matcher(value).replaceAll("");
		}
		return MINI_MESSAGE.deserialize(value);
	}

	private String stripResourcePackContent(final String input) {
		return stripPrivateUseCharacters(RESOURCE_PACK_TAG.matcher(input).replaceAll(""));
	}

	private String stripPrivateUseCharacters(final String input) {
		final StringBuilder result = new StringBuilder(input.length());
		for (int offset = 0; offset < input.length();) {
			final int codePoint = input.codePointAt(offset);
			offset += Character.charCount(codePoint);
			if (Character.getType(codePoint) != Character.PRIVATE_USE) {
				result.appendCodePoint(codePoint);
			}
		}
		return result.toString();
	}

	String serializeLegacy(final Component component) {
		return LEGACY.serialize(component);
	}

	private String legacyToMiniMessage(final String input) {
		return codesToMiniMessage(hexToMiniMessage(input));
	}

	private String hexToMiniMessage(final String input) {
		Matcher matcher = LEGACY_HEX.matcher(input);
		final StringBuffer hex = new StringBuffer(input.length());
		while (matcher.find()) {
			String color = matcher.group(1);
			if (color == null) {
				final StringBuilder builder = new StringBuilder(6);
				for (int i = 2; i <= 7; i++) builder.append(matcher.group(i));
				color = builder.toString();
			}
			matcher.appendReplacement(hex, Matcher.quoteReplacement("<#" + color + ">"));
		}
		return matcher.appendTail(hex).toString();
	}

	private String codesToMiniMessage(final String input) {
		Matcher matcher = LEGACY_CODE.matcher(input);
		final StringBuffer result = new StringBuffer(input.length());
		while (matcher.find()) {
			final char code = Character.toLowerCase(matcher.group(1).charAt(0));
			final String tag;
			if (code >= '0' && code <= '9') tag = NAMES[code - '0'];
			else if (code >= 'a' && code <= 'f') tag = NAMES[10 + code - 'a'];
			else if (code == 'k') tag = "obfuscated";
			else if (code == 'l') tag = "bold";
			else if (code == 'm') tag = "strikethrough";
			else if (code == 'n') tag = "underlined";
			else if (code == 'o') tag = "italic";
			else tag = "reset";
			matcher.appendReplacement(result, Matcher.quoteReplacement("<" + tag + ">"));
		}
		return matcher.appendTail(result).toString();
	}
}
