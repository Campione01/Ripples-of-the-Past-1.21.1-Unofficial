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

public final class WhiteAlbumSuitLayer<
        T extends LivingEntity,
        M extends HumanoidModel<T>> extends RenderLayer<T, M>
        implements FirstPersonModelLayer {
    private final WhiteAlbumWearableModel<T> suitModel;
    private final ResourceLocation texture;

    public WhiteAlbumSuitLayer(
            RenderLayerParent<T, M> parent,
            WhiteAlbumWearableModel<T> suitModel,
            boolean slim) {
        super(parent);
        this.suitModel = suitModel;
        this.texture = slim
                ? WhiteAlbumVisuals.SUIT_SLIM
                : WhiteAlbumVisuals.SUIT_DEFAULT;
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
        if (state == null) {
            return;
        }
        WhiteAlbumVisuals.prepareThirdPerson(
                suitModel,
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
                suitModel,
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
                || state.controller().isStandBlocking()) {
            return;
        }
        WhiteAlbumVisuals.renderFirstPersonArm(
                side,
                poseStack,
                buffer,
                light,
                entity,
                suitModel,
                WhiteAlbumVisuals.resolveTexture(
                        state.controller(), texture),
                state.context(),
                partialTick);
    }
}
