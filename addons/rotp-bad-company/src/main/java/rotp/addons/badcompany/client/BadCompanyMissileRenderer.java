package rotp.addons.badcompany.client;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyMissileEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanyMissileRenderer
        extends BadCompanyProjectileRenderer<
                BadCompanyMissileEntity> {
    private static final ResourceLocation TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/damaging/misile.png");

    public BadCompanyMissileRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new BadCompanyMissileModel(
                        context.bakeLayer(
                                BadCompanyMissileModel.LAYER)));
    }

    @Override
    public ResourceLocation getTextureLocation(
            BadCompanyMissileEntity entity) {
        return BadCompanyVisuals.texture(entity, TEXTURE);
    }
}
