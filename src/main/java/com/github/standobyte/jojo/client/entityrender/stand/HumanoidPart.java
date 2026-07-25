package com.github.standobyte.jojo.client.entityrender.stand;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;

import com.github.standobyte.jojo.client.entityrender.ModelWithExtraFeatures;
import com.github.standobyte.v1_21_4_stuff.missingmethods.Model_1_21_2plus;

import net.minecraft.client.model.geom.ModelPart;

public enum HumanoidPart {
	HEAD,
	BODY,
	LEFT_ARM,
	RIGHT_ARM,
	LEGS;
	
	public static final HumanoidPart[] ALL = HumanoidPart.values();
	public static final HumanoidPart[] NONE = new HumanoidPart[0];
	public static final HumanoidPart[] ARMS_ONLY = new HumanoidPart[] { LEFT_ARM, RIGHT_ARM };
	public static final HumanoidPart[] NON_ARMS = new HumanoidPart[] { HEAD, BODY, LEGS };
	public static final HumanoidPart[] LEFT_ARM_ONLY = new HumanoidPart[] { LEFT_ARM };
	public static final HumanoidPart[] RIGHT_ARM_ONLY = new HumanoidPart[] { RIGHT_ARM };
	
	public static void setPartsVisible(StandEntityModel<?, ?> model, HumanoidPart... parts) {
		ModelPart root = ((Model_1_21_2plus) model).jojo_ripples$root();
		Collection<ModelPart> initiallyHidden = ((ModelWithExtraFeatures) model).jojo_ripples$getInitiallyHidden();
		Set<ModelPart> visibleInitiallyHiddenBeforeReset = visibleInitiallyHiddenBeforeReset(initiallyHidden);
		setAllVisible(root, false, initiallyHidden, visibleInitiallyHiddenBeforeReset);
		for (HumanoidPart part : parts) {
			setPartTreeVisible(root, model, part, initiallyHidden, visibleInitiallyHiddenBeforeReset);
		}
	}

	private static Set<ModelPart> visibleInitiallyHiddenBeforeReset(Collection<ModelPart> initiallyHidden) {
		if (initiallyHidden == null || initiallyHidden.isEmpty()) {
			return Collections.emptySet();
		}
		Set<ModelPart> visibleParts = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ModelPart part : initiallyHidden) {
			if (part.visible) {
				visibleParts.add(part);
			}
		}
		return visibleParts;
	}

	private static void setPartTreeVisible(ModelPart root, StandEntityModel<?, ?> model, HumanoidPart part,
			Collection<ModelPart> initiallyHidden, Set<ModelPart> visibleInitiallyHiddenBeforeReset) {
		switch (part) {
			case HEAD -> setPartTreeVisible(root, model.head, initiallyHidden, visibleInitiallyHiddenBeforeReset);
			case BODY -> {
				setPartTreeVisible(root, model.torso_no_arms, initiallyHidden, visibleInitiallyHiddenBeforeReset);
				setPartTreeVisible(root, model.torso_lower, initiallyHidden, visibleInitiallyHiddenBeforeReset);
			}
			case LEFT_ARM -> setPartTreeVisible(root, model.left_arm, initiallyHidden, visibleInitiallyHiddenBeforeReset);
			case RIGHT_ARM -> setPartTreeVisible(root, model.right_arm, initiallyHidden, visibleInitiallyHiddenBeforeReset);
			case LEGS -> {
				setPartTreeVisible(root, model.left_leg, initiallyHidden, visibleInitiallyHiddenBeforeReset);
				setPartTreeVisible(root, model.right_leg, initiallyHidden, visibleInitiallyHiddenBeforeReset);
			}
		}
	}

	private static void setPartTreeVisible(ModelPart root, ModelPart modelPart,
			Collection<ModelPart> initiallyHidden, Set<ModelPart> visibleInitiallyHiddenBeforeReset) {
		if (modelPart != null && setVisiblePathTo(root, modelPart, initiallyHidden, visibleInitiallyHiddenBeforeReset)) {
			setAllVisible(modelPart, true, initiallyHidden, visibleInitiallyHiddenBeforeReset);
		}
	}

	private static boolean setVisiblePathTo(ModelPart current, ModelPart target,
			Collection<ModelPart> initiallyHidden, Set<ModelPart> visibleInitiallyHiddenBeforeReset) {
		if (current == null) {
			return false;
		}
		if (current == target) {
			setModelPartVisible(current, true, initiallyHidden, visibleInitiallyHiddenBeforeReset);
			return true;
		}
		for (ModelPart child : current.children.values()) {
			if (setVisiblePathTo(child, target, initiallyHidden, visibleInitiallyHiddenBeforeReset)) {
				setModelPartVisible(current, true, initiallyHidden, visibleInitiallyHiddenBeforeReset);
				return true;
			}
		}
		return false;
	}

	private static void setAllVisible(ModelPart modelPart, boolean visible,
			Collection<ModelPart> initiallyHidden, Set<ModelPart> visibleInitiallyHiddenBeforeReset) {
		if (modelPart == null) {
			return;
		}
		setModelPartVisible(modelPart, visible, initiallyHidden, visibleInitiallyHiddenBeforeReset);
		for (ModelPart child : modelPart.children.values()) {
			setAllVisible(child, visible, initiallyHidden, visibleInitiallyHiddenBeforeReset);
		}
	}

	private static void setModelPartVisible(ModelPart modelPart, boolean visible,
			Collection<ModelPart> initiallyHidden, Set<ModelPart> visibleInitiallyHiddenBeforeReset) {
		modelPart.visible = visible && !shouldKeepHidden(modelPart, initiallyHidden, visibleInitiallyHiddenBeforeReset);
	}

	private static boolean shouldKeepHidden(ModelPart modelPart, Collection<ModelPart> initiallyHidden,
			Set<ModelPart> visibleInitiallyHiddenBeforeReset) {
		return initiallyHidden != null && initiallyHidden.contains(modelPart)
				&& !visibleInitiallyHiddenBeforeReset.contains(modelPart);
	}

	public static int partsMask(HumanoidPart... parts) {
		int mask = 0;
		for (HumanoidPart part : parts) {
			mask |= (1 << part.ordinal());
		}
		return mask;
	}

	public static boolean contains(HumanoidPart[] parts, HumanoidPart part) {
		return ArrayUtils.contains(parts, part);
	}
	
	private static HumanoidPart[] temp = new HumanoidPart[HumanoidPart.values().length];
	public static HumanoidPart[] reduce(HumanoidPart[] parts, HumanoidPart[] limit) {
		int i = 0;
		for (HumanoidPart part : parts) {
			if (ArrayUtils.contains(limit, part)) {
				temp[i++] = part;
			}
		}
		HumanoidPart[] arr = new HumanoidPart[i];
		for (int j = 0; j < i; j++) {
			arr[j] = temp[j];
		}
		return arr;
	}

	public static HumanoidPart[] complement(HumanoidPart[] parts) {
		int i = 0;
		for (HumanoidPart part : HumanoidPart.values()) {
			if (!ArrayUtils.contains(parts, part)) {
				temp[i++] = part;
			}
		}
		HumanoidPart[] arr = new HumanoidPart[i];
		for (int j = 0; j < i; j++) {
			arr[j] = temp[j];
		}
		return arr;
	}
	
}
