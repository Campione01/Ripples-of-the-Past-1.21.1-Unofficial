package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class _PartDefinition {

	public static PartDefinition addOrReplaceChild(PartDefinition parent, String name, PartDefinition child) {
        PartDefinition partdefinition = parent.children.put(name, child);
        if (partdefinition != null) {
            child.children.putAll(partdefinition.children);
        }

        return child;
    }

    public static PartDefinition addOrReplaceChild(PartDefinition parent, String name, CubeListBuilder cubes, PartPose partPose) {
        PartDefinition partdefinition = new PartDefinition(cubes.getCubes(), partPose);
        return addOrReplaceChild(parent, name, partdefinition);
    }

    public static PartDefinition clearChild(PartDefinition parent, String name) {
        return addOrReplaceChild(parent, name, CubeListBuilder.create(), PartPose.ZERO);
    }
}
