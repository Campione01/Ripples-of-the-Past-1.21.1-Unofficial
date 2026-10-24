package rotp.core.powersystem.standpower.entity;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.network.s2c.TrSyncStandOffsetPacket;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
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
	@Nullable private Vec3 offsetBeforeAction;
	private Rotations rotations;
	private boolean canInvertSide = true;
	@Nullable public EntityActionType standAbility;
	
	private Vec3 prevAbsoluteOffset;
	private int changedTimestamp;
	private boolean lerps = true;

	public static StandOffsetFromUser createDefault(StandEntity standEntity) {
		// 1.16 StandEntity.offsetDefault: behind the user's right, turned by the view yaw (StandEntity.taskOffset)
		StandOffsetFromUser offset = new StandOffsetFromUser(standEntity, new Vec3(-0.75, standEntity.Y_OFFSET, -0.75), Rotations.HEAD);
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

	/**
	 * 1.16 StandEntity.doOffsetLerp false, which an add-on Stand set in its constructor (the Scary Monsters donor's): its
	 * clients put the Stand at a new offset at once instead of gliding there over {@link #LERP_TIME} ticks. Call it on the
	 * offset the Stand's constructor builds, so both sides have it. RotpAddonApi#FEATURE_STAND_OFFSET_NO_LERP_V1.
	 */
	public StandOffsetFromUser noLerp() {
		this.lerps = false;
		return this;
	}

	/** Whether a client glides the Stand between offsets (1.16 doOffsetLerp); see {@link #noLerp}. */
	public boolean lerps() {
		return lerps;
	}

	public void setOffset(Vec3 offset, Rotations rotations) {
		setOffset(offset, rotations, true);
	}
	
	public void setOffset(Vec3 offset, Rotations rotations, boolean canInvertSide) {
		if (this.relativeOffset == null || this.rotations == null || 
				offset.x != this.relativeOffset.x || offset.y != this.relativeOffset.y || offset.z != this.relativeOffset.z || 
				rotations != this.rotations || canInvertSide != this.canInvertSide) {
			// a second change in one tick (a new action resets the offset, then sets its own) lerps from the first's start
			if (changedTimestamp != standEntity.tickCount || prevAbsoluteOffset == null) {
				LivingEntity userEntity = standEntity.getUser();
				if (userEntity != null) {
					this.prevAbsoluteOffset = getAbsoluteOffset(userEntity, false);
				}
				else {
					this.prevAbsoluteOffset = null;
				}
			}
			
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
	
	/*
	 * 1.16 StandEntity.getOffsetFromUser: a task whose action gives no offset shows the default one. Called as an action
	 * starts, before it sets an offset of its own.
	 */
	void resetForNewAction() {
		offsetBeforeAction = relativeOffset;
		resetToIdle();
	}
	
	// the offset the action before the current one left, which the port's grab throw and charged heavy punch build on
	public Vec3 getOffsetBeforeAction() {
		return offsetBeforeAction != null ? offsetBeforeAction : relativeOffset;
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
		// 1.16 StandRelativeOffset.getAbsoluteVec: arms-only Stands sink with a crouching, swimming or lying user
		if (standEntity.isArmsOnlyMode() && userEntity.getPose() != Pose.STANDING) {
			offset = offset.add(0, (userEntity.getDimensions(userEntity.getPose()).height()
					- userEntity.getDimensions(Pose.STANDING).height()) * 0.85F, 0);
		}
		EntityDimensions defaultSize = userEntity.getType().getDimensions();
		offset = scaleOffsetForUser(offset, userEntity.getBbWidth(), userEntity.getBbHeight(),
				defaultSize.width(), defaultSize.height());

		// 1.16 StandEntity.updatePosition measured every offset from the user's feet and did not shorten one longer than
		// the max range: only StandEntity.move held the Stand within it
		return AlignBy.BOTTOM.align(userEntity, standEntity, offset);
	}

	/*
	 * 1.16 StandRelativeOffset.getAbsoluteVec: an offset follows its user's bounding box, which a pose, a baby's age
	 * and a size mod all change, across by its width and up by its height
	 */
	static Vec3 scaleOffsetForUser(Vec3 offset, float width, float height, float defaultWidth, float defaultHeight) {
		if (width == defaultWidth && height == defaultHeight) {
			return offset;
		}
		double widthRatio = sizeRatio(width, defaultWidth);
		double heightRatio = sizeRatio(height, defaultHeight);
		return new Vec3(offset.x * widthRatio, offset.y * heightRatio, offset.z * widthRatio);
	}
	
	// a broken size keeps the offset rather than sending the Stand to NaN or infinity
	private static double sizeRatio(float size, float defaultSize) {
		float ratio = size / defaultSize;
		return Float.isFinite(ratio) && ratio >= 0 ? ratio : 1;
	}
	
	public Vec3 getAbsoluteOffset(LivingEntity userEntity, boolean lerp) {
		if (grabIdleOffset != null && LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
			return relativeToAbsolute(grabIdleOffset, Rotations.HEAD, userEntity, standEntity.getUserBroadcastedSettings(), canInvertSide);
		}
		if (lerp && prevAbsoluteOffset == null) {
			prevAbsoluteOffset = getAbsoluteOffset(userEntity, false);
		}
		Vec3 absoluteOffset = turnForAction(relativeOffset, rotations, userEntity);
		
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
		return relativeToAbsolute(relativeVec, rotations, rotations == Rotations.BODY ? origin.yBodyRot : origin.getYRot(),
				origin.getXRot(), userSettings, canInvertSide);
	}

	public static Vec3 relativeToAbsolute(Vec3 relativeVec, Rotations rotations, float yRot, float xRot,
			Optional<PlayerClientBroadcastedSettings> userSettings, boolean canInvertSide) {
		if (canInvertSide && userSettings.map(settings -> settings.standSide == HumanoidArm.LEFT).orElse(false)) {
			relativeVec = new Vec3(-relativeVec.x, relativeVec.y, relativeVec.z);
		}
		Vec3 absoluteOffset = relativeVec;
		if (rotations == Rotations.HEAD_XY) {
			absoluteOffset = relativeVec.xRot(-xRot * MathUtil.DEG_TO_RAD);
		}
		absoluteOffset = absoluteOffset.yRot(-yRot * MathUtil.DEG_TO_RAD);
		return absoluteOffset;
	}

	/*
	 * 1.16 StandEntity.taskOffset: while the current action locks on its task's target, any of its offsets, the default
	 * one included, turns by the yaw and pitch from the user's eyes to that target; otherwise by the action's
	 * yRotForOffset (a BODY offset by the body yaw) and the user's pitch. A client turns by the target the server sent.
	 */
	private Vec3 turnForAction(Vec3 relativeVec, Rotations rotations, LivingEntity userEntity) {
		float yRot = rotations == Rotations.BODY ? userEntity.yBodyRot : userEntity.getYRot();
		float xRot = userEntity.getXRot();
		EntityActionInstance action = standEntity.getCurStandAction();
		if (action != null && action.ability instanceof StandEntityAbility standAbility) {
			Vec3 targetPos = standAbility.lockOnTargetPosition(standEntity.getUserPower(), standEntity, action)
					? taskTargetPos(action.getTaskTarget(standEntity.level())) : null;
			if (targetPos != null) {
				Vec3 toTarget = targetPos.subtract(userEntity.getEyePosition());
				yRot = MathUtil.yRotDegFromVec(toTarget);
				xRot = MathUtil.xRotDegFromVec(toTarget);
			}
			else if (rotations != Rotations.BODY) {
				yRot = standAbility.yRotForOffset(userEntity, action);
			}
		}
		return relativeToAbsolute(relativeVec, rotations, yRot, xRot, standEntity.getUserBroadcastedSettings(), canInvertSide);
	}

	// 1.16 ActionTarget.getTargetPos(true): an entity's eyes, whatever entity it is, a block's centre
	@Nullable
	static Vec3 taskTargetPos(ActionTarget target) {
		return switch (target.getType()) {
			case ENTITY -> target.getEntity() != null ? target.getEntity().getEyePosition() : null;
			case BLOCK -> Vec3.atCenterOf(target.getBlockPos());
			default -> null;
		};
	}
	
	// 1.16 StandEntity.defaultRotation set the yaw, the pitch and the head and left the body to the Stand's own head turn
	// (StandEntity.tickHeadTurn)
	public void copyRotation(LivingEntity userEntity, boolean lerp) {
		standEntity.setYRot(userEntity.getYRot());
		standEntity.setXRot(userEntity.getXRot());
		standEntity.yRotO = userEntity.yRotO;
		standEntity.yHeadRot = userEntity.yHeadRot;
		standEntity.yHeadRotO = userEntity.yHeadRotO;
	}
	
//	public float getLerpAmount() {
//		return getLerpAmount(0);
//	}
	
	public float getLerpAmount(/*int tickOffset*/) {
		if (!lerps) {
			return 1.0F;
		}
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
