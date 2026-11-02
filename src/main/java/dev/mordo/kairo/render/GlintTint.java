package dev.mordo.kairo.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.GameVisuals.GlintColor;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;

/**
 * Recolours the enchantment shimmer: swaps the two glint textures for live copies tinted with your colour, a custom
 * one or a slowly cycling rainbow. Back to the game's own textures when set to Vanilla.
 */
public final class GlintTint {
	private static final Identifier[] IDS = {ItemFeatureRenderer.ENCHANTED_GLINT_ITEM, ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR};
	private static final @Nullable NativeImage[] ORIGINALS = new NativeImage[IDS.length];
	private static final @Nullable DynamicTexture[] OURS = new DynamicTexture[IDS.length];
	private static int lastColor;
	private static boolean failed;

	private GlintTint() {
	}

	public static void tick(Minecraft mc) {
		if (failed || !mc.isGameLoadFinished()) {
			return;
		}
		GlintColor mode = Visuals.live() ? GameVisuals.INSTANCE.glintColor.get() : GlintColor.VANILLA;
		TextureManager textures = mc.getTextureManager();
		if (mode == GlintColor.VANILLA) {
			for (int i = 0; i < IDS.length; i++) {
				if (OURS[i] != null) {
					textures.release(IDS[i]); // the game reloads its own texture on next use
					OURS[i] = null;
				}
			}
			return;
		}
		int color = switch (mode) {
			case ACCENT -> Theme.accent();
			case CUSTOM -> GameVisuals.INSTANCE.glintCustom.argb();
			case RAINBOW -> Colors.hsv((System.currentTimeMillis() % 6000L) / 6000f, 0.75f, 1f);
			case VANILLA -> 0;
		} | 0xFF000000;
		try {
			boolean changed = color != lastColor;
			for (int i = 0; i < IDS.length; i++) {
				if (ORIGINALS[i] == null) {
					try (InputStream in = mc.getResourceManager().open(IDS[i])) {
						ORIGINALS[i] = NativeImage.read(in);
					}
				}
				AbstractTexture current = textures.getTexture(IDS[i]);
				if (OURS[i] == null || current != OURS[i]) {
					// First use, or a resource reload put the game's texture back.
					NativeImage copy = new NativeImage(ORIGINALS[i].getWidth(), ORIGINALS[i].getHeight(), true);
					copy.copyFrom(ORIGINALS[i]);
					OURS[i] = new DynamicTexture(() -> "kairo glint", copy);
					textures.register(IDS[i], OURS[i]);
					changed = true;
				}
				if (changed) {
					tint(ORIGINALS[i], OURS[i].getPixels(), color);
					OURS[i].upload();
				}
			}
			lastColor = color;
		} catch (Exception e) {
			failed = true;
			Kairo.LOGGER.warn("Couldn't recolour the enchantment glint", e);
		}
	}

	/** Keeps the original's brightness pattern, in the new colour. */
	private static void tint(NativeImage source, @Nullable NativeImage target, int color) {
		if (target == null) {
			return;
		}
		float r = ((color >> 16) & 0xFF) / 255f;
		float g = ((color >> 8) & 0xFF) / 255f;
		float b = (color & 0xFF) / 255f;
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int p = source.getPixel(x, y);
				int a = (p >>> 24) & 0xFF;
				float lum = Math.max((p >> 16) & 0xFF, Math.max((p >> 8) & 0xFF, p & 0xFF)) / 255f * 1.15f;
				int nr = Math.min(255, Math.round(r * lum * 255));
				int ng = Math.min(255, Math.round(g * lum * 255));
				int nb = Math.min(255, Math.round(b * lum * 255));
				target.setPixel(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
			}
		}
	}
}
