package dev.mordo.kairo.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.mixin.OverlayTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Kairo Void "Hit colour": repaints the red half of the entity overlay texture (what tints mobs and players when
 * they're hurt) in a colour you pick. Only repainted when the colour changes.
 */
public final class HitTint {
	private static final int VANILLA = 0xB3FF0000;
	private static int painted = VANILLA;

	private HitTint() {
	}

	public static void tick(Minecraft mc) {
		VoidSettings s = VoidSettings.INSTANCE;
		int wanted = Visuals.on() && s.hitColor.isOn() ? (0xB3000000 | (s.hitColorValue.argb() & 0xFFFFFF)) : VANILLA;
		if (wanted == painted) {
			return;
		}
		try {
			DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).kairo$texture();
			NativeImage pixels = texture.getPixels();
			if (pixels == null) {
				return;
			}
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 16; x++) {
					pixels.setPixel(x, y, wanted);
				}
			}
			texture.upload();
			painted = wanted;
		} catch (RuntimeException e) {
			painted = wanted; // don't retry every tick
		}
	}
}
