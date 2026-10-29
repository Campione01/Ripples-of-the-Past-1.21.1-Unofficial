package rotp.addons.killerqueen.client;

import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.killerqueen.entity.KillerQueenBombProjectile;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

abstract class KillerQueenProjectileRenderer<
        T extends KillerQueenBombProjectile>
        extends SimpleEntityRenderer<
                T, KillerQueenProjectileModel<T>> {
    protected KillerQueenProjectileRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation layer,
            ResourceLocation texture,
            boolean textureFromSkin) {
        super(context);
        initTexture(texture, textureFromSkin);
        initModel(new KillerQueenProjectileModel<>(
                context.bakeLayer(layer)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }

    @Override
    protected boolean shouldRender(T entity) {
        return ClientGlobals.canSeeStands
                && bombAlpha(entity) > 0.0F
                && super.shouldRender(entity);
    }

    @Override
    protected void doRender(
            T entity,
            KillerQueenProjectileModel<T> model,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float alpha = bombAlpha(entity);
        if (!ClientGlobals.canSeeStands || alpha <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(buffer, alpha);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity)));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }

    // Donor 020 SnowBomb/BubbleBomb renderers draw a bomb for its whole
    // flight with no link to the launching Stand, and a bomb outlives a
    // recall: only the viewer's Stand opacity setting applies.
    private static float bombAlpha(KillerQueenBombProjectile entity) {
        UUID ownerUuid = entity.getControllerUuid();
        Player owner = ownerUuid != null
                ? entity.level().getPlayerByUUID(ownerUuid)
                : null;
        return StandOpacityPolicy.apply(1.0F, owner);
    }
}
