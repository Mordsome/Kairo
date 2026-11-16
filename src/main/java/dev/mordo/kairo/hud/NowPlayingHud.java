package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.BooleanSetting;
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
import net.minecraft.resources.Identifier;

/**
 * Now Playing: what your PC is playing right now (Spotify, YouTube or anything else in a browser, any player that
 * shows up in Windows' media controls), with its cover, the app, a progress bar and the time played or left. Every
 * part has its own switch. Display only: Kairo never controls the player. Windows only; see {@link MediaHelper} for
 * how the information is read.
 */
public final class NowPlayingHud extends HudElement {
	public enum Layout implements EnumSetting.Labeled {
		CARD("Card"),
		LINE("One line");

		private final String label;

		Layout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum TimeStyle implements EnumSetting.Labeled {
		PLAYED_AND_LENGTH("1:23 / 3:45"),
		LEFT("-2:22"),
		PLAYED("1:23");

		private final String label;

		TimeStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Paused implements EnumSetting.Labeled {
		SHOW("Show"),
		DIM("Dim"),
		HIDE("Hide");

		private final String label;

		Paused(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Layout> layout = add(new EnumSetting<>("layout", "Layout", Layout.CARD)
		.description("Card: cover, title, artist and a bar. One line: title and artist side by side"));
	public final BooleanSetting showCover = add(new BooleanSetting("show_cover", "Album cover", true)
		.description("The cover picture the player gives to Windows (Spotify and most browsers do)"));
	public final SliderSetting coverSize = add(new SliderSetting("cover_size", "Cover size", 30, 16, 56, 1).unit("px")
		.visibleWhen(() -> showCover.isOn()));
	public final BooleanSetting showTitle = add(new BooleanSetting("show_title", "Title", true));
	public final BooleanSetting showArtist = add(new BooleanSetting("show_artist", "Artist", true));
	public final BooleanSetting showApp = add(new BooleanSetting("show_app", "App name", true)
		.description("Which program is playing: Spotify, Chrome..."));
	public final BooleanSetting showBar = add(new BooleanSetting("show_bar", "Progress bar", true));
	public final SliderSetting barHeight = add(new SliderSetting("bar_height", "Bar thickness", 2, 1, 6, 0.5).unit("px")
		.visibleWhen(() -> showBar.isOn()));
	public final BooleanSetting showTimes = add(new BooleanSetting("show_times", "Time", true));
	public final EnumSetting<TimeStyle> timeStyle = add(new EnumSetting<>("time_style", "Time shows", TimeStyle.PLAYED_AND_LENGTH)
		.visibleWhen(() -> showTimes.isOn()));
	public final SliderSetting textWidth = add(new SliderSetting("text_width", "Text width", 120, 60, 240, 5).unit("px")
		.description("How wide the title and artist can get before they scroll or are cut"));
	public final BooleanSetting scrollLong = add(new BooleanSetting("scroll_long", "Scroll long titles", true)
		.description("Long titles glide sideways so you can read all of them; off cuts them with ..."));
	public final EnumSetting<Paused> whenPaused = add(new EnumSetting<>("when_paused", "When paused", Paused.DIM));
	public final BooleanSetting songChange = add(new BooleanSetting("song_change", "Animate song changes", true)
		.description("The new title slides in when the song changes"));

	private static final MediaHelper.Track SAMPLE = new MediaHelper.Track(true, true, 83_000, 225_000, "Spotify", "Midnight Drive", "Neon Harbor", 0, 0);

	private MediaHelper.Track track = SAMPLE;
	private boolean sample;
	private boolean visible;
	private String lastSong = "";
	private final Animation change = new Animation(1);
	private float shownProgress;

	public NowPlayingHud() {
		super("now_playing", "Now Playing", "The song your PC is playing: cover, title, artist and time", Icon.NOTE, false, Anchor.TOP_RIGHT, -0.004, 0.12);
	}

	private static String time(long ms) {
		long seconds = Math.max(0, ms / 1000);
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds % 60) : String.format("%d:%02d", minutes, seconds % 60);
	}

	private String timeText() {
		long played = sample ? track.positionMs() : track.positionNow();
		long length = track.lengthMs();
		if (length <= 0) {
			return time(played);
		}
		return switch (timeStyle.get()) {
			case PLAYED_AND_LENGTH -> time(played) + " / " + time(length);
			case LEFT -> "-" + time(length - played);
			case PLAYED -> time(played);
		};
	}

	private float progress() {
		long played = sample ? track.positionMs() : track.positionNow();
		return track.lengthMs() <= 0 ? 0 : Math.clamp(played / (float) track.lengthMs(), 0, 1);
	}

	private boolean card() {
		return layout.get() == Layout.CARD;
	}

	private float cover() {
		return showCover.isOn() ? (card() ? coverSize.floatValue() : HudText.LINE) : 0;
	}

	@Override
	protected void layout(boolean preview) {
		MediaHelper.Track now = MediaHelper.track();
		// With nothing playing, the settings preview and the layout editor show a sample so there's something to place.
		boolean editing = preview || Minecraft.getInstance().gui.screen() instanceof LayoutScreen;
		sample = !now.known() && editing;
		track = sample ? SAMPLE : now;
		visible = track.known() && (track.playing() || whenPaused.get() != Paused.HIDE || editing);
		String song = track.title() + "\n" + track.artist();
		if (!song.equals(lastSong)) {
			lastSong = song;
			if (songChange.isOn() && !Animation.reduceMotion()) {
				change.set(0).animateTo(1, 420);
			}
		}
		float text = textWidth.floatValue();
		float coverSide = cover();
		float gap = coverSide > 0 ? 4 : 0;
		if (card()) {
			int lines = (showTitle.isOn() ? 1 : 0) + (showArtist.isOn() ? 1 : 0) + (showApp.isOn() || showTimes.isOn() ? 1 : 0);
			float textHeight = lines * HudText.LINE + Math.max(0, lines - 1) + (showBar.isOn() ? barHeight.floatValue() + 3 : 0);
			width = pad() * 2 + coverSide + gap + (lines > 0 || showBar.isOn() ? text : 0);
			height = pad() * 2 + Math.max(coverSide, textHeight);
		} else {
			float timeWidth = showTimes.isOn() ? HudText.width(timeText()) + 5 : 0;
			width = pad() * 2 + coverSide + gap + text + timeWidth;
			height = pad() * 2 + HudText.LINE + (showBar.isOn() ? barHeight.floatValue() + 2 : 0);
		}
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		if (!visible) {
			return;
		}
		float dim = !track.playing() && whenPaused.get() == Paused.DIM ? 0.5f : 1;
		drawBox(g, 0, 0, width, height, Colors.multiplyAlpha(backgroundColor.argb(), 0.6f + 0.4f * dim));
		float coverSide = cover();
		float x = pad();
		float y = pad();
		if (coverSide > 0) {
			drawCover(g, x, y, coverSide, dim);
			x += coverSide + 4;
		}
		float text = textWidth.floatValue();
		float slide = change.value();
		int titleColor = Colors.multiplyAlpha(textColor.argb(), dim * slide);
		int softColor = Colors.multiplyAlpha(textColor.argb(), 0.68f * dim * slide);
		int accent = Colors.multiplyAlpha(valueColor.argb(), dim);
		float shift = (1 - slide) * 6;
		if (card()) {
			if (showTitle.isOn()) {
				clipped(g, track.title(), x, y, text, titleColor, shift, 0);
				y += HudText.LINE + 1;
			}
			if (showArtist.isOn()) {
				clipped(g, track.artist(), x, y, text, softColor, shift, 0.37f);
				y += HudText.LINE + 1;
			}
			if (showApp.isOn() || showTimes.isOn()) {
				String times = showTimes.isOn() ? timeText() : "";
				float timesWidth = HudText.width(times);
				if (showApp.isOn()) {
					clipped(g, track.app(), x, y, text - timesWidth - (times.isEmpty() ? 0 : 4), accent, 0, 0);
				}
				if (showTimes.isOn()) {
					HudText.draw(g, times, x + text - timesWidth, y, Colors.multiplyAlpha(textColor.argb(), 0.68f * dim), shadow());
				}
				y += HudText.LINE + 1;
			}
			if (showBar.isOn()) {
				bar(g, x, y + 1, text, accent, dim);
			}
		} else {
			String line = showTitle.isOn() && showArtist.isOn() && !track.artist().isEmpty() ? track.title() + "  -  " + track.artist()
				: showTitle.isOn() ? track.title() : showArtist.isOn() ? track.artist() : showApp.isOn() ? track.app() : "";
			clipped(g, line, x, y, text, titleColor, shift, 0);
			if (showTimes.isOn()) {
				String times = timeText();
				HudText.draw(g, times, width - pad() - HudText.width(times), y, Colors.multiplyAlpha(textColor.argb(), 0.68f * dim), shadow());
			}
			if (showBar.isOn()) {
				bar(g, pad(), y + HudText.LINE + 1, width - pad() * 2, accent, dim);
			}
		}
	}

	private void drawCover(GuiGraphicsExtractor g, float x, float y, float side, float dim) {
		Identifier texture = sample ? null : MediaHelper.cover();
		int ix = Math.round(x);
		int iy = Math.round(y);
		int is = Math.round(side);
		if (texture != null) {
			g.blit(texture, ix, iy, ix + is, iy + is, 0, 1, 0, 1);
			if (dim < 1) {
				Shapes.rect(g, ix, iy, is, is, 0, Colors.withAlpha(0xFF000000, 0.45f));
			}
			return;
		}
		// No cover (yet): a plain tile in your colour with a note on it.
		int accent = valueColor.argb();
		Shapes.gradientRect(g, x, y, side, side, Math.min(cornerRadius.floatValue(), side / 2), Colors.withAlpha(accent, 0.55f * dim),
			Colors.withAlpha(Colors.lerp(accent, 0xFF000000, 0.55f), 0.7f * dim));
		dev.mordo.kairo.gui.IconRenderer.draw(g, Icon.NOTE, x + side / 2, y + side / 2, side * 0.55f, Colors.withAlpha(0xFFFFFFFF, 0.9f * dim), 1.6f);
	}

	private void bar(GuiGraphicsExtractor g, float x, float y, float w, int accent, float dim) {
		float h = barHeight.floatValue();
		float target = progress();
		// Glide to the new position (a seek, a new song) instead of jumping.
		shownProgress = Math.abs(target - shownProgress) > 0.5f || Animation.reduceMotion() ? target : shownProgress + (target - shownProgress) * 0.2f;
		Shapes.rect(g, x, y, w, h, h / 2, Colors.multiplyAlpha(textColor.argb(), 0.22f * dim));
		if (track.lengthMs() > 0) {
			Shapes.rect(g, x, y, Math.max(h, w * shownProgress), h, h / 2, accent);
		}
	}

	/**
	 * Text kept inside {@code max} wide: short text is drawn as it is; long text glides from end to end (with a rest
	 * at each end) or, with scrolling off, is cut with an ellipsis.
	 */
	private void clipped(GuiGraphicsExtractor g, String text, float x, float y, float max, int color, float shift, float phase) {
		if (text.isEmpty() || max <= 4) {
			return;
		}
		float w = HudText.width(text);
		if (w <= max) {
			HudText.draw(g, text, x + shift, y, color, shadow());
			return;
		}
		if (!scrollLong.isOn() || Animation.reduceMotion()) {
			String cut = text;
			while (cut.length() > 1 && HudText.width(cut + "...") > max) {
				cut = cut.substring(0, cut.length() - 1);
			}
			HudText.draw(g, cut.stripTrailing() + "...", x + shift, y, color, shadow());
			return;
		}
		float over = w - max;
		float travel = over / 14f; // seconds to cross, at 14 px a second
		float cycle = travel * 2 + 3.2f;
		float t = (System.nanoTime() / 1e9f + phase * cycle) % cycle;
		float offset = t < 1.6f ? 0 : t < 1.6f + travel ? (t - 1.6f) / travel * over : t < 3.2f + travel ? over : (1 - (t - 3.2f - travel) / travel) * over;
		g.enableScissor((int) Math.floor(x), (int) Math.floor(y - 1), (int) Math.ceil(x + max), (int) Math.ceil(y + HudText.LINE + 1));
		HudText.draw(g, text, x - offset + shift, y, color, shadow());
		g.disableScissor();
	}
}
