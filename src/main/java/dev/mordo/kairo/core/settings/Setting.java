package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.mordo.kairo.core.PvpMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A single configurable value with an optional PvP-mode override.
 *
 * <p>Two layers are kept apart on purpose:
 * <ul>
 *     <li>the normal value, which is what the player set and what gets saved under "settings";</li>
 *     <li>the PvP override, which only applies while PvP mode is on and is saved separately under "pvp".</li>
 * </ul>
 * {@link #get()} picks the right one. Nothing in the override layer ever writes into the normal value,
 * so turning PvP mode off restores the player's values exactly.
 */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	private String description = "";
	private final T defaultValue;
	private T value;

	/** Override declared by the module itself (null = no default override). */
	private T defaultPvpValue;
	/** Current override (null = none). */
	private T pvpValue;

	private boolean hidden;
	private BooleanSupplier visibility = () -> true;
	private final List<Consumer<T>> listeners = new ArrayList<>(1);
	private Runnable dirtyHook = () -> {
	};

	protected Setting(String id, String name, T defaultValue) {
		this.id = Objects.requireNonNull(id);
		this.name = Objects.requireNonNull(name);
		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.value = defaultValue;
	}

	// ---- builder-style configuration (used while declaring a module) ----
	// These return the caller's concrete type (inferred from the assignment), so declarations need no casts:
	//   public final SliderSetting scale = add(new SliderSetting(...).description("..."));

	@SuppressWarnings("unchecked")
	private <S extends Setting<T>> S self() {
		return (S) this;
	}

	public <S extends Setting<T>> S description(String description) {
		this.description = description;
		return self();
	}

	/** Declares this module's default PvP-mode value for the setting. */
	public <S extends Setting<T>> S pvpDefault(T value) {
		if (!supportsPvpOverride()) {
			throw new IllegalStateException(id + " can't have a PvP override");
		}
		this.defaultPvpValue = sanitize(value);
		this.pvpValue = this.defaultPvpValue;
		return self();
	}

	/** Not shown in settings UIs (used for stored state like HUD positions). */
	public <S extends Setting<T>> S hidden() {
		this.hidden = true;
		return self();
	}

	public <S extends Setting<T>> S visibleWhen(BooleanSupplier visibility) {
		this.visibility = visibility;
		return self();
	}

	public <S extends Setting<T>> S onChange(Consumer<T> listener) {
		listeners.add(listener);
		return self();
	}

	/** Called by the owner so that every change schedules a config save. */
	public void setDirtyHook(Runnable dirtyHook) {
		this.dirtyHook = dirtyHook;
	}

	// ---- values ----

	/** The effective value: the PvP override while PvP mode is on (if there is one), otherwise the normal value. */
	public T get() {
		T override = pvpValue;
		if (override != null && PvpMode.isActive()) {
			return override;
		}
		return value;
	}

	/** The player's normal value, ignoring PvP mode. */
	public T getNormal() {
		return value;
	}

	/** Sets the normal value. */
	public void set(T newValue) {
		newValue = sanitize(Objects.requireNonNull(newValue));
		if (newValue.equals(value)) {
			return;
		}
		value = newValue;
		changed();
	}

	/**
	 * Sets the value for the mode the player is in. In PvP mode this writes the PvP override (creating one
	 * if needed), so leaving PvP mode brings the normal value back untouched. Used by quick controls
	 * (mod card switches, toggle keys, the layout editor).
	 */
	public void setEffective(T newValue) {
		if (supportsPvpOverride() && PvpMode.isActive()) {
			setPvpOverride(newValue);
		} else {
			set(newValue);
		}
	}

	/** Back to the default for the mode the player is in (in PvP mode: the module's default override, often "same as normal"). */
	public void resetEffective() {
		if (supportsPvpOverride() && PvpMode.isActive()) {
			setPvpOverride(defaultPvpValue);
		} else {
			set(defaultValue);
		}
	}

	public T getDefault() {
		return defaultValue;
	}

	public boolean hasPvpOverride() {
		return pvpValue != null;
	}

	/** The override value, or null if there is none. */
	public T getPvpOverride() {
		return pvpValue;
	}

	public T getDefaultPvpOverride() {
		return defaultPvpValue;
	}

	public void setPvpOverride(T override) {
		if (!supportsPvpOverride()) {
			return;
		}
		T sanitized = override == null ? null : sanitize(override);
		if (Objects.equals(sanitized, pvpValue)) {
			return;
		}
		pvpValue = sanitized;
		changed();
	}

	public void clearPvpOverride() {
		setPvpOverride(null);
	}

	/** True if this setting has an override and PvP mode is in effect, i.e. {@link #get()} returns the override. */
	public boolean isOverridden() {
		return pvpValue != null && PvpMode.isActive();
	}

	/** Back to the module's defaults, both layers. Does not fire change listeners (used before loading). */
	public void resetSilently() {
		value = defaultValue;
		pvpValue = defaultPvpValue;
	}

	public void reset() {
		boolean differs = !value.equals(defaultValue) || !Objects.equals(pvpValue, defaultPvpValue);
		resetSilently();
		if (differs) {
			changed();
		}
	}

	protected void changed() {
		T effective = get();
		for (Consumer<T> listener : listeners) {
			listener.accept(effective);
		}
		dirtyHook.run();
	}

	/** Re-fires listeners with the effective value (used when PvP mode flips). */
	public void notifyEffectiveChanged() {
		T effective = get();
		for (Consumer<T> listener : listeners) {
			listener.accept(effective);
		}
	}

	// ---- metadata ----

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String descriptionText() {
		return description;
	}

	public boolean isHidden() {
		return hidden;
	}

	public boolean isVisible() {
		return !hidden && visibility.getAsBoolean();
	}

	public boolean supportsPvpOverride() {
		return true;
	}

	// ---- config (de)serialisation, used by ConfigManager; these don't fire listeners ----

	public void loadNormal(JsonElement json) {
		value = sanitize(Objects.requireNonNull(fromJson(json)));
	}

	/** A JSON null means "the player removed the override". */
	public void loadPvp(JsonElement json) {
		if (!supportsPvpOverride()) {
			return;
		}
		pvpValue = json == null || json.isJsonNull() ? null : sanitize(fromJson(json));
	}

	public JsonElement normalJson() {
		return toJson(value);
	}

	/** Only written when there's something to remember (an override, or a removed default override). */
	public boolean shouldWritePvp() {
		return supportsPvpOverride() && (pvpValue != null || defaultPvpValue != null);
	}

	public JsonElement pvpJson() {
		return pvpValue == null ? JsonNull.INSTANCE : toJson(pvpValue);
	}

	// ---- per-type behaviour ----

	/** Clamp/normalise a value before it is stored. */
	protected T sanitize(T value) {
		return value;
	}

	public abstract JsonElement toJson(T value);

	/** Parses a stored value. Throws on anything malformed; the caller falls back to the default. */
	public abstract T fromJson(JsonElement json);
}
