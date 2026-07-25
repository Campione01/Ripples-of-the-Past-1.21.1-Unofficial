package com.github.standobyte.v1_21_4_stuff.missingmethods;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.model.geom.ModelPart;

public interface Model_1_21_2plus {
	void jojo_ripples$initRoot(ModelPart root);
	ModelPart jojo_ripples$root();
	List<ModelPart> jojo_ripples$allParts();
	Optional<ModelPart> jojo_ripples$getAnyDescendantWithName(String name);
}
