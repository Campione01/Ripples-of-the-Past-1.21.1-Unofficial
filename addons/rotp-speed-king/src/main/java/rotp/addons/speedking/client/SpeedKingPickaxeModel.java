package rotp.addons.speedking.client;

import rotp.core.util.functions.MathUtil;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.entity.SpeedKingPickaxeEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The 1.16 {@code ExamplePickaxeModel}: a 15-box pickaxe on a 32x32 sheet, spun on X by twice the age
 * in ticks and turned by the entity's yaw.
 */
public final class SpeedKingPickaxeModel extends HierarchicalModel<SpeedKingPickaxeEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(SpeedKingAddon.id("pickaxe"), "main");

    private final ModelPart root;
    private final ModelPart pickaxe;

    public SpeedKingPickaxeModel(ModelPart root) {
        this.root = root;
        this.pickaxe = root.getChild("pickaxe");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pickaxe", CubeListBuilder.create()
                .texOffs(10, 10).addBox(-0.5F, 5.5F, 4.5F, 1.0F, 1.0F, 2.0F)
                .texOffs(20, 17).addBox(-0.5F, 4.5F, 3.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(8, 19).addBox(-0.5F, 3.5F, 2.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(0, 19).addBox(-0.5F, 2.5F, 1.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(18, 11).addBox(-0.5F, 1.5F, 0.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(18, 7).addBox(-0.5F, 0.5F, -0.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(18, 0).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(0, 14).addBox(-0.5F, -1.5F, -2.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(10, 10).addBox(-0.5F, -2.5F, -6.5F, 1.0F, 1.0F, 6.0F)
                .texOffs(0, 0).addBox(-0.5F, -1.5F, -6.5F, 1.0F, 4.0F, 3.0F)
                .texOffs(5, 0).addBox(-0.5F, 2.5F, -5.5F, 1.0F, 1.0F, 1.0F)
                .texOffs(14, 17).addBox(-0.5F, -3.5F, -5.5F, 1.0F, 1.0F, 4.0F)
                .texOffs(0, 10).addBox(-0.5F, -4.5F, -5.5F, 1.0F, 1.0F, 8.0F)
                .texOffs(0, 0).addBox(-0.5F, -5.5F, -5.5F, 1.0F, 1.0F, 9.0F)
                .texOffs(11, 0).addBox(-0.5F, -6.5F, -2.5F, 1.0F, 1.0F, 5.0F),
                PartPose.offset(0.0F, -8.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            SpeedKingPickaxeEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        pickaxe.yRot = netHeadYaw * MathUtil.DEG_TO_RAD;
        pickaxe.xRot = ageInTicks * 2.0F;
    }
}
