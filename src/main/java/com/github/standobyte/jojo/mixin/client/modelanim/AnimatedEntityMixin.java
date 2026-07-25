package com.github.standobyte.jojo.mixin.client.modelanim;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.github.standobyte.jojo.client.entityanim.barrage.BarrageSwings;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityanim.pose.AnimatedEntity;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@Mixin(Entity.class)
public class AnimatedEntityMixin implements AnimatedEntity {
	@Unique private AnimFramePose finalModelPose;
	@Unique private AnimFramePose unmodifiedModelPose;
	@Unique private boolean hasFinalPose = false;
	@Unique private boolean hasUnmodifiedPose = false;
	@Unique private final BarrageSwings jojo_ripples$barrageSwings = new BarrageSwings();

	@Override
	public void jojo_ripples$setModelPose(PoseType poseType, AnimFramePose srcPose) {
		if (poseType == PoseType.UNMODIFIED) {
			this.hasUnmodifiedPose = srcPose != null;
			this.unmodifiedModelPose = jojo_ripples$copyOrClearPose(this.unmodifiedModelPose, srcPose);
			return;
		}

		this.hasFinalPose = srcPose != null;
		this.finalModelPose = jojo_ripples$copyOrClearPose(this.finalModelPose, srcPose);
	}

	@Unique
	private AnimFramePose jojo_ripples$copyOrClearPose(@Nullable AnimFramePose dstPose, @Nullable AnimFramePose srcPose) {
		if (srcPose != null) {
			if (dstPose == null) {
				dstPose = new AnimFramePose();
			}
			srcPose.copyTo(dstPose);
		}
		else if (dstPose != null) {
			dstPose.clear();
		}
		return dstPose;
	}

	@Nullable
	@Override
	public AnimFramePose jojo_ripples$getModelPose(PoseType poseType) {
		if (poseType == PoseType.UNMODIFIED) {
			return this.hasUnmodifiedPose ? this.unmodifiedModelPose : null;
		}
		return this.hasFinalPose ? this.finalModelPose : null;
	}

	@Override
	public BarrageSwings jojo_ripples$getBarrageSwings() {
		return this.jojo_ripples$barrageSwings;
	}
	
	
	@Override
	public boolean jojo_ripples$crouchDisabled() {
		Entity entity = (Entity) (Object) this;
		return this.hasFinalPose && entity instanceof LivingEntity living
				&& LivingComponentAction.getCurEntityAction(living) != null;
	}

}
