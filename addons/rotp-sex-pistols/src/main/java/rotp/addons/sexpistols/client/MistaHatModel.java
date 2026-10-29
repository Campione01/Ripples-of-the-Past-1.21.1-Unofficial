package rotp.addons.sexpistols.client;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public final class MistaHatModel
        extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    SexPistolsIds.id("mista_suit_hat"),
                    "main");

    public MistaHatModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh =
                HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -4.0F, -8.3F, -4.0F,
                                8.0F, 8.0F, 8.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(32, 0)
                        .addBox(
                                -4.0F, -8.3F, -4.0F,
                                8.0F, 8.0F, 8.0F,
                                new CubeDeformation(0.52F)),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
