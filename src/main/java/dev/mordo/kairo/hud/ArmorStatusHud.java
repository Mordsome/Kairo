package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Held item and armour with durability, plus counters for items in your own inventory. */
public final class ArmorStatusHud extends HudElement {
	public enum Orientation implements EnumSetting.Labeled {
		VERTICAL("Vertical"),
		HORIZONTAL("Horizontal");

		private final String label;

		Orientation(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum DurabilityFormat implements EnumSetting.Labeled {
		NUMBER("Number"),
		PERCENT("Percent"),
		NONE("Hidden");

		private final String label;

		DurabilityFormat(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Orientation> orientation = add(new EnumSetting<>("orientation", "Layout", Orientation.VERTICAL));
	public final EnumSetting<DurabilityFormat> durability = add(new EnumSetting<>("durability", "Durability", DurabilityFormat.NUMBER));
	public final BooleanSetting showHeld = add(new BooleanSetting("show_held", "Held item", true));
	public final BooleanSetting lowPulse = add(new BooleanSetting("low_pulse", "Low durability pulse", true));
	public final SliderSetting lowThreshold = add(new SliderSetting("low_threshold", "Low below", 0.15, 0.05, 0.5, 0.01).percent()
		.visibleWhen(lowPulse::isOn));
	public final BooleanSetting countTotems = add(new BooleanSetting("count_totems", "Totem counter", true));
	public final BooleanSetting countCrystals = add(new BooleanSetting("count_crystals", "End crystal counter", false));
	public final BooleanSetting countObsidian = add(new BooleanSetting("count_obsidian", "Obsidian counter", false));
	public final BooleanSetting countPearls = add(new BooleanSetting("count_pearls", "Ender pearl counter", false));
	public final BooleanSetting countArrows = add(new BooleanSetting("count_arrows", "Arrow counter", false));
	public final BooleanSetting highlightTotems = add(new BooleanSetting("highlight_totems", "Highlight totem count", false)
		.<BooleanSetting>pvpDefault(true));

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int MAX_ROWS = 10;
	private static final float ICON = 16;
	private static final float GAP = 2;

	/** Rows built in layout(): an item to draw, its text, colour and whether it pulses or is highlighted. */
	private final ItemStack[] stacks = new ItemStack[MAX_ROWS];
	private final String[] texts = new String[MAX_ROWS];
	private final int[] colors = new int[MAX_ROWS];
	private final boolean[] pulses = new boolean[MAX_ROWS];
	private final boolean[] highlights = new boolean[MAX_ROWS];
	private int rows;
	private float textWidth;

	/** Sample items for previews outside a world (created on first use, after registries are ready). */
	private ItemStack[] sample;

	private ItemStack[] sample() {
		if (sample == null) {
			sample = new ItemStack[]{new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.NETHERITE_HELMET),
				new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS)};
		}
		return sample;
	}

	public ArmorStatusHud() {
		super("armor_status", "Armor Status", "Durability and totem count", Icon.SHIELD, true, Anchor.BOTTOM_RIGHT, -0.004, -0.01);
		enabled.pvpDefault(true); // always visible in PvP mode
	}

	@Override
	protected void layout(boolean preview) {
		rows = 0;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			for (ItemStack stack : sample()) {
				addEquipment(stack);
			}
		} else {
			if (showHeld.isOn()) {
				addEquipment(player.getMainHandItem());
			}
			for (EquipmentSlot slot : ARMOR) {
				addEquipment(player.getItemBySlot(slot));
			}
			Inventory inventory = player.getInventory();
			if (countTotems.isOn()) {
				addCounter(inventory, Items.TOTEM_OF_UNDYING, highlightTotems.isOn());
			}
			if (countCrystals.isOn()) {
				addCounter(inventory, Items.END_CRYSTAL, false);
			}
			if (countObsidian.isOn()) {
				addCounter(inventory, Items.OBSIDIAN, false);
			}
			if (countPearls.isOn()) {
				addCounter(inventory, Items.ENDER_PEARL, false);
			}
			if (countArrows.isOn()) {
				addCounter(inventory, Items.ARROW, false);
			}
		}
		textWidth = 0;
		for (int i = 0; i < rows; i++) {
			textWidth = Math.max(textWidth, HudText.width(texts[i]));
		}
		float cell = ICON + (textWidth > 0 ? 3 + textWidth : 0);
		if (orientation.get() == Orientation.VERTICAL) {
			width = cell + pad() * 2;
			height = rows == 0 ? ICON + pad() * 2 : rows * ICON + (rows - 1) * GAP + pad() * 2;
		} else {
			width = rows == 0 ? ICON + pad() * 2 : rows * cell + (rows - 1) * (GAP + 4) + pad() * 2;
			height = ICON + pad() * 2;
		}
	}

	private void addEquipment(ItemStack stack) {
		if (stack.isEmpty() || rows >= MAX_ROWS) {
			return;
		}
		String text = "";
		int color = textColor.argb();
		boolean pulse = false;
		if (stack.isDamageableItem() && durability.get() != DurabilityFormat.NONE) {
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			float fraction = max == 0 ? 1 : left / (float) max;
			text = durability.get() == DurabilityFormat.PERCENT ? Math.round(fraction * 100) + "%" : Integer.toString(left);
			color = durabilityColor(fraction);
			pulse = lowPulse.isOn() && fraction < lowThreshold.value();
		} else if (stack.getCount() > 1) {
			text = Integer.toString(stack.getCount());
		}
		put(stack, text, color, pulse, false);
	}

	private void addCounter(Inventory inventory, Item item, boolean highlight) {
		int count = 0;
		ItemStack first = ItemStack.EMPTY;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() == item) {
				count += stack.getCount();
				if (first.isEmpty()) {
					first = stack;
				}
			}
		}
		if (count == 0 || rows >= MAX_ROWS) {
			return;
		}
		put(first, Integer.toString(count), highlight ? ClientSettings.INSTANCE.pvpAccent.argb() : valueColor.argb(), false, highlight);
	}

	private void put(ItemStack stack, String text, int color, boolean pulse, boolean highlight) {
		stacks[rows] = stack;
		texts[rows] = text;
		colors[rows] = color;
		pulses[rows] = pulse;
		highlights[rows] = highlight;
		rows++;
	}

	/** Green when full, through yellow, to red when nearly broken. */
	private static int durabilityColor(float fraction) {
		return fraction > 0.5f
			? Colors.lerp(0xFFFFD45C, 0xFF55FF7F, (fraction - 0.5f) * 2)
			: Colors.lerp(0xFFFF5555, 0xFFFFD45C, fraction * 2);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		drawBackground(g);
		boolean vertical = orientation.get() == Orientation.VERTICAL;
		float cell = ICON + (textWidth > 0 ? 3 + textWidth : 0);
		float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1.5e8);
		for (int i = 0; i < rows; i++) {
			float x = pad() + (vertical ? 0 : i * (cell + GAP + 4));
			float y = pad() + (vertical ? i * (ICON + GAP) : 0);
			if (highlights[i]) {
				Shapes.rect(g, x - 1, y - 1, cell + 2, ICON + 2, 3, Colors.withAlpha(colors[i], 0.22f));
			}
			g.item(stacks[i], (int) x, (int) y);
			if (!texts[i].isEmpty()) {
				int color = pulses[i] ? Colors.multiplyAlpha(colors[i], pulse) : colors[i];
				HudText.draw(g, texts[i], x + ICON + 3, y + (ICON - 8) / 2, color, shadow());
			}
		}
	}
}
