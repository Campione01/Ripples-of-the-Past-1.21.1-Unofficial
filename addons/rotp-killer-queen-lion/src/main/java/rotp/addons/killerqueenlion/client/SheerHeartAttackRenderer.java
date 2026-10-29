package rotp.addons.killerqueenlion.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SheerHeartAttackRenderer
        extends MobRenderer<
                SheerHeartAttackEntity,
                SheerHeartAttackModel> {
    private static final ResourceLocation TEXTURE =
            KillerQueenLionAddon.id(
                    "textures/entity/sheer_heart.png");

    public SheerHeartAttackRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new SheerHeartAttackModel(
                        context.bakeLayer(
                                SheerHeartAttackModel.LAYER)),
                0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(
            SheerHeartAttackEntity entity) {
        StandSkin skin = StandSkinsLoader.getInstance()
                .getSkinFromId(
                        entity.getStandType(),
                        entity.getStandSkin());
        return skin != null ? skin.getTexture(TEXTURE) : TEXTURE;
    }

    @Override
    protected void scale(
            SheerHeartAttackEntity entity,
            PoseStack poseStack,
            float partialTick) {
        float progress = SheerHeartAttackVisuals.swellProgress(
                entity.visualSwellTicks(), entity.postExplosionIdleTicks());
        float eased = progress * progress
                * (3.0F - 2.0F * progress);
        poseStack.scale(
                1.0F + 0.6F * eased,
                1.0F + 0.25F * eased,
                1.0F + 0.6F * eased);
    }

    @Override
    public void render(
            SheerHeartAttackEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        if (ClientGlobals.canSeeStands) {
            super.render(
                    entity,
                    yRotation,
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
        }
    }
}
