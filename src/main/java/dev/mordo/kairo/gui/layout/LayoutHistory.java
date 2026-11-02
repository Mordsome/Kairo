package dev.mordo.kairo.gui.layout;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.hud.CpsHud;
import dev.mordo.kairo.hud.HudElement;
import dev.mordo.kairo.hud.HudManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Undo / redo for the layout editor. A snapshot holds every element's placement settings (anchor, offsets, scale,
 * shown) with both their normal value and PvP override, so undoing restores both layouts exactly.
 */
final class LayoutHistory {
	private static final int LIMIT = 60;

	/** One setting's normal value and PvP override (null = none). */
	private record Value(Setting<?> setting, Object normal, Object override) {
	}

	record Snapshot(List<Value> values) {
		boolean sameAs(Snapshot other) {
			if (other.values.size() != values.size()) {
				return false;
			}
			for (int i = 0; i < values.size(); i++) {
				Value a = values.get(i);
				Value b = other.values.get(i);
				if (!Objects.equals(a.normal(), b.normal()) || !Objects.equals(a.override(), b.override())) {
					return false;
				}
			}
			return true;
		}
	}

	private final Deque<Snapshot> undo = new ArrayDeque<>();
	private final Deque<Snapshot> redo = new ArrayDeque<>();
	private long lastPushNanos;
	private String lastTag = "";

	private static List<Setting<?>> tracked() {
		List<Setting<?>> list = new ArrayList<>();
		for (HudElement e : HudManager.elements()) {
			list.add(e.anchor);
			list.add(e.offsetX);
			list.add(e.offsetY);
			list.add(e.scale);
			list.add(e.enabled);
		}
		list.add(ModuleManager.get(CpsHud.class).attachToKeystrokes);
		return list;
	}

	static Snapshot capture() {
		List<Value> values = new ArrayList<>();
		for (Setting<?> s : tracked()) {
			values.add(new Value(s, s.getNormal(), s.getPvpOverride()));
		}
		return new Snapshot(values);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void restore(Snapshot snapshot) {
		HudManager.startGlide();
		for (Value v : snapshot.values()) {
			Setting s = v.setting();
			if (!Objects.equals(s.getNormal(), v.normal())) {
				s.set(v.normal());
			}
			if (v.override() == null) {
				if (s.getPvpOverride() != null) {
					s.clearPvpOverride();
				}
			} else if (!Objects.equals(s.getPvpOverride(), v.override())) {
				s.setPvpOverride(v.override());
			}
		}
	}

	/** Remembers the layout before a change. */
	void push(Snapshot before) {
		undo.push(before);
		while (undo.size() > LIMIT) {
			undo.removeLast();
		}
		redo.clear();
	}

	/** Like {@link #push(Snapshot)} with the current layout, but repeated quick steps (scroll, arrow keys) count as one. */
	void pushCoalesced(String tag) {
		long now = System.nanoTime();
		if (!tag.equals(lastTag) || now - lastPushNanos > 700_000_000L) {
			push(capture());
		}
		lastTag = tag;
		lastPushNanos = now;
	}

	/** Pushes {@code before} only if the layout really changed since. */
	void commitIfChanged(Snapshot before) {
		if (!before.sameAs(capture())) {
			push(before);
		}
	}

	boolean undo() {
		if (undo.isEmpty()) {
			return false;
		}
		redo.push(capture());
		restore(undo.pop());
		lastTag = "";
		return true;
	}

	boolean redo() {
		if (redo.isEmpty()) {
			return false;
		}
		undo.push(capture());
		restore(redo.pop());
		lastTag = "";
		return true;
	}

	boolean canUndo() {
		return !undo.isEmpty();
	}

	boolean canRedo() {
		return !redo.isEmpty();
	}
}
