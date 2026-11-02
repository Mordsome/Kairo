package dev.mordo.kairo.mixin;

import dev.mordo.kairo.mods.totem.TotemPack;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

/** Adds the client's own resource pack (custom totem texture) to the game's resource pack sources. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"))
	private RepositorySource[] kairo$addClientPack(RepositorySource[] sources) {
		RepositorySource[] all = Arrays.copyOf(sources, sources.length + 1);
		all[sources.length] = TotemPack.SOURCE;
		return all;
	}

	@org.spongepowered.asm.mixin.Shadow
	private int missTime;

	/** Kairo Void: an energy slash when you swing at the air or at something (not when you start mining). */
	@org.spongepowered.asm.mixin.injection.Inject(method = "startAttack", at = @At("HEAD"))
	private void kairo$slash(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (missTime <= 0) {
			dev.mordo.kairo.render.VoidFx.onAttackKey((Minecraft) (Object) this);
		}
	}
}
