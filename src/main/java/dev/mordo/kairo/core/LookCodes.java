package dev.mordo.kairo.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.Setting;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Share codes for a whole look: your colours, button corners and every visual (and, in Kairo Void, its own
 * effects, atmosphere and crystal style), packed into one line of text you can paste to a friend. Keybinds, HUD
 * positions and anything personal are never in it: only how things look.
 */
public final class LookCodes {
	private static final String PREFIX = "TLOOK1-";
	/** The client settings a look carries (only looks, nothing personal). */
	private static final Set<String> CLIENT_KEYS = Set.of("accent", "pvp_accent", "accent_everywhere", "glass_tint", "button_radius", "button_glow",
		"button_outline", "button_color", "button_click", "living_ring", "menu_fx");

	private LookCodes() {
	}

	private static List<SettingHolder> holders() {
		return Edition.isVoid() ? List.of(ClientSettings.INSTANCE, GameVisuals.INSTANCE, VoidSettings.INSTANCE)
			: List.of(ClientSettings.INSTANCE, GameVisuals.INSTANCE);
	}

	private static boolean shared(SettingHolder holder, Setting<?> s) {
		if (s instanceof KeybindSetting) {
			return false;
		}
		if (holder == ClientSettings.INSTANCE) {
			return CLIENT_KEYS.contains(s.id());
		}
		return !s.id().equals("visuals_on");
	}

	/** Your current look as a share code. */
	public static String export() {
		JsonObject root = new JsonObject();
		for (SettingHolder holder : holders()) {
			JsonObject group = new JsonObject();
			for (Setting<?> s : holder.settings()) {
				if (shared(holder, s)) {
					group.add(s.id(), s.normalJson());
				}
			}
			root.add(holder.id(), group);
		}
		byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(json);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		while (!deflater.finished()) {
			out.write(buffer, 0, deflater.deflate(buffer));
		}
		deflater.end();
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
	}

	/**
	 * Applies a share code. Returns how many settings it changed, or -1 if the text isn't a valid code. Values a code
	 * doesn't have (an older client, the other edition) stay as they are.
	 */
	public static int apply(@Nullable String code) {
		JsonObject root = decode(code);
		if (root == null) {
			return -1;
		}
		int changed = 0;
		for (SettingHolder holder : holders()) {
			if (!root.has(holder.id()) || !root.get(holder.id()).isJsonObject()) {
				continue;
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(holder.id()).entrySet()) {
				Setting<?> s = holder.setting(e.getKey());
				if (s != null && shared(holder, s) && load(s, e.getValue())) {
					changed++;
				}
			}
		}
		return changed;
	}

	private static <T> boolean load(Setting<T> setting, JsonElement json) {
		try {
			if (setting instanceof ColorSetting color) {
				color.setMode(ColorSetting.Mode.STATIC);
			}
			T value = setting.fromJson(json);
			boolean differs = !value.equals(setting.getNormal());
			setting.set(value);
			return differs;
		} catch (RuntimeException e) {
			return false; // a value this version doesn't understand: skip it
		}
	}

	private static @Nullable JsonObject decode(@Nullable String code) {
		if (code == null) {
			return null;
		}
		String text = code.trim().replaceAll("\\s+", "");
		int at = text.indexOf(PREFIX);
		if (at < 0) {
			return null;
		}
		try {
			byte[] packed = Base64.getUrlDecoder().decode(text.substring(at + PREFIX.length()));
			Inflater inflater = new Inflater();
			inflater.setInput(packed);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			while (!inflater.finished()) {
				int n = inflater.inflate(buffer);
				if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				out.write(buffer, 0, n);
				if (out.size() > 256 * 1024) {
					inflater.end();
					return null; // not a look code: far too big
				}
			}
			inflater.end();
			JsonElement parsed = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (IllegalArgumentException | DataFormatException | com.google.gson.JsonParseException e) {
			Kairo.LOGGER.info("Not a look code: {}", e.getMessage());
			return null;
		}
	}
}
