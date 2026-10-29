package rotp.addons.harvest.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import rotp.addons.harvest.entity.HarvestStandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class HarvestMemberRenderer
        extends MobRenderer<
                HarvestMemberEntity, HarvestMemberModel> {
    private static final ResourceLocation TEXTURE =
            HarvestAddon.id("textures/entity/harvest.png");

    public HarvestMemberRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new HarvestMemberModel(
                        context.bakeLayer(
                                HarvestMemberModel.LAYER_LOCATION)),
                0.0F);
        addLayer(new ItemInHandLayer<>(
                this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(
            HarvestMemberEntity entity) {
        StandSkin skin = SimpleEntityRenderer.getStandSkin(entity);
        return skin != null
                ? skin.getTexture(TEXTURE)
                : TEXTURE;
    }

    @Override
    public void render(
            HarvestMemberEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float alpha = effectiveAlpha(entity, partialTick);
        if (!ClientGlobals.canSeeStands || alpha <= 0.0F) {
            return;
        }
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                AlphaMultiBufferSource.wrap(buffer, alpha),
                packedLight);
    }

    @Override
    protected void scale(
            HarvestMemberEntity entity,
            PoseStack poseStack,
            float partialTick) {
        boolean rabbit = entity.getStandSkin()
                .map(id -> "rabbit".equals(id.getPath()))
                .orElse(false);
        float scale = rabbit ? 0.2222F : 0.15F;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected RenderType getRenderType(
            HarvestMemberEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        if (effectiveAlpha(entity, 1.0F) < 1.0F) {
            return ModRenderTypes.standTranslucentCull(
                    getTextureLocation(entity));
        }
        return super.getRenderType(
                entity, bodyVisible, translucent, glowing);
    }

    @Override
    public boolean shouldRender(
            HarvestMemberEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return ClientGlobals.canSeeStands
                && effectiveAlpha(entity, 1.0F) > 0.0F
                && super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ);
    }

    private static float effectiveAlpha(
            HarvestMemberEntity entity, float partialTick) {
        HarvestStandEntity coordinator =
                entity.resolveCoordinator();
        if (coordinator == null
                || coordinator.clientStuff == null) {
            return 0.0F;
        }
        float lifecycleAlpha = coordinator.clientStuff.getAlpha(
                coordinator, partialTick);
        return StandVisualContext.world(
                coordinator, lifecycleAlpha).effectiveAlpha();
    }
}
