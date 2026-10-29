package rotp.addons.stand_bearing_creatures.entity.goal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.stands.magiciansred.MRDetectorEntity;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.gametest.TemplateChunkLayout;
import rotp.addons.stand_bearing_creatures.mechanics.combo.ComboManager;
import rotp.addons.stand_bearing_creatures.mechanics.combo.ComboStep;
import rotp.addons.stand_bearing_creatures.mechanics.combo.PowerComboData;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobPressConditionGameTests {
    private MobPressConditionGameTests() {}

    // 1.16 050 StandAI.performAction clicked through PowerBaseImpl, whose
    // checkRequirements and checkTarget refused the whole action on the aim
    // the mob had just set: Gold Experience's Bone Meal wants a baby animal.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aMobsPressIsRefusedWhereItsConditionsFailOnTheNewAimAsIn116(
            GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow adult = EntityType.COW.create(level);
        Cow calf = EntityType.COW.create(level);
        helper.assertTrue(mob != null && adult != null && calf != null,
                "Missing mob fixtures");
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(adult, pos.add(2.5D, 0.0D, 0.0D));
            standStill(calf, pos.add(-2.5D, 0.0D, 0.0D));
            calf.setBaby(true);
            level.addFreshEntity(mob);
            level.addFreshEntity(adult);
            level.addFreshEntity(calf);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.GOLD_EXPERIENCE.get(), true),
                    "Could not give the husk Gold Experience.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Gold Experience.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
            stand.summonLockTicks = 0;
            Ability boneMeal = power.getAbility("bone_meal");
            helper.assertTrue(boneMeal != null, "Setup: Gold Experience has no bone_meal.");
            Map<String, Ability> abilities = Map.of("bone_meal", boneMeal);

            mob.setTarget(adult);
            lookAt(mob, adult);
            // where its tick puts it beside the husk: a Stand just summoned stands on the husk's look line
            stand.updatePosition(mob);
            StandPowerMobGoal goal = new StandPowerMobGoal(mob);
            boolean pressed = goal.performNamed(power, abilities, "bone_meal", null, adult);
            EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(pressed && started == null,
                    "1.16 refused the Bone Meal of a mob aiming at an adult cow (GoldExperienceBoneMeal.checkTarget"
                            + " wants a baby); it started " + started + ".");
            helper.assertTrue(!goal.performNamed(power, abilities, "bone_meal", null, adult),
                    "1.16 StandAI.performAction set the cooldown of the refused Bone Meal; the mob may press it again.");

            mob.setTarget(calf);
            lookAt(mob, calf);
            stand.updatePosition(mob);
            pressed = new StandPowerMobGoal(mob).performNamed(
                    power, abilities, "bone_meal", null, calf);
            started = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(pressed && started != null && started.ability == boneMeal,
                    "Setup: the Bone Meal of a mob aiming at a calf did not start; it started " + started + ".");
            LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            helper.succeed();
        } finally {
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            adult.discard();
            calf.discard();
        }
    }

    // 1.16 050 base_combos.json heavy_combo: jojo:crazy_diamond_heavy_punch, then
    // jojo:crazy_diamond_misshaping_punch, the heavy punch's finisher variation (ModStandsInit :643; an unlocked action
    // from Resolve 1 through StandEntityHeavyAttack :287), which StandAI.performAction clicked at its target, a mob or
    // not; the misshaping follow-ups (crazy_diamond_misshape_face and the others) were no combo step.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void crazyDiamondsHeavyComboEndsWithTheFinisherPunchAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        PowerComboData combos = ComboManager.stand(
                ResourceLocation.fromNamespaceAndPath("jojo_ripples", "crazy_diamond"));
        List<ComboStep> steps = combos != null ? combos.combo("heavy_combo") : null;
        List<String> names = steps != null ? steps.stream().map(ComboStep::action).toList() : null;
        helper.assertTrue(List.of("heavy_punch", "finisher").equals(names),
                "1.16 Crazy Diamond's heavy_combo was the heavy punch, then its finisher punch"
                        + " (crazy_diamond_misshaping_punch); the port's is " + names + ".");
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        standStill(cow, pos.add(0.0D, 0.0D, 2.0D));
        level.addFreshEntity(mob);
        level.addFreshEntity(cow);
        helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                        && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                "Could not give the husk Crazy Diamond.");
        StandPower power = StandPower.get(mob);
        power.setResolveLevel(1);
        power.setStamina(power.getMaxStamina());
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(power.getPowerType().summon(mob, power),
                        "Could not summon the husk's Crazy Diamond.");
                StandEntity stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
                stand.summonLockTicks = 0;
                Ability finisher = power.getAbility(steps.get(1).action());
                helper.assertTrue(finisher != null && finisher.isStandFinisherOf != null
                                && "heavy_punch".equals(finisher.isStandFinisherOf.baseAbilityName())
                                && MobPowerUtil.isMobSafeAbility(power, finisher),
                        "The heavy_combo's second step is not Crazy Diamond's heavy finisher a mob may press: "
                                + finisher + ".");
                mob.setTarget(cow);
                lookAt(mob, cow);
                boolean pressed = new StandPowerMobGoal(mob).performNamed(
                        power, Map.of(finisher.name(), finisher), finisher.name(), steps.get(1), cow);
                EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
                helper.assertTrue(pressed && started != null && started.ability == finisher,
                        "1.16 StandAI threw Crazy Diamond's finisher punch at the cow as the heavy_combo's second"
                                + " step; it started " + started + ".");
                LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
                helper.succeed();
            } finally {
                if (power.isSummoned()) {
                    power.getPowerType().forceUnsummon(mob, power);
                }
                mob.discard();
                cow.discard();
            }
        });
    }

    // 1.16 050 StandAI.isActionAvailable asked StandEntityAction.checkConditions before every combo step and pick; its
    // checkTaskCancelling refused an action the Stand's current task would not let in, so tryToContinueCombo reset the
    // combo and performRandomAvailableAction drew among the others. The step was not clicked and took no cooldown.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aStepTheStandsRunningActionHoldsBackIsNotAvailableAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        StandPower power = null;
        StandEntity stand = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(cow, pos.add(0.0D, 0.0D, 1.5D));
            level.addFreshEntity(mob);
            level.addFreshEntity(cow);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                    "Could not give the husk Crazy Diamond.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Crazy Diamond.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
            stand.summonLockTicks = 0;
            PowerComboData combos = ComboManager.stand(
                    ResourceLocation.fromNamespaceAndPath("jojo_ripples", "crazy_diamond"));
            List<ComboStep> steps = combos != null ? combos.combo("dora_combo") : null;
            Ability heavy = power.getAbility("heavy_punch");
            Ability punch = power.getAbility("punch");
            helper.assertTrue(steps != null && !steps.isEmpty() && "punch".equals(steps.getFirst().action())
                            && heavy != null && punch instanceof EntityActionAbility,
                    "Setup: Crazy Diamond's dora_combo, heavy punch or punch is missing.");
            ComboStep step = steps.getFirst();
            mob.setTarget(cow);
            StandPowerMobGoal goal = new StandPowerMobGoal(mob);

            helper.assertTrue(goal.performNamed(power, Map.of("heavy_punch", heavy), "heavy_punch", null, cow),
                    "Setup: the husk did not press its heavy punch.");
            EntityActionInstance running = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(running != null && running.ability == heavy && !running.isOver()
                            && ((EntityActionAbility) punch).wouldBufferInput(mob),
                    "Setup: the running heavy punch does not hold a punch back; the Stand runs " + running + ".");
            helper.assertFalse(goal.isAvailable(power, punch, step, cow) || goal.isAvailable(power, punch, null, cow),
                    "1.16 StandAI.isActionAvailable refused a punch the Stand's running heavy punch would not let in"
                            + " (StandEntityAction.checkTaskCancelling), as a combo step and as a random pick.");
            boolean stepPressed = goal.performNamed(
                    power, Map.of("punch", punch), "punch", step, cow);
            EntityActionInstance after = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(!stepPressed && after == running,
                    "1.16 left a combo step the running heavy punch held back unclicked and reset the combo; the step was"
                            + " taken (" + stepPressed + ") and the Stand runs " + after + ".");

            LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            boolean freePressed = goal.performNamed(
                    power, Map.of("punch", punch), "punch", step, cow);
            EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(freePressed && started != null && started.ability == punch,
                    "Setup: with the heavy punch over the step did not start (a cooldown from the refusal?); the Stand"
                            + " runs " + started + ".");
            helper.succeed();
        } finally {
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            }
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            cow.discard();
        }
    }

    // 1.16 050 StandAI.isActionAvailable (:190) also required action.getStaminaCost(power) <= power.getStamina() of every
    // combo step, a new combo's first step, the block action and every random candidate; a heavy attack cost 50, a light
    // one 10 (StandEntityHeavyAttack, StandEntityLightAttack).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aStandMobSkipsAMoveItsStaminaCannotPayAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        StandPower power = null;
        StandEntity stand = null;
        try {
            Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), 1.0D, 4.0D, 1.0D, 5.0D);
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(cow, pos.add(0.0D, 0.0D, 1.5D));
            level.addFreshEntity(mob);
            level.addFreshEntity(cow);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                    "Could not give the husk Crazy Diamond.");
            power = StandPower.get(mob);
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Crazy Diamond.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
            stand.summonLockTicks = 0;
            Ability heavy = power.getAbility("heavy_punch");
            Ability punch = power.getAbility("punch");
            helper.assertTrue(heavy != null && punch != null && !power.isStaminaInfinite()
                            && heavy.getStaminaCost(power) == 50.0F && punch.getStaminaCost(power) == 10.0F,
                    "Setup: Crazy Diamond's heavy punch and punch do not report 1.16's costs (50, 10) on a finite bar.");
            Map<String, Ability> abilities = Map.of("heavy_punch", heavy, "punch", punch);
            PowerComboData data = new PowerComboData(Map.of(), null, null,
                    Map.of("heavy_first", List.of(new ComboStep("heavy_punch", null, null))));
            mob.setTarget(cow);
            StandPowerMobGoal goal = new StandPowerMobGoal(mob);

            power.setStamina(30.0F);
            helper.assertFalse(goal.isAvailable(power, heavy, data.combo("heavy_first").getFirst(), cow)
                            || goal.isAvailable(power, heavy, null, cow),
                    "1.16 StandAI.isActionAvailable refused a heavy punch (cost 50) on 30 stamina, as a combo step and"
                            + " as a random pick.");
            helper.assertTrue(goal.isAvailable(power, punch, null, cow),
                    "1.16 StandAI.isActionAvailable let a punch (cost 10) through on 30 stamina.");
            goal.runCombo(power, data, abilities, cow);
            EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(started != null && started.ability == punch,
                    "1.16 StandAI on 30 stamina started no combo that opened with the heavy punch and picked the punch at"
                            + " random; the Stand runs " + started + ".");
            LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);

            power.setStamina(power.getMaxStamina());
            helper.assertTrue(goal.isAvailable(power, heavy, null, cow),
                    "Setup: on a full bar the heavy punch is still not available (a cooldown from the refusal?).");
            goal.runCombo(power, data, abilities, cow);
            started = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(started != null && started.ability == heavy,
                    "Setup: on a full bar the combo did not open with the heavy punch; the Stand runs " + started + ".");
            helper.succeed();
        } finally {
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            }
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            cow.discard();
        }
    }

    // 1.16 050 StandAI drew its random pick from every unlocked action (AddonUtil.getListOfUnlockedStandActions), the
    // attack follow-ups among them (StandAction.Builder.attackRecoveryFollowup :404-419 made them extra unlockables), and
    // clicked one whose conditions passed: CrazyDiamondMisshapeBodyPart.checkSpecificConditions (:33-50) passed during the
    // finisher (crazy_diamond_misshaping_punch, its recorded part) at a player only, and only at AddonUtil.getActionTarget's
    // player, not the mob's own Stand standing before it, so a Crazy Diamond mob misshaped the player its finisher struck
    // when its look met that player; StandAI.performAction then set its cooldown.
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aCrazyDiamondMobMisshapesThePlayerItsFinisherStrikesAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        Player player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MobMisshapeTarget"));
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        standStill(cow, pos.add(0.0D, 0.0D, 2.0D));
        player.moveTo(pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);
        player.setNoGravity(true);
        level.addFreshEntity(mob);
        helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                        && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                "Could not give the husk Crazy Diamond.");
        StandPower power = StandPower.get(mob);
        power.setResolveLevel(1);
        power.setStamina(power.getMaxStamina());
        Runnable cleanup = () -> {
            if (power.isSummoned()) {
                StandEntity stand = power.getSummonedStandEntity();
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
                }
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            cow.discard();
            player.discard();
        };
        int[] waited = new int[1];
        stages(helper, cleanup,
                () -> {
                    helper.assertTrue(power.isAbilityUnlocked("finisher") && power.isAbilityUnlocked("disfiguring_punch"),
                            "Setup: a Resolve 1 Crazy Diamond husk has not unlocked its finisher and misshaping punch.");
                    helper.assertTrue(power.getPowerType().summon(mob, power),
                            "Could not summon the husk's Crazy Diamond.");
                    StandEntity stand = power.getSummonedStandEntity();
                    helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
                    stand.summonLockTicks = 0;
                    level.addFreshEntity(cow);
                    mob.setTarget(cow);
                    startFinisher(helper, mob, power, stand, power.getAbility("finisher"), cow);
                    return true;
                },
                () -> {
                    helper.assertFalse(pool(power).containsKey("disfiguring_punch"),
                            "1.16 offered the misshape modifiers only while the finisher was at a player"
                                    + " (CrazyDiamondMisshapeBodyPart :36); a husk whose finisher is at a cow may take it.");
                    StandEntity stand = power.getSummonedStandEntity();
                    LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
                    cow.discard();
                    helper.assertTrue(level.addFreshEntity(player), "Could not add the player.");
                    power.setStamina(power.getMaxStamina());
                    mob.setTarget(player);
                    startFinisher(helper, mob, power, stand, power.getAbility("finisher"), player);
                    return true;
                },
                () -> {
                    Map<String, Ability> pool = pool(power);
                    Ability misshaping = pool.get("disfiguring_punch");
                    helper.assertTrue(misshaping != null && MobPowerUtil.isAttackFollowUp(misshaping),
                            "1.16 StandAI's random pick reached Crazy Diamond's misshape modifier while its finisher was"
                                    + " at the player its look met; the husk's pool is " + pool.keySet() + ".");
                    // the Stand stands between the husk and the player, as the finisher's front offset puts it
                    StandEntity stand = power.getSummonedStandEntity();
                    stand.setPos(mob.getX(), stand.getY(), mob.getZ() + 1.0D);
                    boolean pressed = new StandPowerMobGoal(mob).performNamed(
                            power, pool, "disfiguring_punch", null, player);
                    ActionTarget aim = LivingComponentAction.getAimTarget(mob);
                    helper.assertTrue(pressed && aim.getEntity() == stand && power.userStandEffects.getEffectOfType(
                                    ModStandAbilities.EFFECT_CD_PUNCH_MISSHAPING.get()).isEmpty(),
                            "1.16 AddonUtil.getActionTarget met the husk's own Stand before the player, which"
                                    + " CrazyDiamondMisshapeBodyPart refused (its Stand user is no player); the press"
                                    + " aimed at " + describe(aim) + ".");
                    // and off the look line
                    stand.setPos(mob.getX() + 1.5D, stand.getY(), mob.getZ() + 1.0D);
                    StandPowerMobGoal goal = new StandPowerMobGoal(mob);
                    pressed = goal.performNamed(power, pool, "disfiguring_punch", null, player);
                    helper.assertTrue(pressed && power.userStandEffects.getEffectOfType(
                                    ModStandAbilities.EFFECT_CD_PUNCH_MISSHAPING.get()).isPresent(),
                            "1.16 StandAI clicked the misshape modifier on the running finisher at the player its look"
                                    + " met; pressed " + pressed + ".");
                    helper.assertFalse(goal.performNamed(power, pool, "disfiguring_punch", null, player),
                            "1.16 StandAI.performAction set the cooldown of the clicked modifier; the husk may press it"
                                    + " again.");
                    return true;
                },
                () -> {
                    boolean misshapen = player.hasEffect(ModStatusEffects.MISSHAPEN_FACE)
                            || player.hasEffect(ModStatusEffects.MISSHAPEN_ARMS)
                            || player.hasEffect(ModStatusEffects.MISSHAPEN_LEGS);
                    helper.assertTrue(misshapen || ++waited[0] <= 120,
                            "1.16 misshaped the player the mob's finisher struck after the modifier; it was not.");
                    return misshapen;
                });
    }

    // 1.16 CrazyDiamondHeavyPunch.attackRecoveryFollowup(CRAZY_DIAMOND_LEAVE_OBJECT) made leave_object an unlocked action a
    // mob's random pick reached; CrazyDiamondLeaveObject.checkSpecificConditions (:99-106) passed with an item to leave in
    // a living target during the punch. The other core follow-ups (Angelo Rock, Gold Experience's tooth lifeform) go the
    // same way; a plain input that was no 1.16 action (the manual control key) stays out of mob logic.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aCrazyDiamondMobLeavesItsItemInTheMobItsHeavyPunchStrikesAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CHORUS_FRUIT, 8));
        standStill(cow, pos.add(0.0D, 0.0D, 2.0D));
        level.addFreshEntity(mob);
        level.addFreshEntity(cow);
        helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                        && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                "Could not give the husk Crazy Diamond.");
        StandPower power = StandPower.get(mob);
        power.setResolveLevel(2);
        power.setStamina(power.getMaxStamina());
        Runnable cleanup = () -> {
            if (power.isSummoned()) {
                StandEntity stand = power.getSummonedStandEntity();
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
                }
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            cow.discard();
        };
        stages(helper, cleanup,
                () -> {
                    helper.assertTrue(power.isAbilityUnlocked("leave_object"),
                            "Setup: a Resolve 2 Crazy Diamond husk has not unlocked leave_object.");
                    Ability angeloRock = power.getAbility("fuse_with_rock");
                    Ability tooth = ModStandAbilities.GE_TOOTH_LIFEFORM.get()._defaultAbilityInstance;
                    Ability manualControl = ModStandAbilities.MANUAL_CONTROL.get()._defaultAbilityInstance;
                    helper.assertTrue(MobPowerUtil.isMobSafeAbility(power, angeloRock)
                                    && MobPowerUtil.isAttackFollowUp(tooth),
                            "1.16 StandAI's random pick reached the Angelo Rock and tooth lifeform follow-ups too: "
                                    + angeloRock + ", " + tooth + ".");
                    helper.assertFalse(MobPowerUtil.isMobSafeAbility(power, manualControl),
                            "A plain input that was no 1.16 action entered mob logic: " + manualControl + ".");
                    helper.assertTrue(power.getPowerType().summon(mob, power),
                            "Could not summon the husk's Crazy Diamond.");
                    StandEntity stand = power.getSummonedStandEntity();
                    helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
                    stand.summonLockTicks = 0;
                    stand.setFinisherMeter(0.0F);
                    mob.setTarget(cow);
                    lookAt(mob, cow);
                    Ability heavy = power.getAbility("heavy_punch");
                    helper.assertTrue(new StandPowerMobGoal(mob).performNamed(
                                    power, Map.of("heavy_punch", heavy), "heavy_punch", null, cow),
                            "Setup: the husk did not press its heavy punch.");
                    EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
                    helper.assertTrue(started != null && started.ability == heavy,
                            "Setup: the husk's heavy punch did not start; the Stand runs " + started + ".");
                    MobPowerUtil.releaseHeldKey(mob, MobPowerUtil.STAND_HELD_KEY_TAG);
                    return true;
                },
                () -> {
                    Map<String, Ability> pool = pool(power);
                    helper.assertTrue(pool.get("leave_object") != null,
                            "1.16 StandAI's random pick reached Crazy Diamond's leave-object modifier during its heavy"
                                    + " punch with an item to leave; the husk's pool is " + pool.keySet() + ".");
                    boolean pressed = new StandPowerMobGoal(mob).performNamed(power, pool, "leave_object", null, cow);
                    helper.assertTrue(pressed && power.userStandEffects.getEffectOfType(
                                    ModStandAbilities.EFFECT_CD_PUNCH_LEAVE_OBJECT.get()).isPresent(),
                            "1.16 StandAI clicked the leave-object modifier on the running heavy punch; pressed " + pressed
                                    + ".");
                    return true;
                });
    }

    // 1.16 050 NonStandAI.tryToStartNewCombo drew from every combo name (NonStandComboData.getComboNames: no Resolve table)
    // and tryToContinueCombo only reset the combo when the next step was not available, with no random action; 050
    // StandAI reset and performed a random available action. A pressed ability takes the goal's cooldown, so a step or pick
    // that was pressed is no longer available.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotp_mwp_combo_flow")
    public static void aPlayerPowerMobsBrokenComboOnlyResetsAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Mob standMob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && standMob != null && cow != null, "Missing mob fixtures");
        // the Stand mob's summon ticks between the stages
        Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), 1.0D, 6.0D, 1.0D, 5.5D);
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        standMob.moveTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        standMob.setNoAi(true);
        standMob.setNoGravity(true);
        standStill(cow, pos.add(1.0D, 0.0D, 2.0D));
        level.addFreshEntity(mob);
        level.addFreshEntity(standMob);
        level.addFreshEntity(cow);
        MobPowerUtil.attachPowers(mob);
        PlayerPower hamon = PowerClass.PLAYER_POWER.attachGet(mob);
        helper.assertTrue(hamon.trySetPowerType(ModPlayerPowers.HAMON.get()), "Could not give the husk Hamon.");
        helper.assertTrue(MobPowerUtil.isEligibleMob(standMob)
                        && MobPowerUtil.grantStand(standMob, ModStands.CRAZY_DIAMOND.get(), true),
                "Could not give the second husk Crazy Diamond.");
        StandPower stand = StandPower.get(standMob);
        stand.setStamina(stand.getMaxStamina());
        Runnable cleanup = () -> {
            if (stand.isSummoned()) {
                StandEntity standEntity = stand.getSummonedStandEntity();
                if (standEntity != null) {
                    LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
                }
                stand.getPowerType().forceUnsummon(standMob, stand);
            }
            MobPowerUtil.releaseInputsAndGrabs(mob);
            mob.discard();
            standMob.discard();
            cow.discard();
        };
        stages(helper, cleanup,
                () -> {
                    List<Ability> safe = hamon.getMoveset().abilities.values().stream()
                            .filter(ability -> MobPowerUtil.isMobSafeAbility(hamon, ability))
                            .sorted(java.util.Comparator.comparing(Ability::name))
                            .toList();
                    helper.assertTrue(safe.size() >= 2, "Setup: a Hamon mob has fewer than two moves: " + safe);
                    Ability first = safe.get(0);
                    Ability random = safe.get(1);
                    Map<String, Ability> abilities = Map.of(first.name(), first, random.name(), random);
                    Map<String, List<ComboStep>> combos = Map.of("test_combo", List.of(
                            new ComboStep(first.name(), null, null), new ComboStep("no_such_step", null, null)));
                    // a Resolve table that offers nothing at the mob's level 0
                    PowerComboData data = new PowerComboData(Map.of(0, List.of()), null, null, combos);
                    mob.setTarget(cow);
                    PlayerPowerMobGoal goal = new PlayerPowerMobGoal(mob);
                    goal.runCombo(hamon, data, abilities, cow);
                    helper.assertTrue("test_combo".equals(goal.currentCombo())
                                    && !goal.isAvailable(hamon, first, null, cow)
                                    && goal.isAvailable(hamon, random, null, cow),
                            "1.16 NonStandAI took every combo name (no Resolve table) and pressed the combo's first step "
                                    + first.name() + "; combo " + goal.currentCombo() + ", pressed it "
                                    + !goal.isAvailable(hamon, first, null, cow) + ", picked " + random.name()
                                    + " at random " + !goal.isAvailable(hamon, random, null, cow) + ".");
                    goal.runCombo(hamon, data, abilities, cow);
                    helper.assertTrue(goal.isAvailable(hamon, random, null, cow),
                            "1.16 NonStandAI.tryToContinueCombo only reset the combo when its next step was not available;"
                                    + " the mob pressed " + random.name() + " at random.");
                    return true;
                },
                () -> {
                    helper.assertTrue(stand.getPowerType().summon(standMob, stand),
                            "Could not summon the husk's Crazy Diamond.");
                    StandEntity standEntity = stand.getSummonedStandEntity();
                    helper.assertTrue(standEntity != null, "Setup: the husk has no Stand.");
                    standEntity.summonLockTicks = 0;
                    return true;
                },
                () -> {
                    StandEntity standEntity = stand.getSummonedStandEntity();
                    Ability first = stand.getAbility("punch");
                    Ability random = stand.getAbility("heavy_punch");
                    helper.assertTrue(first != null && random != null, "Setup: Crazy Diamond has no punch or heavy punch.");
                    Map<String, Ability> abilities = Map.of(first.name(), first, random.name(), random);
                    PowerComboData data = new PowerComboData(Map.of(), first.name(), null, Map.of("test_combo", List.of(
                            new ComboStep(first.name(), null, null), new ComboStep("no_such_step", null, null))));
                    standMob.setTarget(cow);
                    StandPowerMobGoal goal = new StandPowerMobGoal(standMob);
                    goal.runCombo(stand, data, abilities, cow);
                    LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
                    helper.assertTrue(!goal.isAvailable(stand, first, null, cow) && goal.isAvailable(stand, random, null, cow),
                            "Setup: the Stand mob did not press only its combo's first step.");
                    goal.runCombo(stand, data, abilities, cow);
                    LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
                    helper.assertFalse(goal.isAvailable(stand, random, null, cow),
                            "1.16 StandAI.tryToContinueCombo reset the combo and performed a random available action when"
                                    + " the next step was not; the Stand mob did not press its heavy punch.");
                    return true;
                });
    }

    // 1.16 050 NonStandAI.runAI (:101-110) ran its combos whenever the power had combo data, without StandAI's
    // useWIPComboSystem check (StandAI.runAI :111), and never blocked: NonStandComboData read every key but maintenance as a
    // combo, and its random pick left nothing out. A pressed ability takes the goal's cooldown.
    @GameTest(template = "empty", timeoutTicks = 20, batch = "rotp_mwp_combo_flow")
    public static void aPlayerPowerMobsCombosIgnoreTheComboConfigAndItNeverBlocksAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Mob standMob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && standMob != null && cow != null, "Missing mob fixtures");
        StandPower stand = null;
        StandEntity standEntity = null;
        try {
            Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), 1.0D, 6.0D, 1.0D, 5.5D);
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standMob.moveTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
            standMob.setNoAi(true);
            standMob.setNoGravity(true);
            standStill(cow, pos.add(1.0D, 0.0D, 2.0D));
            level.addFreshEntity(mob);
            level.addFreshEntity(standMob);
            level.addFreshEntity(cow);
            MobPowerUtil.attachPowers(mob);
            PlayerPower hamon = PowerClass.PLAYER_POWER.attachGet(mob);
            helper.assertTrue(hamon.trySetPowerType(ModPlayerPowers.HAMON.get()), "Could not give the husk Hamon.");
            List<Ability> safe = hamon.getMoveset().abilities.values().stream()
                    .filter(ability -> MobPowerUtil.isMobSafeAbility(hamon, ability))
                    .sorted(java.util.Comparator.comparing(Ability::name))
                    .toList();
            helper.assertTrue(safe.size() >= 2, "Setup: a Hamon mob has fewer than two moves: " + safe);
            Ability first = safe.get(0);
            Ability second = safe.get(1);
            Map<String, Ability> abilities = Map.of(first.name(), first, second.name(), second);
            mob.setTarget(cow);

            PowerComboData combo = new PowerComboData(Map.of(), null, null, Map.of("test_combo", List.of(
                    new ComboStep(first.name(), null, null), new ComboStep(second.name(), null, null))));
            PlayerPowerMobGoal goal = new PlayerPowerMobGoal(mob);
            goal.pick(hamon, combo, abilities, cow, false);
            helper.assertTrue("test_combo".equals(goal.currentCombo())
                            && !goal.isAvailable(hamon, first, null, cow) && goal.isAvailable(hamon, second, null, cow),
                    "1.16 NonStandAI ran a player power's combo with useWIPComboSystem off and pressed its first step "
                            + first.name() + "; combo " + goal.currentCombo() + ".");

            mob.setLastHurtByMob(cow);
            PowerComboData withBlock = new PowerComboData(Map.of(), second.name(), null, Map.of("test_combo", List.of(
                    new ComboStep(first.name(), null, null))));
            goal = new PlayerPowerMobGoal(mob);
            goal.pick(hamon, withBlock, abilities, cow, true);
            helper.assertTrue(goal.isAvailable(hamon, second, null, cow) && !goal.isAvailable(hamon, first, null, cow),
                    "1.16 NonStandAI never blocked: a just-hurt player-power mob whose combo data names " + second.name()
                            + " as its block pressed it instead of its combo's " + first.name() + ".");

            PowerComboData blockOnly = new PowerComboData(Map.of(), first.name(), null, Map.of());
            goal = new PlayerPowerMobGoal(mob);
            goal.pick(hamon, blockOnly, Map.of(first.name(), first), cow, true);
            helper.assertFalse(goal.isAvailable(hamon, first, null, cow),
                    "1.16 NonStandAI's random pick left no block action out; the player-power mob did not pick "
                            + first.name() + ", the only move, which its combo data names as the block.");

            helper.assertTrue(MobPowerUtil.isEligibleMob(standMob)
                            && MobPowerUtil.grantStand(standMob, ModStands.CRAZY_DIAMOND.get(), true),
                    "Could not give the second husk Crazy Diamond.");
            stand = StandPower.get(standMob);
            stand.setStamina(stand.getMaxStamina());
            helper.assertTrue(stand.getPowerType().summon(standMob, stand), "Could not summon the husk's Crazy Diamond.");
            standEntity = stand.getSummonedStandEntity();
            helper.assertTrue(standEntity != null, "Setup: the husk has no Stand.");
            standEntity.summonLockTicks = 0;
            Ability punch = stand.getAbility("punch");
            helper.assertTrue(punch != null, "Setup: Crazy Diamond has no punch.");
            PowerComboData standCombo = new PowerComboData(Map.of(), null, null, Map.of("test_combo", List.of(
                    new ComboStep("punch", null, null), new ComboStep("punch", null, null))));
            standMob.setTarget(cow);
            StandPowerMobGoal standGoal = new StandPowerMobGoal(standMob);
            standGoal.pick(stand, standCombo, Map.of("punch", punch), cow, false);
            helper.assertTrue(standGoal.currentCombo() == null && !standGoal.isAvailable(stand, punch, null, cow),
                    "1.16 StandAI picked at random with useWIPComboSystem off; the Stand mob's combo is "
                            + standGoal.currentCombo() + ".");
            LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
            standGoal = new StandPowerMobGoal(standMob);
            standGoal.pick(stand, standCombo, Map.of("punch", punch), cow, true);
            helper.assertTrue("test_combo".equals(standGoal.currentCombo()),
                    "Setup: with useWIPComboSystem on the Stand mob did not start its combo.");
            helper.succeed();
        } finally {
            if (standEntity != null) {
                LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
            }
            if (stand != null && stand.isSummoned()) {
                stand.getPowerType().forceUnsummon(standMob, stand);
            }
            MobPowerUtil.releaseInputsAndGrabs(mob);
            mob.discard();
            standMob.discard();
            cow.discard();
        }
    }

    // 1.16 050 StandAI clicked the time-stop blink (TimeStopInstant) whenever its conditions passed: the donor set the time
    // stop's training on every pick (AddonUtil.getListOfUnlockedStandActions :142-144), TimeStop.onTrainingPoints set the
    // blink's, which gave it the learning entry getAllUnlockedActions lists; TimeStopInstant.checkSpecificConditions refused
    // it in stopped time and while the Stand had a task. Time Resume never got an entry (TimeResume.isUnlocked is always
    // true, so StandType.unlockNewActions skipped it), so no 1.16 mob clicked it.
    @GameTest(template = "empty", timeoutTicks = 160, batch = "rotp_mwp_time_stop_variations")
    public static void aTimeStopMobsPickReachesTheBlinkButNeverTimeResumeAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        // the husk, its Stand and the cow tick in the template's chunk, the blink behind the cow included
        Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(1.5D, 2.0D, 1.5D), -0.5D, 3.5D, -0.5D, 9.0D);
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        standStill(cow, pos.add(0.0D, 0.0D, 4.0D));
        level.addFreshEntity(mob);
        level.addFreshEntity(cow);
        helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                        && MobPowerUtil.grantStand(mob, ModStands.THE_WORLD.get(), true),
                "Could not give the husk The World.");
        StandPower power = StandPower.get(mob);
        power.setResolveLevel(2);
        power.setStamina(power.getMaxStamina());
        TimeStopState state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        Runnable cleanup = () -> {
            state.removeInstance(mob.getId());
            mob.removeEffect(ModStatusEffects.TIME_STOP);
            if (power.isSummoned()) {
                StandEntity stand = power.getSummonedStandEntity();
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
                }
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            cow.discard();
        };
        double[] before = new double[1];
        int[] staleTicks = new int[1];
        stages(helper, cleanup,
                () -> {
                    helper.assertTrue(power.isAbilityUnlocked("time_stop"),
                            "Setup: a Resolve 2 The World husk has not unlocked its time stop.");
                    helper.assertTrue(power.getPowerType().summon(mob, power),
                            "Could not summon the husk's The World.");
                    StandEntity stand = power.getSummonedStandEntity();
                    helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
                    stand.summonLockTicks = 0;
                    mob.setTarget(cow);
                    lookAt(mob, cow);
                    return true;
                },
                () -> {
                    if (power.getSummonedStandEntity().getCurStandAction() != null) {
                        return false;
                    }
                    // the stopper moves in its own stop with the time stop effect, as the core's time stop gives it
                    mob.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 60, 0, false, false, true));
                    helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(mob.getId(), 60, 60,
                                    mob.chunkPosition(), 0, mob.getId(), "the_world_time_stop")),
                            "Setup: the husk's time stop did not start.");
                    return true;
                },
                () -> {
                    Map<String, Ability> pool = pool(power);
                    helper.assertTrue(pool.get("time_stop") != null && !pool.containsKey("time_stop_blink"),
                            "1.16 TimeStopInstant.checkSpecificConditions refused the blink in stopped time; the husk's pool"
                                    + " in its own stop is " + pool.keySet() + ".");
                    Ability timeResume = ModStandAbilities.TIME_RESUME.get()._defaultAbilityInstance;
                    helper.assertFalse(MobPowerUtil.isMobSafeAbility(power, timeResume),
                            "No 1.16 mob clicked Time Resume (TimeResume.isUnlocked is always true, so"
                                    + " StandType.unlockNewActions gave it no learning entry); it entered mob logic.");
                    state.removeInstance(mob.getId());
                    mob.removeEffect(ModStatusEffects.TIME_STOP);
                    return true;
                },
                () -> {
                    // the stop's end cooled the blink down for its few ticks
                    Ability blink = power.getAbility("time_stop_blink");
                    helper.assertTrue(blink != null, "Setup: The World has no time-stop blink.");
                    if (power.getSummonedStandEntity().getCurStandAction() != null
                            || power.isAbilityOnCooldown("time_stop_blink")
                            || !blink.checkConditions(power).isPositive()) {
                        return false;
                    }
                    Map<String, Ability> pool = pool(power);
                    // the moves and their checks are cached once a tick, possibly before this tick's last change
                    if (pool.get("time_stop_blink") == null && ++staleTicks[0] < 5) {
                        return false;
                    }
                    helper.assertTrue(pool.get("time_stop_blink") != null,
                            "1.16 StandAI's random pick reached the time-stop blink (TimeStopInstant) outside stopped time"
                                    + " with the Stand idle; the husk's pool is " + pool.keySet() + " for "
                                    + staleTicks[0] + " ticks while the blink's own check passed.");
                    before[0] = mob.distanceTo(cow);
                    boolean pressed = new StandPowerMobGoal(mob).performNamed(power, pool, "time_stop_blink", null, cow);
                    helper.assertTrue(pressed && mob.distanceTo(cow) < before[0] - 1.0D
                                    && power.isAbilityOnCooldown("time_stop_blink"),
                            "1.16 StandAI clicked the time-stop blink at its target: the husk moved from " + before[0]
                                    + " to " + mob.distanceTo(cow) + " blocks of the cow, pressed " + pressed
                                    + ", blink cooldown " + power.isAbilityOnCooldown("time_stop_blink") + ".");
                    return true;
                });
    }

    // 1.16 TimeStop.perform gave the time stop its own cooldown for as many ticks as its stop (getCooldownTechnical :196-198,
    // Action.setCooldownOnUse), so 050 StandAI's click of it during the mob's own stop was refused by
    // PowerBaseImpl.checkRequirements and the stop went on; StandAI.performAction set its own cooldown (getCooldownTechnical,
    // the stop's ticks, + 10) all the same, and UseRandomAction's click was refused too. No 1.16 mob had Time Resume.
    @GameTest(template = "empty", timeoutTicks = 80, batch = "rotp_mwp_time_stop_own_stop")
    public static void aTimeStopMobsPressInItsOwnStopLetsTheStopGoOnAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(1.5D, 2.0D, 1.5D), -0.5D, 3.5D, -0.5D, 5.5D);
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        standStill(cow, pos.add(0.0D, 0.0D, 3.0D));
        level.addFreshEntity(mob);
        level.addFreshEntity(cow);
        helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                        && MobPowerUtil.grantStand(mob, ModStands.THE_WORLD.get(), true),
                "Could not give the husk The World.");
        StandPower power = StandPower.get(mob);
        power.setResolveLevel(2);
        power.setStamina(power.getMaxStamina());
        TimeStopState state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        Runnable cleanup = () -> {
            MobPowerUtil.releaseInputsAndGrabs(mob);
            state.removeInstance(mob.getId());
            mob.removeEffect(ModStatusEffects.TIME_STOP);
            if (power.isSummoned()) {
                StandEntity stand = power.getSummonedStandEntity();
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
                }
                power.getPowerType().forceUnsummon(mob, power);
            }
            LivingComponentAction.getComponent(mob).setAction(null, mob, SyncType.NO_SYNC);
            mob.discard();
            cow.discard();
        };
        int[] waited = new int[1];
        stages(helper, cleanup,
                () -> {
                    Ability timeStop = power.getAbility("time_stop");
                    helper.assertTrue(power.isAbilityUnlocked("time_stop") && timeStop instanceof TimeStopAbility,
                            "Setup: a Resolve 2 The World husk has not unlocked its time stop.");
                    mob.setTarget(cow);
                    mob.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 60, 0, false, false, true));
                    helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(mob.getId(), 60, 60,
                                    mob.chunkPosition(), 0, mob.getId(), "the_world_time_stop")),
                            "Setup: the husk's time stop did not start.");
                    int ticksLeft = state.getInstance(mob.getId()).orElseThrow().ticksLeft();
                    StandPowerMobGoal goal = new StandPowerMobGoal(mob);
                    int stopTicks = TimeStopLearning.getTimeStopTicks(
                            power, ((TimeStopAbility) timeStop).getLearningAbilityName());
                    helper.assertTrue(goal.mobCooldown(power, timeStop) == stopTicks + 10,
                            "1.16 StandAI gave a time stop it clicked its cooldown, the stop's ticks (" + stopTicks
                                    + "), + 10; the goal gives " + goal.mobCooldown(power, timeStop) + ".");
                    boolean pressed = goal.performNamed(power, Map.of("time_stop", timeStop), "time_stop", null, cow);
                    TimeStopState.Instance stop = state.getInstance(mob.getId()).orElse(null);
                    helper.assertTrue(pressed && stop != null && stop.ticksLeft() == ticksLeft && !stop.ticksManuallySet(),
                            "1.16 refused a mob's time stop click during its own stop (the time stop's own cooldown ran"
                                    + " as long as the stop) and the stop went on; pressed " + pressed + ", the stop "
                                    + stop + ".");
                    helper.assertFalse(goal.isAvailable(power, timeStop, null, cow),
                            "1.16 StandAI.performAction set its cooldown of the refused time stop click.");
                    boolean manual = ManualHeldInput.press(timeStop, mob);
                    stop = state.getInstance(mob.getId()).orElse(null);
                    helper.assertTrue(!manual && stop != null && stop.ticksLeft() == ticksLeft
                                    && !stop.ticksManuallySet(),
                            "1.16 UseRandomAction's click of the time stop during the mob's own stop was refused too;"
                                    + " pressed " + manual + ", the stop " + stop + ".");
                    state.removeInstance(mob.getId());
                    mob.removeEffect(ModStatusEffects.TIME_STOP);
                    return true;
                },
                () -> {
                    if (power.isAbilityOnCooldown("time_stop") && ++waited[0] < 40) {
                        return false;
                    }
                    Ability timeStop = power.getAbility("time_stop");
                    helper.assertFalse(MobPowerUtil.resumesOwnTimeStop(mob, timeStop),
                            "Outside its own stop the husk's time stop press counts as a resume; 1.16 clicked it.");
                    boolean pressed = new StandPowerMobGoal(mob).performNamed(
                            power, Map.of("time_stop", timeStop), "time_stop", null, cow);
                    helper.assertTrue(pressed && mob.getPersistentData().contains(MobPowerUtil.STAND_HELD_KEY_TAG),
                            "Outside its own stop 1.16 StandAI held the husk's time stop; the press held no key"
                                    + " (cooldown " + power.isAbilityOnCooldown("time_stop") + ").");
                    return true;
                });
    }

    // 1.16 050 StandAI.performAction (:182) and NonStandAI.performAction (:212) set the mob's own cooldown of the action
    // to its getCooldownTechnical + 10 (setCooldown :238-240), not its whole cooldown: Hierophant Green's string bind
    // cooldown(25, 100, 0.5F) and concentrated Emerald Splash cooldown(5, 60) (ModStandsInit :366, :378), Hamon's Zoom
    // Punch cooldown(14, 0) (ModHamonActions :72), a move without a technical part 0.
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aPowerMobPicksAMoveAgainItsTechnicalCooldownPlusTenTicksLaterAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob standMob = EntityType.HUSK.create(level);
        Mob hamonMob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(standMob != null && hamonMob != null && cow != null, "Missing mob fixtures");
        // both husks count their ticks in the template's chunk
        Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), 1.0D, 6.0D, 1.0D, 5.5D);
        standMob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        standMob.setNoAi(true);
        standMob.setNoGravity(true);
        hamonMob.moveTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        hamonMob.setNoAi(true);
        hamonMob.setNoGravity(true);
        standStill(cow, pos.add(1.0D, 0.0D, 2.0D));
        level.addFreshEntity(standMob);
        level.addFreshEntity(hamonMob);
        level.addFreshEntity(cow);
        helper.assertTrue(MobPowerUtil.isEligibleMob(standMob)
                        && MobPowerUtil.grantStand(standMob, ModStands.HIEROPHANT_GREEN.get(), true),
                "Could not give the husk Hierophant Green.");
        StandPower stand = StandPower.get(standMob);
        stand.setStamina(stand.getMaxStamina());
        MobPowerUtil.attachPowers(hamonMob);
        PlayerPower hamon = PowerClass.PLAYER_POWER.attachGet(hamonMob);
        helper.assertTrue(hamon.trySetPowerType(ModPlayerPowers.HAMON.get()), "Could not give the second husk Hamon.");
        Runnable cleanup = () -> {
            if (stand.isSummoned()) {
                StandEntity standEntity = stand.getSummonedStandEntity();
                if (standEntity != null) {
                    LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
                }
                stand.getPowerType().forceUnsummon(standMob, stand);
            }
            MobPowerUtil.releaseInputsAndGrabs(standMob);
            MobPowerUtil.releaseInputsAndGrabs(hamonMob);
            LivingComponentAction.getComponent(hamonMob).setAction(null, hamonMob, SyncType.NO_SYNC);
            standMob.discard();
            hamonMob.discard();
            cow.discard();
        };
        StandPowerMobGoal standGoal = new StandPowerMobGoal(standMob);
        PlayerPowerMobGoal hamonGoal = new PlayerPowerMobGoal(hamonMob);
        Ability[] moves = new Ability[2];
        int[] pressedAt = new int[2];
        int[] availableAfter = { -1, -1 };
        int[] waited = new int[1];
        stages(helper, cleanup,
                () -> {
                    Ability bind = stand.getAbility("string_bind");
                    Ability splash = stand.getAbility("emerald_splash_concentrated");
                    Ability zoomPunch = hamon.getAbility("zoom_punch");
                    Ability overdrive = hamon.getAbility("hamon_overdrive");
                    helper.assertTrue(bind != null && splash != null && zoomPunch != null && overdrive != null
                                    && bind.getCooldown(stand, 0) > 100 && splash.getCooldown(stand, 0) > 60,
                            "Setup: Hierophant Green's string bind and concentrated Emerald Splash or Hamon's Zoom Punch"
                                    + " and Overdrive are missing or lack their long cooldowns.");
                    List<String> wrong = new java.util.ArrayList<>();
                    expectWait(wrong, "string_bind", standGoal.mobCooldown(stand, bind), 35);
                    expectWait(wrong, "emerald_splash_concentrated", standGoal.mobCooldown(stand, splash), 15);
                    expectWait(wrong, "zoom_punch", hamonGoal.mobCooldown(hamon, zoomPunch), 24);
                    expectWait(wrong, "hamon_overdrive", hamonGoal.mobCooldown(hamon, overdrive), 10);
                    helper.assertTrue(wrong.isEmpty(), "1.16 StandAI and NonStandAI waited getCooldownTechnical + 10"
                            + " after an action: " + String.join("; ", wrong) + ".");
                    standMob.setTarget(cow);
                    hamonMob.setTarget(cow);
                    moves[0] = bind;
                    moves[1] = zoomPunch;
                    helper.assertTrue(standGoal.performNamed(stand, Map.of("string_bind", bind), "string_bind", null, cow)
                                    && hamonGoal.performNamed(
                                            hamon, Map.of("zoom_punch", zoomPunch), "zoom_punch", null, cow),
                            "Setup: the husks did not press the string bind and the Zoom Punch.");
                    pressedAt[0] = standMob.tickCount;
                    pressedAt[1] = hamonMob.tickCount;
                    if (stand.isSummoned()) {
                        LivingComponentAction.getComponent(stand.getSummonedStandEntity())
                                .setAction(null, standMob, SyncType.NO_SYNC);
                    }
                    return true;
                },
                () -> {
                    if (stand.isSummoned()) {
                        LivingComponentAction.getComponent(stand.getSummonedStandEntity())
                                .setAction(null, standMob, SyncType.NO_SYNC);
                    }
                    standGoal.expireCooldowns();
                    hamonGoal.expireCooldowns();
                    if (availableAfter[0] < 0 && standGoal.isAvailable(stand, moves[0], null, cow)) {
                        availableAfter[0] = standMob.tickCount - pressedAt[0];
                    }
                    if (availableAfter[1] < 0 && hamonGoal.isAvailable(hamon, moves[1], null, cow)) {
                        availableAfter[1] = hamonMob.tickCount - pressedAt[1];
                    }
                    if ((availableAfter[0] < 0 || availableAfter[1] < 0) && ++waited[0] < 60) {
                        return false;
                    }
                    helper.assertTrue(availableAfter[0] == 35 && availableAfter[1] == 24,
                            "1.16 StandAI picked Hierophant Green's string bind again 35 ticks after it (technical 25 + 10)"
                                    + " and NonStandAI Hamon's Zoom Punch 24 ticks after it (14 + 10); the husks could"
                                    + " pick them after " + availableAfter[0] + " and " + availableAfter[1]
                                    + " ticks (-1: not within " + waited[0] + ").");
                    return true;
                });
    }

    /*
     * 1.16 050 StandAI, NonStandAI and UseRandomAction checked and pressed every action at AddonUtil.getActionTarget
     * (:206-211), what a 3-block ray along the mob's look met first, not at the mob's attack target, and
     * PowerBaseImpl.checkTarget refused a press its target did not suit: Hamon Overdrive wanted a living target
     * (TargetRequirement.ENTITY, 8 blocks), Gold Experience's Bone Meal a baby animal (GoldExperienceBoneMeal.checkTarget).
     * Row 0: a Hamon husk whose target cow stands 5 blocks ahead on its look line, a Gold Experience husk looking at an
     * adult cow while its target is a calf: neither press goes off. Row 1: the cow 2 blocks ahead, the Gold Experience
     * husk looking at the calf while its target is the adult: both go off at what the look met, and the cow is struck.
     * Row 2: UseRandomAction's press (the Debug Stand's, /rotp_mwp use_random) clicked through checkRequirements: Overdrive
     * with the cow 5 blocks ahead and the aim left on it aims along the look at nothing and is refused (nothing runs, no
     * cooldown, the cow is not struck); with the cow 2 blocks ahead and the aim left on nothing it goes off and strikes it.
     * Row 3: the Gold Experience husk's Bone Meal pressed so while it looks at the adult cow is refused; at the calf it
     * goes off there.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "rotp_mwp_look_aim")
    public static void aPowerMobPressesEveryMoveAtWhatItsLookMeetsAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob hamonMob = EntityType.HUSK.create(level);
        Mob standMob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        Cow adult = EntityType.COW.create(level);
        Cow calf = EntityType.COW.create(level);
        helper.assertTrue(hamonMob != null && standMob != null && cow != null && adult != null && calf != null,
                "Missing mob fixtures");
        // the moves run between the stages: every fixture stays in the template's chunk
        Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), 1.0D, 10.0D, 1.0D, 9.0D);
        Vec3 standPos = pos.add(4.0D, 0.0D, 0.0D);
        for (Mob mob : List.of(hamonMob, standMob)) {
            Vec3 at = mob == hamonMob ? pos : standPos;
            mob.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            turn(mob, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            level.addFreshEntity(mob);
        }
        calf.setBaby(true);
        standAtEyeHeight(cow, hamonMob, pos.add(0.0D, 0.0D, 5.0D));
        standAtEyeHeight(adult, standMob, standPos.add(0.0D, 0.0D, 2.0D));
        standAtEyeHeight(calf, standMob, standPos.add(2.0D, 0.0D, 0.0D));
        level.addFreshEntity(cow);
        level.addFreshEntity(adult);
        level.addFreshEntity(calf);
        MobPowerUtil.attachPowers(hamonMob);
        PlayerPower hamon = PowerClass.PLAYER_POWER.attachGet(hamonMob);
        helper.assertTrue(hamon.trySetPowerType(ModPlayerPowers.HAMON.get()), "Could not give the husk Hamon.");
        HamonData data = hamon.getCurTypeData(ModPlayerPowers.HAMON).orElseThrow();
        data.learnSkill(ModHamonSkills.OVERDRIVE.get());
        helper.assertTrue(MobPowerUtil.isEligibleMob(standMob)
                        && MobPowerUtil.grantStand(standMob, ModStands.GOLD_EXPERIENCE.get(), true),
                "Could not give the second husk Gold Experience.");
        StandPower stand = StandPower.get(standMob);
        stand.setStamina(stand.getMaxStamina());
        Runnable cleanup = () -> {
            if (stand.isSummoned()) {
                StandEntity standEntity = stand.getSummonedStandEntity();
                if (standEntity != null) {
                    LivingComponentAction.getComponent(standEntity).setAction(null, standMob, SyncType.NO_SYNC);
                }
                stand.getPowerType().forceUnsummon(standMob, stand);
            }
            MobPowerUtil.releaseInputsAndGrabs(hamonMob);
            MobPowerUtil.releaseInputsAndGrabs(standMob);
            LivingComponentAction.getComponent(hamonMob).setAction(null, hamonMob, SyncType.NO_SYNC);
            hamonMob.discard();
            standMob.discard();
            cow.discard();
            adult.discard();
            calf.discard();
        };
        int[] waited = new int[1];
        stages(helper, cleanup,
                () -> {
                    Ability overdrive = hamon.getAbility("hamon_overdrive");
                    helper.assertTrue(data.isSkillLearned(ModHamonSkills.OVERDRIVE.get()) && overdrive != null,
                            "Setup: the Hamon husk has not learned Hamon Overdrive.");
                    data.setBreathStability(data.getMaxBreathStability());
                    data.setEnergy(data.getMaxEnergy());
                    helper.assertTrue(stand.getPowerType().summon(standMob, stand),
                            "Could not summon the husk's Gold Experience.");
                    StandEntity standEntity = stand.getSummonedStandEntity();
                    helper.assertTrue(standEntity != null, "Setup: the husk has no Stand.");
                    standEntity.summonLockTicks = 0;
                    hamonMob.setTarget(cow);
                    standMob.setTarget(calf);
                    // the Stand ticks off its husk's look line, where it was summoned
                    return true;
                },
                () -> {
                    Ability overdrive = hamon.getAbility("hamon_overdrive");
                    StandEntity standEntity = stand.getSummonedStandEntity();
                    PlayerPowerMobGoal hamonGoal = new PlayerPowerMobGoal(hamonMob);
                    StandPowerMobGoal standGoal = new StandPowerMobGoal(standMob);
                    hamonGoal.tick();
                    standGoal.tick();
                    expectAims(helper, hamonMob, standMob, standEntity, null, adult,
                            "the Hamon husk's target cow 5 blocks ahead; the Gold Experience husk's look on the adult"
                                    + " cow, its target the calf");
                    boolean pressed = hamonGoal.performNamed(
                            hamon, Map.of("hamon_overdrive", overdrive), "hamon_overdrive", null, cow);
                    EntityActionInstance started = LivingComponentAction.getCurEntityAction(hamonMob);
                    helper.assertTrue(pressed && (started == null || started.ability != overdrive),
                            "1.16 NonStandAI pressed Hamon Overdrive at what the husk's look met, nothing within 3"
                                    + " blocks, which checkTarget refused; it runs " + started
                                    + " at its target cow 5 blocks away.");
                    Ability boneMeal = stand.getAbility("bone_meal");
                    pressed = standGoal.performNamed(stand, Map.of("bone_meal", boneMeal), "bone_meal", null, calf);
                    started = LivingComponentAction.getComponent(standEntity).getAction();
                    helper.assertTrue(pressed && started == null,
                            "1.16 StandAI pressed Bone Meal at the adult cow the husk's look met, which"
                                    + " GoldExperienceBoneMeal.checkTarget refused, not at its target calf; the Stand"
                                    + " runs " + started + ".");
                    return true;
                },
                () -> {
                    helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                            "1.16 never struck the cow 5 blocks ahead of the Hamon husk; it has " + cow.getHealth()
                                    + " of " + cow.getMaxHealth() + " health.");
                    standAtEyeHeight(cow, hamonMob, pos.add(0.0D, 0.0D, 2.0D));
                    turn(standMob, -90.0F, 0.0F);
                    standMob.setTarget(adult);
                    StandEntity standEntity = stand.getSummonedStandEntity();
                    standEntity.updatePosition(standMob);
                    PlayerPowerMobGoal hamonGoal = new PlayerPowerMobGoal(hamonMob);
                    StandPowerMobGoal standGoal = new StandPowerMobGoal(standMob);
                    hamonGoal.tick();
                    standGoal.tick();
                    expectAims(helper, hamonMob, standMob, standEntity, cow, calf,
                            "the Hamon husk's target cow 2 blocks ahead; the Gold Experience husk's look on the calf,"
                                    + " its target the adult cow");
                    Ability overdrive = hamon.getAbility("hamon_overdrive");
                    boolean pressed = hamonGoal.performNamed(
                            hamon, Map.of("hamon_overdrive", overdrive), "hamon_overdrive", null, cow);
                    EntityActionInstance started = LivingComponentAction.getCurEntityAction(hamonMob);
                    helper.assertTrue(pressed && started != null && started.ability == overdrive,
                            "1.16 NonStandAI pressed Hamon Overdrive at the cow the husk's look met 2 blocks ahead;"
                                    + " it runs " + started + ".");
                    Ability boneMeal = stand.getAbility("bone_meal");
                    pressed = standGoal.performNamed(stand, Map.of("bone_meal", boneMeal), "bone_meal", null, adult);
                    started = LivingComponentAction.getComponent(standEntity).getAction();
                    helper.assertTrue(pressed && started != null && started.ability == boneMeal
                                    && started.standRotationTarget != null
                                    && started.standRotationTarget.getEntity() == calf,
                            "1.16 StandAI pressed Bone Meal at the calf the husk's look met, not at its target adult"
                                    + " cow; the Stand runs " + started + (started != null
                                            ? " turned to " + started.standRotationTarget : "") + ".");
                    return true;
                },
                () -> {
                    if (cow.getHealth() >= cow.getMaxHealth() && ++waited[0] < 20) {
                        return false;
                    }
                    helper.assertTrue(cow.getHealth() < cow.getMaxHealth(),
                            "1.16 struck the cow the Hamon husk's look met 2 blocks ahead with Hamon Overdrive; it is"
                                    + " unharmed after " + waited[0] + " ticks.");
                    waited[0] = 0;
                    return true;
                },
                () -> {
                    EntityActionInstance running = LivingComponentAction.getCurEntityAction(hamonMob);
                    if ((running != null && !running.isOver() || data.isAbilityOnCooldown("hamon_overdrive"))
                            && ++waited[0] < 60) {
                        return false;
                    }
                    standAtEyeHeight(cow, hamonMob, pos.add(0.0D, 0.0D, 5.0D));
                    cow.setHealth(cow.getMaxHealth());
                    data.setBreathStability(data.getMaxBreathStability());
                    data.setEnergy(data.getMaxEnergy());
                    // as an aim on the attack target would leave it
                    LivingComponentAction.getComponent(hamonMob).entityAim.setTarget(new ActionTarget(cow));
                    boolean manual = ManualHeldInput.press(hamon.getAbility("hamon_overdrive"), hamonMob);
                    ActionTarget aim = LivingComponentAction.getAimTarget(hamonMob);
                    helper.assertTrue(aim.getType() == ActionTarget.TargetType.EMPTY,
                            "1.16 UseRandomAction pressed at AddonUtil.getActionTarget, nothing within 3 blocks of the"
                                    + " Hamon husk's look; the Debug Stand's press aims at " + describe(aim) + ".");
                    EntityActionInstance after = LivingComponentAction.getCurEntityAction(hamonMob);
                    helper.assertTrue(!manual && after == running && !data.isAbilityOnCooldown("hamon_overdrive")
                                    && !ManualHeldInput.isHolding(hamonMob, PowerClass.PLAYER_POWER),
                            "1.16 UseRandomAction clicked Hamon Overdrive through PowerBaseImpl.checkRequirements, whose"
                                    + " checkTarget refused nothing to strike: nothing ran and no cooldown was set;"
                                    + " pressed " + manual + ", the husk runs " + after + ", on cooldown "
                                    + data.isAbilityOnCooldown("hamon_overdrive") + ".");
                    waited[0] = 0;
                    return true;
                },
                () -> {
                    if (++waited[0] < 5) {
                        helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                                "1.16 UseRandomAction's Hamon Overdrive at nothing struck no one; the cow 5 blocks"
                                        + " ahead has " + cow.getHealth() + " of " + cow.getMaxHealth() + " health.");
                        return false;
                    }
                    standAtEyeHeight(cow, hamonMob, pos.add(0.0D, 0.0D, 2.0D));
                    // the press is checked where it aims, not where the aim was left
                    LivingComponentAction.getComponent(hamonMob).entityAim.setTarget(ActionTarget.EMPTY);
                    Ability overdrive = hamon.getAbility("hamon_overdrive");
                    boolean manual = ManualHeldInput.press(overdrive, hamonMob);
                    EntityActionInstance started = LivingComponentAction.getCurEntityAction(hamonMob);
                    helper.assertTrue(manual && started != null && started.ability == overdrive,
                            "1.16 UseRandomAction's click of Hamon Overdrive at the cow the husk's look met 2 blocks"
                                    + " ahead passed checkRequirements; pressed " + manual + ", the husk runs "
                                    + started + ".");
                    waited[0] = 0;
                    return true;
                },
                () -> {
                    if (cow.getHealth() >= cow.getMaxHealth() && ++waited[0] < 20) {
                        return false;
                    }
                    helper.assertTrue(cow.getHealth() < cow.getMaxHealth(),
                            "1.16 UseRandomAction's Hamon Overdrive struck the cow the husk's look met 2 blocks ahead;"
                                    + " it is unharmed after " + waited[0] + " ticks.");
                    waited[0] = 0;
                    return true;
                },
                () -> {
                    StandEntity standEntity = stand.getSummonedStandEntity();
                    EntityActionInstance running = LivingComponentAction.getComponent(standEntity).getAction();
                    if ((running != null && !running.isOver() || stand.isAbilityOnCooldown("bone_meal"))
                            && ++waited[0] < 60) {
                        return false;
                    }
                    turn(standMob, 0.0F, 0.0F);
                    standEntity.updatePosition(standMob);
                    Ability boneMeal = stand.getAbility("bone_meal");
                    boolean manual = ManualHeldInput.press(boneMeal, standMob);
                    ActionTarget aim = LivingComponentAction.getAimTarget(standMob);
                    EntityActionInstance after = LivingComponentAction.getComponent(standEntity).getAction();
                    helper.assertTrue(!manual && aim.getEntity() == adult && after == running
                                    && !stand.isAbilityOnCooldown("bone_meal")
                                    && !ManualHeldInput.isHolding(standMob, PowerClass.STAND),
                            "1.16 UseRandomAction clicked Bone Meal at the adult cow the husk's look met, which"
                                    + " GoldExperienceBoneMeal.checkTarget refused; pressed " + manual + " at "
                                    + describe(aim) + ", the Stand runs " + after + ", on cooldown "
                                    + stand.isAbilityOnCooldown("bone_meal") + ".");
                    calf.setBaby(true);
                    turn(standMob, -90.0F, 0.0F);
                    standEntity.updatePosition(standMob);
                    manual = ManualHeldInput.press(boneMeal, standMob);
                    EntityActionInstance started = LivingComponentAction.getComponent(standEntity).getAction();
                    helper.assertTrue(manual && started != null && started.ability == boneMeal
                                    && started.standRotationTarget != null
                                    && started.standRotationTarget.getEntity() == calf,
                            "1.16 UseRandomAction clicked Bone Meal at the calf the husk's look met; pressed " + manual
                                    + ", the Stand runs " + started + (started != null
                                            ? " turned to " + started.standRotationTarget : "") + ".");
                    return true;
                });
    }

    // the aims of the Hamon husk, the Gold Experience husk and its Stand after a tick of their goals
    private static void expectAims(GameTestHelper helper, Mob hamonMob, Mob standMob, StandEntity standEntity,
            @Nullable Entity hamonSees, Entity standSees, String setup) {
        ActionTarget hamonAim = LivingComponentAction.getAimTarget(hamonMob);
        ActionTarget standAim = LivingComponentAction.getAimTarget(standMob);
        ActionTarget standEntityAim = LivingComponentAction.getAimTarget(standEntity);
        boolean hamonRight = hamonSees != null
                ? hamonAim.getEntity() == hamonSees
                : hamonAim.getType() == ActionTarget.TargetType.EMPTY;
        helper.assertTrue(hamonRight && standAim.getEntity() == standSees && standEntityAim.getEntity() == standSees,
                "1.16 050 checked and pressed every action at AddonUtil.getActionTarget, what the look met within 3"
                        + " blocks (" + setup + "); the Hamon husk aims at " + describe(hamonAim)
                        + ", the Gold Experience husk at " + describe(standAim) + " and its Stand at "
                        + describe(standEntityAim) + ".");
    }

    /*
     * 1.16 050 NonStandAI pressed every action at AddonUtil.getActionTarget (:206-211), what a 3-block ray along the
     * mob's look met first, entity or block, and setHeldAction kept that target as the power's mouse target through the
     * hold and its release; HamonSendoOverdrive asked for no target (TargetRequirement.NONE) and stoppedHolding sent its
     * wave only when that target was a block. Row 0: a Hamon husk looking at a stone wall 1.5 blocks ahead, its target cow
     * off to the side: Sendo Overdrive is pressed at the wall, held there while the cow steps onto the look line, and
     * when the goal lets go of the full charge the wave comes from the wall and the energy is spent; once Sendo is over
     * the aim follows the look again. Row 1: the
     * cow on its look line before the wall: pressed and held at the cow, let go of at the full charge, no wave. Row 2:
     * UseRandomAction's setHeldAction (the Debug Stand's press) at the wall keeps it while the cow steps onto the look
     * line and the goal ticks, and the wave comes from the wall.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "rotp_mwp_sendo_look")
    public static void aHamonMobPressesSendoOverdriveAtTheBlockItsLookMeetsAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        // the husk's action ticks between the stages: the husk, the cow and the wall stay in the template's chunk
        Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), 1.0D, 6.0D, 1.0D, 5.5D);
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setYHeadRot(0.0F);
        mob.yHeadRotO = 0.0F;
        mob.setNoAi(true);
        mob.setNoGravity(true);
        standStill(cow, pos.add(2.5D, 0.0D, 0.0D));
        level.addFreshEntity(mob);
        level.addFreshEntity(cow);
        MobPowerUtil.attachPowers(mob);
        PlayerPower hamon = PowerClass.PLAYER_POWER.attachGet(mob);
        helper.assertTrue(hamon.trySetPowerType(ModPlayerPowers.HAMON.get()), "Could not give the husk Hamon.");
        var data = hamon.getCurTypeData(ModPlayerPowers.HAMON).orElseThrow();
        helper.assertTrue(data.learnSkill(ModHamonSkills.SENDO_OVERDRIVE.get()), "Could not learn Sendo Overdrive.");
        Ability sendo = hamon.getAbility("sendo_overdrive");
        helper.assertTrue(sendo instanceof HamonSendoOverdriveAbility, "Setup: Hamon has no sendo_overdrive.");
        BlockPos wall = BlockPos.containing(mob.getEyePosition()).south(2);
        BlockState wallWas = level.getBlockState(wall);
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        mob.setTarget(cow);
        AABB wallArea = new AABB(wall).inflate(1.0D);
        Runnable cleanup = () -> {
            LivingComponentAction.getComponent(mob).setAction(null, mob, SyncType.NO_SYNC);
            MobPowerUtil.releaseInputsAndGrabs(mob);
            level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, wallArea.inflate(4.0D)).forEach(Entity::discard);
            mob.discard();
            cow.discard();
            level.setBlockAndUpdate(wall, wallWas);
        };
        PlayerPowerMobGoal[] goal = new PlayerPowerMobGoal[1];
        EntityActionInstance[] started = new EntityActionInstance[1];
        float[] energyBefore = new float[1];
        int[] ticks = new int[1];
        stages(helper, cleanup,
                () -> pressSendo(helper, mob, cow, hamon, data, sendo, goal, started, energyBefore, wall, true),
                () -> {
                    // the cow steps onto the look line before the wall while the charge is held
                    if (ticks[0] == 0) {
                        standStill(cow, new Vec3(pos.x, mob.getEyeY() - cow.getBbHeight() / 2.0D, pos.z + 1.0D));
                    }
                    return holdUntilTheGoalLetsGo(helper, mob, goal[0], started[0], wall, ticks);
                },
                () -> {
                    List<HamonSendoOverdriveEntity> waves =
                            level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, wallArea);
                    float spent = energyBefore[0] - data.getEnergy();
                    if (waves.isEmpty() && ++ticks[0] < 5) {
                        return false;
                    }
                    helper.assertTrue(!waves.isEmpty() && spent > 0.0F,
                            "1.16 stoppedHolding sent Sendo Overdrive's wave at the wall the husk's look met, the target"
                                    + " setHeldAction kept; after the goal let go of the full charge the husk aims at "
                                    + describe(LivingComponentAction.getAimTarget(mob)) + ", " + waves.size()
                                    + " waves at the wall, energy spent " + spent + ".");
                    waves.forEach(Entity::discard);
                    ticks[0] = 0;
                    return true;
                },
                () -> {
                    // the press keeps the wall only while its action runs; then the aim follows the look again
                    if (!started[0].isOver() && ++ticks[0] < 10) {
                        return false;
                    }
                    standStill(cow, new Vec3(pos.x, mob.getEyeY() - cow.getBbHeight() / 2.0D, pos.z + 1.0D));
                    goal[0].tick();
                    ActionTarget aim = LivingComponentAction.getAimTarget(mob);
                    helper.assertTrue(started[0].isOver() && aim.getEntity() == cow,
                            "Once Sendo Overdrive is over 1.16 checked the husk's next moves at what its look met, the"
                                    + " cow now before the wall; over " + started[0].isOver() + ", it aims at "
                                    + describe(aim) + ".");
                    ticks[0] = 0;
                    return true;
                },
                () -> {
                    ActionTarget look = new PlayerPowerMobGoal(mob).lookTarget();
                    helper.assertTrue(look.getType() == ActionTarget.TargetType.ENTITY && look.getEntity() == cow,
                            "1.16 AddonUtil.getActionTarget took the first entity on the look ray; with the cow before"
                                    + " the wall it is " + describe(look) + ".");
                    return pressSendo(helper, mob, cow, hamon, data, sendo, goal, started, energyBefore, wall, false);
                },
                () -> holdUntilTheGoalLetsGo(helper, mob, goal[0], started[0], null, ticks),
                () -> {
                    if (++ticks[0] < 5) {
                        return false;
                    }
                    List<HamonSendoOverdriveEntity> waves =
                            level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, wallArea.inflate(4.0D));
                    helper.assertTrue(waves.isEmpty(),
                            "1.16 stoppedHolding sent no wave when the kept target was a mob; let go of at the cow, the"
                                    + " husk sent " + waves.size() + " waves.");
                    ticks[0] = 0;
                    return true;
                },
                () -> {
                    if (!started[0].isOver() && ++ticks[0] < 20) {
                        return false;
                    }
                    standStill(cow, pos.add(2.5D, 0.0D, 0.0D));
                    data.setBreathStability(data.getMaxBreathStability());
                    data.setEnergy(data.getMaxEnergy());
                    energyBefore[0] = data.getEnergy();
                    helper.assertTrue(ManualHeldInput.press(sendo, mob),
                            "Setup: the Debug Stand's random press could not hold the husk's Sendo Overdrive.");
                    started[0] = LivingComponentAction.getCurEntityAction(mob);
                    ActionTarget aim = LivingComponentAction.getAimTarget(mob);
                    helper.assertTrue(started[0] != null && started[0].ability == sendo
                                    && aim.getType() == ActionTarget.TargetType.BLOCK && wall.equals(aim.getBlockPos()),
                            "1.16 UseRandomAction held Sendo Overdrive at AddonUtil.getActionTarget, the wall the husk's"
                                    + " look met; the husk runs " + started[0] + " aimed at " + describe(aim) + ".");
                    // its goal ticks on while the cow steps onto the look line
                    standStill(cow, new Vec3(pos.x, mob.getEyeY() - cow.getBbHeight() / 2.0D, pos.z + 1.0D));
                    goal[0] = new PlayerPowerMobGoal(mob);
                    ticks[0] = 0;
                    return true;
                },
                () -> {
                    if (started[0].isOver()) {
                        ticks[0] = 0;
                        return true;
                    }
                    goal[0].tick();
                    ActionTarget aim = LivingComponentAction.getAimTarget(mob);
                    helper.assertTrue(aim.getType() == ActionTarget.TargetType.BLOCK && wall.equals(aim.getBlockPos()),
                            "1.16 UseRandomAction's setHeldAction kept the wall as the mouse target through the hold and"
                                    + " its release; with the cow on its look line the husk aims at " + describe(aim)
                                    + " in " + started[0].getPhase() + ".");
                    helper.assertTrue(++ticks[0] < 80, "The manual hold of Sendo Overdrive did not end in 80 ticks.");
                    return false;
                },
                () -> {
                    List<HamonSendoOverdriveEntity> waves =
                            level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, wallArea);
                    float spent = energyBefore[0] - data.getEnergy();
                    if (waves.isEmpty() && ++ticks[0] < 5) {
                        return false;
                    }
                    helper.assertTrue(!waves.isEmpty() && spent > 0.0F,
                            "1.16 sent the wave of UseRandomAction's held Sendo Overdrive at the wall it kept; "
                                    + waves.size() + " waves at the wall, energy spent " + spent + ".");
                    return true;
                });
    }

    // a fresh goal presses Sendo Overdrive, aimed at what the husk's look meets
    private static boolean pressSendo(GameTestHelper helper, Mob mob, Cow cow, PlayerPower hamon,
            HamonData data, Ability sendo, PlayerPowerMobGoal[] goal, EntityActionInstance[] started,
            float[] energyBefore, BlockPos wall, boolean atWall) {
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(mob.blockPosition()),
                "Setup: the husk's chunk does not tick entities, so its action would not tick.");
        data.setBreathStability(data.getMaxBreathStability());
        data.setEnergy(data.getMaxEnergy());
        goal[0] = new PlayerPowerMobGoal(mob);
        // the goal's tick aims as before every pick
        goal[0].tick();
        ActionTarget look = goal[0].lookTarget();
        if (atWall) {
            helper.assertTrue(look.getType() == ActionTarget.TargetType.BLOCK && wall.equals(look.getBlockPos()),
                    "Setup: the husk's look meets " + describe(look) + ", not the wall " + wall + ".");
        }
        Map<String, Ability> pool = goal[0].pool(hamon);
        ActionTarget aim = LivingComponentAction.getAimTarget(mob);
        helper.assertTrue(pool.get("sendo_overdrive") == sendo && aim.equals(look),
                "1.16 050 checked every action at AddonUtil.getActionTarget, what the husk's look met ("
                        + describe(look) + "), and HamonSendoOverdrive asked for no target there; in the pick "
                        + pool.containsKey("sendo_overdrive") + ", the moves checked with the aim on "
                        + describe(aim) + ".");
        energyBefore[0] = data.getEnergy();
        helper.assertTrue(goal[0].performNamed(hamon, pool, "sendo_overdrive", null, cow),
                "Setup: the husk could not press Sendo Overdrive.");
        started[0] = LivingComponentAction.getCurEntityAction(mob);
        aim = LivingComponentAction.getAimTarget(mob);
        boolean atLook = atWall
                ? aim.getType() == ActionTarget.TargetType.BLOCK && wall.equals(aim.getBlockPos())
                : aim.getEntity() == cow;
        helper.assertTrue(started[0] != null && started[0].ability == sendo && !started[0].isOver() && atLook,
                "1.16 NonStandAI held Sendo Overdrive at what the husk's look met, " + describe(look)
                        + "; the husk runs " + started[0] + " aimed at " + describe(aim) + ".");
        return true;
    }

    // the goal ticks as the mob's would until it lets go of the full charge
    private static boolean holdUntilTheGoalLetsGo(GameTestHelper helper, Mob mob, PlayerPowerMobGoal goal,
            EntityActionInstance started, @Nullable BlockPos wall, int[] ticks) {
        goal.tick();
        if (started.getPhase() == ActionPhase.WINDUP && !started.isOver()) {
            ActionTarget aim = LivingComponentAction.getAimTarget(mob);
            helper.assertTrue(wall == null
                            || aim.getType() == ActionTarget.TargetType.BLOCK && wall.equals(aim.getBlockPos()),
                    "1.16 setHeldAction kept the pressed target through the hold; holding, the husk aims at "
                            + describe(aim) + ".");
            helper.assertTrue(++ticks[0] < 60, "The goal did not let go of Sendo Overdrive's full charge in "
                    + ticks[0] + " ticks; it is in " + started.getPhase() + " at tick " + started.getPhaseTick() + ".");
            return false;
        }
        ticks[0] = 0;
        return true;
    }

    private static String describe(ActionTarget target) {
        return target.getType() + " " + (target.getType() == ActionTarget.TargetType.BLOCK
                ? target.getBlockPos() + " " + target.getFace()
                : String.valueOf(target.getEntity()));
    }

    private static void expectWait(List<String> wrong, String move, int wait, int expected) {
        if (wait != expected) {
            wrong.add(move + " " + wait + " ticks, 1.16's " + expected);
        }
    }

    /*
     * Runs each stage on a game tick of its own from the test's fifth tick (the Resolve unlocks are given on the power's
     * ticks, and the mob's available moves are rebuilt once a game tick), moving on when a stage returns true; the test
     * succeeds after the last. Registered at the start: a task scheduled while the test's tasks run may run twice.
     */
    @SafeVarargs
    private static void stages(GameTestHelper helper, Runnable cleanup, java.util.function.BooleanSupplier... stages) {
        long[] lastGameTime = { -1L };
        int[] stage = { 0 };
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (stage[0] >= stages.length || helper.getTick() < 5 || now <= lastGameTime[0]) {
                return;
            }
            lastGameTime[0] = now;
            try {
                if (stages[stage[0]].getAsBoolean() && ++stage[0] == stages.length) {
                    cleanup.run();
                    helper.succeed();
                }
            } catch (RuntimeException | Error error) {
                stage[0] = stages.length;
                cleanup.run();
                throw error;
            }
        });
    }

    // as the goal presses it, then lets the key go as its next tick does (a click action is not held)
    private static void startFinisher(GameTestHelper helper, Mob mob, StandPower power, StandEntity stand,
            Ability finisher, LivingEntity target) {
        lookAt(mob, target);
        // where its tick puts it before the finisher: off the husk's look line
        stand.updatePosition(mob);
        StandPowerMobGoal goal = new StandPowerMobGoal(mob);
        helper.assertTrue(finisher != null && goal.performNamed(
                        power, Map.of("finisher", finisher), "finisher", null, target),
                "Setup: the husk did not press its finisher at " + target + ".");
        EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
        helper.assertTrue(started != null && started.ability == finisher,
                "Setup: the husk's finisher did not start at " + target + "; the Stand runs " + started + ".");
        MobPowerUtil.releaseHeldKey(mob, MobPowerUtil.STAND_HELD_KEY_TAG);
    }

    private static Map<String, Ability> pool(StandPower power) {
        Map<String, Ability> pool = new HashMap<>();
        for (Ability ability : MobPowerUtil.availableMobAbilities(power)) {
            pool.putIfAbsent(ability.name(), ability);
        }
        return pool;
    }

    // 1.16 050 UseRandomAction clicked a move that was no hold-to-fire one (setHeldAction was the hold's), so a plain
    // action like Magician's Red's detector went off and the command reported it; the core takes that press without a
    // held key (an ability with no action of its own), which the manual press took for a failure.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aPlainFollowUpPressReportsThatItWentOffAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        helper.assertTrue(mob != null, "Missing mob fixture");
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            level.addFreshEntity(mob);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.MAGICIANS_RED.get(), true),
                    "Could not give the husk Magician's Red.");
            power = StandPower.get(mob);
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power), "Could not summon the husk's Magician's Red.");
            Ability detector = power.getAbility("mr_detector");
            helper.assertTrue(detector != null, "Setup: Magician's Red has no mr_detector.");
            boolean pressed = ManualHeldInput.press(detector, mob);
            List<MRDetectorEntity> detectors = level.getEntitiesOfClass(MRDetectorEntity.class,
                    mob.getBoundingBox().inflate(5.0D), entity -> entity.isOwner(mob));
            helper.assertTrue(!detectors.isEmpty() && !ManualHeldInput.isHolding(mob, PowerClass.STAND),
                    "Setup: the detector did not go off as a press with no held key: detectors " + detectors.size()
                            + ", holding " + ManualHeldInput.isHolding(mob, PowerClass.STAND));
            detectors.forEach(Entity::discard);
            helper.assertTrue(pressed,
                    "The detector went off but the manual press reported that it did not (the command would say it failed)");
            helper.succeed();
        } finally {
            ManualHeldInput.release(mob);
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
        }
    }

    // 1.16 UseRandomAction pressed a setHeldAction move whose checkRequirements said "go on holding" (Crazy Diamond's
    // Restore Terrain with nothing to restore): PowerBaseImpl.tickHeldAction let the hold run. A negative check that
    // asks to continue the hold must not stop the manual press, the Stand's action starting and the key staying down.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aNegativeCheckThatAsksToContinueTheHoldDoesNotStopTheManualPressAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        helper.assertTrue(mob != null, "Missing mob fixture");
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            level.addFreshEntity(mob);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                    "Could not give the husk Crazy Diamond.");
            power = StandPower.get(mob);
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power), "Could not summon the husk's Crazy Diamond.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
            stand.summonLockTicks = 0;
            Ability restore = power.getAbility("restore_terrain");
            helper.assertTrue(restore != null, "Setup: Crazy Diamond has no restore_terrain.");
            ConditionCheck check = restore.checkConditions(power);
            helper.assertTrue(!check.isPositive() && check.shouldContinueHold(),
                    "Setup: with nothing to restore the check is not a negative one that continues the hold: positive "
                            + check.isPositive() + ", continues " + check.shouldContinueHold());
            boolean pressed = ManualHeldInput.press(restore, mob);
            EntityActionInstance started = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(pressed && started != null && started.ability == restore
                            && ManualHeldInput.isHolding(mob, PowerClass.STAND),
                    "1.16 let the held Restore Terrain go on while there was nothing to restore; the manual press was"
                            + " refused: pressed " + pressed + ", started " + started + ", holding "
                            + ManualHeldInput.isHolding(mob, PowerClass.STAND));
            helper.succeed();
        } finally {
            ManualHeldInput.release(mob);
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
        }
    }

    // 1.16 050 UseRandomAction clicked a move that was no hold-to-fire one through PowerBaseImpl.clickAction, whose
    // checkRequirements ended in StandEntityAction.checkTaskCancelling: canClickDuringTask (:495-497) refused the
    // running task's own non-chainable ability, silent and unqueued, in every phase. The manual press (the command and
    // the Debug Stand) let go of the mob's hold first, which put a held barrage into its recovery (a Resolve-freed one
    // cancels into its own ability), and then pressed it again.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aManualPressOfTheRunningActionsOwnAbilityIsRefusedAsIn116(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(mob != null && cow != null, "Missing mob fixtures");
        StandPower power = null;
        StandEntity stand = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(cow, pos.add(0.0D, 0.0D, 1.5D));
            level.addFreshEntity(mob);
            level.addFreshEntity(cow);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.CRAZY_DIAMOND.get(), true),
                    "Could not give the husk Crazy Diamond.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Crazy Diamond.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: the husk has no Stand.");
            stand.summonLockTicks = 0;
            Ability heavy = power.getAbility("heavy_punch");
            Ability barrage = power.getAbility("barrage");
            helper.assertTrue(heavy != null && barrage != null, "Setup: Crazy Diamond has no heavy punch or barrage.");
            mob.setTarget(cow);
            lookAt(mob, cow);
            stand.updatePosition(mob);
            manualPressAgainIsRefused(helper, mob, stand, heavy);
            LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            // a Resolve-freed recovery cancels into any ability, its own included, unless the press is refused
            mob.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 0));
            power.setStamina(power.getMaxStamina());
            manualPressAgainIsRefused(helper, mob, stand, barrage);
            helper.succeed();
        } finally {
            ManualHeldInput.release(mob);
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            }
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            cow.discard();
        }
    }

    private static void manualPressAgainIsRefused(GameTestHelper helper, Mob mob, StandEntity stand, Ability ability) {
        helper.assertTrue(ManualHeldInput.press(ability, mob),
                "Setup: the manual press did not start the " + ability.name() + ".");
        EntityActionInstance running = LivingComponentAction.getComponent(stand).getAction();
        helper.assertTrue(running != null && running.ability == ability && !running.isOver()
                        && running.refusesOwnAbilityPressedAgain(),
                "Setup: the Stand does not run a " + ability.name() + " that refuses its own press; it runs " + running + ".");
        boolean again = ManualHeldInput.press(ability, mob);
        EntityActionInstance after = LivingComponentAction.getComponent(stand).getAction();
        helper.assertTrue(!again && after == running && !running.isOver(),
                "1.16 refused the running " + ability.name() + "'s own ability pressed again, and let it run on; the press"
                        + " went off " + again + ", the Stand runs " + after + " (was " + running + ").");
    }

    private static void standStill(Cow cow, Vec3 pos) {
        cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        cow.setNoAi(true);
        cow.setNoGravity(true);
    }

    // with its middle at the mob's eye height, where the mob's level look meets it
    private static void standAtEyeHeight(Cow cow, Mob mob, Vec3 pos) {
        standStill(cow, new Vec3(pos.x, mob.getEyeY() - cow.getBbHeight() / 2.0D, pos.z));
    }

    // a no-AI mob's look control does not tick: turned as setLookAt turns it, to the eyes of what it looks at
    private static void lookAt(Mob mob, LivingEntity seen) {
        Vec3 to = seen.getEyePosition().subtract(mob.getEyePosition());
        turn(mob, (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F,
                (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG));
    }

    private static void turn(Mob mob, float yaw, float pitch) {
        mob.setYRot(yaw);
        mob.yRotO = yaw;
        mob.setYHeadRot(yaw);
        mob.yHeadRotO = yaw;
        mob.yBodyRot = yaw;
        mob.yBodyRotO = yaw;
        mob.setXRot(pitch);
        mob.xRotO = pitch;
    }
}
