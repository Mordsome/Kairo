package dev.mordo.kairo.mods;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLMisc;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * After a screenshot: a small preview card, and [Copy] [Open] [Folder] [Delete] buttons on the chat message.
 * Copy puts the image itself on the clipboard; Delete sends the file to the Recycle Bin (so it can be undone).
 * The buttons are chat click events with a private prefix that {@code ScreenClickMixin} handles locally.
 */
public final class Screenshots extends Module {
	/** Click events whose "copy" value starts with this are client actions, never copied as text. */
	public static final String ACTION_PREFIX = "kairo-action:";
	private static final Identifier PREVIEW = ClientInfo.id("screenshot_preview");
	private static final long PREVIEW_MS = 4500;

	public final BooleanSetting chatButtons = add(new BooleanSetting("chat_buttons", "Chat buttons", true)
		.description("Copy, Open, Folder and Delete on the \"screenshot saved\" message"));
	public final BooleanSetting previewCard = add(new BooleanSetting("preview", "Preview card", true)
		.description("A small thumbnail in the corner for a few seconds"));
	public final BooleanSetting autoCopy = add(new BooleanSetting("auto_copy", "Copy automatically", false)
		.description("Every new screenshot goes straight to the clipboard"));

	private @Nullable DynamicTexture preview;
	private int previewW;
	private int previewH;
	private long previewStart;
	private String previewName = "";
	private final Animation previewAnim = new Animation(0);

	public Screenshots() {
		super("screenshots", "Screenshots", "Copy, open or delete right after taking one", Category.UTILITY, Icon.CAMERA, true);
	}

	@Override
	public void init() {
		HudElementRegistry.addLast(ClientInfo.id("screenshot_card"), (g, delta) -> drawPreview(g));
	}

	// ---- the chat message ----

	/** Wraps the "screenshot saved" message consumer (see ScreenshotMixin). */
	public Consumer<Component> wrap(Consumer<Component> original) {
		return message -> {
			File file = isEnabled() ? savedFile(message) : null;
			if (file == null) {
				original.accept(message);
				return;
			}
			original.accept(chatButtons.isOn() ? buildMessage(file) : message);
			if (previewCard.isOn()) {
				loadPreview(file);
			}
			if (autoCopy.isOn()) {
				copy(file);
			}
		};
	}

	/** The file from vanilla's "Saved screenshot as X" message (its name carries an open-file click event). */
	private static @Nullable File savedFile(Component message) {
		if (!(message.getContents() instanceof TranslatableContents tr) || !tr.getKey().equals("screenshot.success")) {
			return null;
		}
		for (Object arg : tr.getArgs()) {
			if (arg instanceof Component c && c.getStyle().getClickEvent() instanceof ClickEvent.OpenFile open) {
				return open.file();
			}
		}
		return null;
	}

	private static Component buildMessage(File file) {
		String path = file.getAbsolutePath();
		MutableComponent msg = Component.literal("Screenshot saved ").withStyle(ChatFormatting.GRAY);
		msg.append(Component.literal(file.getName()).withStyle(Style.EMPTY.withUnderlined(true).withColor(ChatFormatting.WHITE)
			.withClickEvent(new ClickEvent.OpenFile(file))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open")))));
		msg.append(Component.literal("  "));
		msg.append(button("Copy", "copy", path, "Copy the image to the clipboard", 0x7FD8FF));
		msg.append(Component.literal(" "));
		msg.append(button("Open", "open", path, "Open in your image viewer", 0x7FD8FF));
		msg.append(Component.literal(" "));
		msg.append(button("Folder", "folder", path, "Open the screenshots folder", 0x7FD8FF));
		msg.append(Component.literal(" "));
		msg.append(button("Delete", "delete", path, "Move to the Recycle Bin", 0xFF8A8A));
		return msg;
	}

	private static Component button(String label, String action, String path, String hover, int color) {
		return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color)
			.withClickEvent(new ClickEvent.CopyToClipboard(ACTION_PREFIX + action + ":" + path))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** Runs a chat-button action. Returns true if {@code value} was one of ours. */
	public static boolean handleAction(String value) {
		if (!value.startsWith(ACTION_PREFIX)) {
			return false;
		}
		String rest = value.substring(ACTION_PREFIX.length());
		int colon = rest.indexOf(':');
		if (colon < 0) {
			return true;
		}
		String action = rest.substring(0, colon);
		File file = new File(rest.substring(colon + 1));
		if (!action.equals("folder") && !file.isFile()) {
			Toasts.show("That screenshot isn't there any more", 0xFFFF7A7A);
			return true;
		}
		switch (action) {
			case "copy" -> copy(file);
			case "open" -> openFile(file);
			case "folder" -> FilePicker.openFolder(file.getParentFile().toPath());
			case "delete" -> delete(file);
			default -> {
			}
		}
		return true;
	}

	// ---- actions ----

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static String quote(String s) {
		return "'" + s.replace("'", "''") + "'";
	}

	/** Puts the image (not the path) on the clipboard, through Windows' own clipboard API. */
	private static void copy(File file) {
		if (!windows()) {
			Toasts.show("Copying images works on Windows only", 0xFFFF7A7A);
			return;
		}
		String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
			+ "$i = [System.Drawing.Image]::FromFile(" + quote(file.getAbsolutePath()) + "); "
			+ "[System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()";
		runPowerShell(script, "Copied to clipboard", "Couldn't copy the screenshot");
	}

	private static void delete(File file) {
		if (!windows()) {
			Toasts.show("Deleting to the bin works on Windows only", 0xFFFF7A7A);
			return;
		}
		String script = "Add-Type -AssemblyName Microsoft.VisualBasic; "
			+ "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile(" + quote(file.getAbsolutePath()) + ", 'OnlyErrorDialogs', 'SendToRecycleBin')";
		runPowerShell(script, "Moved to the Recycle Bin", "Couldn't delete the screenshot");
	}

	private static void openFile(File file) {
		try {
			if (!SDLMisc.SDL_OpenURL(file.toURI().toString())) {
				Toasts.show("Couldn't open the screenshot", 0xFFFF7A7A);
			}
		} catch (Throwable t) {
			Kairo.LOGGER.warn("Couldn't open {}", file, t);
			Toasts.show("Couldn't open the screenshot", 0xFFFF7A7A);
		}
	}

	private static void runPowerShell(String script, String success, String failure) {
		Minecraft mc = Minecraft.getInstance();
		Util.ioPool().execute(() -> {
			boolean ok;
			try {
				Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden", "-Command", script)
					.redirectErrorStream(true).start();
				p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
				ok = p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
			} catch (Exception e) {
				Kairo.LOGGER.warn("PowerShell action failed", e);
				ok = false;
			}
			boolean done = ok;
			mc.execute(() -> Toasts.show(done ? success : failure, done ? Theme.accent() : 0xFFFF7A7A));
		});
	}

	// ---- preview card ----

	private void loadPreview(File file) {
		Minecraft mc = Minecraft.getInstance();
		Util.ioPool().execute(() -> {
			try {
				BufferedImage full = ImageIO.read(file);
				if (full == null) {
					return;
				}
				int w = 240;
				int h = Math.max(1, Math.round(w * full.getHeight() / (float) full.getWidth()));
				BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = small.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(full, 0, 0, w, h, null);
				g.dispose();
				int[] argb = small.getRGB(0, 0, w, h, null, 0, w);
				mc.execute(() -> showPreview(argb, w, h, file.getName()));
			} catch (Exception e) {
				Kairo.LOGGER.debug("No screenshot preview", e);
			}
		});
	}

	private void showPreview(int[] argb, int w, int h, String name) {
		NativeImage image = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setPixel(x, y, argb[y * w + x] | 0xFF000000);
			}
		}
		if (preview != null) {
			preview.close();
		}
		preview = new DynamicTexture(() -> "Kairo screenshot preview", image);
		Minecraft.getInstance().getTextureManager().register(PREVIEW, preview);
		previewW = w;
		previewH = h;
		previewName = name;
		previewStart = System.currentTimeMillis();
		previewAnim.set(0).animateTo(1, 360, Easing.UI);
	}

	private void drawPreview(GuiGraphicsExtractor g) {
		if (preview == null || !previewCard.isOn()) {
			return;
		}
		long age = System.currentTimeMillis() - previewStart;
		if (age > PREVIEW_MS && previewAnim.target() > 0) {
			previewAnim.animateTo(0, 300, Easing.IN_CUBIC);
		}
		float a = previewAnim.value();
		if (a <= 0.01f) {
			return;
		}
		float w = 100;
		float h = w * previewH / previewW;
		float pad = 5;
		float cardW = w + pad * 2;
		float cardH = h + pad * 2 + 22;
		float x = g.guiWidth() - cardW - 6 + Animation.motion((1 - a) * 30, 0);
		float y = 6;
		Shapes.shadow(g, x, y + 3, cardW, cardH, 7, 8, Colors.argb(0.4f * a, 0));
		Shapes.rect(g, x, y, cardW, cardH, 7, Colors.argb(0.85f * a, dev.mordo.kairo.gui.Theme.tintBackground(0xFF0C1018)), Colors.argb(0.2f * a, 0xFFFFFF), 1);
		g.blit(PREVIEW, Math.round(x + pad), Math.round(y + pad), Math.round(x + pad + w), Math.round(y + pad + h), 0, 1, 0, 1);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Screenshot saved", x + pad + 1, y + pad + h + 7, 6.5f, Colors.argb(a, 0xF4F8FB));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "T: copy · open · delete", x + pad + 1, y + pad + h + 16, 5.5f, Colors.argb(0.6f * a, 0xF4F8FB));
	}
}
