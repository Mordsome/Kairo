package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.core.settings.Setting;

/** Where a widget reads and writes its value: a setting's normal value, its PvP override, or anything else. */
public interface Binding<T> {
	T get();

	void set(T value);

	/** The player's normal value. */
	static <T> Binding<T> normal(Setting<T> setting) {
		return new Binding<>() {
			@Override
			public T get() {
				return setting.getNormal();
			}

			@Override
			public void set(T value) {
				setting.set(value);
			}
		};
	}

	/** Whatever is in effect right now (the PvP override while PvP mode is on, if there is one). */
	static <T> Binding<T> effective(Setting<T> setting) {
		return new Binding<>() {
			@Override
			public T get() {
				return setting.get();
			}

			@Override
			public void set(T value) {
				setting.setEffective(value);
			}
		};
	}

	/** The PvP-mode override. Reads the normal value while there is no override; writing creates one. */
	static <T> Binding<T> pvp(Setting<T> setting) {
		return new Binding<>() {
			@Override
			public T get() {
				T override = setting.getPvpOverride();
				return override != null ? override : setting.getNormal();
			}

			@Override
			public void set(T value) {
				setting.setPvpOverride(value);
			}
		};
	}
}
