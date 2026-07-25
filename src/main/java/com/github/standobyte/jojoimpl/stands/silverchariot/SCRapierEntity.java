package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SCRapierEntity extends ModdedProjectileEntity {

	private static final int MAX_RICOCHETS = 100;
	private static final double RICOCHET_TRACE_RANGE = 16.0D;
	private static final double RICOCHET_TRACE_INFLATE = 1.0D;

	private int ricochetCount;

	public SCRapierEntity(LivingEntity shooter, net.minecraft.world.level.Level level) {
		super(ModEntityTypes.SC_RAPIER.get(), shooter, level);
	}

	public SCRapierEntity(EntityType<? extends SCRapierEntity> type, net.minecraft.world.level.Level level) {
		super(type, level);
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	protected float getBaseDamage() {
		LivingEntity owner = getOwner();
		float damage;
		if (owner != null) {
			damage = (float) owner.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		}
		else {
			damage = (float) ModStands.SILVER_CHARIOT.get().getStandStats().power();
		}
		return damage * 1.5F;
	}

	@Override
	protected float getDamageFinalCalc(float damage) {
		return damage + (float) ricochetCount * 0.5F;
	}

	@Override
	protected boolean debuffsFromStand() {
		return false;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	public int ticksLifespan() {
		return Integer.MAX_VALUE;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (!super.canHitEntity(entity)) {
			return false;
		}
		if (entity instanceof Skeleton && random.nextFloat() < 0.05F) {
			return false;
		}
		return true;
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		// SC rapier never breaks on hit; it sticks (legacy parity) and returns to user via take-rapier-on-touch.
	}

	@Override
	protected void onHitBlock(BlockHitResult blockHit) {
		boolean ricochet = false;
		if (ricochetCount < MAX_RICOCHETS) {
			BlockPos blockPos = blockHit.getBlockPos();
			BlockState blockState = level().getBlockState(blockPos);
			SoundType soundType = blockState.getSoundType(level(), blockPos, this);
			level().playSound(null, blockPos, soundType.getHitSound(), SoundSource.BLOCKS,
					(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
			ricochet = ricochet(blockHit.getDirection());
		}
		if (!ricochet) {
			Vec3 pos = position();
			Vec3 movementVec = getDeltaMovement();
			Direction hitFace = blockHit.getDirection();
			Vec3 blockVec = Vec3.atCenterOf(blockHit.getBlockPos())
					.add(Vec3.atLowerCornerOf(hitFace.getNormal()).scale(0.5));
			double k;
			switch (hitFace.getAxis()) {
				case X:
					if (movementVec.x == 0.0) return;
					k = (blockVec.x - pos.x) / movementVec.x;
					break;
				case Y:
					if (movementVec.y == 0.0) return;
					k = (blockVec.y - pos.y) / movementVec.y;
					break;
				case Z:
					if (movementVec.z == 0.0) return;
					k = (blockVec.z - pos.z) / movementVec.z;
					break;
				default:
					return;
			}
			setPos(getX() + movementVec.x * k,
					getY() + movementVec.y * k,
					getZ() + movementVec.z * k);
			setDeltaMovement(Vec3.ZERO);
		}
	}

	private boolean ricochet(Direction hitSurfaceDirection) {
		if (hitSurfaceDirection == null) {
			return false;
		}
		Vec3 motion = getDeltaMovement();
		Vec3 motionNew;
		switch (hitSurfaceDirection.getAxis()) {
			case X:
				motionNew = new Vec3(-motion.x, motion.y, motion.z);
				break;
			case Y:
				motionNew = new Vec3(motion.x, -motion.y, motion.z);
				break;
			case Z:
				motionNew = new Vec3(motion.x, motion.y, -motion.z);
				break;
			default:
				return false;
		}
		Vec3 from = position();
		if (motionNew.lengthSqr() == 0.0D) {
			return false;
		}
		if (HitResultUtil.clipMultipleTargets(from, motionNew, RICOCHET_TRACE_RANGE,
				level(), this, SCRapierEntity::canRicochetHit, RICOCHET_TRACE_INFLATE, 0.0D).isEmpty()) {
			return false;
		}
		setDeltaMovement(motionNew);
		rotateTowardsMovement(1.0F);
		ricochetCount++;
		return true;
	}

	private static boolean canRicochetHit(Entity entity) {
		return entity.isAlive() && !entity.isSpectator() && entity.isPickable();
	}

	@Override
	public void playerTouch(Player player) {
		if (level().isClientSide()) {
			return;
		}
		if (!leftOwner) {
			return;
		}
		LivingEntity owner = getOwner();
		if (owner instanceof StandEntity stand) {
			LivingEntity user = stand.getUser();
			if (stand.isFollowingUser() && user != null && player.is(user)) {
				takeRapier(stand);
			}
		}
	}

	public void takeRapier(StandEntity stand) {
		if (!leftOwner || !stand.is(getOwner())) {
			return;
		}
		LivingEntity user = stand.getUser();
		if (user != null) {
			takeRapier(user);
			stand.refreshSilverChariotStateAfterMutation(user);
		}
	}

	public void takeRapier(LivingEntity user) {
		SilverChariotState state = SilverChariotState.get(user);
		if (state != null) {
			state.setHasRapier(true);
		}
		StandPower power = StandPower.get(user);
		if (power != null) {
			if (power.isAbilityUnlocked("rapier_launch")) {
				power.setAbilityCooldown("rapier_launch", 0);
			}
		}
		level().playSound(null, getX(), getY(), getZ(),
				SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
		discard();
	}

	@Override
	public boolean isCurrentlyGlowing() {
		boolean shouldGlow = level().isClientSide()
				&& getOwner() instanceof StandEntity stand
				&& stand.getUser() == ClientProxy.getClientPlayer();
		return shouldGlow || super.isCurrentlyGlowing();
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	private static final Vec3 OFFSET_YROT = new Vec3(0.0, -0.29, 0.375);
	private static final Vec3 OFFSET_XROT = new Vec3(0.0, 0.0, 1.375);

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return OFFSET_YROT;
	}

	@Override
	protected Vec3 getXRotOffset() {
		return OFFSET_XROT;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("Ricochets", ricochetCount);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		ricochetCount = nbt.getInt("Ricochets");
	}
}
