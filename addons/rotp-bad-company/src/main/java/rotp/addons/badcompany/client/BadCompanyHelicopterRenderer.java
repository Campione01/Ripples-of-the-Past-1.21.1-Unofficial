package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyHelicopterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanyHelicopterRenderer
        extends BadCompanyUnitRenderer<
                BadCompanyHelicopterEntity,
                BadCompanyHelicopterModel> {
    private static final ResourceLocation TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/stand/helicopter.png");

    public BadCompanyHelicopterRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new BadCompanyHelicopterModel(
                        context.bakeLayer(
                                BadCompanyHelicopterModel.LAYER)));
    }

    @Override
    protected void scale(
            BadCompanyHelicopterEntity entity,
            PoseStack poseStack,
            float partialTick) {
        float scale = BadCompanyHelicopterEntity.MODEL_SCALE;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(
            BadCompanyHelicopterEntity entity) {
        return BadCompanyVisuals.texture(entity, TEXTURE);
    }
}
