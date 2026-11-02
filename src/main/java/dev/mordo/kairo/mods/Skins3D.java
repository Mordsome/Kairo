package dev.mordo.kairo.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.mods.skins.SkinVoxels;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Skin layers in 3D: the hat, jacket, sleeves and pants are rebuilt from little cubes instead of flat textures.
 * Purely visual and client-side. Pieces covered by armour stay flat (they'd poke through it otherwise).
 */
public final class Skins3D extends Module {
	public final BooleanSetting hat = add(new BooleanSetting("hat", "Hat", true));
	public final BooleanSetting jacket = add(new BooleanSetting("jacket", "Jacket", true));
	public final BooleanSetting sleeves = add(new BooleanSetting("sleeves", "Sleeves", true));
	public final BooleanSetting pants = add(new BooleanSetting("pants", "Pants", true));
	public final SliderSetting thickness = add(new SliderSetting("thickness", "Thickness", 1.0, 0.5, 1.5, 0.05).multiplier()
		.description("How far the 3D pixels stand out"));
	public final SliderSetting distance = add(new SliderSetting("distance", "Render distance", 24, 4, 64, 1).unit(" blocks")
		.description("Further away the flat layers are used (cheaper)"));

	/** Test hook (visual tour only): build the 3D layers from this texture instead of the player's skin. */
	public static net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier testTexture;

	/** Which layers this state draws in 3D (bit per {@link SkinVoxels.Layer}); 0 = none. */
	public static final RenderStateDataKey<Integer> LAYERS = RenderStateDataKey.create(() -> "kairo:3d_skin_layers");

	public Skins3D() {
		super("skins_3d", "3D Skins", "Hat, jacket, sleeves and pants in 3D", Category.VISUAL, Icon.USER, true);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer3D((RenderLayerParent) avatar, this));
			}
		});
	}

	/**
	 * Called right after the player's render state is built: hides the flat layers this mod will draw in 3D and
	 * remembers which ones they were.
	 */
	public void apply(AvatarRenderState state) {
		int mask = 0;
		if (isEnabled() && !state.isInvisible && !state.isSpectator && state.distanceToCameraSq <= distance.floatValue() * distance.floatValue()) {
			boolean helmet = !state.headEquipment.isEmpty();
			boolean chest = !state.chestEquipment.isEmpty();
			boolean legs = !state.legsEquipment.isEmpty();
			if (hat.isOn() && state.showHat && !helmet) {
				state.showHat = false;
				mask |= bit(SkinVoxels.Layer.HAT);
			}
			if (jacket.isOn() && state.showJacket && !chest) {
				state.showJacket = false;
				mask |= bit(SkinVoxels.Layer.JACKET);
			}
			if (sleeves.isOn() && !chest) {
				if (state.showLeftSleeve) {
					state.showLeftSleeve = false;
					mask |= bit(SkinVoxels.Layer.LEFT_SLEEVE);
				}
				if (state.showRightSleeve) {
					state.showRightSleeve = false;
					mask |= bit(SkinVoxels.Layer.RIGHT_SLEEVE);
				}
			}
			if (pants.isOn() && !legs) {
				if (state.showLeftPants) {
					state.showLeftPants = false;
					mask |= bit(SkinVoxels.Layer.LEFT_PANTS);
				}
				if (state.showRightPants) {
					state.showRightPants = false;
					mask |= bit(SkinVoxels.Layer.RIGHT_PANTS);
				}
			}
		}
		((FabricRenderState) state).setData(LAYERS, mask);
	}

	private static int bit(SkinVoxels.Layer layer) {
		return 1 << layer.ordinal();
	}

	/** Draws the 3D layers on top of the player model, following each body part's pose. */
	private static final class Layer3D extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final Skins3D mod;

		Layer3D(RenderLayerParent<AvatarRenderState, PlayerModel> parent, Skins3D mod) {
			super(parent);
			this.mod = mod;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			Integer mask = ((FabricRenderState) state).getData(LAYERS);
			if (mask == null || mask == 0) {
				return;
			}
			Identifier texture = testTexture != null ? testTexture : state.skin.body().texturePath();
			boolean slim = state.skin.model() == PlayerModelType.SLIM;
			float t = mod.thickness.floatValue();
			SkinVoxels.Mesh[] meshes = SkinVoxels.meshes(texture, slim, 1.0f * t, 0.75f * t);
			if (meshes == null) {
				return;
			}
			PlayerModel model = getParentModel();
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
			for (SkinVoxels.Layer layer : SkinVoxels.Layer.values()) {
				if ((mask & bit(layer)) == 0) {
					continue;
				}
				ModelPart part = switch (layer) {
					case HAT -> model.head;
					case JACKET -> model.body;
					case LEFT_SLEEVE -> model.leftArm;
					case RIGHT_SLEEVE -> model.rightArm;
					case LEFT_PANTS -> model.leftLeg;
					case RIGHT_PANTS -> model.rightLeg;
				};
				if (!part.visible) {
					continue;
				}
				SkinVoxels.Mesh mesh = meshes[layer.ordinal()];
				if (mesh.count() == 0) {
					continue;
				}
				poseStack.pushPose();
				model.root().translateAndRotate(poseStack);
				part.translateAndRotate(poseStack);
				collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, buffer) -> {
					float[] q = mesh.quads();
					for (int i = 0; i < mesh.count(); i++) {
						int o = i * 17;
						float nx = q[o + 12];
						float ny = q[o + 13];
						float nz = q[o + 14];
						float u = q[o + 15];
						float v = q[o + 16];
						for (int c = 0; c < 4; c++) {
							buffer.addVertex(pose, q[o + c * 3] / 16f, q[o + c * 3 + 1] / 16f, q[o + c * 3 + 2] / 16f)
								.setColor(-1)
								.setUv(u, v)
								.setOverlay(overlay)
								.setLight(light)
								.setNormal(pose, nx, ny, nz);
						}
					}
				});
				poseStack.popPose();
			}
		}
	}
}
