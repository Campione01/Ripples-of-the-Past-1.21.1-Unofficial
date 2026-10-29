package rotp.addons.sexpistols.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.client.RevolverBulletTrailTracker.TrailSnapshot;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class RevolverBulletRenderer
        extends EntityRenderer<RevolverBulletEntity> {
    private static final ResourceLocation TRAIL_TEXTURE =
            SexPistolsIds.id(
                    "textures/entity/projectiles/bullet_trail.png");

    public RevolverBulletRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            RevolverBulletEntity entity) {
        return resolveTexture(entity);
    }

    static ResourceLocation resolveTexture(
            RevolverBulletEntity entity) {
        StandSkin standSkin =
                SimpleEntityRenderer.getStandSkin(entity);
        return standSkin != null
                ? standSkin.getTexture(TRAIL_TEXTURE)
                : TRAIL_TEXTURE;
    }

    @Override
    public boolean shouldRender(
            RevolverBulletEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return true;
    }

    @Override
    public void render(
            RevolverBulletEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext = entity.standDamage()
                ? SexPistolsVisuals.sexPistols(
                        entity.getOwner(), partialTick)
                : null;
        if (visualContext != null
                && visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        TrailSnapshot snapshot =
                RevolverBulletTrailTracker.activeSnapshot(
                        entity, partialTick);
        if (snapshot == null) {
            return;
        }
        MultiBufferSource trailBuffer = visualContext != null
                ? AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha())
                : buffer;
        RenderType renderType = visualContext != null
                ? ModRenderTypes.standTranslucent(
                        snapshot.texture(), false)
                : RenderType.entityTranslucent(
                        snapshot.texture());
        VertexConsumer vertices = trailBuffer.getBuffer(renderType);
        Vec3 camera = Minecraft.getInstance()
                .gameRenderer.getMainCamera().getPosition();
        Vec3 origin = entity.getPosition(partialTick);
        RevolverBulletTrailTracker.renderRibbon(
                poseStack,
                vertices,
                snapshot,
                camera,
                origin,
                1.0F);
        SexPistolsPresentationDiagnostics.recordActiveTrail(
                snapshot.points().size(), snapshot.distance());
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                trailBuffer,
                packedLight);
    }
}
