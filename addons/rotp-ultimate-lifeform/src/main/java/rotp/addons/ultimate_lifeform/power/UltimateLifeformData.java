package rotp.addons.ultimate_lifeform.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.customobjects.AfterimageEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.impl.powers.hamon.HamonWallClimbingHelper;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.util.functions.UtilFunctions;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.client.UltimateHamonAuraClient;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UltimateLifeformData extends PlayerPowerData {
    public static final float MAX_ENERGY = 12000.0F;
    public static final int MAX_HAMON_CHARGE = 6000;
    public static final int ARMOR_EFFECT_TICKS = 1_000_000;
    public static final ResourceLocation MOVEMENT_MODIFIER_ID =
            UltimateLifeformAddon.id("movement_speed");
    public static final ResourceLocation ATTACK_MODIFIER_ID =
            UltimateLifeformAddon.id("attack_damage");
    private static final AttributeModifier MOVEMENT_MODIFIER =
            new AttributeModifier(
                    MOVEMENT_MODIFIER_ID,
                    0.04D,
                    AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier ATTACK_MODIFIER =
            new AttributeModifier(
                    ATTACK_MODIFIER_ID,
                    4.0D,
                    AttributeModifier.Operation.ADD_VALUE);

    public enum ArmorType {
        NONE,
        NORMAL,
        FIRE
    }

    private float energy;
    private int hamonCharge;
    private int hamonCooldown;
    private PillarmanMode previousMode = PillarmanMode.NONE;
    private ArmorType armorType = ArmorType.NONE;
    private boolean stoneForm;
    private boolean flying;
    private boolean glideSeenSinceLeap;
    private boolean nightVision;
    private boolean bladesVisible;
    private boolean waterWalking;
    private boolean liquidWalkedSinceTick;
    private boolean doubleShiftPress;
    private boolean shiftSynced;
    private int fireDamageTaken;
    private int summonedRabbitsCount;
    private int summonedPhantomsCount;
    private int summonedRabbitsLifetime;
    private int summonedPhantomsLifetime;
    private int healthDebuff;
    private final List<Integer> eatenTntFuses =
            new ArrayList<>();
    private final Map<String, Integer> abilityCooldowns =
            new HashMap<>();
    private final Map<String, Integer> abilityCooldownTotals =
            new HashMap<>();
    private final Set<UUID> actionHitEntities = new HashSet<>();
    @Nullable private UltimateLifeformSpec heldAbility;
    @Nullable private LivingEntity heldTarget;
    @Nullable private LivingEntity shockedTarget;
    private int hamonBarrageHits;
    private int immortalityLevel;
    private int heldTicks;
    private boolean wallClimbing;
    private Direction wallFace = Direction.NORTH;
    private boolean wallMoving;
    private int wallClimbTicks;
    private float wallInputStrafe;
    private float wallInputForward;
    private boolean wallInputJump;
    private long lastWallInputGameTime = Long.MIN_VALUE;
    @Nullable private UUID guardianEndpointUuid;
    private int syncTicker;
    private long receivedSyncCount;
    private boolean lastSyncSentToTracking;

    public UltimateLifeformData() {
        super(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get());
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag nbt = super.serializeNBT(provider);
        nbt.putFloat("Energy", energy);
        nbt.putInt("HamonCharge", hamonCharge);
        nbt.putString("PillarmanMode", previousMode.name());
        nbt.putString("ArmorType", armorType.name());
        nbt.putBoolean("StoneForm", stoneForm);
        nbt.putBoolean("IsFlying", flying);
        nbt.putBoolean("NightVision", nightVision);
        nbt.putBoolean("BladesVisible", bladesVisible);
        nbt.putBoolean("WaterWalking", waterWalking);
        nbt.putInt("FireDamageTaken", fireDamageTaken);
        nbt.putInt("SummonedRabbitsCount", summonedRabbitsCount);
        nbt.putInt("SummonedPhantomsCount", summonedPhantomsCount);
        nbt.putInt("SummonedRabbitsLifetime", summonedRabbitsLifetime);
        nbt.putInt("SummonedPhantomsLifetime", summonedPhantomsLifetime);
        nbt.putInt("HealthDebuff", healthDebuff);
        nbt.putIntArray(
                "EatenTntFuse",
                eatenTntFuses.stream()
                        .mapToInt(Integer::intValue)
                        .toArray());
        CompoundTag cooldowns = new CompoundTag();
        abilityCooldowns.forEach((name, cooldown) ->
                cooldowns.putIntArray(
                        name,
                        new int[] {
                                cooldown,
                                abilityCooldownTotals.getOrDefault(
                                        name, cooldown)
                        }));
        nbt.put("AbilityCooldowns", cooldowns);
        return nbt;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag nbt) {
        super.deserializeNBT(provider, nbt);
        energy = Mth.clamp(
                nbt.getFloat("Energy"), 0.0F, MAX_ENERGY);
        hamonCharge = Mth.clamp(
                nbt.getInt("HamonCharge"), 0, MAX_HAMON_CHARGE);
        // 1.16 writeNBT kept no hamonCooldown: a loaded charge waits its
        // 600 ticks again before it drains
        hamonCooldown = 0;
        previousMode = parseEnum(
                PillarmanMode.class,
                nbt.getString("PillarmanMode"),
                PillarmanMode.NONE);
        armorType = parseEnum(
                ArmorType.class,
                nbt.getString("ArmorType"),
                ArmorType.NONE);
        stoneForm = nbt.getBoolean("StoneForm");
        flying = nbt.getBoolean("IsFlying");
        nightVision = nbt.getBoolean("NightVision");
        bladesVisible = nbt.getBoolean("BladesVisible");
        waterWalking = nbt.getBoolean("WaterWalking");
        fireDamageTaken = Math.max(
                nbt.getInt("FireDamageTaken"), 0);
        summonedRabbitsCount = Math.max(
                nbt.getInt("SummonedRabbitsCount"), 0);
        summonedPhantomsCount = Math.max(
                nbt.getInt("SummonedPhantomsCount"), 0);
        summonedRabbitsLifetime = Math.max(
                nbt.getInt("SummonedRabbitsLifetime"), 0);
        summonedPhantomsLifetime = Math.max(
                nbt.getInt("SummonedPhantomsLifetime"), 0);
        healthDebuff = Math.max(
                nbt.getInt("HealthDebuff"), 0);
        eatenTntFuses.clear();
        for (int fuse : nbt.getIntArray("EatenTntFuse")) {
            if (fuse > 0) {
                eatenTntFuses.add(fuse);
            }
        }
        readCooldowns(nbt.getCompound("AbilityCooldowns"));
        clearHeldState();
        wallClimbing = false;
        wallMoving = false;
        wallClimbTicks = 0;
        guardianEndpointUuid = null;
    }

    @Override
    public void toBuf(
            FriendlyByteBuf buf, boolean isSentToTracking) {
        super.toBuf(buf, isSentToTracking);
        buf.writeFloat(energy);
        buf.writeVarInt(hamonCharge);
        buf.writeVarInt(hamonCooldown);
        buf.writeEnum(previousMode);
        buf.writeEnum(armorType);
        buf.writeBoolean(stoneForm);
        buf.writeBoolean(flying);
        buf.writeBoolean(nightVision);
        buf.writeBoolean(bladesVisible);
        buf.writeBoolean(waterWalking);
        buf.writeBoolean(wallClimbing);
        buf.writeEnum(wallFace);
        buf.writeBoolean(wallMoving);
        buf.writeVarInt(wallClimbTicks);
        buf.writeVarInt(fireDamageTaken);
        buf.writeVarInt(summonedRabbitsCount);
        buf.writeVarInt(summonedPhantomsCount);
        buf.writeVarInt(summonedRabbitsLifetime);
        buf.writeVarInt(summonedPhantomsLifetime);
        buf.writeVarInt(healthDebuff);
        buf.writeVarInt(immortalityLevel);
        buf.writeUtf(heldAbility != null ? heldAbility.id : "");
        buf.writeVarInt(heldTicks);
        buf.writeVarInt(abilityCooldowns.size());
        abilityCooldowns.forEach((name, cooldown) -> {
            buf.writeUtf(name);
            buf.writeVarInt(cooldown);
            buf.writeVarInt(
                    abilityCooldownTotals.getOrDefault(
                            name, cooldown));
        });
    }

    @Override
    public void fromBuf(
            FriendlyByteBuf buf, boolean isSentToTracking) {
        super.fromBuf(buf, isSentToTracking);
        energy = Mth.clamp(
                buf.readFloat(), 0.0F, MAX_ENERGY);
        hamonCharge = Mth.clamp(
                buf.readVarInt(), 0, MAX_HAMON_CHARGE);
        hamonCooldown = Math.max(buf.readVarInt(), 0);
        previousMode = buf.readEnum(PillarmanMode.class);
        armorType = buf.readEnum(ArmorType.class);
        stoneForm = buf.readBoolean();
        flying = buf.readBoolean();
        nightVision = buf.readBoolean();
        bladesVisible = buf.readBoolean();
        waterWalking = buf.readBoolean();
        wallClimbing = buf.readBoolean();
        wallFace = buf.readEnum(Direction.class);
        wallMoving = buf.readBoolean();
        wallClimbTicks = buf.readVarInt();
        fireDamageTaken = buf.readVarInt();
        summonedRabbitsCount = buf.readVarInt();
        summonedPhantomsCount = buf.readVarInt();
        summonedRabbitsLifetime = buf.readVarInt();
        summonedPhantomsLifetime = buf.readVarInt();
        healthDebuff = buf.readVarInt();
        immortalityLevel = buf.readVarInt();
        heldAbility = UltimateLifeformSpec.byId(buf.readUtf());
        heldTicks = buf.readVarInt();
        abilityCooldowns.clear();
        abilityCooldownTotals.clear();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            int cooldown = buf.readVarInt();
            int total = buf.readVarInt();
            if (cooldown > 0) {
                abilityCooldowns.put(name, cooldown);
                abilityCooldownTotals.put(
                        name, Math.max(total, cooldown));
            }
        }
        receivedSyncCount++;
        lastSyncSentToTracking = isSentToTracking;
    }

    @Override
    public void onPowerGiven(
            Power<?> userPower,
            @Nullable PowerType oldType,
            @Nullable PowerData oldData) {
        LivingEntity user = userPower.getUser();
        if (!user.level().isClientSide()) {
            // 1.16 NonStandPower.onNewPowerGiven: empty, full only in creative
            energy = user instanceof Player player
                            && player.getAbilities().instabuild
                    ? MAX_ENERGY
                    : 0.0F;
            hamonCharge = 0;
            // 1.16 onPowerGiven kept the mode of a Pillar Man made an
            // Ultimate Lifeform by any route
            if (oldData instanceof PillarmanData pillarman) {
                previousMode = pillarman.getMode() != null
                        ? pillarman.getMode()
                        : PillarmanMode.NONE;
            }
            applyMovementModifier(user);
            applyAttackModifier(user);
            user.level().playSound(
                    null,
                    user,
                    UltimateLifeformAddon.SOUNDS
                            .get("awaken").get(),
                    user.getSoundSource(),
                    1.0F,
                    1.0F);
            syncOnUpdate(user);
        }
        super.onPowerGiven(userPower, oldType, oldData);
    }

    @Override
    public void onPowerCleared(
            Power<?> userPower, @Nullable PowerType newType) {
        LivingEntity user = userPower.getUser();
        removeMovementModifier(user);
        removeAttackModifier(user);
        removeOwnedEffects(user);
        clearGuardianEndpoint(user);
        stopWallClimbing(user);
        if (user instanceof Player player) {
            UltimateLifeformPowerType.disableFlight(player);
        }
        clearHeldState();
        super.onPowerCleared(userPower, newType);
    }

    @Override
    public void tick(Power<?> userPower) {
        super.tick(userPower);
        LivingEntity user = userPower.getUser();
        tickCooldowns();
        tickHamonDecay();
        tickDoubleShift(user);
        if (user.level().isClientSide()) {
            tickHamonAura(user);
            return;
        }
        applyMovementModifier(user);
        tickEnergy(user);
        tickPassives(user);
        tickHeld(user);
        tickWallClimbing(user);
        tickTnt(user);
        if (++syncTicker >= 10) {
            syncTicker = 0;
            syncOnUpdate(user);
        }
    }

    public boolean beginHeld(
            LivingEntity user, UltimateLifeformSpec spec) {
        return beginHeld(
                user,
                spec,
                UltimateLifeformCombat.crosshairTarget(user));
    }

    public boolean beginHeld(
            LivingEntity user,
            UltimateLifeformSpec spec,
            @Nullable ActionTarget target) {
        if (heldAbility != null) {
            return false;
        }
        heldAbility = spec;
        ActionTarget resolvedTarget = target != null
                ? target.resolveEntityId(user.level())
                : ActionTarget.EMPTY;
        // 1.16 a held action's target was the client's mouse target only
        if (resolvedTarget.getType() == TargetType.EMPTY) {
            resolvedTarget = UltimateLifeformCombat.crosshairTarget(user);
        }
        heldTarget = resolvedTarget.getType() == TargetType.ENTITY
                        && resolvedTarget.getMainEntity()
                                instanceof LivingEntity living
                ? living
                : null;
        heldTicks = 0;
        actionHitEntities.clear();
        bladesVisible = spec == UltimateLifeformSpec.BLADE_BARRAGE
                || spec == UltimateLifeformSpec.BLADE_DASH_ATTACK;
        syncOnUpdate(user);
        return true;
    }

    public void releaseHeld(LivingEntity user) {
        UltimateLifeformSpec released = heldAbility;
        int ticks = heldTicks;
        clearHeldState();
        if (released != null && released.holdOnly()) {
            setAbilityCooldown(released.id, released.cooldownTicks(
                    user instanceof Player player
                            && player.getAbilities().instabuild,
                    ticks));
        }
        if (released != null) {
            UltimateLifeformCombat.release(
                    user, this, released, ticks);
            if (released == UltimateLifeformSpec.GUARDIAN_LASER) {
                clearGuardianEndpoint(user);
            }
            syncOnUpdate(user);
        }
    }

    private void tickHeld(LivingEntity user) {
        if (heldAbility == null) {
            return;
        }
        UltimateLifeformSpec spec = heldAbility;
        float tickCost = spec.heldTickEnergyCost();
        // 1.16 NonStandPowerType.consumeEnergy took nothing from a creative
        // user
        boolean creative = user instanceof Player player
                && player.getAbilities().instabuild;
        if (tickCost > 0.0F
                && !creative
                && !consumeEnergy(user, tickCost)) {
            releaseHeld(user);
            return;
        }
        heldTicks++;
        UltimateLifeformCombat.tickHeld(
                user, this, spec, heldTicks);
    }

    private void tickEnergy(LivingEntity user) {
        if (energy >= MAX_ENERGY || user.isFallFlying()) {
            return;
        }
        if (stoneForm) {
            addEnergy(user, 24.0F);
        }
        else if (armorType == ArmorType.NONE
                && user.level().canSeeSky(user.blockPosition())) {
            addEnergy(user, 12.0F);
        }
    }

    private void tickHamonDecay() {
        if (hamonCooldown < 600) {
            hamonCooldown++;
        }
        else if (hamonCharge > 0) {
            hamonCharge = Math.max(hamonCharge - 100, 0);
        }
    }

    /*
     * 1.16 UltimateLifeformData.tick on the client, ratio = charge / 6000:
     * round(16 * ratio) rainbow aura particles a tick around the lower half
     * of the user, and while in water, rain or a bubble column round(3 *
     * ratio) green and core blue ones; for the camera user the aura on the
     * first-person arms too.
     */
    private void tickHamonAura(LivingEntity user) {
        float ratio = getHamonRatio();
        boolean wet = user.isInWaterRainOrBubble();
        if (wet) {
            for (int index = Math.round(3.0F * ratio); index > 0; index--) {
                spawnAura(user, UltimateLifeformAddon.HAMON_AURA_GREEN.get());
                UltimateHamonAuraClient.thirdPersonCoreBlue(user,
                        auraX(user), auraY(user), auraZ(user));
            }
        }
        for (int index = Math.round(16.0F * ratio); index > 0; index--) {
            spawnAura(user, UltimateLifeformAddon.HAMON_AURA_RAINBOW.get());
        }
        UltimateHamonAuraClient.firstPerson(user, ratio, wet);
    }

    private static void spawnAura(
            LivingEntity user, SimpleParticleType aura) {
        UltimateHamonAuraClient.thirdPerson(
                aura, user, auraX(user), auraY(user), auraZ(user));
    }

    private static double auraX(LivingEntity user) {
        return user.getX() + (user.getRandom().nextDouble() - 0.5D)
                * (user.getBbWidth() + 0.5F);
    }

    private static double auraY(LivingEntity user) {
        return user.getY() + user.getRandom().nextDouble()
                * (user.getBbHeight() * 0.5F);
    }

    private static double auraZ(LivingEntity user) {
        return user.getZ() + (user.getRandom().nextDouble() - 0.5D)
                * (user.getBbWidth() + 0.5F);
    }

    private void tickPassives(LivingEntity user) {
        if (!user.isAlive()) {
            clearHeldState();
            stoneForm = false;
            flying = false;
            // 1.16 UltimateLifeformData.tick: a dead user's armor type went
            // back to NONE, so the armor did not come back after respawning
            if (armorType != ArmorType.NONE) {
                armorType = ArmorType.NONE;
                syncOnUpdate(user);
            }
        }
        int healthLevel = Math.max(
                20 - (summonedPhantomsCount * 2
                        + summonedRabbitsCount
                        + healthDebuff),
                0);
        if (healthLevel > 0) {
            refreshHiddenEffect(
                    user,
                    UltimateLifeformAddon
                            .ULTIMATE_HEALTH_BOOST,
                    20,
                    healthLevel - 1);
        }
        else {
            removeHiddenEffect(
                    user,
                    UltimateLifeformAddon
                            .ULTIMATE_HEALTH_BOOST);
        }
        if (user instanceof Player player) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
        }
        // 1.16 tickUser / UltimateLifeformData.tick put these on every tick
        // with their icons shown (EffectInstance(..., false, false, true)):
        // Regeneration III while not burning below Y 400 among them
        if (user.getRemainingFireTicks() < 0 && user.getY() < 400.0D) {
            applyShownEffect(
                    user, MobEffects.REGENERATION, 20, 2);
        }
        if (user.isSprinting() && !user.isFallFlying()) {
            applyShownEffect(
                    user, MobEffects.MOVEMENT_SPEED, 10, 3);
            AfterimageEntity.addAfterimages(user, 3, 100);
        }
        if (user.isSwimming() || user.isInWater()) {
            applyShownEffect(
                    user, MobEffects.DOLPHINS_GRACE, 10, 4);
            applyShownEffect(
                    user, MobEffects.WATER_BREATHING, 10, 0);
        }
        if (stoneForm) {
            applyShownEffect(
                    user, ModStatusEffects.STUN, 20, 0);
            applyShownEffect(
                    user, MobEffects.DAMAGE_RESISTANCE, 40, 3);
            applyShownEffect(
                    user, MobEffects.BLINDNESS, 40, 0);
            applyShownEffect(
                    user, MobEffects.FIRE_RESISTANCE, 40, 0);
        }
        if (nightVision) {
            applyShownEffect(
                    user, MobEffects.NIGHT_VISION, 400, 0);
        }
        // 1.16 UltimateArmorEffect / UltimateFireArmorEffect.applyEffectTick:
        // a fire hit within the last 40 ticks took the armor effect off or put
        // the fire out. The armor type stayed, as it did after milk.
        DamageSource lastDamage = user.getLastDamageSource();
        if (lastDamage != null
                && (lastDamage.is(DamageTypes.IN_FIRE)
                        || lastDamage.is(DamageTypes.CAMPFIRE)
                        || lastDamage.is(DamageTypes.ON_FIRE)
                        || lastDamage.is(DamageTypes.LAVA))) {
            if (user.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)) {
                user.removeEffect(UltimateLifeformAddon.ULTIMATE_ARMOR);
            }
            if (user.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)) {
                user.clearFire();
            }
        }
        // 1.16 UltimateLifeformPowerType.tickUser: only the fire armor effect
        // came off, the armor type stayed FIRE
        if (fireDamageTaken >= 1200) {
            user.removeEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
            fireDamageTaken = 0;
        }
        // 1.16 UltimateArmorEffect / UltimateFireArmorEffect.applyEffectTick
        // set the Caelus flight to 0 on every tick the user glided with the
        // armor effect on
        if (user instanceof Player player
                && player.isFallFlying()
                && (player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                        || player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR))) {
            UltimateLifeformPowerType.disableFlight(player);
        }
        if (user.onGround()) {
            flying = false;
        }
        // 1.16 tickUser took the flight away on every tick without a glide;
        // the flag only bridges the leap until the server sees the glide, so
        // a glide that ends in the air needs a new leap
        if (flying && user.isFallFlying()) {
            glideSeenSinceLeap = true;
        }
        else if (flying && glideSeenSinceLeap) {
            flying = false;
            syncOnUpdate(user);
        }
        if (!flying) {
            glideSeenSinceLeap = false;
        }
        if (user instanceof Player player
                && !player.isFallFlying()
                && !flying) {
            UltimateLifeformPowerType.disableFlight(player);
        }
        // the fluid surface walk itself runs in Entity.move (UltimateLiquidWalking)
        waterWalking = liquidWalkedSinceTick;
        liquidWalkedSinceTick = false;
    }

    public void markLiquidWalking() {
        liquidWalkedSinceTick = true;
    }

    /*
     * 1.16 UltimateLifeformLiquidWalking.isLiquidWalking took
     * consumeHamonCharge(waterWalkingTickCost()) without a check, so the charge
     * could go below 0 until the next power tick set it back to 0; the port's
     * charge stops at 0.
     */
    public void payLiquidWalking(LivingEntity user, int cost) {
        consumeHamonCharge(Math.min(cost, hamonCharge));
        if (!user.level().isClientSide()) {
            syncOnUpdate(user);
        }
    }

    // 1.16 PlayerUtilCap.setDoubleShiftPress / tickDoubleShift: a double shift
    // press lasts until shift is let go.
    public void setDoubleShiftPress(LivingEntity user) {
        doubleShiftPress = true;
        shiftSynced = user.isShiftKeyDown();
    }

    public boolean getDoubleShiftPress() {
        return doubleShiftPress;
    }

    private void tickDoubleShift(LivingEntity user) {
        if (!doubleShiftPress) {
            return;
        }
        if (!shiftSynced) {
            if (user.isShiftKeyDown()) {
                shiftSynced = true;
            }
        }
        else if (!user.isShiftKeyDown()) {
            doubleShiftPress = false;
        }
    }

    private void tickTnt(LivingEntity user) {
        for (int i = eatenTntFuses.size() - 1;
                i >= 0;
                i--) {
            int fuse = eatenTntFuses.get(i) - 1;
            if (fuse <= 0) {
                user.level().explode(
                        user,
                        user.getX(),
                        user.getY(),
                        user.getZ(),
                        2.0F,
                        false,
                        net.minecraft.world.level.Level
                                .ExplosionInteraction.NONE);
                eatenTntFuses.remove(i);
            }
            else {
                eatenTntFuses.set(i, fuse);
            }
        }
    }

    private void tickCooldowns() {
        Iterator<Map.Entry<String, Integer>> iterator =
                abilityCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
            }
            else {
                abilityCooldownTotals.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void readCooldowns(CompoundTag cooldowns) {
        abilityCooldowns.clear();
        abilityCooldownTotals.clear();
        for (String name : cooldowns.getAllKeys()) {
            int[] values = cooldowns.getIntArray(name);
            int cooldown = values.length > 0 ? values[0] : 0;
            int total = values.length > 1
                    ? values[1]
                    : cooldown;
            if (cooldown > 0) {
                abilityCooldowns.put(name, cooldown);
                abilityCooldownTotals.put(
                        name, Math.max(total, cooldown));
            }
        }
    }

    public float getEnergy() {
        return energy;
    }

    public float getEnergyRatio() {
        return energy / MAX_ENERGY;
    }

    public boolean hasEnergy(float amount) {
        return amount <= 0.0F || energy >= amount;
    }

    public boolean consumeEnergy(
            LivingEntity user, float amount) {
        if (!hasEnergy(amount)) {
            return false;
        }
        energy = Mth.clamp(
                energy - Math.max(amount, 0.0F),
                0.0F,
                MAX_ENERGY);
        return true;
    }

    public void addEnergy(
            LivingEntity user, float amount) {
        energy = Mth.clamp(
                energy + amount, 0.0F, MAX_ENERGY);
    }

    // /jojoenergy hooks (1.16 getMaxEnergy 12000); no @Override so older cores still load this.
    public float getCommandMaxEnergy(LivingEntity user) {
        return MAX_ENERGY;
    }

    public float getCommandEnergy() {
        return energy;
    }

    public void setCommandEnergy(
            LivingEntity user, float amount) {
        energy = Mth.clamp(amount, 0.0F, MAX_ENERGY);
        syncOnUpdate(user);
    }

    public int getHamonCharge() {
        return hamonCharge;
    }

    public float getHamonRatio() {
        return (float) hamonCharge / MAX_HAMON_CHARGE;
    }

    public void addHamonCharge(int amount) {
        hamonCharge = Mth.clamp(
                hamonCharge + amount,
                0,
                MAX_HAMON_CHARGE);
        hamonCooldown = 0;
    }

    public boolean consumeHamonCharge(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (hamonCharge < amount) {
            return false;
        }
        hamonCharge -= amount;
        return true;
    }

    public PillarmanMode getPreviousMode() {
        return previousMode;
    }

    public void setPreviousMode(
            LivingEntity user, PillarmanMode mode) {
        previousMode = mode != null
                ? mode
                : PillarmanMode.NONE;
        syncOnUpdate(user);
    }

    public ArmorType getArmorType() {
        return armorType;
    }

    public void setArmorType(
            LivingEntity user, ArmorType armorType) {
        this.armorType = armorType != null
                ? armorType
                : ArmorType.NONE;
        if (this.armorType != ArmorType.NONE
                && user instanceof Player player) {
            UltimateLifeformPowerType.disableFlight(player);
            player.stopFallFlying();
            flying = false;
        }
        user.removeEffect(UltimateLifeformAddon.ULTIMATE_ARMOR);
        user.removeEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
        // 1.16 setArmorType put the effect on once, for 1000000 ticks, with
        // its icon shown; milk, a fire hit or 1200 cancelled fire hits could
        // take it off again
        if (!user.level().isClientSide()) {
            if (this.armorType == ArmorType.NORMAL) {
                user.addEffect(new MobEffectInstance(
                        UltimateLifeformAddon.ULTIMATE_ARMOR,
                        ARMOR_EFFECT_TICKS, 0, false, false, true));
            }
            else if (this.armorType == ArmorType.FIRE) {
                user.addEffect(new MobEffectInstance(
                        UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR,
                        ARMOR_EFFECT_TICKS, 0, false, false, true));
            }
        }
        syncOnUpdate(user);
    }

    public boolean isStoneForm() {
        return stoneForm;
    }

    public void toggleStoneForm(LivingEntity user) {
        stoneForm = !stoneForm;
        bladesVisible = false;
        syncOnUpdate(user);
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(
            LivingEntity user, boolean flying) {
        this.flying = flying;
        glideSeenSinceLeap = false;
        syncOnUpdate(user);
    }

    public boolean isNightVision() {
        return nightVision;
    }

    public void toggleNightVision(LivingEntity user) {
        nightVision = !nightVision;
        // 1.16 toggleNightVision took Night Vision off on every toggle; the
        // power tick puts it back while the toggle is on
        user.removeEffect(MobEffects.NIGHT_VISION);
        syncOnUpdate(user);
    }

    /*
     * 1.16 UltimateLifeformData.writeNBT kept no NightVision: a relog or a
     * server restart found the toggle off (the last 400-tick effect ran out),
     * while a death or a dimension change moved the data object and kept it.
     * The port's NBT also carries the data across a respawn, so the toggle is
     * cleared when the player logs in.
     */
    public void clearNightVisionOnLogin(LivingEntity user) {
        if (nightVision) {
            nightVision = false;
            syncOnUpdate(user);
        }
    }

    // 1.16 writeNBT kept no healthDebuff either: after a relog the maximum
    // health lost to dead summons was back
    public void clearHealthDebuffOnLogin(LivingEntity user) {
        if (healthDebuff != 0) {
            healthDebuff = 0;
            syncOnUpdate(user);
        }
    }

    public boolean isBladesVisible() {
        return bladesVisible;
    }

    public void setBladesVisible(
            LivingEntity user, boolean bladesVisible) {
        if (this.bladesVisible == bladesVisible) {
            return;
        }
        this.bladesVisible = bladesVisible;
        syncOnUpdate(user);
    }

    public boolean isWaterWalking() {
        return waterWalking;
    }

    @Nullable
    public UltimateLifeformSpec getHeldAbility() {
        return heldAbility;
    }

    public int getHeldTicks() {
        return heldTicks;
    }

    public int getHamonCooldown() {
        return hamonCooldown;
    }

    // 1.16 UltimateLifeformHamonShock.Instance.shockedTarget
    @Nullable
    public LivingEntity getShockedTarget() {
        return shockedTarget;
    }

    public void setShockedTarget(@Nullable LivingEntity target) {
        shockedTarget = target;
    }

    // 1.16 UltimateLifeformHamonBarrage.hurtCount, kept across holds
    public int countHamonBarrageHit() {
        return ++hamonBarrageHits;
    }

    public void resetHamonBarrageHits() {
        hamonBarrageHits = 0;
    }

    // 1.16 UltimateLifeformAbsoluteImmortality.effectLevel (an action field,
    // read per user here): the last Absorption step, back to 0 only when a
    // held tick heals; not saved
    public int getImmortalityLevel() {
        return immortalityLevel;
    }

    public int raiseImmortalityLevel() {
        return ++immortalityLevel;
    }

    public void resetImmortalityLevel() {
        immortalityLevel = 0;
    }

    @Nullable
    public LivingEntity getHeldTarget(
            LivingEntity user, double range) {
        LivingEntity target = heldTarget;
        if (target == null
                || target.level() != user.level()
                || !target.isAlive()
                || target == user
                || user.isAlliedTo(target)
                || target.distanceToSqr(user) > range * range) {
            return null;
        }
        return target;
    }

    public boolean markActionHit(LivingEntity target) {
        return target != null && actionHitEntities.add(target.getUUID());
    }

    public boolean isWallClimbing() {
        return wallClimbing;
    }

    public Direction getWallFace() {
        return wallFace;
    }

    public boolean isWallMoving() {
        return wallMoving;
    }

    public int getWallClimbTicks() {
        return wallClimbTicks;
    }

    public void startWallClimbing(
            LivingEntity user, BlockPos blockPos, Direction face) {
        if (face == null || face.getAxis() == Direction.Axis.Y) {
            return;
        }
        // 1.16 UltimateLifeformWallClimb.perform: nothing happens on the wall
        // the climber is already on
        if (wallClimbing && wallFace == face) {
            return;
        }
        Vec3 grip = Vec3.atLowerCornerOf(
                face.getOpposite().getNormal()).scale(0.5D);
        Vec3 collided = HamonWallClimbingHelper.collide(
                user, user.getBoundingBox(), grip, true);
        if (collided.subtract(grip).lengthSqr() < 1.0E-7D) {
            return;
        }
        Vec3 outward = Vec3.atLowerCornerOf(face.getNormal())
                .scale(user.getBbWidth() * 0.15D);
        Vec3 moveTo = user.position().add(collided).add(outward);
        user.teleportTo(moveTo.x, moveTo.y, moveTo.z);
        climbLimitHeadRot(user, face);
        if (user instanceof Player player) {
            player.displayClientMessage(Component.translatable(
                    "jojo.message.wall_climb.hint_jump",
                    Component.keybind("key.jump")), true);
        }
        wallClimbing = true;
        wallFace = face;
        wallMoving = false;
        wallClimbTicks = 0;
        wallInputStrafe = 0.0F;
        wallInputForward = 0.0F;
        wallInputJump = false;
        lastWallInputGameTime = Long.MIN_VALUE;
        user.fallDistance = 0.0F;
        syncOnUpdate(user);
    }

    /**
     * 1.16 LivingWallClimbing.climbLimitPlayerHeadRot: the body turns to the
     * negated climb rotation, which faces the wall, and the head stays within
     * 75 degrees of it.
     */
    public static void climbLimitHeadRot(LivingEntity user, Direction face) {
        float bodyYRot = face.toYRot() - 180.0F;
        user.setYBodyRot(bodyYRot);
        user.yBodyRotO = user.yBodyRot;
        float turn = Mth.wrapDegrees(user.getYRot() - bodyYRot);
        float limited = Mth.clamp(turn, -75.0F, 75.0F);
        user.yRotO += limited - turn;
        user.setYRot(user.getYRot() + limited - turn);
        user.setYHeadRot(user.getYRot());
    }

    public void stopWallClimbing(LivingEntity user) {
        if (!wallClimbing) {
            return;
        }
        wallClimbing = false;
        wallMoving = false;
        wallClimbTicks = 0;
        wallInputStrafe = 0.0F;
        wallInputForward = 0.0F;
        wallInputJump = false;
        lastWallInputGameTime = Long.MIN_VALUE;
        if (!user.level().isClientSide()) {
            syncOnUpdate(user);
        }
    }

    public void acceptWallInput(
            LivingEntity user,
            float strafe,
            float forward,
            boolean jump) {
        if (!wallClimbing
                || !Float.isFinite(strafe)
                || !Float.isFinite(forward)) {
            return;
        }
        wallInputStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        wallInputForward = Mth.clamp(forward, -1.0F, 1.0F);
        wallInputJump = jump;
        lastWallInputGameTime = user.level().getGameTime();
    }

    /**
     * 1.16 UltimateLifeformWallClimb.travelWallClimb: while the climb holds, this moves
     * the player instead of vanilla travel (no gravity, no input acceleration). Returns
     * true when vanilla travel must be skipped. The client passes its own travel vector
     * and jump key; the server uses the input the client reported.
     */
    public boolean travelWallClimb(
            Player user, Vec3 travelVector, boolean localJump) {
        if (!wallClimbing) {
            return false;
        }
        user.fallDistance = 0.0F;
        if (!canKeepWallClimbing(user)) {
            stopWallClimbing(user);
            return false;
        }
        float strafe;
        float forward;
        boolean jump;
        if (user.level().isClientSide()) {
            strafe = (float) travelVector.x;
            forward = (float) travelVector.z;
            jump = localJump;
        }
        else {
            long gameTime = user.level().getGameTime();
            boolean packetFresh =
                    gameTime - lastWallInputGameTime <= 5L;
            strafe = packetFresh ? wallInputStrafe : user.xxa;
            forward = packetFresh ? wallInputForward : user.zza;
            jump = packetFresh && wallInputJump;
        }
        Vec3 movement = resolveWallMovement(user, strafe, forward, jump);
        if (movement == null) {
            return false;
        }
        user.setDeltaMovement(movement);
        user.move(MoverType.SELF, movement);
        user.calculateEntityAnimation(false);
        user.fallDistance = 0.0F;
        return true;
    }

    // 1.16 MCUtil.itemHandFree: an empty hand or open-finger gloves.
    private static boolean canKeepWallClimbing(LivingEntity user) {
        return user.isAlive()
                && !(user instanceof Player player && player.isSpectator())
                && UtilFunctions.itemHandFree(user.getMainHandItem())
                && UtilFunctions.itemHandFree(user.getOffhandItem());
    }

    // 1.16 tickWallClimbing is empty for the Ultimate Lifeform: the climb
    // costs no energy and has no time limit.
    private void tickWallClimbing(LivingEntity user) {
        if (!wallClimbing) {
            return;
        }
        if (!canKeepWallClimbing(user)) {
            stopWallClimbing(user);
            return;
        }
        long gameTime = user.level().getGameTime();
        boolean packetFresh = gameTime - lastWallInputGameTime <= 5L;
        float strafe = packetFresh ? wallInputStrafe : user.xxa;
        float forward = packetFresh ? wallInputForward : user.zza;
        boolean jump = packetFresh && wallInputJump;
        applyWallMovement(user, strafe, forward, jump);
    }

    private void applyWallMovement(
            LivingEntity user,
            float strafe,
            float forward,
            boolean jump) {
        Vec3 movement = resolveWallMovement(user, strafe, forward, jump);
        if (movement == null) {
            return;
        }
        wallMoving = movement.lengthSqr() > 1.0E-7D;
        wallClimbTicks++;
        user.setDeltaMovement(movement);
        user.hurtMarked = true;
        user.fallDistance = 0.0F;
    }

    @Nullable
    private Vec3 resolveWallMovement(
            LivingEntity user,
            float strafe,
            float forward,
            boolean jump) {
        strafe = Mth.clamp(strafe, -1.0F, 1.0F);
        forward = Mth.clamp(forward, -1.0F, 1.0F);
        Vec3 grip = Vec3.atLowerCornerOf(
                wallFace.getOpposite().getNormal()).scale(0.5D);
        if (!hasWallGrip(user, user.getBoundingBox(), grip)) {
            stopWallClimbing(user);
            return null;
        }
        double climbSpeed = 2.0D * user.getAttributeValue(
                Attributes.MOVEMENT_SPEED);
        Vec3 localMovement = new Vec3(
                strafe * 0.5D,
                forward > 0.0F ? forward : forward * 0.5D,
                0.0D);
        if (localMovement.lengthSqr() > 1.0D) {
            localMovement = localMovement.normalize();
        }
        if (Math.abs(strafe) > 1.0E-4F
                && Math.abs(forward) > 1.0E-4F) {
            climbSpeed *= forward > 0.0F
                    ? 1.0D / Math.sqrt(1.25D)
                    : 0.5D / Math.sqrt(0.5D);
        }
        float climbYRot = (180.0F - wallFace.toYRot())
                * Mth.DEG_TO_RAD;
        Vec3 movement = localMovement.scale(climbSpeed)
                .yRot(climbYRot);
        Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
        // 1.16 travelWallClimb grips with the upper half of the body: a
        // descent that reaches the ground ends the climb, and the climber
        // can pull up once the wall ends below its head.
        AABB gripBox = user.getBoundingBox().contract(
                0.0D, -user.getBbHeight() * 0.5D, 0.0D);
        boolean canPullUp = false;
        if (movement.y < 0.0D) {
            if (user.onGround()) {
                stopWallClimbing(user);
                return null;
            }
            if (!hasWallGrip(
                    user,
                    gripBox.move(0.0D,
                            -gripBox.getYsize() + movement.y, 0.0D),
                    grip)) {
                movement = horizontal;
            }
        }
        if (!hasWallGrip(
                user,
                gripBox.move(0.0D, gripBox.getYsize(), 0.0D),
                grip)) {
            if (movement.y > 0.0D) {
                movement = horizontal;
            }
            canPullUp = true;
        }
        if (horizontal.lengthSqr() > 1.0E-7D
                && !hasWallGrip(
                        user,
                        gripBox.move(horizontal.normalize().scale(
                                horizontal.length()
                                        + user.getBbWidth() + 0.1D)),
                        grip)) {
            movement = new Vec3(0.0D, movement.y, 0.0D);
        }
        if (jump) {
            stopWallClimbing(user);
            // Space pulls up at the ledge; below it, it only lets go
            if (canPullUp) {
                user.move(MoverType.SELF,
                        new Vec3(0.0D, user.getBbHeight(), 0.0D));
                user.setDeltaMovement(grip.normalize().scale(0.1D));
            }
            return null;
        }
        return movement;
    }

    private static boolean hasWallGrip(
            LivingEntity user, AABB box, Vec3 grip) {
        Vec3 collided = HamonWallClimbingHelper.collide(
                user, box, grip, true);
        return collided.subtract(grip).lengthSqr() >= 1.0E-7D;
    }

    public void setGuardianEndpoint(
            UltimateProjectileEntity endpoint) {
        guardianEndpointUuid = endpoint != null
                ? endpoint.getUUID()
                : null;
    }

    @Nullable
    public UltimateProjectileEntity getGuardianEndpoint(
            ServerLevel level) {
        if (guardianEndpointUuid == null) {
            return null;
        }
        net.minecraft.world.entity.Entity entity =
                level.getEntity(guardianEndpointUuid);
        return entity instanceof UltimateProjectileEntity projectile
                && projectile.getType()
                        == UltimateLifeformAddon.LASER_END.get()
                ? projectile
                : null;
    }

    public void clearGuardianEndpoint(LivingEntity user) {
        if (guardianEndpointUuid != null
                && user.level() instanceof ServerLevel level) {
            net.minecraft.world.entity.Entity entity =
                    level.getEntity(guardianEndpointUuid);
            if (entity != null) {
                entity.discard();
            }
        }
        guardianEndpointUuid = null;
    }

    public boolean isAbilityOnCooldown(String name) {
        return abilityCooldowns.getOrDefault(name, 0) > 0;
    }

    public int getAbilityCooldownTicks(String name) {
        return abilityCooldowns.getOrDefault(name, 0);
    }

    public int getAbilityCooldownTotalTicks(String name) {
        return abilityCooldownTotals.getOrDefault(name, 0);
    }

    public Map<String, Integer> getAbilityCooldownsSnapshot() {
        return Map.copyOf(abilityCooldowns);
    }

    public long getReceivedSyncCount() {
        return receivedSyncCount;
    }

    public boolean wasLastSyncSentToTracking() {
        return lastSyncSentToTracking;
    }

    public void setAbilityCooldown(
            String name, int cooldown) {
        if (cooldown <= 0) {
            return;
        }
        abilityCooldowns.put(name, cooldown);
        abilityCooldownTotals.put(name, cooldown);
    }

    // core hook: entering Creative clears every cooldown (1.16 resetCooldowns).
    // No @Override so the add-on still loads on cores without the hook.
    public void resetAbilityCooldowns(LivingEntity user) {
        if (abilityCooldowns.isEmpty()
                && abilityCooldownTotals.isEmpty()) {
            return;
        }
        abilityCooldowns.clear();
        abilityCooldownTotals.clear();
        syncOnUpdate(user);
    }

    @Override
    public float getAbilityCooldownRatio(
            String abilityName, float partialTick) {
        int cooldown =
                abilityCooldowns.getOrDefault(abilityName, 0);
        int total =
                abilityCooldownTotals.getOrDefault(
                        abilityName, cooldown);
        return total > 0
                ? Mth.clamp(
                        (cooldown - partialTick) / total,
                        0.0F,
                        1.0F)
                : 0.0F;
    }

    public void addFireDamageCount() {
        fireDamageTaken++;
    }

    public int getFireDamageCount() {
        return fireDamageTaken;
    }

    public int getSummonedRabbitsCount() {
        return summonedRabbitsCount;
    }

    public int getSummonedPhantomsCount() {
        return summonedPhantomsCount;
    }

    public int getSummonedRabbitsLifetime() {
        return summonedRabbitsLifetime;
    }

    public int getSummonedPhantomsLifetime() {
        return summonedPhantomsLifetime;
    }

    public int getHealthDebuff() {
        return healthDebuff;
    }

    public void setSummonedRabbits(
            int count, int lifetime) {
        summonedRabbitsCount = Math.max(count, 0);
        summonedRabbitsLifetime = Math.max(lifetime, 0);
    }

    public void setSummonedPhantoms(
            int count, int lifetime) {
        summonedPhantomsCount = Math.max(count, 0);
        summonedPhantomsLifetime = Math.max(lifetime, 0);
    }

    // 1.16: each summon wrote its age into the data on every tick and took
    // itself off the count when it vanished at the owner (no debuff)
    public void setSummonedRabbitsLifetime(int lifetime) {
        summonedRabbitsLifetime = Math.max(lifetime, 0);
    }

    public void setSummonedPhantomsLifetime(int lifetime) {
        summonedPhantomsLifetime = Math.max(lifetime, 0);
    }

    public void summonedRabbitReturned() {
        summonedRabbitsCount = Math.max(summonedRabbitsCount - 1, 0);
    }

    public void summonedPhantomReturned() {
        summonedPhantomsCount = Math.max(summonedPhantomsCount - 1, 0);
    }

    public void summonedRabbitDied() {
        summonedRabbitsCount =
                Math.max(summonedRabbitsCount - 1, 0);
        healthDebuff++;
    }

    public void summonedPhantomDied() {
        summonedPhantomsCount =
                Math.max(summonedPhantomsCount - 1, 0);
        healthDebuff += 2;
    }

    public void clearSummons() {
        summonedRabbitsCount = 0;
        summonedPhantomsCount = 0;
        summonedRabbitsLifetime = 0;
        summonedPhantomsLifetime = 0;
        healthDebuff = 0;
    }

    private void clearHeldState() {
        heldAbility = null;
        heldTarget = null;
        shockedTarget = null;
        heldTicks = 0;
        bladesVisible = false;
        actionHitEntities.clear();
    }

    private static void applyMovementModifier(
            LivingEntity user) {
        AttributeInstance speed =
                user.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null
                && !speed.hasModifier(MOVEMENT_MODIFIER_ID)) {
            speed.addTransientModifier(MOVEMENT_MODIFIER);
        }
    }

    private static void removeMovementModifier(
            LivingEntity user) {
        AttributeInstance speed =
                user.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MOVEMENT_MODIFIER_ID);
        }
    }

    private static void applyAttackModifier(
            LivingEntity user) {
        AttributeInstance attack =
                user.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null
                && !attack.hasModifier(ATTACK_MODIFIER_ID)) {
            attack.addTransientModifier(ATTACK_MODIFIER);
        }
    }

    private static void removeAttackModifier(
            LivingEntity user) {
        AttributeInstance attack =
                user.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(ATTACK_MODIFIER_ID);
        }
    }

    private static void refreshHiddenEffect(
            LivingEntity user,
            net.minecraft.core.Holder<MobEffect> effect,
            int duration,
            int amplifier) {
        MobEffectInstance current = user.getEffect(effect);
        if (current == null
                || current.getDuration() < 5
                || current.getAmplifier() != amplifier) {
            user.addEffect(new MobEffectInstance(
                    effect,
                    duration,
                    amplifier,
                    false,
                    false,
                    false));
        }
    }

    private static void applyShownEffect(
            LivingEntity user,
            net.minecraft.core.Holder<MobEffect> effect,
            int duration,
            int amplifier) {
        user.addEffect(new MobEffectInstance(
                effect, duration, amplifier, false, false, true));
    }

    private static void removeHiddenEffect(
            LivingEntity user,
            net.minecraft.core.Holder<MobEffect> effect) {
        MobEffectInstance current = user.getEffect(effect);
        if (current != null
                && !current.isVisible()
                && !current.showIcon()) {
            user.removeEffect(effect);
        }
    }

    private static void removeOwnedEffects(
            LivingEntity user) {
        removeHiddenEffect(
                user,
                UltimateLifeformAddon
                        .ULTIMATE_HEALTH_BOOST);
        // 1.16 UltimateLifeformPowerType.onClear
        user.removeEffect(MobEffects.NIGHT_VISION);
        user.removeEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
        user.removeEffect(UltimateLifeformAddon.ULTIMATE_ARMOR);
    }

    private static <E extends Enum<E>> E parseEnum(
            Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        }
        catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
