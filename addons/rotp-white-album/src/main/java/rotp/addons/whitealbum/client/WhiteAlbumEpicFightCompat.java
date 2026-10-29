package rotp.addons.whitealbum.client;

import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.registry.RegisterPatchedRenderersEvent;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PPlayerRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

public final class WhiteAlbumEpicFightCompat {
    private static final String SUBSCRIPTION =
            WhiteAlbumAddon.MOD_ID + ":player_layers";

    public static void register() {
        EpicFightClientEventHooks.Registry.MODIFY_PATCHED_ENTITY
                .registerEvent(
                        WhiteAlbumEpicFightCompat::addPlayerLayers,
                        SUBSCRIPTION,
                        0);
    }

    private static void addPlayerLayers(
            RegisterPatchedRenderersEvent.ModifyEntity event) {
        if (event.get(EntityType.PLAYER)
                instanceof PPlayerRenderer renderer) {
            renderer.addCustomLayer(
                    new WhiteAlbumPatchedLayer(false));
            renderer.addCustomLayer(
                    new WhiteAlbumPatchedLayer(true));
        }
    }

    private static final class WhiteAlbumPatchedLayer
            extends PatchedLayer<
                    AbstractClientPlayer,
                    AbstractClientPlayerPatch<
                            AbstractClientPlayer>,
                    PlayerModel<AbstractClientPlayer>,
                    RenderLayer<
                            AbstractClientPlayer,
                            PlayerModel<AbstractClientPlayer>>> {
        private final boolean shield;

        private WhiteAlbumPatchedLayer(boolean shield) {
            this.shield = shield;
        }

        @Override
        public void renderLayer(
                AbstractClientPlayerPatch<
                        AbstractClientPlayer> patch,
                AbstractClientPlayer entity,
                RenderLayer<
                        AbstractClientPlayer,
                        PlayerModel<AbstractClientPlayer>>
                        vanillaLayer,
                PoseStack poseStack,
                MultiBufferSource buffer,
                int packedLight,
                OpenMatrix4f[] poses,
                float bob,
                float yRot,
                float xRot,
                float partialTick) {
            WhiteAlbumVisuals.RenderState state =
                    WhiteAlbumVisuals.resolve(
                            entity, partialTick);
            if (state == null
                    || state.controller().isStandBlocking()
                            != shield) {
                return;
            }

            boolean slim =
                    entity.getSkin().model()
                            == PlayerSkin.Model.SLIM;
            SkinnedMesh mesh = (
                    slim ? Meshes.ALEX : Meshes.BIPED).get();
            ResourceLocation texture = shield
                    ? slim
                            ? WhiteAlbumVisuals.SHIELD_SLIM
                            : WhiteAlbumVisuals.SHIELD_DEFAULT
                    : slim
                            ? WhiteAlbumVisuals.SUIT_SLIM
                            : WhiteAlbumVisuals.SUIT_DEFAULT;
            texture = WhiteAlbumVisuals.resolveTexture(
                    state.controller(), texture);
            mesh.draw(
                    poseStack,
                    buffer,
                    ModRenderTypes.standTranslucent(
                            texture,
                            state.context().classicOutline()),
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    state.context().effectiveAlpha(),
                    LivingEntityRenderer.getOverlayCoords(
                            entity, 0.0F),
                    patch.getArmature(),
                    poses);
        }
    }

    private WhiteAlbumEpicFightCompat() {}
}
