package com.github.standobyte.jojoimpl.powers.hamon.client;

public class SnakeMufflerModel extends SatiporojaScarfModel {
    public SnakeMufflerModel(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    public static SnakeMufflerModel create() {
        return new SnakeMufflerModel(SatiporojaScarfModel.createBodyLayer().bakeRoot());
    }
}
