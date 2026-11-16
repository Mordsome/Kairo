package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.layout.LayoutScreen;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.sound.MediaHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Music Visualiser: bars (or a wave, dots or a ring) that move to the sound your PC is playing, placed on the HUD
 * like any other mod. It can follow everything the PC plays or only move while a player is playing music. The
 * sound levels come from {@link MediaHelper} (Windows only); nothing is recorded.
 */
public final class VisualizerHud extends HudElement {
	public enum Style implements EnumSetting.Labeled {
		BARS("Bars"),
		MIRROR("Mirrored bars"),
		WAVE("Wave"),
		DOTS("Dots"),
		RING("Ring");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Paint implements EnumSetting.Labeled {
		SOLID("One colour"),
		GRADIENT("Two colours"),
		RAINBOW("Rainbow"),
		LOUDNESS("By loudness");

		private final String label;

		Paint(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Source implements EnumSetting.Labeled {
		ALL_SOUND("All PC sound"),
		MUSIC_ONLY("Only while music plays");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Motion implements EnumSetting.Labeled {
		SNAPPY("Snappy"),
		SMOOTH("Smooth"),
		FLOATY("Floaty");

		private final String label;

		Motion(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Source> source = add(new EnumSetting<>("source", "Listens to", Source.ALL_SOUND)
		.description("All PC sound also moves to Minecraft's own sounds. Only while music plays: it rests unless a player (Spotify, a browser...) is playing"));
	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.BARS));
	public final SliderSetting count = add(new SliderSetting("count", "How many", 24, 6, 48, 1)
		.description("Bars, dots or points of the wave"));
	public final SliderSetting barWidth = add(new SliderSetting("bar_width", "Thickness", 3, 1, 8, 0.5).unit("px"));
	public final SliderSetting gap = add(new SliderSetting("gap", "Gap", 1.5, 0, 6, 0.5).unit("px")
		.visibleWhen(() -> style.get() != Style.WAVE));
	public final SliderSetting tall = add(new SliderSetting("height", "Height", 36, 12, 90, 1).unit("px"));
	public final SliderSetting edges = add(new SliderSetting("edges", "Round edges", 1.0, 0.0, 1.0, 0.05).percent()
		.description("0% square ends, 100% fully round"));
	public final EnumSetting<Paint> paint = add(new EnumSetting<>("paint", "Colours", Paint.GRADIENT)
		.description("One colour uses the value colour. Two colours blends from it to the second colour"));
	public final ColorSetting secondColor = add(new ColorSetting("second_color", "Second colour", 0xFFFF5CA8)
		.visibleWhen(() -> paint.get() == Paint.GRADIENT || paint.get() == Paint.LOUDNESS));
	public final BooleanSetting acrossHeight = add(new BooleanSetting("across_height", "Blend along each bar", false)
		.description("The two colours run up each bar instead of from left to right")
		.visibleWhen(() -> paint.get() == Paint.GRADIENT && (style.get() == Style.BARS || style.get() == Style.MIRROR)));
	public final EnumSetting<Motion> motion = add(new EnumSetting<>("motion", "Animation", Motion.SMOOTH)
		.description("How quickly it follows the sound: snappy jumps with every beat, floaty glides"));
	public final SliderSetting sensitivity = add(new SliderSetting("sensitivity", "Sensitivity", 1.0, 0.4, 2.5, 0.05).multiplier()
		.description("Turn it up for quiet music, down if it's always at the top"));
	public final BooleanSetting caps = add(new BooleanSetting("caps", "Falling caps", true)
		.description("A small mark rests on each bar's highest point and slowly falls")
		.visibleWhen(() -> style.get() == Style.BARS));
	public final BooleanSetting mirrorSides = add(new BooleanSetting("mirror_sides", "Low notes in the middle", false)
		.description("The same picture on both sides, low notes in the centre"));
	public final BooleanSetting glow = add(new BooleanSetting("glow", "Glow", true)
		.description("A soft light under it in its own colours"));
	public final BooleanSetting hideSilent = add(new BooleanSetting("hide_silent", "Hide when silent", false)
		.description("Fades away while nothing is playing"));

	private static final int MAX = 48;
	private final float[] level = new float[MAX];
	private final float[] cap = new float[MAX];
	private long lastFrame = System.nanoTime();
	private float presence = 1;
	private float energy;

	public VisualizerHud() {
		super("music_visualizer", "Music Visualiser", "Bars, a wave or a ring that move to the sound your PC plays", Icon.SIGNAL, false,
			Anchor.BOTTOM_RIGHT, -0.004, -0.14);
		background.startsAs(false);
	}

	private int points() {
		return Math.clamp(Math.round(count.floatValue()), 6, MAX);
	}

	/** The level for point {@code i} of {@code n}, read from the helper's 32 bands (or a made-up tune for previews). */
	private float sample(float[] bands, boolean made, int i, int n, float time) {
		float at = n <= 1 ? 0 : i / (float) (n - 1);
		if (mirrorSides.isOn()) {
			at = Math.abs(at * 2 - 1);
		}
		if (made) {
			float beat = (float) Math.pow(0.5 + 0.5 * Math.sin(time * 5.2), 6);
			float value = 0.28f + 0.22f * (float) Math.sin(time * 2.1 + at * 9) + 0.18f * (float) Math.sin(time * 3.7 - at * 15)
				+ beat * 0.5f * (1 - at) * (1 - at);
			return Math.clamp(value * (1 - at * 0.45f), 0, 1);
		}
		float position = at * (MediaHelper.BANDS - 1);
		int low = (int) Math.floor(position);
		int high = Math.min(MediaHelper.BANDS - 1, low + 1);
		float raw = Animation.lerp(bands[low], bands[high], position - low);
		// Below about a sixth it's only the noise floor.
		return Math.clamp((raw - 0.17f) / 0.83f * sensitivity.floatValue(), 0, 1);
	}

	@Override
	protected void layout(boolean preview) {
		int n = points();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		boolean editing = preview || Minecraft.getInstance().gui.screen() instanceof LayoutScreen;
		// Previews, the layout editor, and a PC we can't listen on show a made-up tune so there's something to see.
		boolean made = preview || (editing && !MediaHelper.hearing());
		boolean resting = !made && source.get() == Source.MUSIC_ONLY && !MediaHelper.track().playing();
		float[] bands = MediaHelper.bands();
		float time = now / 1e9f;
		float rise = switch (motion.get()) {
			case SNAPPY -> 60;
			case SMOOTH -> 26;
			case FLOATY -> 10;
		};
		float fall = switch (motion.get()) {
			case SNAPPY -> 5.5f;
			case SMOOTH -> 2.6f;
			case FLOATY -> 1.1f;
		};
		float total = 0;
		for (int i = 0; i < n; i++) {
			float target = resting ? 0 : sample(bands, made, i, n, time);
			if (target > level[i]) {
				level[i] += (target - level[i]) * Math.min(1, dt * rise);
			} else {
				level[i] = Math.max(target, level[i] - dt * fall * Math.max(0.25f, level[i]));
			}
			cap[i] = Math.max(level[i], cap[i] - dt * 0.45f);
			total += level[i];
		}
		energy = total / n;
		float wanted = hideSilent.isOn() && energy < 0.012f && !editing ? 0 : 1;
		presence += (wanted - presence) * Math.min(1, dt * 5);
		float h = tall.floatValue();
		if (style.get() == Style.RING) {
			float size = h * 2 + 20;
			width = size + pad() * 2;
			height = size + pad() * 2;
		} else {
			float step = style.get() == Style.WAVE ? barWidth.floatValue() + 2 : barWidth.floatValue() + gap.floatValue();
			width = pad() * 2 + n * step - (style.get() == Style.WAVE ? 2 : gap.floatValue());
			height = pad() * 2 + h;
		}
	}

	private int color(int i, int n, float value, float along) {
		int first = valueColor.argb() | 0xFF000000;
		int second = secondColor.argb() | 0xFF000000;
		float across = n <= 1 ? 0 : i / (float) (n - 1);
		int c = switch (paint.get()) {
			case SOLID -> first;
			case GRADIENT -> Colors.lerp(first, second, acrossHeight.isOn() ? along : across);
			case RAINBOW -> Colors.hsv((across * 0.85f + System.nanoTime() / 1e9f * 0.04f) % 1f, 0.75f, 1f) | 0xFF000000;
			case LOUDNESS -> Colors.lerp(first, second, Math.clamp(value * 1.15f, 0, 1));
		};
		return Colors.multiplyAlpha(c, presence);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		if (presence < 0.01f) {
			return;
		}
		drawBox(g, 0, 0, width, height, Colors.multiplyAlpha(backgroundColor.argb(), presence));
		int n = points();
		float h = tall.floatValue();
		float w = barWidth.floatValue();
		float radius = Math.min(w, 8) / 2 * edges.floatValue();
		float left = pad();
		float bottom = pad() + h;
		if (glow.isOn() && style.get() != Style.RING) {
			int light = Colors.withAlpha(color(n / 2, n, energy, 0.5f), Math.min(0.5f, 0.12f + energy * 0.6f) * presence);
			Shapes.shadow(g, left, bottom - h * Math.max(0.2f, energy * 1.6f), width - pad() * 2, h * Math.max(0.2f, energy * 1.6f), 6, 14, light);
		}
		switch (style.get()) {
			case BARS -> {
				float step = w + gap.floatValue();
				for (int i = 0; i < n; i++) {
					float bar = Math.max(w * 0.6f, level[i] * h);
					float x = left + i * step;
					if (paint.get() == Paint.GRADIENT && acrossHeight.isOn()) {
						Shapes.gradientRect(g, x, bottom - bar, w, bar, radius, color(i, n, level[i], Math.min(1, bar / h)), color(i, n, level[i], 0));
					} else {
						Shapes.rect(g, x, bottom - bar, w, bar, radius, color(i, n, level[i], 0));
					}
					if (caps.isOn()) {
						float top = bottom - Math.max(bar, cap[i] * h) - 2.2f;
						Shapes.rect(g, x, top, w, 1.2f, Math.min(0.6f, radius), Colors.multiplyAlpha(color(i, n, cap[i], 1), 0.85f));
					}
				}
			}
			case MIRROR -> {
				float step = w + gap.floatValue();
				float middle = pad() + h / 2;
				for (int i = 0; i < n; i++) {
					float bar = Math.max(w * 0.6f, level[i] * h);
					float x = left + i * step;
					if (paint.get() == Paint.GRADIENT && acrossHeight.isOn()) {
						int inner = color(i, n, level[i], 0);
						int outer = color(i, n, level[i], Math.min(1, bar / h));
						Shapes.gradientRect(g, x, middle - bar / 2, w, bar / 2 + 0.5f, radius, outer, inner);
						Shapes.gradientRect(g, x, middle - 0.5f, w, bar / 2 + 0.5f, radius, inner, outer);
					} else {
						Shapes.rect(g, x, middle - bar / 2, w, bar, radius, color(i, n, level[i], 0));
					}
				}
			}
			case DOTS -> {
				float step = w + gap.floatValue();
				float dot = w;
				int rows = Math.max(3, (int) (h / (dot + 1.5f)));
				for (int i = 0; i < n; i++) {
					int lit = Math.round(level[i] * rows);
					float x = left + i * step;
					for (int row = 0; row < rows; row++) {
						float y = bottom - (row + 1) * (dot + 1.5f) + 1.5f;
						int c = color(i, n, level[i], row / (float) rows);
						Shapes.rect(g, x, y, dot, dot, dot / 2 * edges.floatValue(), row < lit ? c : Colors.multiplyAlpha(c, 0.13f));
					}
				}
			}
			case WAVE -> {
				float step = w + 2;
				float middle = pad() + h / 2;
				float px = 0;
				float py = 0;
				for (int i = 0; i < n; i++) {
					float x = left + i * step + w / 2;
					// Up and down in turn, so the levels read as one line swinging round the middle.
					float swing = level[i] * h / 2 * (i % 2 == 0 ? 1 : -1) * (0.75f + 0.25f * (float) Math.sin(System.nanoTime() / 1e9f * 3 + i));
					float y = middle - swing;
					if (i > 0) {
						Shapes.line(g, px, py, x, y, Math.max(1, w * 0.6f), color(i, n, level[i], 0));
					}
					if (edges.floatValue() > 0.05f) {
						Shapes.circle(g, x, y, Math.max(0.5f, w * 0.3f * edges.floatValue()), color(i, n, level[i], 0));
					}
					px = x;
					py = y;
				}
			}
			case RING -> {
				float cx = width / 2;
				float cy = height / 2;
				float inner = h * 0.45f + 6 + energy * 4;
				if (glow.isOn()) {
					Shapes.shadow(g, cx - inner, cy - inner, inner * 2, inner * 2, inner, 12,
						Colors.withAlpha(color(0, n, energy, 0.5f), Math.min(0.22f, 0.06f + energy * 0.3f) * presence));
				}
				for (int i = 0; i < n; i++) {
					float bar = Math.max(w * 0.6f, level[i] * h * 0.55f);
					float angle = (float) (i * Math.PI * 2 / n - Math.PI / 2);
					g.pose().pushMatrix();
					g.pose().translate(cx, cy);
					g.pose().rotate(angle + (float) Math.PI / 2);
					Shapes.rect(g, -w / 2, -inner - bar, w, bar, radius, color(i, n, level[i], 0));
					g.pose().popMatrix();
				}
				Shapes.ring(g, cx, cy, inner - 2.5f, 1, Colors.multiplyAlpha(color(0, n, energy, 0), 0.5f));
			}
		}
	}
}
