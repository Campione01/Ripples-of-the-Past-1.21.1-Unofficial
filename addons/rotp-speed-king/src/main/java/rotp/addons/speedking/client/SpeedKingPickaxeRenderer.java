package rotp.addons.speedking.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.entity.SpeedKingPickaxeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The 1.16 {@code ExamplePickaxeRenderer}: the spinning 3D pickaxe model. The 1.16 Speed King jar
 * shipped no {@code example_pickaxe.png}; the sheet is the identical Jumping Jack Flash one.
 */
public final class SpeedKingPickaxeRenderer
        extends SimpleEntityRenderer<SpeedKingPickaxeEntity, SpeedKingPickaxeModel> {
    public SpeedKingPickaxeRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(SpeedKingAddon.id("textures/entity/example_pickaxe.png"), false);
        initModel(new SpeedKingPickaxeModel(context.bakeLayer(SpeedKingPickaxeModel.LAYER)));
    }
}
