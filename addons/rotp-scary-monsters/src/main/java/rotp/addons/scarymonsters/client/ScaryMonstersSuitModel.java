package rotp.addons.scarymonsters.client;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * The 1.16 {@code ExampleStandModelForLayer}: a PlayerModel inflated by 0.2 that the Scary Monsters user
 * wears while the Stand is summoned, blocking, charging the dragon or in dragon form. The Stand entity
 * itself never rendered; this suit was the Stand's only visual.
 */
public final class ScaryMonstersSuitModel<T extends LivingEntity> extends PlayerModel<T> {
    public static final float INFLATION = 0.2F;
    public static final int TEXTURE_SIZE = 64;
    public static final ModelLayerLocation SUIT_DEFAULT =
            new ModelLayerLocation(ScaryMonstersAddon.id("suit_default"), "main");
    public static final ModelLayerLocation SUIT_SLIM =
            new ModelLayerLocation(ScaryMonstersAddon.id("suit_slim"), "main");

    public ScaryMonstersSuitModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    public static LayerDefinition createDefaultLayer() {
        return createLayer(false);
    }

    public static LayerDefinition createSlimLayer() {
        return createLayer(true);
    }

    private static LayerDefinition createLayer(boolean slim) {
        return LayerDefinition.create(
                PlayerModel.createMesh(new CubeDeformation(INFLATION), slim),
                TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
