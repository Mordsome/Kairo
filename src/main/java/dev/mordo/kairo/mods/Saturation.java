package dev.mordo.kairo.mods;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Locale;

/**
 * Hidden saturation and food previews:
 * <ul>
 *   <li>a gold outline around each hunger icon covered by saturation (partly drawn for partial points),</li>
 *   <li>while you hold food: the hunger and saturation it would give flash on the bar, and the health you'd
 *       regenerate from it flashes on the hearts,</li>
 *   <li>food tooltips show hunger and saturation.</li>
 * </ul>
 * Works on Minecraft's icons and on the Hotbar mod's squares and bars.
 */
public final class Saturation extends Module {
	public enum Style implements EnumSetting.Labeled {
		OUTLINE("Outline"),
		GLOW("Glow"),
		UNDERLINE("Underline");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.OUTLINE)
		.description("Outline: a gold rim around the hunger icons covered by saturation"));
	public final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFD34D, false).accentable());
	public final BooleanSetting foodPreview = add(new BooleanSetting("food_preview", "Preview food you hold", true)
		.description("The hunger and saturation it would give flash on the bar"));
	public final BooleanSetting healthPreview = add(new BooleanSetting("health_preview", "Preview health", true)
		.description("The health it would let you regenerate flashes on your hearts"));
	public final BooleanSetting tooltips = add(new BooleanSetting("tooltips", "Food tooltips", true)
		.description("Hunger and saturation in food tooltips"));
	public final BooleanSetting number = add(new BooleanSetting("number", "Show the number", false)
		.description("Saturation as a number next to the hunger row"));

	private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
	private static final Identifier OUTLINE_TEXTURE = ClientInfo.id("saturation_outline");
	private boolean outlineReady;
	private boolean outlineFailed;

	public Saturation() {
		super("saturation", "Saturation", "Saturation and food previews on the hunger bar", Category.VISUAL, Icon.FOOD, true);
	}

	@Override
	public void init() {
		// Registered after the Hotbar mod, so these wrap whichever bars are in use.
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, previous -> (g, delta) -> {
			Player player = player();
			boolean on = isEnabled() && player != null;
			if (on && style.get() == Style.GLOW) {
				drawSaturation(g, player, true);
			}
			previous.extractRenderState(g, delta);
			if (on) {
				drawSaturation(g, player, false);
				drawFoodPreview(g, player);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, previous -> (g, delta) -> {
			previous.extractRenderState(g, delta);
			Player player = player();
			if (isEnabled() && player != null) {
				drawHealthPreview(g, player);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!isEnabled() || !tooltips.isOn()) {
				return;
			}
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null) {
				lines.add(Component.literal("Hunger ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal("+" + food.nutrition()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("   Saturation ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal("+" + String.format(Locale.ROOT, "%.1f", food.saturation())).withColor(color.argb() & 0xFFFFFF)));
			}
		});
	}

	private static @Nullable Player player() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getCameraEntity() instanceof Player p && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR ? p : null;
	}

	/** The food in your hand (main hand first), or null. */
	private static @Nullable FoodProperties heldFood(Player player) {
		for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null) {
				return food;
			}
		}
		return null;
	}

	/** 0..1 flashing for the food previews. */
	private static float flash() {
		return 0.25f + 0.5f * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
	}

	private enum Mode { VANILLA, SQUARES, BARS }

	private static Mode mode() {
		Hotbar hotbar = ModuleManager.get(Hotbar.class);
		if (!hotbar.styledOthers()) {
			return Mode.VANILLA;
		}
		return hotbar.healthStyle.get() == Hotbar.HealthStyle.BARS ? Mode.BARS : Mode.SQUARES;
	}

	private static Mode healthMode() {
		Hotbar hotbar = ModuleManager.get(Hotbar.class);
		if (!hotbar.styledHealth()) {
			return Mode.VANILLA;
		}
		return hotbar.healthStyle.get() == Hotbar.HealthStyle.BARS ? Mode.BARS : Mode.SQUARES;
	}

	// ---- saturation ----

	/** {@code behind}: the pass under the hunger icons (glow); otherwise the pass on top. */
	private void drawSaturation(GuiGraphicsExtractor g, Player player, boolean behind) {
		float saturation = Math.min(player.getFoodData().getSaturationLevel(), 20);
		float xRight = g.guiWidth() / 2f + 91;
		float y = g.guiHeight() - 39;
		Mode mode = mode();
		int gold = color.argb();
		if (mode != Mode.BARS && saturation > 0) {
			Style s = style.get();
			if (behind && s == Style.GLOW) {
				int halves = (int) Math.ceil(saturation);
				float w = halves * 4f;
				float x0 = xRight - w - 0.5f;
				Shapes.shadow(g, x0 + 1, y + 1.5f, w - 1, 7, 3.5f, 6, Colors.withAlpha(gold, 0.75f));
				Shapes.rect(g, x0 + 1, y + 1, w - 1, 7, 3.5f, Colors.withAlpha(gold, 0.26f));
			} else if (!behind && s == Style.UNDERLINE) {
				float w = (float) Math.ceil(saturation) * 4f;
				Shapes.rect(g, xRight - w, y + 9.5f, w - 1, 1, 0.5f, Colors.withAlpha(gold, 0.9f));
			} else if (!behind && s == Style.OUTLINE) {
				drawOutlines(g, xRight, y, saturation, gold, mode, 1);
			}
		}
		if (!behind && number.isOn()) {
			String text = String.format(Locale.ROOT, "%.1f", player.getFoodData().getSaturationLevel());
			Fonts.draw(g, Typeface.MONO_BOLD, text, xRight + 3, y + 4.5f, 6, Colors.withAlpha(gold, 0.95f), Fonts.Align.LEFT, 0, true);
		}
	}

	/** Gold rims for {@code points} of saturation (icon 0 is the rightmost; each icon holds 2 points). */
	private void drawOutlines(GuiGraphicsExtractor g, float xRight, float y, float points, int gold, Mode mode, float alpha) {
		for (int i = 0; i < 10; i++) {
			float f = Math.clamp((points - i * 2) / 2f, 0, 1);
			if (f <= 0) {
				break;
			}
			int xo = Math.round(xRight - i * 8 - 9);
			int yo = Math.round(y);
			if (mode == Mode.SQUARES) {
				float r = Math.min(ModuleManager.get(Hotbar.class).corner.floatValue(), 2) + 0.5f;
				float w = 8 * f;
				Shapes.outline(g, xo + 0.5f + 8 - w, yo + 0.5f, w, 8, Math.min(r, w / 2), Colors.withAlpha(gold, 0.9f * alpha), 0.8f);
			} else if (ensureOutline()) {
				// The rim, cut to the covered part (icons empty from the left, so it grows from the right).
				int w = Mth.ceil(9 * f);
				g.blit(RenderPipelines.GUI_TEXTURED, OUTLINE_TEXTURE, xo + 9 - w, yo, 9 - w, 0, w, 9, w, 9, 9, 9,
					ARGB.multiply(gold, ARGB.white(alpha)));
			}
		}
	}

	/** Builds the drumstick rim from Minecraft's own empty hunger icon: its black outline pixels, recoloured gold. */
	private boolean ensureOutline() {
		if (outlineReady || outlineFailed) {
			return outlineReady;
		}
		try (InputStream in = Minecraft.class.getResourceAsStream("/assets/minecraft/textures/gui/sprites/hud/food_empty.png")) {
			if (in == null) {
				outlineFailed = true;
				return false;
			}
			NativeImage icon = NativeImage.read(in);
			NativeImage rim = new NativeImage(9, 9, true);
			for (int y = 0; y < 9; y++) {
				for (int x = 0; x < 9; x++) {
					int p = x < icon.getWidth() && y < icon.getHeight() ? icon.getPixel(x, y) : 0;
					boolean outline = (p >>> 24) > 0 && (p & 0xFFFFFF) == 0; // pure black = the outline
					rim.setPixel(x, y, outline ? 0xFFFFFFFF : 0);
				}
			}
			icon.close();
			Minecraft.getInstance().getTextureManager().register(OUTLINE_TEXTURE, new DynamicTexture(() -> "kairo saturation rim", rim));
			outlineReady = true;
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't build the saturation outline", e);
			outlineFailed = true;
		}
		return outlineReady;
	}

	// ---- previews while holding food ----

	private void drawFoodPreview(GuiGraphicsExtractor g, Player player) {
		FoodProperties food = foodPreview.isOn() ? heldFood(player) : null;
		if (food == null) {
			return;
		}
		int current = player.getFoodData().getFoodLevel();
		int after = Math.min(20, current + food.nutrition());
		float sat = player.getFoodData().getSaturationLevel();
		float satAfter = Math.min(after, sat + food.saturation());
		float xRight = g.guiWidth() / 2f + 91;
		float y = g.guiHeight() - 39;
		float flash = flash();
		Mode mode = mode();
		if (mode == Mode.BARS) {
			float x0 = xRight - 81 * after / 20f;
			float x1 = xRight - 81 * current / 20f;
			if (x1 - x0 > 0.5f) {
				Shapes.rect(g, x0, y + 1, x1 - x0, 7, 1.5f, Colors.argb(0.45f * flash, 0xFFFFFF));
			}
			float s0 = xRight - 81 * satAfter / 20f;
			float s1 = xRight - 81 * Math.min(sat, 20) / 20f;
			if (s1 - s0 > 0.5f) {
				Shapes.rect(g, s0, y + 1 + 7 - 1.5f, s1 - s0, 1.5f, 0.75f, Colors.withAlpha(color.argb(), flash));
			}
			return;
		}
		for (int i = 0; i < 10; i++) {
			int full = i * 2 + 2;
			int half = i * 2 + 1;
			if (half <= current || half > after) {
				continue;
			}
			int xo = Math.round(xRight - i * 8 - 9);
			int yo = Math.round(y);
			boolean isHalf = after == half;
			if (mode == Mode.SQUARES) {
				int c = Colors.withAlpha(ModuleManager.get(Hotbar.class).hungerColor.argb(), flash);
				if (isHalf) {
					Shapes.rect(g, xo + 4.5f, yo + 1, 3.5f, 7, 1.25f, c);
				} else {
					Shapes.rect(g, xo + 1, yo + 1, 7, 7, 2, c);
				}
			} else {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, isHalf ? FOOD_HALF : FOOD_FULL, xo, yo, 9, 9, ARGB.white(flash));
			}
			if (full <= current) {
				break;
			}
		}
		// Saturation it would add, as flashing rims.
		if (style.get() == Style.OUTLINE && satAfter > sat) {
			float flashAlpha = flash;
			for (int i = 0; i < 10; i++) {
				float covered = Math.clamp((satAfter - i * 2) / 2f, 0, 1);
				float already = Math.clamp((sat - i * 2) / 2f, 0, 1);
				if (covered <= already) {
					continue;
				}
				drawOutlinesOne(g, xRight, y, i, covered, mode, flashAlpha);
			}
		}
	}

	private void drawOutlinesOne(GuiGraphicsExtractor g, float xRight, float y, int i, float f, Mode mode, float alpha) {
		int gold = color.argb();
		int xo = Math.round(xRight - i * 8 - 9);
		int yo = Math.round(y);
		if (mode == Mode.SQUARES) {
			float w = 8 * f;
			Shapes.outline(g, xo + 0.5f + 8 - w, yo + 0.5f, w, 8, Math.min(2.5f, w / 2), Colors.withAlpha(gold, 0.9f * alpha), 0.8f);
		} else if (ensureOutline()) {
			int w = Mth.ceil(9 * f);
			g.blit(RenderPipelines.GUI_TEXTURED, OUTLINE_TEXTURE, xo + 9 - w, yo, 9 - w, 0, w, 9, w, 9, 9, 9, ARGB.multiply(gold, ARGB.white(alpha)));
		}
	}

	/**
	 * Health you'd regenerate from the food in hand. Natural regeneration needs 18+ hunger and spends 6
	 * exhaustion per half heart: 1.5 saturation, then 1.5 hunger (down to 18). A close estimate.
	 */
	private void drawHealthPreview(GuiGraphicsExtractor g, Player player) {
		FoodProperties food = healthPreview.isOn() ? heldFood(player) : null;
		if (food == null || !player.isHurt()) {
			return;
		}
		int foodAfter = Math.min(20, player.getFoodData().getFoodLevel() + food.nutrition());
		float satAfter = Math.min(foodAfter, player.getFoodData().getSaturationLevel() + food.saturation());
		if (foodAfter < 18) {
			return;
		}
		float heal = (satAfter + Math.max(0, foodAfter - 18)) / 1.5f;
		float health = player.getHealth();
		float max = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), health);
		float after = Math.min(max, health + heal);
		if (after <= health + 0.01f) {
			return;
		}
		float flash = flash();
		float xLeft = g.guiWidth() / 2f - 91;
		float yBase = g.guiHeight() - 39;
		Mode mode = healthMode();
		if (mode == Mode.BARS) {
			float total = max + player.getAbsorptionAmount();
			float x0 = xLeft + 81 * health / total;
			float x1 = xLeft + 81 * after / total;
			Shapes.rect(g, x0, yBase + 1, x1 - x0, 7, 1.5f, Colors.argb(0.45f * flash, 0xFFFFFF));
			return;
		}
		int rows = Mth.ceil((max + Mth.ceil(player.getAbsorptionAmount())) / 2 / 10);
		int rowH = Math.max(10 - (rows - 2), 3);
		int now = Mth.ceil(health);
		int target = Mth.ceil(after);
		// Every heart that would change, drawn as it would end up (a half heart being topped up flashes full).
		for (int i = now / 2; i * 2 < target; i++) {
			int halvesAfter = Math.min(target - i * 2, 2);
			boolean isHalf = halvesAfter == 1;
			int xo = Math.round(xLeft + (i % 10) * 8);
			int yo = Math.round(yBase - (i / 10) * rowH);
			if (mode == Mode.SQUARES) {
				int c = Colors.withAlpha(ModuleManager.get(Hotbar.class).healthColor.argb(), flash);
				Shapes.rect(g, xo + 1, yo + 1, isHalf ? 3.5f : 7, 7, 2, c);
			} else {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, isHalf ? HEART_HALF : HEART_FULL, xo, yo, 9, 9, ARGB.white(flash));
			}
		}
	}
}
