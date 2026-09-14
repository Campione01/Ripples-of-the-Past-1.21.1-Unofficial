package rotp.core.powersystem.entityaction;

import net.minecraft.world.entity.LivingEntity;

public interface HeldInput {
	void onKeyRelease(LivingEntity user);
}
