package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * One tile in Kairo Void's Visuals grid. Every tile has the same shape: a rounded picture on top (a little scene for
 * atmospheres, crystals and presets, otherwise the visual's icon on a soft glow), the name and one line under it, and a
 * switch. Clicking the tile switches it; the gear opens its options. Presets have no switch: clicking applies them.
 */
public final class VisualCard extends Widget {
	/** Corner radius of the pictures (the tile itself uses {@link #RADIUS}). */
	public static final float ART_RADIUS = 10;
	private static final float RADIUS = 16;
	private static final float TEXT_H = 54;

	/** A picture filling the top of the tile. Use {@link #clip} for anything that reaches the corners. */
	@FunctionalInterface
	public interface Art {
		void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha);
	}

	private final String name;
	private final String description;
	private final Icon icon;
	private final Binding<Boolean> value;
	private final boolean hasSettings;
	private final ToggleSwitch toggle;
	private final Runnable openSettings;
	public final Animation appear = new Animation(0);
	private final Animation on;
	private final Animation gearHover = new Animation(0);
	private final float phase;
	private @Nullable Art art;
	private boolean pickOnly;
	private java.util.function.BooleanSupplier favourite = () -> false;
	private @Nullable Runnable toggleFavourite;

	public VisualCard(String name, String description, Icon icon, Binding<Boolean> value, boolean hasSettings, Runnable openSettings) {
		this.name = name;
		this.description = description;
		this.icon = icon;
		this.value = value;
		this.hasSettings = hasSettings;
		this.openSettings = openSettings;
		this.toggle = new ToggleSwitch(value);
		this.on = new Animation(value.get() ? 1 : 0);
		this.phase = (name.hashCode() & 0xFFFF) / 65535f;
	}

	public VisualCard art(Art art) {
		this.art = art;
		return this;
	}

	/** Right-click stars or unstars it; a starred card shows a heart in its corner. */
	public VisualCard favourite(java.util.function.BooleanSupplier starred, Runnable toggle) {
		this.favourite = starred;
		this.toggleFavourite = toggle;
		return this;
	}

	public VisualCard pickOnly() {
		this.pickOnly = true;
		return this;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	/**
	 * Draws {@code content} clipped to a rounded rectangle: in three strips that don't overlap (the middle column, then
	 * the left and right edges between the corners), so nothing spills past the rounded corners. Fill the corners
	 * themselves with a rounded shape first.
	 */
	public static void clip(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Runnable content) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int x1 = (int) Math.ceil(x + w);
		int y1 = (int) Math.ceil(y + h);
		int ri = Math.round(r);
		int[][] strips = {{x0 + ri, y0, x1 - ri, y1}, {x0, y0 + ri, x0 + ri, y1 - ri}, {x1 - ri, y0 + ri, x1, y1 - ri}};
		for (int[] s : strips) {
			if (s[2] <= s[0] || s[3] <= s[1]) {
				continue;
			}
			g.enableScissor(s[0], s[1], s[2], s[3]);
			try {
				content.run();
			} finally {
				g.disableScissor();
			}
		}
	}

	private float artH() {
		return h - TEXT_H - 8;
	}

	private float gearX() {
		return x + w - 14 - (pickOnly ? 0 : ToggleSwitch.WIDTH + 8) - 24;
	}

	private float gearY() {
		return y + h - TEXT_H + 6;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float a = appear.value() * fade;
		if (a <= 0.002f) {
			return;
		}
		on.animateTo(value.get() ? 1 : 0, 260);
		float t = on.value();
		float hv = hover.value();
		float lift = Animation.motion(hv * 3 + (1 - appear.value()) * -12, 0);
		float cy = y - lift;
		int accent = Theme.accent() | 0xFF000000;
		double time = System.nanoTime() / 1e9;

		if (t > 0.01f) {
			float breathe = 0.75f + 0.25f * (float) Math.sin(time * 1.6 + phase * 6.28);
			Shapes.shadow(g, x, cy + 2, w, h, RADIUS, 16, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.16f * t * breathe), a));
		}
		if (hv > 0.01f) {
			Shapes.shadow(g, x, cy + 8, w, h, RADIUS, 16, Colors.argb(0.3f * hv * a, 0));
		}
		int fill = Colors.lerp(Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hv), Colors.withAlpha(accent, 0.07f + 0.04f * hv), t);
		int stroke = Colors.lerp(Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hv), Colors.withAlpha(accent, 0.45f + 0.3f * hv), t);
		Shapes.rect(g, x, cy, w, h, RADIUS, Colors.multiplyAlpha(fill, a), Colors.multiplyAlpha(stroke, a), 1);

		// The picture.
		float ax = x + 7;
		float ay = cy + 7;
		float aw = w - 14;
		float ah = artH();
		if (art != null) {
			art.draw(g, ax, ay, aw, ah, a);
		} else {
			iconArt(g, ax, ay, aw, ah, a, t, accent, time);
		}
		Shapes.rect(g, ax, ay, aw, ah, ART_RADIUS, 0, Colors.multiplyAlpha(Colors.lerp(0x24FFFFFF, Colors.withAlpha(accent, 0.85f), t), a), 1 + 0.5f * t);
		if (pickOnly && t > 0.01f) {
			Shapes.shadow(g, ax + aw - 26, ay + 6, 20, 20, 10, 6, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.7f * t), a));
			Shapes.circle(g, ax + aw - 16, ay + 16, 9 * t, Colors.multiplyAlpha(accent, a));
			IconRenderer.draw(g, Icon.CHECK, ax + aw - 16, ay + 16, 11 * t, Colors.multiplyAlpha(Theme.DARK_TEXT, a), 3f);
		}

		if (favourite.getAsBoolean()) {
			Shapes.circle(g, ax + 16, ay + 16, 9, Colors.multiplyAlpha(0x99000000, a));
			IconRenderer.draw(g, Icon.HEART, ax + 16, ay + 16.5f, 10, Colors.multiplyAlpha(Colors.lerp(accent, 0xFFFFFFFF, 0.35f), a), 2f);
		}

		// Name, one line, switch and gear.
		float textY = cy + h - TEXT_H + 18;
		float maxName = w - 28 - (pickOnly ? 0 : ToggleSwitch.WIDTH + 8) - (hasSettings ? 30 : 0);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, name, 13.5f, maxName), x + 14, textY, 13.5f, Theme.text(1, a));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, description, 10.5f, w - 28), x + 14, textY + 19, 10.5f,
			Theme.text(0.55f, a));
		if (!pickOnly) {
			toggle.bounds(x + w - 14 - ToggleSwitch.WIDTH, textY - 10, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
			toggle.render(g, mx, my, a, hv > 0);
		}
		if (hasSettings) {
			boolean overGear = mx >= gearX() && mx <= gearX() + 24 && my >= gearY() && my <= gearY() + 24;
			gearHover.animateTo(overGear ? 1 : 0, 140);
			float gh = gearHover.value();
			if (gh > 0.01f) {
				Shapes.circle(g, gearX() + 12, gearY() - lift + 12, 12, Colors.argb(0.08f * gh * a, 0xFFFFFF));
			}
			IconRenderer.draw(g, Icon.GEAR, gearX() + 12, gearY() - lift + 12, 14, Theme.text(0.45f + 0.5f * Math.max(gh, hv * 0.4f), a), 2f);
		}
	}

	/** The picture for visuals without their own: the icon on a soft glow, a slow ring round it when switched on. */
	private void iconArt(GuiGraphicsExtractor g, float x, float y, float w, float h, float a, float t, int accent, double time) {
		int top = Colors.lerp(0xFF16161D, Colors.lerp(accent, 0xFF0E0E14, 0.72f), t);
		int bottom = Colors.lerp(0xFF0F0F14, Colors.lerp(accent, 0xFF08080C, 0.86f), t);
		Shapes.gradientRect(g, x, y, w, h, ART_RADIUS, Colors.multiplyAlpha(top, a), Colors.multiplyAlpha(bottom, a));
		float cx = x + w / 2;
		float cy = y + h / 2;
		float r = Math.min(w, h) * 0.26f;
		Shapes.shadow(g, cx - r, cy - r, r * 2, r * 2, r, r * 0.9f, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.12f + 0.38f * t), a));
		if (t > 0.01f) {
			float spin = (float) (time * 0.8 + phase * 6.28);
			Shapes.arc(g, cx, cy, r * 1.25f, 1.6f, spin, 1.9f, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.8f * t), a));
			Shapes.arc(g, cx, cy, r * 1.25f, 1.2f, spin + (float) Math.PI, 1.1f, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.45f * t), a));
		}
		float bob = (float) Math.sin(time * 2.2 + phase * 6.28) * 1.5f * t;
		IconRenderer.draw(g, icon, cx, cy + bob, r * 1.15f, Colors.multiplyAlpha(Colors.lerp(Theme.text(0.6f), 0xFFFFFFFF, t), a), 1.9f);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && toggleFavourite != null) {
			toggleFavourite.run();
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (pickOnly) {
			value.set(true);
			dev.mordo.kairo.sound.UiSounds.click();
			return true;
		}
		if (toggle.contains(mx, my)) {
			return toggle.mouseClicked(mx, my, button);
		}
		boolean onGear = hasSettings && mx >= gearX() - 4 && mx <= gearX() + 28 && my >= gearY() - 4 && my <= gearY() + 28;
		if (onGear) {
			openSettings.run();
			dev.mordo.kairo.sound.UiSounds.click();
			return true;
		}
		// Anywhere else switches it.
		toggle.mouseClicked(toggle.x + 1, toggle.y + 1, button);
		return true;
	}
}
