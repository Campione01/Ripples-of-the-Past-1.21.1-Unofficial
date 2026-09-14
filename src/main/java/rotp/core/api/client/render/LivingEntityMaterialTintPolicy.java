package rotp.core.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface LivingEntityMaterialTintPolicy {
	@Nullable
	LivingEntityMaterialTint materialTint(
			LivingEntityMaterialTintQuery query);
}
