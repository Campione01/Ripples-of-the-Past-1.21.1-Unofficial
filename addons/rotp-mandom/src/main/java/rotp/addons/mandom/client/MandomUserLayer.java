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
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Donor user attachment rendered from the parent model root. */
public final class MandomUserLayer<
        T extends LivingEntity,
        M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE =
            MandomAddon.id("textures/entity/stand/mandom.png");

    private static volatile MandomAttachmentModel firstPersonModel;

    private final MandomAttachmentModel model;

    public MandomUserLayer(
            RenderLayerParent<T, M> parent,
            MandomAttachmentModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T user,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        RenderState state = resolve(user, partialTick);
        if (state == null) {
            return;
        }
        if (getParentModel() instanceof HumanoidModel<?> humanoid) {
            model.copyRightArmPose(humanoid.rightArm);
        }
        else {
            model.resetRightArmPose();
        }
        renderModel(model, poseStack, buffer, packedLight, state);
    }

    static void installFirstPersonModel(
            MandomAttachmentModel model) {
        firstPersonModel = model;
    }

    static void renderFirstPersonAttachment(
            RenderHandEvent event,
            LivingEntity user) {
        MandomAttachmentModel model = firstPersonModel;
        if (model == null || user.isSpectator()) {
            return;
        }
        RenderState state = resolve(
                user, event.getPartialTick());
        if (state == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            applyFirstPersonRightArmTransform(
                    poseStack,
                    event.getEquipProgress(),
                    event.getSwingProgress());
            model.resetRightArmPose();
            renderModel(
                    model,
                    poseStack,
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    state);
        }
        finally {
            poseStack.popPose();
        }
    }

    private static void applyFirstPersonRightArmTransform(
            PoseStack poseStack,
            float equipProgress,
            float swingProgress) {
        float swingRoot = Mth.sqrt(swingProgress);
        float swingX = -0.3F * Mth.sin(
                swingRoot * (float) Math.PI);
        float swingY = 0.4F * Mth.sin(
                swingRoot * (float) (Math.PI * 2.0D));
        float swingZ = -0.4F * Mth.sin(
                swingProgress * (float) Math.PI);
        poseStack.translate(
                swingX + 0.64000005F,
                swingY - 0.6F + equipProgress * -0.6F,
                swingZ - 0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        float swingYaw = Mth.sin(
                swingRoot * (float) Math.PI);
        float swingRoll = Mth.sin(
                swingProgress * swingProgress
                        * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                swingYaw * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                swingRoll * -20.0F));
        poseStack.translate(-1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-135.0F));
        poseStack.translate(5.6F, 0.0F, 0.0F);
    }

    private static void renderModel(
            MandomAttachmentModel model,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            RenderState state) {
        MultiBufferSource alphaBuffer = AlphaMultiBufferSource.wrap(
                buffer, state.visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(state.texture));
        model.setupAnim(
                state.controller.tickCount + state.partialTick);
        model.render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY);
    }

    private static RenderState resolve(
            LivingEntity user, float partialTick) {
        if (!ClientGlobals.canSeeStands) {
            return null;
        }
        MandomStandEntity controller = MandomAddon.controller(user);
        StandPower power = StandPower.get(user);
        if (controller == null
                || power == null
                || controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff.getAlpha(
                controller, partialTick);
        StandVisualContext visualContext = StandVisualContext.world(
                controller, lifecycleAlpha);
        if (visualContext.effectiveAlpha() <= 0.0F) {
            return null;
        }
        StandSkin skin =
                StandSkinsLoader.getInstance().getSkin(power);
        ResourceLocation texture = skin != null
                ? skin.getTexture(TEXTURE, TEXTURE)
                : TEXTURE;
        return new RenderState(
                controller,
                visualContext,
                texture,
                partialTick);
    }

    private record RenderState(
            MandomStandEntity controller,
            StandVisualContext visualContext,
            ResourceLocation texture,
            float partialTick) {}
}
