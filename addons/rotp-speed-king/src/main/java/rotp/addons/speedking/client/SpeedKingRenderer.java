package rotp.addons.speedking.client;

import rotp.core.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.standskin.StandSkin;
import rotp.core.core.JojoMod;
import rotp.core.util.objects_java.LazyNullable;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.entity.SpeedKingEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class SpeedKingRenderer extends StandEntityRenderer<
        SpeedKingEntity, StandEntityRenderState, SpeedKingModel> {
    private static final ResourceLocation TEXTURE = SpeedKingAddon.id(
            "textures/entity/stand/speedking_stand.png");

    public SpeedKingRenderer(EntityRendererProvider.Context context) {
        super(context);
        missingSkinModel = LazyNullable.of(() -> {
            RotpGeckoModelLoader loader =
                    RotpGeckoModelLoader.getInstance();
            LayerDefinition definition = loader.getModelDefinition(
                    SpeedKingAddon.id("speedking_stand"));
            if (definition == null) {
                definition = loader.getModelDefinition(
                        JojoMod.resLoc("stand_default"));
            }
            return definition != null ? createStandModel(definition) : null;
        });
    }

    @Override
    public SpeedKingModel createStandModel(LayerDefinition definition) {
        return new SpeedKingModel(definition.bakeRoot());
    }

    @Override
    public ResourceLocation getTextureLocation(
            StandEntityRenderState state) {
        StandSkin skin = state.skin;
        return skin != null ? skin.getTexture(TEXTURE, TEXTURE) : TEXTURE;
    }
}
