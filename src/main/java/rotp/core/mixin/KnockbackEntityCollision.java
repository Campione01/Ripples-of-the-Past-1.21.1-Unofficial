package rotp.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.mechanics.KnockbackCollisionImpact;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class KnockbackEntityCollision {

	@Shadow public Level level;
	@Unique private boolean jojo_ripples$repeatCollide;

	/**
	 * 1.16 EntityMixin: runs after vanilla collision, so the impact checks blocks only when vanilla collision changed
	 * the move, and collides again once blocks may have been broken. RETURN also covers the step-up return 1.21 added.
	 */
	@Inject(method = "collide", at = @At("RETURN"), cancellable = true)
	private void jojo_ripples$collideBreakBlocks(Vec3 movementVec, CallbackInfoReturnable<Vec3> ci) {
		if (jojo_ripples$repeatCollide) {
			return;
		}
		// looked up every time: loading the entity's NBT replaces the attachment
		KnockbackCollisionImpact kbCollision = KnockbackCollisionImpact.getExistingHandler((Entity) (Object) this);
		if (kbCollision != null && kbCollision.collideBreakBlocks(movementVec, ci.getReturnValue(), level)) {
			jojo_ripples$repeatCollide = true;
			try {
				ci.setReturnValue(collide(movementVec));
			}
			finally {
				jojo_ripples$repeatCollide = false;
			}
		}
	}

    @Shadow protected abstract Vec3 collide(Vec3 pVec);
}
