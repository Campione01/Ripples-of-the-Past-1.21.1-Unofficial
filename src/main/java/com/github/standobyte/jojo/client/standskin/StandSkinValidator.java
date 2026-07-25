package com.github.standobyte.jojo.client.standskin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Slice 5a framework extension — minimum stand-skin JSON validation hook.
 *
 * <p>Used by {@link StandSkinsLoader} (planned 5b consumer) to validate skin
 * descriptors loaded from datapack JSON before they are admitted into the
 * loader's per-stand cache. Currently performs the smallest viable checks
 * (id non-null + non-empty); deeper validation (texture-path existence,
 * sound-event id resolution, per-stand applicability) is consumed by 5b.</p>
 */
public final class StandSkinValidator {

	private StandSkinValidator() {
	}

	public static List<String> validate(ResourceLocation skinId, ResourceLocation textureId) {
		List<String> errors = new ArrayList<>();
		if (skinId == null) {
			errors.add("skinId must not be null");
		} else if (skinId.getPath().isEmpty()) {
			errors.add("skinId path must not be empty");
		}
		if (textureId == null) {
			errors.add("textureId must not be null");
		}
		return errors;
	}

	public static boolean isValid(ResourceLocation skinId, ResourceLocation textureId) {
		return validate(skinId, textureId).isEmpty();
	}
}
