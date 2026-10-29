package rotp.addons.badcompany.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanySoldierEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanySoldierRenderer
        extends HumanoidMobRenderer<
                BadCompanySoldierEntity,
                BadCompanySoldierModel> {
    private static final ResourceLocation TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/stand/soldier.png");

    public BadCompanySoldierRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new BadCompanySoldierModel(
                        context.bakeLayer(ModelLayers.PLAYER)),
                0.0F);
        addLayer(new ItemInHandLayer<>(
                this, context.getItemInHandRenderer()));
        addLayer(new BadCompanyParachuteLayer(
                this,
                new BadCompanyParachuteModel(
                        context.bakeLayer(
                                BadCompanyParachuteModel.LAYER))));
    }

    @Override
    public ResourceLocation getTextureLocation(
            BadCompanySoldierEntity entity) {
        return BadCompanyVisuals.texture(entity, TEXTURE);
    }

    @Override
    protected void scale(
            BadCompanySoldierEntity entity,
            PoseStack poseStack,
            float partialTick) {
        float scale = BadCompanySoldierEntity.MODEL_SCALE;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(
            BadCompanySoldierEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                BadCompanyVisuals.context(entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha()),
                packedLight);
    }

    @Override
    protected RenderType getRenderType(
            BadCompanySoldierEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        return ModRenderTypes.standTranslucentCull(
                getTextureLocation(entity));
    }

    @Override
    public boolean shouldRender(
            BadCompanySoldierEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        StandVisualContext visualContext =
                BadCompanyVisuals.context(entity, 1.0F);
        return ClientGlobals.canSeeStands
                && visualContext != null
                && visualContext.effectiveAlpha() > 0.0F
                && super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ);
    }
}
