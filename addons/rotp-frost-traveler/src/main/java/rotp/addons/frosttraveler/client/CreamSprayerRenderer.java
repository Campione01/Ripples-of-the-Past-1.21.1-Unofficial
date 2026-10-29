package rotp.addons.frosttraveler.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.item.SprayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

public final class CreamSprayerRenderer
        extends BlockEntityWithoutLevelRenderer {
    public CreamSprayerRenderer() {
        super(
                Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        float alpha = 1.0F;
        if (displayContext != ItemDisplayContext.GUI) {
            Player owner = data != null
                    && minecraft.level != null
                    ? minecraft.level.getPlayerByUUID(
                            data.ownerUuid())
                    : null;
            StandVisualContext visualContext = owner != null
                    ? CreamVisuals.context(owner, 1.0F)
                    : null;
            if (!ClientGlobals.canSeeStands
                    || visualContext == null
                    || visualContext.effectiveAlpha() <= 0.0F) {
                return;
            }
            alpha = visualContext.effectiveAlpha();
        }
        BakedModel model = minecraft.getModelManager().getModel(
                data != null
                        && data.standSkin().endsWith(":pkmn")
                        ? FrostTravelerClientEvents
                                .SPRAYER_PKMN_MODEL
                        : FrostTravelerClientEvents.SPRAYER_MODEL);
        boolean leftHand =
                displayContext
                                == ItemDisplayContext
                                        .FIRST_PERSON_LEFT_HAND
                        || displayContext
                                == ItemDisplayContext
                                        .THIRD_PERSON_LEFT_HAND;
        poseStack.pushPose();
        try {
            // ItemRenderer centered the builtin/entity bridge. Recreate the
            // selected baked model's normal transform order inside it.
            poseStack.translate(0.5F, 0.5F, 0.5F);
            model = ClientHooks.handleCameraTransforms(
                    poseStack, model, displayContext, leftHand);
            poseStack.translate(-0.5F, -0.5F, -0.5F);

            MultiBufferSource alphaBuffer =
                    AlphaMultiBufferSource.wrap(buffer, alpha);
            VertexConsumer vertices = alphaBuffer.getBuffer(
                    CreamVisuals.renderType(
                            TextureAtlas.LOCATION_BLOCKS));
            minecraft.getItemRenderer().renderModelLists(
                    model,
                    stack,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    vertices);
        }
        finally {
            poseStack.popPose();
        }
    }
}
