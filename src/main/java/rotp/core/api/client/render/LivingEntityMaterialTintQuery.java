package rotp.core.api.client.render;

import net.minecraft.world.entity.LivingEntity;

public record LivingEntityMaterialTintQuery(
		LivingEntity entity,
		float partialTick) {}
