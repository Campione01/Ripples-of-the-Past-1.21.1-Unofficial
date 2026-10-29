package rotp.addons.diverdown.client;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 1.16 DiverDownNewArmModel: Diver Down's right arm on the Stand's texture, hidden while the trap waits; a firing
 * trap turns it to its victim and thrusts it out along its length. The 1.16 model also carried the core humanoid's
 * plain arm boxes, which sit on a transparent part of the texture and are left out.
 */
public final class DiverDownTrapModel
        extends HierarchicalModel<KineticTrapEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    DiverDownAddon.id("kinetic_trap"),
                    "main");
    // 1.16 added 202.5 to the radians of the trap's pitch
    private static final float ARM_PITCH_OFFSET = 202.5F;
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

    private final ModelPart root;
    private final ModelPart arm;
    private final ModelPart armMove;

    public DiverDownTrapModel(ModelPart root) {
        this.root = root;
        this.arm = root.getChild("arm");
        this.armMove = arm.getChild("arm_move");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition arm = root.addOrReplaceChild(
                "arm", CubeListBuilder.create(), PartPose.ZERO);
        arm.addOrReplaceChild(
                "arm_move",
                CubeListBuilder.create()
                        .texOffs(56, 54)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.01F))
                        .texOffs(0, 58)
                        .addBox(-2.025F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.251F))
                        .texOffs(16, 62)
                        .addBox(-2.0F, 4.0F, -2.0F, 4.0F, 6.0F, 4.0F,
                                CubeDeformation.NONE)
                        .texOffs(64, 20)
                        .addBox(-2.0F, 4.0F, -2.0F, 4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.25F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            KineticTrapEntity entity,
            float walkAnimationPosition,
            float walkAnimationSpeed,
            float ageInTicks,
            float yRotationOffset,
            float xRotation) {
        arm.visible = entity.isTriggered();
        if (!arm.visible) {
            return;
        }
        arm.xRot = entity.getXRot() * DEG_TO_RAD + ARM_PITCH_OFFSET;
        arm.yRot = -entity.getYRot() * DEG_TO_RAD;
        armMove.y = KineticTrapEntity.armThrust(entity.armTicks());
    }
}
