package rotp.addons.ultimate_lifeform;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModParticles;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.HamonSpreadEffect;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.network.s2c.TrHamonParticlesPacket;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;

/**
 * 1.16 UltimateHamonCharge: the Ultimate Lifeform's own Hamon charge, apart from the core's HamonCharge. It hurts with
 * KarsUtil.dealUltimateHamonDamage, gives no Hamon points, and its infuser shocks for 60 ticks while it has the
 * Ultimate Lifeform power.
 */
public final class UltimateHamonCharge {
    private static final float KNOCKBACK = 0.75F;
    private static final int SHOCK_TICKS = 60;
    private static final float[] DAMAGE_FOR_HAMON_SPREAD_EFFECT = { 5.0F, 12.5F, 25.0F, 50.0F, 125.0F };
    // the core's Hamon hits add to the same accumulator, as 1.16 LivingUtilCap kept one for both
    private static final String HAMON_SPREAD_DAMAGE_TAG = "jojo_ripples.hamon_spread_damage";
    private static final String HAMON_SPREAD_DAMAGE_TICK_TAG = "jojo_ripples.hamon_spread_damage_tick";

    private final float damage;
    private final int chargeTicksInitial;
    private int chargeTicks;
    @Nullable private UUID hamonUserId;
    @Nullable private Entity hamonUser;
    private boolean doLargeChargeDmg = true;
    private final float energySpent;

    public UltimateHamonCharge(float damage, int chargeTicks, @Nullable LivingEntity hamonUser, float energySpent) {
        this.damage = damage;
        this.chargeTicksInitial = chargeTicks;
        this.chargeTicks = chargeTicks;
        if (hamonUser != null) {
            this.hamonUserId = hamonUser.getUUID();
        }
        this.energySpent = energySpent;
    }

    public float getDamage() {
        return damage;
    }

    public void tick(@Nullable Entity chargedEntity, @Nullable BlockPos chargedBlock, Level level, AABB aabb) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, aabb, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            if (target.is(chargedEntity) || !target.isAlive() || target.getUUID().equals(hamonUserId)) {
                continue;
            }
            Entity user = getUser(serverLevel);
            float dmgAmount = doLargeChargeDmg ? damage : damage * 0.1F;
            if (!dealUltimateHamonDamage(target, dmgAmount, chargedEntity, chargedEntity instanceof LivingEntity ? null : user)) {
                continue;
            }
            if (!target.isAlive() && user instanceof ServerPlayer player) {
                ModCriteriaTriggers.triggerHamonChargeKill(player, target, chargedEntity);
            }
            Vec3 chargePos = chargedBlock != null ? Vec3.atCenterOf(chargedBlock)
                    : chargedEntity != null ? chargedEntity.getBoundingBox().getCenter() : null;
            if (chargePos != null && doLargeChargeDmg) {
                // KarsUtil.emitUltimateHamonSparkParticles at intensity 4
                serverLevel.sendParticles(UltimateLifeformAddon.RAINBOW_SPARK.get(),
                        chargePos.x, chargePos.y, chargePos.z, 66, 0.05D, 0.05D, 0.05D, 0.25D);
                // CustomParticles.createHamonSparkParticles(null, chargePos, 4), which 1.16 called on the server thread
                // and only an integrated server's own client drew
                serverLevel.sendParticles(ModParticles.HAMON_SPARK.get(),
                        chargePos.x, chargePos.y, chargePos.z, 4, 0.05D, 0.05D, 0.05D, 0.25D);
            }
            if (doLargeChargeDmg) {
                if (chargePos != null) {
                    knockbackFromCharge(target, chargePos);
                }
                if (user instanceof LivingEntity userLiving
                        && PlayerPower.getPowerData(userLiving, UltimateLifeformAddon.ULTIMATE_LIFEFORM).isPresent()
                        && chargedEntity instanceof LivingEntity
                        && HamonUtil.isLiving(target)
                        && !ModStatusEffects.isStunned(target)) {
                    target.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SHOCK, SHOCK_TICKS, 0, false, false));
                }
            }
            if (chargedBlock != null) {
                if (doLargeChargeDmg && !level.getBlockState(chargedBlock).is(Blocks.COBWEB)) {
                    chargeTicks = 0;
                }
            }
            else if (chargedEntity != null) {
                doLargeChargeDmg = false;
            }
            else {
                chargeTicks = 0;
            }
        }
        --chargeTicks;
    }

    // 1.16 knockback(0.75, ...) towards the charge; a zero direction halved the speed and made a grounded target hop,
    // where 1.21 would pick a random direction
    private static void knockbackFromCharge(LivingEntity target, Vec3 chargePos) {
        Vec3 direction = new Vec3(chargePos.x - target.getX(), 0.0D, chargePos.z - target.getZ()).normalize();
        if (direction.lengthSqr() > 0.0D) {
            target.knockback(KNOCKBACK, direction.x, direction.z);
            return;
        }
        LivingKnockBackEvent event = CommonHooks.onLivingKnockBack(target, KNOCKBACK, 0.0D, 0.0D);
        if (event.isCanceled()) {
            return;
        }
        double strength = event.getStrength() * (1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (strength > 0.0D) {
            target.hasImpulse = true;
            Vec3 speed = target.getDeltaMovement();
            Vec3 push = new Vec3(event.getRatioX(), 0.0D, event.getRatioZ()).normalize().scale(strength);
            target.setDeltaMovement(speed.x / 2.0D - push.x,
                    target.onGround() ? Math.min(0.4D, speed.y / 2.0D + strength) : speed.y,
                    speed.z / 2.0D - push.z);
        }
    }

    /**
     * 1.16 KarsUtil.dealUltimateHamonDamage with noSrcEntityHamonMultiplier: the core's Hamon damage rules with the
     * rainbow spark, and Hamon spread on every hit that lands on an undead target.
     */
    public static boolean dealUltimateHamonDamage(
            LivingEntity target, float amount, @Nullable Entity srcDirect, @Nullable Entity srcIndirect) {
        if (target.getExistingData(ModDataAttachmentTypes.HAMON_CHARGE).map(EntityHamonChargeState::hasHamonCharge).orElse(false)
                || PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).map(PillarmanData::isStoneFormEnabled).orElse(false)) {
            return false;
        }
        boolean scarf = target.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get());
        if (scarf) {
            if (PlayerPower.getPowerData(target, ModPlayerPowers.HAMON).isPresent()) {
                return false;
            }
            amount *= 0.25F;
        }
        // 1.16 used the anonymous DamageUtil.HAMON without a direct entity and dropped the indirect one, so a charged
        // block hits in nobody's name
        DamageSource source = HamonAbilityHelpers.hamonDamageSource(
                target.level(), srcDirect, srcDirect != null ? srcIndirect : null);
        if (srcDirect == null && source instanceof DamageSourceModified modified) {
            // 1.16 hurt() knocked back only from a source entity; 1.21 pushes in a random direction without one
            modified.jojo_ripples$modifyKnockback(0.0F, 0.0F);
        }
        boolean undeadTarget = JojoDefinitions.isAffectedByHamon(target);
        if (!undeadTarget) {
            amount *= 0.2F;
        }
        else if (PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isPresent()) {
            amount *= 0.5F;
        }
        float spreadAmount = amount;
        amount *= HamonAbilityHelpers.configHamonDamageMultiplier();
        if (!DamageUtil.hurtThroughInvulTicks(target, source, amount)) {
            return false;
        }
        TrHamonParticlesPacket emitter = TrHamonParticlesPacket.emitter(target,
                amount / (HamonData.MAX_HAMON_STRENGTH_MULTIPLIER * 5.0F), 1.0F, UltimateLifeformAddon.RAINBOW_SPARK.get());
        if (emitter != null) {
            TrHamonParticlesPacket.send(target, emitter);
        }
        if (scarf && undeadTarget && target instanceof ServerPlayer player) {
            ModCriteriaTriggers.triggerVampireHamonDamageScarf(player);
        }
        if (undeadTarget) {
            hamonSpread(target, spreadAmount);
        }
        return true;
    }

    /**
     * 1.16 DamageUtil.HAMON: the plain Hamon source naming no entity, so hurt() pushed nothing (the core takes 1.21's
     * random push of such a hit out for its no_sourceless_knockback types).
     */
    public static DamageSource plainHamonSource(Level level) {
        return HamonAbilityHelpers.hamonDamageSource(level, null, null);
    }

    private static void hamonSpread(LivingEntity target, float damageReceived) {
        CompoundTag data = target.getPersistentData();
        long gameTime = target.level().getGameTime();
        float received = data.getFloat(HAMON_SPREAD_DAMAGE_TAG);
        if (data.contains(HAMON_SPREAD_DAMAGE_TICK_TAG)) {
            long elapsedTicks = Math.max(gameTime - data.getLong(HAMON_SPREAD_DAMAGE_TICK_TAG), 0L);
            received = Math.max(received - elapsedTicks * 0.1F, 0.0F);
        }
        received += damageReceived;
        data.putFloat(HAMON_SPREAD_DAMAGE_TAG, received);
        data.putLong(HAMON_SPREAD_DAMAGE_TICK_TAG, gameTime);
        for (int i = DAMAGE_FOR_HAMON_SPREAD_EFFECT.length - 1; i >= 0; i--) {
            if (received >= DAMAGE_FOR_HAMON_SPREAD_EFFECT[i]) {
                HamonSpreadEffect.giveEffectTo(target, 60 + 40 * i, i);
                break;
            }
        }
    }

    @Nullable
    public Entity getUser(Level level) {
        if (hamonUser == null && hamonUserId != null && level instanceof ServerLevel serverLevel) {
            hamonUser = serverLevel.getEntity(hamonUserId);
        }
        return hamonUser;
    }

    public boolean shouldBeRemoved() {
        return chargeTicks < 0;
    }

    public void setTicks(int ticks) {
        chargeTicks = ticks;
    }

    public void decreaseTicks(int ticks) {
        setTicks(chargeTicks - ticks);
    }

    public int getTicks() {
        return chargeTicks;
    }

    public int getInitialTicks() {
        return chargeTicksInitial;
    }

    public static UltimateHamonCharge fromNBT(CompoundTag nbt) {
        UltimateHamonCharge charge = new UltimateHamonCharge(
                nbt.getFloat("ULFCharge"), nbt.getInt("ULFChargeTicksInitial"), null, nbt.getFloat("ULFEnergySpent"));
        charge.chargeTicks = nbt.getInt("ULFChargeTicks");
        if (nbt.hasUUID("ULFUser")) {
            charge.hamonUserId = nbt.getUUID("ULFUser");
        }
        charge.doLargeChargeDmg = nbt.getBoolean("ULFLargeDmg");
        return charge;
    }

    public CompoundTag toNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("ULFCharge", damage);
        nbt.putInt("ULFChargeTicksInitial", chargeTicksInitial);
        nbt.putInt("ULFChargeTicks", chargeTicks);
        if (hamonUserId != null) {
            nbt.putUUID("ULFUser", hamonUserId);
        }
        nbt.putFloat("ULFEnergySpent", energySpent);
        nbt.putBoolean("ULFLargeDmg", doLargeChargeDmg);
        return nbt;
    }
}
