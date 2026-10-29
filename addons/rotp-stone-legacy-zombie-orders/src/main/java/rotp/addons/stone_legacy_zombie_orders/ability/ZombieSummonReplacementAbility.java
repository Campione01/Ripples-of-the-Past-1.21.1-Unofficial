package rotp.addons.stone_legacy_zombie_orders.ability;

import java.util.List;

import rotp.core.api.playerpower.PlayerPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.init.ModCustomStats;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.impl.powers.zombie.ZombieData;

import rotp.addons.stone_legacy_zombie_orders.ZombieOwnership;
import rotp.addons.stone_legacy_zombie_orders.ZombieOrdersAddon;
import rotp.addons.stone_legacy_zombie_orders.ZombiePowerEnergy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class ZombieSummonReplacementAbility
        extends VampirismActionAbility {
    private static final float BLOOD_COST = 100.0F;

    public ZombieSummonReplacementAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                3,
                BLOOD_COST,
                SummonInstance::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        // The core moveset adds vampirism_zombie_summon with the
        // VampirismPowerType::utility customizer; replaceAbility drops it.
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public String getResourceNamespace() {
        return JojoMod.MOD_ID;
    }

    @Override
    public void setCooldownOnUse(Power<?> context) {
        setVampirismCooldown(context, 100, 100);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (user.level().getDifficulty()
                == Difficulty.PEACEFUL) {
            return ConditionCheck.createNegative("peaceful");
        }
        if (zombieLimitReached(user)) {
            return ConditionCheck.createNegative(
                    "zombies_limit");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class SummonInstance
            extends EntityActionInstance {
        public SummonInstance(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null
                    || zombieLimitReached(user)
                    || !consumeBlood(user, BLOOD_COST)) {
                return;
            }

            int requested =
                    level.getDifficulty().getId();
            int converted = convertDyingEntities(
                    level, user, requested);
            int spawned = spawnHungryZombies(
                    level, user, requested - converted);
            if (spawned > 0
                    && user instanceof ServerPlayer player) {
                player.awardStat(
                        Stats.CUSTOM.get(
                                ModCustomStats
                                        .VAMPIRE_ZOMBIES_SUMMONED),
                        spawned);
            }
        }
    }

    private static int convertDyingEntities(
            ServerLevel level,
            LivingEntity owner,
            int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<LivingEntity> candidates =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        owner.getBoundingBox().inflate(16.0D),
                        candidate -> candidate != owner
                                && candidate.deathTime >= 1);
        int converted = 0;
        for (LivingEntity candidate : candidates) {
            if (converted >= limit) {
                break;
            }
            if (ZombieOrdersAddon.conversion(candidate)
                    .isConverted()) {
                continue;
            }
            // 1.16 converted only entities with the non-stand power
            // capability: the core gave it to players alone, and other
            // mobs got it only from Stand-Bearing Creatures, whose port
            // attaches PlayerPower to its eligible mobs.
            PlayerPower power = candidate instanceof Player
                    ? PowerClass.PLAYER_POWER.attachGet(candidate)
                    : PlayerPower.get(candidate);
            if (power == null
                    || ZombiePowerEnergy.blocksZombieConversion(power)) {
                continue;
            }
            ZombiePowerEnergy.Energy previousEnergy =
                    ZombiePowerEnergy.read(power, candidate);
            boolean noPreviousPower = power.getPowerType() == null;
            PlayerPowerTransitions.Snapshot snapshot = null;
            if (noPreviousPower) {
                power.setPowerType(ModPlayerPowers.ZOMBIE.get());
            }
            else {
                snapshot = PlayerPowerTransitions.forceTemporary(
                                power, ModPlayerPowers.ZOMBIE.get())
                        .orElse(null);
                if (snapshot == null) {
                    continue;
                }
            }
            ZombieData zombie = power
                    .getCurTypeData(ModPlayerPowers.ZOMBIE)
                    .orElse(null);
            if (zombie == null) {
                if (noPreviousPower) {
                    power.setPowerType(null);
                }
                continue;
            }

            zombie.setEnergy(
                    candidate,
                    previousEnergy
                            .convertedZombieEnergy());
            zombie.syncOnUpdate(candidate);
            if (noPreviousPower) {
                ZombieOwnership.markConvertedFromNoPower(
                        candidate, owner);
            }
            else {
                ZombieOwnership.markConverted(
                        candidate, owner, snapshot);
            }
            resurrectKeepingExperience(candidate);
            candidate.setHealth(2.0F);
            converted++;
        }
        return converted;
    }

    // 1.16 revived through VampireActionMixin's own onEntityResurrect,
    // which only reset the death timer and, unlike the core rule, kept
    // a player's experience. The core call still resets the client's
    // death timer; the levels it zeroes are put back.
    private static void resurrectKeepingExperience(
            LivingEntity candidate) {
        if (!(candidate instanceof ServerPlayer player)) {
            JojoModUtil.onLivingResurrect(candidate);
            return;
        }
        int levels = player.experienceLevel;
        float progress = player.experienceProgress;
        JojoModUtil.onLivingResurrect(candidate);
        player.setExperienceLevels(levels);
        player.experienceProgress = progress;
    }

    private static int spawnHungryZombies(
            ServerLevel level,
            LivingEntity owner,
            int count) {
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            HungryZombieEntity zombie =
                    new HungryZombieEntity(level);
            zombie.setSummonedFromAbility();
            zombie.copyPosition(owner);
            ZombieOwnership.trackHungryZombie(zombie, owner);
            if (level.addFreshEntity(zombie)) {
                spawned++;
            }
            else {
                ZombieOrdersAddon.roster(owner)
                        .remove(zombie.getUUID());
            }
        }
        return spawned;
    }

    private static boolean zombieLimitReached(
            LivingEntity user) {
        Level level = user.level();
        int limit = level.getDifficulty().getId() * 10;
        if (limit <= 0) {
            return true;
        }
        AABB area = new AABB(
                user.getX(),
                level.getMinBuildHeight(),
                user.getZ(),
                user.getX(),
                level.getMaxBuildHeight(),
                user.getZ())
                .inflate(16.0D, 0.0D, 16.0D);
        return level.getEntitiesOfClass(
                        HungryZombieEntity.class, area)
                .size() > limit;
    }
}
