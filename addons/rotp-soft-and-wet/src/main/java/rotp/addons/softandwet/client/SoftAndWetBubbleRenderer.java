package rotp.addons.softandwet.client;

import javax.annotation.Nullable;

import rotp.core.api.client.render.EntityPostRenderExtensions;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.softandwet.SoftAndWetAddon;
import rotp.addons.softandwet.entity.SoftAndWetBubbleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;

public final class SoftAndWetBubbleRenderer
        extends SimpleEntityRenderer<
                SoftAndWetBubbleEntity, SoftAndWetBubbleModel> {
    public SoftAndWetBubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                SoftAndWetAddon.id(
                        "textures/entity/projectiles/sw_bubble.png"),
                false);
        initModel(new SoftAndWetBubbleModel(
                context.bakeLayer(SoftAndWetBubbleModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
    }

    @Override
    public boolean shouldRender(
            SoftAndWetBubbleEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        boolean accepted = super.shouldRender(
                entity, frustum, cameraX, cameraY, cameraZ);
        if (accepted) {
            SoftAndWetClientEvents.recordBubbleFrustum(
                    entity.getUUID(),
                    EntityPostRenderExtensions.currentFrameId());
        }
        return accepted;
    }

    @Override
    public void render(
            SoftAndWetBubbleEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                visualContext(entity, partialTick);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha()),
                packedLight);
        if (entity.getOwner() != null) {
            SoftAndWetClientEvents.recordBubbleDraw(
                    entity.getUUID(),
                    entity.getOwner().getUUID(),
                    entity.getKind(),
                    getTextureLocation(entity),
                    visualContext.effectiveAlpha(),
                    EntityPostRenderExtensions.currentFrameId());
        }
    }

    @Override
    protected void doRender(
            SoftAndWetBubbleEntity entity,
            SoftAndWetBubbleModel model,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        renderModel(
                entity,
                model,
                partialTick,
                poseStack,
                buffer.getBuffer(
                        ModRenderTypes.standTranslucentCull(
                                getTextureLocation(entity))),
                packedLight);
    }

    @Nullable
    private static StandVisualContext visualContext(
            SoftAndWetBubbleEntity bubble,
            float partialTick) {
        LivingEntity owner = bubble.getOwner();
        StandEntity controller;
        if (owner instanceof StandEntity stand) {
            controller = stand;
        } else {
            StandPower power = owner != null
                    ? StandPower.get(owner)
                    : null;
            controller = power != null
                    && power.getPowerType()
                            == SoftAndWetAddon.SOFT_AND_WET.get()
                    && power.getSummonedStandEntity()
                            instanceof StandEntity stand
                    ? stand
                    : null;
        }
        if (controller == null || !controller.isAlive()) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
