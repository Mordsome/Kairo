package dev.mordo.kairo.render.background;

import dev.mordo.kairo.Kairo;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

/**
 * Plays an animated GIF on its own thread: frames are decoded in order (honouring frame offsets, delays and
 * disposal), scaled to fit {@code maxW x maxH}, and the newest one is handed over as RGBA pixels.
 * Only two frames are ever in memory, so long or large GIFs are fine. Pauses while nobody is looking.
 */
final class GifPlayer {
	private final File file;
	private final int maxW;
	private final int maxH;
	private volatile boolean running = true;
	private volatile long lastWanted = System.currentTimeMillis();

	private volatile int[] latest;
	private volatile long version;
	private int width;
	private int height;
	private volatile boolean failed;

	GifPlayer(File file, int maxW, int maxH) {
		this.file = file;
		this.maxW = maxW;
		this.maxH = maxH;
		Thread thread = new Thread(this::run, "Kairo GIF background");
		thread.setDaemon(true);
		thread.setPriority(Thread.NORM_PRIORITY - 1);
		thread.start();
	}

	void stop() {
		running = false;
	}

	/** Called while the background is on screen; the player idles when it hasn't been for a moment. */
	void wanted() {
		lastWanted = System.currentTimeMillis();
	}

	boolean failed() {
		return failed;
	}

	long version() {
		return version;
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}

	/** The newest frame (ABGR ints, i.e. RGBA bytes), or null before the first one. */
	int[] latest() {
		return latest;
	}

	private void run() {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
		if (!readers.hasNext()) {
			failed = true;
			return;
		}
		ImageReader reader = readers.next();
		try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
			reader.setInput(in, false);
			int frames = reader.getNumImages(true);
			int[] logical = logicalSize(reader.getStreamMetadata());
			BufferedImage first = reader.read(0);
			int canvasW = logical != null ? logical[0] : first.getWidth();
			int canvasH = logical != null ? logical[1] : first.getHeight();
			float fit = Math.min(1, Math.min(maxW / (float) canvasW, maxH / (float) canvasH));
			width = Math.max(1, Math.round(canvasW * fit));
			height = Math.max(1, Math.round(canvasH * fit));

			BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
			BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			int[][] buffers = {new int[width * height], new int[width * height]};
			int which = 0;
			BufferedImage previous = null;

			while (running) {
				for (int i = 0; i < frames && running; i++) {
					while (running && System.currentTimeMillis() - lastWanted > 1500) {
						Thread.sleep(120); // not on screen: hold this frame
					}
					long start = System.currentTimeMillis();
					BufferedImage frame = i == 0 && first != null ? first : reader.read(i);
					first = null;
					FrameInfo info = frameInfo(reader.getImageMetadata(i));

					String disposal = info.disposal();
					BufferedImage snapshot = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
					Graphics2D g = canvas.createGraphics();
					g.drawImage(frame, info.x(), info.y(), null);
					g.dispose();

					// Publish the composed frame, scaled.
					Graphics2D s = scaled.createGraphics();
					s.setComposite(AlphaComposite.Src);
					s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					s.drawImage(canvas, 0, 0, width, height, null);
					s.dispose();
					int[] out = buffers[which];
					scaled.getRGB(0, 0, width, height, out, 0, width);
					for (int p = 0; p < out.length; p++) {
						int c = out[p];
						// ARGB -> ABGR (RGBA bytes); background shows black under transparent pixels.
						out[p] = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
					}
					latest = out;
					version++;
					which ^= 1;

					// Dispose before the next frame.
					if (disposal.equals("restoreToBackgroundColor")) {
						Graphics2D clear = canvas.createGraphics();
						clear.setComposite(AlphaComposite.Clear);
						clear.fillRect(info.x(), info.y(), frame.getWidth(), frame.getHeight());
						clear.dispose();
					} else if (snapshot != null) {
						canvas = snapshot;
					}
					previous = frame;

					long delay = info.delayMs() <= 10 ? 100 : info.delayMs();
					long sleep = delay - (System.currentTimeMillis() - start);
					if (sleep > 0) {
						Thread.sleep(sleep);
					}
				}
				if (frames <= 1) {
					return; // a still GIF: one frame is enough
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't play GIF background {}", file, e);
			failed = true;
		} finally {
			reader.dispose();
		}
	}

	private static BufferedImage copy(BufferedImage source) {
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setComposite(AlphaComposite.Src);
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return out;
	}

	private record FrameInfo(int x, int y, long delayMs, String disposal) {
	}

	private static FrameInfo frameInfo(IIOMetadata metadata) {
		int x = 0;
		int y = 0;
		long delay = 100;
		String disposal = "none";
		Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
		for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
			switch (n.getNodeName()) {
				case "ImageDescriptor" -> {
					x = intAttr(n, "imageLeftPosition", 0);
					y = intAttr(n, "imageTopPosition", 0);
				}
				case "GraphicControlExtension" -> {
					delay = intAttr(n, "delayTime", 10) * 10L;
					Node d = n.getAttributes().getNamedItem("disposalMethod");
					if (d != null) {
						disposal = d.getNodeValue();
					}
				}
				default -> {
				}
			}
		}
		return new FrameInfo(x, y, delay, disposal);
	}

	private static int[] logicalSize(IIOMetadata stream) {
		if (stream == null) {
			return null;
		}
		Node root = stream.getAsTree("javax_imageio_gif_stream_1.0");
		for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals("LogicalScreenDescriptor")) {
				int w = intAttr(n, "logicalScreenWidth", 0);
				int h = intAttr(n, "logicalScreenHeight", 0);
				return w > 0 && h > 0 ? new int[]{w, h} : null;
			}
		}
		return null;
	}

	private static int intAttr(Node node, String name, int fallback) {
		Node attr = node.getAttributes().getNamedItem(name);
		if (attr == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(attr.getNodeValue());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
