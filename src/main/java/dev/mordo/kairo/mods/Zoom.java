package dev.mordo.kairo.mods;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import net.minecraft.client.Minecraft;

/**
 * Hold to zoom, scroll to zoom further, with smooth easing and scaled mouse sensitivity.
 * Purely a camera FOV change on your own screen.
 */
public final class Zoom extends Module {
	private static final float MIN_ZOOM = 1.5f;
	private static final float MAX_ZOOM = 50f;

	public final KeybindSetting key = add(new KeybindSetting("key", "Zoom key", Keybind.keyboard(InputConstants.KEY_C)));
	public final SliderSetting level = add(new SliderSetting("level", "Default zoom", 4, 1.5, 10, 0.5).unit("x"));
	public final BooleanSetting scrollZoom = add(new BooleanSetting("scroll", "Scroll to zoom", true)
		.description("Scroll while zooming; the hotbar slot stays put"));
	public final SliderSetting smoothness = add(new SliderSetting("smoothness", "Smoothness", 0.6, 0.0, 1.0, 0.05).percent());
	public final BooleanSetting hideHand = add(new BooleanSetting("hide_hand", "Hide hand while zoomed", true));
	public final BooleanSetting cinematic = add(new BooleanSetting("cinematic", "Cinematic camera", false)
		.description("Smooth camera movement while zoomed"));

	private float current = 1;
	private float scrollLevel = 4;
	private boolean held;
	private long lastNanos;
	private boolean forcedSmoothCamera;

	public Zoom() {
		super("zoom", "Zoom", "Smooth, scroll to adjust", Category.VISUAL, Icon.SEARCH, true);
	}

	private boolean keyHeld() {
		Minecraft mc = Minecraft.getInstance();
		return isEnabled() && mc.player != null && mc.gui.screen() == null && key.isDown();
	}

	/** Called every frame from the camera; returns what to multiply the FOV by. */
	public float fovMultiplier() {
		boolean now = keyHeld();
		if (now && !held) {
			scrollLevel = level.floatValue();
		}
		held = now;
		float target = held ? scrollLevel : 1;

		long time = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (time - lastNanos) / 1e9f);
		lastNanos = time;
		float smooth = smoothness.floatValue();
		if (smooth <= 0.001f) {
			current = target;
		} else {
			// Exponential approach: frame-rate independent, eases in and out naturally.
			float speed = 4 + (1 - smooth) * 26;
			current += (target - current) * (1 - (float) Math.exp(-speed * dt));
			if (Math.abs(target - current) < 0.001f) {
				current = target;
			}
		}
		updateCinematic();
		return 1 / current;
	}

	private void updateCinematic() {
		Minecraft mc = Minecraft.getInstance();
		boolean want = held && cinematic.isOn();
		if (want && !forcedSmoothCamera && !mc.options.smoothCamera) {
			mc.options.smoothCamera = true;
			forcedSmoothCamera = true;
		} else if (!want && forcedSmoothCamera) {
			mc.options.smoothCamera = false;
			forcedSmoothCamera = false;
		}
	}

	/** Mouse sensitivity factor: slower turning the further you zoom. */
	public double sensitivityFactor() {
		return 1.0 / current;
	}

	/** Scroll while zooming changes the zoom instead of the hotbar slot. Returns true if it used the scroll. */
	public boolean onScroll(double amount) {
		if (!held || !scrollZoom.isOn() || amount == 0) {
			return false;
		}
		float step = amount > 0 ? 1.2f : 1 / 1.2f;
		scrollLevel = Math.clamp(scrollLevel * step, MIN_ZOOM, MAX_ZOOM);
		return true;
	}

	public boolean shouldHideHand() {
		return hideHand.isOn() && current > 1.05f;
	}

	@Override
	protected void onDisable() {
		held = false;
		current = 1;
		updateCinematic();
	}
}
