package rotp.core.subsystems.entity_grab;

import java.util.Optional;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.client.entityrender.ModelUtil;
import rotp.core.core.JojoMod;
import rotp.core.entityattachment.TickingEntityData;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.MathUtil;
import rotp.core.util.functions.UtilFunctions;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class LivingComponentGrab implements TickingEntityData {
	static final AttributeModifier GRABBED_NO_ATTACK_POWER = new AttributeModifier(
			JojoMod.resLoc("grabbed_no_attack"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	static final AttributeModifier GRABBED_NO_GRAVITY = new AttributeModifier(
			JojoMod.resLoc("grabbed_no_gravity"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	private final LivingEntity thisEntity;
	private LivingEntity grabbingEntity;
	private LivingEntity grabbedTarget;
	private long grabRevision;

	public float xRotWhenGrabbed;
	public float yRotDiffWhenGrabbed;
	public float yHeadRotDiffWhenGrabbed;
	public float yBodyRotDiffWhenGrabbed;

	public LivingComponentGrab(LivingEntity entity) {
		this.thisEntity = entity;
		addTicking(entity);
	}

	@Nullable
	public static LivingEntity getEntityGrabbedBy(LivingEntity grabbing) {
		if (!grabbing.hasData(ModDataAttachmentTypes.LIVING_GRAB.get())) return null;
		LivingComponentGrab grabbingData = grabbing.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
		return grabbingData != null ? grabbingData.grabbedTarget : null;
	}

	@Nullable
	public static LivingEntity getEntityGrabbing(LivingEntity target) {
		if (!target.hasData(ModDataAttachmentTypes.LIVING_GRAB.get())) return null;
		LivingComponentGrab targetData = target.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
		return targetData != null ? targetData.grabbingEntity : null;
	}

	@Override
	public void tick() {
		if (grabbedTarget != null) {
			if (!grabbedTarget.isAlive()) {
				setGrabTarget(null);
			}
			else if (thisEntity instanceof StandEntity stand) {
				LivingEntity user = stand.getUser();
				if (user != null && grabbedTarget.isPassengerOfSameVehicle(user)) {
					setGrabTarget(null);
				}
			}
		}

		if (grabbingEntity != null) {
			if (!grabbingEntity.isAlive()) {
				LivingComponentGrab grabbingData = grabbingEntity.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
				if (grabbingData != null) {
					grabbingData.setGrabTarget(null);
				}
			}
			else {
				thisEntity.fallDistance = 0;
			}
		}
	}

	public void setGrabTarget(@Nullable LivingEntity target) {
		if (grabbedTarget != null && grabbedTarget != target) {
			LivingComponentGrab oldTargetData = grabbedTarget.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			if (oldTargetData != null) {
				oldTargetData.setGrabbedBy(null);
			}
		}

		if (target != null) {
			LivingComponentGrab targetData = target.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			if (targetData != null && !targetData.isGrabbed()) {
				targetData.setGrabbedBy(thisEntity);
			}
			else {
				target = null;
			}
		}

		grabbedTarget = target;
		if (!thisEntity.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(thisEntity,
					new TrSetGrabbedEntityPacket(thisEntity.getId(), target != null ? target.getId() : -1));
		}
	}

	@ApiStatus.Internal
	public void setGrabbedBy(@Nullable LivingEntity grabbing) {
		boolean clientSide = thisEntity.level().isClientSide();
		if (this.grabbingEntity != grabbing) {
			TimeStopState timeStop = getTimeStopState();
			if (this.grabbingEntity != null && thisEntity.isAlive()
					&& timeStop != null && timeStop.shouldFreeze(thisEntity)) {
				setGrabbedPos();
			}
			grabRevision++;
		}
		if (!clientSide) {
			Optional.ofNullable(thisEntity.getAttribute(Attributes.ATTACK_DAMAGE)).ifPresent(attackDamage -> {
				attackDamage.removeModifier(GRABBED_NO_ATTACK_POWER.id());
				if (grabbing != null) {
					attackDamage.addTransientModifier(GRABBED_NO_ATTACK_POWER);
				}
			});
			Optional.ofNullable(thisEntity.getAttribute(Attributes.GRAVITY)).ifPresent(gravity -> {
				gravity.removeModifier(GRABBED_NO_GRAVITY.id());
				if (grabbing != null) {
					gravity.addTransientModifier(GRABBED_NO_GRAVITY);
				}
			});
		}

		if (grabbing != null) {
			if (!clientSide && thisEntity.isPassenger()) {
				thisEntity.stopRiding();
			}
			saveRotationDiff(grabbing);
		}

		this.grabbingEntity = grabbing;
	}

	public long getGrabRevision() {
		return grabRevision;
	}

	public boolean isGrabbed() {
		return grabbingEntity != null && grabbingEntity.isAlive();
	}

	@Nullable
	public LivingEntity getGrabbedEntity() {
		return grabbedTarget;
	}

	protected void saveRotationDiff(LivingEntity grabbing) {
		this.xRotWhenGrabbed = thisEntity.getXRot();
		float yRot = thisEntity.getYRot();
		this.yRotDiffWhenGrabbed = yRot - grabbing.yBodyRot;
		this.yHeadRotDiffWhenGrabbed = thisEntity.getYHeadRot() - yRot;
		this.yBodyRotDiffWhenGrabbed = thisEntity.yBodyRot - yRot;
	}

	protected static Vec3 armChokeOffset = new Vec3(0, -0.125, 0);

	@ApiStatus.Internal
	public void setGrabbedPos() {
		if (grabbingEntity == null) {
			return;
		}

		HumanoidArm grabbingArm = HumanoidArm.LEFT;
		double neckY = -thisEntity.getBbHeight() * 0.8 - 0.125;
		Vec3 grabOffset = new Vec3(0, neckY, 0);

		boolean useModelArmPos = grabbingEntity.level().isClientSide();
		float yRot = -grabbingEntity.yBodyRot * MathUtil.DEG_TO_RAD;
		if (useModelArmPos) {
			Vec3 animOffset = ModelUtil.getModelPartPos(grabbingEntity,
					grabbingArm == HumanoidArm.LEFT ? "left_item" : "right_item", armChokeOffset);
			if (animOffset != null) {
				grabOffset = grabOffset.add(animOffset.yRot(yRot));
			}
			else {
				useModelArmPos = false;
			}
		}

		if (!useModelArmPos) {
			grabOffset = grabOffset.add(new Vec3(grabbingArm == HumanoidArm.LEFT ? 0.2 : -0.2, 1.5, 0.875).yRot(yRot));
		}

		Vec3 grabbedPos = grabbingEntity.position().add(grabOffset);
		thisEntity.setPos(grabbedPos.x, grabbedPos.y, grabbedPos.z);
		thisEntity.setDeltaMovement(Vec3.ZERO);
		for (Entity passenger : thisEntity.getPassengers()) {
			thisEntity.positionRider(passenger);
		}

		if (!thisEntity.level().isClientSide()) {
			applyRotationDiff();
			TimeStopState timeStop = getTimeStopState();
			if (timeStop != null) {
				timeStop.updateGrabbedEntityPosition(thisEntity);
			}
		}
	}

	@Nullable
	private TimeStopState getTimeStopState() {
		if (thisEntity.level() instanceof ServerLevel level
				&& level.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			return level.getData(ModDataAttachmentTypes.TIME_STOP.get());
		}
		return null;
	}

	@ApiStatus.Internal
	public void applyRotationDiff() {
		if (grabbingEntity != null) {
			thisEntity.setXRot(this.xRotWhenGrabbed);
			float yRot = grabbingEntity.getYRot() + this.yRotDiffWhenGrabbed;
			thisEntity.setYRot(yRot);
			thisEntity.setYHeadRot(yRot + this.yHeadRotDiffWhenGrabbed);
			thisEntity.setYBodyRot(yRot + this.yBodyRotDiffWhenGrabbed);
		}
	}

	@SubscribeEvent
	public static void onLevelTickPost(LevelTickEvent.Post event) {
		Level level = event.getLevel();
		var attachmentType = ModDataAttachmentTypes.LIVING_GRAB.get();
		for (Entity entity : UtilFunctions.getEntities(level)) {
			LivingComponentGrab grabComponent = entity.getData(attachmentType);
			if (grabComponent != null) {
				grabComponent.setGrabbedPos();
			}
		}
	}
}
