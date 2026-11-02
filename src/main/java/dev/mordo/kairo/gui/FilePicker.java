package dev.mordo.kairo.gui;

import dev.mordo.kairo.Kairo;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLMisc;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * The system's "open file" dialog (through SDL, which the game already uses for its window), plus copying the
 * picked file into one of the client's folders. If the dialog isn't available, the folder opens instead so the
 * player can drop files in by hand.
 */
public final class FilePicker {
	/** Native memory and the callback must stay alive until the dialog answers. */
	private static @Nullable SDL_DialogFileCallback pending;
	private static SDL_DialogFileFilter.@Nullable Buffer pendingFilters;
	private static @Nullable ByteBuffer filterName;
	private static @Nullable ByteBuffer filterPattern;

	private FilePicker() {
	}

	/**
	 * Lets the player pick a file and copies it into {@code folder}. {@code onImported} gets the copied file
	 * on the client thread. {@code patterns} is semicolon-separated extensions, e.g. "png;jpg".
	 */
	public static void importInto(Path folder, String filterLabel, String patterns, Consumer<Path> onImported) {
		Minecraft mc = Minecraft.getInstance();
		if (pending != null) {
			return; // a dialog is already open
		}
		try {
			Files.createDirectories(folder);
			freePending();
			filterName = MemoryUtil.memUTF8(filterLabel);
			filterPattern = MemoryUtil.memUTF8(patterns);
			pendingFilters = SDL_DialogFileFilter.calloc(1);
			pendingFilters.get(0).name(filterName).pattern(filterPattern);
			pending = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
				Path picked = null;
				if (fileList != 0) {
					long first = MemoryUtil.memGetAddress(fileList);
					if (first != 0) {
						picked = Path.of(MemoryUtil.memUTF8(first));
					}
				}
				Path chosen = picked;
				// The callback can come from another thread: finish on the client thread.
				mc.execute(() -> {
					freePending();
					if (chosen != null) {
						copyIn(chosen, folder, onImported);
					}
				});
			});
			SDLDialog.SDL_ShowOpenFileDialog(pending, 0, mc.getWindow().handle(), pendingFilters, (CharSequence) null, false);
		} catch (Throwable t) {
			Kairo.LOGGER.warn("File dialog unavailable, opening the folder instead", t);
			freePending();
			openFolder(folder);
			Toasts.show("Put your files in this folder", Theme.accent());
		}
	}

	/** The system's "choose folder" dialog; {@code onPicked} runs on the client thread. */
	public static void pickFolder(Consumer<Path> onPicked) {
		Minecraft mc = Minecraft.getInstance();
		if (pending != null) {
			return;
		}
		try {
			freePending();
			pending = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
				Path picked = null;
				if (fileList != 0) {
					long first = MemoryUtil.memGetAddress(fileList);
					if (first != 0) {
						picked = Path.of(MemoryUtil.memUTF8(first));
					}
				}
				Path chosen = picked;
				mc.execute(() -> {
					freePending();
					if (chosen != null) {
						onPicked.accept(chosen);
					}
				});
			});
			SDLDialog.SDL_ShowOpenFolderDialog(pending, 0, mc.getWindow().handle(), (CharSequence) null, false);
		} catch (Throwable t) {
			Kairo.LOGGER.warn("Folder dialog unavailable", t);
			freePending();
			Toasts.show("Folder picker unavailable on this system", 0xFFFF7A7A);
		}
	}

	public static void openFolder(Path folder) {
		try {
			Files.createDirectories(folder);
		} catch (IOException ignored) {
			// openPath shows the error itself
		}
		try {
			// SDL opens file:// URLs in the system file manager.
			if (!SDLMisc.SDL_OpenURL(folder.toAbsolutePath().toUri().toString())) {
				Toasts.show("Folder: " + folder.toAbsolutePath(), Theme.accent());
			}
		} catch (Throwable t) {
			Kairo.LOGGER.warn("Couldn't open {}", folder, t);
			Toasts.show("Folder: " + folder.toAbsolutePath(), Theme.accent());
		}
	}

	private static void copyIn(Path source, Path folder, Consumer<Path> onImported) {
		try {
			Path target = folder.resolve(source.getFileName().toString());
			if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
				// Same name already there with different content: add a number.
				String name = source.getFileName().toString();
				int dot = name.lastIndexOf('.');
				String base = dot < 0 ? name : name.substring(0, dot);
				String ext = dot < 0 ? "" : name.substring(dot);
				for (int i = 2; Files.exists(target) && Files.mismatch(source, target) != -1; i++) {
					target = folder.resolve(base + " (" + i + ")" + ext);
				}
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
			onImported.accept(target);
		} catch (IOException e) {
			Kairo.LOGGER.warn("Couldn't import {}", source, e);
			Toasts.show("Couldn't import " + source.getFileName(), 0xFFFF7A7A);
		}
	}

	private static void freePending() {
		if (pending != null) {
			pending.free();
			pending = null;
		}
		if (pendingFilters != null) {
			pendingFilters.free();
			pendingFilters = null;
		}
		if (filterName != null) {
			MemoryUtil.memFree(filterName);
			filterName = null;
		}
		if (filterPattern != null) {
			MemoryUtil.memFree(filterPattern);
			filterPattern = null;
		}
	}
}
