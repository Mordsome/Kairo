package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Your own position: XYZ, chunk, biome and facing. */
public final class CoordinatesHud extends HudElement {
	public final SliderSetting decimals = add(new SliderSetting("decimals", "Decimals", 0, 0, 3, 1));
	public final BooleanSetting showChunk = add(new BooleanSetting("show_chunk", "Chunk info", true));
	public final BooleanSetting showBiome = add(new BooleanSetting("show_biome", "Biome", true));
	public final BooleanSetting showFacing = add(new BooleanSetting("show_facing", "Facing", true));
	public final BooleanSetting netherCoords = add(new BooleanSetting("nether_coords", "Nether / Overworld coords", false)
		.description("Also show the matching position in the other dimension"));
	public final BooleanSetting compact = add(new BooleanSetting("compact", "Compact", false)
		.description("One line: X Y Z only"));

	private static final int MAX_LINES = 8;
	private final String[] labels = new String[MAX_LINES];
	private final String[] values = new String[MAX_LINES];
	private int lines;

	public CoordinatesHud() {
		super("coordinates", "Coordinates", "XYZ, chunk, biome, facing", Icon.COMPASS, true, Anchor.MIDDLE_LEFT, 0.004, 0.12);
	}

	private String number(double v) {
		int d = decimals.intValue();
		return d == 0 ? Long.toString((long) Math.floor(v)) : String.format(Locale.ROOT, "%." + d + "f", v);
	}

	private void line(String label, String value) {
		labels[lines] = label;
		values[lines] = value;
		lines++;
	}

	/** What the text was built from last time; the strings are only rebuilt when this changes. */
	private long lastKey = Long.MIN_VALUE;
	private final java.util.Map<Object, String> biomeNames = new java.util.HashMap<>();

	@Override
	protected void layout(boolean preview) {
		LocalPlayer player = Minecraft.getInstance().player;
		double x = player == null ? 128.5 : player.getX();
		double y = player == null ? 64 : player.getY();
		double z = player == null ? -42.3 : player.getZ();
		// Rebuilding text every frame allocates; skip it while nothing visible changed.
		double scale = Math.pow(10, decimals.intValue());
		long key = 17;
		key = key * 31 + (long) Math.floor(x * scale);
		key = key * 31 + (long) Math.floor(y * scale);
		key = key * 31 + (long) Math.floor(z * scale);
		key = key * 31 + (player == null ? 0 : player.getDirection().ordinal());
		key = key * 31 + (player == null ? 0 : player.level().dimension().hashCode());
		key = key * 31 + (decimals.intValue() | (compact.isOn() ? 8 : 0) | (netherCoords.isOn() ? 16 : 0)
			| (showChunk.isOn() ? 32 : 0) | (showBiome.isOn() ? 64 : 0) | (showFacing.isOn() ? 128 : 0));
		key = key * 31 + (long) HudText.width("X");
		if (key == lastKey && lines > 0) {
			return;
		}
		lastKey = key;
		lines = 0;
		if (compact.isOn()) {
			line("", number(x) + ", " + number(y) + ", " + number(z));
		} else {
			line("X: ", number(x));
			line("Y: ", number(y));
			line("Z: ", number(z));
		}
		if (netherCoords.isOn() && player != null) {
			boolean inNether = player.level().dimension() == Level.NETHER;
			double factor = inNether ? 8 : 1 / 8.0;
			line(inNether ? "Overworld: " : "Nether: ", number(x * factor) + ", " + number(z * factor));
		}
		if (!compact.isOn()) {
			BlockPos pos = player == null ? BlockPos.ZERO : player.blockPosition();
			if (showChunk.isOn()) {
				line("Chunk: ", (pos.getX() & 15) + " " + (pos.getZ() & 15) + " in " + (pos.getX() >> 4) + " " + (pos.getZ() >> 4));
			}
			if (showBiome.isOn()) {
				line("Biome: ", player == null ? "Plains" : biomeNames.computeIfAbsent(player.level().getBiome(pos).unwrapKey().orElse(null),
					k -> biomeName(player, pos)));
			}
			if (showFacing.isOn()) {
				line("Facing: ", player == null ? "S (+Z)" : facing(player.getDirection()));
			}
		}
		float maxW = 0;
		for (int i = 0; i < lines; i++) {
			maxW = Math.max(maxW, HudText.width(labels[i]) + HudText.width(values[i]));
		}
		width = maxW + pad() * 2;
		height = lines * (HudText.LINE + 1) - 1 + pad() * 2;
	}

	private static String biomeName(LocalPlayer player, BlockPos pos) {
		return player.level().getBiome(pos).unwrapKey()
			.map(key -> titleCase(key.identifier().getPath()))
			.orElse("Unknown");
	}

	private static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	private static String facing(Direction direction) {
		String axis = switch (direction) {
			case NORTH -> "-Z";
			case SOUTH -> "+Z";
			case WEST -> "-X";
			case EAST -> "+X";
			default -> "";
		};
		return direction.getName().substring(0, 1).toUpperCase(Locale.ROOT) + " (" + axis + ")";
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		drawBackground(g);
		for (int i = 0; i < lines; i++) {
			drawPair(g, labels[i], values[i], pad(), pad() + i * (HudText.LINE + 1), valueColor.argb());
		}
	}
}
