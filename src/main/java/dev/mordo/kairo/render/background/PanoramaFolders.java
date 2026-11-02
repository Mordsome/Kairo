package dev.mordo.kairo.render.background;

import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A custom panorama is a folder with six faces: panorama_0 … panorama_5 (PNG or JPEG), like Minecraft's own. */
public final class PanoramaFolders {
	private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

	private PanoramaFolders() {
	}

	public static @Nullable Path face(Path folder, int index) {
		for (String ext : EXTENSIONS) {
			Path p = folder.resolve("panorama_" + index + ext);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		return null;
	}

	public static boolean isPanorama(Path folder) {
		if (!Files.isDirectory(folder)) {
			return false;
		}
		for (int i = 0; i < 6; i++) {
			if (face(folder, i) == null) {
				return false;
			}
		}
		return true;
	}

	/** The face as PNG bytes (JPEGs are converted; Minecraft only reads PNG textures). */
	static byte[] facePng(Path face) throws IOException {
		if (face.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
			return Files.readAllBytes(face);
		}
		BufferedImage image = ImageIO.read(face.toFile());
		if (image == null) {
			throw new IOException("not an image: " + face);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}
}
