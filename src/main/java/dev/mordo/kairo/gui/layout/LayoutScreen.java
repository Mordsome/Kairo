package dev.mordo.kairo.gui.layout;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.KairoScreen;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.gui.widgets.Binding;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ModeSwitch;
import dev.mordo.kairo.gui.widgets.WidgetHost;
import dev.mordo.kairo.hud.CpsHud;
import dev.mordo.kairo.hud.HudElement;
import dev.mordo.kairo.hud.HudManager;
import dev.mordo.kairo.hud.KeystrokesHud;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HUD layout editor. The game stays visible (lightly dimmed) and every HUD element is drawn with an outline;
 * elements hidden in the current mode show as faint placeholders. Drag to move (snaps to screen edges,
 * centre lines and other elements, with guide lines), scroll to resize, right-click for the element's settings.
 *
 * <p>It edits the layout of the mode the player is in. The Normal/PvP switch flips the real PvP mode, and
 * every change made while it's on PvP goes into the PvP layer (positions, sizes, hidden elements), so the
 * normal layout comes back untouched.
 */
public final class LayoutScreen extends KairoScreen {
	private static final float SNAP = 4;

	private final WidgetHost host = new WidgetHost();
	private final Animation intro = new Animation(0);
	private final Map<HudElement, float[]> boxes = new IdentityHashMap<>();
	private final Set<HudElement> ghosts = Collections.newSetFromMap(new IdentityHashMap<>());
	private final ModeSwitch mode;
	private final Button visibility;
	private final Button resetElement;
	private final Button resetAll;
	private final Button done;

	/** The element clicked last (handles, label); always part of {@link #selection} when set. */
	private @Nullable HudElement selected;
	/** Everything selected: shift/ctrl-click to add or remove, or drag a box over empty space. */
	private final Set<HudElement> selection = Collections.newSetFromMap(new IdentityHashMap<>());
	/** While dragging a group: each other member's offset from the element being dragged. */
	private final Map<HudElement, float[]> groupOffsets = new IdentityHashMap<>();
	private final Map<HudElement, Float> resizeScales = new IdentityHashMap<>();
	private boolean marquee;
	private float marqueeX0;
	private float marqueeY0;
	private final Set<HudElement> marqueeBase = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Button attachCps;
	private final Button undoButton;
	private final Button redoButton;
	/** Kept across editor sessions (right-click settings reopens a new editor). */
	private static final LayoutHistory HISTORY = new LayoutHistory();
	/** The layout when the current drag / resize started; committed to the history if it changed. */
	private LayoutHistory.@Nullable Snapshot pending;
	private @Nullable HudElement dragging;
	private @Nullable HudElement hovered;
	/** Element being resized with its corner grip, with its box and scale when the drag started. */
	private @Nullable HudElement resizing;
	private float[] resizeStart = new float[4];
	private float resizeStartScale;
	private float mouseGuiX = -1000;
	private float mouseGuiY = -1000;
	private static final com.mojang.blaze3d.platform.cursor.CursorType RESIZE_DIAGONAL =
		com.mojang.blaze3d.platform.cursor.CursorType.createStandardCursor(5, "resize_nwse", CursorTypes.RESIZE_ALL);
	private float grabX;
	private float grabY;
	private float dragX;
	private float dragY;
	private float guideX = Float.NaN;
	private float guideY = Float.NaN;
	private int guiW;
	private int guiH;

	public LayoutScreen() {
		super(Component.literal(ClientInfo.NAME + " layout"));
		mode = new ModeSwitch(new Binding<>() {
			@Override
			public Boolean get() {
				return PvpMode.isActiveForReal();
			}

			@Override
			public void set(Boolean pvp) {
				PvpMode.set(pvp);
			}
		}).textSize(11.5f);
		visibility = new Button("Hide", null, Button.Style.SUBTLE, () -> {
			if (selected != null) {
				HISTORY.push(LayoutHistory.capture());
				boolean show = !selected.isEnabled();
				for (HudElement element : selection) {
					element.enabled.setEffective(show);
				}
			}
		}).textSize(11.5f);
		resetElement = new Button("Reset element", null, Button.Style.SUBTLE, () -> {
			HISTORY.push(LayoutHistory.capture());
			HudManager.startGlide();
			for (HudElement element : selection) {
				element.resetPosition();
			}
		}).textSize(11.5f);
		attachCps = new Button("Attach to Keystrokes", null, Button.Style.SUBTLE, () -> {
			HISTORY.push(LayoutHistory.capture());
			HudManager.startGlide();
			cps().attachToKeystrokes.setEffective(!cps().attachToKeystrokes.isOn());
		}).textSize(11.5f);
		resetAll = new Button("Reset all", null, Button.Style.SUBTLE, () -> {
			HISTORY.push(LayoutHistory.capture());
			HudManager.startGlide();
			for (HudElement element : HudManager.elements()) {
				element.resetPosition();
			}
			cps().attachToKeystrokes.resetEffective();
		}).textSize(11.5f);
		done = new Button("Done", null, Button.Style.PRIMARY, this::onClose).textSize(11.5f);
		undoButton = new Button("", Icon.UNDO, Button.Style.SUBTLE, this::undo);
		redoButton = new Button("", Icon.REDO, Button.Style.SUBTLE, this::redo);
	}

	private static CpsHud cps() {
		return ModuleManager.get(CpsHud.class);
	}

	@Override
	protected void init() {
		super.init();
		intro.animateTo(1, 260, Easing.OUT_CUBIC);
	}

	@Override
	public void removed() {
		host.reset();
	}

	// ---- drawing ----

	/** GUI space: dim, the HUD elements with outlines, placeholders for hidden ones, and snap guides. */
	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		float a = intro.value();
		guiW = g.guiWidth();
		guiH = g.guiHeight();
		g.fill(0, 0, guiW, guiH, Colors.argb(0.28f * a, 0x05070A));
		if (dragging != null) {
			// Faint centre lines while dragging, so there's something to aim at.
			Shapes.rect(g, guiW / 2f - 0.5f, 0, 1, guiH, 0, Colors.argb(0.10f, 0xFFFFFF));
			Shapes.rect(g, 0, guiH / 2f - 0.5f, guiW, 1, 0, Colors.argb(0.10f, 0xFFFFFF));
		}

		boxes.clear();
		ghosts.clear();
		for (HudElement element : HudManager.elements()) {
			HudManager.prepare(element, false);
			boolean shown = element.isEnabled();
			float x;
			float y;
			float scale;
			if (shown) {
				float[] p = HudManager.place(element, guiW, guiH);
				x = p[0];
				y = p[1];
				scale = p[2];
			} else {
				ghosts.add(element);
				x = HudManager.x(element, guiW);
				y = HudManager.y(element, guiH);
				scale = HudManager.scale(element);
			}
			if (element == dragging) {
				x = dragX;
				y = dragY;
			} else if (dragging != null && groupOffsets.containsKey(element)) {
				float[] off = groupOffsets.get(element);
				x = Math.clamp(dragX + off[0], 0, Math.max(0, guiW - element.width() * scale));
				y = Math.clamp(dragY + off[1], 0, Math.max(0, guiH - element.height() * scale));
			}
			float w = element.width() * scale;
			float h = element.height() * scale;
			boxes.put(element, new float[]{x, y, w, h});
			if (shown) {
				HudManager.render(g, element, x, y, scale, false);
			} else {
				ghost(g, element, x, y, w, h, a);
			}
		}

		int accent = Theme.modeAccent();
		for (Map.Entry<HudElement, float[]> entry : boxes.entrySet()) {
			HudElement element = entry.getKey();
			float[] b = entry.getValue();
			boolean active = selection.contains(element) || element == dragging;
			int color = active ? accent : element == hovered ? 0xB3FFFFFF : 0x59FFFFFF;
			if (ghosts.contains(element) && !active) {
				color = Colors.multiplyAlpha(color, 0.6f);
			}
			if (active && selection.size() > 1) {
				Shapes.rect(g, b[0] - 1.5f, b[1] - 1.5f, b[2] + 3, b[3] + 3, 2, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.10f), a));
			}
			Shapes.outline(g, b[0] - 1.5f, b[1] - 1.5f, b[2] + 3, b[3] + 3, 2, Colors.multiplyAlpha(color, a), 1);
			if (element == selected || element == dragging || element == hovered) {
				label(g, element, b);
			}
		}

		if (marquee) {
			float x0 = Math.min(marqueeX0, mouseGuiX);
			float y0 = Math.min(marqueeY0, mouseGuiY);
			float w = Math.abs(mouseGuiX - marqueeX0);
			float h = Math.abs(mouseGuiY - marqueeY0);
			Shapes.rect(g, x0, y0, w, h, 1, Colors.withAlpha(accent, 0.12f), Colors.withAlpha(accent, 0.8f), 0.75f);
		}

		HudElement owner = handleOwner();
		if (owner != null && boxes.containsKey(owner) && dragging == null) {
			drawHandles(g, owner, boxes.get(owner), a);
		}

		int guide = Colors.withAlpha(accent, 0.85f);
		if (!Float.isNaN(guideX)) {
			Shapes.rect(g, guideX - 0.5f, 0, 1, guiH, 0, guide);
		}
		if (!Float.isNaN(guideY)) {
			Shapes.rect(g, 0, guideY - 0.5f, guiW, 1, 0, guide);
		}
	}

	// ---- corner handles: ✕ (hide / show) top-right, resize bottom-right ----

	private static final float CLOSE_R = 3.6f;
	private static final float GRIP = 4.5f;

	/** The element whose handles show: the one being resized, else the hovered one, else the selected one. */
	private @Nullable HudElement handleOwner() {
		if (resizing != null) {
			return resizing;
		}
		return hovered != null ? hovered : selected;
	}

	private static float closeX(float[] b) {
		return b[0] + b[2] + 1.5f;
	}

	private static float closeY(float[] b) {
		return b[1] - 1.5f;
	}

	private static float gripX(float[] b) {
		return b[0] + b[2] + 1.5f;
	}

	private static float gripY(float[] b) {
		return b[1] + b[3] + 1.5f;
	}

	private static boolean onClose(float[] b, float gx, float gy) {
		float dx = gx - closeX(b);
		float dy = gy - closeY(b);
		return dx * dx + dy * dy <= (CLOSE_R + 1.5f) * (CLOSE_R + 1.5f);
	}

	private static boolean onGrip(float[] b, float gx, float gy) {
		return Math.abs(gx - gripX(b)) <= GRIP / 2 + 1.5f && Math.abs(gy - gripY(b)) <= GRIP / 2 + 1.5f;
	}

	private void drawHandles(GuiGraphicsExtractor g, HudElement element, float[] b, float a) {
		boolean ghost = ghosts.contains(element);
		int accent = Theme.modeAccent();
		float gx = mouseGuiX;
		float gy = mouseGuiY;

		// ✕ hides the element; on a hidden one it's a + that brings it back.
		if (resizing == null) {
			float cx = closeX(b);
			float cy = closeY(b);
			boolean hot = onClose(b, gx, gy);
			int ring = ghost ? accent : 0xFFFF5A5F;
			Shapes.circle(g, cx, cy, CLOSE_R, Colors.multiplyAlpha(hot ? ring : 0xF0111720, a),
				Colors.multiplyAlpha(ring, a), 0.75f);
			int mark = Colors.multiplyAlpha(hot ? 0xFF111720 : ring, a);
			float s = CLOSE_R * 0.45f;
			if (ghost) {
				Shapes.line(g, cx - s, cy, cx + s, cy, 0.9f, mark);
				Shapes.line(g, cx, cy - s, cx, cy + s, 0.9f, mark);
			} else {
				Shapes.line(g, cx - s, cy - s, cx + s, cy + s, 0.9f, mark);
				Shapes.line(g, cx - s, cy + s, cx + s, cy - s, 0.9f, mark);
			}
		}

		// Resize grip.
		if (!ghost) {
			boolean hot = resizing == element || onGrip(b, gx, gy);
			float size = hot ? GRIP + 1 : GRIP;
			float x = gripX(b) - size / 2;
			float y = gripY(b) - size / 2;
			Shapes.rect(g, x, y, size, size, 1, Colors.multiplyAlpha(accent, a), Colors.multiplyAlpha(0xFF111720, a), 0.6f);
			if (resizing == element) {
				HudElement target = HudManager.isAttached(element) ? ModuleManager.get(KeystrokesHud.class) : element;
				String text = String.format(java.util.Locale.ROOT, "%.2fx", target.scaleValue());
				float tw = Fonts.width(Typeface.MONO_BOLD, text, 6.5f) + 6;
				float tx = Math.min(gripX(b) + 5, guiW - tw - 2);
				float ty = Math.min(gripY(b) + 4, guiH - 12);
				Shapes.rect(g, tx, ty, tw, 10, 3, 0xE6111720);
				Fonts.draw(g, Typeface.MONO_BOLD, text, tx + 3, ty + 5, 6.5f, Theme.TEXT);
			}
		}
	}

	/** Placeholder for an element that's hidden in the current mode: faint box with its name. */
	private static void ghost(GuiGraphicsExtractor g, HudElement element, float x, float y, float w, float h, float a) {
		Shapes.rect(g, x, y, w, h, 2, Colors.argb(0.10f * a, 0xFFFFFF));
		float size = Math.min(7, Math.max(4.5f, h * 0.3f));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, element.name(), size, Math.max(10, w - 4)),
			x + w / 2, y + h / 2, size, Theme.text(0.5f, a), Fonts.Align.CENTER, 0, false);
	}

	/** Small name tag above (or below, near the top edge) an element. */
	private void label(GuiGraphicsExtractor g, HudElement element, float[] b) {
		String name = element.name();
		if (HudManager.isAttached(element)) {
			name += " · attached";
		}
		if (ghosts.contains(element)) {
			name += PvpMode.isActive() ? " · hidden in PvP" : " · hidden";
		} else if (PvpMode.isActive() && element.hasPvpPlacement()) {
			name += " · PvP position";
		}
		float size = 7;
		float w = Fonts.width(Typeface.OUTFIT_SEMIBOLD, name, size) + 8;
		float h = 11;
		float x = Math.clamp(b[0], 2, guiW - w - 2);
		float y = b[1] - h - 4 < 2 ? b[1] + b[3] + 4 : b[1] - h - 4;
		Shapes.rect(g, x, y, w, h, 3, 0xE6111720);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, name, x + 4, y + h / 2, size, Theme.TEXT);
	}

	/** Reference space: hints and toolbar. */
	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float partialTick) {
		host.beginFrame(viewWidth, viewHeight);
		float a = intro.value();
		updateHover(mx * unit, my * unit);
		float[] hb = hovered != null ? boxes.get(hovered) : null;
		if (resizing != null || (hb != null && dragging == null && !ghosts.contains(hovered) && onGrip(hb, mouseGuiX, mouseGuiY))) {
			g.requestCursor(RESIZE_DIAGONAL);
		} else if (hb != null && dragging == null && onClose(hb, mouseGuiX, mouseGuiY)) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		} else if (hovered != null && dragging == null) {
			g.requestCursor(CursorTypes.RESIZE_ALL);
		}

		boolean hasSelection = selected != null && !selection.isEmpty();
		int count = selection.size();
		visibility.setEnabled(hasSelection);
		visibility.setLabel(hasSelection && !selected.isEnabled() ? (count > 1 ? "Show " + count : "Show") : (count > 1 ? "Hide " + count : "Hide"));
		resetElement.setEnabled(hasSelection);
		resetElement.setLabel(count > 1 ? "Reset " + count + " elements" : "Reset element");
		// CPS can sit under Keystrokes; offer it right here whenever CPS (or Keystrokes) is selected.
		boolean cpsChoice = selection.contains(cps()) || selection.contains(ModuleManager.get(KeystrokesHud.class));
		attachCps.setLabel(cps().attachToKeystrokes.isOn() ? "Detach CPS" : "Attach CPS to Keystrokes");
		List<Button> actions = new java.util.ArrayList<>(List.of(visibility, resetElement));
		if (cpsChoice) {
			actions.add(attachCps);
		}
		actions.add(resetAll);
		undoButton.setEnabled(HISTORY.canUndo());
		redoButton.setEnabled(HISTORY.canRedo());
		actions.add(undoButton);
		actions.add(redoButton);
		float bw0 = mode.preferredWidth();
		float gap = 8;
		float split = 18; // a little more room between the mode switch and the actions
		float barW = bw0 + split + 16 + 16 + done.preferredWidth() + gap;
		for (Button action : actions) {
			barW += action.preferredWidth() + gap;
		}
		barW -= gap;
		float barH = 48;
		float x = (viewWidth - barW) / 2;
		// Below the crosshair but well clear of the hotbar and health bars.
		float y = viewHeight * 0.64f + Animation.motion((1 - a) * 16, 0);
		Shapes.glassRect(g, x, y, barW, barH, 16, Theme.glassTint(), Theme.PANEL_BORDER, 1.25f, a);
		mode.bounds(x + 8, y + 7, bw0, 34);
		float ax = mode.x + bw0 + split;
		for (Button action : actions) {
			float w = action.preferredWidth();
			action.bounds(ax, y + 7, w, 34);
			ax += w + gap;
		}
		done.bounds(ax, y + 7, done.preferredWidth() + 16, 34);
		Shapes.rect(g, mode.x + bw0 + split / 2 - 0.5f, y + 12, 1, barH - 24, 0, Colors.argb(0.12f * a, 0xFFFFFF));
		host.render(mode, g, mx, my, a);
		for (Button action : actions) {
			host.render(action, g, mx, my, a);
		}
		host.render(done, g, mx, my, a);
		if (count > 1) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, count + " selected  ·  drag any of them to move them together", viewWidth / 2, y + barH + 16, 11,
				Colors.multiplyAlpha(Theme.modeAccent(), a), Fonts.Align.CENTER, 0, true);
		}

		boolean pvp = PvpMode.isActive();
		String modeLine = pvp
			? "Editing the PvP layout  ·  only used while PvP mode is on"
			: "Editing the normal layout";
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, modeLine, viewWidth / 2, y - 34, 11.5f,
			Colors.multiplyAlpha(pvp ? Theme.pvpAccent() : Theme.text(0.9f), a), Fonts.Align.CENTER, 0, true);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "Drag to move  ·  Corner or scroll to resize  ·  × to hide  ·  Shift-click or drag a box to select several  ·  Ctrl+Z undo  ·  Right-click for settings",
			viewWidth / 2, y - 16, 11, Theme.text(0.7f, a), Fonts.Align.CENTER, 0, true);
	}


	private static boolean modifierShift() {
		return dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_LSHIFT)
			|| dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_RSHIFT);
	}

	private static boolean modifierDown() {
		return dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_LSHIFT)
			|| dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_RSHIFT)
			|| dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_LCONTROL)
			|| dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_RCONTROL);
	}

	private void selectOnly(@Nullable HudElement element) {
		selection.clear();
		selected = element;
		if (element != null) {
			selection.add(element);
		}
	}

	/** Elements whose boxes overlap the drag-selection rectangle. */
	private void updateMarquee() {
		float x0 = Math.min(marqueeX0, mouseGuiX);
		float y0 = Math.min(marqueeY0, mouseGuiY);
		float x1 = Math.max(marqueeX0, mouseGuiX);
		float y1 = Math.max(marqueeY0, mouseGuiY);
		selection.clear();
		selection.addAll(marqueeBase);
		for (Map.Entry<HudElement, float[]> entry : boxes.entrySet()) {
			float[] b = entry.getValue();
			if (b[0] < x1 && b[0] + b[2] > x0 && b[1] < y1 && b[1] + b[3] > y0) {
				selection.add(entry.getKey());
			}
		}
		if (selected == null || !selection.contains(selected)) {
			selected = selection.isEmpty() ? null : selection.iterator().next();
		}
	}

	private void updateHover(float gx, float gy) {
		mouseGuiX = gx;
		mouseGuiY = gy;
		// The corner handles stick out of the box: while the cursor is on one, keep its element hovered.
		HudElement owner = handleOwner();
		float[] ob = owner != null ? boxes.get(owner) : null;
		if (ob != null && (onClose(ob, gx, gy) || (!ghosts.contains(owner) && onGrip(ob, gx, gy)))) {
			hovered = owner;
			return;
		}
		hovered = null;
		for (Map.Entry<HudElement, float[]> entry : boxes.entrySet()) {
			float[] b = entry.getValue();
			if (gx >= b[0] - 2 && gx <= b[0] + b[2] + 2 && gy >= b[1] - 2 && gy <= b[1] + b[3] + 2) {
				// Prefer shown elements over placeholders, then the smallest box (CPS sits right under Keystrokes).
				HudElement candidate = entry.getKey();
				if (hovered == null || better(candidate, b, hovered, boxes.get(hovered))) {
					hovered = candidate;
				}
			}
		}
	}

	private boolean better(HudElement a, float[] ab, HudElement b, float[] bb) {
		boolean aGhost = ghosts.contains(a);
		boolean bGhost = ghosts.contains(b);
		if (aGhost != bGhost) {
			return !aGhost;
		}
		return ab[2] * ab[3] < bb[2] * bb[3];
	}

	// ---- input (arrives in reference units; elements live in GUI pixels) ----

	@Override
	protected boolean onClick(float x, float y, int button) {
		if (host.mouseClicked(x, y, button)) {
			return true;
		}
		updateHover(x * unit, y * unit);
		boolean modifier = modifierDown();
		if (hovered == null) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			// Empty space: start a selection box (with shift/ctrl it adds to what's selected).
			marqueeBase.clear();
			if (modifier) {
				marqueeBase.addAll(selection);
			} else {
				selectOnly(null);
			}
			marquee = true;
			marqueeX0 = x * unit;
			marqueeY0 = y * unit;
			return true;
		}
		HudElement element = hovered;
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			close(MenuScreen.forModSettings(element, new LayoutScreen()));
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		float[] b = boxes.get(element);
		if (onClose(b, x * unit, y * unit)) {
			HISTORY.push(LayoutHistory.capture());
			boolean show = !element.isEnabled();
			element.enabled.setEffective(show);
			dev.mordo.kairo.sound.UiSounds.toggle(show);
			if (!selection.contains(element)) {
				selectOnly(element);
			}
			return true;
		}
		if (!ghosts.contains(element) && onGrip(b, x * unit, y * unit)) {
			if (!selection.contains(element)) {
				selectOnly(element);
			}
			selected = element;
			resizing = element;
			pending = LayoutHistory.capture();
			resizeStart = b.clone();
			resizeScales.clear();
			for (HudElement member : selection) {
				HudElement target = HudManager.isAttached(member) ? ModuleManager.get(KeystrokesHud.class) : member;
				resizeScales.put(target, target.scaleValue());
			}
			HudElement target = HudManager.isAttached(element) ? ModuleManager.get(KeystrokesHud.class) : element;
			resizeStartScale = target.scaleValue();
			return true;
		}
		if (modifier) {
			// Shift/ctrl-click adds or removes; no drag.
			if (selection.remove(element)) {
				if (selected == element) {
					selected = selection.isEmpty() ? null : selection.iterator().next();
				}
			} else {
				selection.add(element);
				selected = element;
			}
			return true;
		}
		if (!selection.contains(element)) {
			selectOnly(element);
		}
		selected = element;
		dragging = element;
		pending = LayoutHistory.capture();
		dragX = b[0];
		dragY = b[1];
		grabX = x * unit - b[0];
		grabY = y * unit - b[1];
		// Everything else selected moves along, keeping its distance.
		groupOffsets.clear();
		for (HudElement member : selection) {
			float[] mb = boxes.get(member);
			if (member != element && mb != null && !HudManager.isAttached(member)) {
				groupOffsets.put(member, new float[]{mb[0] - b[0], mb[1] - b[1]});
			}
		}
		return true;
	}

	@Override
	protected boolean onDrag(float x, float y, int button, float dx, float dy) {
		if (host.mouseDragged(x, y, button)) {
			return true;
		}
		if (resizing != null) {
			resizeTo(x * unit, y * unit);
			return true;
		}
		if (marquee) {
			mouseGuiX = x * unit;
			mouseGuiY = y * unit;
			updateMarquee();
			return true;
		}
		if (dragging == null) {
			return false;
		}
		float[] b = boxes.get(dragging);
		if (b == null) {
			return false;
		}
		dragX = x * unit - grabX;
		dragY = y * unit - grabY;
		snap(dragging, b[2], b[3]);
		return true;
	}

	@Override
	protected boolean onRelease(float x, float y, int button) {
		host.mouseReleased(x, y, button);
		if (resizing != null) {
			resizing = null;
			commitPending();
			return true;
		}
		if (marquee) {
			marquee = false;
			return true;
		}
		if (dragging == null) {
			return false;
		}
		HudElement element = dragging;
		guideX = Float.NaN;
		guideY = Float.NaN;
		// Group members first, from the positions they were drawn at.
		for (Map.Entry<HudElement, float[]> entry : groupOffsets.entrySet()) {
			float[] mb = boxes.get(entry.getKey());
			if (mb != null) {
				entry.getKey().moveTo(mb[0], mb[1], guiW, guiH);
			}
		}
		groupOffsets.clear();
		dragging = null;
		if (HudManager.isAttached(element)) {
			// Dragging the attached CPS counter detaches it from Keystrokes (in this mode's layout).
			cps().attachToKeystrokes.setEffective(false);
		}
		element.moveTo(dragX, dragY, guiW, guiH);
		commitPending();
		return true;
	}

	private void commitPending() {
		if (pending != null) {
			HISTORY.commitIfChanged(pending);
			pending = null;
		}
	}

	private void undo() {
		if (HISTORY.undo()) {
			dev.mordo.kairo.sound.UiSounds.click();
		} else {
			dev.mordo.kairo.gui.Toasts.show("Nothing to undo", Theme.modeAccent());
		}
	}

	private void redo() {
		if (HISTORY.redo()) {
			dev.mordo.kairo.sound.UiSounds.click();
		} else {
			dev.mordo.kairo.gui.Toasts.show("Nothing to redo", Theme.modeAccent());
		}
	}

	/** Corner drag: the box grows or shrinks towards the cursor, its top-left corner stays put. */
	private void resizeTo(float gx, float gy) {
		HudElement element = resizing;
		boolean attached = HudManager.isAttached(element);
		HudElement target = attached ? ModuleManager.get(KeystrokesHud.class) : element;
		float[] b = resizeStart;
		float w = Math.max(1, b[2]);
		float h = Math.max(1, b[3]);
		// Project the cursor onto the box's diagonal so both directions feel the same.
		float ratio = ((gx - b[0]) * w + (gy - b[1]) * h) / (w * w + h * h);
		double scale = Math.round(resizeStartScale * ratio / 0.05) * 0.05;
		scale = Math.clamp(scale, target.scale.min(), target.scale.max());
		if (Math.abs(scale - target.scale.value()) < 1e-6) {
			return;
		}
		// Every selected element grows by the same factor, each from its own top-left corner.
		double factor = scale / resizeStartScale;
		for (Map.Entry<HudElement, Float> entry : resizeScales.entrySet()) {
			HudElement member = entry.getKey();
			float[] mb = boxes.get(member);
			double s = member == target ? scale
				: Math.clamp(Math.round(entry.getValue() * factor / 0.05) * 0.05, member.scale.min(), member.scale.max());
			member.scale.setEffective(s);
			if (mb != null && member != target) {
				member.moveTo(mb[0], mb[1], guiW, guiH);
			}
		}
		if (!resizeScales.containsKey(target)) {
			target.scale.setEffective(scale);
		}
		if (!attached) {
			element.moveTo(b[0], b[1], guiW, guiH);
		}
	}

	@Override
	protected boolean onScroll(float x, float y, float amount) {
		updateHover(x * unit, y * unit);
		if (hovered == null) {
			return false;
		}
		HISTORY.pushCoalesced("scroll");
		// Scrolling over a selected element resizes the whole selection.
		List<HudElement> members = selection.contains(hovered) ? List.copyOf(selection) : List.of(hovered);
		Set<HudElement> done = Collections.newSetFromMap(new IdentityHashMap<>());
		for (HudElement member : members) {
			HudElement target = HudManager.isAttached(member) ? ModuleManager.get(KeystrokesHud.class) : member;
			if (!done.add(target)) {
				continue;
			}
			// Keep the element's centre in place while it grows or shrinks.
			float[] before = boxes.get(target);
			target.scale.setEffective(target.scale.value() + Math.signum(amount) * 0.05);
			if (before != null) {
				float s = target.scaleValue();
				float w = target.width() * s;
				float h = target.height() * s;
				target.moveTo(before[0] + before[2] / 2 - w / 2, before[1] + before[3] / 2 - h / 2, guiW, guiH);
			}
		}
		if (!selection.contains(hovered)) {
			selectOnly(hovered);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (host.keyPressed(event)) {
			return true;
		}
		boolean ctrl = event.hasControlDown() || dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_LCONTROL)
			|| dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_RCONTROL);
		if (ctrl && (event.key() == InputConstants.KEY_Z || event.key() == InputConstants.KEY_Y)) {
			if (dragging == null && resizing == null) {
				if (event.key() == InputConstants.KEY_Y || event.hasShiftDown() || modifierShift()) {
					redo();
				} else {
					undo();
				}
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_A && event.hasControlDown()) {
			// Ctrl+A: select every shown element.
			selection.clear();
			for (HudElement element : boxes.keySet()) {
				if (!ghosts.contains(element)) {
					selection.add(element);
				}
			}
			selected = selection.isEmpty() ? null : selection.iterator().next();
			return true;
		}
		if (selected != null && dragging == null) {
			int dx = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
			int dy = event.isUp() ? -1 : event.isDown() ? 1 : 0;
			if (dx != 0 || dy != 0) {
				HISTORY.pushCoalesced("arrows");
				for (HudElement member : selection) {
					float[] b = boxes.get(member);
					if (b != null && !HudManager.isAttached(member)) {
						member.moveTo(b[0] + dx, b[1] + dy, guiW, guiH);
					}
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	// ---- snapping ----

	/** Pulls the dragged box onto nearby screen edges, centre lines and other shown elements' edges. */
	private void snap(HudElement element, float w, float h) {
		float bestX = SNAP + 1;
		float bestY = SNAP + 1;
		float snappedX = dragX;
		float snappedY = dragY;
		guideX = Float.NaN;
		guideY = Float.NaN;

		float[] xs = {0, guiW / 2f, guiW};
		float[] ys = {0, guiH / 2f, guiH};
		for (int pass = 0; pass < 2; pass++) {
			for (float target : xs) {
				for (int edge = 0; edge < 3; edge++) {
					float offset = edge * w / 2; // left, centre, right of the box
					float d = Math.abs(dragX + offset - target);
					if (d < bestX) {
						bestX = d;
						snappedX = target - offset;
						guideX = target;
					}
				}
			}
			for (float target : ys) {
				for (int edge = 0; edge < 3; edge++) {
					float offset = edge * h / 2;
					float d = Math.abs(dragY + offset - target);
					if (d < bestY) {
						bestY = d;
						snappedY = target - offset;
						guideY = target;
					}
				}
			}
			if (pass == 0) {
				// Second pass: edges and centres of the other shown elements (placeholders don't count).
				List<float[]> others = boxes.entrySet().stream()
					.filter(e -> e.getKey() != element && !ghosts.contains(e.getKey()))
					.map(Map.Entry::getValue).toList();
				xs = new float[others.size() * 3];
				ys = new float[others.size() * 3];
				for (int i = 0; i < others.size(); i++) {
					float[] o = others.get(i);
					xs[i * 3] = o[0];
					xs[i * 3 + 1] = o[0] + o[2] / 2;
					xs[i * 3 + 2] = o[0] + o[2];
					ys[i * 3] = o[1];
					ys[i * 3 + 1] = o[1] + o[3] / 2;
					ys[i * 3 + 2] = o[1] + o[3];
				}
			}
		}
		dragX = Math.clamp(snappedX, 0, Math.max(0, guiW - w));
		dragY = Math.clamp(snappedY, 0, Math.max(0, guiH - h));
	}
}
