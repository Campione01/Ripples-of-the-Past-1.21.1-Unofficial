package rotp.addons.catchtherainbow.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public final class CatchTheRainbowWearableLayer
        extends RenderLayer<
                AbstractClientPlayer,
                PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE =
            CatchTheRainbowAddon.id(
                    "textures/models/armor/"
                            + "catch_the_rainbow_mask_item_layer_1.png");

    private final CatchTheRainbowMaskModel maskModel;

    public CatchTheRainbowWearableLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            CatchTheRainbowMaskModel maskModel) {
        super(parent);
        this.maskModel = maskModel;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!ClientGlobals.canSeeStands
                || !player.getItemBySlot(EquipmentSlot.HEAD)
                        .is(CatchTheRainbowAddon.MASK_ITEM.get())) {
            return;
        }
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(player);
        if (controller == null || controller.clientStuff == null) {
            return;
        }
        float lifecycleAlpha =
                controller.clientStuff.getAlpha(
                        controller, partialTick);
        StandVisualContext visualContext =
                StandVisualContext.world(
                        controller, lifecycleAlpha);
        if (visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        PlayerModel<AbstractClientPlayer> parent =
                getParentModel();
        maskModel.setAllVisible(false);
        maskModel.head.visible = true;
        maskModel.head.copyFrom(parent.head);
        maskModel.young = parent.young;
        maskModel.crouching = parent.crouching;
        maskModel.riding = parent.riding;
        maskModel.attackTime = parent.attackTime;

        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(TEXTURE));
        maskModel.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        player, 0.0F),
                0xFFFFFFFF);
    }
}
