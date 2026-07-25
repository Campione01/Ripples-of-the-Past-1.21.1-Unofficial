package com.github.standobyte.jojoimpl.powers.hamon.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public class SatiporojaScarfBindingModel extends HamonRepeatingModel {
    private final ModelPart scarf;
    private final ModelPart scarfExtending;

    public SatiporojaScarfBindingModel(ModelPart root) {
        this.scarf = root.getChild("scarf");
        this.scarfExtending = root.getChild("scarf_extending");
    }

    public static SatiporojaScarfBindingModel create() {
        return new SatiporojaScarfBindingModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("scarf", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -2.5F, -0.5F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.2F)),
                PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("scarf_extending", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 3.0F, 12.0F, new CubeDeformation(-0.3F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    protected ModelPart getMainPart() {
        return scarf;
    }

    @Override
    protected float getMainPartLength() {
        return 8.4F;
    }

    @Override
    protected ModelPart getRepeatingPart() {
        return scarfExtending;
    }

    @Override
    protected float getRepeatingPartLength() {
        return 11.4F;
    }
}
