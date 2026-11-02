package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.Fullbright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fullbright: replaces only the gamma (brightness option) value the lightmap is built from. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
	@ModifyVariable(method = "extract", at = @At("STORE"), name = "brightnessOption")
	private float kairo$fullbrightGamma(float gamma) {
		return ModuleManager.get(Fullbright.class).gamma(gamma);
	}
}
