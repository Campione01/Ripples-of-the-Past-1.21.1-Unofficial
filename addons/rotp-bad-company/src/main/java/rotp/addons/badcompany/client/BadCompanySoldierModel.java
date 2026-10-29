package rotp.addons.badcompany.client;

import rotp.addons.badcompany.entity.BadCompanySoldierEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

public final class BadCompanySoldierModel
        extends PlayerModel<BadCompanySoldierEntity> {
    public BadCompanySoldierModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(
            BadCompanySoldierEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        super.setupAnim(
                entity,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        if (entity.isShooting()) {
            rightArm.xRot = -1.37F;
            rightArm.yRot = -0.51F;
            rightArm.zRot = -0.10F;
            leftArm.xRot = -1.53F;
            leftArm.yRot = 0.0F;
            leftArm.zRot = 0.0F;
        }
    }
}
