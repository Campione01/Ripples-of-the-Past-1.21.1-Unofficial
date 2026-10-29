package rotp.addons.tickettoride.client;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.entity.DeadPhotoMementoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// 1.16 DeadphotomemtoRenderer: a plain mob renderer with a 0.5 shadow; the
// rock is an ordinary mob that every player sees.
public final class DeadPhotoMementoRenderer
        extends MobRenderer<
                DeadPhotoMementoEntity,
                DeadPhotoMementoModel> {
    private static final ResourceLocation TEXTURE =
            TicketToRideAddon.id(
                    "textures/entity/deadphotom.png");

    public DeadPhotoMementoRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new DeadPhotoMementoModel(
                        context.bakeLayer(
                                DeadPhotoMementoModel
                                        .LAYER_LOCATION)),
                0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(
            DeadPhotoMementoEntity entity) {
        return TEXTURE;
    }
}
