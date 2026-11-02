package dev.mordo.kairo.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers end crystals leaving the client world, so the crystal pop knows an explosion was a crystal. */
@Mixin(Entity.class)
abstract class EntityRemovalMixin {
	@Inject(method = "onClientRemoval", at = @At("HEAD"))
	private void kairo$crystalGone(CallbackInfo ci) {
		dev.mordo.kairo.render.CrystalFx.removed((Entity) (Object) this);
		dev.mordo.kairo.render.ProjectileTrails.removed((Entity) (Object) this);
	}
}
