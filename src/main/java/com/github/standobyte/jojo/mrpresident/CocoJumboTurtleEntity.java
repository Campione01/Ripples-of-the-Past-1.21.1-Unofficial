package com.github.standobyte.jojo.mrpresident;

import java.util.List;
import java.util.function.Predicate;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowItem;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.effect.UserStandEffects;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.stands.goldexperience.GECreatedLifeformEffect;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

public class CocoJumboTurtleEntity extends Turtle {
	private static final EntityDataAccessor<Boolean> HAS_KEY = SynchedEntityData.defineId(CocoJumboTurtleEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ASSIGNED_KEY = SynchedEntityData.defineId(CocoJumboTurtleEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> IS_CARRIED = SynchedEntityData.defineId(CocoJumboTurtleEntity.class, EntityDataSerializers.BOOLEAN);
	private static final ResourceLocation GOT_ARROW_ADVANCEMENT = JojoMod.resLoc("jojo/stand_arrow");
	private static final ResourceLocation MET_TURTLE_ADVANCEMENT = JojoMod.resLoc("jojo/coco_jumbo");
	private static long lastSpawnTime = Long.MIN_VALUE;

	public CocoJumboTurtleEntity(EntityType<? extends Turtle> type, Level level) {
		super(type, level);
	}

	public CocoJumboTurtleEntity(Level level) {
		this(ModEntityTypes.COCO_JUMBO_TURTLE.get(), level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(HAS_KEY, false);
		builder.define(ASSIGNED_KEY, false);
		builder.define(IS_CARRIED, false);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putBoolean("Key", hasKey());
		nbt.putBoolean("AssignedKey", hasAssignedKey());
		nbt.putBoolean("Carried", isCarried());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		setHasKey(nbt.getBoolean("Key"));
		setAssignedKey(nbt.getBoolean("AssignedKey"));
		setCarried(nbt.getBoolean("Carried"));
	}

	public boolean hasKey() {
		return entityData.get(HAS_KEY);
	}

	public void setHasKey(boolean hasKey) {
		entityData.set(HAS_KEY, hasKey);
	}

	public boolean hasAssignedKey() {
		return entityData.get(ASSIGNED_KEY);
	}

	public void setAssignedKey(boolean assignedKey) {
		entityData.set(ASSIGNED_KEY, assignedKey);
	}

	public boolean isCarried() {
		return entityData.get(IS_CARRIED);
	}

	public void setCarried(boolean carried) {
		entityData.set(IS_CARRIED, carried);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Turtle.createAttributes()
				.add(Attributes.MAX_HEALTH, 60.0D)
				.add(Attributes.ARMOR, 2.0D);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel serverLevel) {
			for (ServerPlayer player : serverLevel.players()) {
				if (player.distanceToSqr(this) < 36.0D) {
					ModCriteriaTriggers.triggerMetModdedMob(player, this);
				}
			}
			if (getVehicle() == null) {
				setCarried(false);
			}
			MinecraftServer server = serverLevel.getServer();
			MrPresidentRoomStateOwner owner = MrPresidentRoomStateOwner.get(server);
			boolean hasMrPresidentStand = hasMrPresidentStandPower();
			if (hasMrPresidentStand || owner.getForTurtle(getUUID()) != null) {
				owner.rememberTurtlePosition(this);
			}
			boolean roomLocked = isRoomLocked();
			owner.tickRoomLockState(server, getUUID(), roomLocked);
			if (hasMrPresidentStand && !roomLocked) {
				tryTeleportFallingTargetsIntoRoom(serverLevel, owner);
			}
		}
		LivingEntity carrier = getCarrier();
		if (carrier != null) {
			if (!carrier.getOffhandItem().isEmpty() || carrier.isSpectator()) {
				stopRiding();
			}
			else {
				setYRot(carrier.getYRot());
				setYHeadRot(carrier.getYHeadRot());
				setYBodyRot(carrier.yBodyRot);
			}
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && !level().isClientSide() && level().getServer() != null) {
			MrPresidentRoomStateOwner.get(level().getServer()).cleanupRoomForTurtle(level().getServer(), getUUID());
		}
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distToClosestPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && hasKey() && player.getItemInHand(hand).isEmpty()) {
			if (!level().isClientSide()) {
				setHasKey(false);
				ItemStack keyStack = hasAssignedKey() ? makeBoundKey() : new ItemStack(ModItems.MR_PRESIDENT_KEY.get());
				player.setItemInHand(hand, keyStack);
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}
		ItemStack heldItem = player.getItemInHand(hand);
		if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && !isPassenger() && !(heldItem.getItem() instanceof ProjectileWeaponItem)) {
			if (player.getOffhandItem().isEmpty()) {
				if (startRiding(player, true) && !level().isClientSide()) {
					setCarried(true);
					player.displayClientMessage(Component.translatable("coco_jumbo.hint.release",
							Component.keybind("key.swapOffhand"), getDisplayName()), true);
				}
				return InteractionResult.sidedSuccess(level().isClientSide());
			}
			if (level().isClientSide()) {
				player.displayClientMessage(Component.translatable("coco_jumbo.carry.offhand", getDisplayName()), true);
			}
			return InteractionResult.PASS;
		}
		return super.mobInteract(player, hand);
	}

	public LivingEntity getCarrier() {
		return isCarried() && getVehicle() instanceof LivingEntity living ? living : null;
	}

	@Override
	public void stopRiding() {
		super.stopRiding();
		if (!level().isClientSide() && getVehicle() == null) {
			setCarried(false);
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (IS_CARRIED.equals(key) && !isCarried()) {
			stopRiding();
		}
	}

	@Override
	public boolean isPickable() {
		return super.isPickable() && !isCarried();
	}

	public static Vec3 carryOffset(float yRot, LivingEntity carrier) {
		HumanoidArm offHand = carrier.getMainArm().getOpposite();
		float width = carrier.getBbWidth();
		Vec3 carryVec = new Vec3(
				width * (offHand == HumanoidArm.LEFT ? 0.55D : -0.55D),
				carrier.getBbHeight() * 0.35D,
				width * 0.75D);
		return carryVec.yRot(-yRot * Mth.DEG_TO_RAD);
	}

	public static boolean isCarriedTurtle(Entity passenger, Entity carrier) {
		return passenger.getType() == ModEntityTypes.COCO_JUMBO_TURTLE.get()
				&& passenger instanceof CocoJumboTurtleEntity turtle
				&& turtle.getCarrier() == carrier;
	}

	public InteractionResult interactWithKeyItem(Player player, InteractionHand hand, ItemStack stack, MrPresidentKeyItem keyItem) {
		if (hasKey()) {
			return InteractionResult.PASS;
		}

		if (keyItem.isMasterKey()) {
			if (!level().isClientSide()) {
				setHasKey(true);
				setAssignedKey(true);
				MinecraftServer server = level().getServer();
				if (server != null) {
					MrPresidentRoomStateOwner.get(server).getOrCreateForTurtle(getUUID());
				}
				triggerKeyAdvancement(player);
				stack.shrink(1);
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}

		if (!hasStandPower()) {
			return keyRejected(player, Component.translatable("coco_jumbo.key.no_stand", getDisplayName()));
		}

		if (hasAssignedKey()) {
			if (!matchesAssignedKey(stack, keyItem)) {
				String messageKey = keyItem.hasAssignedTurtle(stack) ? "coco_jumbo.key.wrong" : "coco_jumbo.key.empty";
				return keyRejected(player, Component.translatable(messageKey));
			}
		}
		else {
			if (keyItem.hasAssignedTurtle(stack)) {
				return keyRejected(player, Component.translatable("coco_jumbo.key.not_empty"));
			}
		}

		if (!level().isClientSide()) {
			setHasKey(true);
			setAssignedKey(true);
			keyItem.assignToTurtle(stack, getUUID(), getDisplayName());
			MinecraftServer server = level().getServer();
			if (server != null) {
				MrPresidentRoomStateOwner.get(server).getOrCreateForTurtle(getUUID());
			}
			triggerKeyAdvancement(player);
			stack.shrink(1);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	private boolean hasStandPower() {
		StandPower standPower = StandPower.get(this);
		return standPower != null && standPower.hasPower();
	}

	private boolean hasMrPresidentStandPower() {
		StandPower standPower = StandPower.get(this);
		return standPower != null && standPower.hasPower() && standPower.getPowerType() == ModStands.MR_PRESIDENT.get();
	}

	private boolean isRoomLocked() {
		return hasAssignedKey() && !hasKey();
	}

	private void tryTeleportFallingTargetsIntoRoom(ServerLevel serverLevel, MrPresidentRoomStateOwner owner) {
		StandPower standPower = StandPower.get(this);
		if (standPower == null || !standPower.hasPower() || standPower.getPowerType() != ModStands.MR_PRESIDENT.get()
				|| !standPower.canUsePower()) {
			return;
		}
		List<Entity> targets = findTargets(this, this::canEnterMrPresidentRoom);
		if (!targets.isEmpty()) {
			owner.enterFallingTargets(serverLevel, this, targets);
		}
	}

	public static List<Entity> findTargets(Entity turtle, Predicate<Entity> filter) {
		Predicate<Entity> predicate = EntitySelector.NO_SPECTATORS
				.and(entity -> entity.getBbWidth() < 4.0F && entity.getBbHeight() < 4.0F)
				.and(filter);
		return turtle.level().getEntities(turtle, turtle.getBoundingBox().move(0.0D, 0.5D, 0.0D).inflate(0.25D), predicate);
	}

	private boolean canEnterMrPresidentRoom(Entity entity) {
		return EntitySelector.NO_SPECTATORS.test(entity)
				&& entity.getBbWidth() < 4.0F
				&& entity.getBbHeight() < 4.0F
				&& !entity.onGround()
				&& entity.getDeltaMovement().y < 0.0D
				&& entity.getY() > getY(1.0D)
				&& !(entity.tickCount < 20 && entity.getType() == EntityType.PLAYER)
				&& !(entity instanceof StandEntity)
				&& !entity.isPassengerOfSameVehicle(this)
				&& !(entity instanceof LivingEntity living && isGECreatedLifeformStandUser(living));
	}

	private boolean isGECreatedLifeformStandUser(LivingEntity entity) {
		return UserStandEffects.getEffectsTargetedBy(this, ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
				.filter(GECreatedLifeformEffect.class::isInstance)
				.map(GECreatedLifeformEffect.class::cast)
				.anyMatch(effect -> effect.getStandUser() == entity);
	}

	private InteractionResult keyRejected(Player player, Component message) {
		if (level().isClientSide()) {
			player.displayClientMessage(message, true);
		}
		return InteractionResult.FAIL;
	}

	private void triggerKeyAdvancement(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			ModCriteriaTriggers.triggerCocoJumboKey(serverPlayer);
		}
	}

	private boolean matchesAssignedKey(ItemStack stack, MrPresidentKeyItem keyItem) {
		var uuid = keyItem.getAssignedTurtleUuid(stack);
		return uuid != null && uuid.equals(getUUID());
	}

	private ItemStack makeBoundKey() {
		ItemStack keyStack = new ItemStack(ModItems.MR_PRESIDENT_KEY.get());
		ItemStack copy = keyStack.copy();
		if (copy.getItem() instanceof MrPresidentKeyItem keyItem) {
			keyItem.assignToTurtle(copy, getUUID(), getDisplayName());
		}
		return copy;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		dropKey();
	}

	public void dropKey() {
		if (!level().isClientSide() && hasKey()) {
			spawnAtLocation(new ItemStack(ModItems.MR_PRESIDENT_KEY.get()));
		}
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(source);
	}

	public static void onRegularTurtleSpawn(FinalizeSpawnEvent event) {
		if (event.getEntity().getType() != EntityType.TURTLE) {
			return;
		}
		MobSpawnType spawnType = event.getSpawnType();
		if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION && spawnType != MobSpawnType.SPAWNER) {
			return;
		}
		ServerLevelAccessor spawnLevel = event.getLevel();
		if (lastSpawnTime == spawnLevel.dayTime()) {
			return;
		}
		ServerLevel level = spawnLevel.getLevel();
		Player nearest = level.getNearestPlayer(event.getX(), event.getY(), event.getZ(), -1.0D, EntitySelector.NO_SPECTATORS);
		if (!(nearest instanceof ServerPlayer player)) {
			return;
		}

		float spawnChancePerTurtle = spawnChancePerTurtle(player, spawnType);
		if (player.getRandom().nextFloat() >= spawnChancePerTurtle) {
			return;
		}

		CocoJumboTurtleEntity extraTurtle = ModEntityTypes.COCO_JUMBO_TURTLE.get().create(level);
		if (extraTurtle == null) {
			return;
		}
		extraTurtle.moveTo(event.getX(), event.getY(), event.getZ(), event.getEntity().getRandom().nextFloat() * 360.0F, 0.0F);
		if (extraTurtle.checkSpawnRules(spawnLevel, spawnType) && extraTurtle.checkSpawnObstruction(spawnLevel)) {
			DifficultyInstance difficulty = event.getDifficulty();
			SpawnGroupData spawnData = extraTurtle.finalizeSpawn(spawnLevel, difficulty, spawnType, null);
			level.addFreshEntityWithPassengers(extraTurtle);
			lastSpawnTime = spawnLevel.dayTime();
		}
	}

	private static float spawnChancePerTurtle(ServerPlayer player, MobSpawnType spawnType) {
		boolean hasTurtleAdvancement = hasAdvancement(player, MET_TURTLE_ADVANCEMENT);
		boolean hasArrow = player.getInventory().contains(stack -> stack.getItem() instanceof StandArrowItem);
		boolean hasArrowAdvancement = hasAdvancement(player, GOT_ARROW_ADVANCEMENT);
		boolean chunkGeneration = spawnType == MobSpawnType.CHUNK_GENERATION;
		if (!hasTurtleAdvancement) {
			return chunkGeneration ? 0.075F : 0.015F;
		}
		if (hasArrow) {
			return chunkGeneration ? 0.0375F : 0.0075F;
		}
		if (hasArrowAdvancement) {
			return chunkGeneration ? 0.025F : 0.005F;
		}
		return chunkGeneration ? 0.0125F : 0.0025F;
	}

	private static boolean hasAdvancement(ServerPlayer player, ResourceLocation advancementId) {
		AdvancementHolder advancement = player.server.getAdvancements().get(advancementId);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}
}
