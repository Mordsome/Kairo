package dev.mordo.kairo.api;

/**
 * The entry point of a Kairo addon: a small separate mod that adds things to Kairo and Kairo Void through the
 * sockets in {@link AddonRegistrar}, and never changes Kairo's own code.
 *
 * <p>An addon is a normal Fabric mod jar whose {@code fabric.mod.json} names a class implementing this interface
 * under the {@code kairo:addon} entrypoint, depends on {@code kairo}, and says which version of these sockets it was
 * made for:
 * <pre>
 * "entrypoints": { "kairo:addon": ["com.example.MyAddon"] },
 * "depends": { "kairo": "&gt;=0.3.0" },
 * "custom": { "kairo:addon": { "api": 1, "editions": ["kairo", "void"] } }
 * </pre>
 * Kairo calls {@link #register} once while it starts, after its own mods are registered and before settings are
 * loaded, so everything an addon adds is saved and restored like Kairo's own. The full contract (file names, the
 * catalogue, what the launcher does) is in the Kairo Addons spec.
 *
 * <p>The same rules as Kairo apply: visual and client-side only, no cheats, no automation, nothing sent anywhere.
 */
@FunctionalInterface
public interface KairoAddon {
	void register(AddonRegistrar kairo);
}
