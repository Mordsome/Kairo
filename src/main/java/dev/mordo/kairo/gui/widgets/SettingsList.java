package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ChoiceSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.core.settings.TextSetting;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scrolling list of setting rows (name and description on the left, the right widget on the right),
 * optionally followed by an "In PvP mode" section where each setting can get an override value.
 */
public final class SettingsList {
	/** One row: a setting plus how to label it. */
	public record Entry(Setting<?> setting, String label, String description) {
		public static Entry of(Setting<?> setting) {
			return new Entry(setting, setting.name(), setting.descriptionText());
		}
	}

	/** Extra content a module puts above its settings (e.g. the waypoint manager). Scrolls with the list. */
	public interface Section {
		/**
		 * Draw at (x, y), {@code w} wide; hand child widgets to {@code put} so they get input.
		 * Returns the height used.
		 */
		float render(GuiGraphicsExtractor g, float x, float y, float w, float mx, float my, float fade, java.util.function.Consumer<Widget> put);
	}

	/** A module that adds a {@link Section} to its settings page. */
	public interface HasSection {
		Section settingsSection(WidgetHost host);
	}

	private @Nullable Section header;
	private java.util.function.@Nullable Function<Setting<?>, Widget> widgetFactory;

	public SettingsList header(@Nullable Section header) {
		this.header = header;
		return this;
	}

	/** Builds the controls for the normal rows instead of the default (used by the PvP page to edit overrides). */
	public SettingsList widgets(java.util.function.Function<Setting<?>, Widget> factory) {
		this.widgetFactory = factory;
		return this;
	}

	private static final float ROW = 46;
	private static final float ROW_WITH_DESCRIPTION = 58;
	private static final float SECTION_GAP = 30;

	private final WidgetHost host;
	private final List<Entry> entries;
	private final List<Entry> pvpEntries;
	private final @Nullable String title;
	private final Map<Setting<?>, Widget> widgets = new IdentityHashMap<>();
	private final Map<Setting<?>, Widget> pvpWidgets = new IdentityHashMap<>();
	private final Map<Setting<?>, ToggleSwitch> overrideToggles = new IdentityHashMap<>();
	private final Map<Setting<?>, Animation> overrideAnims = new IdentityHashMap<>();
	private final ScrollState scroll = new ScrollState();
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private boolean interactive;

	/**
	 * @param title section label above the normal rows (null for none)
	 * @param pvpEntries rows for the PvP section, empty for no PvP section
	 */
	public SettingsList(WidgetHost host, @Nullable String title, List<Entry> entries, List<Entry> pvpEntries) {
		this.host = host;
		this.title = title;
		this.entries = entries;
		this.pvpEntries = pvpEntries;
	}

	/** Rows for every visible setting of a holder, plus a PvP section if asked for. */
	public static SettingsList forSettings(WidgetHost host, @Nullable String title, List<Setting<?>> settings, boolean pvpSection) {
		List<Entry> normal = new ArrayList<>();
		List<Entry> pvp = new ArrayList<>();
		for (Setting<?> setting : settings) {
			if (setting.isHidden()) {
				continue;
			}
			if (!setting.id().equals("enabled")) {
				normal.add(Entry.of(setting));
			}
			if (pvpSection && setting.supportsPvpOverride() && !CustomWidgets.has(setting)) {
				pvp.add(setting.id().equals("enabled")
					? new Entry(setting, "Enabled", "Turn this mod on or off in PvP mode")
					: Entry.of(setting));
			}
		}
		return new SettingsList(host, title, normal, pvp);
	}

	public void resetScroll() {
		scroll.reset();
	}

	public boolean contains(float mx, float my) {
		return mx >= viewX && mx <= viewX + viewW && my >= viewY && my <= viewY + viewH;
	}

	public boolean mouseScrolled(float mx, float my, float amount) {
		if (!contains(mx, my)) {
			return false;
		}
		scroll.scroll(amount);
		return true;
	}

	/** {@code interactive} false = draw only (leaving or arriving), widgets don't take input. */
	public void render(GuiGraphicsExtractor g, float x, float y, float w, float h, float mx, float my, float fade, boolean interactive) {
		this.interactive = interactive;
		viewX = x;
		viewY = y;
		viewW = w;
		viewH = h;
		float listW = w - 14; // room for the scrollbar
		g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
		float cy = y - scroll.value();
		try {
			if (header != null) {
				float rowFade = fade;
				cy += header.render(g, x, cy, listW, mx, my, fade, widget -> {
					// Only widgets inside the visible part of the list take input.
					if (widget.y + widget.h > viewY && widget.y < viewY + viewH) {
						put(widget, g, mx, my, rowFade);
					}
				}) + 18;
			}
			if (title != null) {
				sectionLabel(g, title, x, cy + 8, Theme.text(0.45f, fade));
				cy += 26;
			}
			for (Entry entry : entries) {
				if (!entry.setting().isVisible()) {
					continue;
				}
				cy += row(g, entry, widget(entry.setting(), false), null, x, cy, listW, mx, my, fade);
			}
			if (!pvpEntries.isEmpty()) {
				cy += SECTION_GAP;
				sectionLabel(g, "IN PVP MODE", x, cy + 8, Colors.multiplyAlpha(Theme.pvpAccent(), fade));
				Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Override a value to use something else while PvP mode is on. Normal values stay untouched.",
					x, cy + 28, 10.5f, Theme.text(0.5f, fade));
				Fonts.draw(g, Typeface.MONO_BOLD, "OVERRIDE", x + listW - ToggleSwitch.WIDTH / 2, cy + 8, 7.5f,
					Theme.text(0.45f, fade), Fonts.Align.CENTER, 1.2f, false);
				cy += 46;
				for (Entry entry : pvpEntries) {
					Setting<?> setting = entry.setting();
					if (!setting.isVisible() && !setting.id().equals("enabled")) {
						continue;
					}
					ToggleSwitch override = overrideToggle(setting);
					Widget widget = widget(setting, true);
					widget.setEnabled(setting.hasPvpOverride());
					cy += row(g, entry, widget, override, x, cy, listW, mx, my, fade);
				}
			}
		} finally {
			g.disableScissor();
		}
		float contentHeight = cy + scroll.value() - y + 16;
		scroll.setSizes(contentHeight, h);
		scroll.drawBar(g, x + w, y, fade);
	}

	private static void sectionLabel(GuiGraphicsExtractor g, String text, float x, float cy, int color) {
		Fonts.draw(g, Typeface.MONO_BOLD, text, x, cy, 8.5f, color, Fonts.Align.LEFT, 2f, false);
	}

	/** Draws one row and returns its height. */
	private float row(GuiGraphicsExtractor g, Entry entry, Widget widget, @Nullable ToggleSwitch override,
					  float x, float y, float w, float mx, float my, float fade) {
		boolean hasDescription = !entry.description().isEmpty();
		float rowH = Math.max(hasDescription ? ROW_WITH_DESCRIPTION : ROW, widget.preferredHeight() + 16);
		float right = x + w;
		float overrideShown = 1;
		if (override != null) {
			// PvP rows: override switch on the far right, the PvP value appears to its left once overridden.
			override.bounds(right - ToggleSwitch.WIDTH, y + (rowH - ToggleSwitch.HEIGHT) / 2, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
			right = override.x - 22;
			Animation shown = overrideShown(entry.setting());
			shown.animateTo(entry.setting().hasPvpOverride() ? 1 : 0, 220);
			overrideShown = shown.value();
		}
		float widgetW = widget.preferredWidth();
		widget.bounds(right - widgetW, y + (rowH - widget.preferredHeight()) / 2, widgetW, widget.preferredHeight());
		float textRight = widget.x - 24;
		float textW = Math.max(40, textRight - x);
		float nameY = hasDescription ? y + rowH / 2 - 8 : y + rowH / 2;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, entry.label(), 13, textW), x, nameY, 13, Theme.text(1, fade));
		if (hasDescription) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, entry.description(), 10.5f, textW),
				x, y + rowH / 2 + 9, 10.5f, Theme.text(0.5f, fade));
		}
		Shapes.rect(g, x, y + rowH - 0.5f, w, 1, 0, Colors.argb(0.06f * fade, 0xFFFFFF));

		// Only rows that are actually inside the view take input.
		boolean visible = y + rowH > viewY && y < viewY + viewH;
		if (visible) {
			if (override != null) {
				put(override, g, mx, my, fade);
				if (overrideShown < 0.999f) {
					Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Same as normal", right, y + rowH / 2, 11, Theme.text(0.4f, fade * (1 - overrideShown)),
						Fonts.Align.RIGHT, 0, false);
				}
				if (overrideShown > 0.001f) {
					put(widget, g, mx, my, fade * overrideShown);
				}
			} else {
				put(widget, g, mx, my, fade);
			}
		}
		return rowH;
	}

	private Animation overrideShown(Setting<?> setting) {
		return overrideAnims.computeIfAbsent(setting, s -> new Animation(s.hasPvpOverride() ? 1 : 0));
	}

	private void put(Widget widget, GuiGraphicsExtractor g, float mx, float my, float fade) {
		if (interactive) {
			host.render(widget, g, mx, my, fade);
		} else {
			widget.render(g, mx, my, fade, false);
		}
	}

	private ToggleSwitch overrideToggle(Setting<?> setting) {
		return overrideToggles.computeIfAbsent(setting, s -> new ToggleSwitch(new Binding<>() {
			@Override
			public Boolean get() {
				return s.hasPvpOverride();
			}

			@Override
			public void set(Boolean on) {
				if (on) {
					copyNormalToOverride(s);
				} else {
					s.clearPvpOverride();
				}
			}
		}).accent(Theme::pvpAccent));
	}

	private static <T> void copyNormalToOverride(Setting<T> setting) {
		setting.setPvpOverride(setting.getNormal());
	}

	private Widget widget(Setting<?> setting, boolean pvp) {
		Map<Setting<?>, Widget> cache = pvp ? pvpWidgets : widgets;
		if (!pvp && widgetFactory != null) {
			return cache.computeIfAbsent(setting, widgetFactory);
		}
		return cache.computeIfAbsent(setting, s -> create(s, pvp, host));
	}

	/** The right control for a setting type. */
	public static Widget create(Setting<?> setting, boolean pvp, WidgetHost host) {
		Widget custom = CustomWidgets.create(setting, host);
		if (custom != null) {
			return custom;
		}
		return create(setting, pvp ? Binding.pvp(setting) : Binding.normal(setting), pvp, host);
	}

	/** The right control for a setting type, reading and writing through {@code binding}. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Widget create(Setting<?> setting, Binding binding, boolean pvp, WidgetHost host) {
		if (setting instanceof BooleanSetting) {
			ToggleSwitch toggle = new ToggleSwitch(binding);
			return pvp ? toggle.accent(Theme::pvpAccent) : toggle;
		}
		if (setting instanceof SliderSetting slider) {
			Slider widget = new Slider(slider, binding);
			return pvp ? widget.accent(Theme::pvpAccent) : widget;
		}
		if (setting instanceof ColorSetting color) {
			return new ColorButton(color, binding, host);
		}
		if (setting instanceof ChoiceSetting choice) {
			return new ChoicePicker(choice, binding, host);
		}
		if (setting instanceof EnumSetting enumSetting) {
			return new Dropdown(enumSetting.values(), binding, host);
		}
		if (setting instanceof KeybindSetting keybind) {
			return new KeybindField(keybind);
		}
		if (setting instanceof TextSetting text) {
			TextField field = new TextField(text.name(), null).maxLength(text.maxLength());
			field.setText((String) binding.get());
			field.onChange(binding::set);
			return field;
		}
		throw new IllegalArgumentException("No widget for " + setting.getClass().getSimpleName());
	}
}
