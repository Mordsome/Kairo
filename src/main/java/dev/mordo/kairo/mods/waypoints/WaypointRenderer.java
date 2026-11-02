package dev.mordo.kairo.mods.waypoints;

import dev.mordo.kairo.mods.Waypoints;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws the player's waypoints as screen-space labels (with an optional beam), clamped to the edge when off screen. */
public final class WaypointRenderer {
	private static final double NEAR = 0.1;
	private static final double BEAM_HEIGHT = 96;
	private final Waypoints module;
	private final List<Waypoint> sorted = new ArrayList<>();

	public WaypointRenderer(Waypoints module) {
		this.module = module;
	}

	public void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.hud.isHidden() || mc.level == null) {
			return;
		}
		String dimension = Waypoints.dimensionOf(player);
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		sorted.clear();
		for (Waypoint w : module.store().all()) {
			if (w.visible && w.dimension.equals(dimension)) {
				sorted.add(w);
			}
		}
		// Far first, so nearer labels draw on top.
		sorted.sort(Comparator.comparingDouble((Waypoint w) -> -w.distanceTo(eye.x, eye.y, eye.z)));
		for (Waypoint w : sorted) {
			draw(g, mc, w, w.distanceTo(eye.x, eye.y, eye.z));
		}
	}

	private void draw(GuiGraphicsExtractor g, Minecraft mc, Waypoint w, double distance) {
		float width = g.guiWidth();
		float height = g.guiHeight();
		float alpha = module.fadeWithDistance.isOn() ? (float) Math.clamp(1 - (distance - 48) / 1200, 0.35, 1) : 1;
		var camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vector3fc forward = camera.forwardVector();
		Vec3 base = new Vec3(w.x + 0.5, w.y, w.z + 0.5);
		Vec3 labelPos = base.add(0, 1.6, 0);
		// Depth along the view direction; the projected z can't be trusted for points behind the camera.
		boolean behind = depth(labelPos, eye, forward) < NEAR;
		Vec3 ndc = behind ? Vec3.ZERO : mc.gameRenderer.projectPointToScreen(labelPos);
		boolean onScreen = !behind && Math.abs(ndc.x) <= 1 && Math.abs(ndc.y) <= 1;

		if (module.showBeam.isOn()) {
			drawBeam(g, mc, base, eye, forward, w.color, alpha, width, height);
		}

		float sx;
		float sy;
		boolean clamped = false;
		if (onScreen) {
			sx = toScreenX(ndc.x, width);
			sy = toScreenY(ndc.y, height);
		} else if (module.clampToEdge.isOn()) {
			double dx;
			double dy;
			if (behind) {
				// Point the label the way the player would turn: sideways relative to the camera's yaw.
				Vec3 rel = labelPos.subtract(eye);
				double yaw = Math.toRadians(camera.yRot());
				double leftAmount = rel.x * Math.cos(yaw) + rel.z * Math.sin(yaw);
				dx = -leftAmount;
				dy = rel.y / Math.max(1, Math.abs(leftAmount)) * 0.25;
				if (Math.abs(dx) < 1e-3) {
					dx = 1e-3; // straight behind: pick a side instead of the bottom
				}
			} else {
				dx = ndc.x;
				dy = ndc.y;
			}
			double scale = 0.9 / Math.max(1e-4, Math.max(Math.abs(dx), Math.abs(dy)));
			sx = toScreenX(dx * scale, width);
			// Keep edge labels above the hotbar.
			sy = toScreenY(Math.max(-0.7, dy * scale), height);
			clamped = true;
		} else {
			return;
		}
		label(g, w, distance, sx, sy, alpha, clamped, (float) Math.atan2(-(sy - height / 2), sx - width / 2));
	}

	private static double depth(Vec3 point, Vec3 eye, Vector3fc forward) {
		return (point.x - eye.x) * forward.x() + (point.y - eye.y) * forward.y() + (point.z - eye.z) * forward.z();
	}

	/** Vertical beam from the waypoint up, clipped to the part in front of the camera. */
	private static void drawBeam(GuiGraphicsExtractor g, Minecraft mc, Vec3 base, Vec3 eye, Vector3fc forward, int color, float alpha, float width, float height) {
		double d0 = depth(base, eye, forward);
		double slope = forward.y(); // depth change per block of height
		double from = 0;
		double to = BEAM_HEIGHT;
		if (Math.abs(slope) < 1e-6) {
			if (d0 < NEAR) {
				return;
			}
		} else {
			double cross = (NEAR - d0) / slope; // height where the beam crosses the near plane
			if (slope > 0) {
				from = Math.max(from, cross);
			} else {
				to = Math.min(to, cross);
			}
		}
		if (to - from < 0.01) {
			return;
		}
		Vec3 bottom = mc.gameRenderer.projectPointToScreen(base.add(0, from, 0));
		Vec3 top = mc.gameRenderer.projectPointToScreen(base.add(0, to, 0));
		float x1 = toScreenX(bottom.x, width);
		float y1 = toScreenY(bottom.y, height);
		float x2 = toScreenX(top.x, width);
		float y2 = toScreenY(top.y, height);
		Shapes.line(g, x1, y1, x2, y2, 5, Colors.withAlpha(color, 0.12f * alpha));
		Shapes.line(g, x1, y1, x2, y2, 1.5f, Colors.withAlpha(color, 0.55f * alpha));
	}

	private static float toScreenX(double ndcX, float width) {
		return (float) ((ndcX * 0.5 + 0.5) * width);
	}

	private static float toScreenY(double ndcY, float height) {
		return (float) ((1 - (ndcY * 0.5 + 0.5)) * height);
	}

	private void label(GuiGraphicsExtractor g, Waypoint w, double distance, float cx, float cy, float alpha, boolean clamped, float edgeAngle) {
		float s = module.labelScale.floatValue();
		String name = w.name;
		String dist = module.showDistance.isOn() ? Math.round(distance) + "m" : "";
		float size = 7.5f;
		float nameW = Fonts.width(Typeface.OUTFIT_SEMIBOLD, name, size);
		float distW = dist.isEmpty() ? 0 : Fonts.width(Typeface.MONO_MEDIUM, dist, size * 0.9f) + 5;
		float w2 = nameW + distW + 18;
		float h = 13;
		if (clamped) {
			// Keep the whole pill (plus its arrow) on screen.
			float marginX = (w2 / 2 + 12) * s;
			float marginY = (h / 2 + 12) * s;
			cx = Math.clamp(cx, marginX, Math.max(marginX, g.guiWidth() - marginX));
			cy = Math.clamp(cy, marginY, Math.max(marginY, g.guiHeight() - marginY));
		}
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(s, s);
		Shapes.rect(g, -w2 / 2, -h / 2, w2, h, h / 2, Colors.argb(0.62f * alpha, 0x0C1018), Colors.withAlpha(w.color, 0.45f * alpha), 0.75f);
		Shapes.circle(g, -w2 / 2 + 7, 0, 2.5f, Colors.withAlpha(w.color, alpha));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, name, -w2 / 2 + 13, 0, size, Colors.argb(alpha, 0xF4F8FB));
		if (!dist.isEmpty()) {
			Fonts.draw(g, Typeface.MONO_MEDIUM, dist, w2 / 2 - 5, 0, size * 0.9f, Colors.argb(0.7f * alpha, 0xF4F8FB), Fonts.Align.RIGHT, 0, false);
		}
		if (clamped) {
			// Small arrow pointing towards where the waypoint is.
			float ax = (float) Math.cos(edgeAngle) * (w2 / 2 + 7);
			float ay = (float) -Math.sin(edgeAngle) * (h / 2 + 7);
			Shapes.triangle(g, ax, ay, 6, (float) -edgeAngle, Colors.withAlpha(w.color, alpha));
		}
		g.pose().popMatrix();
	}
}
