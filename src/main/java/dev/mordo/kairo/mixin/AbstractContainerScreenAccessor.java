package dev.mordo.kairo.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int kairo$leftPos();

	@Accessor("topPos")
	int kairo$topPos();

	@Accessor("imageWidth")
	int kairo$imageWidth();

	@Accessor("imageHeight")
	int kairo$imageHeight();
}
