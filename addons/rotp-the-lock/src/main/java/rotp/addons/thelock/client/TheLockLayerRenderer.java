package rotp.addons.thelock.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.state.LockState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;

public final class TheLockLayerRenderer<
        T extends LivingEntity,
        M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE =
            TheLockAddon.id(
                    "textures/entity/stand/the_lock.png");

    private final TheLockLayerModel lockModel;

    public TheLockLayerRenderer(
            RenderLayerParent<T, M> parent,
            TheLockLayerModel lockModel) {
        super(parent);
        this.lockModel = lockModel;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        MobEffectInstance effect = entity.getEffect(
                TheLockAddon.LOCK_EFFECT);
        StandVisualContext visualContext =
                visualContext(entity);
        if (effect == null
                || !ClientGlobals.canSeeStands
                || entity instanceof StandEntity
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        float translateY;
        if (getParentModel()
                instanceof HumanoidModel<?> humanoid) {
            humanoid.body.translateAndRotate(poseStack);
            translateY = 0.0F;
        } else if (entity instanceof Ghast) {
            translateY = 1.4F;
        } else if (entity instanceof Blaze) {
            translateY = 0.1F;
        } else if (entity instanceof Phantom) {
            translateY = entity.getBbHeight() - 0.59F;
        } else if (entity instanceof Slime) {
            translateY = 0.0F;
        } else if (entity.getBbHeight() > 2.5F) {
            translateY = -0.35F;
        } else if (entity.getBbHeight() > 1.7F) {
            translateY = 0.1F;
        } else if (entity.getBbHeight() > 1.0F) {
            translateY = entity.getBbHeight() / 3.0F;
        } else if (entity.getBbHeight() > 0.5F) {
            translateY = entity.getBbHeight() / 0.95F;
        } else {
            translateY = entity.getBbHeight() + 0.5F;
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(0.0D, translateY, 0.18D);
        float scale =
                0.5F + 0.04F * effect.getAmplifier();
        poseStack.scale(scale, scale, scale);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(TEXTURE));
        lockModel.render(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        entity, 0.0F));
        poseStack.popPose();
    }

    private static StandVisualContext visualContext(
            LivingEntity target) {
        LockState state = TheLockAddon.lockState(target);
        if (!state.active()
                || state.ownerUuid() == null) {
            return null;
        }
        return StandVisualContext.preview();
    }
}
