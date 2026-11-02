package dev.mordo.kairo.mods.totem;

import dev.mordo.kairo.core.ClientInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A small resource pack the client writes itself (config/kairo/pack). It's always on, sits on top of every
 * other pack, and only contains what the player changed (right now: the totem texture). Empty = vanilla look.
 */
public final class TotemPack {
	public static final String ID = ClientInfo.MOD_ID + ":client";
	private static final Path ROOT = ClientInfo.configDir("pack");
	public static final Path TOTEM_TEXTURE = ROOT.resolve("assets/minecraft/textures/item/totem_of_undying.png");

	/** The pack's folder (the one with assets/ in it). */
	public static Path root() {
		return ROOT;
	}

	/** Where a custom title panorama face goes (replaces Minecraft's). */
	public static Path panoramaFace(int index) {
		return ROOT.resolve("assets/minecraft/textures/gui/title/background/panorama_" + index + ".png");
	}

	private static final PackLocationInfo LOCATION = new PackLocationInfo(ID, Component.literal(ClientInfo.NAME),
		PackSource.BUILT_IN, Optional.empty());
	/** Required (always selected), on top, fixed (can't be moved under another pack). */
	private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);

	/** Added to the client's pack repository by MinecraftMixin. */
	public static final RepositorySource SOURCE = consumer -> {
		try {
			Files.createDirectories(ROOT.resolve("assets"));
		} catch (IOException ignored) {
			// PathPackResources copes with a missing folder
		}
		Pack.Metadata metadata = new Pack.Metadata(Component.literal("Your totem look and other client tweaks"),
			PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
		consumer.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
			@Override
			public PackMetadataResources openMetadata(PackLocationInfo location) {
				return new PathPackResources(location, ROOT);
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata meta) {
				return Stream.of(new PathPackResources(location, ROOT));
			}
		}, metadata, SELECTION));
	};

	private TotemPack() {
	}
}
