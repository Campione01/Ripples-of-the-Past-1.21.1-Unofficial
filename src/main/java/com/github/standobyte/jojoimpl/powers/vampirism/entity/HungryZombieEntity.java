package com.github.standobyte.jojoimpl.powers.vampirism.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismPowerType;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismBloodDrainAbility;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.EventHooks;

public class HungryZombieEntity extends Zombie {
	private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
			HungryZombieEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private double distanceFromOwner;
	private boolean summonedFromAbility;

	public HungryZombieEntity(Level level) {
		this(ModEntityTypes.HUNGRY_ZOMBIE.get(), level);
	}

	public HungryZombieEntity(EntityType<? extends HungryZombieEntity> type, Level level) {
		super(type, level);
		xpReward = Math.round(xpReward * 1.5F);
	}

	public void setSummonedFromAbility() {
		this.summonedFromAbility = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 30.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.ATTACK_DAMAGE, 5.0D);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(6, new HungryZombieFollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
		targetSelector.addGoal(1, new HungryZombieOwnerHurtByTargetGoal(this));
		targetSelector.addGoal(2, new HungryZombieOwnerHurtTargetGoal(this));
	}

	@Override
	protected void addBehaviourGoals() {
		goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false));
		goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0D, true, 4, this::canBreakDoors));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new HungryZombieNearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
		targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER_UUID, Optional.empty());
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		UUID owner = getOwnerUUID();
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		tag.putBoolean("AbilitySummon", summonedFromAbility);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
		summonedFromAbility = tag.getBoolean("AbilitySummon");
	}

	@Nullable
	private UUID getOwnerUUID() {
		return entityData.get(OWNER_UUID).orElse(null);
	}

	private void setOwnerUUID(@Nullable UUID uuid) {
		entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
	}

	public void setOwner(@Nullable LivingEntity owner) {
		setOwnerUUID(owner != null ? owner.getUUID() : null);
	}

	@Nullable
	public LivingEntity getOwner() {
		UUID uuid = getOwnerUUID();
		if (uuid == null) {
			return null;
		}
		Player owner = level().getPlayerByUUID(uuid);
		if (owner != null && PlayerPower.getPowerData(owner, VampirismPowerType.VAMPIRISM)
				.map(data -> data.getCuringStage(owner) >= 4)
				.orElse(true)) {
			setOwner(null);
			return null;
		}
		return owner;
	}

	public boolean isEntityOwner(LivingEntity entity) {
		UUID owner = getOwnerUUID();
		return owner != null && entity.getUUID().equals(owner);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			LivingEntity owner = getOwner();
			distanceFromOwner = owner != null ? distanceToSqr(owner) : -1.0D;
		}
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	public boolean farFromOwner(double distance) {
		return distanceFromOwner > distance * distance;
	}

	@Override
	public boolean canBeLeashed() {
		return true;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !isEntityOwner(target) && super.canAttack(target);
	}

	public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
		if (target instanceof Player targetPlayer && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(targetPlayer)) {
			return false;
		}
		return true;
	}

	@Override
	public PlayerTeam getTeam() {
		LivingEntity owner = getOwner();
		return owner != null ? owner.getTeam() : super.getTeam();
	}

	@Override
	public boolean isAlliedTo(Entity entity) {
		LivingEntity owner = getOwner();
		if (entity == owner) {
			return true;
		}
		return owner != null ? owner.isAlliedTo(entity) : super.isAlliedTo(entity);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {}

	@Override
	protected ItemStack getSkull() {
		return ItemStack.EMPTY;
	}

	@Override
	protected int getBaseExperienceReward() {
		LivingEntity killCredit = getKillCredit();
		return killCredit != null && isEntityOwner(killCredit) ? 0 : super.getBaseExperienceReward();
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (super.doHurtTarget(target)) {
			if (getMainHandItem().isEmpty() && target instanceof LivingEntity livingTarget
					&& livingTarget.getArmorCoverPercentage() <= random.nextFloat()
					&& JojoDefinitions.canBleed(livingTarget)) {
				float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
				if (VampirismBloodDrainAbility.drainBlood(this, livingTarget, damage / 5.0F)) {
					heal(damage / 2.0F);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean killedEntity(ServerLevel level, LivingEntity dead) {
		boolean result = super.killedEntity(level, dead);
		if (level.getDifficulty() != Difficulty.EASY) {
			createZombie(level, getOwner(), dead, isPersistenceRequired());
		}
		return result;
	}

	public static boolean createZombie(ServerLevel level, @Nullable LivingEntity owner, LivingEntity dead, boolean makePersistent) {
		boolean shouldConvert = level.getDifficulty() == Difficulty.HARD
				|| level.getDifficulty() == Difficulty.NORMAL && dead.getRandom().nextBoolean()
				|| level.getDifficulty() == Difficulty.EASY && dead.getRandom().nextFloat() <= 0.125F;
		if (!shouldConvert || !(dead instanceof Villager || dead instanceof AbstractIllager) || !(dead instanceof Mob deadMob)) {
			return false;
		}
		if (!EventHooks.canLivingConvert(dead, ModEntityTypes.HUNGRY_ZOMBIE.get(), ticks -> {})) {
			return false;
		}

		HungryZombieEntity zombie = deadMob.convertTo(ModEntityTypes.HUNGRY_ZOMBIE.get(), true);
		if (zombie == null) {
			return false;
		}
		zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()),
				MobSpawnType.CONVERSION, new ZombieGroupData(false, true));
		zombie.setOwner(owner);
		if (makePersistent) {
			zombie.setPersistenceRequired();
		}
		EventHooks.onLivingConvert(dead, zombie);
		if (!dead.isSilent()) {
			level.levelEvent(null, 1026, dead.blockPosition(), 0);
		}
		return true;
	}

	private static class HungryZombieNearestAttackableTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
		private final HungryZombieEntity zombie;

		private HungryZombieNearestAttackableTargetGoal(HungryZombieEntity zombie, Class<T> targetClass, boolean mustSee) {
			super(zombie, targetClass, mustSee);
			this.zombie = zombie;
		}

		@Override
		public boolean canUse() {
			if (super.canUse()) {
				LivingEntity owner = zombie.getOwner();
				return owner == null || zombie.farFromOwner(16);
			}
			return false;
		}
	}

	private static class HungryZombieFollowOwnerGoal extends Goal {
		private final HungryZombieEntity zombie;
		private final double speed;
		private final float startDistance;
		private final float stopDistance;
		private final PathNavigation navigation;
		@Nullable
		private LivingEntity owner;
		private int timeToRecalcPath;
		private float oldWaterCost;

		private HungryZombieFollowOwnerGoal(HungryZombieEntity zombie, double speed, float startDistance, float stopDistance) {
			this.zombie = zombie;
			this.speed = speed;
			this.startDistance = startDistance;
			this.stopDistance = stopDistance;
			this.navigation = zombie.getNavigation();
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity owner = zombie.getOwner();
			if (owner == null || zombie.distanceToSqr(owner) < startDistance * startDistance) {
				return false;
			}
			this.owner = owner;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return owner != null && !navigation.isDone() && zombie.distanceToSqr(owner) > stopDistance * stopDistance;
		}

		@Override
		public void start() {
			timeToRecalcPath = 0;
			oldWaterCost = zombie.getPathfindingMalus(PathType.WATER);
			zombie.setPathfindingMalus(PathType.WATER, 0.0F);
		}

		@Override
		public void stop() {
			owner = null;
			navigation.stop();
			zombie.setPathfindingMalus(PathType.WATER, oldWaterCost);
		}

		@Override
		public void tick() {
			if (owner == null) {
				return;
			}
			zombie.getLookControl().setLookAt(owner, 10.0F, zombie.getMaxHeadXRot());
			if (--timeToRecalcPath <= 0) {
				timeToRecalcPath = adjustedTickDelay(10);
				navigation.moveTo(owner, speed);
			}
		}
	}

	private static class HungryZombieOwnerHurtByTargetGoal extends TargetGoal {
		private final HungryZombieEntity zombie;
		@Nullable
		private LivingEntity ownerLastHurtBy;
		private int timestamp;

		private HungryZombieOwnerHurtByTargetGoal(HungryZombieEntity zombie) {
			super(zombie, false);
			this.zombie = zombie;
			setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			LivingEntity owner = zombie.getOwner();
			if (owner == null) {
				return false;
			}
			ownerLastHurtBy = owner.getLastHurtByMob();
			int ownerTimestamp = owner.getLastHurtByMobTimestamp();
			return ownerTimestamp != timestamp
					&& ownerLastHurtBy != null
					&& canAttack(ownerLastHurtBy, TargetingConditions.DEFAULT)
					&& zombie.wantsToAttack(ownerLastHurtBy, owner);
		}

		@Override
		public void start() {
			mob.setTarget(ownerLastHurtBy);
			LivingEntity owner = zombie.getOwner();
			if (owner != null) {
				timestamp = owner.getLastHurtByMobTimestamp();
			}
			super.start();
		}
	}

	private static class HungryZombieOwnerHurtTargetGoal extends TargetGoal {
		private final HungryZombieEntity zombie;
		@Nullable
		private LivingEntity ownerLastHurt;
		private int timestamp;

		private HungryZombieOwnerHurtTargetGoal(HungryZombieEntity zombie) {
			super(zombie, false);
			this.zombie = zombie;
			setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			LivingEntity owner = zombie.getOwner();
			if (owner == null) {
				return false;
			}
			ownerLastHurt = owner.getLastHurtMob();
			int ownerTimestamp = owner.getLastHurtMobTimestamp();
			return ownerTimestamp != timestamp
					&& ownerLastHurt != null
					&& canAttack(ownerLastHurt, TargetingConditions.DEFAULT)
					&& zombie.wantsToAttack(ownerLastHurt, owner);
		}

		@Override
		public void start() {
			mob.setTarget(ownerLastHurt);
			LivingEntity owner = zombie.getOwner();
			if (owner != null) {
				timestamp = owner.getLastHurtMobTimestamp();
			}
			super.start();
		}
	}
}
