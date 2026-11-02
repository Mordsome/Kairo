package dev.mordo.kairo.mixin.restyle;

import dev.mordo.kairo.gui.ButtonFx;
import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla buttons (and cycle buttons) get the client's dark glass look, a fade-in when the menu opens, a smooth
 * hover and the chosen click effect (Visuals → Buttons). The label is still drawn by vanilla.
 */
@Mixin(AbstractButton.class)
abstract class AbstractButtonMixin {
	@Unique
	private float kairo$alpha = -1;
	@Unique
	private boolean kairo$pushed;

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void kairo$button(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!VanillaStyle.enabled()) {
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaStyle.button(g, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.active, self.isHoveredOrFocused(),
			self.getAlpha(), ButtonFx.state(this));
		ci.cancel();
	}

	/** Intro fade and slide, and the Pop click effect, around the whole button (label included). */
	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
	private void kairo$animateIn(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		kairo$pushed = false;
		if (!VanillaStyle.enabled()) {
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		ButtonFx.State fx = ButtonFx.state(this);
		int screenH = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
		// Inside inventories (recipe book, its tabs) buttons just appear: they'd flicker every time it opens.
		boolean container = Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
		float intro = container ? 1 : ButtonFx.intro(fx, self.getY() / (float) screenH);
		float pop = ButtonFx.popScale(fx);
		if (intro >= 1 && pop == 1) {
			return;
		}
		kairo$alpha = self.getAlpha();
		self.setAlpha(kairo$alpha * intro);
		float cx = self.getX() + self.getWidth() / 2f;
		float cy = self.getY() + self.getHeight() / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + (1 - intro) * 5);
		g.pose().scale(pop, pop);
		g.pose().translate(-cx, -cy);
		kairo$pushed = true;
	}

	@Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
	private void kairo$animateInEnd(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (kairo$pushed) {
			g.pose().popMatrix();
			((AbstractWidget) (Object) this).setAlpha(kairo$alpha);
			kairo$pushed = false;
		}
	}

	@Inject(method = "onClick", at = @At("HEAD"))
	private void kairo$clickEffect(MouseButtonEvent event, boolean doubleClick, CallbackInfo ci) {
		if (VanillaStyle.enabled() && ((AbstractWidget) (Object) this).active) {
			ButtonFx.click(ButtonFx.state(this), (float) event.x(), (float) event.y());
		}
	}
}
