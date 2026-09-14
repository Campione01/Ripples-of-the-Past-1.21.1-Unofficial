package rotp.core.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface StandMaterialTintPolicy {
	@Nullable
	StandMaterialTint materialTint(StandMaterialTintQuery query);
}
