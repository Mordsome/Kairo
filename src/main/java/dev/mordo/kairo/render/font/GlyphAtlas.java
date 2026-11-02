package dev.mordo.kairo.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.render.TextureSetup;

/**
 * One big single-channel texture for every glyph of every size, packed in shelves.
 * Sampled with linear filtering; each glyph is uploaded with a 1px empty border so edges filter cleanly.
 */
final class GlyphAtlas {
	static final int SIZE = 2048;

	private final GpuTexture texture;
	private final GpuTextureView view;
	private final TextureSetup setup;
	private int shelfX = 0;
	private int shelfY = 0;
	private int shelfHeight = 0;
	private boolean full;

	GlyphAtlas() {
		GpuDevice device = RenderSystem.getDevice();
		this.texture = device.createTexture("Kairo glyph atlas",
			GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.R8_UNORM, SIZE, SIZE, 1, 1);
		this.view = device.createTextureView(texture);
		this.setup = TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
	}

	TextureSetup setup() {
		return setup;
	}

	/**
	 * Uploads a glyph image (already including its border) and returns its top-left as {@code x | y << 16},
	 * or -1 when the atlas is full.
	 */
	int add(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		if (shelfX + w > SIZE) {
			shelfY += shelfHeight;
			shelfX = 0;
			shelfHeight = 0;
		}
		if (shelfY + h > SIZE) {
			full = true;
			return -1;
		}
		int x = shelfX;
		int y = shelfY;
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, 0, 0, x, y);
		shelfX += w;
		shelfHeight = Math.max(shelfHeight, h);
		return x | y << 16;
	}

	boolean isFull() {
		return full;
	}

	void reset() {
		shelfX = 0;
		shelfY = 0;
		shelfHeight = 0;
		full = false;
	}
}
