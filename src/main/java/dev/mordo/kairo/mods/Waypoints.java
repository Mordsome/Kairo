package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.WidgetHost;
import dev.mordo.kairo.mods.waypoints.Waypoint;
import dev.mordo.kairo.mods.waypoints.WaypointRenderer;
import dev.mordo.kairo.mods.waypoints.WaypointSection;
import dev.mordo.kairo.mods.waypoints.WaypointStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Your own markers: name, distance, optional beam, plus an automatic death point. Stored per world/server
 * and per dimension. Only waypoints you created are shown; nothing about other players or the world is revealed.
 */
public final class Waypoints extends Module implements SettingsList.HasSection {
	public static final int[] PALETTE = {0xFF7FD8FF, 0xFFFF9A52, 0xFF7CFF8A, 0xFFFF6B8B, 0xFFC792FF, 0xFFFFE066, 0xFFFFFFFF};

	public final KeybindSetting openKey = add(new KeybindSetting("open_key", "Open waypoints", Keybind.keyboard(com.mojang.blaze3d.platform.InputConstants.KEY_M))
		.onPress(this::openScreen)
		.<KeybindSetting>description("The waypoints screen: every dimension, sort, copy coordinates"));
	public final dev.mordo.kairo.core.settings.EnumSetting<dev.mordo.kairo.mods.waypoints.WaypointsScreen.Sort> sort = add(
		new dev.mordo.kairo.core.settings.EnumSetting<>("sort", "Sort", dev.mordo.kairo.mods.waypoints.WaypointsScreen.Sort.NEAREST).hidden());
	public final KeybindSetting createKey = add(new KeybindSetting("create_key", "Create waypoint key", Keybind.NONE)
		.onPress(this::createAtPlayer));
	public final BooleanSetting showBeam = add(new BooleanSetting("beam", "Beam", true));
	public final BooleanSetting showDistance = add(new BooleanSetting("distance", "Show distance", true));
	public final BooleanSetting fadeWithDistance = add(new BooleanSetting("fade", "Fade with distance", true));
	public final BooleanSetting clampToEdge = add(new BooleanSetting("clamp", "Keep on screen edge", true)
		.description("Off-screen waypoints stick to the screen edge"));
	public final BooleanSetting deathWaypoint = add(new BooleanSetting("death", "Death waypoint", true));
	public final SliderSetting labelScale = add(new SliderSetting("label_scale", "Label size", 1.0, 0.5, 2.0, 0.05).multiplier());

	private final WaypointStore store = new WaypointStore();
	private final WaypointRenderer renderer = new WaypointRenderer(this);
	private boolean wasDead;
	private int nextColor;

	public Waypoints() {
		super("waypoints", "Waypoints", "Markers, beams and death points", Category.UTILITY, Icon.PIN, true);
	}

	/** Opens the waypoints screen (M by default, or clicking the Waypoints card). */
	public void openScreen() {
		Minecraft.getInstance().gui.setScreen(new dev.mordo.kairo.mods.waypoints.WaypointsScreen(null));
	}

	@Override
	public Runnable openAction() {
		// From the Mods panel: closing the waypoints screen goes back there.
		return () -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new dev.mordo.kairo.mods.waypoints.WaypointsScreen(mc.gui.screen()));
		};
	}

	public WaypointStore store() {
		return store;
	}

	@Override
	public void init() {
		HudElementRegistry.addLast(ClientInfo.id("waypoints"), (g, delta) -> {
			if (isEnabled() && !dev.mordo.kairo.core.PvpSettings.active(dev.mordo.kairo.core.PvpSettings.INSTANCE.hideWaypoints)) {
				renderer.render(g);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> store.save());
	}

	@Override
	public void tick() {
		store.sync();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			wasDead = false;
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead && deathWaypoint.isOn()) {
			addDeathPoint(player);
		}
		wasDead = dead;
	}

	public static String dimensionOf(LocalPlayer player) {
		return player.level().dimension().identifier().toString();
	}

	private void addDeathPoint(LocalPlayer player) {
		// Keep only the latest death point.
		store.all().removeIf(w -> w.death);
		BlockPos pos = player.blockPosition();
		Waypoint death = new Waypoint("Death", pos.getX(), pos.getY(), pos.getZ(), dimensionOf(player), 0xFFFF5A5A);
		death.death = true;
		store.add(death);
	}

	/** Adds a waypoint where the player stands (keybind or the manager's button). */
	public void createAtPlayer() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		store.sync();
		BlockPos pos = player.blockPosition();
		int color = PALETTE[nextColor++ % PALETTE.length];
		store.add(new Waypoint("Waypoint " + (store.all().size() + 1), pos.getX(), pos.getY(), pos.getZ(), dimensionOf(player), color));
		Toasts.show("Waypoint added", Theme.accent());
	}

	@Override
	public SettingsList.Section settingsSection(WidgetHost host) {
		return new WaypointSection(this, host);
	}
}
