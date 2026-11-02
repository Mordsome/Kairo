package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.core.settings.TextSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Previewable;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.CustomWidgets;
import dev.mordo.kairo.gui.widgets.PixelGrid;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.UiScale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

/** Crosshair editor: presets or a custom pixel grid, plus attack cooldown styles. Replaces only how it looks. */
public final class Crosshair extends Module implements Previewable {
	public enum Preset implements EnumSetting.Labeled {
		CROSS("Cross"),
		DOT("Dot"),
		CIRCLE("Circle"),
		T_SHAPE("T-shape"),
		CUSTOM("Custom grid");

		private final String label;

		Preset(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum CooldownStyle implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		BAR("Bar under"),
		RING("Ring"),
		HIDDEN("Hidden");

		private final String label;

		CooldownStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public static final int GRID = 15;
	private static final Identifier INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Shape", Preset.CROSS));
	public final TextSetting customPixels = add(new TextSetting("custom_pixels", "Pixel grid", defaultGrid(), GRID * GRID)
		.description("Left-drag paints, right-drag erases")
		.visibleWhen(() -> preset.get() == Preset.CUSTOM));
	public final SliderSetting size = add(new SliderSetting("size", "Size", 4, 1, 20, 0.5).unit("px")
		.visibleWhen(() -> preset.get() != Preset.CUSTOM));
	public final SliderSetting gap = add(new SliderSetting("gap", "Gap", 2, 0, 10, 0.5).unit("px")
		.visibleWhen(() -> preset.get() != Preset.CUSTOM && preset.get() != Preset.DOT));
	public final SliderSetting thickness = add(new SliderSetting("thickness", "Thickness", 1, 0.5, 5, 0.5).unit("px"));
	public final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFFFF).accentable());
	public final BooleanSetting outline = add(new BooleanSetting("outline", "Outline", true));
	public final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Outline color", 0xB3000000).visibleWhen(outline::isOn));
	public final BooleanSetting centerDot = add(new BooleanSetting("center_dot", "Center dot", false)
		.visibleWhen(() -> preset.get() != Preset.CUSTOM && preset.get() != Preset.DOT));
	public final EnumSetting<CooldownStyle> cooldown = add(new EnumSetting<>("cooldown", "Attack cooldown", CooldownStyle.VANILLA)
		.description("Vanilla follows the game's Attack Indicator option"));

	public Crosshair() {
		super("crosshair", "Crosshair", "Custom shape, color and gap", Category.VISUAL, Icon.CROSSHAIR, true);
		CustomWidgets.register(customPixels, host -> new PixelGrid(customPixels, GRID));
	}

	private static String defaultGrid() {
		StringBuilder sb = new StringBuilder();
		for (int row = 0; row < GRID; row++) {
			for (int col = 0; col < GRID; col++) {
				boolean arm = (row == 7 && (col <= 4 || col >= 10)) || (col == 7 && (row <= 4 || row >= 10));
				sb.append(arm ? '1' : '0');
			}
		}
		return sb.toString();
	}

	@Override
	public void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			if (shouldReplace()) {
				render(g);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
	}

	/** Same situations vanilla draws a crosshair in; spectator and debug crosshairs stay vanilla. */
	private boolean shouldReplace() {
		Minecraft mc = Minecraft.getInstance();
		return isEnabled() && mc.player != null && mc.options.getCameraType().isFirstPerson()
			&& mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
	}

	private void render(GuiGraphicsExtractor g) {
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		// Snap the centre to a physical pixel so thin lines stay sharp.
		float px = 1f / Minecraft.getInstance().getWindow().getGuiScale();
		cx = Math.round(cx / px) * px;
		cy = Math.round(cy / px) * px;
		drawShape(g, cx, cy);
		drawCooldown(g, cx, cy);
	}

	/** Draws the crosshair centred on (cx, cy) in the current GUI units. */
	public void drawShape(GuiGraphicsExtractor g, float cx, float cy) {
		if (outline.isOn()) {
			drawParts(g, cx, cy, outlineColor.argb(), Math.max(0.5f, 1f / UiScale.current() * 2));
		}
		drawParts(g, cx, cy, color.argb(), 0);
	}

	/** {@code grow} > 0 draws the same shape fatter (the outline pass). */
	private void drawParts(GuiGraphicsExtractor g, float cx, float cy, int argb, float grow) {
		float t = thickness.floatValue() + grow * 2;
		float s = size.floatValue();
		float gp = gap.floatValue();
		switch (preset.get()) {
			case CROSS, T_SHAPE -> {
				boolean top = preset.get() == Preset.CROSS;
				arm(g, cx - gp - s - grow, cy - t / 2, s + grow * 2, t, argb);
				arm(g, cx + gp - grow, cy - t / 2, s + grow * 2, t, argb);
				if (top) {
					arm(g, cx - t / 2, cy - gp - s - grow, t, s + grow * 2, argb);
				}
				arm(g, cx - t / 2, cy + gp - grow, t, s + grow * 2, argb);
				if (centerDot.isOn()) {
					Shapes.rect(g, cx - t / 2, cy - t / 2, t, t, 0, argb);
				}
			}
			case DOT -> Shapes.circle(g, cx, cy, Math.max(t, s * 0.35f) / 2 + grow, argb);
			case CIRCLE -> {
				Shapes.ring(g, cx, cy, gp + s / 2, t, argb);
				if (centerDot.isOn()) {
					Shapes.circle(g, cx, cy, t / 2 + grow, argb);
				}
			}
			case CUSTOM -> {
				String bits = customPixels.get();
				float cell = thickness.floatValue();
				float origin = -GRID * cell / 2;
				for (int i = 0; i < GRID * GRID && i < bits.length(); i++) {
					if (bits.charAt(i) == '1') {
						float x = cx + origin + (i % GRID) * cell;
						float y = cy + origin + (i / GRID) * cell;
						Shapes.rect(g, x - grow, y - grow, cell + grow * 2, cell + grow * 2, 0, argb);
					}
				}
			}
		}
	}

	private static void arm(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
		Shapes.rect(g, x, y, w, h, 0, argb);
	}

	private void drawCooldown(GuiGraphicsExtractor g, float cx, float cy) {
		Minecraft mc = Minecraft.getInstance();
		float strength = mc.player.getAttackStrengthScale(0.0F);
		switch (cooldown.get()) {
			case HIDDEN -> {
			}
			case VANILLA -> {
				if (mc.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) {
					return;
				}
				boolean full = strength >= 1 && mc.crosshairPickEntity instanceof LivingEntity target && target.isAlive()
					&& mc.player.getCurrentItemAttackStrengthDelay() > 5.0F;
				int y = (int) cy - 7 + 16;
				int x = (int) cx - 8;
				if (full) {
					g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_FULL, x, y, 16, 16);
				} else if (strength < 1) {
					g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_BACKGROUND, x, y, 16, 4);
					g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (strength * 17), 4);
				}
			}
			case BAR -> {
				if (strength < 1) {
					float w = 16;
					float y = cy + 10;
					Shapes.rect(g, cx - w / 2, y, w, 2, 1, 0x66000000);
					Shapes.rect(g, cx - w / 2, y, w * strength, 2, 1, Colors.withAlpha(Theme.accent(), 0.9f));
				}
			}
			case RING -> {
				if (strength < 1) {
					float r = gap.floatValue() + size.floatValue() + 4;
					Shapes.ring(g, cx, cy, r, 1.5f, 0x40000000);
					Shapes.arc(g, cx, cy, r, 1.5f, (float) (-Math.PI / 2), (float) (Math.PI * 2 * strength), Colors.withAlpha(Theme.accent(), 0.9f));
				}
			}
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, float cx, float cy, float maxW, float maxH, float unit) {
		// Shown at 4x its real size so the shape is easy to judge.
		float zoom = 4;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(zoom / unit, zoom / unit);
		UiScale.push(Minecraft.getInstance().getWindow().getGuiScale() * zoom);
		try {
			drawShape(g, 0, 0);
		} finally {
			UiScale.pop();
			g.pose().popMatrix();
		}
	}
}
