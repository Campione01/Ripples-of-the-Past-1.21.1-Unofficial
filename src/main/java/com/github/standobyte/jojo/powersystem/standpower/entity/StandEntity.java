package com.github.standobyte.jojo.powersystem.standpower.entity;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.customobjects.EntityStandVisibility;
import com.github.standobyte.jojo.customobjects.EntityWithStandSkin;
import com.github.standobyte.jojo.customobjects.LivingReactToNewAction;
import com.github.standobyte.jojo.customobjects.entity_projectile.DamagingEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModEntityAttributes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModSpecialActions;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.item.KnifeItem;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.mechanics.resolve.ResolveCounter;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.network.s2c.StandEntitySoundPacket;
import com.github.standobyte.jojo.network.s2c.TrSetStandEntityPacket;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.powersystem.standpower.type.SummonedStand;
import com.github.standobyte.jojo.subsystems.EntityHandItemsAsInventory;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.PlayerExternalContainers;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer._stand.StandHandsContainerMenu;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.AttributeUtil;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.CollisionHelper;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojo.util.functions.MathUtil.AABBDist;
import com.github.standobyte.jojo.util.objects_java.Lerp;
import com.github.standobyte.jojo.util.objects_mc.PrevRotations;
import com.github.standobyte.jojo.util.sound.MultiSoundEventResolver;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityManualControlToggle;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityUnsummonAction;
import com.github.standobyte.jojoimpl.stands.silverchariot.SCRapierEntity;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotState;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandEntity extends LivingEntity implements SummonedStand, IEntityWithComplexSpawn, LivingReactToNewAction, EntityStandVisibility, EntityWithStandSkin {
	private static final ResourceLocation SILVER_CHARIOT_ID = JojoMod.resLoc("silver_chariot");
	private static final ResourceLocation THE_WORLD_ID = JojoMod.resLoc("the_world");
	private static final ResourceLocation MAGICIANS_RED_ID = JojoMod.resLoc("magicians_red");
	private static final int MAGICIANS_RED_STAND_ATTACK_FIRE_TICKS = 10 * 20;
	private static final double SILVER_CHARIOT_ARMOR = 20.0D;
	private static final double SILVER_CHARIOT_ARMOR_TOUGHNESS = 12.0D;
	private static final StandStatFormulas.BlockMiningTier SILVER_CHARIOT_RAPIER_HARVEST_TIER =
			new StandStatFormulas.BlockMiningTier.VanillaTierWrapper(Tiers.WOOD);
	private static final AttributeModifier SC_NO_ARMOR_MOVEMENT_SPEED_BOOST = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_armor_movement_speed"), 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	private static final AttributeModifier SC_NO_ARMOR_ATTACK_SPEED_BOOST = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_armor_attack_speed"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	private static final AttributeModifier SC_NO_ARMOR = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_armor"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier SC_NO_ARMOR_TOUGHNESS = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_armor_toughness"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier SC_NO_ARMOR_DURABILITY_DECREASE = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_armor_durability"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier SC_NO_RAPIER_DAMAGE_DECREASE = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_rapier_damage"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	private static final AttributeModifier SC_NO_RAPIER_ATTACK_SPEED_DECREASE = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_rapier_attack_speed"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	private static final double SILVER_CHARIOT_RAPIER_RANGE = 1;
	private static final AttributeModifier SC_NO_RAPIER_BLOCK_RANGE_DECREASE = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_rapier_block_range"), -SILVER_CHARIOT_RAPIER_RANGE, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier SC_NO_RAPIER_ENTITY_RANGE_DECREASE = new AttributeModifier(
			JojoMod.resLoc("silver_chariot_no_rapier_entity_range"), -SILVER_CHARIOT_RAPIER_RANGE, AttributeModifier.Operation.ADD_VALUE);
	private static final int SILVER_CHARIOT_RAPIER_FIRE_TICKS = 300;
	private static final AttributeModifier ATTACK_DAMAGE_ARMS_ONLY = new AttributeModifier(
			JojoMod.resLoc("stand_arms_only_attack_damage"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier DURABILITY_ARMS_ONLY = new AttributeModifier(
			JojoMod.resLoc("stand_arms_only_durability"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier PRECISION_ARMS_ONLY = new AttributeModifier(
			JojoMod.resLoc("stand_arms_only_precision"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	
	protected ResourceLocation standId;
	protected EntityDimensions standDimensions;
	protected static final EntityDataAccessor<Byte> STAND_FLAGS = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.BYTE);
	protected static final EntityDataAccessor<Byte> ARMS_ONLY_MODE = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.BYTE);
	protected static final EntityDataAccessor<Boolean> SWING_OFF_HAND = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.BOOLEAN);
	protected static final EntityDataAccessor<Integer> USER_ID = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Integer> NO_BLOCKING_TICKS = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Integer> BARRAGE_CLASH_OPPONENT_ID = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Byte> MANUAL_MOVEMENT_LOCK = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.BYTE);
	protected WeakReference<LivingEntity> userRef = new WeakReference<>(null);
	protected StandPower userPower;
	private Optional<PlayerClientBroadcastedSettings> playerSettings = Optional.empty();
	protected final LivingComponentAction standAction;
	private final ManualStandMovementLock manualMovementLocks = new ManualStandMovementLock(this);
	
	protected static final EntityDataAccessor<Float> FINISHER_VALUE = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.FLOAT);
	protected static final EntityDataAccessor<Float> LAST_HEAVY_FINISHER_VALUE = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.FLOAT);
	
	public double Y_OFFSET = 0.2;
	public StandOffsetFromUser offsetFromUser;
    public double rangeEfficiency = 1;
    public double staminaCondition = 1;
	private boolean distanceStrengthDecayEnabled = true;
    public Lerp.FloatValue modelAlpha = new Lerp.FloatValue(1);
	public int summonLockTicks;
	public int gradualSummonWeaknessTicks;
	private int summonPoseRandomByte;
	private int alphaTicks;
	public int overlayTickCount;
	private boolean noFireAnimFrame;
	private int silverChariotRapierFireTicks;
	private float blockDamage = 0;
	private float prevBlockDamage = 0;
	private boolean wasDamageBlocked;
	public int barrageHits;
	private boolean barrageParryAccumulating;
	private int barrageParryCount;
	private Optional<Entity> barrageClashOpponent = Optional.empty();
	private int lastStandSwingTick;
	private boolean alternateAdditionalSwing;
	private static final Map<LivingEntity, StandAttackInvulState> STAND_ATTACK_INVUL = Collections.synchronizedMap(new WeakHashMap<>());
	
	public ClientStandEntityStuff clientStuff;

	private static final class StandAttackInvulState {
		private float lastStandDamage;
		private int expiresAtTick;
	}

	public StandEntity(EntityType<? extends StandEntity> type, Level level) {
		super(type, level);
		this.standAction = LivingComponentAction.getComponent(this);
		this.offsetFromUser = StandOffsetFromUser.createDefault(this);
		if (level.isClientSide()) {
			this.clientStuff = new ClientStandEntityStuff();
		}
		if (!level.isClientSide()) {
			this.summonPoseRandomByte = random.nextInt(128);
		}
	}
	
	public StandEntity withStandType(StandType standType) {
		if (isAddedToLevel()) throw new IllegalStateException();
		initStandTypeState(standType);
		return this;
	}

	private void initStandTypeState(StandType standType) {
		this.standId = standType.getId();
		initStandStatsValues(standType.getStandStats());
		if (standType instanceof EntityStandType entityStandType) {
			standDimensions = entityStandType.standDimensions;
			distanceStrengthDecayEnabled = entityStandType.usesDistanceStrengthDecay();
			this.refreshDimensions();
		}
		initSilverChariotArmorAttributes();
		initSummonAlphaTicks(standType.getStandStats().speed());
	}

	public EntityDimensions getStandDimensions() {
		return standDimensions;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return standDimensions != null ? standDimensions.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
	}

	private void initSilverChariotArmorAttributes() {
		if (!isSilverChariotStand()) {
			return;
		}
		if (getAttribute(Attributes.ARMOR) != null) {
			getAttribute(Attributes.ARMOR).setBaseValue(SILVER_CHARIOT_ARMOR);
		}
		if (getAttribute(Attributes.ARMOR_TOUGHNESS) != null) {
			getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(SILVER_CHARIOT_ARMOR_TOUGHNESS);
		}
		if (getAttribute(Attributes.BLOCK_INTERACTION_RANGE) != null) {
			getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(DEFAULT_ATTACK_RANGE + SILVER_CHARIOT_RAPIER_RANGE);
		}
		if (getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null) {
			getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(DEFAULT_ATTACK_RANGE + SILVER_CHARIOT_RAPIER_RANGE);
		}
	}

	private void initSummonAlphaTicks(double speed) {
		if (isArmsOnlyMode()) {
			this.summonLockTicks = 0;
			this.gradualSummonWeaknessTicks = 0;
			this.alphaTicks = 0;
			return;
		}
		this.summonLockTicks = StandStatFormulas.getSummonLockTicks(speed);
		this.gradualSummonWeaknessTicks = 0;
		this.alphaTicks = this.summonLockTicks;
	}

	public int getSummonPoseRandomByte() {
		return summonPoseRandomByte;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(USER_ID, -1);
		builder.define(STAND_FLAGS, defaultStandFlags());
		builder.define(ARMS_ONLY_MODE, (byte) 0);
		builder.define(SWING_OFF_HAND, false);
		builder.define(FINISHER_VALUE, 0f);
		builder.define(LAST_HEAVY_FINISHER_VALUE, 0f);
		builder.define(NO_BLOCKING_TICKS, 0);
		builder.define(BARRAGE_CLASH_OPPONENT_ID, -1);
		builder.define(MANUAL_MOVEMENT_LOCK, (byte) 0);
	}
	
	@Override
	public void onAddedToLevel() {
		super.onAddedToLevel();
		if (standHasNoGravity) {
			setNoGravity(true);
		}
		if (standCanHaveNoPhysics) {
			noPhysics = true;
		}
		
		openStandHandsContainer();
	}
	

	public PrevRotations rotO = new PrevRotations();
	@Override
	public void tick() {
		fallDistance = 0;
		modelAlpha.set(1, true);
		rotO.rememberAngles(this);
		LivingEntity user = getUser();
		Level level = level();
		if (!level.isClientSide()) {
			if (requiresUser() && (user == null || user.isRemoved())) {
				this.remove(user != null ? user.getRemovalReason() : RemovalReason.DISCARDED);
				return;
			}
		}
		
		updateStandStatAttributes(this, user);
		
		super.tick();
		if (!level.isClientSide()) {
			int noBlockingTicks = entityData.get(NO_BLOCKING_TICKS);
			if (noBlockingTicks > 0) {
				entityData.set(NO_BLOCKING_TICKS, noBlockingTicks - 1);
			}
			barrageParryAccumulating = false;
			if (barrageClashOpponentOutOfReach()) {
				setBarrageClashOpponent(null);
			}
		}
		
		if (user != null) {
			UtilFunctions.wrapYRotationAngles(user);
			updatePosition(user);
			if (!level.isClientSide()) {
				tickHealth(user);
				syncSilverChariotState(user);
			}
			updateUserOffset(user);
		}
		this.xRotO = rotO.xRot;
		this.yRotO = rotO.yRot;
		this.yBodyRotO = rotO.yBodyRot;
		
		yHeadRot = getYRot();
		yHeadRotO = yRotO;

        updateStrengthMultipliers();
		tickFinisherMeter();
		tickSummonAlpha();
		if (level.isClientSide()) {
			++overlayTickCount;
		}
	}
	
	@Override
	public void aiStep() {
		super.aiStep();
		pickUpItemEntities();
	}
	
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && level() instanceof ServerLevel) {
			dropEquipment(/*level*/);
		}
		super.remove(reason);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		LivingEntity user = getUser();
		if (user != null && player.is(user) && player.isAlive()) {
			StandEntityManualControlToggle.off(level(), this, false);
		}
	}

	
	@Override
	public void setUserAndPower(LivingEntity user, StandPower power) {
		if (!level().isClientSide()) {
			entityData.set(USER_ID, user.getId());
		}
		this.userPower = power;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> dataParameter) {
		super.onSyncedDataUpdated(dataParameter);
		if (STAND_FLAGS.equals(dataParameter)) {
			noPhysics = getStandFlag(StandFlag.NO_PHYSICS);
		}
		else if (USER_ID.equals(dataParameter)) {
			updateUserFromNetwork(entityData.get(USER_ID));
		}
		else if (ARMS_ONLY_MODE.equals(dataParameter)) {
			onArmsOnlyModeUpdated();
		}
		else if (SWING_OFF_HAND.equals(dataParameter)) {
			swingingArm = getPunchingHand();
		}
		else if (BARRAGE_CLASH_OPPONENT_ID.equals(dataParameter)) {
			barrageClashOpponent = Optional.ofNullable(level().getEntity(entityData.get(BARRAGE_CLASH_OPPONENT_ID)));
		}
		else if (MANUAL_MOVEMENT_LOCK.equals(dataParameter)) {
			manualMovementLocks.onEntityDataUpdated(this);
		}
	}
	
	@Override
	public void tickStand(LivingEntity user, StandPower userStand) {
		if (!user.level().isClientSide() && this.isRemoved()) {
			userStand.setSummonedStand(null);
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrSetStandEntityPacket(user.getId(), 0));
		}
	}
	
	@Override
	public StandEntity getStandEntity() {
		return this;
	}
	
	/**
	 * Careful - the user's entity might not always be loaded on client in case of long-ranged Stands.
	 */
	@Nullable
	public LivingEntity getUser() {
		if (hasUser()) {
			LivingEntity user = userRef.get();
			if (user == null) {
				user = lookupUser(entityData.get(USER_ID));
				if (user != null) {
					setUserRef(user);
				}
			}
			return user;
		}
		return null;
	}

	public Optional<PlayerClientBroadcastedSettings> getUserBroadcastedSettings() {
		return playerSettings;
	}

	@Nullable
	public StandPower getUserPower() {
		if (userPower == null && hasUser()) {
			LivingEntity user = getUser();
			if (user != null) {
				userPower = StandPower.get(user);
			}
		}
		return userPower;
	}

	protected final boolean hasUser() {
		return entityData.get(USER_ID) >= 0;
	}
	
	protected void updateUserFromNetwork(int userId) {
		LivingEntity user = lookupUser(userId);
		setUserRef(user);
		if (user != null) {
			if (user instanceof Player player) {
				playerSettings = PlayerClientBroadcastedSettings.getPlayerSettings(player);
			}
			else {
				playerSettings = Optional.empty();
			}
			if (level().isClientSide()) {
				StandPower standPower = StandPower.get(user);
				if (standPower != null && standPower.getSummonedStand() != this) {
					standPower.setSummonedStand(this);
				}
			}
		}
		else {
			playerSettings = Optional.empty();
		}
	}

	@Nullable
	protected LivingEntity lookupUser(int userId) {
		Entity user = level().getEntity(userId);
		return user instanceof LivingEntity living ? living : null;
	}
	
	protected void setUserRef(LivingEntity userEntity) {
		this.userRef = new WeakReference<>(userEntity);
	}
	
	
	public void updatePosition(LivingEntity user) {
		if (ModStatusEffects.isStunned(this)) return;
		if (isFollowingUser()) {
			if (user != null) {
				Vec3 pos = offsetFromUser.getPosition(user);
				if (offsetFromUser.standAbility == null && user.isShiftKeyDown()) {
					pos = new Vec3(pos.x, user.getY(), pos.z);
				}
				if (!isArmsOnlyMode()) {
					pos = collideNextPos(pos);
				}
				setPos(pos.x, pos.y, pos.z);
				copyStandUserRotation(user);
				EntityActionInstance curAction = standAction.getAction();
				if (curAction != null) {
					curAction.applyStandUserRotation(this, user);
				}
			}
			lookAtCurTarget(rotO);
		}
		else if (isManuallyControlled()) {
			moveStandManualControl();
		}
		if (isBeingRetracted() && user != null) {
			if (!isCloseToUser()) {
				Vec3 targetPos = offsetFromUser.getPosition(user);
				Vec3 movementVec = targetPos.subtract(position());
				setDeltaMovement(movementVec.normalize().scale(getAttributeValue(Attributes.MOVEMENT_SPEED)));
			}
			else {
				setDeltaMovement(Vec3.ZERO);
				setStandFlag(StandFlag.BEING_RETRACTED, false);
			}
		}
	}
	
	public Vec3 collideNextPos(Vec3 pos) {
		if (noPhysics) {
			return pos;
		}
		AABB collisionBox = getBoundingBox();
		double height = collisionBox.getYsize();
		double width = collisionBox.getXsize();
		if (height > width) {
			collisionBox = new AABB(
					collisionBox.minX,
					collisionBox.maxY - Math.max(height * 0.5, width),
					collisionBox.minZ,
					collisionBox.maxX,
					collisionBox.maxY,
					collisionBox.maxZ);
		}
		Vec3 movement = pos.subtract(position());
		CollisionHelper.BlockCollisionResult collision = CollisionHelper.collideBoundingBox(
				movement, collisionBox, level(), CollisionContext.of(this));
		return position().add(collision.x, collision.y, collision.z);
	}
	
	public void copyStandUserRotation(LivingEntity user) {
		offsetFromUser.copyRotation(user, level().isClientSide());
	}
	
	public boolean lookAtCurTarget(PrevRotations rotO) {
		ActionTarget lookTarget;
		EntityActionInstance curAction = standAction.getAction();
		boolean fullyRotateBody = curAction != null;
		if (curAction != null) {
			lookTarget = curAction.standRotationTarget;
			if (lookTarget == null) {
				lookTarget = ActionTarget.EMPTY;
			}
			else if (lookTarget.isEmpty(level())) {
				curAction.standRotationTarget = null;
				lookTarget = ActionTarget.EMPTY;
			}
		}
		else {
			ActionTarget crosshairTarget = standAction.entityAim.getTarget();
			if (crosshairTarget.getType() == TargetType.ENTITY) {
				lookTarget = crosshairTarget;
			}
			else {
				lookTarget = ActionTarget.EMPTY;
			}
		}
		
		return lookAtTarget(lookTarget, fullyRotateBody);
	}
	
	public Vec3 getPosToLookAt(ActionTarget target) {
		EntityActionInstance curAction = standAction.getAction();
		if (curAction != null) {
			Vec3 targetOverride = curAction.getStandLookTargetPosition(this, target);
			if (targetOverride != null) {
				return targetOverride;
			}
		}
		return switch (target.getType()) {
			case ENTITY -> {
				Entity targetEntity = target.getEntity();
                if (targetEntity != null){
                    // TODO (stand aiming) look closer to where the user is looking (legs/head aiming)
                    double y = targetEntity instanceof LivingEntity ?
                            targetEntity.getEyeY() :
                            (targetEntity.getBoundingBox().minY + targetEntity.getBoundingBox().maxY) / 2.0;
                    yield new Vec3(targetEntity.getX(), y, targetEntity.getZ());
                }
				yield null;
			}
			case BLOCK -> {
				yield Vec3.atCenterOf(target.getBlockPos());
			}
			default -> null;
		};
	}
	
	public boolean lookAtTarget(ActionTarget target, boolean fullyRotateBody) {
		Vec3 targetPos = getPosToLookAt(target);
		
		if (targetPos != null) {
			Vec2 rotations = MathUtil.lookAnglesTowards(targetPos, this, EntityAnchorArgument.Anchor.EYES);
			if (fullyRotateBody) {
				this.setXRot(rotations.x);
				this.setYRot(rotations.y);
				
				this.setYHeadRot(this.getYRot());
				this.setYBodyRot(this.getYRot());
			}
			else {
				float maxHeadYRot = 75;
				float f2 = Mth.wrapDegrees(yBodyRot - rotations.y);
				if (Math.abs(f2) < maxHeadYRot) {
					this.setXRot(rotations.x);
					this.setYRot(rotations.y);
					this.setYHeadRot(this.getYRot());
				}
			}
//			this.xRotO = rotO.xRot;
//			this.yRotO = rotO.yRot;
//			this.yHeadRotO = rotO.yHeadRot;
//			this.yBodyRotO = rotO.yBodyRot;
			return true;
		}
		return false;
	}
	
	protected Vec3 _offsetFromUserVec;
	protected Vec3 _manualControlInput = Vec3.ZERO;
	protected void updateUserOffset(LivingEntity user) {
		if (user != null) {
			this._offsetFromUserVec = this.position().subtract(user.position());
		}
	}
	
	public void manualControlInput(Vec3 motionInput) {
		this._manualControlInput = motionInput;
	}
	
	protected void moveStandManualControl() {
		LivingEntity user = getUser();
		if (user != null && isControlledByLocalInstance()) {
			if (_offsetFromUserVec == null) {
				updateUserOffset(user);
			}
			
			if (_offsetFromUserVec != null) {
				_offsetFromUserVec = _offsetFromUserVec.add(_manualControlInput);
				_manualControlInput = Vec3.ZERO;
				
				Vec3 userPos = user.position();
				Vec3 newPos = userPos.add(_offsetFromUserVec);
				Vec3 move = newPos.subtract(this.position());
				Vec3 oldPos = position();
				move(MoverType.SELF, move);
				setDeltaMovement(position().subtract(oldPos));
			}
			else {
				Vec3 oldPos = position();
				move(MoverType.SELF, _manualControlInput);
				setDeltaMovement(position().subtract(oldPos));
				_manualControlInput = Vec3.ZERO;
			}
		}
	}

	@Override
	public void move(MoverType type, Vec3 vec) {
		super.move(type, vec);
		
		LivingEntity user = getUser();
		Level level = this.level();
		if (user != null && user.level() == level) {
			AABBDist bbDistance = MathUtil.getAABBDistanceDetailed(this.getBoundingBox(), user.getBoundingBox());
			double distance = bbDistance.distance();
			double range = getMaxRangeForMovement(user);
			if (distance > range) {
				Vec3 standPos = bbDistance.posBB1();
				Vec3 userPos = bbDistance.posBB2();
				Vec3 vecToUser = userPos.subtract(standPos).scale(1 - range / distance);
				moveWithoutCollision(vecToUser);
			}
			if (!vec.equals(Vec3.ZERO)) {
				updateUserOffset(user);
			}
		}
	}

	public double getMaxRangeForMovement(LivingEntity user) {
		return getMaxRange();
	}

	protected void moveWithoutCollision(Vec3 moveVec) {
		AABB bb = getBoundingBox().move(moveVec);
		setBoundingBox(bb);
		setPosRaw((bb.minX + bb.maxX) / 2, bb.minY, (bb.minZ + bb.maxZ) / 2);
	}

	@Override
	public boolean isControlledByLocalInstance() {
		if (isManuallyControlled()) {
			Entity user = getUser();
			if (user instanceof Player player) {
				return player.isLocalPlayer();
			}
		}
		return isEffectiveAi();
	}

	
	protected void setStandFlag(StandFlag flag, boolean value) {
		byte i = entityData.get(STAND_FLAGS);
		if (value) {
			i |= flag.bit;
		} else {
			i &= ~flag.bit;
		}
		entityData.set(STAND_FLAGS, i);
	}
	
	protected byte defaultStandFlags() {
		byte i = 0;
		for (StandFlag flag : StandFlag.values()) {
			if (flag.defaultValue) {
				i |= flag.bit;
			}
		}
		
		if (standCanHaveNoPhysics) {
			i |= StandFlag.NO_PHYSICS.bit;
		} else {
			i &= ~StandFlag.NO_PHYSICS.bit;
		}
		
		return i;
	}

	public boolean getStandFlag(StandFlag flag) {
		return (entityData.get(STAND_FLAGS) & flag.bit) != 0;
	}

	public static enum StandFlag {
		MANUAL_CONTROL(false),
		CAN_FOLLOW_USER(true),
		BEING_RETRACTED(false),
		NO_PHYSICS(true),
		ARMS_ONLY_MODE(false),
		SILVER_CHARIOT_ARMOR_VISIBLE(true),
		SILVER_CHARIOT_RAPIER_VISIBLE(true),
		SILVER_CHARIOT_RAPIER_ON_FIRE(false);

		public final byte bit;
		public final boolean defaultValue;
		private StandFlag(boolean defaultValue) {
			this.bit = (byte) (1 << ordinal());
			this.defaultValue = defaultValue;
		}
	}

	public void setArmsOnlyMode() {
		setArmsOnlyMode(true, true);
	}

	public void setArmsOnlyMode(boolean armsOnlyMode) {
		if (armsOnlyMode) {
			setArmsOnlyMode(true, true);
		}
		else {
			entityData.set(ARMS_ONLY_MODE, (byte) 0);
			setStandFlag(StandFlag.ARMS_ONLY_MODE, false);
			removeArmsOnlyModifiers();
		}
	}

	public void addToArmsOnly(InteractionHand arm) {
		if (arm != null && isArmsOnlyMode()) {
			byte b = entityData.get(ARMS_ONLY_MODE);
			switch (arm) {
			case MAIN_HAND:
				b |= 4;
				break;
			case OFF_HAND:
				b |= 8;
				break;
			}
			entityData.set(ARMS_ONLY_MODE, b);
			setStandFlag(StandFlag.ARMS_ONLY_MODE, true);
		}
	}

	public void setArmsOnlyMode(boolean showMainArm, boolean showOffArm) {
		byte b = 3;
		if (showMainArm) {
			b |= 4;
		}
		if (showOffArm) {
			b |= 8;
		}
		entityData.set(ARMS_ONLY_MODE, b);
		setStandFlag(StandFlag.ARMS_ONLY_MODE, true);
		addArmsOnlyModifiers();
	}

	private void onArmsOnlyModeUpdated() {
		resetIdleOffsetForArmsOnlyMode();
		if (isArmsOnlyMode()) {
			summonLockTicks = 0;
			gradualSummonWeaknessTicks = 0;
			addArmsOnlyModifiers();
		}
		else {
			if (level().isClientSide()) {
				overlayTickCount = 0;
			}
			if (wasSummonedAsArms()) {
				summonLockTicks = 0;
				gradualSummonWeaknessTicks = StandStatFormulas.getSummonLockTicks(getAttributeValue(Attributes.ATTACK_SPEED));
				alphaTicks = gradualSummonWeaknessTicks;
				if (gradualSummonWeaknessTicks == 0) {
					removeArmsOnlyModifiers();
				}
			}
			else {
				removeArmsOnlyModifiers();
			}
		}
	}

	private void addArmsOnlyModifiers() {
		updateTransientModifier(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ARMS_ONLY, true);
		updateTransientModifier(ModEntityAttributes.STAND_DURABILITY, DURABILITY_ARMS_ONLY, true);
		updateTransientModifier(ModEntityAttributes.STAND_PRECISION, PRECISION_ARMS_ONLY, true);
	}
	
	private void removeArmsOnlyModifiers() {
		updateTransientModifier(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ARMS_ONLY, false);
		updateTransientModifier(ModEntityAttributes.STAND_DURABILITY, DURABILITY_ARMS_ONLY, false);
		updateTransientModifier(ModEntityAttributes.STAND_PRECISION, PRECISION_ARMS_ONLY, false);
	}
	
	private void resetIdleOffsetForArmsOnlyMode() {
		if (offsetFromUser != null && offsetFromUser.standAbility == null) {
			offsetFromUser.resetToIdle();
		}
	}

	public boolean wasSummonedAsArms() {
		return (entityData.get(ARMS_ONLY_MODE) & 2) != 0;
	}

	public boolean showArm(InteractionHand hand) {
		switch (hand) {
		case MAIN_HAND:
			return (entityData.get(ARMS_ONLY_MODE) & 4) != 0;
		case OFF_HAND:
			return (entityData.get(ARMS_ONLY_MODE) & 8) != 0;
		default:
			return false;
		}
	}

	public InteractionHand getPunchingHand() {
		if (usesSilverChariotRapierHand()) {
			return InteractionHand.MAIN_HAND;
		}
		return entityData.get(SWING_OFF_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	public InteractionHand alternateHands() {
		if (usesSilverChariotRapierHand()) {
			entityData.set(SWING_OFF_HAND, false);
			lastStandSwingTick = tickCount;
			alternateAdditionalSwing = false;
			swingingArm = InteractionHand.MAIN_HAND;
			return InteractionHand.MAIN_HAND;
		}
		InteractionHand hand = InteractionHand.MAIN_HAND;
		if (tickCount - lastStandSwingTick > 1) {
			boolean offHand = entityData.get(SWING_OFF_HAND);
			if (offHand) {
				hand = InteractionHand.OFF_HAND;
			}
			entityData.set(SWING_OFF_HAND, !offHand);
			lastStandSwingTick = tickCount;
			alternateAdditionalSwing = false;
		}
		else {
			if (alternateAdditionalSwing) {
				hand = InteractionHand.OFF_HAND;
			}
			alternateAdditionalSwing = !alternateAdditionalSwing;
		}
		swingingArm = hand;
		return hand;
	}

	public void fullSummonFromArms() {
		if (isArmsOnlyMode()) {
			entityData.set(ARMS_ONLY_MODE, (byte) 2);
			setStandFlag(StandFlag.ARMS_ONLY_MODE, false);
			EntityActionInstance curAction = getCurStandAction();
			if (curAction != null && curAction.ability == ModSpecialActions.STAND_UNSUMMON.get()) {
				stopRetraction();
			}
		}
	}
	
	
	public boolean isFollowingUser() {
		return !isManuallyControlled()
				&& followingUserIsEnabled()
				&& !isBeingRetracted()
				&& !curActionDisablesUserOffset();
	}

	private boolean isFollowingUserForRetractionDecision() {
		return !isManuallyControlled()
				&& followingUserIsEnabled()
				&& !isBeingRetracted();
	}
	
	private boolean curActionDisablesUserOffset() {
		EntityActionInstance action = getCurStandAction();
		if (action != null && action.ability instanceof StandEntityAbility standAbility) {
			LivingEntity user = getUser();
			StandPower standPower = user != null ? getUserPower() : null;
			if (standPower != null) {
				return standAbility.noAdheringToUserOffset(standPower, this);
			}
			return level().isClientSide() && standAbility.noAdheringToUserOffsetClientFallback(this);
		}
		return false;
	}

	public boolean canMoveManually() {
		EntityActionInstance action = getCurStandAction();
		if (action != null && action.ability instanceof StandEntityAbility standAbility) {
			LivingEntity user = getUser();
			StandPower standPower = user != null ? StandPower.get(user) : null;
			return standPower == null || !standAbility.lockStandManualMovement(standPower, this);
		}
		return true;
	}

	public ManualStandMovementLock getManualMovementLocks() {
		return manualMovementLocks;
	}
	
	public boolean isManuallyControlled() {
		// makes it smoother if you move as soon as you enter manual control, otherwise there is a little stumble
		if (level().isClientSide() && getUser() == ClientProxy.getClientPlayer()) {
			ClientEntityController ctrl = ClientEntityController.getInstance();
			return ctrl != null && ctrl.entity == this;
		}
		return getStandFlag(StandFlag.MANUAL_CONTROL);
	}
	
	public void setManuallyControlled(boolean value) {
		setStandFlag(StandFlag.MANUAL_CONTROL, value);
		if (level().isClientSide()) {
			setDeltaMovement(Vec3.ZERO);
		}

		if (!value && followingUserIsEnabled()) {
			retract();
		}
		else {
			setStandFlag(StandFlag.BEING_RETRACTED, false);
		}
		
		updateNoPhysics();
	}
	
	public void setCanFollowUser(boolean enabled) {
		setStandFlag(StandFlag.CAN_FOLLOW_USER, enabled);
	}
	
	public boolean followingUserIsEnabled() {
		return getStandFlag(StandFlag.CAN_FOLLOW_USER);
	}
	
	public boolean isBeingRetracted() {
		return getStandFlag(StandFlag.BEING_RETRACTED);
	}
	
	public void retract() {
		LivingEntity user = getUser();
		if (user != null) {
			setStandFlag(StandFlag.BEING_RETRACTED, true);
		}
	}

	public void retractAndUnsummon() {
		LivingEntity user = getUser();
		if (user != null) {
			if (!isFollowingUserForRetractionDecision()) {
				setStandFlag(StandFlag.BEING_RETRACTED, true);
			}
			if (!hasHeldAbilityInput(user)) {
				startStandUnsummon();
			}
		}
	}
	
	private boolean hasHeldAbilityInput(LivingEntity user) {
		EntityActionInputState inputState = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		return inputState != null && !inputState.heldKeys.isEmpty();
	}
	
	public boolean isCloseToUser() {
		LivingEntity user = getUser();
		return user != null ? distanceToSqr(user) < 4 : false;
	}
	
	public void onUnsummonUserInput() {
		if (!this.isBeingRetracted()) {
			this.retractAndUnsummon();
		}
		else if (this.isManuallyControlled()) {
			this.stopRetraction();
		}
	}

	public void stopRetraction() {
		setStandFlag(StandFlag.BEING_RETRACTED, false);
		
		EntityActionInstance curAction = getCurStandAction();
		if (curAction != null && curAction.ability == ModSpecialActions.STAND_UNSUMMON.get()) {
			standAction.setAction(null, SyncType.TRACKING_AND_SELF);
		}
	}

	protected void startStandUnsummon() {
		if (!level().isClientSide()) {
			var unsummonAction = new StandEntityUnsummonAction.StandUnsummonInstance();
			standAction.setAction(unsummonAction, getUser(), SyncType.TRACKING_AND_SELF);
		}
	}

	public void updateNoPhysics() {
		setNoPhysics(shouldHaveNoPhysics());
	}

	protected boolean shouldHaveNoPhysics() {
		return standCanHaveNoPhysics && !isManuallyControlled() && followingUserIsEnabled();
	}

	public void setNoPhysics(boolean noPhysics) {
		if (noPhysics || standCanHaveNoPhysics) {
			setStandFlag(StandFlag.NO_PHYSICS, noPhysics);
		}
	}
	
	
	public void multiplyTranslucency(float multiplier) {
		modelAlpha.set(modelAlpha.get() * multiplier, false);
	}

	private void tickSummonAlpha() {
		if (summonLockTicks > 0) {
			summonLockTicks--;
		}
		else if (gradualSummonWeaknessTicks > 0) {
			gradualSummonWeaknessTicks--;
			if (gradualSummonWeaknessTicks == 0 && !level().isClientSide()) {
				removeArmsOnlyModifiers();
			}
		}
		float summonAlpha = getSummonAlpha();
		if (summonAlpha < 1) {
			multiplyTranslucency(getSummonAlpha());
		}
	}

	private float getSummonAlpha() {
		int ticks = summonLockTicks > 0 ? summonLockTicks : gradualSummonWeaknessTicks;
		if (ticks > 0 && alphaTicks > 0) {
			return Mth.clamp((float) (alphaTicks - ticks) / (float) alphaTicks, 0F, 1F);
		}
		return 1F;
	}

	public void playStandSummonSound() {
		if (level().isClientSide() || isMagiciansRedStand() && isArmsOnlyMode()) {
			return;
		}
		StandType standType = StandType.fromId(standId);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new StandEntitySoundPacket(
				this, MultiSoundEventResolver.resolve(standType != null ? standType.getSummonSound() : ModSoundEvents.STAND_SUMMON), 1.0F, 1.0F));
	}

	public int getUnsummonDuration() {
		LivingEntity user = getUser();
		boolean resolve = user != null && ResolveModeEffect.getResolveEffectLvl(user) >= 0;
		if (resolve) {
			return isArmsOnlyMode() ? 3 : 5;
		}
		int ticks = isArmsOnlyMode() ? 7 : 10;
		double staminaDebuff = (staminaCondition * 2 + 1) / 3.0;
		if (staminaDebuff < 1) {
			ticks = Mth.ceil((double) ticks / staminaDebuff);
		}
		return ticks;
	}
	
	
	@Nullable
	public EntityActionInstance getCurStandAction() {
		return standAction.getAction();
	}
	
	@Nonnull
	public LivingComponentAction getStandActionComponent() {
		return standAction;
	}
	
	@Override
	public boolean onActionSet(@Nullable EntityActionInstance action) {
		EntityActionInstance curAction = getCurStandAction();
		if (!level().isClientSide() && curAction != null) {
			rollGuardCounterDamage();
		}
		if (action != null) {
			setNoPhysics(false);
		}
		else {
			if (!level().isClientSide() && actionRetractsStandAfterClear(curAction)) {
				if (isArmsOnlyMode()) {
					startStandUnsummon();
					return true;
				}
				if (getUser() != null && !isCloseToUser() && isFollowingUserForRetractionDecision()) {
					retract();
				}
			}
			updateNoPhysics();
			offsetFromUser.resetToIdle();
		}
		return false;
	}

	private boolean actionRetractsStandAfterClear(@Nullable EntityActionInstance oldAction) {
		return oldAction != null
				&& oldAction.ability instanceof StandEntityAbility standAbility
				&& standAbility.retractsStandAfterAction(getUserPower(), this, oldAction);
	}

	private void rollGuardCounterDamage() {
		if (blockDamage > 0) {
			prevBlockDamage += blockDamage;
			blockDamage = 0;
		}
		else {
			prevBlockDamage = 0;
		}
	}
	// Entity actions sync through LivingComponentAction and the client action sync queue.
	
	
	protected static final double DEFAULT_ATTACK_RANGE = 2.5D;
	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
			.add(Attributes.ATTACK_DAMAGE, 8)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.ATTACK_SPEED, 8)
			.add(Attributes.BLOCK_INTERACTION_RANGE, DEFAULT_ATTACK_RANGE)
			.add(Attributes.ENTITY_INTERACTION_RANGE, DEFAULT_ATTACK_RANGE)
			.add(ModEntityAttributes.STAND_EFFECTIVE_RANGE, 2)
			.add(ModEntityAttributes.STAND_MAX_RANGE, 4)
			.add(ModEntityAttributes.STAND_DURABILITY, 8)
			.add(ModEntityAttributes.STAND_PRECISION, 8)
			.add(Attributes.LUCK)
			.add(Attributes.BLOCK_BREAK_SPEED)
			.add(Attributes.SUBMERGED_MINING_SPEED)
			.add(Attributes.SNEAKING_SPEED)
			.add(Attributes.MINING_EFFICIENCY)
			.add(Attributes.SWEEPING_DAMAGE_RATIO);
	}

	public void initStandStatsValues(StandStats stats) {
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.power());
		getAttribute(Attributes.ATTACK_SPEED).setBaseValue(stats.speed());
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(StandStatFormulas.getMovementSpeed(stats.speed()));
		getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(stats.rangeEffective());
		getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(stats.rangeMax());
		getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(stats.durability());
		getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(stats.precision());
	}
	
	public static void updateStandStatAttributes(LivingEntity stand, @Nullable LivingEntity user) {
		if (user != null) {
			AttributeMap standAttributes = stand.getAttributes();
			AttributeMap userAttributes = user.getAttributes();
			if (userAttributes.hasAttribute(ModEntityAttributes.STAND_STRENGTH)) {
				double strength = userAttributes.getValue(ModEntityAttributes.STAND_STRENGTH);
				AttributeUtil.setBaseValue(standAttributes, Attributes.ATTACK_DAMAGE, strength);
			}
			if (userAttributes.hasAttribute(ModEntityAttributes.STAND_SPEED)) {
				double speed = userAttributes.getValue(ModEntityAttributes.STAND_SPEED);
				AttributeUtil.setBaseValue(standAttributes, Attributes.ATTACK_SPEED, speed);
				AttributeUtil.setBaseValue(standAttributes, Attributes.MOVEMENT_SPEED, StandStatFormulas.getMovementSpeed(speed));
			}
			if (userAttributes.hasAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE)) {
				double effectiveRange = userAttributes.getValue(ModEntityAttributes.STAND_EFFECTIVE_RANGE);
				AttributeUtil.setBaseValue(standAttributes, ModEntityAttributes.STAND_EFFECTIVE_RANGE, effectiveRange);
			}
			if (userAttributes.hasAttribute(ModEntityAttributes.STAND_MAX_RANGE)) {
				double maxRange = userAttributes.getValue(ModEntityAttributes.STAND_MAX_RANGE);
				AttributeUtil.setBaseValue(standAttributes, ModEntityAttributes.STAND_MAX_RANGE, maxRange);
			}
			if (userAttributes.hasAttribute(ModEntityAttributes.STAND_DURABILITY)) {
				double durability = userAttributes.getValue(ModEntityAttributes.STAND_DURABILITY);
				AttributeUtil.setBaseValue(standAttributes, ModEntityAttributes.STAND_DURABILITY, durability);
			}
			if (userAttributes.hasAttribute(ModEntityAttributes.STAND_PRECISION)) {
				double precision = userAttributes.getValue(ModEntityAttributes.STAND_PRECISION);
				AttributeUtil.setBaseValue(standAttributes, ModEntityAttributes.STAND_PRECISION, precision);
			}
		}
	}

	public double getAttackDamage() {
		double damage = getAttributeValue(Attributes.ATTACK_DAMAGE);
		return damage * getStandEfficiency();
	}

	public double getAttackSpeed() {
		double speed = getAttributeValue(Attributes.ATTACK_SPEED);
		return speed * getStandEfficiency();
	}

	public boolean canAttackMelee() {
		return getAttackSpeed() > 0
				&& getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) > 0
				&& getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) > 0;
	}

	public double getAttackKnockback() {
		double damage = getAttributeValue(Attributes.ATTACK_KNOCKBACK);
		return damage * getStandEfficiency();
	}

	public double getDurability() {
		double durability = getAttributeValue(ModEntityAttributes.STAND_DURABILITY);
//		if (ModPowers.VAMPIRISM.get().isHighOnBlood(getUser())) {
//			durability *= 2;
//		}
		return durability * getStandEfficiency();
	}

	public double getPrecision() {
		double precision = getAttributeValue(ModEntityAttributes.STAND_PRECISION);
		return precision * getStandEfficiency();
	}
	
	public double getEffectiveRange() {
		return getAttributeValue(ModEntityAttributes.STAND_EFFECTIVE_RANGE);
	}
	
	public double getMaxRange() {
		return getAttributeValue(ModEntityAttributes.STAND_MAX_RANGE);
	}
	
	public double getStandEfficiency() {
		return rangeEfficiency * staminaCondition;
	}

	public float getLeapStrength() {
		return StandStatFormulas.getLeapStrength(leapBaseStrength() * getStandEfficiency());
	}

	protected double leapBaseStrength() {
		return getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	public void updateStrengthMultipliers() {
		LivingEntity user = getUser();

		rangeEfficiency = user != null ? StandStatFormulas.rangeStrengthFactor(distanceStrengthDecayEnabled,
				getEffectiveRange(), getMaxRange(),
				MathUtil.getAABBDistance(this.getBoundingBox(), user.getBoundingBox())) : 1;

		if (user != null && userPower != null) {
			staminaCondition = StandUtil.staminaCondition(userPower);
		}
	}
	
	
	public boolean isArmsOnlyMode() {
		return (entityData.get(ARMS_ONLY_MODE) & 1) != 0;
	}

	private void syncSilverChariotState(LivingEntity user) {
		if (isSilverChariotStand()) {
			SilverChariotState state = SilverChariotState.get(user);
			if (state != null) {
				boolean hasArmor = state.hasArmor();
				boolean hasRapier = state.hasRapier();
				applySilverChariotStateFlagsAndAttributes(hasArmor, hasRapier);
				if (!hasArmor) {
					state.incrementTicksAfterArmorRemoval();
				}
				else {
					state.resetTicksAfterArmorRemoval();
				}
				if (!hasRapier) {
					clearSilverChariotRapierFire();
					recoverSilverChariotRapier();
				}
				else if (silverChariotRapierFireTicks > 0 && --silverChariotRapierFireTicks == 0) {
					setSilverChariotRapierOnFire(false);
				}
			}
		}
	}

	public void refreshSilverChariotStateAfterMutation(LivingEntity user) {
		if (!isSilverChariotStand()) {
			return;
		}
		SilverChariotState state = SilverChariotState.get(user);
		if (state == null) {
			return;
		}
		applySilverChariotStateFlagsAndAttributes(state.hasArmor(), state.hasRapier());
	}

	private void applySilverChariotStateFlagsAndAttributes(boolean hasArmor, boolean hasRapier) {
		setSilverChariotArmorVisible(hasArmor);
		setSilverChariotRapierVisible(hasRapier);
		updateSilverChariotAttributeModifiers(hasArmor, hasRapier);
		if (!hasRapier) {
			clearSilverChariotRapierFire();
		}
	}

	private void recoverSilverChariotRapier() {
		for (SCRapierEntity rapier : level().getEntitiesOfClass(SCRapierEntity.class, getBoundingBox(), Entity::isAlive)) {
			rapier.takeRapier(this);
			if (rapier.isRemoved()) {
				break;
			}
		}
	}

	private void updateSilverChariotAttributeModifiers(boolean hasArmor, boolean hasRapier) {
		updateTransientModifier(Attributes.MOVEMENT_SPEED, SC_NO_ARMOR_MOVEMENT_SPEED_BOOST, !hasArmor);
		updateTransientModifier(Attributes.ATTACK_SPEED, SC_NO_ARMOR_ATTACK_SPEED_BOOST, !hasArmor);
		updateTransientModifier(Attributes.ARMOR, SC_NO_ARMOR, !hasArmor);
		updateTransientModifier(Attributes.ARMOR_TOUGHNESS, SC_NO_ARMOR_TOUGHNESS, !hasArmor);
		updateTransientModifier(ModEntityAttributes.STAND_DURABILITY, SC_NO_ARMOR_DURABILITY_DECREASE, !hasArmor);
		updateTransientModifier(Attributes.ATTACK_DAMAGE, SC_NO_RAPIER_DAMAGE_DECREASE, !hasRapier);
		updateTransientModifier(Attributes.ATTACK_SPEED, SC_NO_RAPIER_ATTACK_SPEED_DECREASE, !hasRapier);
		updateTransientModifier(Attributes.BLOCK_INTERACTION_RANGE, SC_NO_RAPIER_BLOCK_RANGE_DECREASE, !hasRapier);
		updateTransientModifier(Attributes.ENTITY_INTERACTION_RANGE, SC_NO_RAPIER_ENTITY_RANGE_DECREASE, !hasRapier);
	}

	private void updateTransientModifier(Holder<Attribute> attribute, AttributeModifier modifier, boolean add) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifier.id());
		if (add) {
			instance.addTransientModifier(modifier);
		}
	}

	public void setSilverChariotArmorVisible(boolean visible) {
		setStandFlag(StandFlag.SILVER_CHARIOT_ARMOR_VISIBLE, visible);
	}

	public boolean isSilverChariotArmorVisible() {
		return getStandFlag(StandFlag.SILVER_CHARIOT_ARMOR_VISIBLE);
	}

	public void setSilverChariotRapierVisible(boolean visible) {
		setStandFlag(StandFlag.SILVER_CHARIOT_RAPIER_VISIBLE, visible);
	}

	public boolean isSilverChariotRapierVisible() {
		return getStandFlag(StandFlag.SILVER_CHARIOT_RAPIER_VISIBLE);
	}

	public boolean isSilverChariotRapierOnFire() {
		return getStandFlag(StandFlag.SILVER_CHARIOT_RAPIER_ON_FIRE);
	}

	private void setSilverChariotRapierOnFire(boolean onFire) {
		setStandFlag(StandFlag.SILVER_CHARIOT_RAPIER_ON_FIRE, onFire);
	}

	private void clearSilverChariotRapierFire() {
		silverChariotRapierFireTicks = 0;
		setSilverChariotRapierOnFire(false);
	}

	public void removeSilverChariotRapierFire() {
		clearSilverChariotRapierFire();
	}

	public boolean deflectSilverChariotTargetProjectile(@Nullable Entity target) {
		if (!canDeflectSilverChariotProjectiles() || !(target instanceof Projectile projectile)) {
			return false;
		}
		LivingEntity user = getUser();
		Entity owner = projectile.getOwner();
		if (owner != null && user != null && owner.is(user)) {
			return false;
		}
		return deflectSilverChariotProjectile(projectile);
	}

	public void deflectSilverChariotNearbyProjectiles() {
		if (!canDeflectSilverChariotProjectiles()) {
			return;
		}
		double reach = getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		double minDot = Mth.cos((float) ((30.0 + Mth.clamp(getPrecision(), 0, 16) * 30.0 / 16.0) * MathUtil.DEG_TO_RAD));
		Vec3 lookVec = getLookAngle().normalize();
		for (Projectile projectile : level().getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(reach),
				entity -> entity.isAlive() && !entity.isPickable())) {
			Vec3 reverseMotion = projectile.getDeltaMovement().reverse();
			if (reverseMotion.lengthSqr() > 1.0E-7 && lookVec.dot(reverseMotion.normalize()) >= minDot) {
				deflectSilverChariotProjectile(projectile);
			}
		}
	}

	private boolean canDeflectSilverChariotProjectiles() {
		if (!isSilverChariotStand() || !isSilverChariotRapierVisible()) {
			return false;
		}
		LivingEntity user = getUser();
		return userPower == null || user != null && ResolveModeEffect.getEffectiveResolveLevel(user, userPower) >= 4;
	}

	private boolean deflectSilverChariotProjectile(Projectile projectile) {
		if (projectile instanceof ModdedProjectileEntity moddedProjectile && !moddedProjectile.canBeDeflected(this)) {
			return false;
		}
		Vec3 deflectedPos = projectile.position();
		Vec3 deflectVec = getLookAngle().normalize().scale(projectile.getDeltaMovement().length());
		projectile.setDeltaMovement(deflectVec);
		projectile.move(MoverType.SELF, projectile.getDeltaMovement());
		projectile.hasImpulse = true;
		if (projectile instanceof ModdedProjectileEntity moddedProjectile) {
			moddedProjectile.setIsDeflected(projectile.getDeltaMovement(), deflectedPos);
		}
		if (projectile instanceof DamagingEntity damagingProjectile && damagingProjectile.isFiery()) {
			setSilverChariotRapierOnFire(true);
			silverChariotRapierFireTicks = SILVER_CHARIOT_RAPIER_FIRE_TICKS;
		}
		return true;
	}

	public boolean hurtWithStandAttack(Entity target, DamageSource dmgSource, float dmgAmount) {
		if (target instanceof LivingEntity targetLiving) {
			float finalDamage = applyStandInvulDamage(targetLiving, dmgAmount);
			if (finalDamage <= 0) {
				return false;
			}
			boolean hurt;
			if (isMagiciansRedStand()) {
				hurt = DamageUtil.dealDamageAndSetOnFire(target, entity -> DamageUtil.hurtThroughInvulTicks(entity, dmgSource, finalDamage), MAGICIANS_RED_STAND_ATTACK_FIRE_TICKS, true);
			}
			else if (isSilverChariotStand() && isSilverChariotRapierVisible() && isSilverChariotRapierOnFire()) {
				hurt = DamageUtil.dealDamageAndSetOnFire(target, entity -> DamageUtil.hurtThroughInvulTicks(entity, dmgSource, finalDamage), 80, true);
			}
			else {
				hurt = DamageUtil.hurtThroughInvulTicks(target, dmgSource, finalDamage);
			}
			if (hurt) {
				setStandInvulAfterHit(targetLiving, dmgSource, finalDamage);
			}
			return hurt;
		}
		if (isMagiciansRedStand()) {
			return DamageUtil.dealDamageAndSetOnFire(target, entity -> DamageUtil.hurtThroughInvulTicks(entity, dmgSource, dmgAmount), MAGICIANS_RED_STAND_ATTACK_FIRE_TICKS, true);
		}
		if (isSilverChariotStand() && isSilverChariotRapierVisible() && isSilverChariotRapierOnFire()) {
			return DamageUtil.dealDamageAndSetOnFire(target, entity -> DamageUtil.hurtThroughInvulTicks(entity, dmgSource, dmgAmount), 80, true);
		}
		return DamageUtil.hurtThroughInvulTicks(target, dmgSource, dmgAmount);
	}

	public float getBlockHardnessForStandBreak(BlockState blockState, Level level, BlockPos blockPos) {
		if (isSilverChariotStand() && isSilverChariotRapierVisible()
				&& !canSilverChariotRapierBreakBlock(blockState, level, blockPos)) {
			return -1.0F;
		}
		return StandStatFormulas.getBlockHardness(getAttackDamage(), blockState, level, blockPos);
	}

	private static boolean canSilverChariotRapierBreakBlock(BlockState blockState, Level level, BlockPos blockPos) {
		float blockHardness = getSilverChariotRapierBreakHardness(blockState, level, blockPos);
		return blockHardness >= 0 && blockHardness <= 1.0F && SILVER_CHARIOT_RAPIER_HARVEST_TIER.canMine(blockState);
	}

	private static float getSilverChariotRapierBreakHardness(BlockState blockState, Level level, BlockPos blockPos) {
		float hardness = blockState.getDestroySpeed(level, blockPos);
		if (!blockState.requiresCorrectToolForDrops()) {
			hardness *= 0.6F;
		}
		return hardness;
	}

	private static float applyStandInvulDamage(LivingEntity target, float damage) {
		StandAttackInvulState state = STAND_ATTACK_INVUL.get(target);
		if (state != null && state.expiresAtTick > target.tickCount) {
			state.lastStandDamage = damage;
			return Math.max(damage - state.lastStandDamage, 0);
		}
		return damage;
	}

	private static void setStandInvulAfterHit(LivingEntity target, DamageSource dmgSource, float damage) {
		if (dmgSource instanceof DamageSourceModified) {
			int standInvulTicks = ((DamageSourceModified) dmgSource).jojo_ripples$standInvulTicks();
			if (standInvulTicks > 0) {
				StandAttackInvulState state = STAND_ATTACK_INVUL.computeIfAbsent(target, ignored -> new StandAttackInvulState());
				state.lastStandDamage = damage;
				state.expiresAtTick = target.tickCount + standInvulTicks;
			}
		}
	}

	private boolean isSilverChariotStand() {
		return standId != null && SILVER_CHARIOT_ID.equals(standId);
	}

	private boolean usesSilverChariotRapierHand() {
		return isSilverChariotStand() && isSilverChariotRapierVisible();
	}

	private boolean isTheWorldStand() {
		return standId != null && THE_WORLD_ID.equals(standId);
	}

	private boolean isMagiciansRedStand() {
		return standId != null && MAGICIANS_RED_ID.equals(standId);
	}

	public void onKnivesThrow(Level level, Player playerUser, ItemStack knivesStack, int knivesThrown) {
		if (level.isClientSide()) {
			return;
		}
		if (!isTheWorldStand()) {
			return;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		StandPower power = getUserPower();
		if (power == null) {
			return;
		}
		JojoModLivingVariables vars = JojoModLivingVariables.get(playerUser);

		if (knivesThrown == 1 && vars.knivesThrewTicks > 0) {
			JojoModUtil.sayVoiceLine(playerUser, ModSoundEvents.DIO_ONE_MORE);
			vars.knivesThrewTicks = 0;
		}

		TimeStopState timeStopState = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
		if (knivesThrown > 1 && timeStopState.isTimeStopped(playerUser)) {
			vars.knivesThrewTicks = 80;
		}

		if (playerUser.isShiftKeyDown() || knivesStack.getCount() <= 0) {
			return;
		}

		int standKnives = Math.min(knivesStack.getCount(), KnifeItem.MAX_KNIVES_THROW);
		for (int i = 0; i < standKnives; i++) {
			ItemStack projectileStack = knivesStack.copy();
			projectileStack.setCount(1);
			KnifeEntity knife = new KnifeEntity(level, playerUser, projectileStack);
			knife.setPos(this.getX(), this.getEyeY() - 0.1, this.getZ());
			knife.setTimeStopFlightTicks(5);
			knife.shootFromRotation(playerUser, playerUser.getXRot(), playerUser.getYRot(), 0.0F, 1.5F, i == 0 ? 1.0F : 16.0F);
			level.addFreshEntity(knife);
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				standKnives == 1 ? ModSoundEvents.KNIFE_THROW.get() : ModSoundEvents.KNIVES_THROW.get(),
				SoundSource.PLAYERS, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));

		if (!playerUser.getAbilities().instabuild) {
			knivesStack.shrink(standKnives);
		}

		timeStopState.getInstance(playerUser.getId())
				.filter(instance -> instance.totalTicks() == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS)
				.filter(instance -> instance.ticksLeft() > 50 && instance.ticksLeft() <= 80)
				.ifPresent(instance -> JojoModUtil.sayVoiceLine(playerUser, ModSoundEvents.DIO_5_SECONDS));
	}

	
	@Override
	public ResourceLocation getStandType() {
		return standId;
	}

	public float getUserWalkSpeed(float baseWalkSpeed) {
		if (isSilverChariotStand() && getUserPower() != null) {
			LivingEntity user = getUser();
			if (user != null && ResolveModeEffect.getEffectiveResolveLevel(user, getUserPower()) >= 4) {
				SilverChariotState state = SilverChariotState.get(user);
				boolean hasArmor = state == null || state.hasArmor();
				return baseWalkSpeed + (1.0F - baseWalkSpeed) * (hasArmor ? 0.5F : 1.0F);
			}
		}
		return baseWalkSpeed;
	}

	protected Optional<ResourceLocation> standSkin = Optional.empty();
	@Override
	public void setSelectedSkin(Optional<ResourceLocation> standSkin) {
		this.standSkin = standSkin;
	}
	
	@Override
	public Optional<ResourceLocation> getStandSkin() {
		return standSkin;
	}
	
	
	public boolean onlyVisibleToStandUsers = true;
	public boolean standCanHaveNoPhysics = true;
	public boolean standHasNoGravity = true;
	public boolean canOnlyHurtFromStands = true;
	public boolean healthLinkedWithUser = true;
	public void setIsPhysicalObject() {
		onlyVisibleToStandUsers = false;
		standCanHaveNoPhysics = false;
		standHasNoGravity = false;
		canOnlyHurtFromStands = false;
		healthLinkedWithUser = false;
	}
	
	@Override
	public boolean isInvisible() {
		return clientCantSeeThisStand() || underInvisibilityEffect();
	}

	public boolean underInvisibilityEffect() {
		return super.isInvisible();
	}
	
	@Override
	public boolean isInvisibleTo(Player player) {
		return isInvisibleToStandViewer(player, underInvisibilityEffect());
	}

	@Override
	public boolean displayFireAnimation() {
		if (noFireAnimFrame) {
			noFireAnimFrame = false;
			return false;
		}
		return !clientCantSeeThisStand() && super.displayFireAnimation();
	}

	public void setNoFireAnimFrame() {
		this.noFireAnimFrame = true;
	}
	
	@Override
	public boolean onlyVisibleToStandUsers() {
		return onlyVisibleToStandUsers;
	}
	
	public final boolean isVisibleForAll() {
		return !onlyVisibleToStandUsers();
	}
	
	@Override
	public boolean canBeSeenByAnyone() {
		return isVisibleForAll() && super.canBeSeenByAnyone();
	}
	
	
	@Override
	public void push(Entity entity) {}
	
	@Override
	public boolean isPushable() { return false; }
	
	@Override
	public void pushEntities() {}

	@Override
	public boolean isPickable() {
		if (level().isClientSide()) {
			Player clientPlayer = ClientProxy.getClientPlayer();
			if (clientPlayer != null && this.is(ClientGlobals.playerStandEntity)) {
				return false;
			}
		}
		return super.isPickable();
	}
	
	
	public boolean requiresUser() {
		return healthLinkedWithUser;
	}
	
	@Override
	public boolean isInvulnerableTo(/*ServerLevel level, */DamageSource damageSource) {
		LivingEntity user = getUser();
		return user != null && (
					user.isInvulnerableTo(/*level, */damageSource)
					|| user instanceof Player player && player.getAbilities().invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
				|| canOnlyHurtFromStands && !DamageUtil.canHurtStands(damageSource)
				|| super.isInvulnerableTo(/*level, */damageSource);
	}

	@Override
	public boolean hurt(DamageSource dmgSource, float dmgAmount) {
		if (!level().isClientSide() && dmgSource instanceof DamageSourceModified modified) {
			dmgAmount = barrageClashParryPunches(dmgSource, modified, dmgAmount);
			if (dmgAmount <= 0) {
				return false;
			}
		}
		return super.hurt(dmgSource, dmgAmount);
	}

	protected float barrageClashParryPunches(DamageSource dmgSource, DamageSourceModified modified, float dmgAmount) {
		if (barrageParryCount <= 0 || isDeadOrDying() || !canBlockDamage(dmgSource) || !canBlockFromAngle(dmgSource.getSourcePosition())) {
			return dmgAmount;
		}
		int punchesIncoming = modified.jojo_ripples$barrageHitsCount();
		if (punchesIncoming <= 0) {
			return dmgAmount;
		}
		float parriableProportion = Math.min(StandStatFormulas.getMaxBarrageParryTickDamage(getDurability()) / dmgAmount, 1);
		int punchesCanParry = Mth.floor(parriableProportion * barrageParryCount);
		if (punchesCanParry <= 0) {
			return dmgAmount;
		}

		Vec3 attackPos = getEyePosition();
		Entity attacker = dmgSource.getDirectEntity();
		if (attacker != null) {
			attackPos = attackPos.scale(0.5).add(attacker.getEyePosition().scale(0.5));
		}
		else {
			attackPos = attackPos.add(getLookAngle());
		}
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.CRIT,
					attackPos.x, attackPos.y, attackPos.z,
					1, 0.5D, 0.25D, 0.5D, 0.2D);
		}

		punchesCanParry = Math.min(punchesCanParry, punchesIncoming);
		modified.jojo_ripples$setBarrageHitsCount(punchesIncoming - punchesCanParry);
		barrageParryCount -= punchesCanParry;
		addFinisherMeter(0.0125F, FINISHER_NO_DECAY_TICKS);
		setBarrageClashOpponent(attacker);

		StandPower attackerStand = modified.jojo_ripples$standPower();
		if (punchesCanParry == punchesIncoming) {
			ResolveCounter.addResolve(attackerStand, this, dmgAmount);
			return 0;
		}
		float damageParried = dmgAmount * (float) punchesCanParry / (float) punchesIncoming;
		ResolveCounter.addResolve(attackerStand, this, damageParried);
		return dmgAmount - damageParried;
	}
	
	@Override
	protected float getDamageAfterMagicAbsorb(DamageSource dmgSource, float dmgAmount) {
		boolean blockableAngle = canBlockFromAngle(dmgSource.getSourcePosition());
		tryAutoBlock(dmgSource, blockableAngle);
		boolean isBlocking = isStandBlocking() && blockableAngle;
		dmgAmount = super.getDamageAfterMagicAbsorb(dmgSource, dmgAmount);
		dmgAmount = standDamageResistance(dmgSource, dmgAmount, isBlocking);
		this.damageContainers.peek().setNewDamage(dmgAmount);
		return dmgAmount;
	}

	private boolean tryAutoBlock(DamageSource dmgSource, boolean blockableAngle) {
		LivingEntity user = getUser();
		if (level().isClientSide() || isManuallyControlled() || !blockableAngle || getCurStandAction() != null
				|| !canStartBlocking() || !canBlockDamage(dmgSource) || user == null) {
			return false;
		}
		if (userPower == null) {
			userPower = StandPower.get(user);
		}
		if (userPower == null || !userPower.isAbilityUnlocked("guard")) {
			return false;
		}
		Ability guard = userPower.getAbility("guard");
		if (guard instanceof EntityActionType guardActionType) {
			EntityActionInstance action = guardActionType.createActionObj();
			guardActionType.initActionFromConfig(action, level(), user, this);
			action.phasesLength.put(ActionPhase.PERFORM, 5F);
			action.setStartingPhase();
			standAction.setAction(action, user, SyncType.TRACKING_AND_SELF);
			return true;
		}
		return false;
	}

	protected float standDamageResistance(DamageSource dmgSource, float dmgAmount, boolean isBlocking) {
		wasDamageBlocked = false;
		if (canBlockDamage(dmgSource)) {
			float blockedRatio = 0;
			if (isBlocking && userPower != null) {
				blockedRatio = 1F;
				if (userPower.usesStamina()) {
					float staminaCost = StandStatFormulas.getGuardStaminaCost(dmgAmount);
					float stamina = userPower.getStamina();
					if (!userPower.consumeStamina(staminaCost)) {
						blockedRatio = staminaCost > 0 ? Mth.clamp(stamina / staminaCost, 0, 1) : 1F;
						standCrash();
					}
				}
			}
//			Float multiplier = getCurrentTask().map(task -> task.getAction()
//					.getDamageBlockMultiplier(userPower, this, task)).orElse(null);
//			if (multiplier != null && multiplier != 0) {
//				blockedRatio += (1 - blockedRatio) * multiplier;
//			}
			if (blockedRatio >= 1) {
				wasDamageBlocked = true;
//				if (dmgSource.getEntity() instanceof StandEntity) {
//					((StandEntity) dmgSource.getEntity()).playPunchSound = false;
//				}
			}
			float standResistanceDamage = dmgAmount * (1 - getPhysicalResistance(blockedRatio, dmgAmount));
			LivingEntity user = getUser();
			float finalDamage;
			if (user != null) {
				float hypotheticalUserDamage = DamageUtil.damageEntityWillTake(user, dmgSource, 
						damageContainers.peek().getOriginalDamage(), true).getNewDamage();
				finalDamage = Math.min(standResistanceDamage, hypotheticalUserDamage);
			}
			else {
				finalDamage = standResistanceDamage;
			}
			if (wasDamageBlocked) {
				blockDamage += finalDamage;
			}
			return finalDamage;
		}
		return dmgAmount;
	}

	public boolean canBlockDamage(DamageSource dmgSource) {
		return dmgSource.getDirectEntity() != null && !dmgSource.is(DamageTypeTags.BYPASSES_ARMOR) && !ModStatusEffects.isStunned(this);
	}

	protected float getPhysicalResistance(float blockedRatio, float damageDealt) {
		if (isSilverChariotStand()) {
			return StandStatFormulas.getPhysicalResistance(0, 0, blockedRatio, damageDealt);
		}
		return StandStatFormulas.getPhysicalResistance(getDurability(), getAttackDamage(), blockedRatio, damageDealt);
	}
	
	@Override
	public void setHealth(float health) {
		if (healthLinkedWithUser) {
			redirectDamageToUser(health);
		}
		super.setHealth(health);
	}
	
	protected void redirectDamageToUser(float newHealthValue) {
		if (level() instanceof ServerLevel level) {
			LivingEntity user = getUser();
			if (user != null) {
				DamageContainer currentlyTakingDamage = !damageContainers.empty() ? damageContainers.peek() : null;
				if (currentlyTakingDamage != null && this.getHealth() - currentlyTakingDamage.getNewDamage() == newHealthValue) { // this means it is *very* likely being called in LivingEntity#actuallyHurt
					user.hurt(new StandLinkDamageSource(level, this, currentlyTakingDamage.getSource()), currentlyTakingDamage.getNewDamage());
				}
				else {
					user.setHealth(newHealthValue);
				}
			}
		}
	}

	@Override
	public void knockback(double strength, double xRatio, double zRatio) {
		LivingKnockBackEvent event = CommonHooks.onLivingKnockBack(this, (float) strength, xRatio, zRatio);
		if (event.isCanceled()) return;
		
		DamageContainer curDamage = !damageContainers.isEmpty() ? damageContainers.peek() : null;
		strength = event.getStrength();
		xRatio = event.getRatioX();
		zRatio = event.getRatioZ();
		strength *= 1.0F - (float) getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		if (isStandBlocking() && canBlockFromAngle(position().add(new Vec3(xRatio, 0, zRatio)))) {
			double durabilityStat = getDurability();
			strength *= StandStatFormulas.getBlockingKnockbackMult(durabilityStat);
		}

		if (strength > 0) {
			hasImpulse = true;
			Vec3 motionVec = getDeltaMovement();
			Vec3 knockbackVec = new Vec3(xRatio, 0, zRatio).normalize().scale(strength);
			setDeltaMovement(
					motionVec.x / 2 - knockbackVec.x, 
					this.onGround() ? Math.min(0.4, motionVec.y / 2 + strength) : motionVec.y, 
					motionVec.z / 2 - knockbackVec.z);
			DamageSourceModified.afterKnockbackApplied(this, curDamage != null ? curDamage.getSource() : null);
		}

		if (healthLinkedWithUser) {
			LivingEntity user = getUser();
			if (user != null && user.isAlive()) {
				user.knockback(strength, xRatio, zRatio);
				DamageSourceModified.afterKnockbackApplied(user, curDamage != null ? curDamage.getSource() : null);
				user.hurtMarked = true;
			}
		}
	}

	protected void tickHealth(LivingEntity user) {
		if (healthLinkedWithUser) {
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(user.getMaxHealth());
			super.setHealth(user.isAlive() ? user.getHealth() : 0);
			deathTime = user.deathTime;
		}
	}
	
	public boolean isCurrentAttackBlocked() {
		return wasDamageBlocked;
	}

	public float guardCounter() {
		return Math.min((isStandBlocking() ? blockDamage : prevBlockDamage) / 5F, 1F);
	}

	public void setBarrageHitsThisTick(int hits) {
		this.barrageHits = Math.max(hits, 0);
		addBarrageParryCount(hits);
	}

	private void addBarrageParryCount(int hits) {
		if (!barrageParryAccumulating) {
			barrageParryAccumulating = true;
			barrageParryCount = hits + 1;
		}
		else {
			barrageParryCount += hits;
		}
	}

	public void resetBarrageParry() {
		barrageHits = 0;
		barrageParryAccumulating = false;
		barrageParryCount = 0;
	}

	public Optional<Entity> barrageClashOpponent() {
		return barrageClashOpponent;
	}

	public void barrageClashStopped() {
		setBarrageClashOpponent(null);
	}

	private void setBarrageClashOpponent(@Nullable Entity opponent) {
		Entity prevOpponent = barrageClashOpponent().orElse(null);
		barrageClashOpponent = Optional.ofNullable(opponent);
		if (!level().isClientSide()) {
			entityData.set(BARRAGE_CLASH_OPPONENT_ID, opponent != null ? opponent.getId() : -1);
		}

		if (prevOpponent instanceof StandEntity prevStandOpponent && prevOpponent != opponent) {
			if (opponent == null) {
				if (prevStandOpponent.barrageClashOpponent().orElse(null) == this) {
					prevStandOpponent.setBarrageClashOpponent(null);
				}
			}
			else if (prevStandOpponent.barrageClashOpponent().orElse(null) != this) {
				prevStandOpponent.setBarrageClashOpponent(this);
			}
		}
		if (opponent instanceof StandEntity standOpponent
				&& standOpponent.barrageClashOpponent().orElse(null) != this) {
			standOpponent.setBarrageClashOpponent(this);
		}
	}

	private boolean barrageClashOpponentOutOfReach() {
		Entity opponent = barrageClashOpponent().orElse(null);
		if (opponent == null) {
			return false;
		}
		if (!opponent.isAlive()) {
			return true;
		}
		return MathUtil.getAABBDistanceDetailed(getBoundingBox(), opponent.getBoundingBox()).distance() > getMaxRange();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		if (wasDamageBlocked) {
			return getStandBlockSound(damageSource);
		}
		return super.getHurtSound(damageSource);
	}

	protected SoundEvent getStandBlockSound(DamageSource damageSource) {
		if (isSilverChariotStand()) {
			LivingEntity user = getUser();
			if (user != null) {
				SilverChariotState state = SilverChariotState.get(user);
				if (state != null && state.hasRapier()) {
					return ModSoundEvents.SILVER_CHARIOT_BLOCK.get();
				}
			}
		}
		return ModSoundEvents.STAND_DAMAGE_BLOCK.get();
	}
	
	public boolean isStandBlocking() {
		EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(this);
		return curAction != null 
				&& curAction.getPhase() == ActionPhase.PERFORM
				&& curAction.ability.getAbilityId() != null
				&& curAction.ability.getAbilityId().nameInMoveset().equals("guard");
	}

	public boolean canStartBlocking() {
		return canBlockOrParryNow() && entityData.get(NO_BLOCKING_TICKS) <= 0;
	}

	public void breakStandBlocking(int lockTicks) {
		if (!level().isClientSide() && isStandBlocking()) {
			entityData.set(NO_BLOCKING_TICKS, lockTicks);
			standAction.setAction(null, SyncType.TRACKING_AND_SELF);
			playSound(ModSoundEvents.STAND_PARRY.get(), 1.0F, 1.0F);
		}
	}
	
	public void standCrash() {
		if (!level().isClientSide()) {
			standAction.setAction(null, SyncType.TRACKING_AND_SELF);
			addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
		}
	}

	public boolean canBlockFromAngle(Vec3 dmgPosition) {
		if (!canBlockOrParryNow()) {
			return false;
		}
		if (dmgPosition == null) {
			return false;
		}
		Vec3 viewVec = getViewVector(1.0F);
		Vec3 diffVec = dmgPosition.subtract(position()).normalize();
		return diffVec.dot(viewVec) > 0.5;
	}

	private boolean canBlockOrParryNow() {
		if (level() instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			TimeStopState timeStop = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			if (timeStop.shouldFreeze(this)) {
				return false;
			}
		}
		return true;
	}


	@Deprecated
	@Override
	public boolean canAttack(LivingEntity entity) {
		if (entity.is(this)) return false;

		LivingEntity user = getUser();
		if (user != null) {
			boolean canHarm = DamageUtil.isNotFriendlyFire(user, StandUtil.getStandUser(entity));
			if (canHarm && entity instanceof Animal) {
				canHarm &= !entity.isPassengerOfSameVehicle(user);
				if (canHarm && entity instanceof TamableAnimal tameable) {
					LivingEntity tameableOwner = tameable.getOwner();
					canHarm &= !(tameableOwner != null && tameableOwner == user);
				}
			}
			return canHarm;
		}

		return true;
	}

	public boolean canAttackEntity(Entity target) {
		if (!target.isAlive()) {
			return false;
		}
		if (target instanceof LivingEntity living) {
			return canAttack(living);
		}
		LivingEntity user = getUser();
		if (target instanceof Projectile projectile) {
			Entity owner = projectile.getOwner();
			if (owner != null && (owner.is(this) || owner.is(user))) {
				return target instanceof DamagingEntity modProjectile && modProjectile.canHitOwner();
			}
		}
		if (user != null && target.getControllingPassenger() == user) {
			return false;
		}
		return true;
	}
	
	
	protected NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
	public EntityHandItemsAsInventory<StandEntity> handsPseudoInventory = new EntityHandItemsAsInventory<>(this, handItems) {
		@Override
		public boolean stillValid(Player player) {
			return super.stillValid(player) && player.is(entity.getUser());
		}
	};
	
	protected void openStandHandsContainer() {
		if (!level().isClientSide()) {
			LivingEntity user = getUser();
			if (user instanceof ServerPlayer pl) {
				PlayerExternalContainers.get(pl).openMenu(StandHandsContainerMenu.createServerSide(this), null);
			}
		}
	}
	
	@Override
	public Iterable<ItemStack> getHandSlots() {
		return this.handItems;
	}
	
	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return Collections.emptyList();
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return switch (slot.getType()) {
			case HAND -> this.handItems.get(slot.getIndex());
			default -> ItemStack.EMPTY;
		};
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
		this.verifyEquippedItem(stack);
		switch (slot.getType()) {
			case HAND -> {
				this.onEquipItem(slot, this.handItems.set(slot.getIndex(), stack), stack);
			}
			default -> {}
		}
	}

	@Override
	public HumanoidArm getMainArm() {
		if (isSilverChariotStand()) {
			return HumanoidArm.RIGHT;
		}
		LivingEntity user = getUser();
		if (user != null) {
			return user.getMainArm();
		}
		return HumanoidArm.RIGHT;
	}
	
	@Nullable
	public HandOccupied getHandOccupiedBy(InteractionHand hand) {
		ItemStack heldItem = getItemInHand(hand);
		if (!heldItem.isEmpty()) {
			return HandOccupied.ITEM;
		}
		return null;
	}

	/**
	 * @return false it's not possible to place the entire stack in the inventory.
	 */
	public boolean addItem(ItemStack item) {
		return handsPseudoInventory.add(item);
	}
	
	@Override
	protected void dropEquipment(/*ServerLevel level*/) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			dropItem(slot);
		}
	}
	
	public void dropItem(EquipmentSlot slot) {
		Level level = level();
		if (!level.isClientSide()) {
			ItemStack item = getItemBySlot(slot);
			if (!item.isEmpty()) {
				Vec3 itemPos = position();
				InteractionHand hand = slot == EquipmentSlot.MAINHAND ? InteractionHand.MAIN_HAND
						: slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND
								: null;
				Vec3 offset = new Vec3(
						hand != null ? getBbWidth() * 0.5 * (UtilFunctions.getHandSide(this, hand) == HumanoidArm.LEFT ? -1 : 1) : 0, 
						getBbHeight() * 0.4, 
						0);
				itemPos = itemPos.add(offset.yRot((180 - getYRot()) * MathUtil.DEG_TO_RAD));
				ItemEntity itemEntity = new ItemEntity(level, itemPos.x, itemPos.y, itemPos.z, item.copy());
				setItemSlot(slot, ItemStack.EMPTY);
				level.addFreshEntity(itemEntity);
			}
		}
	}

	@Nullable
	public ItemEntity tossItem(InteractionHand hand, boolean singleItem) {
		EquipmentSlot slot = switch (hand) {
			case MAIN_HAND -> EquipmentSlot.MAINHAND;
			case OFF_HAND -> EquipmentSlot.OFFHAND;
		};
		return tossItem(slot, getXRot(), getYRot(), singleItem ? 1 : Integer.MAX_VALUE);
	}

	@Nullable
	public ItemEntity tossItem(EquipmentSlot slot, Vec3 tossVec) {
		Vec2 angles = MathUtil.lookAngles(tossVec);
		float xRot = angles.x;
		float yRot = angles.y;
		return tossItem(slot, xRot, yRot, Integer.MAX_VALUE);
	}

	@Nullable
	public ItemEntity tossItem(EquipmentSlot slot, float xRot, float yRot, int maxCount) {
		Level level = level();
		if (level.isClientSide()) return null;

		ItemStack item = getItemBySlot(slot);
		if (item.isEmpty()) return null;
		
		if (maxCount < item.getCount()) {
			item = item.split(maxCount);
		}
		else {
			setItemSlot(slot, ItemStack.EMPTY);
		}
		InteractionHand hand = slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		return drop(item, xRot, yRot, hand);
	}
	
	public ItemEntity drop(ItemStack item, @Nullable InteractionHand itemHand) {
		return drop(item, getXRot(), getYRot(), itemHand);
	}
	
	public ItemEntity drop(ItemStack item, float xRot, float yRot, @Nullable InteractionHand itemHand) {
		Level level = level();
		if (!level.isClientSide()) {
			ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getEyeY() - 0.3F, this.getZ(), item);
			// itemEntity.setPickUpDelay(40);
			itemEntity.setThrower(this);

			float f8 = Mth.sin(xRot * MathUtil.DEG_TO_RAD);
			float f2 = Mth.cos(xRot * MathUtil.DEG_TO_RAD);
			float f3 = Mth.sin(yRot * MathUtil.DEG_TO_RAD);
			float f4 = Mth.cos(yRot * MathUtil.DEG_TO_RAD);
			float f5 = this.random.nextFloat() * (float) (Math.PI * 2);
			float f6 = 0.02F * this.random.nextFloat();
			itemEntity.setDeltaMovement(
					(double)(-f3 * f2 * 0.3F) + Math.cos((double)f5) * (double)f6,
					(double)(-f8 * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F),
					(double)(f4 * f2 * 0.3F) + Math.sin((double)f5) * (double)f6
					);

			if (itemHand != null) swing(itemHand);
			level.addFreshEntity(itemEntity);
			return itemEntity;
		}
		return null;
	}
	
	public enum HandOccupied {
		ITEM,
		BLOCK
	}

	@Override
	public ItemStack getProjectile(ItemStack shootable) {
		if (!(shootable.getItem() instanceof ProjectileWeaponItem)) {
			return ItemStack.EMPTY;
		} else {
			ProjectileWeaponItem weaponItem = (ProjectileWeaponItem) shootable.getItem();
			Predicate<ItemStack> projectileCondition = weaponItem.getSupportedHeldProjectiles(shootable);
			ItemStack projectile = ProjectileWeaponItem.getHeldProjectile(this, projectileCondition);
			if (!projectile.isEmpty()) {
				return CommonHooks.getProjectile(this, shootable, projectile);
			} else {
				projectileCondition = weaponItem.getAllSupportedProjectiles(shootable);

				for (InteractionHand hand : InteractionHand.values()) {
					ItemStack item = this.getItemInHand(hand);
					if (projectileCondition.test(item)) {
						return CommonHooks.getProjectile(this, shootable, item);
					}
				}

				return CommonHooks.getProjectile(this, shootable, ItemStack.EMPTY);
			}
		}
	}
	
	protected void pickUpItemEntities() {
		Level level = this.level();
		if (!level.isClientSide() && this.isManuallyControlled() && getCurStandAction() == null && this.getHealth() > 0) {
			AABB aabb;
			if (this.isPassenger() && !this.getVehicle().isRemoved()) {
				aabb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0);
			} else {
				aabb = this.getBoundingBox().inflate(1.0, 0.5, 1.0);
			}

			List<Entity> list = this.level().getEntities(this, aabb);

			for (Entity entity : list) {
				if (!entity.isRemoved()) {
					this.touch(entity);
				}
			}
		}
	}
	
	protected void touch(Entity entity) {
		switch (entity) {
			case ItemEntity itemEntity -> {
				ItemStack itemStack = itemEntity.getItem();
				Item item = itemStack.getItem();
				int count = itemStack.getCount();
	
				// Neo: Fire item pickup pre/post and adjust handling logic to adhere to the event result.
//				TriState result = EventHooks.fireItemPickupPre(itemEntity, this).canPickup();
				TriState result = TriState.DEFAULT;
				if (result.isFalse()) {
					return;
				}
	
				// Make a copy of the original stack for use in ItemEntityPickupEvent.Post
				ItemStack originalCopy = itemStack.copy();
				// Subvert the vanilla conditions (pickup delay and target check) if the result is true.
				if ((itemEntity.getOwner() != this || !itemEntity.hasPickUpDelay() && itemEntity.tickCount > 40)
						&& (itemEntity.getTarget() == null || itemEntity.getTarget().equals(this.getUUID()))) {
					result = TriState.TRUE;
				}
				if (result.isTrue()) {
					boolean tookEntireStack = this.addItem(itemStack);
					if (tookEntireStack) {
						// Fire ItemEntityPickupEvent.Post
//						EventHooks.fireItemPickupPost(itemEntity, this, originalCopy);
						// Update `i` to reflect the actual pickup amount. Vanilla is wrong here and always reports the whole stack.
						count = originalCopy.getCount() - itemStack.getCount();

						this.take(itemEntity, count);
						if (itemStack.isEmpty()) {
							itemEntity.discard();
							itemStack.setCount(count);
						}

						if (getUser() instanceof ServerPlayer player) {
							player.awardStat(Stats.ITEM_PICKED_UP.get(item), count);
						}
						this.onItemPickup(itemEntity);
					}
				}
			}
			case AbstractArrow arrow -> {
				if (!(arrow instanceof ThrownTrident trident && !(trident.ownedBy(this) || trident.getOwner() == null))
						&& (/*arrow.isInGround()*/ arrow.inGround || arrow.isNoPhysics()) && arrow.shakeTime <= 0) {
					if (arrow.pickup == AbstractArrow.Pickup.ALLOWED && this.addItem(arrow.getPickupItem())) {
						this.take(arrow, 1);
						arrow.discard();
					}
				}
			}
			default -> {}
		}

	}
	

	protected float lastTickFinisherVal;
	protected float finisherVal;
	protected int noFinisherDecayTicks;
	protected static final int FINISHER_NO_DECAY_TICKS = 40;
	protected static final float FINISHER_DECAY = 0.025F;
    
	public float getFinisherMeter() {
		return entityData.get(FINISHER_VALUE);
	}
    
	public float getFinisherMeter(float partialTick) {
		return Mth.clamp(partialTick, lastTickFinisherVal, finisherVal);
	}
	
	public void setFinisherMeter(float value) {
		entityData.set(FINISHER_VALUE, Mth.clamp(value, 0, getFinisherMeterMax()));
	}
	
	public void addFinisherMeter(float value) {
		addFinisherMeter(value, FINISHER_NO_DECAY_TICKS);
	}
	
	public void addFinisherMeter(float value, int noDecayTicks) {
		if (value > 0) {
			LivingEntity user = getUser();
			if (user != null && ResolveModeEffect.getResolveEffectLvl(getUser()) >= 0) {
				value *= 2;
			}
		}
		float prev = getFinisherMeter();
		setFinisherMeter(prev + value);
		this.noFinisherDecayTicks = Math.max(this.noFinisherDecayTicks, noDecayTicks);
	}
	
	public void consumeFinisherMeter(float value) {
		consumeFinisherMeter(value, FINISHER_NO_DECAY_TICKS);
	}
	
	public void consumeFinisherMeter(float value, int noDecayTicks) {
		addFinisherMeter(-value, noDecayTicks);
	}
	
	public void setHeavyPunchFinisher() {
		entityData.set(LAST_HEAVY_FINISHER_VALUE, getFinisherMeter());
	}
	
	public float getLastHeavyFinisherValue() {
		return entityData.get(LAST_HEAVY_FINISHER_VALUE);
	}
	
	public boolean willHeavyPunchBeFinisher() {
		return getFinisherMeter() >= 0.5F;
	}
	
	public boolean isCurrentHeavyPunchFinisher() {
		return getLastHeavyFinisherValue() >= 0.5F;
	}
	
	public float getFinisherMeterMax() {
		return 1;
	}
	
	protected void tickFinisherMeter() {
		if (!level().isClientSide()) {
			if (noFinisherDecayTicks > 0) {
				noFinisherDecayTicks--;
			}
			else {
				EntityActionInstance currentAction = getCurStandAction();
				if (currentAction == null || !(currentAction.ability instanceof StandEntityAbility standAbility && standAbility.noFinisherBarDecay)) {
					float decay = FINISHER_DECAY;
					float value = entityData.get(FINISHER_VALUE);
					if (value < 1F) {
						decay *= 0.5F;
					}
					LivingEntity user = getUser();
					if (user != null && ResolveModeEffect.getResolveEffectLvl(user) >= 0) {
						decay *= 0.5F;
					}
					setFinisherMeter(Math.max(value - decay, 0));
				}
			}
		}
		lastTickFinisherVal = finisherVal;
		finisherVal = getFinisherMeter();
	}

	
	/**
	 * Apparently we have to do this to make sure the user's id is read before the EntityJoinLevelEvent fires on client side.
	 */
	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity trackedEntity) {
		return new ClientboundAddEntityPacket(this, trackedEntity, entityData.get(USER_ID));
	}
	
	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket packet) {
		super.recreateFromPacket(packet);
		entityData.set(USER_ID, packet.getData());
	}
	
	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		ResourceLocation.STREAM_CODEC.encode(buffer, standId);
		buffer.writeFloat(yBodyRot);
		buffer.writeVarInt(tickCount);
		if (standDimensions == null) {
			standDimensions = getType().getDimensions();
		}
		buffer.writeFloat(standDimensions.width());
		buffer.writeFloat(standDimensions.height());
		buffer.writeVarInt(summonPoseRandomByte);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		standId = ResourceLocation.STREAM_CODEC.decode(additionalData);
		StandType standType = StandType.fromId(standId);
		if (standType != null) {
			initStandTypeState(standType);
		}
		yBodyRot = additionalData.readFloat();
		yBodyRotO = yBodyRot;
		tickCount = additionalData.readVarInt();
		standDimensions = EntityDimensions.scalable(additionalData.readFloat(), additionalData.readFloat());
		this.refreshDimensions();
		summonPoseRandomByte = additionalData.readVarInt();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);

		ListTag listtag = new ListTag();
		for (ItemStack item : this.handItems) {
			if (!item.isEmpty()) {
				listtag.add(item.save(this.registryAccess()));
			} else {
				listtag.add(new CompoundTag());
			}
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);

		if (compound.contains("HandItems", 9)) {
			ListTag listtag = compound.getList("HandItems", 10);

			for (int i = 0; i < this.handItems.size(); i++) {
				CompoundTag itemNbt = listtag.getCompound(i);
				this.handItems.set(i, ItemStack.parseOptional(this.registryAccess(), itemNbt));
			}
		} else {
			this.handItems.replaceAll(item -> ItemStack.EMPTY);
		}
	}

	
	public int nonIdlePoseTimeStamp;
}
