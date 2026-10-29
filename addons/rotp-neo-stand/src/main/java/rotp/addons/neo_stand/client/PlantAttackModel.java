package rotp.addons.neo_stand.client;

import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.entity.PlantAttackEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class PlantAttackModel
        extends HierarchicalModel<PlantAttackEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    NeoStandAddon.id("plant_attack"),
                    "main");

    private final ModelPart root;

    public PlantAttackModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition vine = root.addOrReplaceChild(
                "vine",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -3.5F,
                                -14.6F,
                                -271.0F,
                                4.0F,
                                4.0F,
                                240.0F),
                PartPose.offset(1.5F, 26.6F, -3.0F));

        branch(
                vine,
                "branch_01",
                3.5F,
                -8.2F,
                -127.8F,
                -0.3996F,
                -0.1809F,
                1.9139F,
                132,
                543,
                -2.0F,
                -3.0F,
                -60.0F,
                3.0F,
                3.0F,
                63.0F);
        branch(
                vine,
                "branch_02",
                -3.4F,
                -2.6F,
                -6.0F,
                -0.0444F,
                0.0608F,
                1.8128F,
                406,
                375,
                -2.0F,
                -3.0F,
                -125.0F,
                3.0F,
                3.0F,
                128.0F);
        branch(
                vine,
                "branch_03",
                -14.6F,
                -20.6F,
                -133.8F,
                0.1856F,
                -0.5019F,
                0.1466F,
                192,
                447,
                -1.0F,
                -3.0F,
                -90.0F,
                3.0F,
                3.0F,
                93.0F);
        branch(
                vine,
                "branch_04",
                -27.6F,
                -19.6F,
                -100.8F,
                -0.0717F,
                -0.3713F,
                0.1359F,
                584,
                582,
                -1.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_05",
                21.8F,
                8.9F,
                -182.0F,
                0.4108F,
                0.3417F,
                1.6902F,
                488,
                0,
                -2.0F,
                -3.0F,
                -90.0F,
                3.0F,
                3.0F,
                93.0F);
        branch(
                vine,
                "branch_06",
                1.6F,
                -12.6F,
                -34.0F,
                -0.0761F,
                -0.4992F,
                -0.0734F,
                424,
                582,
                -2.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_07",
                -20.6F,
                -16.6F,
                -66.0F,
                -0.0687F,
                0.2381F,
                0.0936F,
                344,
                582,
                -1.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_08",
                -24.8F,
                8.9F,
                -182.0F,
                0.4108F,
                -0.3417F,
                -1.6902F,
                488,
                96,
                -1.0F,
                -3.0F,
                -90.0F,
                3.0F,
                3.0F,
                93.0F);
        branch(
                vine,
                "branch_09",
                -6.5F,
                -8.2F,
                -127.8F,
                -0.3996F,
                0.1809F,
                -1.9139F,
                0,
                543,
                -1.0F,
                -3.0F,
                -60.0F,
                3.0F,
                3.0F,
                63.0F);
        branch(
                vine,
                "branch_10",
                0.4F,
                -2.6F,
                -6.0F,
                -0.0444F,
                -0.0608F,
                -1.8128F,
                406,
                244,
                -1.0F,
                -3.0F,
                -125.0F,
                3.0F,
                3.0F,
                128.0F);
        branch(
                vine,
                "branch_11",
                -0.1F,
                -23.4F,
                -66.6F,
                0.0551F,
                0.0205F,
                0.1191F,
                0,
                244,
                -1.0F,
                -3.0F,
                -197.0F,
                3.0F,
                3.0F,
                200.0F);
        branch(
                vine,
                "branch_12",
                -3.2F,
                -12.6F,
                -34.0F,
                -0.3287F,
                -0.0666F,
                0.1143F,
                504,
                582,
                -1.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_13",
                -4.6F,
                -12.6F,
                -34.0F,
                -0.0761F,
                0.4992F,
                0.0734F,
                572,
                192,
                -1.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_14",
                24.0F,
                0.4F,
                -212.4F,
                -0.1147F,
                0.444F,
                0.1495F,
                384,
                506,
                -1.0F,
                -3.0F,
                -70.0F,
                3.0F,
                3.0F,
                73.0F);
        branch(
                vine,
                "branch_15",
                -27.0F,
                0.4F,
                -212.4F,
                -0.1147F,
                -0.444F,
                -0.1495F,
                536,
                506,
                -2.0F,
                -3.0F,
                -70.0F,
                3.0F,
                3.0F,
                73.0F);
        branch(
                vine,
                "branch_16",
                11.6F,
                -20.6F,
                -133.8F,
                0.1856F,
                0.5019F,
                -0.1466F,
                0,
                447,
                -2.0F,
                -3.0F,
                -90.0F,
                3.0F,
                3.0F,
                93.0F);
        branch(
                vine,
                "branch_17",
                24.6F,
                -19.6F,
                -100.8F,
                -0.0717F,
                0.3713F,
                -0.1359F,
                264,
                583,
                -2.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_18",
                17.6F,
                -16.6F,
                -66.0F,
                -0.0687F,
                -0.2381F,
                -0.0936F,
                264,
                543,
                -2.0F,
                -3.0F,
                -34.0F,
                3.0F,
                3.0F,
                37.0F);
        branch(
                vine,
                "branch_19",
                0.0F,
                0.0F,
                0.0F,
                -0.3054F,
                0.0F,
                0.0F,
                488,
                192,
                -4.0F,
                -5.0F,
                -34.0F,
                5.0F,
                5.0F,
                37.0F);
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    private static void branch(
            PartDefinition parent,
            String name,
            float offsetX,
            float offsetY,
            float offsetZ,
            float xRotation,
            float yRotation,
            float zRotation,
            int textureX,
            int textureY,
            float cubeX,
            float cubeY,
            float cubeZ,
            float width,
            float height,
            float depth) {
        parent.addOrReplaceChild(
                name,
                CubeListBuilder.create()
                        .texOffs(textureX, textureY)
                        .addBox(
                                cubeX,
                                cubeY,
                                cubeZ,
                                width,
                                height,
                                depth),
                PartPose.offsetAndRotation(
                        offsetX,
                        offsetY,
                        offsetZ,
                        xRotation,
                        yRotation,
                        zRotation));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            PlantAttackEntity entity,
            float walkAnimationPosition,
            float walkAnimationSpeed,
            float ageInTicks,
            float yRotationOffset,
            float xRotation) {}
}
