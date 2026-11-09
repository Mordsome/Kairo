package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.NametagStyle;
import dev.mordo.kairo.cosmetics.Geo;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Kairo Void nametags. Minecraft decides whether a name shows and whether it shows through walls, exactly as without
 * Kairo; this only restyles it: the text's colours, and light drawn around it (an underline, glowing bars, brackets
 * or a scan line) that never shows through blocks. Players only; other mobs keep vanilla tags.
 */
public final class VoidNametags {
	private VoidNametags() {
	}

	private static NametagStyle style(EntityRenderState state) {
		if (!Visuals.on() || !(state instanceof AvatarRenderState)) {
			return NametagStyle.VANILLA;
		}
		return VoidSettings.INSTANCE.nametags.get();
	}

	/** In place of vanilla's submitNameTag call: same arguments, restyled. */
	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 attachment, int offsetY, Component text, boolean seeThrough,
							  int light, CameraRenderState camera, EntityRenderState state) {
		NametagStyle style = style(state);
		if (style == NametagStyle.VANILLA) {
			collector.submitNameTag(poseStack, attachment, offsetY, text, seeThrough, light, camera);
			return;
		}
		float time = System.nanoTime() / 1e9f;
		int accent = Theme.accent() | 0xFF000000;
		Component shown = switch (style) {
			// Text colours stay light so the name always reads well on the dark tag background.
			case NEON -> gradient(text, Colors.lerp(accent, 0xFFFFFFFF, 0.55f), Colors.lerp(accent, 0xFFFFFFFF, 0.88f), time, 0.6f);
			case HOLOGRAM -> gradient(text, 0xFFA8F4FF, 0xFFF2FFFF, time, 0.25f);
			case SHIMMER -> shimmer(text, accent, time);
			default -> text;
		};
		collector.submitNameTag(poseStack, attachment, offsetY, shown, seeThrough, light, camera);
		if (offsetY != 0) {
			return; // the light goes with the main name line only
		}
		// Light round the tag, in the tag's own space (one unit = one text pixel), never through walls.
		float width = Minecraft.getInstance().font.width(shown);
		poseStack.pushPose();
		poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(0.025f, -0.025f, 0.025f);
		float half = width / 2f + 2;
		collector.submitCustomGeometry(poseStack, Geo.glowType(), (pose, b) -> {
			switch (style) {
				case GLASS -> {
					// A thin line of your colour under the name, brightest in the middle.
					GlowGeo.quad(pose, b, new Vector3f(-half, 9.4f, -0.01f), new Vector3f(0, 9.4f, -0.01f), new Vector3f(0, 10.5f, -0.01f),
						new Vector3f(-half, 10.5f, -0.01f), Colors.withAlpha(accent, 0), Colors.withAlpha(accent, 0.95f), Colors.withAlpha(accent, 0.95f),
						Colors.withAlpha(accent, 0));
					GlowGeo.quad(pose, b, new Vector3f(0, 9.4f, -0.01f), new Vector3f(half, 9.4f, -0.01f), new Vector3f(half, 10.5f, -0.01f),
						new Vector3f(0, 10.5f, -0.01f), Colors.withAlpha(accent, 0.95f), Colors.withAlpha(accent, 0), Colors.withAlpha(accent, 0),
						Colors.withAlpha(accent, 0.95f));
				}
				case NEON -> {
					int glow = Colors.withAlpha(accent, 0.75f);
					bar(pose, b, -half - 3, -3.2f, half + 3, 1.0f, glow);
					bar(pose, b, -half - 3, 10.2f, half + 3, 1.0f, glow);
					// Soft light above and below the bars, never over the letters.
					fade(pose, b, -half - 3, -6.2f, half + 3, -3.2f, 0, Colors.withAlpha(accent, 0.22f));
					fade(pose, b, -half - 3, 11.2f, half + 3, 14.2f, Colors.withAlpha(accent, 0.22f), 0);
				}
				case HOLOGRAM -> {
					int edge = Colors.withAlpha(Colors.lerp(accent, 0xFF8FFBFF, 0.5f), 0.9f);
					// Brackets at both ends.
					for (int s = -1; s <= 1; s += 2) {
						float x = s * (half + 2);
						bar(pose, b, Math.min(x, x - s * 0.9f), -2, Math.max(x, x - s * 0.9f), 13, edge);
						bar(pose, b, Math.min(x, x - s * 3.5f), -2, Math.max(x, x - s * 3.5f), 0.9f, edge);
						bar(pose, b, Math.min(x, x - s * 3.5f), 10.1f, Math.max(x, x - s * 3.5f), 0.9f, edge);
					}
					// A light running along under the name (kept clear of the letters).
					float k = (time * 0.5f) % 1f;
					float run = -half + k * (half * 2);
					bar(pose, b, Math.max(-half, run - 6), 10.4f, Math.min(half, run + 6), 0.7f,
						Colors.withAlpha(edge, 0.8f * (float) Math.sin(k * Math.PI)));
				}
				case SHIMMER -> bar(pose, b, -half, 9.6f, half, 0.8f, Colors.withAlpha(accent, 0.6f));
				default -> {
				}
			}
		});
		poseStack.popPose();
	}

	/** A flat bar from (x0, y) to (x1, y + h), just in front of the text background. */
	private static void bar(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer b, float x0, float y, float x1, float h, int color) {
		if (Colors.alpha(color) == 0 || x1 <= x0) {
			return;
		}
		GlowGeo.quad(pose, b, new Vector3f(x0, y, -0.01f), new Vector3f(x1, y, -0.01f), new Vector3f(x1, y + h, -0.01f), new Vector3f(x0, y + h, -0.01f), color);
	}

	/** A bar fading from {@code top} at y0 to {@code bottom} at y1. */
	private static void fade(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer b, float x0, float y0, float x1, float y1, int top, int bottom) {
		GlowGeo.quad(pose, b, new Vector3f(x0, y0, -0.01f), new Vector3f(x1, y0, -0.01f), new Vector3f(x1, y1, -0.01f), new Vector3f(x0, y1, -0.01f),
			top, top, bottom, bottom);
	}

	/** The text with a colour flowing from {@code a} to {@code b} and back across it. */
	private static Component gradient(Component text, int a, int b, float time, float speed) {
		String s = text.getString();
		MutableComponent out = Component.empty();
		int n = Math.max(1, s.codePointCount(0, s.length()));
		int i = 0;
		for (int offset = 0; offset < s.length(); ) {
			int cp = s.codePointAt(offset);
			float k = 0.5f + 0.5f * (float) Math.sin(i / (float) n * Math.PI * 2 - time * speed * 4);
			out.append(Component.literal(new String(Character.toChars(cp))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Colors.lerp(a, b, k) & 0xFFFFFF))));
			offset += Character.charCount(cp);
			i++;
		}
		return out;
	}

	/** The text in its own colours with a bright sweep running across it. */
	private static Component shimmer(Component text, int accent, float time) {
		String s = text.getString();
		MutableComponent out = Component.empty();
		int n = Math.max(1, s.codePointCount(0, s.length()));
		float sweep = (time * 0.6f) % 1.6f - 0.3f;
		int i = 0;
		for (int offset = 0; offset < s.length(); ) {
			int cp = s.codePointAt(offset);
			float d = Math.abs(i / (float) n - sweep);
			float light = Math.max(0, 1 - d * 5);
			int color = Colors.lerp(Colors.lerp(0xFFFFFFFF, accent, 0.35f), 0xFFFFFFFF, light);
			out.append(Component.literal(new String(Character.toChars(cp))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF))));
			offset += Character.charCount(cp);
			i++;
		}
		return out;
	}
}
