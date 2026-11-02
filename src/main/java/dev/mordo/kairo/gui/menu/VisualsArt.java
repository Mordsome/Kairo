package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.GameVisuals.Filter;
import dev.mordo.kairo.core.GameVisuals.MiningStyle;
import dev.mordo.kairo.core.GameVisuals.SwingStyle;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.PickTile;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Tile pictures for the in-game sections of Visuals: motion paths, filter swatches and effect icons. */
final class VisualsArt {
	private VisualsArt() {
	}

	// ---- motion paths: a faint path and a comet moving along it ----

	private interface Path {
		/** Point at s (0..1) in -1..1 space, y down. */
		float[] at(float s);
	}

	private static float[] lerp(float ax, float ay, float bx, float by, float k) {
		return new float[]{ax + (bx - ax) * k, ay + (by - ay) * k};
	}

	private static float bump(float s) {
		return (float) Math.sin(s * Math.PI);
	}

	private static float tau(float s) {
		return (float) (s * Math.PI * 2);
	}

	static Path swingPath(SwingStyle style) {
		return switch (style) {
			case VANILLA -> s -> lerp(0.55f, -0.35f, -0.45f, 0.55f, bump(s));
			case SMOOTH -> s -> lerp(0.55f, -0.35f, -0.45f, 0.55f, bump(s * s * (3 - 2 * s)));
			case SLASH -> s -> {
				float a = (float) Math.toRadians(-15 + 210 * s);
				return new float[]{(float) Math.cos(a) * 0.8f, (float) Math.sin(a) * 0.3f + 0.1f};
			};
			case CHOP -> s -> {
				float a = (float) Math.toRadians(-160 + 200 * s);
				return new float[]{(float) Math.cos(a) * 0.35f + 0.1f, (float) Math.sin(a) * 0.75f};
			};
			case STAB -> s -> lerp(0.6f, 0.6f, -0.15f, -0.15f, bump(s));
			case SPIN -> s -> new float[]{(float) Math.cos(tau(s)) * 0.6f, (float) Math.sin(tau(s)) * 0.6f};
			case FLIP -> s -> new float[]{(float) Math.cos(tau(s)) * 0.22f, (float) Math.sin(tau(s)) * 0.7f};
			case SWIRL -> s -> new float[]{(float) Math.sin(tau(s)) * 0.75f, (float) Math.sin(tau(s) * 2) * 0.35f};
			case PUNCH -> s -> lerp(0.45f, 0.45f, 0.05f, 0.05f, bump(s));
			case SWEEP -> s -> {
				float a = (float) Math.toRadians(40 + 150 * s);
				return new float[]{(float) Math.cos(a) * 0.7f, (float) Math.sin(a) * 0.7f - 0.15f};
			};
		};
	}

	static Path miningPath(MiningStyle style) {
		return switch (style) {
			case VANILLA, SMOOTH -> s -> lerp(0.55f, -0.35f, -0.45f, 0.55f, bump(s));
			case SIDEWAYS -> s -> new float[]{(float) Math.sin(tau(s)) * 0.75f, 0.2f + (float) Math.cos(tau(s) * 2) * 0.08f};
			case HAMMER -> s -> {
				float k = s < 0.6f ? s / 0.6f : 1 - (s - 0.6f) / 0.4f;
				float drop = s < 0.45f ? -0.7f * (s / 0.45f) : -0.7f + 1.4f * Math.min(1, (s - 0.45f) / 0.13f);
				return new float[]{0.1f - 0.1f * k, Math.min(0.7f, drop * (1 - Math.max(0, s - 0.6f) / 0.4f))};
			};
			case DRILL -> s -> new float[]{(float) Math.cos(tau(s) * 4) * 0.12f, (float) Math.sin(tau(s) * 5) * 0.12f};
			case CIRCLE -> s -> new float[]{(float) Math.cos(tau(s)) * 0.55f, (float) Math.sin(tau(s)) * 0.4f};
			case SAW -> s -> lerp(0.5f, 0.5f, -0.3f, -0.3f, 0.5f + 0.5f * (float) Math.sin(tau(s)));
		};
	}

	private static PickTile.Art motion(Path path) {
		return (g, x, y, size, t, fade, on) -> {
			float cx = x + size / 2;
			float cy = y + size / 2;
			float r = size * 0.38f;
			int accent = Theme.accent();
			float[] prev = path.at(0);
			for (int i = 1; i <= 28; i++) {
				float[] p = path.at(i / 28f);
				Shapes.line(g, cx + prev[0] * r, cy + prev[1] * r, cx + p[0] * r, cy + p[1] * r, 1.4f, Theme.text(0.22f, fade));
				prev = p;
			}
			float head = t == 0 ? 0.3f : t;
			for (int i = 6; i >= 0; i--) {
				float s = head - i * 0.025f;
				if (s < 0) {
					s += 1;
				}
				float[] p = path.at(s);
				float k = 1 - i / 7f;
				Shapes.circle(g, cx + p[0] * r, cy + p[1] * r, 1.2f + 2.6f * k, Colors.multiplyAlpha(Colors.withAlpha(on || t > 0 ? accent : 0xFFFFFFFF, 0.25f + 0.75f * k), fade));
			}
		};
	}

	static List<PickTile> swingTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.swingStyle;
		for (SwingStyle style : SwingStyle.values()) {
			tiles.add(new PickTile(style.label(), motion(swingPath(style)), () -> setting.get() == style, () -> setting.set(style)));
		}
		return tiles;
	}

	static List<PickTile> inspectTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.inspectStyle;
		for (GameVisuals.InspectStyle style : GameVisuals.InspectStyle.values()) {
			Path path = switch (style) {
				case LOOK_OVER -> s -> new float[]{(float) Math.sin(tau(s)) * 0.65f, 0.15f - 0.1f * (float) Math.cos(tau(s) * 2)};
				case TWIRL -> s -> new float[]{(float) Math.cos(tau(s) * 2) * 0.45f, (float) Math.sin(tau(s) * 2) * 0.45f};
				case TOSS -> s -> new float[]{0.3f - 0.6f * s, 0.6f - 3.2f * s * (1 - s)};
				case FLOURISH -> s -> new float[]{(float) Math.sin(tau(s)) * 0.7f, (float) Math.sin(tau(s) * 2) * 0.35f};
			};
			tiles.add(new PickTile(style.label(), motion(path), () -> setting.get() == style, () -> setting.set(style)));
		}
		return tiles;
	}

	static List<PickTile> miningTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.miningStyle;
		for (MiningStyle style : MiningStyle.values()) {
			tiles.add(new PickTile(style.label(), motion(miningPath(style)), () -> setting.get() == style, () -> setting.set(style)));
		}
		return tiles;
	}

	// ---- filters: a tiny landscape in the filter's colours ----

	private static int[] filterColors(Filter f) {
		return switch (f) {
			case NONE -> new int[]{0xFF7FB8FF, 0xFFCDE6FF, 0xFF5FA040, 0xFFFFF4C0};
			case CINEMATIC -> new int[]{0xFF2F8C9C, 0xFFF0A060, 0xFF2E4A3A, 0xFFFFD08A};
			case WARM -> new int[]{0xFFF2B36B, 0xFFFFE0A8, 0xFF8A9A3A, 0xFFFFF0C0};
			case COLD -> new int[]{0xFF4A7CD0, 0xFFBFE0FF, 0xFF3F7A70, 0xFFEAF6FF};
			case VIBRANT -> new int[]{0xFF1E8BFF, 0xFF9BE7FF, 0xFF2FD02F, 0xFFFFF060};
			case DREAMY -> new int[]{0xFFF4B8D8, 0xFFFFF0F6, 0xFFB8D8A8, 0xFFFFFFFF};
			case NOIR -> new int[]{0xFF2A2A2A, 0xFFB0B0B0, 0xFF1A1A1A, 0xFFF0F0F0};
			case RETRO -> new int[]{0xFFB89A6A, 0xFFE8D3A8, 0xFF7A6A40, 0xFFFFF1D0};
			case NEON -> new int[]{0xFF1A0033, Theme.accent(), 0xFF00FFAA, 0xFFFFFFFF};
			case VAPORWAVE -> new int[]{0xFFFF71CE, 0xFF01CDFE, 0xFFB967FF, 0xFFFFF59E};
			case SUNSET -> new int[]{0xFFFF8C42, 0xFFFF4D6D, 0xFF3A1F4A, 0xFFFFE08A};
			case MATRIX -> new int[]{0xFF001A00, 0xFF00AA33, 0xFF003300, 0xFF7CFF9C};
		};
	}

	static List<PickTile> filterTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.filter;
		for (Filter f : Filter.values()) {
			tiles.add(new PickTile(f.label(), (g, x, y, size, t, fade, on) -> {
				int[] c = filterColors(f);
				float r = 9;
				Shapes.gradientRect(g, x, y, size, size, r, Colors.multiplyAlpha(c[0], fade), Colors.multiplyAlpha(c[1], fade));
				Shapes.circle(g, x + size * 0.68f, y + size * 0.4f, size * 0.13f, Colors.multiplyAlpha(c[3], fade));
				// Ground, drawn in thin rows that follow the tile's rounded corners so nothing pokes past the outline.
				float groundY = y + size * 0.68f;
				float bottom = y + size;
				int ground = Colors.multiplyAlpha(c[2], fade);
				for (float row = groundY; row < bottom; row += 0.5f) {
					float mid = row + 0.25f;
					float inset = 0;
					if (mid > bottom - r) {
						float d = mid - (bottom - r);
						inset = r - (float) Math.sqrt(Math.max(0, r * r - d * d));
					}
					Shapes.rect(g, x + inset, row, size - inset * 2, 0.5f, 0, ground);
				}
				Shapes.rect(g, x, y, size, size, r, 0, Colors.multiplyAlpha(0x33FFFFFF, fade), 1);
			}, () -> setting.get() == f, () -> setting.set(f)));
		}
		return tiles;
	}

	// ---- hit and kill effects ----

	static List<PickTile> hitTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.hitBurst;
		for (GameVisuals.HitBurst b : GameVisuals.HitBurst.values()) {
			Icon icon = switch (b) {
				case NONE -> Icon.CLOSE;
				case SPARKS, MAGIC -> Icon.SPARKLE;
				case CRITS -> Icon.SWORD;
				case HEARTS, BLOOD -> Icon.HEART;
				case STARS -> Icon.SUN;
				case FLAMES, SOUL -> Icon.FLAME;
			};
			int color = switch (b) {
				case NONE -> 0x99FFFFFF;
				case SPARKS -> 0xFFB8F0FF;
				case CRITS -> 0xFFE8E8E8;
				case MAGIC -> 0xFFC084FC;
				case HEARTS -> 0xFFFF5C7A;
				case STARS -> 0xFFFFF3B0;
				case FLAMES -> 0xFFFF9A3C;
				case SOUL -> 0xFF4FE3F0;
				case BLOOD -> 0xFFC81E2E;
			};
			tiles.add(PickTile.icon(b.label(), icon, color, () -> setting.get() == b, () -> setting.set(b)));
		}
		return tiles;
	}

	static List<PickTile> projectileTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.projectileTrail;
		for (GameVisuals.ProjectileTrail p : GameVisuals.ProjectileTrail.values()) {
			if (p == GameVisuals.ProjectileTrail.NONE) {
				tiles.add(PickTile.icon(p.label(), Icon.CLOSE, 0x99FFFFFF, () -> setting.get() == p, () -> setting.set(p)));
				continue;
			}
			// A little arrow with its trail behind it.
			tiles.add(new PickTile(p.label(), (g, x, y, s, t, fade, on) -> {
				float y0 = y + s * 0.62f;
				float y1 = y + s * 0.38f;
				for (int i = 0; i < 7; i++) {
					float k = i / 7f;
					float px = x + s * (0.12f + 0.5f * k);
					float py = y0 + (y1 - y0) * k + (float) Math.sin(t * 6 + i) * 1.2f;
					int c = switch (p) {
						case ACCENT -> Theme.accent();
						case RAINBOW -> 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((float) ((t * 0.3 + k) % 1.0), 0.8f, 1f);
						case FLAME -> 0xFFFF9A3C;
						case MAGIC -> 0xFFC084FC;
						case CLOUD -> 0xFFE6E6E6;
						default -> 0xFFB8F0FF;
					};
					Shapes.circle(g, px, py, 1.2f + 1.6f * k, Colors.multiplyAlpha(Colors.withAlpha(c, 0.25f + 0.75f * k), fade));
				}
				Shapes.line(g, x + s * 0.62f, y + s * 0.37f, x + s * 0.86f, y + s * 0.25f, 2.2f, Colors.multiplyAlpha(0xFFF4F8FB, fade));
			}, () -> setting.get() == p, () -> setting.set(p)));
		}
		return tiles;
	}

	static List<PickTile> soundPackTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var hits = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HitSounds.class);
		for (dev.mordo.kairo.mods.HitSounds.Pack p : dev.mordo.kairo.mods.HitSounds.Pack.values()) {
			int color = switch (p) {
				case NONE -> 0x99FFFFFF;
				case ARCADE -> 0xFFFFD84D;
				case CRYSTAL -> 0xFF8FFBFF;
				case HEAVY -> 0xFFFF7A4D;
				case VOID -> 0xFFC084FC;
				case BUBBLE -> 0xFF7FD8FF;
			};
			tiles.add(PickTile.icon(p.label(), p == dev.mordo.kairo.mods.HitSounds.Pack.NONE ? Icon.CLOSE : Icon.VOLUME, color,
				() -> hits.pack.get() == p, () -> hits.pack.set(p)));
		}
		return tiles;
	}

	static List<PickTile> killTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.killEffect;
		for (GameVisuals.KillEffect k : GameVisuals.KillEffect.values()) {
			if (k == GameVisuals.KillEffect.LIGHTNING) {
				tiles.add(new PickTile(k.label(), (g, x, y, s, t, fade, on) -> {
					int c = Colors.multiplyAlpha(0xFFFFF59E, fade);
					float cx = x + s / 2;
					float[][] pts = {{0.15f, -0.4f}, {-0.1f, 0.0f}, {0.1f, 0.0f}, {-0.15f, 0.42f}};
					for (int i = 0; i < pts.length - 1; i++) {
						Shapes.line(g, cx + pts[i][0] * s, y + s / 2 + pts[i][1] * s, cx + pts[i + 1][0] * s, y + s / 2 + pts[i + 1][1] * s, 2.4f, c);
					}
				}, () -> setting.get() == k, () -> setting.set(k)));
				continue;
			}
			if (k == GameVisuals.KillEffect.SHATTER) {
				// Little cubes flying apart.
				tiles.add(new PickTile(k.label(), (g, x, y, s, t, fade, on) -> {
					float cx = x + s / 2;
					float cy = y + s / 2;
					for (int i = 0; i < 9; i++) {
						double a = i * 0.7 + 0.4;
						float r = s * (0.12f + 0.05f * (i % 3)) + s * 0.08f * (float) (0.5 + 0.5 * Math.sin(t * 3 + i));
						float q = s * (0.07f + 0.02f * (i % 2));
						Shapes.rect(g, cx + (float) Math.cos(a) * r - q / 2, cy + (float) Math.sin(a) * r - q / 2, q, q, 1,
							Colors.multiplyAlpha(i % 3 == 0 ? 0xFFFFFFFF : Theme.accent(), fade));
					}
				}, () -> setting.get() == k, () -> setting.set(k)));
				continue;
			}
			if (k == GameVisuals.KillEffect.IMPLODE) {
				// Rings pulling into a dark point.
				tiles.add(new PickTile(k.label(), (g, x, y, s, t, fade, on) -> {
					float cx = x + s / 2;
					float cy = y + s / 2;
					for (int i = 0; i < 3; i++) {
						float p = (float) ((t * 0.6 + i / 3.0) % 1.0);
						Shapes.ring(g, cx, cy, s * 0.36f * (1 - p) + 1, 1.6f, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), p), fade));
					}
					Shapes.circle(g, cx, cy, s * 0.08f, Colors.multiplyAlpha(0xFF0A0A10, fade), Colors.multiplyAlpha(Theme.accent(), fade), 1.2f);
				}, () -> setting.get() == k, () -> setting.set(k)));
				continue;
			}
			if (k == GameVisuals.KillEffect.ASCEND) {
				// A beam of light going up.
				tiles.add(new PickTile(k.label(), (g, x, y, s, t, fade, on) -> {
					float cx = x + s / 2;
					Shapes.gradientRect(g, cx - s * 0.06f, y + s * 0.1f, s * 0.12f, s * 0.75f, s * 0.06f, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0), fade),
						Colors.multiplyAlpha(Theme.accent(), fade));
					for (int i = 0; i < 4; i++) {
						float p = (float) ((t * 0.5 + i / 4.0) % 1.0);
						Shapes.circle(g, cx + (i % 2 == 0 ? -1 : 1) * s * 0.16f, y + s * (0.85f - 0.7f * p), 1.6f,
							Colors.multiplyAlpha(Colors.withAlpha(0xFFFFFFFF, 1 - p), fade));
					}
				}, () -> setting.get() == k, () -> setting.set(k)));
				continue;
			}
			Icon icon = switch (k) {
				case NONE -> Icon.CLOSE;
				case FIREWORK -> Icon.SPARKLE;
				case SOUL -> Icon.FLAME;
				case EXPLOSION -> Icon.SUN;
				case TOTEM -> Icon.TOTEM;
				case HEARTS, BLOOD -> Icon.HEART;
				default -> Icon.REFRESH;
			};
			int color = switch (k) {
				case NONE -> 0x99FFFFFF;
				case FIREWORK -> Theme.accent();
				case SOUL -> 0xFF4FE3F0;
				case EXPLOSION -> 0xFFE0E0E0;
				case TOTEM -> 0xFFFFD84D;
				case HEARTS -> 0xFFFF5C7A;
				case BLOOD -> 0xFFC81E2E;
				default -> 0xFFC084FC;
			};
			tiles.add(PickTile.icon(k.label(), icon, color, () -> setting.get() == k, () -> setting.set(k)));
		}
		return tiles;
	}
	// ---- held item, shimmer colour, jumps, explosions ----

	static List<PickTile> shineTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.heldShine;
		for (GameVisuals.HeldShine s : GameVisuals.HeldShine.values()) {
			Icon icon = switch (s) {
				case NONE -> Icon.CLOSE;
				case SHIMMER, BOTH -> Icon.SPARKLE;
				case OUTLINE -> Icon.SWORD;
			};
			int color = switch (s) {
				case NONE -> 0x99FFFFFF;
				case SHIMMER -> 0xFFC084FC;
				case OUTLINE -> 0xFFFFE07A;
				case BOTH -> Theme.accent();
			};
			tiles.add(PickTile.icon(s.label(), icon, color, () -> setting.get() == s, () -> setting.set(s)));
		}
		return tiles;
	}

	static List<PickTile> glintTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.glintColor;
		for (GameVisuals.GlintColor c : GameVisuals.GlintColor.values()) {
			tiles.add(new PickTile(c.label(), (g, x, y, size, t, fade, on) -> {
				float cx = x + size / 2;
				float cy = y + size / 2;
				float r = size * 0.3f;
				if (c == GameVisuals.GlintColor.RAINBOW) {
					for (int i = 0; i < 12; i++) {
						float a = (float) (i * Math.PI * 2 / 12 - Math.PI / 2);
						Shapes.arc(g, cx, cy, r, r * 0.9f, a, (float) (Math.PI * 2 / 12) + 0.02f, Colors.multiplyAlpha(Colors.hsv(i / 12f + t, 0.75f, 1), fade));
					}
					return;
				}
				int color = switch (c) {
					case VANILLA -> 0xFFA06BFF;
					case ACCENT -> Theme.accent();
					case CUSTOM -> GameVisuals.INSTANCE.glintCustom.argb() | 0xFF000000;
					case RAINBOW -> 0;
				};
				Shapes.circle(g, cx, cy, r, Colors.multiplyAlpha(color, fade));
				Shapes.circle(g, cx - r * 0.3f, cy - r * 0.3f, r * 0.25f, Colors.multiplyAlpha(0x88FFFFFF, fade));
			}, () -> setting.get() == c, () -> setting.set(c)));
		}
		return tiles;
	}

	static List<PickTile> jumpTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.jumpEffect;
		for (GameVisuals.JumpEffect j : GameVisuals.JumpEffect.values()) {
			if (j == GameVisuals.JumpEffect.RING) {
				tiles.add(new PickTile(j.label(), (g, x, y, size, t, fade, on) -> {
					float cx = x + size / 2;
					float cy = y + size * 0.62f;
					float grow = t == 0 ? 0.6f : t;
					Shapes.ring(g, cx, cy, size * 0.36f * grow, 1.6f, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 1 - grow * 0.6f), fade));
					Shapes.rect(g, cx - 3, cy - size * 0.32f, 6, size * 0.28f, 3, Colors.multiplyAlpha(0xCCFFFFFF, fade));
				}, () -> setting.get() == j, () -> setting.set(j)));
				continue;
			}
			Icon icon = switch (j) {
				case NONE -> Icon.CLOSE;
				case DUST, CLOUD -> Icon.SUN;
				case SPARKLES -> Icon.SPARKLE;
				case FLAMES, SOUL -> Icon.FLAME;
				case HEARTS -> Icon.HEART;
				case RING -> Icon.REFRESH;
			};
			int color = switch (j) {
				case NONE -> 0x99FFFFFF;
				case DUST -> 0xFFC9B79C;
				case CLOUD -> 0xFFF0F4F8;
				case SPARKLES -> 0xFFFFF3B0;
				case FLAMES -> 0xFFFF9A3C;
				case SOUL -> 0xFF4FE3F0;
				case HEARTS -> 0xFFFF5C7A;
				case RING -> Theme.accent();
			};
			tiles.add(PickTile.icon(j.label(), icon, color, () -> setting.get() == j, () -> setting.set(j)));
		}
		return tiles;
	}

	static List<PickTile> explosionTiles() {
		List<PickTile> tiles = new ArrayList<>();
		var setting = GameVisuals.INSTANCE.explosionStyle;
		for (GameVisuals.ExplosionStyle e : GameVisuals.ExplosionStyle.values()) {
			int[] colors = switch (e) {
				case VANILLA -> new int[]{0xFFE0E0E0, 0xFF9A9A9A};
				case SHOCKWAVE -> new int[]{Theme.accent(), 0xFFFFFFFF};
				case INFERNO -> new int[]{0xFFFF6A00, 0xFFFFD25A};
				case FIREWORK -> new int[]{0xFFFF5C7A, 0xFF7FD8FF};
				case MAGIC -> new int[]{0xFFC084FC, 0xFFF0E6FF};
				case VOID -> new int[]{0xFF6A1BBF, 0xFF120022};
			};
			tiles.add(new PickTile(e.label(), (g, x, y, size, t, fade, on) -> {
				float cx = x + size / 2;
				float cy = y + size / 2;
				float burst = t == 0 ? 0.7f : 0.3f + t * 0.7f;
				for (int i = 0; i < 10; i++) {
					double a = i * Math.PI * 2 / 10;
					float r1 = size * 0.12f * burst;
					float r2 = size * 0.4f * burst;
					Shapes.line(g, cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2,
						cy + (float) Math.sin(a) * r2, 1.6f, Colors.multiplyAlpha(i % 2 == 0 ? colors[0] : colors[1], fade));
				}
				Shapes.circle(g, cx, cy, size * 0.1f, Colors.multiplyAlpha(colors[1], fade));
			}, () -> setting.get() == e, () -> setting.set(e)));
		}
		return tiles;
	}
}