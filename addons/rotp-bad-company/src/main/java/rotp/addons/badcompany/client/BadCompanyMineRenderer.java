package rotp.addons.badcompany.client;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyMineEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanyMineRenderer
        extends BadCompanyProjectileRenderer<BadCompanyMineEntity> {
    private static final ResourceLocation TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/damaging/bad_mine.png");

    public BadCompanyMineRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new BadCompanyMineModel(
                        context.bakeLayer(
                                BadCompanyMineModel.LAYER)));
    }

    @Override
    public ResourceLocation getTextureLocation(
            BadCompanyMineEntity entity) {
        return BadCompanyVisuals.texture(entity, TEXTURE);
    }
}
