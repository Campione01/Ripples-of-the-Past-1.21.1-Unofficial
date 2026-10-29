package rotp.addons.d4c.client;

import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class D4CCloneRenderer
        extends HumanoidMobRenderer<
                D4CAnnihilationCloneEntity,
                PlayerModel<D4CAnnihilationCloneEntity>> {
    private final PlayerModel<D4CAnnihilationCloneEntity> wideModel;
    private final PlayerModel<D4CAnnihilationCloneEntity> slimModel;

    public D4CCloneRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new PlayerModel<>(
                        context.bakeLayer(ModelLayers.PLAYER),
                        false),
                0.5F);
        wideModel = model;
        slimModel = new PlayerModel<>(
                context.bakeLayer(ModelLayers.PLAYER_SLIM),
                true);
    }

    @Override
    public void render(
            D4CAnnihilationCloneEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        if (!entity.isPlayerClone()) {
            renderTargetAppearance(
                    entity,
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
            return;
        }
        PlayerSkin skin = D4CPlayerSkinResolver.resolve(
                entity.profileId(),
                entity.profileName());
        model = skin.model() == PlayerSkin.Model.SLIM
                ? slimModel
                : wideModel;
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    private void renderTargetAppearance(
            D4CAnnihilationCloneEntity clone,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        Entity target = clone.level().getEntity(
                clone.targetEntityId());
        if (target == null
                || target instanceof D4CAnnihilationCloneEntity) {
            return;
        }
        EntityRenderer<? super Entity> targetRenderer =
                entityRenderDispatcher.getRenderer(target);
        if (targetRenderer.getClass() == D4CCloneRenderer.class) {
            return;
        }
        entityRenderDispatcher.render(
                target,
                0.0D,
                0.0D,
                0.0D,
                target.getYRot(),
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(
            D4CAnnihilationCloneEntity entity) {
        return D4CPlayerSkinResolver.resolve(
                entity.profileId(),
                entity.profileName()).texture();
    }
}
