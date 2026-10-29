package rotp.addons.stand_bearing_creatures.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabThrowAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchInput;
import rotp.core.impl.stands.crazydiamond.CrazyDLeaveObjectPunchInput;
import rotp.core.impl.stands.crazydiamond.CrazyDMisshapingPunchInput;
import rotp.core.impl.stands.goldexperience.GoldExperienceEntityLifeshotAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceToothLifeformAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.entity.goal.ManualHeldInput;

public final class MobPowerUtil {
    public static final TagKey<EntityType<?>> POWER_AI_EXCLUDED =
            TagKey.create(
                    Registries.ENTITY_TYPE,
                    MobsWithPowersAddon.id("power_ai_excluded"));
    public static final String PROCESSED_TAG = "rotp_mwp:processed";
    public static final String STAND_HELD_KEY_TAG =
            "rotp_mwp:stand_held_key";
    public static final String PLAYER_POWER_HELD_KEY_TAG =
            "rotp_mwp:player_power_held_key";
    public static final String DEBUG_AGGRO_FIRST_TAG =
            "rotp_mwp:debug_aggro_first";

    private static final ResourceLocation SUMMON_STAND_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "jojo/summon_stand");
    private static final Set<String> CORE_BLOCKED_MOB_ABILITIES = Set.of(
            "jojo_ripples:enhanced_eyesight",
            "jojo_ripples:repair_item",
            "jojo_ripples:block_anchor",
            "jojo_ripples:uncraft_item");
    private static final List<String> UNSUPPORTED_INPUT_TOKENS = List.of(
            "choose",
            "select",
            "inventory",
            "repair_item",
            "block_anchor",
            "enhanced_eyesight",
            "skin",
            "control_scheme");

    public static boolean isEligibleMob(Entity entity) {
        if (!(entity instanceof Mob mob)
                || entity instanceof StandEntity
                || entity instanceof EnderDragon
                || entity.getType().is(POWER_AI_EXCLUDED)) {
            return false;
        }
        return isEntityListEligible(mob);
    }

    private static boolean isEntityListEligible(Entity entity) {
        ResourceLocation typeId =
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) {
            return false;
        }
        boolean listed = MobPowerConfig.ENTITY_LIST.get()
                .contains(typeId.toString());
        return MobPowerConfig.USE_ENTITY_WHITELIST.get()
                ? listed
                : !listed;
    }

    public static boolean isStandEntityPowerEligible(Entity entity) {
        return entity instanceof StandEntity
                && MobPowerConfig.SMALL_ANARCHY_WITH_STANDS.get()
                && isEntityListEligible(entity);
    }

    public static boolean isDebugPowerTarget(LivingEntity entity) {
        return entity instanceof Player
                || isEligibleMob(entity)
                || isStandEntityPowerEligible(entity);
    }

    public static void attachPowers(Mob mob) {
        attachPowers((LivingEntity) mob);
    }

    public static void attachPowers(LivingEntity entity) {
        PowerClass.STAND.attachPower(entity);
        PowerClass.PLAYER_POWER.attachPower(entity);
    }

    public static boolean attachDebugPowers(LivingEntity entity) {
        if (!isDebugPowerTarget(entity)) {
            return false;
        }
        attachPowers(entity);
        return StandPower.get(entity) != null
                && PlayerPower.get(entity) != null;
    }

    public static boolean hasStand(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.hasPower();
    }

    public static boolean hasPlayerPower(LivingEntity entity) {
        PlayerPower power = PlayerPower.get(entity);
        return power != null && power.hasPower();
    }

    public static boolean isGoldExperienceCreated(Mob mob) {
        CompoundTag serialized = new CompoundTag();
        mob.saveWithoutId(serialized);
        ResourceLocation lootTable = ResourceLocation.tryParse(
                serialized.getString("DeathLootTable"));
        return BuiltInLootTables.EMPTY.location().equals(lootTable);
    }

    public static boolean advancementGateOpen(MinecraftServer server) {
        if (!MobPowerConfig
                .REQUIRE_STAND_ADVANCEMENT_TO_SPAWN_MOBS_WITH_POWERS
                .get()) {
            return true;
        }
        AdvancementHolder advancement =
                server.getAdvancements().get(SUMMON_STAND_ADVANCEMENT);
        if (advancement == null) {
            return false;
        }
        for (ServerPlayer player
                : server.getPlayerList().getPlayers()) {
            if (player.getAdvancements()
                    .getOrStartProgress(advancement)
                    .isDone()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static StandType randomStand(LivingEntity entity) {
        List<StandType> eligible = StandType.getAllEnabledStands()
                .filter(ModStands.PLAYER_CAN_GET_FROM_ARROW::contains)
                .filter(stand -> MobPowerConfig.USE_ADDON_STANDS.get()
                        || stand.getId().getNamespace()
                                .equals("jojo_ripples"))
                .filter(stand -> !MobPowerConfig
                        .BLOCKED_STANDS_FOR_MOBS
                        .get()
                        .contains(stand.getId().toString()))
                .toList();
        double totalWeight = 0.0D;
        for (StandType stand : eligible) {
            totalWeight += safeRandomWeight(stand);
        }
        if (!(totalWeight > 0.0D) || !Double.isFinite(totalWeight)) {
            return null;
        }

        double selectedWeight =
                entity.getRandom().nextDouble() * totalWeight;
        StandType lastWeighted = null;
        for (StandType stand : eligible) {
            double weight = safeRandomWeight(stand);
            if (weight <= 0.0D) {
                continue;
            }
            lastWeighted = stand;
            selectedWeight -= weight;
            if (selectedWeight < 0.0D) {
                return stand;
            }
        }
        return lastWeighted;
    }

    private static double safeRandomWeight(StandType stand) {
        double weight = stand.getStandStats().getRandomWeight();
        return Double.isFinite(weight) && weight > 0.0D
                ? weight
                : 0.0D;
    }

    @Nullable
    public static PlayerPowerType<?> randomPlayerPower(
            LivingEntity entity) {
        List<PlayerPowerType<?>> powers =
                PlayerPowerType.getAllEnabledPlayerPowers().toList();
        return powers.isEmpty()
                ? null
                : powers.get(
                        entity.getRandom().nextInt(powers.size()));
    }

    @Nullable
    public static StandType findStand(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return null;
        }
        return StandType.getAllEnabledStands()
                .filter(stand -> stand.getId().equals(parsed))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public static PlayerPowerType<?> findPlayerPower(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return null;
        }
        return PlayerPowerType.getAllEnabledPlayerPowers()
                .filter(power -> power.getId().equals(parsed))
                .findFirst()
                .orElse(null);
    }

    public static boolean grantStand(
            LivingEntity entity,
            StandType stand,
            boolean replace) {
        if (stand == null) {
            return false;
        }
        StandPower power = PowerClass.STAND.attachGet(entity);
        StandInstance replacement = new StandInstance(stand);
        boolean granted;
        if (!power.hasPower()) {
            granted = StandPowerTransitions
                    .insert(power, replacement)
                    .applied();
        }
        else if (!replace) {
            granted = power.getPowerType() == stand;
        }
        else {
            ResourceLocation current =
                    power.getStandInstance()
                            .orElseThrow()
                            .getStandId();
            granted = StandPowerTransitions
                    .replace(power, current, replacement)
                    .applied();
        }
        // Skills unlock through the core's Resolve rule (on give, on each
        // new level, every tick), as 1.16's unlockNewActions did. Forcing
        // them here skipped gates such as Reworked's time stop.
        return granted;
    }

    public static boolean removeStand(Mob mob) {
        StandPower power = StandPower.get(mob);
        if (power == null || !power.hasPower()) {
            return false;
        }
        cleanup(mob);
        ResourceLocation current =
                power.getStandInstance()
                        .orElseThrow()
                        .getStandId();
        return StandPowerTransitions.extract(power, current).applied();
    }

    @Nullable
    public static Mob convertBabyVillager(Mob mob) {
        if (!(mob instanceof Villager villager)
                || !villager.isBaby()
                || !MobPowerConfig.SPAWN_BOY_2_MAN.get()) {
            return null;
        }
        Mob converted =
                villager.convertTo(ModEntityTypes.RPS_KID.get(), true);
        if (converted != null) {
            attachPowers(converted);
            grantStand(converted, ModStands.BOY_II_MAN.get(), true);
            converted.getPersistentData().putBoolean(PROCESSED_TAG, true);
        }
        return converted;
    }

    public static boolean isLongRange(StandType stand) {
        return stand != null
                && MobPowerConfig.LONG_RANGE_STANDS.get()
                        .contains(stand.getId().toString());
    }

    public static boolean isMobSafeAbility(
            Power<?> power,
            Ability ability) {
        if (!(ability instanceof EntityActionAbility
                        || isAttackFollowUp(ability)
                        || isTimeStopBlink(ability))
                || ability == null
                || power == null
                || power.getPowerType() == null
                || ability.getAbilityUsageCategory()
                        == AbilityUsageGroup.INVENTORY) {
            return false;
        }
        String name = ability.name();
        String qualified =
                power.getPowerType().getId().getNamespace()
                        + ":"
                        + name;
        if (CORE_BLOCKED_MOB_ABILITIES.contains(qualified)
                || MobPowerConfig.BLOCKED_STAND_ACTIONS_FOR_MOBS.get()
                        .contains(name)
                || MobPowerConfig.BLOCKED_STAND_ACTIONS_FOR_MOBS.get()
                        .contains(qualified)
                || isPortOnlyUngatedMove(ability)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return UNSUPPORTED_INPUT_TOKENS.stream()
                .noneMatch(lower::contains);
    }

    /**
     * 1.16 050 StandAI drew its picks from every unlocked action, among them
     * the attack follow-ups (StandAction.Builder.attackRecoveryFollowup made
     * them extra unlockables), which it clicked whenever their conditions
     * passed: Crazy Diamond's misshape, leave-object and Angelo Rock
     * modifiers and Gold Experience's tooth lifeform. The core has them as
     * plain inputs on the attack's key, with those conditions.
     */
    public static boolean isAttackFollowUp(Ability ability) {
        return ability instanceof CrazyDMisshapingPunchInput
                || ability instanceof CrazyDLeaveObjectPunchInput
                || ability instanceof CrazyDAngeloRockPunchInput
                || ability instanceof GoldExperienceToothLifeformAbility;
    }

    /**
     * 1.16 050 StandAI clicked the time-stop blink (TimeStopInstant) whenever
     * its conditions passed: AddonUtil.getListOfUnlockedStandActions set the
     * time stop's training on every pick, TimeStop.onTrainingPoints set the
     * blink's, and that gave the blink the learning entry getAllUnlockedActions
     * lists. The core has it as a plain input with TimeStopInstant's
     * conditions (outside stopped time, the Stand idle). Time Resume never
     * got an entry (TimeResume.isUnlocked is always true, so unlockNewActions
     * skipped it): no 1.16 mob clicked it.
     */
    public static boolean isTimeStopBlink(Ability ability) {
        return ability instanceof TimeStopBlinkAbility;
    }

    /**
     * 1.16 TimeStop.perform gave the time stop its own cooldown for as many
     * ticks as the stop it started (getCooldownTechnical is the stop's
     * ticks), so 050 StandAI's or UseRandomAction's click of it during the
     * user's own stop was refused (PowerBaseImpl.checkRequirements) and the
     * stop went on; only Time Resume, which no mob had, cut it short. The
     * core's time stop key resumes the user's own stop instead.
     */
    public static boolean resumesOwnTimeStop(
            LivingEntity user,
            Ability ability) {
        return ability instanceof TimeStopAbility
                && user.level() instanceof ServerLevel level
                && level.getData(ModDataAttachmentTypes.TIME_STOP.get())
                        .getInstance(user.getId())
                        .filter(TimeStopState.Instance::isActive)
                        .isPresent();
    }

    /**
     * Port-only Stand moves no Resolve level gates, which 1.16 mobs never
     * had (owner decision R148): the Stand grab with its throw and grab
     * punches, and Gold Experience's standalone entity lifeshot. The grab
     * goes with its family: a Stand that grabs can use nothing else.
     */
    public static boolean isPortOnlyUngatedMove(Ability ability) {
        if (ability instanceof StandEntityGrabAbility
                || ability instanceof StandEntityGrabThrowAbility
                || ability instanceof GoldExperienceEntityLifeshotAbility) {
            return true;
        }
        // initIsGrabVariation marks the grab punches, barrages and kicks.
        return ability != null
                && ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB
                && (ability instanceof StandEntityPunchAbility
                        || ability instanceof StandEntityBarrageAbility
                        || ability instanceof StandEntityHeavyPunchAbility);
    }

    public static List<Ability> availableMobAbilities(Power<?> power) {
        if (power == null || !power.hasPower()) {
            return List.of();
        }
        return new ArrayList<>(
                power.updateAvailableMoves()
                        .inMovesetAndCanBeUsed
                        .values())
                .stream()
                .filter(ability -> isMobSafeAbility(power, ability))
                .toList();
    }

    /**
     * 1.16 050 AddonUtil.getListOfUnlockedStandActions, run by every
     * StandAI pick and by Use Random Action, set each unlocked time stop's
     * training to a random 0 to max time-stop ticks, so a mob's time stop
     * lasts anywhere from the shortest to the longest.
     */
    public static void randomizeTimeStopTraining(
            StandPower power,
            RandomSource random) {
        StandTypePersistentData data = power != null && power.hasPower()
                ? power.getCurTypeData()
                : null;
        if (data == null) {
            return;
        }
        float maxPoints = TimeStopLearning.getMaxTrainingPoints(power);
        int maxTicks = Math.round(maxPoints)
                + TimeStopLearning.MIN_TIME_STOP_TICKS;
        if (maxTicks <= 0) {
            return;
        }
        for (Ability ability : power.getMoveset().abilities.values()) {
            if (ability instanceof TimeStopAbility) {
                // Locked time stops are left alone by the setter.
                data.setAbilityLearningProgressPoints(
                        TimeStopLearning.getLearningName(ability),
                        random.nextInt(maxTicks + 1),
                        maxPoints,
                        power);
            }
        }
    }

    public static boolean grabStateMatches(
            Mob mob,
            Ability ability) {
        String name = ability.name();
        LivingEntity grabbed =
                LivingComponentGrab.getEntityGrabbedBy(mob);
        if (name.equals("grab")) {
            return grabbed == null;
        }
        if (name.startsWith("grab_")) {
            return grabbed != null && grabbed.isAlive();
        }
        return true;
    }

    public static void rememberHeldKey(
            Mob mob,
            String tag,
            short keyId) {
        mob.getPersistentData().putInt(tag, keyId);
    }

    public static void releaseHeldKey(Mob mob, String tag) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(tag)) {
            AbilityInput.keyRelease((short) data.getInt(tag), mob);
            data.remove(tag);
        }
    }

    @Nullable
    public static Mob getDebugAggroFirst(
            @Nullable LivingEntity user) {
        if (user == null
                || !(user.level() instanceof ServerLevel level)) {
            return null;
        }
        CompoundTag data = user.getPersistentData();
        if (!data.hasUUID(DEBUG_AGGRO_FIRST_TAG)) {
            return null;
        }
        UUID firstId = data.getUUID(DEBUG_AGGRO_FIRST_TAG);
        Entity first = level.getEntity(firstId);
        if (first instanceof Mob mob && mob.isAlive()) {
            return mob;
        }
        data.remove(DEBUG_AGGRO_FIRST_TAG);
        return null;
    }

    public static void setDebugAggroFirst(
            LivingEntity user,
            @Nullable Mob first) {
        CompoundTag data = user.getPersistentData();
        if (first != null) {
            data.putUUID(DEBUG_AGGRO_FIRST_TAG, first.getUUID());
        }
        else {
            data.remove(DEBUG_AGGRO_FIRST_TAG);
        }
    }

    // Lets go of held keys and grabs; the Stand stays out. 1.16 StandAI
    // left the Stand to its own gentle unsummon on target loss.
    public static void releaseInputsAndGrabs(Mob mob) {
        releaseHeldKey(mob, STAND_HELD_KEY_TAG);
        releaseHeldKey(mob, PLAYER_POWER_HELD_KEY_TAG);
        ManualHeldInput.release(mob);
        releaseGrab(mob);

        StandPower standPower = StandPower.get(mob);
        StandEntity stand = standPower != null && standPower.hasPower()
                ? standPower.getSummonedStandEntity()
                : null;
        if (stand != null) {
            releaseGrab(stand);
        }
    }

    // Death, level leave and Stand removal: the Stand goes at once.
    public static void cleanup(Mob mob) {
        releaseInputsAndGrabs(mob);
        StandPower standPower = StandPower.get(mob);
        if (standPower != null && standPower.hasPower()) {
            standPower.getPowerType().forceUnsummon(mob, standPower);
        }
    }

    private static void releaseGrab(LivingEntity entity) {
        if (entity.hasData(ModDataAttachmentTypes.LIVING_GRAB.get())) {
            entity.getData(ModDataAttachmentTypes.LIVING_GRAB.get())
                    .setGrabTarget(null);
        }
        LivingEntity grabber =
                LivingComponentGrab.getEntityGrabbing(entity);
        if (grabber != null
                && grabber.hasData(
                        ModDataAttachmentTypes.LIVING_GRAB.get())) {
            grabber.getData(ModDataAttachmentTypes.LIVING_GRAB.get())
                    .setGrabTarget(null);
        }
    }

    private MobPowerUtil() {}
}
