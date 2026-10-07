package rotp.core.mixin.block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import rotp.core.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.TargetBlock;

// The 1.16 Clackers were arrows, which a target block answers with the long signal.
@Mixin(TargetBlock.class)
public abstract class TargetBlockClackersMixin {
	@ModifyExpressionValue(
			method = "updateRedstoneOutput",
			at = @At(value = "CONSTANT", args = "intValue=8"))
	private static int jojo$clackersSignalLikeArrows(
			int ticks,
			@Local(argsOnly = true) Entity projectile) {
		return projectile instanceof ClackersEntity ? 20 : ticks;
	}
}
