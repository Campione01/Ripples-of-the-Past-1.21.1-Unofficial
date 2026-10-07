package rotp.core.mixin.block;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import rotp.core.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

// The 1.16 Clackers were arrows, which press a wooden button for as long as they stay inside it.
@Mixin(ButtonBlock.class)
public abstract class ButtonBlockClackersMixin {
	@Shadow
	@Final
	private BlockSetType type;

	@ModifyVariable(
			method = "checkPressed",
			at = @At("STORE"),
			ordinal = 0)
	private boolean jojo$countClackersAsArrows(
			boolean arrowInside,
			BlockState state,
			Level level,
			BlockPos position) {
		return arrowInside || type.canButtonBeActivatedByArrows() && !level.getEntitiesOfClass(
				ClackersEntity.class,
				state.getShape(level, position).bounds().move(position)).isEmpty();
	}
}
