package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.mods.hotbar.HotbarRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

/**
 * The hotbar, health, hunger, armor, air and XP bar in the client's style. Every part can stay Minecraft's own;
 * positions match vanilla so item names, titles and other mods still line up.
 */
public final class Hotbar extends Module {
	public enum HotbarStyle implements EnumSetting.Labeled {
		MINECRAFT("Minecraft"),
		PANEL("Glass panel"),
		SLOTS("Separate slots");

		private final String label;

		HotbarStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum HealthStyle implements EnumSetting.Labeled {
		HEARTS("Hearts"),
		SQUARES("Squares"),
		BARS("Bars");

		private final String label;

		HealthStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum XpStyle implements EnumSetting.Labeled {
		MINECRAFT("Minecraft"),
		SLIM("Slim line"),
		HIDDEN("Hidden");

		private final String label;

		XpStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	// ---- hotbar ----
	public final EnumSetting<HotbarStyle> style = add(new EnumSetting<>("style", "Hotbar style", HotbarStyle.PANEL));
	public final ColorSetting background = add(new ColorSetting("background", "Background", 0xA00C1018)
		.description("Behind the slots and the bars"));
	public final BooleanSetting accentSelection = add(new BooleanSetting("accent_selection", "Selection in accent color", true)
		.description("Follows the client accent (orange in PvP mode)"));
	public final ColorSetting selectionColor = add(new ColorSetting("selection_color", "Selection color", 0xFF7FD8FF, false).followsAccent()
		.visibleWhen(() -> !accentSelection.isOn()));
	public final SliderSetting corner = add(new SliderSetting("corner", "Corner radius", 4, 0, 8, 0.5).unit("px"));
	public final BooleanSetting slotNumbers = add(new BooleanSetting("slot_numbers", "Slot numbers", false));

	// ---- health and friends ----
	public final EnumSetting<HealthStyle> healthStyle = add(new EnumSetting<>("health_style", "Health style", HealthStyle.BARS)
		.description("Hearts, squares, or one bar"));
	public final BooleanSetting styleOthers = add(new BooleanSetting("style_others", "Armor, hunger and air too", true)
		.description("Same style for the other status icons"));
	public final BooleanSetting numbers = add(new BooleanSetting("numbers", "Numbers on bars", true)
		.visibleWhen(() -> healthStyle.get() == HealthStyle.BARS));
	public final ColorSetting healthColor = add(new ColorSetting("health_color", "Health color", 0xFFFF4D5E, false));
	public final ColorSetting absorptionColor = add(new ColorSetting("absorption_color", "Absorption color", 0xFFFFC94D, false));
	public final ColorSetting armorColor = add(new ColorSetting("armor_color", "Armor color", 0xFFC9D4E0, false));
	public final ColorSetting hungerColor = add(new ColorSetting("hunger_color", "Hunger color", 0xFFE8A04A, false));
	public final ColorSetting airColor = add(new ColorSetting("air_color", "Air color", 0xFF7FD8FF, false));

	// ---- XP ----
	public final EnumSetting<XpStyle> xpStyle = add(new EnumSetting<>("xp_style", "XP bar", XpStyle.SLIM));
	public final ColorSetting xpColor = add(new ColorSetting("xp_color", "XP color", 0xFF8CE05A, false)
		.visibleWhen(() -> xpStyle.get() == XpStyle.SLIM));

	// ---- animations ----
	public final BooleanSetting smoothSelection = add(new BooleanSetting("smooth_selection", "Sliding selection", true)
		.description("The highlight glides to the slot you pick"));
	public final BooleanSetting pickupBounce = add(new BooleanSetting("pickup_bounce", "Pickup bounce", true)
		.description("Items squash briefly when you pick them up"));
	public final BooleanSetting animateBars = add(new BooleanSetting("animate_bars", "Animated health", true)
		.description("Bars ease to new values; health you lose fades out"));
	public final BooleanSetting lowHealthPulse = add(new BooleanSetting("low_health_pulse", "Low health pulse", true)
		.description("Health glows when you're below a quarter"));

	private final HotbarRenderer renderer = new HotbarRenderer(this);

	public Hotbar() {
		super("hotbar", "Hotbar", "Hotbar, health and XP in the client style", Category.VISUAL, Icon.HOTBAR, true);
	}

	@Override
	public void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (g, delta) -> {
			if (styledHotbar()) {
				renderer.hotbar(g, delta);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> (g, delta) -> {
			if (styledHealth()) {
				renderer.health(g);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> (g, delta) -> {
			if (styledOthers()) {
				renderer.armor(g);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> (g, delta) -> {
			if (styledOthers()) {
				renderer.food(g);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, vanilla -> (g, delta) -> {
			if (styledOthers()) {
				renderer.air(g);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, vanilla -> (g, delta) -> {
			if (styledXp()) {
				renderer.xpLevel(g);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
	}

	private static boolean inGame() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
	}

	public boolean styledHotbar() {
		return isEnabled() && style.get() != HotbarStyle.MINECRAFT && inGame();
	}

	public boolean styledHealth() {
		return isEnabled() && healthStyle.get() != HealthStyle.HEARTS && inGame();
	}

	public boolean styledOthers() {
		return styledHealth() && styleOthers.isOn();
	}

	/** Used by the experience bar mixin too. */
	public boolean styledXp() {
		return isEnabled() && xpStyle.get() != XpStyle.MINECRAFT && inGame();
	}

	public HotbarRenderer renderer() {
		return renderer;
	}
}
