package com.github.standobyte.jojo.powersystem.standpower.entity;

import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.network.s2c.TrSyncStandOffsetPacket;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandOffsetFromUser {
	private StandEntity standEntity;
	
	public Vec3 idleOffset;
	public Rotations idleRotations;
	public Vec3 armsOnlyIdleOffset;
	public Rotations armsOnlyIdleRotations;
	@Nullable public Vec3 grabIdleOffset;
	
	private Vec3 relativeOffset;
	private Rotations rotations;
	private boolean canInvertSide = true;
	@Nullable public EntityActionType standAbility;
	
	private Vec3 prevAbsoluteOffset;
	private Rotations prevRotations;
	private float prevBodyRotDiff;
	private int changedTimestamp;
	
	public static StandOffsetFromUser createDefault(StandEntity standEntity) {
		StandOffsetFromUser offset = new StandOffsetFromUser(standEntity, new Vec3(0.75, standEntity.Y_OFFSET, -0.75), Rotations.BODY);
		offset.armsOnlyIdleOffset(new Vec3(0, 0, 0.15), Rotations.HEAD);
		offset.grabOffset(new Vec3(-1, standEntity.Y_OFFSET, 1.5));
		return offset;
	}
	
	public StandOffsetFromUser(StandEntity standEntity, Vec3 idleOffset, Rotations idleRotations) {
		this.standEntity = standEntity;
		this.idleOffset = idleOffset;
		this.idleRotations = idleRotations;
		this.armsOnlyIdleOffset = idleOffset;
		this.armsOnlyIdleRotations = idleRotations;
		setOffset(idleOffset, idleRotations);
	}
	
	public StandOffsetFromUser armsOnlyIdleOffset(Vec3 offset, Rotations rotations) {
		this.armsOnlyIdleOffset = offset;
		this.armsOnlyIdleRotations = rotations;
		return this;
	}

	public StandOffsetFromUser grabOffset(Vec3 offset) {
		this.grabIdleOffset = offset;
		return this;
	}
	
	public void setOffset(Vec3 offset, Rotations rotations) {
		setOffset(offset, rotations, true);
	}
	
	public void setOffset(Vec3 offset, Rotations rotations, boolean canInvertSide) {
		if (this.relativeOffset == null || this.rotations == null || 
				offset.x != this.relativeOffset.x || offset.y != this.relativeOffset.y || offset.z != this.relativeOffset.z || 
				rotations != this.rotations || canInvertSide != this.canInvertSide) {
			LivingEntity userEntity = standEntity.getUser();
			if (userEntity != null) {
				this.prevAbsoluteOffset = getAbsoluteOffset(userEntity, false);
				this.prevBodyRotDiff = userEntity.yBodyRot - userEntity.getYRot();
			}
			else {
				this.prevAbsoluteOffset = null;
			}
			this.prevRotations = this.rotations != null ? this.rotations : rotations;
			
			this.relativeOffset = offset;
			this.rotations = rotations;
			this.canInvertSide = canInvertSide;
			this.changedTimestamp = standEntity.tickCount;
		}
	}
	
	public void syncToTracking() {
		if (!standEntity.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(standEntity, new TrSyncStandOffsetPacket(standEntity.getId(), relativeOffset, rotations, canInvertSide));
		}
	}
	
	public void resetToIdle() {
		setOffset(getIdleOffset(), getIdleRotations());
		this.standAbility = null;
	}
	
	public boolean isIdle() {
		return rotations == getIdleRotations() && relativeOffset.equals(getIdleOffset());
	}
	
	private Vec3 getIdleOffset() {
		return standEntity.isArmsOnlyMode() ? armsOnlyIdleOffset : idleOffset;
	}
	
	private Rotations getIdleRotations() {
		return standEntity.isArmsOnlyMode() ? armsOnlyIdleRotations : idleRotations;
	}
	
	public Vec3 getPosition(LivingEntity userEntity) {
		Vec3 offset = getAbsoluteOffset(userEntity, standEntity.level().isClientSide());
		if (userEntity.isBaby()) {
			offset = offset.scale(userEntity.getAgeScale());
		}
		
		double maxRange = standEntity.getMaxRangeForMovement(userEntity);
		double offsetDist = offset.lengthSqr();
		if (offsetDist > maxRange * maxRange) {
			offsetDist = Math.sqrt(offsetDist);
			offset = offset.scale(maxRange / offsetDist);
		}
		
		return AlignBy.EYE_POS.align(userEntity, standEntity, offset);
	}
	
	public Vec3 getAbsoluteOffset(LivingEntity userEntity, boolean lerp) {
		if (grabIdleOffset != null && LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
			return relativeToAbsolute(grabIdleOffset, Rotations.HEAD, userEntity, standEntity.getUserBroadcastedSettings(), canInvertSide);
		}
		if (lerp && prevAbsoluteOffset == null) {
			prevAbsoluteOffset = getAbsoluteOffset(userEntity, false);
		}
		Vec3 absoluteOffset = relativeToAbsolute(relativeOffset, rotations, userEntity, standEntity.getUserBroadcastedSettings(), canInvertSide);
		
		if (lerp) {
			double lerpAmount = getLerpAmount();
			absoluteOffset = new Vec3(
					Mth.lerp(lerpAmount, prevAbsoluteOffset.x, absoluteOffset.x),
					Mth.lerp(lerpAmount, prevAbsoluteOffset.y, absoluteOffset.y),
					Mth.lerp(lerpAmount, prevAbsoluteOffset.z, absoluteOffset.z));
		}
		
		return absoluteOffset;
	}
	
	public static Vec3 relativeToAbsolute(Vec3 relativeVec, Rotations rotations, LivingEntity origin) {
		return relativeToAbsolute(relativeVec, rotations, origin, Optional.empty());
	}
	
	public static Vec3 relativeToAbsolute(Vec3 relativeVec, Rotations rotations, LivingEntity origin, 
			Optional<PlayerClientBroadcastedSettings> userSettings) {
		return relativeToAbsolute(relativeVec, rotations, origin, userSettings, true);
	}
	
	public static Vec3 relativeToAbsolute(Vec3 relativeVec, Rotations rotations, LivingEntity origin, 
			Optional<PlayerClientBroadcastedSettings> userSettings, boolean canInvertSide) {
		if (canInvertSide && userSettings.map(settings -> settings.standSide == HumanoidArm.LEFT).orElse(false)) {
			relativeVec = new Vec3(-relativeVec.x, relativeVec.y, relativeVec.z);
		}
		Vec3 absoluteOffset = relativeVec;
		if (rotations == Rotations.HEAD_XY) {
			absoluteOffset = relativeVec.xRot(-origin.getXRot() * MathUtil.DEG_TO_RAD);
		}
		float userYRot = rotations == Rotations.BODY ? origin.yBodyRot : origin.getYRot();
		absoluteOffset = absoluteOffset.yRot(-userYRot * MathUtil.DEG_TO_RAD);
		return absoluteOffset;
	}
	
	public void copyRotation(LivingEntity userEntity, boolean lerp) {
		standEntity.setYRot(userEntity.getYRot());
		standEntity.setXRot(userEntity.getXRot());
		standEntity.yRotO = userEntity.yRotO;
		standEntity.yHeadRot = userEntity.yHeadRot;
		standEntity.yHeadRotO = userEntity.yHeadRotO;
		
		// this shit so ass
		boolean isBodyRot = rotations == Rotations.BODY;
		float bodyRotAmount = isBodyRot ? 1 : 0;
		if (lerp) {
			boolean wasBodyRot = prevRotations == Rotations.BODY;
			if (isBodyRot != wasBodyRot) {
				float lerpAmount = getLerpAmount();
				bodyRotAmount = isBodyRot ? lerpAmount : (1 - lerpAmount);
			}
		}
		
		if (bodyRotAmount == 1) {
			standEntity.yBodyRot = userEntity.yBodyRot;
			standEntity.yBodyRotO = userEntity.yBodyRotO;
		}
		else if (bodyRotAmount == 0) {
			standEntity.yBodyRot = userEntity.getYRot();
			standEntity.yBodyRotO = userEntity.yRotO;
		}
		else {
			standEntity.yBodyRot = Mth.lerp(bodyRotAmount, userEntity.getYRot(), userEntity.yBodyRot);
			standEntity.yBodyRotO = standEntity.yBodyRot + prevBodyRotDiff / (isBodyRot ? -LERP_TIME : LERP_TIME);
		}
	}
	
//	public float getLerpAmount() {
//		return getLerpAmount(0);
//	}
	
	public float getLerpAmount(/*int tickOffset*/) {
		int timeDiff = (standEntity.tickCount/* + tickOffset*/) - changedTimestamp;
		return Mth.clamp((float) timeDiff / LERP_TIME, 0, 1);
	}
	
	public static final int LERP_TIME = 4;
	
	
	public enum Rotations {
		HEAD,
		BODY,
		HEAD_XY
	}
	
	public enum AlignBy {
		CENTER,
		BOTTOM,
		EYE_POS;
		
		public Vec3 align(Entity entity1, Entity entity2, Vec3 offset) {
			return switch (this) {
				case CENTER -> {
					Vec3 userCenter = entity1.getBoundingBox().getCenter();
					Vec3 standCenter = userCenter.add(offset);
					Vec3 pos = standCenter.subtract(0, entity2.getBoundingBox().getYsize() / 2, 0);
					yield pos;
				}
				case BOTTOM -> {
					Vec3 userPos = entity1.position();
					Vec3 standPos = userPos.add(offset);
					yield standPos;
				}
				case EYE_POS -> {
					Vec3 userEyePos = entity1.getEyePosition();
					Vec3 standEyePos = userEyePos.add(offset);
					Vec3 pos = standEyePos.subtract(0, entity2.getEyeHeight(), 0);
					yield pos;
				}
			};
		}
	}
	
	
	public Vec3 getRelativeOffset() {
		return relativeOffset;
	}

	public Rotations getRotations() {
		return rotations;
	}
}
