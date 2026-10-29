package rotp.addons.speedwagon.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

public final class SpeedwagonNpcRenderer<T extends Mob>
        extends MobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation texture;

    public SpeedwagonNpcRenderer(
            EntityRendererProvider.Context context,
            ResourceLocation texture) {
        super(
                context,
                new PlayerModel<>(
                        context.bakeLayer(ModelLayers.PLAYER),
                        false),
                0.5F);
        this.texture = texture;
        addLayer(new ItemInHandLayer<>(
                this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
