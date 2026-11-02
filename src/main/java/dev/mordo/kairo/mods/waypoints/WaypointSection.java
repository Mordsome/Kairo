package dev.mordo.kairo.mods.waypoints;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.TextField;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.gui.widgets.WidgetHost;
import dev.mordo.kairo.mods.Waypoints;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Waypoint manager on the Waypoints settings page: add, rename, recolour, hide and delete, sorted by distance. */
public final class WaypointSection implements SettingsList.Section {
	private static final float ROW = 46;

	private final Waypoints module;
	private final Button add;
	private final Button open;
	private final Map<Waypoint, Row> rows = new IdentityHashMap<>();
	private final List<Waypoint> sorted = new ArrayList<>();

	private final class Row {
		final TextField name;
		final ColorDot dot;
		final Button visibility;
		final Button delete;

		Row(Waypoint w) {
			name = new TextField("Name", null).maxLength(32).onChange(text -> {
				w.name = text.isBlank() ? "Waypoint" : text;
				module.store().markDirty();
			});
			name.setText(w.name);
			dot = new ColorDot(w);
			visibility = new Button("", Icon.EYE, Button.Style.GHOST, () -> {
				w.visible = !w.visible;
				module.store().markDirty();
			});
			delete = new Button("", Icon.TRASH, Button.Style.GHOST, () -> {
				module.store().remove(w);
				rows.remove(w);
			});
		}
	}

	public WaypointSection(Waypoints module, WidgetHost host) {
		this.module = module;
		this.add = new Button("Add at my position", Icon.PLUS, Button.Style.PRIMARY, module::createAtPlayer).textSize(11.5f);
		this.open = new Button("Open waypoints", Icon.PIN, Button.Style.SUBTLE, () -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new WaypointsScreen(mc.gui.screen()));
		}).textSize(11.5f);
	}

	@Override
	public float render(GuiGraphicsExtractor g, float x, float y, float w, float mx, float my, float fade, Consumer<Widget> put) {
		float top = y;
		Fonts.draw(g, Typeface.MONO_BOLD, "WAYPOINTS", x, y + 8, 8.5f, Theme.text(0.45f, fade), Fonts.Align.LEFT, 2f, false);
		float addW = add.preferredWidth();
		add.bounds(x + w - addW, y - 4, addW, 30);
		put.accept(add);
		float openW = open.preferredWidth();
		open.bounds(add.x - 8 - openW, y - 4, openW, 30);
		put.accept(open);
		y += 34;

		LocalPlayer player = Minecraft.getInstance().player;
		module.store().sync();
		sorted.clear();
		sorted.addAll(module.store().all());
		if (player != null) {
			sorted.sort(Comparator.comparingDouble(wp -> wp.distanceTo(player.getX(), player.getY(), player.getZ())));
		}
		if (sorted.isEmpty()) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, player == null ? "Join a world to manage its waypoints." : "No waypoints in this world yet.",
				x, y + 12, 11.5f, Theme.text(0.5f, fade));
			return y + 26 - top;
		}
		String here = player == null ? "" : Waypoints.dimensionOf(player);
		for (Waypoint wp : sorted) {
			Row row = rows.computeIfAbsent(wp, Row::new);
			drawRow(g, wp, row, here, player, x, y, w, fade, put);
			y += ROW + 6;
		}
		return y - top;
	}

	private void drawRow(GuiGraphicsExtractor g, Waypoint wp, Row row, String here, LocalPlayer player,
						 float x, float y, float w, float fade, Consumer<Widget> put) {
		Shapes.rect(g, x, y, w, ROW, 12, Colors.multiplyAlpha(Theme.SURFACE, fade), Colors.multiplyAlpha(Theme.BORDER, fade), 1);
		row.dot.bounds(x + 10, y + ROW / 2 - 9, 18, 18);
		put.accept(row.dot);
		row.name.bounds(x + 36, y + 8, 170, 30);
		put.accept(row.name);

		boolean sameDimension = wp.dimension.equals(here);
		String coords = wp.x + ", " + wp.y + ", " + wp.z;
		Fonts.draw(g, Typeface.MONO_MEDIUM, coords, x + 220, y + ROW / 2 - 7, 10, Theme.text(0.85f, fade));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, dimensionLabel(wp) + otherDimension(wp), x + 220, y + ROW / 2 + 8, 9.5f, Theme.text(0.5f, fade));

		row.delete.bounds(x + w - 8 - 30, y + ROW / 2 - 15, 30, 30);
		row.visibility.bounds(row.delete.x - 4 - 30, y + ROW / 2 - 15, 30, 30);
		put.accept(row.visibility);
		put.accept(row.delete);
		if (!wp.visible) {
			Shapes.line(g, row.visibility.x + 8, row.visibility.y + 8, row.visibility.x + 22, row.visibility.y + 22, 1.6f, Theme.text(0.8f, fade));
		}
		String distance = player != null && sameDimension ? Math.round(wp.distanceTo(player.getX(), player.getY(), player.getZ())) + "m" : "—";
		Fonts.draw(g, Typeface.MONO_BOLD, distance, row.visibility.x - 12, y + ROW / 2, 10.5f,
			wp.death ? Colors.multiplyAlpha(0xFFFF7A7A, fade) : Colors.multiplyAlpha(Theme.accent(), fade), Fonts.Align.RIGHT, 0.4f, false);
	}

	private static String dimensionLabel(Waypoint wp) {
		return switch (wp.dimension) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> wp.dimension;
		} + (wp.death ? " · death point" : "");
	}

	/** The matching Nether / Overworld position (x and z divided or multiplied by 8). */
	private static String otherDimension(Waypoint wp) {
		return switch (wp.dimension) {
			case "minecraft:overworld" -> "  ·  Nether " + Math.floorDiv(wp.x, 8) + ", " + Math.floorDiv(wp.z, 8);
			case "minecraft:the_nether" -> "  ·  Overworld " + wp.x * 8 + ", " + wp.z * 8;
			default -> "";
		};
	}

	/** Colour swatch that cycles through the palette on click. */
	private final class ColorDot extends Widget {
		private final Waypoint waypoint;

		ColorDot(Waypoint waypoint) {
			this.waypoint = waypoint;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			float r = 7 + hover.value();
			Shapes.circle(g, x + w / 2, y + h / 2, r, Colors.multiplyAlpha(waypoint.color, fade), Theme.text(0.4f + 0.4f * hover.value(), fade), 1.2f);
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			int index = 0;
			for (int i = 0; i < Waypoints.PALETTE.length; i++) {
				if (Waypoints.PALETTE[i] == waypoint.color) {
					index = i + 1;
				}
			}
			waypoint.color = Waypoints.PALETTE[index % Waypoints.PALETTE.length];
			module.store().markDirty();
			return true;
		}
	}
}
