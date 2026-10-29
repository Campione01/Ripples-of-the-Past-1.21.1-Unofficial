package rotp.addons.rhcp.entity;

import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.util.functions.DamageUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import java.util.EnumSet;
import java.util.Locale;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.ability.RhcpAbility;
import rotp.addons.rhcp.network.RhcpManualMovePayload;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RhcpStandEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean> ATTACK_ALTERNATE =
            SynchedEntityData.defineId(
                    RhcpStandEntity.class, EntityDataSerializers.BOOLEAN);
    // The controller's manual Shift, for viewers other than the controller.
    private static final EntityDataAccessor<Boolean> MANUAL_SHIFT =
            SynchedEntityData.defineId(
                    RhcpStandEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHASING =
            SynchedEntityData.defineId(
                    RhcpStandEntity.class, EntityDataSerializers.BOOLEAN);
    // 1.16 RHCPEntity.setPotency (RhcpService.potencySpeedBonus)
    private static final ResourceLocation POTENCY_SPEED =
            RhcpAddon.id("potency_movement_speed");
    // 1.16 RHCPEntity.modifiersFromResolveLevel does nothing: Resolve never
    // grows RHCP's stats (potency does). The core puts the growth into the
    // Stand's base values, so these take it back out.
    private static final ResourceLocation NO_RESOLVE_POWER =
            RhcpAddon.id("no_resolve_power");
    private static final ResourceLocation NO_RESOLVE_SPEED =
            RhcpAddon.id("no_resolve_speed");
    private static final ResourceLocation NO_RESOLVE_MOVEMENT =
            RhcpAddon.id("no_resolve_movement_speed");
    private static final ResourceLocation NO_RESOLVE_DURABILITY =
            RhcpAddon.id("no_resolve_durability");
    private static final ResourceLocation NO_RESOLVE_PRECISION =
            RhcpAddon.id("no_resolve_precision");
    // 1.16 RHCPEntity.canBreakBlock without power: harvest level 0 at most.
    private static final StandStatFormulas.BlockMiningTier UNPOWERED_TIER =
            new StandStatFormulas.BlockMiningTier.VanillaTierWrapper(Tiers.WOOD);
    private int lastLightningTick = -100;
    // 1.16 RHCPEntity.lastFireDamageTick
    private int lastFireDamageTick = -100;
    private boolean wetExhaustionRemoval;
    private int alternateAttackTicks;
    // 1.16 RHCPEntity.ticksEvaded
    private int ticksEvaded;
    // 1.16 getLastPunch().getDamageDealtToLiving() of the last heavy punch;
    // the server's alone.
    private float lastHeavyDamage;
    // 1.16 RHCPEntity.standCount: idle ticks the target lock outlives an attack
    private int standCount = -1;
    // 1.16 RHCPEntity.recentBlock: the last block RHCP entered or left; the
    // server's alone, saved with the entity (RHCPEntity :1265-1275).
    private BlockPos recentBlock;
    // 1.16 RHCPEntity.noTask: the attacks that hold standCount before their
    // recovery.
    private static final EnumSet<RhcpAbility.Mode> LOCK_ATTACKS = EnumSet.of(
            RhcpAbility.Mode.HEAVY_PUNCH, RhcpAbility.Mode.COMBO_PUNCH,
            RhcpAbility.Mode.PUNCH, RhcpAbility.Mode.PUNCH_OFF,
            RhcpAbility.Mode.BARRAGE, RhcpAbility.Mode.COUNTERATTACK,
            RhcpAbility.Mode.BLITZ_ATTACK);
    // The port's heavy (the electrocute target), combo, their follow-ups and
    // the dash read the lock while they run.
    private static final EnumSet<RhcpAbility.Mode> LOCK_READERS = EnumSet.of(
            RhcpAbility.Mode.HEAVY_PUNCH, RhcpAbility.Mode.COMBO_PUNCH,
            RhcpAbility.Mode.ELECTROCUTE_TARGET, RhcpAbility.Mode.CHASE_TARGET,
            RhcpAbility.Mode.ELECTRIC_DASH);
    // 1.16 GameplayEventHandler: a direct entity named for fire or lightning.
    private static final String[] ABSORB_ENTITY_KEYWORDS = {
            "lightning", "fire", "flame", "电", "火", "雷"};
    // Walk and jump costs seen on the controlling client, sent once a tick.
    private boolean manualWalkPending;
    private boolean manualJumpPending;
    private boolean manualChaseCancelPending;
    private boolean walkingManualInputActive;
    private boolean walkingManualTravelPrepared;
    // Last synced teleport (RhcpService.teleportStand): server game time and
    // its two spots. Stale controller packets are dropped for at most this long.
    public static final int DIRECT_POS_STALE_TICKS = 20;
    private long directPosTick = -1000L;
    private Vec3 directPosFrom;
    private Vec3 directPosTo;

    public RhcpStandEntity(
            EntityType<? extends RhcpStandEntity> type,
            Level level) {
        super(type, level);
        standHasNoGravity = false;
        standCanHaveNoPhysics = false;
        setNoGravity(false);
        // 1.16 RHCPEntity.getDefaultOffsetFromUser: behind the user's right
        // shoulder and arms-only both 0.5 up, turned with the view yaw. The
        // 0.5 is also the default y of task offsets without their own.
        Y_OFFSET = 0.5D;
        offsetFromUser.idleOffset = new Vec3(-0.75D, Y_OFFSET, -0.75D);
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.armsOnlyIdleOffset = new Vec3(0.0D, 0.5D, 0.15D);
        offsetFromUser.armsOnlyIdleRotations =
                StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.grabIdleOffset = new Vec3(-1.0D, Y_OFFSET, 1.5D);
        offsetFromUser.resetToIdle();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_ALTERNATE, false);
        builder.define(MANUAL_SHIFT, false);
        builder.define(CHASING, false);
    }

    public boolean isAttackAlternate() {
        return entityData.get(ATTACK_ALTERNATE);
    }

    public void setAttackAlternate(boolean alternate) {
        entityData.set(ATTACK_ALTERNATE, alternate);
        alternateAttackTicks = alternate ? 20 : 0;
    }

    public boolean isChasing() {
        return entityData.get(CHASING);
    }

    public void setChasing(boolean chasing) {
        if (isChasing() != chasing) {
            entityData.set(CHASING, chasing);
            _manualControlInput = Vec3.ZERO;
            if (!chasing) {
                setDeltaMovement(Vec3.ZERO);
            }
            updateUserOffset(getUser());
        }
    }

    public void cancelChaseFromManualInput(boolean walked, boolean jumped) {
        if ((walked || jumped) && isChasing() && isManuallyControlled()
                && canMoveManually()) {
            if (level().isClientSide()) {
                manualChaseCancelPending = true;
            }
            clearChase(getUser());
        }
    }

    private void clearChase(LivingEntity user) {
        setChasing(false);
        standCount = -1;
        RhcpAbility.Mode mode = RhcpAbility.modeOf(getCurStandAction());
        if (isAttackAlternate() && mode != RhcpAbility.Mode.HEAVY_PUNCH) {
            setAttackAlternate(false);
        }
        RhcpStandEntity current = RhcpService.activeStand(user);
        if (user != null && (current == null || current == this)) {
            RhcpState state = RhcpAddon.state(user);
            if (state.targetId() >= 0) {
                state.setTargetId(-1);
                if (user instanceof ServerPlayer player) {
                    RhcpService.sync(player);
                }
            }
        }
    }

    public void addTicksEvaded() {
        ticksEvaded++;
    }

    public int ticksEvaded() {
        return ticksEvaded;
    }

    public float lastHeavyDamage() {
        return lastHeavyDamage;
    }

    public void setLastHeavyDamage(float damage) {
        lastHeavyDamage = damage;
    }

    public int standCount() {
        return standCount;
    }

    public void setStandCount(int ticks) {
        standCount = ticks;
    }

    public void addStandCount(int ticks) {
        standCount += ticks;
    }

    // 1.16 RHCPEntity.getRecentBlock: RHCP's own block until it has one.
    public BlockPos recentBlock() {
        return recentBlock != null ? recentBlock : blockPosition();
    }

    public void setRecentBlock(BlockPos pos) {
        recentBlock = pos != null ? pos.immutable() : null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (recentBlock != null) {
            compound.put("RecentBlock", NbtUtils.writeBlockPos(recentBlock));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        recentBlock = NbtUtils.readBlockPos(compound, "RecentBlock")
                .orElse(null);
    }

    @Override
    public void tick() {
        updateStatModifiers();
        prepareWalkingManualTravel();
        super.tick();
        walkingManualTravelPrepared = false;
        LivingEntity user = getUser();
        boolean insideConductor = user != null
                && RhcpAddon.state(user).inConductor();
        noPhysics = insideConductor;
        setNoGravity(insideConductor);
        setInvisible(insideConductor);
        if (level().isClientSide()) {
            sendManualMovementCosts();
        }
        else {
            entityData.set(MANUAL_SHIFT, isManuallyControlled()
                    && StandManualInput.isShiftHeld(user, this));
        }
        if (!(user instanceof ServerPlayer player)
                || !RhcpAddon.isUser(player)) {
            if (!level().isClientSide() && isChasing()) {
                clearChase(user);
            }
            return;
        }

        var action = getCurStandAction();
        boolean heavy = action != null
                && action.ability instanceof Ability ability
                && "rhcp_heavy_punch".equals(ability.name());
        if (isAttackAlternate() && !heavy && --alternateAttackTicks <= 0) {
            setAttackAlternate(false);
        }
        RhcpState state = RhcpAddon.state(player);
        // 1.16 RHCPEntity.tick: standCount runs down while noTask() (no
        // attack, or an attack in its recovery: guard, dodge, recharge too)
        // and below 0 the target lock is dropped, except while an action
        // that reads the lock runs.
        RhcpAbility.Mode taskMode = RhcpAbility.modeOf(action);
        boolean noTask = taskMode == null || !LOCK_ATTACKS.contains(taskMode)
                || action.getPhase() == ActionPhase.RECOVERY;
        if (standCount >= 0 && noTask) {
            standCount--;
        }
        if (isChasing() && standCount < 0) {
            clearChase(player);
        }
        if (standCount < 0 && state.targetId() >= 0
                && (taskMode == null || !LOCK_READERS.contains(taskMode))) {
            state.setTargetId(-1);
            RhcpService.sync(player);
        }
        if (state.inConductor()) {
            if (!RhcpService.isConductor(
                    level().getBlockState(state.conductorPos()))) {
                RhcpService.leaveConductor(player);
            }
            else {
                net.minecraft.core.BlockPos conductor =
                        RhcpService.nearestConductor(
                                level(), blockPosition(), 1);
                if (conductor == null) {
                    conductor = state.conductorPos();
                    RhcpService.teleportStand(this,
                            RhcpService.conductorSpot(conductor));
                    setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                }
                else if (state.enterConductor(conductor)) {
                    RhcpService.sync(player);
                }
                setNoGravity(true);
                noPhysics = true;
                setInvisible(true);
                // 1.16 RHCPEntity.updatePosition: RHCP rests in the block.
                // Resting or travelling in a wire costs no energy in 1.16.
                setDeltaMovement(Vec3.ZERO);
                RhcpService.tickCarriedTarget(player, this);
            }
        }
        else {
            setNoGravity(false);
            noPhysics = false;
            setInvisible(false);
        }

        if (isInWaterOrRain()) {
            RhcpService.consume(
                    player,
                    isInWater() ? 80.0F : 5.0F,
                    0.0F);
            if (state.energy() <= 0.0F
                    && StandPowerAvailable.staminaEmpty(player)) {
                wetExhaustionRemoval = true;
                RhcpAddon.RHCP.get().forceUnsummon(player, StandPowerAvailable.get(player));
                return;
            }
        }

        // 1.16 RHCPEntity.tick: only a powered RHCP with room in its battery
        // takes in a nearby bolt.
        if (!level().isClientSide()
                && state.potency() > 0
                && state.energy() < RhcpState.MAX_ENERGY
                && tickCount - lastLightningTick >= 5) {
            LightningBolt bolt = level().getEntitiesOfClass(
                    LightningBolt.class,
                    getBoundingBox().inflate(6.0D),
                    Entity::isAlive)
                    .stream()
                    .filter(lightning -> distanceToSqr(lightning) <= 36.0D)
                    .findFirst()
                    .orElse(null);
            if (bolt != null) {
                lastLightningTick = tickCount;
                state.addEnergy(300.0F + state.potency() * 50.0F);
                raisePotencyAfterAbsorbing(state);
                bolt.discard();
                RhcpService.sync(player);
                RhcpService.emitElectricParticles(
                        player.serverLevel(), this, 30);
            }
        }

        // 1.16 RHCPEntity.tick: once the dodge (and a counter it started) is
        // over, the dodge cools down for as long as it was held, at most 400
        // ticks (10 in Creative).
        StandPower power = getUserPower();
        RhcpAbility.Mode mode = RhcpAbility.modeOf(getCurStandAction());
        if (ticksEvaded > 0 && mode != RhcpAbility.Mode.DODGE
                && mode != RhcpAbility.Mode.COUNTERATTACK
                && mode != RhcpAbility.Mode.BLITZ_ATTACK) {
            if (power != null) {
                power.setAbilityCooldown("rhcp_dodge",
                        power.isUserCreative() ? 10 : Math.min(ticksEvaded, 400));
            }
            ticksEvaded = 0;
        }

        // 1.16 RHCPEntity.tick: a dodging RHCP evades the nearest enemy
        // Stand projectile.
        if (mode == RhcpAbility.Mode.DODGE) {
            RhcpService.evadeProjectiles(player, this);
            mode = RhcpAbility.modeOf(getCurStandAction());
        }

        // 1.16 RHCPEntity.tick: from Resolve 4, or standing in a conductor
        // block, RHCP charges by itself while it is not recharging; below
        // Resolve 4 that is the conductor block's own signal.
        int resolve = power != null ? power.getResolveLevel() : 0;
        BlockPos standPos = blockPosition();
        boolean inConductorBlock =
                RhcpService.isConductor(level().getBlockState(standPos));
        if ((resolve >= 4 || inConductorBlock)
                && mode != RhcpAbility.Mode.RECHARGE) {
            RhcpService.energyCharge(player, this,
                    inConductorBlock && resolve < 4 ? standPos : null, true);
        }
    }

    @Override
    public void onUnsummonUserInput() {
        if (isChasing()) {
            clearChase(getUser());
        }
        if (!isBeingRetracted() && getUser() instanceof ServerPlayer player) {
            RhcpService.leaveConductor(player);
            RhcpService.cleanupTransient(player);
        }
        super.onUnsummonUserInput();
    }

    @Override
    public boolean onActionSet(EntityActionInstance action) {
        boolean continuingChase = action == null && isChasing() && !isBeingRetracted();
        boolean canceled = super.onActionSet(action);
        // The core's automatic post-action recall must not end the donor's continuing chase.
        if (continuingChase && !canceled) {
            stopRetraction();
        }
        return canceled;
    }

    @Override
    public void remove(RemovalReason reason) {
        setChasing(false);
        if (!isRemoved() && getUser() instanceof ServerPlayer player) {
            if (wetExhaustionRemoval) {
                wetExhaustionRemoval = false;
                if (player.isAlive()) {
                    DamageUtil.hurtThroughInvulTicks(player, player.damageSources().generic(), 2.0F);
                    player.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
                }
            }
            RhcpStandEntity current = RhcpService.activeStand(player);
            if (current == null || current == this) {
                RhcpService.cleanupTransient(player);
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        LivingEntity user = getUser();
        boolean lightning = source.is(DamageTypeTags.IS_LIGHTNING)
                || source.getDirectEntity() instanceof LightningBolt;
        if (!level().isClientSide()
                && user instanceof ServerPlayer player
                && (lightning || source.is(DamageTypeTags.IS_FIRE))) {
            RhcpState state = RhcpAddon.state(player);
            // 1.16 GameplayEventHandler (HIGHEST LivingAttackEvent): with room
            // in the battery a powered RHCP takes in every fire or lightning
            // hit, puts the fire out and cancels it. At potency 0 RHCP takes
            // the hit.
            if (state.potency() > 0
                    && state.energy() < RhcpState.MAX_ENERGY) {
                state.addEnergy(absorbedEnergy(source, state.potency()));
                raisePotencyAfterAbsorbing(state);
                clearFire();
                RhcpService.sync(player);
                RhcpService.emitElectricParticles(
                        player.serverLevel(), this, 20);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        LivingEntity user = getUser();
        boolean lightning = source.is(DamageTypeTags.IS_LIGHTNING)
                || source.getDirectEntity() instanceof LightningBolt;
        if (!level().isClientSide()
                && user instanceof ServerPlayer player
                && player.isAlive()
                && (lightning || source.is(DamageTypeTags.IS_FIRE))) {
            RhcpState state = RhcpAddon.state(player);
            // Native hurt has already established its hurt window before this callback.
            if (state.potency() > 0
                    && state.energy() >= RhcpState.MAX_ENERGY
                    && (lightning || tickCount - lastFireDamageTick >= 5)) {
                if (!lightning) {
                    lastFireDamageTick = tickCount;
                }
                if (damageContainers != null && !damageContainers.isEmpty()) {
                    damageContainers.peek().setNewDamage(0.0F);
                }
                raisePotencyAfterAbsorbing(state);
                RhcpService.sync(player);
                RhcpService.emitElectricParticles(
                        player.serverLevel(), this, 20);
                return;
            }
        }
        // Scale the accepted native hurt-window remainder, not its admission amount.
        if (damageContainers != null && !damageContainers.isEmpty()) {
            var container = damageContainers.peek();
            container.setNewDamage(container.getNewDamage()
                    * (source.is(DamageTypeTags.BYPASSES_ARMOR) ? 0.75F : 0.8F));
        }
        super.actuallyHurt(source, amount);
    }

    // 1.16 GameplayEventHandler: every vanilla fire or lightning msgId
    // (inFire, onFire, lava, hotFloor, fireball, lightningBolt) matches its
    // keywords, 100 + 15 a potency level; a direct entity named for fire or
    // lightning (a fireball, a bolt) overrides that with 80 + 10.
    // RHCPEntity.absorbLightningEnergy adds 50 a potency level.
    static float absorbedEnergy(DamageSource source, int potency) {
        float base = 100.0F + potency * 15.0F;
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            String name = direct.getName().getString().toLowerCase(Locale.ROOT);
            for (String keyword : ABSORB_ENTITY_KEYWORDS) {
                if (name.contains(keyword)) {
                    base = 80.0F + potency * 10.0F;
                    break;
                }
            }
        }
        return base + potency * 50.0F;
    }

    // 1.16 RHCPEntity: an absorption raises potency only from 400 energy,
    // up to 5.
    private static void raisePotencyAfterAbsorbing(RhcpState state) {
        if (state.energy() >= 400.0F && state.potency() < 5) {
            state.setPotency(state.potency() + 1);
        }
    }

    // 1.16 RHCPEntity.standDamageResistance: while RHCP is powered, a hit its
    // held rhcp_block stops costs a tenth of the damage in energy, taken from
    // stamina 1:1 once the battery is empty. The 5-tick auto-guard (1.16
    // BLOCK_STAND_ENTITY) costs none.
    @Override
    protected float standDamageResistance(
            DamageSource dmgSource, float dmgAmount, boolean isBlocking) {
        var action = getCurStandAction();
        if (isBlocking && !level().isClientSide() && !isAutoGuarding()
                && action != null
                && action.ability instanceof Ability ability
                && "rhcp_block".equals(ability.name())
                && getUser() instanceof ServerPlayer player
                && RhcpAddon.state(player).potency() > 0) {
            RhcpService.consume(
                    player,
                    dmgAmount < 0.0F ? 0.1F : dmgAmount * 0.1F,
                    0.0F,
                    false);
        }
        return super.standDamageResistance(dmgSource, dmgAmount, isBlocking);
    }

    private int potency() {
        LivingEntity user = getUser();
        return user != null ? RhcpAddon.state(user).potency() : 0;
    }

    private void updateStatModifiers() {
        int potency = potency();
        setModifier(Attributes.MOVEMENT_SPEED, POTENCY_SPEED,
                RhcpService.potencySpeedBonus(potency),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        StandPower power = getUserPower();
        float development = power != null ? power.getStatsDevelopment() : 0.0F;
        StandStats stats = RhcpAddon.RHCP.get().getStandStats();
        setModifier(Attributes.ATTACK_DAMAGE, NO_RESOLVE_POWER,
                -stats.getDevPower(development),
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(Attributes.ATTACK_SPEED, NO_RESOLVE_SPEED,
                -stats.getDevAttackSpeed(development),
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(Attributes.MOVEMENT_SPEED, NO_RESOLVE_MOVEMENT,
                StandStatFormulas.getMovementSpeed(stats.getBaseAttackSpeed())
                        - StandStatFormulas.getMovementSpeed(stats.speed(development)),
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(ModEntityAttributes.STAND_DURABILITY, NO_RESOLVE_DURABILITY,
                -stats.getDevDurability(development),
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(ModEntityAttributes.STAND_PRECISION, NO_RESOLVE_PRECISION,
                -stats.getDevPrecision(development),
                AttributeModifier.Operation.ADD_VALUE);
    }

    // Changes a modifier only when its amount changes, so the attribute is
    // not re-sent every tick.
    private void setModifier(Holder<Attribute> attribute, ResourceLocation id,
            double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.amount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0D) {
            instance.addTransientModifier(
                    new AttributeModifier(id, amount, operation));
        }
    }

    // 1.16 RHCPEntity.canBreakBlock: without power RHCP breaks only blocks of
    // hardness 1 or less (1.16 getStandBreakBlockHardness) that a wooden tool
    // can harvest.
    @Override
    public float getBlockHardnessForStandBreak(
            BlockState blockState, Level level, BlockPos blockPos) {
        if (potency() < 1) {
            float hardness = blockState.getDestroySpeed(level, blockPos);
            if (!blockState.requiresCorrectToolForDrops()) {
                hardness *= 0.6F;
            }
            if (hardness < 0.0F || hardness > 1.0F
                    || !UNPOWERED_TIER.canMine(blockState)) {
                return -1.0F;
            }
        }
        return super.getBlockHardnessForStandBreak(blockState, level, blockPos);
    }

    // The unpowered rule above replaced 1.16 StandEntity.canBreakBlock, so
    // the Stand strength rule does not apply on top of it.
    @Override
    public boolean replacesStandBreakStrengthRule(
            BlockState blockState, Level level, BlockPos blockPos) {
        return potency() < 1
                || super.replacesStandBreakStrengthRule(
                        blockState, level, blockPos);
    }

    // 1.16 RHCPEntity.getAttackDamage / getAttackSpeed: potency raises the
    // Stand's strength and speed, and halves them without power.
    @Override
    public double getAttackDamage() {
        return RhcpService.powerIncrease(super.getAttackDamage(), potency(), false);
    }

    @Override
    public double getAttackSpeed() {
        return RhcpService.powerIncrease(super.getAttackSpeed(), potency(), true);
    }

    // 1.16 RHCPEntity.getDurability: a quarter without power.
    @Override
    public double getDurability() {
        return potency() > 0 ? super.getDurability() : super.getDurability() * 0.25D;
    }

    // 1.16 RHCPEntity.leapBaseStrength: 7 without power.
    @Override
    protected double leapBaseStrength() {
        int potency = potency();
        return potency > 0
                ? RhcpService.powerIncrease(super.leapBaseStrength(), potency, false)
                : 7.0D;
    }

    // 1.16 RHCPEntity.getUserWalkSpeed: from Resolve 3, each potency level
    // past the first gives back a quarter of what an action slows the user,
    // except while recharging, dodging or countering.
    @Override
    public float getUserWalkSpeed(float baseWalkSpeed) {
        float factor = super.getUserWalkSpeed(baseWalkSpeed);
        int potency = potency();
        var power = getUserPower();
        RhcpAbility.Mode mode = RhcpAbility.modeOf(getCurStandAction());
        if (power != null && power.getResolveLevel() >= 3 && potency > 1
                && mode != RhcpAbility.Mode.RECHARGE
                && mode != RhcpAbility.Mode.DODGE
                && mode != RhcpAbility.Mode.COUNTERATTACK
                && mode != RhcpAbility.Mode.BLITZ_ATTACK) {
            factor += (1.0F - factor) * (0.25F * (potency - 1));
        }
        return factor;
    }

    // 1.16 keeps chase, conductor entry and the dash out of automatic follow.
    @Override
    public boolean isFollowingUser() {
        LivingEntity user = getUser();
        RhcpAbility.Mode mode = RhcpAbility.modeOf(getCurStandAction());
        return super.isFollowingUser()
                && !isChasing()
                && mode != RhcpAbility.Mode.ENTER_CONDUCTOR
                && mode != RhcpAbility.Mode.ELECTRIC_DASH
                && !(user != null && RhcpAddon.state(user).inConductor());
    }

    @Override
    public void updatePosition(LivingEntity user) {
        RhcpState state = user != null ? RhcpAddon.state(user) : null;
        RhcpAbility.Mode mode = RhcpAbility.modeOf(getCurStandAction());
        if (isChasing() && !level().isClientSide()
                && (user == null || !user.isAlive() || user.level() != level()
                        || RhcpService.activeStand(user) != this || state == null
                        || !state.powered() || state.inConductor() || isBeingRetracted())) {
            clearChase(user);
        }
        if (isChasing() && (mode == RhcpAbility.Mode.ENTER_CONDUCTOR
                || mode == RhcpAbility.Mode.ELECTRIC_DASH)) {
            setChasing(false);
        }
        if (!isChasing()) {
            super.updatePosition(user);
            faceLockUnderManualControl(state, mode);
            return;
        }
        // 1.16 StandEntity.tick turned RHCP before its updatePosition, while
        // chasing too: to its task's target, else along its user's look.
        if (!isManuallyControlled() && !lookAtTaskTarget()
                && user != null && followingUserIsEnabled()) {
            setYRot(user.getYRot());
            setXRot(user.getXRot());
            setYHeadRot(getYRot());
        }
        faceLockUnderManualControl(state, mode);
        if (level().isClientSide() && !isManuallyControlled()) {
            return;
        }
        Entity locked = state != null ? level().getEntity(state.targetId()) : null;
        if (!(locked instanceof LivingEntity target) || !target.isAlive()) {
            // The flag and lock arrive separately; only the server expires a missing lock.
            if (!level().isClientSide()) {
                clearChase(user);
            }
            return;
        }
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (speed <= 0.0D) {
            return;
        }
        Vec3 targetPos = target.getEyePosition(1.0F);
        Vec3 difference = targetPos.subtract(position());
        double distance = difference.length();
        boolean attacking = mode == RhcpAbility.Mode.PUNCH
                || mode == RhcpAbility.Mode.BARRAGE
                || mode == RhcpAbility.Mode.HEAVY_PUNCH
                || mode == RhcpAbility.Mode.COMBO_PUNCH;
        boolean manualAttackInReach = isManuallyControlled() && attacking
                && state.energy() > distance * 10.0D
                && HitResultUtil.isTargetWithinRange(new ActionTarget(target), this, level(),
                        getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
                        getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        if (manualAttackInReach || distance <= 2.0D) {
            setDeltaMovement(Vec3.ZERO);
            if (!position().equals(targetPos)) {
                if (level().isClientSide()) {
                    setPos(targetPos);
                    updateUserOffset(user);
                }
                else {
                    RhcpService.teleportStand(this, targetPos);
                }
            }
            return;
        }
        Vec3 movement = difference.normalize().scale(speed * (distance > 5.0D ? 1.4D : 0.75D));
        float cost = (float) movement.length() * 3.0F;
        if (state.energy() <= cost) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide()) {
                clearChase(user);
            }
            return;
        }
        setDeltaMovement(movement);
        if (user instanceof ServerPlayer player) {
            if (!RhcpService.consume(player, cost, cost * 0.5F, true) || !state.powered()) {
                clearChase(user);
            }
        }
    }

    // 1.16 RHCPEntity.defaultRotation, on both sides: under manual control
    // RHCP faces its lock (getTargetHit) while it chases or winds up a
    // counter or blitz; in a conductor or dashing it keeps its rotation.
    private void faceLockUnderManualControl(RhcpState state, RhcpAbility.Mode mode) {
        if (!isManuallyControlled() || state == null || state.inConductor()
                || mode == RhcpAbility.Mode.ELECTRIC_DASH) {
            return;
        }
        boolean counterWindup = (mode == RhcpAbility.Mode.COUNTERATTACK
                        || mode == RhcpAbility.Mode.BLITZ_ATTACK)
                && getCurStandAction().getPhase() == ActionPhase.WINDUP;
        if ((isChasing() || counterWindup)
                && level().getEntity(state.targetId()) instanceof LivingEntity lock) {
            lookAtPos(lock.getEyePosition(), true);
        }
    }

    // 1.16 RHCPEntity.isPickable: while dodging or countering, a Stand
    // projectile RHCP can evade passes through it.
    @Override
    public boolean isPickable() {
        RhcpAbility.Mode mode = RhcpAbility.modeOf(getCurStandAction());
        if ((mode == RhcpAbility.Mode.DODGE
                        || mode == RhcpAbility.Mode.COUNTERATTACK)
                && RhcpService.evadableProjectileNear(this)) {
            return false;
        }
        return !isInvisible() && super.isPickable();
    }

    @Override
    public void manualControlInput(Vec3 motionInput) {
        cancelChaseFromManualInput(motionInput.x != 0.0D || motionInput.z != 0.0D, false);
        LivingEntity user = getUser();
        boolean conductor = user != null
                && RhcpAddon.state(user).inConductor();
        super.manualControlInput(conductor
                ? motionInput
                : new Vec3(motionInput.x, 0.0D, motionInput.z));
        // 1.16 RHCPEntity.moveStandManually: 0.1 / 0.05 energy each tick the
        // walking Stand's own input moves it.
        if (level().isClientSide() && walksUnderManualControl()
                && (motionInput.x != 0.0D || motionInput.z != 0.0D)) {
            manualWalkPending = true;
        }
    }

    // 1.16 RHCPEntity.standHasNoGravity = canTravel: outside a conductor RHCP
    // has gravity, so under manual control it walks and jumps.
    @Override
    public boolean walksUnderManualControl() {
        return !isNoGravity();
    }

    @Override
    public boolean manualControlJump(boolean jumping) {
        cancelChaseFromManualInput(false, jumping);
        return super.manualControlJump(jumping);
    }

    // 1.16 RHCPEntity.getJumpPower: potency raises the jump.
    @Override
    protected float getJumpPower() {
        return super.getJumpPower() * (1.0F + 0.5F * potency());
    }

    // 1.16 RHCPEntity.jumpFromGround / jumpInLiquid: 1.25 / 0.625 energy.
    @Override
    public void jumpFromGround() {
        chargeManualJump();
        super.jumpFromGround();
    }

    @Override
    public void jumpInFluid(FluidType type) {
        chargeManualJump();
        super.jumpInFluid(type);
    }

    private void chargeManualJump() {
        if (level().isClientSide()) {
            manualJumpPending = true;
        }
        else if (getUser() instanceof ServerPlayer player
                && RhcpAddon.isUser(player)) {
            RhcpService.consume(player, 1.25F, 0.625F);
        }
    }

    // The controlling client moves RHCP; the server charges what it reports.
    private void sendManualMovementCosts() {
        if (manualWalkPending || manualJumpPending || manualChaseCancelPending) {
            PacketDistributor.sendToServer(new RhcpManualMovePayload(
                    getId(), manualWalkPending, manualJumpPending, manualChaseCancelPending));
            manualWalkPending = false;
            manualJumpPending = false;
            manualChaseCancelPending = false;
        }
    }

    private boolean usesWalkingManualTravel() {
        LivingEntity user = getUser();
        return user != null && isManuallyControlled()
                && isControlledByLocalInstance() && !isChasing()
                && canMoveManually()
                && !RhcpAddon.state(user).inConductor();
    }

    private void prepareWalkingManualTravel() {
        walkingManualTravelPrepared = false;
        if (!usesWalkingManualTravel()) {
            walkingManualInputActive = false;
            return;
        }
        Vec3 input = _manualControlInput;
        _manualControlInput = Vec3.ZERO;
        boolean moving = input.x != 0.0D || input.z != 0.0D;
        // The donor queues velocity for travel, clearing it once on release.
        if (moving || walkingManualInputActive || jumping
                || StandManualInput.isShiftHeld(getUser(), this)) {
            setDeltaMovement(input.x, getDeltaMovement().y, input.z);
        }
        walkingManualInputActive = moving;
        walkingManualTravelPrepared = true;
    }

    @Override
    protected void moveStandManualControl() {
        if (walkingManualTravelPrepared) {
            LivingEntity user = getUser();
            if (user != null) {
                updateUserOffset(user);
            }
            return;
        }
        LivingEntity user = getUser();
        if (user == null || !isControlledByLocalInstance()
                || RhcpAddon.state(user).inConductor()) {
            super.moveStandManualControl();
            return;
        }
        if (_offsetFromUserVec == null) {
            updateUserOffset(user);
        }
        // Gravity has already moved the entity this tick; do not rewind its Y.
        double verticalMotion = getDeltaMovement().y;
        _offsetFromUserVec = new Vec3(
                _offsetFromUserVec.x,
                getY() - user.getY(),
                _offsetFromUserVec.z);
        super.moveStandManualControl();
        Vec3 horizontalMotion = getDeltaMovement();
        setDeltaMovement(new Vec3(
                horizontalMotion.x, verticalMotion, horizontalMotion.z));
        // The flat offset move cleared onGround; restore it so the next
        // aiStep jump check sees the ground (1.16 moved once, in travel).
        if (verticalMotion <= 0.0D) {
            setOnGround(!level().noCollision(this,
                    getBoundingBox().move(0.0D, -1.0E-3D, 0.0D)));
        }
    }

    // A teleport (TrDirectEntityPosPacket on the client) re-anchors manual
    // control; the stale offset would pull RHCP back to the old spot.
    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        super.moveTo(x, y, z, yRot, xRot);
        updateUserOffset(getUser());
    }

    public void markDirectPos(Vec3 from) {
        directPosTick = level().getGameTime();
        directPosFrom = from;
        directPosTo = position();
    }

    // 1.16 applied every controller packet. One sent before the client saw
    // a synced teleport aims nearer the old spot than the landing and is
    // dropped; the first one nearer the landing ends the window.
    public boolean isStaleManualMove(Vec3 target) {
        if (directPosFrom == null) {
            return false;
        }
        if (level().getGameTime() - directPosTick > DIRECT_POS_STALE_TICKS
                || target.distanceToSqr(directPosTo)
                        <= target.distanceToSqr(directPosFrom)) {
            directPosFrom = null;
            return false;
        }
        return true;
    }

    // The manual movement packet moves the Stand with MoverType.PLAYER;
    // a stale one keeps the landing (its rotation still applies).
    @Override
    public void move(MoverType type, Vec3 vec) {
        if (type == MoverType.PLAYER && !level().isClientSide()
                && isManuallyControlled()
                && isStaleManualMove(position().add(vec))) {
            return;
        }
        super.move(type, vec);
    }

    // 1.16 RHCPModel read the user's sneak, which port manual control
    // clears: the controller reads its own key, other viewers the synced one.
    public boolean isUserCrouching() {
        LivingEntity user = getUser();
        if (user == null) {
            return false;
        }
        if (level().isClientSide() && isManuallyControlled()
                && !(user instanceof Player player && player.isLocalPlayer())) {
            return entityData.get(MANUAL_SHIFT);
        }
        return StandManualInput.isShiftHeld(user, this);
    }

    private static final class StandPowerAvailable {
        private StandPowerAvailable() {}

        static rotp.core.powersystem.standpower.StandPower
                get(ServerPlayer player) {
            return rotp.core.powersystem.standpower
                    .StandPower.get(player);
        }

        static boolean staminaEmpty(ServerPlayer player) {
            var power = get(player);
            return power == null || power.getStamina() <= 0.0F;
        }
    }
}
