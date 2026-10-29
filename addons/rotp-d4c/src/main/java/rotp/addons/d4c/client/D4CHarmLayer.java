package rotp.addons.d4c.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class D4CHarmLayer extends RenderLayer<
        StandEntity,
        StandEntityModel<StandEntity, StandEntityRenderState>> {
    public static final float HEALTH_THRESHOLD = 0.3F;
    private static final ResourceLocation MODEL =
            D4CAddon.id("d4c_harm");
    private static final ResourceLocation TEXTURE =
            D4CAddon.id("textures/entity/d4c_harm.png");

    private final StandEntityRenderer<
            StandEntity,
            StandEntityRenderState,
            StandEntityModel<StandEntity, StandEntityRenderState>>
            standRenderer;

    public D4CHarmLayer(
            StandEntityRenderer<
                    StandEntity,
                    StandEntityRenderState,
                    StandEntityModel<
                            StandEntity,
                            StandEntityRenderState>> parent) {
        super(parent);
        this.standRenderer = parent;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            StandEntity stand,
            float walkAnimationPosition,
            float walkAnimationSpeed,
            float partialTick,
            float ageInTicks,
            float headYaw,
            float headPitch) {
        LivingEntity user = stand.getUser();
        if (user == null
                || user.getMaxHealth() <= 0.0F
                || user.getHealth() / user.getMaxHealth()
                        >= HEALTH_THRESHOLD
                || !(RenderStateCrutches.currentStandEntityRenderState
                        instanceof StandEntityRenderState renderState)
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator
                || renderState.visualContext.classicObstruction()) {
            return;
        }

        StandSkin skin = renderState.skin;
        StandEntityModel<StandEntity, StandEntityRenderState>
                harmModel = skin != null
                        ? skin.getModel(
                                MODEL,
                                standRenderer::createStandModel)
                        : null;
        if (harmModel == null) {
            return;
        }

        ResourceLocation texture = skin.getTexture(TEXTURE);
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucentCull(texture));
        harmModel.setupAnim(renderState);
        harmModel.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
