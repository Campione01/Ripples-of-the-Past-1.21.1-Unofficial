package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyTankEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanyTankRenderer
        extends BadCompanyUnitRenderer<
                BadCompanyTankEntity,
                BadCompanyTankModel> {
    private static final ResourceLocation TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/stand/tank.png");

    public BadCompanyTankRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new BadCompanyTankModel(
                        context.bakeLayer(
                                BadCompanyTankModel.LAYER)));
    }

    @Override
    protected void scale(
            BadCompanyTankEntity entity,
            PoseStack poseStack,
            float partialTick) {
        float scale = BadCompanyTankEntity.MODEL_SCALE;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(
            BadCompanyTankEntity entity) {
        return BadCompanyVisuals.texture(entity, TEXTURE);
    }
}
