package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** World and server icons as textures, loaded once and cached. Draws a placeholder tile when there's no icon. */
public final class Favicons {
	private static final Map<String, Entry> WORLDS = new HashMap<>();
	private static final Map<String, Entry> SERVERS = new HashMap<>();

	private record Entry(@Nullable FaviconTexture texture, @Nullable Object source) {
	}

	private Favicons() {
	}

	public static @Nullable Identifier world(LevelSummary summary) {
		Entry entry = WORLDS.computeIfAbsent(summary.getLevelId(), id -> {
			Path icon = summary.getIcon();
			if (icon == null || !Files.isRegularFile(icon)) {
				return new Entry(null, null);
			}
			FaviconTexture texture = FaviconTexture.forWorld(Minecraft.getInstance().getTextureManager(), id);
			try (InputStream in = Files.newInputStream(icon)) {
				texture.upload(NativeImage.read(in));
				return new Entry(texture, null);
			} catch (Exception e) {
				Kairo.LOGGER.debug("Bad world icon {}", icon, e);
				texture.close();
				return new Entry(null, null);
			}
		});
		return entry.texture() == null ? null : entry.texture().textureLocation();
	}

	public static @Nullable Identifier server(ServerData data) {
		byte[] bytes = data.getIconBytes();
		Entry entry = SERVERS.get(data.ip);
		if (entry != null && entry.source() == bytes) {
			return entry.texture() == null ? null : entry.texture().textureLocation();
		}
		if (entry != null && entry.texture() != null) {
			entry.texture().close();
		}
		FaviconTexture texture = null;
		if (bytes != null) {
			texture = FaviconTexture.forServer(Minecraft.getInstance().getTextureManager(), data.ip);
			try {
				texture.upload(NativeImage.read(bytes));
			} catch (Exception e) {
				texture.close();
				texture = null;
			}
		}
		SERVERS.put(data.ip, new Entry(texture, bytes));
		return texture == null ? null : texture.textureLocation();
	}

	/** Forget a world's icon (renamed, edited, deleted). */
	public static void forgetWorld(String levelId) {
		Entry entry = WORLDS.remove(levelId);
		if (entry != null && entry.texture() != null) {
			entry.texture().close();
		}
	}

	public static void forgetAllWorlds() {
		for (Entry entry : WORLDS.values()) {
			if (entry.texture() != null) {
				entry.texture().close();
			}
		}
		WORLDS.clear();
	}

	/** Draws an icon (or a placeholder with {@code fallback}) in a size x size tile at (x, y). */
	public static void draw(GuiGraphicsExtractor g, @Nullable Identifier texture, Icon fallback, float x, float y, float size, float alpha) {
		if (texture != null && alpha > 0.5f) {
			int ix = Math.round(x);
			int iy = Math.round(y);
			int s = Math.round(size);
			g.blit(texture, ix, iy, ix + s, iy + s, 0, 1, 0, 1);
			Shapes.outline(g, ix, iy, s, s, 4, Colors.argb(0.18f * alpha, 0xFFFFFF), 1);
		} else {
			Shapes.rect(g, x, y, size, size, size * 0.22f, Colors.argb(0.08f * alpha, 0xFFFFFF), Colors.argb(0.12f * alpha, 0xFFFFFF), 1);
			IconRenderer.draw(g, fallback, x + size / 2, y + size / 2, size * 0.45f, Theme.text(0.5f, alpha), 1.8f);
		}
	}
}
