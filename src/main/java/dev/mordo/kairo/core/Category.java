package dev.mordo.kairo.core;

public enum Category {
	HUD("HUD"),
	PVP("PvP"),
	VISUAL("Visual"),
	UTILITY("Utility");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
