package dev.mordo.kairo.mods.waypoints;

/** One of the player's own markers. Coordinates are block positions in {@link #dimension}. */
public final class Waypoint {
	public String name;
	public int x;
	public int y;
	public int z;
	public String dimension;
	public int color;
	public boolean visible = true;
	/** Created automatically where the player died. */
	public boolean death;
	/** When it was made (ms); 0 for waypoints saved before this was recorded. */
	public long created = System.currentTimeMillis();

	public Waypoint(String name, int x, int y, int z, String dimension, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
	}

	public double distanceTo(double px, double py, double pz) {
		double dx = x + 0.5 - px;
		double dy = y + 0.5 - py;
		double dz = z + 0.5 - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
