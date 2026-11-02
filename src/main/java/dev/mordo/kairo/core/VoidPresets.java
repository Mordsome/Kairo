package dev.mordo.kairo.core;

import dev.mordo.kairo.core.VoidSettings.Atmosphere;
import dev.mordo.kairo.core.VoidSettings.CrystalStyle;
import dev.mordo.kairo.core.VoidSettings.HudStyle;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.hud.HudElement;

import java.util.List;

/**
 * Kairo Void presets: a whole look in one click (accent colour, atmosphere, HUD style, how round the HUD and buttons
 * are, crystal style). Everything a preset sets can still be changed afterwards.
 */
public final class VoidPresets {
	public record Preset(String name, String description, int accent, Atmosphere atmosphere, HudStyle hud, float radius,
						 CrystalStyle crystal) {
	}

	public static final List<Preset> ALL = List.of(
		new Preset("Void", "Violet glow, clear skies, softly rounded", 0xFF8B5CF6, Atmosphere.NONE, HudStyle.GLOW, 4, CrystalStyle.PRISM),
		new Preset("Frostbite", "Ice blue neon in a white-out blizzard", 0xFF7FD8FF, Atmosphere.BLIZZARD, HudStyle.NEON, 6, CrystalStyle.HOLO),
		new Preset("Hellfire", "Burning orange under an ash-filled sky", 0xFFFF5A1F, Atmosphere.INFERNO, HudStyle.GLOW, 2, CrystalStyle.PLASMA),
		new Preset("Nebula", "Magenta light under an open galaxy", 0xFFE04CFF, Atmosphere.GALAXY, HudStyle.PULSE, 8, CrystalStyle.VOID),
		new Preset("Sakura", "Soft pink, round corners, falling petals", 0xFFFF8FC8, Atmosphere.SAKURA, HudStyle.GLOW, 8, CrystalStyle.PRISM),
		new Preset("Retrowave", "Hot pink neon, sharp corners, a striped sun", 0xFFFF2E97, Atmosphere.SYNTHWAVE, HudStyle.NEON, 0, CrystalStyle.HOLO),
		new Preset("Abyssal", "Deep-sea cyan with light rippling below", 0xFF22D3EE, Atmosphere.ABYSS, HudStyle.PULSE, 6, CrystalStyle.VOID),
		new Preset("Venom", "Acid green in a toxic haze", 0xFF84F23A, Atmosphere.TOXIC, HudStyle.NEON, 3, CrystalStyle.PLASMA),
		new Preset("Bloodline", "Crimson under a blood moon", 0xFFE11D48, Atmosphere.BLOOD_MOON, HudStyle.GLOW, 2, CrystalStyle.VOID),
		new Preset("Thunder", "Electric blue-white in a lightning storm", 0xFFB8C6FF, Atmosphere.STORM, HudStyle.PULSE, 4, CrystalStyle.PLASMA),
		new Preset("Clean", "Plain and calm: white accent, no atmosphere", 0xFFE6EEF6, Atmosphere.NONE, HudStyle.CLASSIC, 3, CrystalStyle.VANILLA));

	private VoidPresets() {
	}

	public static void apply(Preset p) {
		ColorSetting accent = ClientSettings.INSTANCE.accent;
		accent.setMode(ColorSetting.Mode.STATIC);
		accent.set(p.accent());
		VoidSettings s = VoidSettings.INSTANCE;
		s.atmosphere.set(p.atmosphere());
		s.hudStyle.set(p.hud());
		s.crystalStyle.set(p.crystal());
		ClientSettings.INSTANCE.buttonRadius.set((double) Math.min(10, p.radius() + 1));
		for (Module module : ModuleManager.modules()) {
			if (module instanceof HudElement hud) {
				hud.cornerRadius.set((double) p.radius());
			}
		}
		s.master.set(true);
	}

	/** True when this preset's colour and atmosphere are what you have now. */
	public static boolean active(Preset p) {
		ColorSetting accent = ClientSettings.INSTANCE.accent;
		return accent.mode() == ColorSetting.Mode.STATIC && (accent.getNormal() & 0xFFFFFF) == (p.accent() & 0xFFFFFF)
			&& VoidSettings.INSTANCE.atmosphere.get() == p.atmosphere() && VoidSettings.INSTANCE.hudStyle.get() == p.hud();
	}
}
