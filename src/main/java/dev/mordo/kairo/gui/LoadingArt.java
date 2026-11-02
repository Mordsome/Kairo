package dev.mordo.kairo.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.io.InputStream;

/**
 * The client's loading screen (in place of Mojang's): deep navy, the Kairo wordmark, and a slim accent progress
 * line. It shows before the game's resources (and the client's own fonts and shaders) exist, so it only uses
 * plain fills and an image read straight from the mod jar.
 */
public final class LoadingArt {
	public static final int BACKGROUND = 0xFF0B1019;
	private static final Identifier WORDMARK = ClientInfo.id("loading_wordmark");
	private static boolean ready;
	private static boolean failed;

	private LoadingArt() {
	}

	private static boolean ensureTexture() {
		if (ready || failed) {
			return ready;
		}
		try (InputStream in = LoadingArt.class.getResourceAsStream("/assets/kairo/textures/gui/loading_wordmark.png")) {
			if (in == null) {
				failed = true;
				return false;
			}
			NativeImage image = NativeImage.read(in);
			DynamicTexture texture = new DynamicTexture(() -> "kairo loading wordmark", image) {
				{
					// Smooth scaling: it's drawn much smaller than the image.
					this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			};
			Minecraft.getInstance().getTextureManager().register(WORDMARK, texture);
			ready = true;
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't load the loading screen wordmark", e);
			failed = true;
		}
		return ready;
	}

	/** The wordmark, centred on (cx, cy), {@code height} tall (4:1), breathing gently. */
	public static void drawLogo(GuiGraphicsExtractor g, int cx, int cy, int height, float alpha) {
		if (!ensureTexture()) {
			return;
		}
		float breathe = 0.92f + 0.08f * (float) Math.sin(System.currentTimeMillis() / 700.0);
		int h = Math.max(8, Math.round(height * 0.8f));
		int w = h * 4;
		g.blit(RenderPipelines.GUI_TEXTURED, WORDMARK, cx - w / 2, cy - h / 2, 0, 0, w, h, 1024, 256, 1024, 256,
			ARGB.white(Mth.clamp(alpha * breathe, 0, 1)));
	}

	/** Slim progress line: dim track, accent fill with a soft bright head. */
	public static void drawProgress(GuiGraphicsExtractor g, int x0, int x1, int cy, float progress, float fade) {
		int alpha = Math.round(Mth.clamp(fade, 0, 1) * 255);
		if (alpha <= 0) {
			return;
		}
		int accent = ClientSettings.INSTANCE.accent.argb() & 0xFFFFFF;
		int width = x1 - x0;
		int filled = Math.round(width * Mth.clamp(progress, 0, 1));
		g.fill(x0, cy - 1, x1, cy + 1, ARGB.color(Math.round(alpha * 0.14f), 255, 255, 255));
		if (filled > 0) {
			g.fill(x0, cy - 1, x0 + filled, cy + 1, ARGB.color(alpha, accent >> 16 & 255, accent >> 8 & 255, accent & 255));
			// Brighter head.
			int head = Math.min(filled, 6);
			g.fill(x0 + filled - head, cy - 1, x0 + filled, cy + 1, ARGB.color(alpha, 255, 255, 255));
			g.fill(x0 + filled - head, cy - 2, x0 + filled, cy + 2, ARGB.color(Math.round(alpha * 0.35f), accent >> 16 & 255, accent >> 8 & 255, accent & 255));
		}
	}
}
