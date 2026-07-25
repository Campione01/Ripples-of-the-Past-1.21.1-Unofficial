package com.github.standobyte.jojoimpl.powers.hamon.client;

import javax.annotation.Nullable;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public class SatiporojaScarfModel extends HamonRepeatingModel {
    private final ModelPart scarfExtending;

    public SatiporojaScarfModel(ModelPart root) {
        this.scarfExtending = root.getChild("scarf_extending");
    }

    public static SatiporojaScarfModel create() {
        return new SatiporojaScarfModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("scarf_extending", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 12.0F, new CubeDeformation(-0.3F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    @Nullable
    protected ModelPart getMainPart() {
        return null;
    }

    @Override
    protected float getMainPartLength() {
        return 0.0F;
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
