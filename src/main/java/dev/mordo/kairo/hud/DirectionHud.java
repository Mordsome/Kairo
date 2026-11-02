package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpSettings;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.mods.Waypoints;
import dev.mordo.kairo.mods.waypoints.Waypoint;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** A compass strip (or just the direction you face): N, NE, E... with degree ticks, and your waypoints on it. */
public final class DirectionHud extends HudElement {
	public enum Style implements EnumSetting.Labeled {
		BAR("Compass bar"),
		TEXT("Direction only");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.BAR));
	public final SliderSetting barWidth = add(new SliderSetting("bar_width", "Width", 180, 100, 320, 10).unit("px")
		.visibleWhen(() -> style.get() == Style.BAR));
	public final SliderSetting fov = add(new SliderSetting("fov", "Visible range", 180, 90, 360, 15).unit("°")
		.description("How many degrees of the compass the bar shows")
		.visibleWhen(() -> style.get() == Style.BAR));
	public final BooleanSetting intermediates = add(new BooleanSetting("intermediates", "NE, SE, SW, NW", true));
	public final BooleanSetting degrees = add(new BooleanSetting("degrees", "Show degrees", true));
	public final BooleanSetting showWaypoints = add(new BooleanSetting("waypoints", "Waypoints on the bar", true)
		.visibleWhen(() -> style.get() == Style.BAR));
	public final ColorSetting northColor = add(new ColorSetting("north_color", "North color", 0xFFFF6B6B, false));

	private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	public DirectionHud() {
		super("direction", "Direction HUD", "Compass bar with N, E, S, W", Icon.COMPASS, false, Anchor.TOP_CENTER, 0, 0.012);
	}

	private static float yaw(boolean preview) {
		LocalPlayer player = Minecraft.getInstance().player;
		return preview || player == null ? 200 : Mth.wrapDegrees(player.getYRot());
	}

	/** "N", "NE"... for the direction you face (Minecraft yaw: 0 = south, 90 = west). */
	private String facingName(float yaw) {
		int index = Math.floorMod(Math.round(yaw / 45f), 8);
		String name = NAMES[index];
		if (!intermediates.isOn() && name.length() > 1) {
			name = NAMES[Math.floorMod(Math.round(yaw / 90f) * 2, 8)];
		}
		return name;
	}

	@Override
	protected void layout(boolean preview) {
		if (style.get() == Style.BAR) {
			width = barWidth.floatValue();
			height = barHeight() + (degrees.isOn() ? HudText.LINE + 4 : 0);
		} else {
			String text = text(yaw(preview));
			width = HudText.width(text) + pad() * 2;
			height = HudText.LINE + pad() * 2;
		}
	}

	private float barHeight() {
		return HudText.LINE + pad() * 2 + 6;
	}

	private String text(float yaw) {
		String name = facingName(yaw);
		return degrees.isOn() ? name + "  " + Math.floorMod(Math.round(yaw + 180), 360) + "°" : name;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		float yaw = yaw(preview);
		if (style.get() == Style.TEXT) {
			drawBackground(g);
			String text = text(yaw);
			int color = facingName(yaw).equals("N") ? northColor.argb() : textColor.argb();
			HudText.draw(g, text, pad(), pad(), color, shadow());
			return;
		}
		float range = fov.floatValue();
		float cx = width / 2;
		float bh = barHeight();
		drawBox(g, 0, 0, width, bh, backgroundColor.argb());
		float tickY = bh - pad() - 3;
		g.enableScissor(0, 0, (int) Math.ceil(width), (int) Math.ceil(bh));
		try {
			// Degree ticks every 15°, labels every 45°.
			for (int deg = 0; deg < 360; deg += 15) {
				float rel = Mth.wrapDegrees(deg - yaw);
				if (Math.abs(rel) > range / 2) {
					continue;
				}
				float x = cx + rel / range * width;
				float fade = 1 - Math.abs(rel) / (range / 2);
				fade = Math.min(1, fade * 2.2f);
				if (deg % 45 == 0) {
					String name = NAMES[deg / 45];
					if (name.length() > 1 && !intermediates.isOn()) {
						Shapes.rect(g, x - 0.5f, tickY, 1, 3, 0, Colors.multiplyAlpha(textColor.argb(), 0.6f * fade));
						continue;
					}
					int color = name.equals("N") ? northColor.argb() : name.length() > 1 ? Colors.multiplyAlpha(textColor.argb(), 0.7f) : textColor.argb();
					HudText.drawCentered(g, name, x, pad(), Colors.multiplyAlpha(color, fade), shadow());
				} else {
					Shapes.rect(g, x - 0.5f, tickY + 1, 1, 2, 0, Colors.multiplyAlpha(textColor.argb(), 0.35f * fade));
				}
			}
			if (showWaypoints.isOn() && !preview) {
				drawWaypoints(g, yaw, range, cx, bh);
			}
		} finally {
			g.disableScissor();
		}
		// Centre marker, and the exact heading under it.
		Shapes.triangle(g, cx, bh - 2, 3, (float) (-Math.PI / 2), valueColor.argb());
		if (degrees.isOn()) {
			String deg = (Math.floorMod(Math.round(yaw + 180), 360)) + "°";
			float w = HudText.width(deg) + 6;
			drawBox(g, cx - w / 2, bh + 2, w, HudText.LINE + 2, backgroundColor.argb());
			HudText.drawCentered(g, deg, cx, bh + 3, valueColor.argb(), shadow());
		}
	}

	private void drawWaypoints(GuiGraphicsExtractor g, float yaw, float range, float cx, float bh) {
		Waypoints mod = ModuleManager.get(Waypoints.class);
		LocalPlayer player = Minecraft.getInstance().player;
		if (!mod.isEnabled() || player == null || PvpSettings.active(PvpSettings.INSTANCE.hideWaypoints)) {
			return;
		}
		String dim = Waypoints.dimensionOf(player);
		for (Waypoint w : mod.store().all()) {
			if (!w.visible || !dim.equals(w.dimension)) {
				continue;
			}
			double dx = w.x + 0.5 - player.getX();
			double dz = w.z + 0.5 - player.getZ();
			float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float rel = Mth.wrapDegrees(bearing - yaw);
			if (Math.abs(rel) > range / 2) {
				continue;
			}
			float x = cx + rel / range * width;
			Shapes.circle(g, x, bh - pad() - 1.5f, 2, w.color | 0xFF000000);
		}
	}
}
