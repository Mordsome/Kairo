package dev.mordo.kairo.test;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.hud.HudElement;
import dev.mordo.kairo.hud.HudManager;
import dev.mordo.kairo.hud.HudProbeAccess;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Looks for HUD elements that change size or place from one moment to the next while nothing is happening (the
 * "growing and shrinking" flicker): at the HUD's default size, in game and in the layout editor. Run with
 * build/tour-hudprobe.
 */
final class HudFlickerProbe {
	void run(ClientGameTestContext ctx) {
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> ClientSettings.INSTANCE.onboarded.set(true));
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			world.getServer().runCommand("tp @p 0 -60 0 0 20");
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> {
				for (String id : new String[]{"keystrokes", "cps", "fps", "coordinates", "ping", "armor_status"}) {
					var m = ModuleManager.get(id);
					if (m != null && !m.isEnabled()) {
						m.toggle();
					}
				}
				ModuleManager.syncAll();
				ClientSettings.INSTANCE.hudScale.set(1.3);
			});
			ctx.waitTicks(10);
			sample(ctx, "in game");
			ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.layout.LayoutScreen()));
			ctx.waitTicks(30);
			sample(ctx, "layout editor");
			drag(ctx);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
			sliderDrag(ctx);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(5);
	}

	/**
	 * Drags Keystrokes' resize corner slowly outwards, then back, like a player, and logs its size every step: the size
	 * should only ever go one way while the mouse goes one way.
	 */
	private void drag(ClientGameTestContext ctx) {
		var input = ctx.getInput();
		HudElement keys = ModuleManager.get(dev.mordo.kairo.hud.KeystrokesHud.class);
		double gui = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		float[] box = ctx.computeOnClient(mc -> new float[]{HudProbeAccess.lastX(keys), HudProbeAccess.lastY(keys), keys.width() * keys.scaleValue(),
			keys.height() * keys.scaleValue()});
		// The grip: just inside the bottom-right corner (GUI pixels -> window pixels).
		double gx = (box[0] + box[2] - 2) * gui;
		double gy = (box[1] + box[3] - 2) * gui;
		input.setCursorPos(gx, gy);
		ctx.waitTicks(2);
		input.holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		StringBuilder sizes = new StringBuilder();
		int reversals = 0;
		float previous = -1;
		float direction = 0;
		for (int i = 0; i < 40; i++) {
			double step = i < 20 ? 3 : -3;
			input.moveCursor(step * gui, step * gui * box[3] / Math.max(1, box[2]));
			ctx.waitTicks(1);
			float now = ctx.computeOnClient(mc -> keys.scaleValue());
			sizes.append(String.format(java.util.Locale.ROOT, "%.2f ", now));
			if (previous >= 0 && Math.abs(now - previous) > 1e-4) {
				float dir = Math.signum(now - previous);
				boolean mouseOut = i < 20;
				if ((mouseOut && dir < 0) || (!mouseOut && dir > 0)) {
					reversals++;
				}
				direction = dir;
			}
			previous = now;
		}
		input.releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		Kairo.LOGGER.info("PROBE corner drag sizes: {}", sizes);
		Kairo.LOGGER.info("PROBE corner drag went against the mouse {} times", reversals);
	}

	/**
	 * Opens Visuals, finds the HUD size slider and drags it the way the mouse would (press, then a move every frame),
	 * logging what the HUD does and taking a picture of every frame.
	 */
	private void sliderDrag(ClientGameTestContext ctx) {
		// The player's setup: a big window, GUI Scale 3 (below the biggest), Menu size 130%.
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
			ClientSettings.INSTANCE.menuScale.set(1.3);
		});
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visualsPage, null)));
		ctx.waitTicks(30);
		dragSlider(ctx, ClientSettings.INSTANCE.hudScale, 1.0, 1, "HUD size");
		dragSlider(ctx, ClientSettings.INSTANCE.menuScale, 1.0, -1, "Menu size");
	}

	/**
	 * Finds the slider for {@code setting}, puts it at {@code from}, then drags its knob with the real mouse one pixel a
	 * tick in {@code dir}: logs the setting, the HUD, the menu size and whether the drag is seen, then the same after
	 * letting go.
	 */
	private void dragSlider(ClientGameTestContext ctx, dev.mordo.kairo.core.settings.SliderSetting setting, double from, int dir, String name) {
		ctx.runOnClient(mc -> setting.set(from));
		ctx.waitTicks(5);
		dev.mordo.kairo.gui.widgets.Slider[] found = new dev.mordo.kairo.gui.widgets.Slider[1];
		for (int section = 0; section < 12 && found[0] == null; section++) {
			int at = section;
			ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).visuals().select(at));
			ctx.waitTicks(20);
			found[0] = ctx.computeOnClient(mc -> findSlider((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen(), setting));
		}
		if (found[0] == null) {
			Kairo.LOGGER.info("PROBE {}: slider not found", name);
			return;
		}
		var slider = found[0];
		HudElement keys = ModuleManager.get(dev.mordo.kairo.hud.KeystrokesHud.class);
		float[] track = ctx.computeOnClient(mc -> new float[]{slider.x + 8, slider.x + slider.w - 52 - 8, slider.y + slider.h / 2});
		double unit = ctx.computeOnClient(mc -> (double) ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).unit() * mc.getWindow().getGuiScale());
		double progress = (from - setting.min()) / (setting.max() - setting.min());
		var input = ctx.getInput();
		input.setCursorPos((track[0] + (track[1] - track[0]) * progress) * unit, track[2] * unit);
		ctx.waitTicks(2);
		input.holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < 30; i++) {
			input.moveCursor(dir, 0);
			ctx.waitTicks(1);
			log.append(state(ctx, keys, setting, String.valueOf(i)));
		}
		input.releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		log.append(state(ctx, keys, setting, "released"));
		Kairo.LOGGER.info("PROBE {} drag: {}", name, log);
	}

	private static String state(ClientGameTestContext ctx, HudElement keys, dev.mordo.kairo.core.settings.SliderSetting setting, String at) {
		return ctx.computeOnClient(mc -> String.format(java.util.Locale.ROOT, "[%s set=%.2f hud=%.3f menu=%.3f unit=%.4f drag=%b] ", at,
			setting.value(), HudProbeAccess.lastScale(keys), dev.mordo.kairo.core.UiScaling.menus(),
			((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).unit(), dev.mordo.kairo.gui.widgets.Slider.anyDragging()));
	}

	private static dev.mordo.kairo.gui.widgets.Slider findSlider(dev.mordo.kairo.gui.menu.MenuScreen menu, Object setting) {
		try {
			var prev = dev.mordo.kairo.gui.widgets.WidgetHost.class.getDeclaredField("previous");
			prev.setAccessible(true);
			var meta = dev.mordo.kairo.gui.widgets.Slider.class.getDeclaredField("meta");
			meta.setAccessible(true);
			for (Object w : (java.util.List<?>) prev.get(menu.host())) {
				if (w instanceof dev.mordo.kairo.gui.widgets.Slider sl && meta.get(sl) == setting) {
					return sl;
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	/** 60 ticks: what each shown element's last drawn place and size were, and whether they changed. */
	private void sample(ClientGameTestContext ctx, String where) {
		Map<String, Set<String>> seen = new HashMap<>();
		for (int i = 0; i < 60; i++) {
			ctx.runOnClient(mc -> {
				for (HudElement e : HudManager.elements()) {
					if (!HudManager.isShown(e)) {
						continue;
					}
					String state = String.format(java.util.Locale.ROOT, "w=%.2f h=%.2f scale=%.3f x=%.1f y=%.1f", e.width(), e.height(), e.scaleValue(),
						HudProbeAccess.lastX(e), HudProbeAccess.lastY(e));
					seen.computeIfAbsent(e.id(), k -> new HashSet<>()).add(state);
				}
			});
			ctx.waitTicks(1);
		}
		for (Map.Entry<String, Set<String>> entry : seen.entrySet()) {
			Kairo.LOGGER.info("PROBE {} {}: {} different states {}", where, entry.getKey(), entry.getValue().size(),
				entry.getValue().size() > 1 ? entry.getValue() : "");
		}
	}
}
