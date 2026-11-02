package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Base for HUD mods. Position is an anchor plus an offset stored as a fraction of the screen size,
 * so layouts survive resolution and GUI scale changes. The default style matches a classic PvP HUD:
 * black box at 45%, vanilla-style text shadow, white labels, coloured values.
 *
 * <p>Each frame {@link HudManager} calls {@link #layout} (set {@link #width}/{@link #height}) and then
 * {@link #draw} with the origin at the element's top-left, in HUD units (GUI pixels before scaling).
 */
public abstract class HudElement extends Module {
	public final EnumSetting<Anchor> anchor;
	public final SliderSetting offsetX;
	public final SliderSetting offsetY;

	public final SliderSetting scale = add(new SliderSetting("scale", "Scale", 1.0, 0.5, 2.5, 0.05).multiplier());
	public final BooleanSetting background = add(new BooleanSetting("background", "Background", true));
	public final ColorSetting backgroundColor = add(new ColorSetting("background_color", "Background color", 0x73000000)
		.visibleWhen(background::isOn));
	public final SliderSetting cornerRadius = add(new SliderSetting("corner_radius", "Corner radius", 0, 0, 8, 0.5).unit("px")
		.visibleWhen(background::isOn));
	public final ColorSetting textColor = add(new ColorSetting("text_color", "Label color", 0xFFFFFFFF));
	public final ColorSetting valueColor = add(new ColorSetting("value_color", "Value color", 0xFF7FD8FF).followsAccent());
	public final BooleanSetting textShadow = add(new BooleanSetting("text_shadow", "Text shadow", true));
	public final SliderSetting padding = add(new SliderSetting("padding", "Padding", 3, 0, 10, 1).unit("px"));

	/** Size of the content box this frame, in HUD units (before scale). */
	protected float width;
	protected float height;

	protected HudElement(String id, String name, String description, Icon icon, boolean enabledByDefault,
						 Anchor defaultAnchor, double defaultOffsetX, double defaultOffsetY) {
		this(id, name, description, Category.HUD, icon, enabledByDefault, defaultAnchor, defaultOffsetX, defaultOffsetY);
	}

	protected HudElement(String id, String name, String description, Category category, Icon icon, boolean enabledByDefault,
						 Anchor defaultAnchor, double defaultOffsetX, double defaultOffsetY) {
		super(id, name, description, category, icon, enabledByDefault);
		this.anchor = add(new EnumSetting<>("anchor", "Anchor", defaultAnchor).hidden());
		this.offsetX = add(new SliderSetting("offset_x", "Offset X", defaultOffsetX, -1, 1, 0).hidden());
		this.offsetY = add(new SliderSetting("offset_y", "Offset Y", defaultOffsetY, -1, 1, 0).hidden());
	}

	/** Work out {@link #width} and {@link #height} for this frame. {@code preview}: no world, show sample data. */
	protected abstract void layout(boolean preview);

	/** Draw at the origin, inside width x height. */
	protected abstract void draw(GuiGraphicsExtractor g, boolean preview);

	// ---- geometry ----

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public float scaleValue() {
		return scale.floatValue();
	}

	/** Top-left x on a screen {@code guiWidth} GUI pixels wide. */
	public float screenX(float guiWidth) {
		Anchor a = anchor.get();
		return a.fx * guiWidth + offsetX.floatValue() * guiWidth - a.fx * width * scaleValue();
	}

	public float screenY(float guiHeight) {
		Anchor a = anchor.get();
		return a.fy * guiHeight + offsetY.floatValue() * guiHeight - a.fy * height * scaleValue();
	}

	/**
	 * Stores a new top-left position for the mode the player is in (in PvP mode it becomes the element's
	 * PvP position). The anchor is picked from the third of the screen the element's centre is in, so
	 * resizing the window keeps elements near the edge they were placed against.
	 */
	public void moveTo(float x, float y, float guiWidth, float guiHeight) {
		float w = width * scaleValue();
		float h = height * scaleValue();
		float cx = (x + w / 2) / guiWidth;
		float cy = (y + h / 2) / guiHeight;
		int col = cx < 1 / 3f ? 0 : cx > 2 / 3f ? 2 : 1;
		int row = cy < 1 / 3f ? 0 : cy > 2 / 3f ? 2 : 1;
		Anchor a = Anchor.values()[row * 3 + col];
		// All three together, so a PvP position never mixes with parts of the normal one.
		anchor.setEffective(a);
		offsetX.setEffective((double) ((x + a.fx * w - a.fx * guiWidth) / guiWidth));
		offsetY.setEffective((double) ((y + a.fy * h - a.fy * guiHeight) / guiHeight));
	}

	/** Position and size back to default for the current mode (in PvP mode: back to "same as normal"). */
	public void resetPosition() {
		anchor.resetEffective();
		offsetX.resetEffective();
		offsetY.resetEffective();
		scale.resetEffective();
	}

	/** True if this element has its own PvP position or size. */
	public boolean hasPvpPlacement() {
		return anchor.hasPvpOverride() || offsetX.hasPvpOverride() || offsetY.hasPvpOverride() || scale.hasPvpOverride();
	}

	// ---- glide between layouts (driven by HudManager) ----

	/** Where it was drawn last frame (GUI pixels) and whether it was drawn at all. */
	float lastX;
	float lastY;
	float lastScale = 1;
	boolean drawnLastFrame;
	/** Start of the current glide; only valid while HudManager's glide runs. */
	float fromX;
	float fromY;
	float fromScale;
	boolean glides;

	// ---- drawing helpers ----

	protected float pad() {
		return padding.floatValue();
	}

	protected boolean shadow() {
		return textShadow.isOn();
	}

	/** The background box over the whole element (if enabled). */
	protected void drawBackground(GuiGraphicsExtractor g) {
		drawBox(g, 0, 0, width, height, backgroundColor.argb());
	}

	protected void drawBox(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		if (background.isOn()) {
			float seed = id().hashCode() % 97 + x * 0.13f + y * 0.07f;
			HudFx.under(g, x, y, w, h, cornerRadius.floatValue(), seed, 0);
			Shapes.rect(g, x, y, w, h, cornerRadius.floatValue(), color);
			HudFx.over(g, x, y, w, h, cornerRadius.floatValue(), seed, 0);
		}
	}

	/** "Label value" in label and value colours; returns the x after it. */
	protected float drawPair(GuiGraphicsExtractor g, String label, String value, float x, float y, int valueArgb) {
		float after = HudText.draw(g, label, x, y, textColor.argb(), shadow());
		return HudText.draw(g, value, after, y, valueArgb, shadow());
	}
}
