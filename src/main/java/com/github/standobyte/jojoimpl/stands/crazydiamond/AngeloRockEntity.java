package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.LivingComponentPossession;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.general.TimerQueue;
import com.github.standobyte.jojo.util.reflection.CommonReflection;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.PrevBlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class AngeloRockEntity extends Entity implements IEntityWithComplexSpawn {
	private static final EntityDataAccessor<Optional<BlockPos>> DATA_ATTACH_POS_ID = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Boolean> CREATION_COMPLETE = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(AngeloRockEntity.class, EntityDataSerializers.FLOAT);
	private static final int CREATION_ANIM_LEN = 40;
	private static final float ROCK_BREAK_DAMAGE = 40.0F;
	@Nullable public static LivingEntity mobLootFortune;
	private static int mobLootFortuneLevel;

	private int creationAnimTicks;
	private boolean startedSound;
	private final Map<BlockPos, PrevBlockInfo> angeloRockBlocks = new HashMap<>();
	private final List<ItemStack> itemDrops = new ArrayList<>();
	@Nullable private UUID targetUUID;
	@Nullable private LivingEntity targetEntity;
	public boolean keepMobInside;
	@Nullable private Mob mob;
	private boolean useMobHurtSound;
	private DamageSource lastAttack;
	private final TimerQueue responseSoundTimer = new TimerQueue(false);

	public AngeloRockEntity(EntityType<? extends AngeloRockEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static int getMobLootFortuneLevel(LivingEntity entity) {
		return entity == mobLootFortune ? mobLootFortuneLevel : 0;
	}

	public static AngeloRockEntity turnIntoRock(Level level, LivingEntity target, Vec3 rockPos, float yRot,
			PrevBlockInfo... angeloRockBlocks) {
		if (level.isClientSide()) {
			return null;
		}

		AngeloRockEntity rock = new AngeloRockEntity(ModEntityTypes.ANGELO_ROCK.get(), level);
		rock.setYRot(yRot);
		rock.setPos(rockPos.x, rockPos.y, rockPos.z);
		rock.creationAnimTicks = CREATION_ANIM_LEN;
		rock.targetUUID = target.getUUID();
		rock.targetEntity = target;
		if (angeloRockBlocks != null) {
			for (PrevBlockInfo block : angeloRockBlocks) {
				if (block != null) {
					rock.angeloRockBlocks.put(block.pos, block);
				}
			}
		}
		level.addFreshEntity(rock);
		return rock;
	}

	public void setBlockDrops(List<ItemStack> itemDrops) {
		this.itemDrops.clear();
		itemDrops.forEach(stack -> this.itemDrops.add(stack.copy()));
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!isFullyFormed()) {
			return InteractionResult.FAIL;
		}
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (mob != null) {
			mob.setPos(getX(), getY(), getZ());
		}
		if (playAngeloInteractionVoiceLine(player)) {
			responseSoundTimer.add(30);
		}
		else {
			playMobResponseSound();
		}
		return InteractionResult.CONSUME;
	}

	private boolean playAngeloInteractionVoiceLine(Player player) {
		StandPower standPower = PowerClass.STAND.get(player);
		if (standPower != null && standPower.hasPower()
				&& standPower.getPowerType() == ModStands.CRAZY_DIAMOND.get()) {
			return JojoModUtil.sayVoiceLine(player, ModSoundEvents.JOSUKE_YO_ANGELO);
		}
		return false;
	}

	public void playMobResponseSound() {
		if (mob != null) {
			if (useMobHurtSound) {
				CommonReflection.playHurtSound(mob, mob.damageSources().generic());
			}
			else {
				mob.playAmbientSound();
			}
		}
	}

	private void tickResponseTimers() {
		if (!level().isClientSide()) {
			responseSoundTimer.tick(this::playMobResponseSound);
		}
	}

	public boolean isFullyFormed() {
		return entityData.get(CREATION_COMPLETE);
	}

	public void breakRock() {
		if (!level().isClientSide()) {
			entityData.set(DAMAGE, Float.MAX_VALUE);
		}
	}

	public float getCreationAnimProgress(float partialTick) {
		return creationAnimTicks <= 0 ? 1 : (CREATION_ANIM_LEN - creationAnimTicks + partialTick) / CREATION_ANIM_LEN;
	}

	public boolean preventsDeathFor(LivingEntity target) {
		return isAlive() && !isFullyFormed() && targetUUID != null && targetUUID.equals(target.getUUID());
	}

	public static boolean preventTargetDeath(LivingEntity target) {
		return !target.level().isClientSide()
				&& target.level().getEntitiesOfClass(AngeloRockEntity.class, target.getBoundingBox().inflate(4.0D),
						rock -> rock.preventsDeathFor(target))
				.stream().findAny().isPresent();
	}

	public BlockState getLowerBlock() {
		return getBlock(0);
	}

	public BlockState getUpperBlock() {
		return getBlock(1);
	}

	private BlockState getBlock(int index) {
		return angeloRockBlocks.values().stream()
				.sorted((a, b) -> Integer.compare(a.pos.getY(), b.pos.getY()))
				.skip(index)
				.findFirst()
				.map(block -> block.state)
				.orElse(Blocks.STONE.defaultBlockState());
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		tickCreationAnim();
		if (!level().isClientSide()) {
			tickResponseTimers();
			if (!isFullyFormed()) {
				LivingEntity target = resolveTarget();
				if (target != null && target.isAlive()) {
					target.setDeltaMovement(Vec3.ZERO);
					target.setPos(getX(), getY(), getZ());
				}
				if (creationAnimTicks <= 0) {
					entityData.set(CREATION_COMPLETE, true);
					if (target instanceof ServerPlayer player) {
						LivingComponentPossession.setPossessionTarget(player, this, "angelo_rock");
					}
					else if (target != null) {
						target.discard();
						if (keepMobInside && target instanceof Mob targetMob) {
							this.mob = targetMob;
							useMobHurtSound = CommonReflection.getAmbientSound(targetMob) == null;
						}
						targetEntity = null;
						targetUUID = null;
					}
				}
			}
			BlockPos attachPos = getAttachPosition();
			if (attachPos == null && !level().isClientSide()) {
				attachPos = this.blockPosition();
				entityData.set(DATA_ATTACH_POS_ID, Optional.of(attachPos));
			}
			else if (attachPos != null && !level().isClientSide()) {
				Optional<BlockPos> moveWithPiston = moveWithPiston(attachPos);
				if (moveWithPiston.isEmpty()) {
					moveWithPiston = moveWithPiston(attachPos.above());
				}
				moveWithPiston.ifPresent(pos -> entityData.set(DATA_ATTACH_POS_ID, Optional.of(pos)));
			}
			if (attachPos != null) {
				setPos(attachPos.getX() + 0.5D, attachPos.getY(), attachPos.getZ() + 0.5D);
				setBoundingBox(new AABB(
						getX() - 0.5D,
						getY(),
						getZ() - 0.5D,
						getX() + 0.5D,
						getY() + getBbHeight(),
						getZ() + 0.5D));
			}
			if (isBroken()) {
				doBreakRock();
			}
		}
	}

	private void tickCreationAnim() {
		if (creationAnimTicks > 0) {
			if (level().isClientSide()) {
				clientCreationAnimTick();
			}
			--creationAnimTicks;
		}
	}

	private void clientCreationAnimTick() {
		if (ClientGlobals.canSeeStands) {
			CrazyDHealAbility.addParticlesAround(this);
		}
		if (ClientGlobals.canHearStands && !isSilent()) {
			if (!startedSound) {
				level().playLocalSound(this, ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), getSoundSource(), 1.0F, 1.0F);
				ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(
						ModSoundEvents.CRAZY_DIAMOND_FIX_LOOP.get(), getSoundSource(), 1.0F, 1.0F, true, this,
						level().random.nextLong(), () -> this.isRemoved() || creationAnimTicks <= 0));
				startedSound = true;
			}
			if (creationAnimTicks == 1) {
				level().playLocalSound(this, ModSoundEvents.CRAZY_DIAMOND_FIX_ENDED.get(), getSoundSource(), 1.0F, 1.0F);
			}
		}
	}

	private Optional<BlockPos> moveWithPiston(BlockPos pistonBlockPos) {
		Direction pistonHeadDir = null;
		BlockState blockState = level().getBlockState(pistonBlockPos);
		if (!blockState.isAir() && (blockState.is(Blocks.MOVING_PISTON) || blockState.is(Blocks.PISTON_HEAD))) {
			pistonHeadDir = blockState.getValue(BlockStateProperties.FACING);
		}
		if (pistonHeadDir != null) {
			BlockPos attachPos = getAttachPosition();
			if (attachPos != null) {
				BlockPos newPos = attachPos.relative(pistonHeadDir);
				if (this.level().isEmptyBlock(newPos) && this.level().isEmptyBlock(newPos.above())) {
					return Optional.of(newPos);
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public void setPos(double pX, double pY, double pZ) {
		super.setPos(pX, pY, pZ);
		if (this.entityData != null && this.tickCount != 0) {
			Optional<BlockPos> optional = this.entityData.get(DATA_ATTACH_POS_ID);
			Optional<BlockPos> optional1 = Optional.of(BlockPos.containing(pX, pY, pZ));
			if (!optional1.equals(optional)) {
				this.entityData.set(DATA_ATTACH_POS_ID, optional1);
				this.hasImpulse = true;
			}
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> dataParameter) {
		super.onSyncedDataUpdated(dataParameter);
		if (DATA_ATTACH_POS_ID.equals(dataParameter) && level().isClientSide()) {
			BlockPos blockpos = getAttachPosition();
			if (blockpos != null) {
				setPos(blockpos.getX() + 0.5D, blockpos.getY(), blockpos.getZ() + 0.5D);
			}
		}
	}

	@Nullable
	public BlockPos getAttachPosition() {
		return this.entityData.get(DATA_ATTACH_POS_ID).orElse(null);
	}

	public void setAttachPosition(@Nullable BlockPos pPos) {
		this.entityData.set(DATA_ATTACH_POS_ID, Optional.ofNullable(pPos));
	}

	@Nullable
	private LivingEntity resolveTarget() {
		if (targetEntity != null && targetEntity.isAlive()) {
			return targetEntity;
		}
		if (targetUUID != null && level() instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntity(targetUUID);
			if (entity instanceof LivingEntity living) {
				targetEntity = living;
				return living;
			}
		}
		return null;
	}

	@Override
	public boolean hurt(DamageSource damageSource, float amount) {
		if (level().isClientSide()) {
			return false;
		}
		if (!(damageSource.getEntity() instanceof LivingEntity attacker)) {
			return false;
		}

		if (attacker instanceof Player player && player.getAbilities().instabuild) {
			lastAttack = damageSource;
			entityData.set(DAMAGE, Float.MAX_VALUE);
			return true;
		}

		ItemStack item = attacker.getMainHandItem();
		if (item.isEmpty() || !item.is(ItemTags.PICKAXES)) {
			return false;
		}

		float blockDestroySpeed = (item.getDestroySpeed(getLowerBlock()) + item.getDestroySpeed(getUpperBlock())) * 0.5F;
		int efficiency = getEfficiencyLevel(attacker, item);
		if (efficiency > 0) {
			blockDestroySpeed += efficiency * efficiency + 1;
		}
		if (requiresBetterTool(item)) {
			blockDestroySpeed *= 0.3F;
		}
		float damage = Math.max(blockDestroySpeed, 1.0F);
		playHitSound();
		lastAttack = damageSource;
		entityData.set(DAMAGE, entityData.get(DAMAGE) + damage);
		if (isBroken()) {
			item.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	private int getEfficiencyLevel(LivingEntity attacker, ItemStack item) {
		HolderGetter<Enchantment> enchantments = attacker.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.EFFICIENCY), item);
	}

	private int getFortuneLevel(@Nullable DamageSource damageSource) {
		if (damageSource != null && damageSource.getEntity() instanceof LivingEntity attacker) {
			HolderGetter<Enchantment> enchantments = attacker.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			return EnchantmentHelper.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.FORTUNE), attacker);
		}
		return 0;
	}

	private boolean requiresBetterTool(ItemStack item) {
		return !(isCorrectToolFor(getLowerBlock(), item) || isCorrectToolFor(getUpperBlock(), item));
	}

	private boolean isCorrectToolFor(BlockState state, ItemStack item) {
		return !state.requiresCorrectToolForDrops() || item.isCorrectToolForDrops(state);
	}

	private void playHitSound() {
		BlockState randomBlock = random.nextBoolean() ? getLowerBlock() : getUpperBlock();
		SoundType soundType = randomBlock.getSoundType();
		level().playSound(null, getX(), getY(0.5), getZ(), soundType.getHitSound(), SoundSource.BLOCKS,
				(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
	}

	private boolean isBroken() {
		return entityData.get(DAMAGE) >= ROCK_BREAK_DAMAGE;
	}

	private void doBreakRock() {
		if (!level().isClientSide()) {
			boolean doDrops = level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
			angeloRockBlocks.values().forEach(block ->
					CrazyDRestoreTerrainAbility.rememberBrokenBlock(level(), block.pos, block.state, Optional.empty(), block.drops));
			if (doDrops) {
				Vec3 pos = position();
				for (ItemStack stack : itemDrops) {
					if (!stack.isEmpty()) {
						spawnAtLocation(stack.copy(), (float) pos.y);
					}
				}
			}
			if (level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT) && mob != null && mob.isRemoved() && lastAttack != null) {
				mob.setPos(getX(), getY(), getZ());
				mobLootFortune = mob;
				mobLootFortuneLevel = getFortuneLevel(lastAttack);
				try {
					mob.lastHurtByPlayerTime = 1;
					CommonReflection.dropAllDeathLoot(mob, lastAttack);
				}
				finally {
					mobLootFortune = null;
					mobLootFortuneLevel = 0;
				}
			}
			discard();
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ATTACH_POS_ID, Optional.empty());
		builder.define(CREATION_COMPLETE, false);
		builder.define(DAMAGE, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("APX")) {
			int i = tag.getInt("APX");
			int j = tag.getInt("APY");
			int k = tag.getInt("APZ");
			entityData.set(DATA_ATTACH_POS_ID, Optional.of(new BlockPos(i, j, k)));
		}
		else {
			entityData.set(DATA_ATTACH_POS_ID, Optional.empty());
		}
		this.creationAnimTicks = tag.getInt("CreationAnim");
		entityData.set(CREATION_COMPLETE, tag.getBoolean("Created"));
		entityData.set(DAMAGE, tag.getFloat("RockDamage"));
		if (tag.hasUUID("Target")) {
			this.targetUUID = tag.getUUID("Target");
		}
		angeloRockBlocks.clear();
		ListTag blocksTag = tag.getList("RockBlocks", Tag.TAG_COMPOUND);
		for (Tag blockTag : blocksTag) {
			PrevBlockInfo block = PrevBlockInfo.fromNBT((CompoundTag) blockTag, level().registryAccess(), true);
			if (block != null) {
				angeloRockBlocks.put(block.pos, block);
			}
		}
		itemDrops.clear();
		ListTag dropsTag = tag.getList("BlockDrops", Tag.TAG_COMPOUND);
		for (Tag dropTag : dropsTag) {
			ItemStack stack = ItemStack.parseOptional(level().registryAccess(), (CompoundTag) dropTag);
			if (!stack.isEmpty()) {
				itemDrops.add(stack);
			}
		}
		keepMobInside = tag.getBoolean("KeepMob");
		if (tag.contains("AngeloMob", Tag.TAG_COMPOUND)) {
			CompoundTag mobTag = tag.getCompound("AngeloMob");
			Entity mobEntity = EntityType.create(mobTag, level()).orElse(null);
			this.mob = mobEntity instanceof Mob ? (Mob) mobEntity : null;
			if (mob != null) {
				mob.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			}
		}
		else {
			mob = null;
		}
		useMobHurtSound = tag.getBoolean("NoAmbient");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		BlockPos blockpos = getAttachPosition();
		if (blockpos != null) {
			tag.putInt("APX", blockpos.getX());
			tag.putInt("APY", blockpos.getY());
			tag.putInt("APZ", blockpos.getZ());
		}
		tag.putInt("CreationAnim", creationAnimTicks);
		tag.putBoolean("Created", entityData.get(CREATION_COMPLETE));
		tag.putFloat("RockDamage", entityData.get(DAMAGE));
		if (targetUUID != null) {
			tag.putUUID("Target", targetUUID);
		}
		ListTag blocksTag = new ListTag();
		angeloRockBlocks.values().forEach(block -> {
			CompoundTag blockTag = block.toNBT(level().registryAccess(), true);
			if (blockTag != null) {
				blocksTag.add(blockTag);
			}
		});
		tag.put("RockBlocks", blocksTag);
		ListTag dropsTag = new ListTag();
		itemDrops.forEach(stack -> dropsTag.add(stack.save(level().registryAccess())));
		tag.put("BlockDrops", dropsTag);
		tag.putBoolean("KeepMob", keepMobInside);
		if (mob != null) {
			String encodeId = mob.getEncodeId();
			if (encodeId != null) {
				CompoundTag mobTag = new CompoundTag();
				mobTag.putString("id", encodeId);
				mob.saveWithoutId(mobTag);
				mobTag.remove("Passengers");
				tag.put("AngeloMob", mobTag);
				tag.putBoolean("NoAmbient", useMobHurtSound);
			}
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buf) {
		buf.writeInt(creationAnimTicks);
		buf.writeInt(angeloRockBlocks.size());
		for (PrevBlockInfo block : angeloRockBlocks.values()) {
			PrevBlockInfo.STREAM_CODEC.encode(buf, block);
		}
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buf) {
		creationAnimTicks = buf.readInt();
		angeloRockBlocks.clear();
		int blockCount = buf.readInt();
		for (int i = 0; i < blockCount; i++) {
			PrevBlockInfo block = PrevBlockInfo.STREAM_CODEC.decode(buf);
			angeloRockBlocks.put(block.pos, block);
		}
	}
}
