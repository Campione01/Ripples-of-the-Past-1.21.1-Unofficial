package rotp.addons.whitealbum.client;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public final class WhiteAlbumShieldLayer<
        T extends LivingEntity,
        M extends HumanoidModel<T>> extends RenderLayer<T, M>
        implements FirstPersonModelLayer {
    private final WhiteAlbumWearableModel<T> shieldModel;
    private final ResourceLocation texture;

    public WhiteAlbumShieldLayer(
            RenderLayerParent<T, M> parent,
            WhiteAlbumWearableModel<T> shieldModel,
            boolean slim) {
        super(parent);
        this.shieldModel = shieldModel;
        this.texture = slim
                ? WhiteAlbumVisuals.SHIELD_SLIM
                : WhiteAlbumVisuals.SHIELD_DEFAULT;
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
        WhiteAlbumVisuals.RenderState state =
                WhiteAlbumVisuals.resolve(
                        entity, partialTick);
        if (state == null
                || !state.controller().isStandBlocking()) {
            return;
        }
        WhiteAlbumVisuals.prepareThirdPerson(
                shieldModel,
                getParentModel(),
                entity,
                limbSwing,
                limbSwingAmount,
                partialTick,
                ageInTicks,
                netHeadYaw,
                headPitch);
        WhiteAlbumVisuals.renderModel(
                poseStack,
                buffer,
                packedLight,
                entity,
                state.controller(),
                shieldModel,
                WhiteAlbumVisuals.resolveTexture(
                        state.controller(), texture),
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
        WhiteAlbumVisuals.RenderState state =
                WhiteAlbumVisuals.resolve(
                        entity, partialTick);
        if (state == null
                || !state.controller().isStandBlocking()) {
            return;
        }
        WhiteAlbumVisuals.renderFirstPersonArm(
                side,
                poseStack,
                buffer,
                light,
                entity,
                shieldModel,
                WhiteAlbumVisuals.resolveTexture(
                        state.controller(), texture),
                state.context(),
                partialTick);
    }
}
