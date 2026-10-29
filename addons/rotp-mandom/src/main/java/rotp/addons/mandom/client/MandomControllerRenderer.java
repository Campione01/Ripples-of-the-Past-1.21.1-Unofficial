package rotp.addons.mandom.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.mandom.MandomAddon;
import rotp.addons.mandom.entity.MandomStandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renderer owner for the donor zero-offset Stand controller.
 *
 * <p>The controller stays fully invisible during normal play. The matching
 * user layer supplies the visible arm attachment while this renderer keeps
 * the production Stand renderer identity and a safe visible fallback.</p>
 */
public final class MandomControllerRenderer
        extends EntityRenderer<MandomStandEntity> {
    private static final ResourceLocation TEXTURE =
            MandomAddon.id("textures/entity/stand/mandom.png");

    private final MandomAttachmentModel model;

    public MandomControllerRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        model = new MandomAttachmentModel(context.bakeLayer(
                MandomAttachmentModel.LAYER_LOCATION));
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            MandomStandEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            MandomStandEntity controller,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        LivingEntity user = controller.getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        if (!ClientGlobals.canSeeStands
                || user == null
                || power == null
                || power.getPowerType() != MandomAddon.MANDOM.get()
                || controller.clientStuff == null
                || controller.isInvisible()) {
            return;
        }

        float lifecycleAlpha = controller.clientStuff.getAlpha(
                controller, partialTick);
        StandVisualContext visualContext = StandVisualContext.world(
                controller, lifecycleAlpha);
        if (visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        StandSkin skin =
                StandSkinsLoader.getInstance().getSkin(power);
        ResourceLocation texture = skin != null
                ? skin.getTexture(TEXTURE, TEXTURE)
                : TEXTURE;
        MultiBufferSource alphaBuffer = AlphaMultiBufferSource.wrap(
                buffer, visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(texture));

        poseStack.pushPose();
        float bodyRotation = Mth.rotLerp(
                partialTick, user.yBodyRotO, user.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                180.0F - bodyRotation));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.resetRightArmPose();
        model.setupAnim(controller.tickCount + partialTick);
        model.render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
