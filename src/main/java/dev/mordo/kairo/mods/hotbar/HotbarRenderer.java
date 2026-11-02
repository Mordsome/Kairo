package dev.mordo.kairo.mods.hotbar;

import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.mods.Hotbar;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the styled hotbar and status bars. Geometry follows vanilla (hotbar 182x22 at the bottom, status rows
 * 81 wide either side at 39 px from the bottom) so everything else on the HUD stays where players expect it.
 * All values are GUI pixels.
 */
public final class HotbarRenderer {
	private static final float BAR_H = 7;
	private static final float ROW_W = 81;

	private final Hotbar mod;

	// Animation state.
	private long lastNanos;
	private float dt;
	private float selectionX = Float.NaN;
	private float shownHealth = Float.NaN;
	private float trailHealth = Float.NaN;
	private long trailHoldUntil;
	private float shownXp = Float.NaN;
	private float shownFood = Float.NaN;
	private float shownArmor = Float.NaN;
	private float shownAir = Float.NaN;

	public HotbarRenderer(Hotbar mod) {
		this.mod = mod;
	}

	/** Frame time for the animations; whichever part draws first in a frame advances it. */
	private void tickClock() {
		long now = System.nanoTime();
		if (now - lastNanos < 2_000_000L) {
			return; // same frame
		}
		dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
	}

	private static float ease(float current, float target, float speed, float dt) {
		if (Float.isNaN(current)) {
			return target;
		}
		return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
	}

	private static Player player() {
		return Minecraft.getInstance().getCameraEntity() instanceof Player p ? p : null;
	}

	private float radius(float max) {
		return Math.min(mod.corner.floatValue(), max);
	}

	private int selection() {
		return mod.accentSelection.isOn() ? Theme.modeAccent() : mod.selectionColor.argb();
	}

	// ---- hotbar ----

	public void hotbar(GuiGraphicsExtractor g, DeltaTracker delta) {
		tickClock();
		Player player = player();
		if (player == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		float x0 = cx - 91;
		float y0 = h - 22;
		int bg = mod.background.argb();
		int border = Colors.argb(0.10f, 0xFFFFFF);
		float r = radius(8);
		boolean slots = mod.style.get() == Hotbar.HotbarStyle.SLOTS;

		if (slots) {
			for (int i = 0; i < 9; i++) {
				Shapes.rect(g, x0 + i * 20 + 1.5f, y0 + 1.5f, 19, 19, Math.min(r, 6), bg, border, 0.5f);
			}
		} else {
			Shapes.rect(g, x0, y0, 182, 22, r, bg, border, 0.5f);
		}

		// Selection: slides between slots.
		int selected = player.getInventory().getSelectedSlot();
		float target = x0 + selected * 20 + 1;
		selectionX = mod.smoothSelection.isOn() ? ease(selectionX, target, 22, dt) : target;
		if (Math.abs(selectionX - target) < 0.02f) {
			selectionX = target;
		}
		int sel = selection();
		Shapes.rect(g, selectionX, y0 + 1, 20, 20, Math.max(0, Math.min(r, 6) - 0.5f),
			Colors.withAlpha(sel, 0.22f), Colors.withAlpha(sel, 0.95f), 0.9f);

		if (mod.slotNumbers.isOn()) {
			for (int i = 0; i < 9; i++) {
				Fonts.draw(g, Typeface.MONO_BOLD, String.valueOf(i + 1), x0 + i * 20 + 4, y0 + 5, 4.5f,
					i == selected ? Colors.withAlpha(sel, 0.9f) : Theme.text(0.4f), Fonts.Align.LEFT, 0, false);
			}
		}

		// Offhand slot.
		ItemStack offhand = player.getOffhandItem();
		HumanoidArm offhandArm = player.getMainArm().getOpposite();
		if (!offhand.isEmpty()) {
			float ox = offhandArm == HumanoidArm.LEFT ? cx - 91 - 29 : cx + 91 + 7;
			Shapes.rect(g, ox, y0, 22, 22, r, bg, border, 0.5f);
		}

		int seed = 1;
		for (int i = 0; i < 9; i++) {
			slot(g, cx - 90 + i * 20 + 2, h - 19, delta, player, player.getInventory().getItem(i), seed++);
		}
		if (!offhand.isEmpty()) {
			int ix = offhandArm == HumanoidArm.LEFT ? cx - 91 - 26 : cx + 91 + 10;
			slot(g, ix, h - 19, delta, player, offhand, seed);
		}

		// Attack cooldown when the player picked "Hotbar" for the attack indicator.
		if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && mc.player != null) {
			float strength = mc.player.getAttackStrengthScale(0);
			if (strength < 1) {
				float ax = offhandArm == HumanoidArm.RIGHT ? cx - 91 - 22 + 7.5f : cx + 91 + 6 + 7.5f;
				Shapes.rect(g, ax, h - 20, 3, 18, 1.5f, bg);
				float fill = 18 * strength;
				Shapes.rect(g, ax, h - 20 + 18 - fill, 3, fill, 1.5f, sel);
			}
		}
	}

	private void slot(GuiGraphicsExtractor g, int x, int y, DeltaTracker delta, Player player, ItemStack stack, int seed) {
		if (stack.isEmpty()) {
			return;
		}
		float pop = mod.pickupBounce.isOn() ? stack.getPopTime() - delta.getGameTimeDeltaPartialTick(false) : 0;
		if (pop > 0) {
			float squeeze = 1 + pop / 5;
			g.pose().pushMatrix();
			g.pose().translate(x + 8, y + 12);
			g.pose().scale(1 / squeeze, (squeeze + 1) / 2);
			g.pose().translate(-(x + 8), -(y + 12));
		}
		g.item(player, stack, x, y, seed);
		if (pop > 0) {
			g.pose().popMatrix();
		}
		g.itemDecorations(Minecraft.getInstance().font, stack, x, y);
	}

	// ---- health ----

	private boolean bars() {
		return mod.healthStyle.get() == Hotbar.HealthStyle.BARS;
	}

	private int heartColor(Player player) {
		if (player.hasEffect(MobEffects.POISON)) {
			return 0xFF97C93D;
		}
		if (player.hasEffect(MobEffects.WITHER)) {
			return 0xFF5A5560;
		}
		if (player.isFullyFrozen()) {
			return 0xFF9FE3FF;
		}
		return mod.healthColor.argb();
	}

	private float maxHealth(Player player) {
		return Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
	}

	/** Rows of hearts vanilla would use, and the space between them (armor sits above the last one). */
	private int[] heartRows(Player player) {
		if (bars()) {
			return new int[]{1, 10};
		}
		int rows = Mth.ceil((maxHealth(player) + Mth.ceil(player.getAbsorptionAmount())) / 2 / 10);
		return new int[]{rows, Math.max(10 - (rows - 2), 3)};
	}

	public void health(GuiGraphicsExtractor g) {
		tickClock();
		Player player = player();
		if (player == null) {
			return;
		}
		float health = player.getHealth();
		float max = maxHealth(player);
		float absorption = player.getAbsorptionAmount();
		long now = System.nanoTime();

		// Fill eases to the real value; health you just lost stays as a pale trail for a moment.
		boolean animate = mod.animateBars.isOn();
		if (!animate || Float.isNaN(shownHealth)) {
			shownHealth = health;
			trailHealth = health;
		} else {
			if (health < shownHealth) {
				if (trailHealth < shownHealth) {
					trailHealth = shownHealth;
				}
				trailHoldUntil = now + 350_000_000L;
				shownHealth = health;
			} else {
				shownHealth = ease(shownHealth, health, 10, dt);
			}
			if (now > trailHoldUntil) {
				trailHealth = ease(trailHealth, shownHealth, 7, dt);
			}
			if (trailHealth < shownHealth) {
				trailHealth = shownHealth;
			}
		}

		int color = heartColor(player);
		if (mod.lowHealthPulse.isOn() && health <= max * 0.25f && health > 0) {
			float t = (float) (Math.sin(now / 1e9 * 7) * 0.5 + 0.5);
			color = Colors.lerp(color, 0xFFFFFFFF, t * 0.35f);
		}

		int w = g.guiWidth();
		int h = g.guiHeight();
		float xLeft = w / 2f - 91;
		float yBase = h - 39;
		if (bars()) {
			float total = max + absorption;
			float x = xLeft;
			float y = yBase + 1;
			float r = radius(BAR_H / 2);
			Shapes.rect(g, x, y, ROW_W, BAR_H, r, mod.background.argb());
			float trailW = ROW_W * Math.min(trailHealth, max) / total;
			float fillW = ROW_W * Math.min(shownHealth, max) / total;
			if (trailW > fillW + 0.05f) {
				fillBar(g, x, y, trailW, r, Colors.argb(0.55f, 0xFFFFFF));
			}
			fillBar(g, x, y, fillW, r, color);
			if (absorption > 0) {
				float ax = x + ROW_W * max / total;
				Shapes.rect(g, ax, y, x + ROW_W - ax, BAR_H, r, mod.absorptionColor.argb());
			}
			if (mod.numbers.isOn()) {
				String text = String.valueOf(Mth.ceil(health));
				float tx = x + 3;
				Fonts.draw(g, Typeface.MONO_BOLD, text, tx, y + BAR_H / 2, 5.5f, 0xFFFFFFFF, Fonts.Align.LEFT, 0, true);
				if (absorption > 0) {
					tx += Fonts.width(Typeface.MONO_BOLD, text, 5.5f) + 2;
					Fonts.draw(g, Typeface.MONO_BOLD, "+" + Mth.ceil(absorption), tx, y + BAR_H / 2, 5.5f, 0xFFFFF3C4,
						Fonts.Align.LEFT, 0, true);
				}
			}
		} else {
			squaresHealth(g, player, xLeft, yBase, max, health, absorption, color);
		}
	}

	private static void fillBar(GuiGraphicsExtractor g, float x, float y, float width, float r, int color) {
		if (width <= 0.05f) {
			return;
		}
		Shapes.rect(g, x, y, width, BAR_H, Math.min(r, width / 2), color);
	}

	private void squaresHealth(GuiGraphicsExtractor g, Player player, float xLeft, float yBase, float max, float health,
							   float absorption, int color) {
		int[] rows = heartRows(player);
		int rowH = rows[1];
		int containers = Mth.ceil(max / 2);
		int absorbing = Mth.ceil(absorption / 2);
		int current = Mth.ceil(shownHealth);
		int trail = Mth.ceil(trailHealth);
		int absHalves = Mth.ceil(absorption);
		int bg = mod.background.argb();
		int gold = mod.absorptionColor.argb();
		for (int i = containers + absorbing - 1; i >= 0; i--) {
			float xo = xLeft + (i % 10) * 8;
			float yo = yBase - (float) (i / 10) * rowH;
			int halves = i * 2;
			square(g, xo, yo, 2, bg);
			if (i >= containers) {
				int a = halves - containers * 2;
				if (a < absHalves) {
					square(g, xo, yo, a + 1 == absHalves ? 1 : 2, gold);
				}
				continue;
			}
			if (halves < trail && halves + 1 >= current) {
				square(g, xo, yo, halves + 1 == trail ? 1 : 2, Colors.argb(0.6f, 0xFFFFFF));
			}
			if (halves < current) {
				square(g, xo, yo, halves + 1 == current ? 1 : 2, color);
			}
		}
	}

	/** One status square in a vanilla icon cell at (xo, yo); {@code halves} 1 fills the left half. */
	private void square(GuiGraphicsExtractor g, float xo, float yo, int halves, int color) {
		float r = radius(2);
		if (halves >= 2) {
			Shapes.rect(g, xo + 1, yo + 1, 7, 7, r, color);
		} else {
			Shapes.rect(g, xo + 1, yo + 1, 3.5f, 7, Math.min(r, 1.25f), color);
		}
	}

	/** Same, mirrored: {@code halves} 1 fills the right half (hunger and air drain from the left). */
	private void squareRight(GuiGraphicsExtractor g, float xo, float yo, int halves, int color) {
		if (halves >= 2) {
			square(g, xo, yo, 2, color);
		} else {
			Shapes.rect(g, xo + 4.5f, yo + 1, 3.5f, 7, Math.min(radius(2), 1.25f), color);
		}
	}

	// ---- armor, food, air ----

	public void armor(GuiGraphicsExtractor g) {
		tickClock();
		Player player = player();
		if (player == null) {
			return;
		}
		int armor = player.getArmorValue();
		if (armor <= 0) {
			shownArmor = 0;
			return;
		}
		int[] rows = heartRows(player);
		float xLeft = g.guiWidth() / 2f - 91;
		float y = g.guiHeight() - 39 - (rows[0] - 1) * rows[1] - 10;
		int color = mod.armorColor.argb();
		if (bars()) {
			shownArmor = mod.animateBars.isOn() ? ease(shownArmor, armor, 10, dt) : armor;
			bar(g, xLeft, y + 1, shownArmor / 20f, color, false, String.valueOf(armor));
		} else {
			for (int i = 0; i < 10; i++) {
				float xo = xLeft + i * 8;
				square(g, xo, y, 2, mod.background.argb());
				if (i * 2 + 1 < armor) {
					square(g, xo, y, 2, color);
				} else if (i * 2 + 1 == armor) {
					square(g, xo, y, 1, color);
				}
			}
		}
	}

	public void food(GuiGraphicsExtractor g) {
		tickClock();
		Player player = player();
		if (player == null) {
			return;
		}
		int food = player.getFoodData().getFoodLevel();
		float saturation = player.getFoodData().getSaturationLevel();
		float xRight = g.guiWidth() / 2f + 91;
		float y = g.guiHeight() - 39;
		int color = player.hasEffect(MobEffects.HUNGER) ? 0xFF8FB04A : mod.hungerColor.argb();
		if (bars()) {
			shownFood = mod.animateBars.isOn() ? ease(shownFood, food, 10, dt) : food;
			bar(g, xRight - ROW_W, y + 1, shownFood / 20f, color, true, String.valueOf(food));
			if (saturation > 0 && dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Saturation.class).isEnabled()) {
				float sw = ROW_W * Math.min(saturation, 20) / 20f;
				Shapes.rect(g, xRight - sw, y + 1 + BAR_H - 1.5f, sw, 1.5f, 0.75f, Colors.lerp(color, 0xFFFFFFFF, 0.55f));
			}
		} else {
			for (int i = 0; i < 10; i++) {
				float xo = xRight - i * 8 - 9;
				square(g, xo, y, 2, mod.background.argb());
				if (i * 2 + 1 < food) {
					square(g, xo, y, 2, color);
				} else if (i * 2 + 1 == food) {
					squareRight(g, xo, y, 1, color);
				}
			}
		}
	}

	public void air(GuiGraphicsExtractor g) {
		Player player = player();
		if (player == null) {
			return;
		}
		int maxAir = player.getMaxAirSupply();
		int air = Math.clamp(player.getAirSupply(), 0, maxAir);
		boolean underWater = player.isEyeInFluid(FluidTags.WATER);
		if (!underWater && air >= maxAir) {
			shownAir = Float.NaN;
			return;
		}
		float xRight = g.guiWidth() / 2f + 91;
		float y = g.guiHeight() - 49 - (vehicleHeartRows(player) - 1) * 10f;
		if (vehicleHeartRows(player) > 0) {
			y += 10; // no food row while riding something with hearts
		}
		int color = mod.airColor.argb();
		float fraction = (float) air / maxAir;
		if (bars()) {
			shownAir = mod.animateBars.isOn() ? ease(shownAir, fraction, 12, dt) : fraction;
			bar(g, xRight - ROW_W, y + 1, shownAir, color, true, null);
		} else {
			int bubbles = Mth.ceil(fraction * 10);
			for (int i = 0; i < 10; i++) {
				float xo = xRight - i * 8 - 9;
				if (i < bubbles) {
					Shapes.circle(g, xo + 4.5f, y + 4.5f, 3.5f, color);
				} else {
					Shapes.circle(g, xo + 4.5f, y + 4.5f, 3.5f, mod.background.argb());
				}
			}
		}
	}

	private static int vehicleHeartRows(Player player) {
		Entity vehicle = player.getVehicle();
		if (vehicle instanceof LivingEntity living && living.showVehicleHealth()) {
			int hearts = Math.min(30, (int) (living.getMaxHealth() + 0.5f) / 2);
			return (int) Math.ceil(hearts / 10.0);
		}
		return 0;
	}

	/** A 81-wide status bar; {@code fromRight} fills from the right edge like vanilla's hunger icons. */
	private void bar(GuiGraphicsExtractor g, float x, float y, float fraction, int color, boolean fromRight, String number) {
		float r = radius(BAR_H / 2);
		Shapes.rect(g, x, y, ROW_W, BAR_H, r, mod.background.argb());
		float fw = ROW_W * Math.clamp(fraction, 0, 1);
		if (fw > 0.05f) {
			Shapes.rect(g, fromRight ? x + ROW_W - fw : x, y, fw, BAR_H, Math.min(r, fw / 2), color);
		}
		if (number != null && mod.numbers.isOn()) {
			if (fromRight) {
				Fonts.draw(g, Typeface.MONO_BOLD, number, x + ROW_W - 3, y + BAR_H / 2, 5.5f, 0xFFFFFFFF, Fonts.Align.RIGHT, 0, true);
			} else {
				Fonts.draw(g, Typeface.MONO_BOLD, number, x + 3, y + BAR_H / 2, 5.5f, 0xFFFFFFFF, Fonts.Align.LEFT, 0, true);
			}
		}
	}

	// ---- XP ----

	/** Replaces the experience bar's background and progress (called from the experience bar mixin). */
	public void xpBar(GuiGraphicsExtractor g) {
		tickClock();
		if (mod.xpStyle.get() == Hotbar.XpStyle.HIDDEN) {
			return;
		}
		Player player = player();
		if (player == null || player.getXpNeededForNextLevel() <= 0) {
			return;
		}
		float progress = player.experienceProgress;
		shownXp = mod.animateBars.isOn() && !Float.isNaN(shownXp) && progress >= shownXp ? ease(shownXp, progress, 8, dt) : progress;
		float x = g.guiWidth() / 2f - 91;
		float y = g.guiHeight() - 29 + 1;
		Shapes.rect(g, x, y, 182, 3, 1.5f, mod.background.argb());
		float fw = 182 * Math.clamp(shownXp, 0, 1);
		if (fw > 0.05f) {
			Shapes.rect(g, x, y, fw, 3, Math.min(1.5f, fw / 2), mod.xpColor.argb());
		}
	}

	public void xpLevel(GuiGraphicsExtractor g) {
		if (mod.xpStyle.get() == Hotbar.XpStyle.HIDDEN) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || !mc.gameMode.hasExperience() || mc.player.experienceLevel <= 0) {
			return;
		}
		String text = String.valueOf(mc.player.experienceLevel);
		float cy = g.guiHeight() - 33;
		float tw = Fonts.width(Typeface.MONO_BOLD, text, 7) + 8;
		float x = g.guiWidth() / 2f - tw / 2;
		Shapes.rect(g, x, cy - 4.5f, tw, 9, 4.5f, mod.background.argb());
		Fonts.draw(g, Typeface.MONO_BOLD, text, g.guiWidth() / 2f, cy, 7, mod.xpColor.argb(), Fonts.Align.CENTER, 0, false);
	}
}
