package rotp.addons.yellowtemperance.client;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.yellowtemperance.ability.YellowTemperanceBlockAbility;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public final class YellowTemperanceSuitLayer<
        T extends LivingEntity,
        M extends HumanoidModel<T>> extends RenderLayer<T, M>
        implements FirstPersonModelLayer {
    private final YellowTemperanceWearableModel<T> model;
    private final ResourceLocation texture;

    public YellowTemperanceSuitLayer(
            RenderLayerParent<T, M> parent,
            YellowTemperanceWearableModel<T> model,
            boolean slim) {
        super(parent);
        this.model = model;
        texture = slim
                ? YellowTemperanceVisuals.SLIM_TEXTURE
                : YellowTemperanceVisuals.DEFAULT_TEXTURE;
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
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        entity, partialTick);
        if (state == null || state.controller().isDisguised()) {
            return;
        }
        YellowTemperanceVisuals.prepareThirdPerson(
                model,
                getParentModel(),
                entity,
                limbSwing,
                limbSwingAmount,
                partialTick,
                ageInTicks,
                netHeadYaw,
                headPitch);
        YellowTemperanceVisuals.renderModel(
                poseStack,
                buffer,
                packedLight,
                entity,
                model,
                texture,
                state.context());
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        entity, partialTick);
        // 1.16 YellowTemperanceLayer.renderHandFirstPerson :112 left the
        // gloves off the first-person hands while the guard was held
        if (state == null
                || state.controller().isDisguised()
                || isGuarding(entity)) {
            return;
        }
        YellowTemperanceVisuals.renderFirstPersonArm(
                side,
                poseStack,
                buffer,
                light,
                entity,
                model,
                texture,
                state.context(),
                partialTick);
    }

    // the guard is held in its user's Stand-power slot, the Stand out or not
    private static boolean isGuarding(LivingEntity user) {
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        user, PowerClass.STAND);
        return action != null
                && action.ability instanceof YellowTemperanceBlockAbility
                && !action.isOver()
                && action.getPhase() != ActionPhase.RECOVERY;
    }
}
