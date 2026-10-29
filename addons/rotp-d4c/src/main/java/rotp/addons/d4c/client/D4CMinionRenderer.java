package rotp.addons.d4c.client;

import rotp.addons.d4c.entity.D4CMinionEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class D4CMinionRenderer
        extends HumanoidMobRenderer<
                D4CMinionEntity,
                PlayerModel<D4CMinionEntity>> {
    private final PlayerModel<D4CMinionEntity> wideModel;
    private final PlayerModel<D4CMinionEntity> slimModel;

    public D4CMinionRenderer(EntityRendererProvider.Context context) {
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
            D4CMinionEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
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

    @Override
    public ResourceLocation getTextureLocation(D4CMinionEntity entity) {
        return D4CPlayerSkinResolver.resolve(
                entity.profileId(),
                entity.profileName()).texture();
    }
}
