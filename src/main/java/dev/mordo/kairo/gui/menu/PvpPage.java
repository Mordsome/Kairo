package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.core.PvpSettings;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.layout.LayoutScreen;
import dev.mordo.kairo.gui.widgets.Binding;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ModeSwitch;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.mods.Fullbright;
import dev.mordo.kairo.mods.HurtEffects;
import dev.mordo.kairo.mods.ItemScale;
import dev.mordo.kairo.mods.TotemPops;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * PvP mode options (right-click PvP Mode in the ring). Most rows edit a mod setting's PvP override directly,
 * so changing one here never touches the normal value; the rest are {@link PvpSettings} that only exist for
 * PvP mode. Changing a mod's value here also switches that mod on for PvP mode.
 */
public final class PvpPage extends PanelPage {
	private record Row(Setting<?> setting, @Nullable Module module, String label, String description) {
	}

	private record Section(String name, Icon icon, List<Row> rows, SettingsList list) {
	}

	private final List<Section> sections = new ArrayList<>();
	private final List<NavItem> nav = new ArrayList<>();
	private final Button reset;
	private final ModeSwitch mode;
	private final Button editLayout;
	private int selected;

	PvpPage(MenuScreen menu) {
		super(menu);
		PvpSettings pvp = PvpSettings.INSTANCE;
		HurtEffects hurt = ModuleManager.get(HurtEffects.class);
		TotemPops pops = ModuleManager.get(TotemPops.class);
		ItemScale items = ModuleManager.get(ItemScale.class);
		Fullbright bright = ModuleManager.get(Fullbright.class);

		section("Camera", Icon.EYE, List.of(
			own(pvp.noViewBobbing),
			linked(hurt.hurtTilt, hurt, "Hurt camera tilt", "How much the camera shakes when you take damage (0% = none)")));
		section("Explosions", Icon.FLAME, List.of(
			linked(hurt.explosionParticles, hurt, "Explosion particles", "0% = no explosion particles at all, for crystal PvP"),
			linked(hurt.hideExplosionFlash, hurt, "Hide explosion flash", "No big white flash when something explodes"),
			linked(hurt.explosionLimit, hurt, "Max explosions per second", "Explosions past this only make sound (0 = no limit)")));
		section("Overlays", Icon.TOTEM, List.of(
			linked(hurt.fireOverlay, hurt, "Fire overlay height", "Lower fire so you can see while burning"),
			linked(hurt.fireOpacity, hurt, "Fire overlay opacity", "See-through fire"),
			linked(pops.popScale, pops, "Totem pop size", "How big the totem animation gets"),
			linked(pops.popDuration, pops, "Totem pop length", "How long the totem animation stays"),
			linked(pops.particles, pops, "Totem particles", "0% = none")));
		section("Items", Icon.SWORD, List.of(
			linked(items.enabled, null, "Smaller held items", "Item Scale while PvP mode is on"),
			linked(items.mainHand, items, "Main hand size", ""),
			linked(items.offHand, items, "Offhand size", "Totems and shields in the other hand"),
			linked(items.height, items, "Item height", "Lower items cover less of the screen")));
		section("World & HUD", Icon.PIN, List.of(
			own(pvp.hideWaypoints),
			linked(bright.enabled, null, "Fullbright", "Raise the gamma while PvP mode is on")));

		reset = new Button("Reset section", null, Button.Style.SUBTLE, this::resetSection).textSize(11.5f);
		mode = new ModeSwitch(new Binding<>() {
			@Override
			public Boolean get() {
				return PvpMode.isActiveForReal();
			}

			@Override
			public void set(Boolean on) {
				PvpMode.set(on);
			}
		}).textSize(11.5f);
		editLayout = new Button("Edit PvP HUD layout", Icon.LAYOUT, Button.Style.SUBTLE, () -> {
			if (Minecraft.getInstance().player == null) {
				dev.mordo.kairo.gui.Toasts.show("Join a world to edit the HUD layout", Theme.accent());
				return;
			}
			PvpMode.set(true);
			menu.close(new LayoutScreen());
		}).textSize(11.5f);
		sections.get(sections.size() - 1).list().header((g, x, y, w, mx, my, fade, put) -> {
			Shapes.rect(g, x, y, w, 58, 14, Colors.multiplyAlpha(Colors.withAlpha(Theme.pvpAccent(), 0.07f), fade),
				Colors.multiplyAlpha(Colors.withAlpha(Theme.pvpAccent(), 0.3f), fade), 1);
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "HUD in PvP mode", x + 18, y + 21, 13, Theme.text(1, fade));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Every HUD element can have its own position, size and visibility.", x + 18, y + 39,
				10.5f, Theme.text(0.55f, fade));
			float bw = editLayout.preferredWidth();
			editLayout.bounds(x + w - 14 - bw, y + 12, bw, 34);
			put.accept(editLayout);
			return 58;
		});
	}

	/** A PvP-only option: its plain value is what applies in PvP mode. */
	private static Row own(Setting<?> setting) {
		return new Row(setting, null, setting.name(), setting.descriptionText());
	}

	/** A mod setting whose PvP override this row edits. */
	private static Row linked(Setting<?> setting, @Nullable Module module, String label, String description) {
		return new Row(setting, module, label, description);
	}

	private void section(String name, Icon icon, List<Row> rows) {
		int index = sections.size();
		Map<Setting<?>, Row> byId = new IdentityHashMap<>();
		List<SettingsList.Entry> entries = new ArrayList<>();
		for (Row row : rows) {
			byId.put(row.setting(), row);
			entries.add(new SettingsList.Entry(row.setting(), row.label(), row.description()));
		}
		SettingsList list = new SettingsList(host, null, entries, List.of()).widgets(setting -> widgetFor(byId.get(setting)));
		sections.add(new Section(name, icon, rows, list));
		nav.add(new NavItem(name, icon, null, () -> selected == index, () -> {
			selected = index;
			sections.get(index).list().resetScroll();
		}));
	}

	private Widget widgetFor(Row row) {
		boolean own = isOwn(row);
		Binding<?> binding = own ? Binding.normal(row.setting()) : overrideBinding(row.setting(), row.module());
		return SettingsList.create(row.setting(), binding, true, host);
	}

	private static boolean isOwn(Row row) {
		return PvpSettings.INSTANCE.settings().contains(row.setting());
	}

	/** Reads the PvP value (the normal one while there's no override); writing sets the override. */
	private static <T> Binding<T> overrideBinding(Setting<T> setting, @Nullable Module module) {
		return new Binding<>() {
			@Override
			public T get() {
				T override = setting.getPvpOverride();
				return override != null ? override : setting.getNormal();
			}

			@Override
			public void set(T value) {
				setting.setPvpOverride(value);
				if (module != null) {
					onInPvp(module);
				}
			}
		};
	}

	/** Tuning a mod for PvP only makes sense if it runs in PvP mode. */
	private static void onInPvp(Module module) {
		Boolean override = module.enabled.getPvpOverride();
		boolean on = override != null ? override : module.enabled.getNormal();
		if (!on) {
			module.enabled.setPvpOverride(true);
		}
	}

	private void resetSection() {
		for (Row row : sections.get(selected).rows()) {
			if (isOwn(row)) {
				row.setting().reset();
			} else {
				row.setting().clearPvpOverride();
			}
		}
	}

	@Override
	protected String navLabel() {
		return "PVP MODE";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		Section section = sections.get(selected);
		drawTitle(g, "PvP mode", section.name(), contentX, fade);
		float modeW = mode.preferredWidth();
		mode.bounds(contentX + contentW - modeW, py + 27, modeW, 34);
		put(mode, g, mx, my, fade, live);
		float resetW = reset.preferredWidth();
		reset.bounds(mode.x - 10 - resetW, py + 28, resetW, 32);
		put(reset, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Applied only while PvP mode is on. Your normal settings stay untouched.",
			contentX, py + 82, 11, Colors.multiplyAlpha(Theme.pvpAccent(), 0.9f * fade));
		float top = py + 104;
		section.list().render(g, contentX, top, contentW + 14, py + ph - 20 - top, mx, my, fade, live);
	}

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		return sections.get(selected).list().mouseScrolled(mx, my, amount);
	}
}
