package rotp.addons.frosttraveler.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.ability.CreamOperations;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility.Operation;
import rotp.addons.frosttraveler.state.CreamStarterService;
import rotp.addons.frosttraveler.state.CreamStarterState;
import rotp.addons.frosttraveler.state.CreamStarterState.SprayMode;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 registered give_cs, cs_heal, cs_switch, cs_item_fill, cs_entity_fill and cs_dsg_item as StandEntityActions
 * (InitStands.java:50-54, 57): FULL auto-summon, the IDLE pose and a Stand task. give_cs and cs_switch performed for
 * one tick (give_cs paying staminaCost(1), cs_switch with cooldown(10) and the CS_SHAKE Stand sound); cs_heal
 * (holdType(60), cooldown(20), staminaCostTick(10)) and the fills (holdType()) worked on every held tick;
 * cs_dsg_item charged for holdToFire(15, false). Also DeformAction.checkTarget's no_cream, cs_def's hidden effects
 * and PutItemAction's ender eye used up without a stronghold.
 */
@GameTestHolder("frost_injection")
@PrefixGameTestTemplate(false)
public final class FrostStandTaskGameTests {
    private static final short KEY = 73;
    private static final short OTHER_KEY = 74;
    private static final float EPS = 1.0E-4F;
    // 6 x 1 x 4 of air: the framework forces and waits for only the chunks of the structure's bounds, and every
    // fixture of these scenes stands within them (Scene.inArea)
    private static final String AREA = "fixture_area";

    private FrostStandTaskGameTests() {}

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void theOtherSixCreamActionsAreStandTasksWithTheDonorTiming(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            check(helper, scene, FrostTravelerAddon.GIVE_CS_NAME, Operation.GIVE, 0, 1, 0);
            check(helper, scene, FrostTravelerAddon.CS_SWITCH_NAME, Operation.SWITCH, 0, 1,
                    CreamStandActionAbility.SWITCH_COOLDOWN);
            check(helper, scene, FrostTravelerAddon.CS_HEAL_NAME, Operation.HEAL, 0,
                    CreamOperations.SELF_HEAL_MAX_TICKS, CreamStandActionAbility.HEAL_COOLDOWN);
            check(helper, scene, FrostTravelerAddon.CS_ITEM_FILL_NAME, Operation.ITEM_FILL, 0, 999999, 0);
            check(helper, scene, FrostTravelerAddon.CS_ENTITY_FILL_NAME, Operation.ENTITY_FILL, 0, 999999, 0);
            check(helper, scene, FrostTravelerAddon.CS_DSG_ITEM_NAME, Operation.DISGUISE_ITEM, 15, 1, 0);
            CreamStandActionAbility heal = scene.ability(FrostTravelerAddon.CS_HEAL_NAME, Operation.HEAL);
            CreamStandActionAbility change = scene.ability(FrostTravelerAddon.CS_SWITCH_NAME, Operation.SWITCH);
            helper.assertTrue(!heal.shouldSetCooldownOnKeyPress(InputMethod.HOLD)
                            && change.shouldSetCooldownOnKeyPress(InputMethod.CLICK),
                    "1.16 cs_heal was holdOnly (its cooldown when the hold stops); cs_switch's came at the press");
            helper.succeed();
        }
    }

    private static void check(GameTestHelper helper, Scene scene, String name, Operation operation,
            float charge, float perform, int cooldown) {
        CreamStandActionAbility ability = scene.ability(name, operation);
        EntityActionInstance action = ability.initActionOnAbilityUse(
                helper.getLevel(), scene.player, scene.stand(), null);
        float actualCharge = action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
        float actualPerform = action.phasesLength.getFloat(ActionPhase.PERFORM);
        helper.assertTrue(actualCharge == charge && actualPerform == perform
                        && action.phasesLength.getFloat(ActionPhase.WINDUP) == 0.0F
                        && action.phasesLength.getFloat(ActionPhase.RECOVERY) == 0.0F,
                "1.16 " + name + " charged " + charge + " ticks and performed " + perform + " on the Stand's task; got "
                        + actualCharge + " / " + actualPerform);
        ActionAnimIdentifier anim = ability.getEntityAnim(action);
        helper.assertTrue(anim.isIdle() && "idle".equals(anim.name()),
                "1.16 " + name + " kept StandPose.IDLE; got " + anim);
        int actualCooldown = ability.getCooldown(scene.power, -1);
        helper.assertTrue(actualCooldown == cooldown,
                "1.16 " + name + " had cooldown " + cooldown + "; got " + actualCooldown);
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void giveCsPressedWithTheStandAwaySummonsItAndPaysOneStamina(GameTestHelper helper) {
        // a server player, whom the sprayer upkeep (CreamStarterService.reconcile) serves
        try (Scene scene = new Scene(helper, true)) {
            CreamStandActionAbility give = scene.ability(FrostTravelerAddon.GIVE_CS_NAME, Operation.GIVE);
            FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(scene.player, scene.power);
            // the purge walks the player list, which a fake player is not on
            scene.clearOwnedSprayers();
            helper.assertTrue(!scene.power.isSummoned() && scene.ownedSprayers() == 0,
                    "setup: the Cream Starter is still out or its sprayer is still held");
            float stamina = scene.power.getMaxStamina();
            scene.power.setStamina(stamina);
            helper.assertTrue(scene.pressAsServer(FrostTravelerAddon.GIVE_CS_NAME, InputMethod.CLICK),
                    "1.16 give_cs had no condition: it must be usable with the Stand away; got " + key(scene.lastCheck));
            StandEntity stand = scene.power.getSummonedStandEntity();
            helper.assertTrue(scene.lastHeld != null && stand != null && !stand.isArmsOnlyMode() && stand.isAddedToLevel()
                            && stand.getCurStandAction() != null && stand.getCurStandAction().ability == give,
                    "1.16 StandEntityAction.onClick summoned the full Stand (AutoSummonMode.FULL) for give_cs: "
                            + stand);
            stand.summonLockTicks = 0;
            scene.runToEnd(stand.getCurStandAction());
            helper.assertTrue(Math.abs(stamina - scene.power.getStamina() - CreamStandActionAbility.GIVE_STAMINA) < EPS,
                    "1.16 give_cs paid staminaCost(1) at its perform; stamina went from " + stamina + " to "
                            + scene.power.getStamina());
            helper.assertTrue(scene.ownedSprayers() == 1,
                    "1.16 gave the Cream Starter on the first tick the Stand was out; owned sprayers "
                            + scene.ownedSprayers());
            helper.succeed();
        }
    }

    /**
     * 1.16 PowerBaseImpl.onClickAction ran StandEntityAction.onClick (the auto-summon) before checkRequirements, and
     * PutItemAction.checkSpecificConditions asked power.isActive() and the off-hand item and the target, no sprayer: so
     * cs_item pressed with the Stand away summoned it and charged. Every other action needed the sprayer, which exists
     * only with the Stand out, and StandEntityAction.afterClick unsummoned the Stand again after their refused press.
     * GameplayHandler then gave the sprayer on the next player tick. The presses go through the server's admission.
     */
    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void itemInjectionPressedWithTheStandAwaySummonsItAndGoesOn(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, true)) {
            CreamStandActionAbility injection = scene.ability(FrostTravelerAddon.CS_ITEM_NAME, Operation.ITEM_INJECTION);
            Cow cow = scene.addCow(scene.player.position().add(0.0D, 0.0D, 2.0D));
            BlockPos bars = scene.block(2, 13, 1);
            try {
                FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(scene.player, scene.power);
                scene.clearOwnedSprayers();
                scene.power.setStamina(scene.power.getMaxStamina());
                helper.assertTrue(!scene.power.isSummoned() && scene.ownedSprayers() == 0,
                        "setup: the Cream Starter is still out or its sprayer is still held");

                helper.getLevel().setBlockAndUpdate(bars, Blocks.IRON_BARS.defaultBlockState());
                scene.turn(-90.0F, 0.0F);
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_DEFORM_NAME, InputMethod.HOLD,
                        "jojo.message.action_condition.no_cream");
                helper.getLevel().setBlockAndUpdate(bars, Blocks.AIR.defaultBlockState());
                scene.turn(0.0F, 40.0F);
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_SWITCH_NAME, InputMethod.CLICK,
                        "jojo.message.action_condition.no_cream");
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_HEAL_NAME, InputMethod.HOLD,
                        "jojo.message.action_condition.no_cream");
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_ITEM_FILL_NAME, InputMethod.HOLD, null);
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_ENTITY_FILL_NAME, InputMethod.HOLD, null);
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_DEF_NAME, InputMethod.HOLD, null);
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_DSG_ITEM_NAME, InputMethod.HOLD,
                        "jojo.message.action_condition.no_cream.main");
                // 1.16 PutItemAction refused an empty off hand, the Stand out or not
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_ITEM_NAME, InputMethod.HOLD, null);

                scene.turn(0.0F, 20.0F);
                scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
                helper.assertTrue(scene.pressAsServer(FrostTravelerAddon.CS_ITEM_NAME, InputMethod.HOLD),
                        "1.16 PutItemAction asked power.isActive() after the press had summoned the Stand and needed"
                                + " no sprayer: cs_item must be usable with the Stand away; got "
                                + key(scene.lastCheck));
                StandEntity stand = scene.power.getSummonedStandEntity();
                helper.assertTrue(scene.lastHeld != null && stand != null && !stand.isArmsOnlyMode()
                                && stand.isAddedToLevel()
                                && stand.getCurStandAction() != null && stand.getCurStandAction().ability == injection
                                && stand.getCurStandAction().getPhase() == ActionPhase.BUTTON_CHARGE,
                        "1.16 StandEntityAction.onClick summoned the full Stand and cs_item went on to its charge: "
                                + stand);
                helper.assertTrue(scene.ownedSprayers() == 1
                                && !CreamStarterService.heldOwnedSprayer(scene.player).isEmpty(),
                        "1.16 GameplayHandler gave the Cream Starter on the first player tick the Stand was out, not"
                                + " up to a second later; owned sprayers " + scene.ownedSprayers());
                stand.summonLockTicks = 0;
                scene.runToEnd(stand.getCurStandAction());
                helper.assertTrue(cow.getRemainingFireTicks() == 100
                                && scene.player.getOffhandItem().getCount() == 1,
                        "1.16 PutItemAction put the Fire Charge into the cow it was pressed at; fire "
                                + cow.getRemainingFireTicks() + ", charges left "
                                + scene.player.getOffhandItem().getCount());
                scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            finally {
                cow.discard();
                helper.getLevel().setBlockAndUpdate(bars, Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        }
    }

    /**
     * 1.16 PutItemAction (cs_item) and DeformTargetAction (cs_def) had TargetRequirement.ENTITY, and
     * StandEntityAction.checkStandTarget refused, with no message, an entity StandEntity.canAttack spared: the user's
     * own tamed animal, a teammate with friendly fire off. PowerBaseImpl.tickHeldAction asked it again against the
     * crosshair on every held tick, so such a target, or none, coming under the crosshair stopped the charge unfired:
     * nothing injected, no 100 stamina, no effect.
     */
    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void injectionAndImpairmentRefuseWhatTheStandMayNotAttack(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, true)) {
            Vec3 front = scene.inArea(scene.player.position().add(0.0D, 0.0D, 2.0D));
            Wolf wolf = EntityType.WOLF.create(helper.getLevel());
            helper.assertTrue(wolf != null, "Could not create a wolf");
            wolf.setNoAi(true);
            wolf.setNoGravity(true);
            wolf.setTame(true, false);
            wolf.setOwnerUUID(scene.player.getUUID());
            wolf.moveTo(front.x, front.y, front.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(wolf) && wolf.getOwner() == scene.player,
                    "setup: the wolf is not the user's tamed wolf");
            Cow cow = scene.addCow(front.add(3.0D, 0.0D, 0.0D));
            Scoreboard scoreboard = helper.getLevel().getScoreboard();
            PlayerTeam team = null;
            try {
                scene.turn(0.0F, 40.0F);
                scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
                scene.power.setStamina(scene.power.getMaxStamina());
                float stamina = scene.power.getStamina();
                Item sprayerItem = FrostTravelerAddon.CREAM_STARTER_ITEM.get();

                // pressed at the user's own wolf, with the Stand out
                for (String name : List.of(FrostTravelerAddon.CS_ITEM_NAME, FrostTravelerAddon.CS_DEF_NAME)) {
                    boolean admitted = scene.pressAsServer(name, InputMethod.HOLD);
                    helper.assertTrue(!admitted && scene.lastCheck.getWarning() == null
                                    && scene.stand().getCurStandAction() == null,
                            "1.16 checkStandTarget refused " + name + " at the user's own tamed wolf (StandEntity"
                                    + ".canAttack) without a message; admitted " + admitted + ", warning "
                                    + key(scene.lastCheck));
                }
                helper.assertTrue(scene.power.getStamina() == stamina
                                && scene.player.getOffhandItem().getCount() == 2
                                && !scene.player.getCooldowns().isOnCooldown(sprayerItem)
                                && wolf.getRemainingFireTicks() <= 0
                                && !wolf.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "1.16 refused presses at the user's wolf charged nothing: stamina " + scene.power.getStamina()
                                + ", fire charges " + scene.player.getOffhandItem().getCount() + ", wolf on fire "
                                + wolf.getRemainingFireTicks());

                // cs_item with the Stand away: the Stand its press summoned in 1.16 could not attack the wolf either
                FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(scene.player, scene.power);
                scene.refusedWithTheStandAway(FrostTravelerAddon.CS_ITEM_NAME, InputMethod.HOLD, null);
                helper.assertTrue(FrostTravelerAddon.CREAM_STARTER.get().summon(scene.player, scene.power),
                        "setup: could not summon the Cream Starter again");
                scene.stand().summonLockTicks = 0;

                // a cow on the user's team with friendly fire off
                wolf.setPos(scene.inArea(front.add(3.0D, 0.0D, 0.0D)));
                cow.setPos(front);
                team = scoreboard.addPlayerTeam("frost_r770_" + scene.player.getUUID().toString().substring(0, 8));
                team.setAllowFriendlyFire(false);
                scoreboard.addPlayerToTeam(scene.player.getScoreboardName(), team);
                scoreboard.addPlayerToTeam(cow.getScoreboardName(), team);
                helper.assertTrue(!scene.pressAsServer(FrostTravelerAddon.CS_DEF_NAME, InputMethod.HOLD)
                                && !cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "1.16 checkStandTarget refused cs_def at a teammate with friendly fire off");
                scoreboard.removePlayerTeam(team);
                team = null;

                // held at the cow, then the user's wolf takes its place under the crosshair
                helper.assertTrue(scene.pressAsServer(FrostTravelerAddon.CS_ITEM_NAME, InputMethod.HOLD),
                        "setup: cs_item cannot be pressed at the cow; got " + key(scene.lastCheck));
                EntityActionInstance held = scene.stand().getCurStandAction();
                scene.tick(2);
                cow.setPos(scene.inArea(front.add(1.5D, 0.0D, 0.0D)));
                wolf.setPos(front);
                scene.tick(2);
                helper.assertTrue(held.isOver() && cow.getRemainingFireTicks() <= 0
                                && scene.power.getStamina() == stamina
                                && scene.player.getOffhandItem().getCount() == 2,
                        "1.16 tickHeldAction rechecked checkStandTarget against the crosshair: the user's wolf under"
                                + " it stopped the held cs_item unfired; over " + held.isOver() + ", cow on fire "
                                + cow.getRemainingFireTicks() + ", stamina " + scene.power.getStamina());
                scene.release();
                scene.player.getCooldowns().removeCooldown(sprayerItem);

                // held at the cow's legs, then the crosshair leaves for the sky
                wolf.setPos(scene.inArea(front.add(3.0D, 0.0D, 0.0D)));
                cow.setPos(front);
                helper.assertTrue(scene.pressAsServer(FrostTravelerAddon.CS_DEF_NAME, InputMethod.HOLD),
                        "setup: cs_def cannot be pressed at the cow's legs; got " + key(scene.lastCheck));
                held = scene.stand().getCurStandAction();
                scene.tick(2);
                scene.turn(0.0F, -90.0F);
                scene.tick(2);
                helper.assertTrue(held.isOver() && !cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                && !cow.hasEffect(MobEffects.BLINDNESS),
                        "1.16 tickHeldAction stopped the held cs_def once the crosshair left its ENTITY target;"
                                + " over " + held.isOver());
                scene.release();
                scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            finally {
                if (team != null) {
                    scoreboard.removePlayerTeam(team);
                }
                wolf.discard();
                cow.discard();
            }
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void healWorksOnEveryHeldTickAndItsCooldownStartsWhenItStops(GameTestHelper helper) {
        AtomicInteger sprays = new AtomicInteger();
        Consumer<PlayLevelSoundEvent.AtPosition> spray = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == FrostTravelerAddon.CS_SHOT.get()) {
                sprays.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, spray);
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility heal = scene.ability(FrostTravelerAddon.CS_HEAL_NAME, Operation.HEAL);
            CreamStarterState state = CreamStarterService.state(scene.player);
            String name = heal.name();

            // five held ticks, then the release
            scene.player.setHealth(10.0F);
            int ammo = state.ammo();
            EntityActionInstance action = scene.press(heal, InputMethod.HOLD);
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM
                            && action.getCurPhaseLength() == CreamOperations.SELF_HEAL_MAX_TICKS
                            && heal.isActionHeld(action),
                    "1.16 cs_heal (holdType(60)) performed on its task while held, for at most 60 ticks; got "
                            + action.getPhase() + " " + action.getCurPhaseLength());
            helper.assertTrue(!scene.power.isAbilityOnCooldown(name),
                    "1.16 cs_heal was holdOnly: no cooldown at the press");
            scene.tick(5);
            helper.assertTrue(Math.abs(scene.player.getHealth() - 11.25F) < EPS && state.ammo() == ammo - 5
                            && sprays.getAndSet(0) == 5,
                    "1.16 Healing.standTickPerform healed 0.25, used one cream and sprayed on each of the 5 held"
                            + " ticks; health " + scene.player.getHealth() + ", ammo " + state.ammo()
                            + ", sprays " + sprays.get());
            scene.release();
            helper.assertTrue(action.isOver() && scene.stand().getCurStandAction() == null
                            && scene.power.getAbilityCooldown(name) == CreamStandActionAbility.HEAL_COOLDOWN,
                    "1.16 stopHeldAction ended the released heal and started its 20-tick cooldown; cooldown "
                            + scene.power.getAbilityCooldown(name));
            scene.power.setAbilityCooldown(name, 0);

            // held on, it stops by itself after 60 ticks
            scene.player.setHealth(1.0F);
            ammo = state.ammo();
            action = scene.press(heal, InputMethod.HOLD);
            int ticks = 0;
            for (; ticks < 70 && scene.stand().getCurStandAction() == action; ticks++) {
                scene.stand().getStandActionComponent().tick();
            }
            helper.assertTrue(action.isOver() && state.ammo() == ammo - CreamOperations.SELF_HEAL_MAX_TICKS
                            && Math.abs(scene.player.getHealth() - 16.0F) < EPS
                            && scene.power.isAbilityOnCooldown(name),
                    "1.16 holdType(60): the held heal worked 60 ticks, then stopped with its cooldown; ammo used "
                            + (ammo - state.ammo()) + ", health " + scene.player.getHealth() + ", ticks " + ticks);
            AbilityInput.keyRelease(KEY, scene.player);
            scene.power.setAbilityCooldown(name, 0);

            // 5 stamina: no heal, the cream is still used, the bar drops to 0 and the hold goes on
            scene.player.setHealth(10.0F);
            scene.power.setStamina(5.0F);
            ammo = state.ammo();
            action = scene.press(heal, InputMethod.HOLD);
            scene.tick(1);
            helper.assertTrue(scene.player.getHealth() == 10.0F && state.ammo() == ammo - 1
                            && scene.power.getStamina() == 0.0F && !action.isOver(),
                    "1.16 Healing healed only above 9 stamina and StandEntityTask paid staminaCostTick(10) anyway;"
                            + " health " + scene.player.getHealth() + ", ammo used " + (ammo - state.ammo())
                            + ", stamina " + scene.power.getStamina() + ", over " + action.isOver());
            scene.release();
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(spray);
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void aHitOverTenStopsTheHeldHealWithItsCooldown(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility heal = scene.ability(FrostTravelerAddon.CS_HEAL_NAME, Operation.HEAL);
            scene.player.setHealth(20.0F);
            EntityActionInstance action = scene.press(heal, InputMethod.HOLD);
            scene.tick(1);
            scene.player.hurt(scene.player.damageSources().generic(), 10.0F);
            helper.assertTrue(!action.isOver(), "1.16 stopped the heal only on a hit over 10, but 10 stopped it");
            scene.player.invulnerableTime = 0;
            scene.player.setHealth(20.0F);
            scene.player.hurt(scene.player.damageSources().generic(), 10.5F);
            helper.assertTrue(action.isOver(),
                    "1.16 GameplayHandler.onPlayerHurt stopped the held cs_heal on a hit over 10");
            scene.stand().getStandActionComponent().tick();
            helper.assertTrue(scene.power.getAbilityCooldown(heal.name()) == CreamStandActionAbility.HEAL_COOLDOWN,
                    "1.16 stopHeldAction(false) started the heal's cooldown; got "
                            + scene.power.getAbilityCooldown(heal.name()));
            scene.release();
            helper.succeed();
        }
    }

    /*
     * 1.16 cs_heal (holdType(60)), cs_item_fill and cs_entity_fill (holdType()) had no recovery: a Stand action pressed
     * during the held task replaced it at once (StandEntityAction.checkTaskCancelling, isCancelable) and the hold ended
     * with stopHeldAction(false), which started cs_heal's 20-tick cooldown (the fills had none); an action of the power the
     * Stand does not perform ended the hold the same way. A hold-to-fire action let no other action through its charge
     * (heldAllowsOtherAction false), which the input buffer keeps.
     */
    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void aCreamActionPressedDuringTheHeldHealOrFillReplacesItAtOnceAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStarterState state = CreamStarterService.state(scene.player);
            CreamStandActionAbility change = scene.ability(FrostTravelerAddon.CS_SWITCH_NAME, Operation.SWITCH);
            scene.player.getInventory().setItem(5, new ItemStack(Items.ROTTEN_FLESH, 8));
            for (Operation operation : List.of(Operation.HEAL, Operation.ITEM_FILL, Operation.ENTITY_FILL)) {
                String name = operation == Operation.HEAL ? FrostTravelerAddon.CS_HEAL_NAME
                        : operation == Operation.ITEM_FILL ? FrostTravelerAddon.CS_ITEM_FILL_NAME
                        : FrostTravelerAddon.CS_ENTITY_FILL_NAME;
                CreamStandActionAbility held = scene.ability(name, operation);
                state.setAmmo(100);
                scene.player.setHealth(10.0F);
                EntityActionInstance hold = scene.holdWith(held);
                BufferingState pressed = scene.pressWith(FrostTravelerAddon.CS_SWITCH_NAME, InputMethod.CLICK);
                EntityActionInstance now = scene.stand().getCurStandAction();
                helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && now != null && now != hold
                                && now.ability == change && hold.isOver(),
                        "1.16 let cs_switch pressed during the held " + name + " through at once (holdType,"
                                + " isCancelable): started " + pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer
                                + ", the Stand's action " + now + ", the " + name + " over " + hold.isOver());
                int cooldown = scene.power.getAbilityCooldown(name);
                int expected = operation == Operation.HEAL ? CreamStandActionAbility.HEAL_COOLDOWN : 0;
                helper.assertTrue(cooldown == expected,
                        "1.16 stopHeldAction(false) gave the " + name + " replaced by cs_switch a cooldown of " + expected
                                + "; got " + cooldown);
                scene.clearHolds();
                scene.power.setAbilityCooldown(change.name(), 0);
                scene.power.setAbilityCooldown(name, 0);
            }

            // the Cream Starter's power has only Stand actions; a plain ability of the power (as add-ons give Stands)
            // goes off and ends the hold as letting go of its key does
            CreamStandActionAbility heal = scene.ability(FrostTravelerAddon.CS_HEAL_NAME, Operation.HEAL);
            state.setAmmo(100);
            EntityActionInstance hold = scene.holdWith(heal);
            Ability plain = new Ability(heal.abilityType,
                    new AbilityId(PowerClass.STAND, heal.getAbilityId().powerTypeId(), "w2_plain_press"));
            BufferingState plainPress = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(OTHER_KEY, plain, scene.player, null, InputMethod.CLICK, 0, plainPress,
                    plain.getAbilityId());
            AbilityInput.keyRelease(OTHER_KEY, scene.player);
            scene.tick(1);
            helper.assertTrue(plainPress.isActionSuccess && hold.isOver() && !scene.isHeldByKey(KEY)
                            && scene.power.getAbilityCooldown(heal.name()) == CreamStandActionAbility.HEAL_COOLDOWN,
                    "1.16 ended the held cs_heal once another action of the power went off (stopHeldAction(false)),"
                            + " with its cooldown: over " + hold.isOver() + ", its key holds it " + scene.isHeldByKey(KEY)
                            + ", cooldown " + scene.power.getAbilityCooldown(heal.name()));
            scene.clearHolds();

            // cs_dsg_item (holdToFire(15, false)) charging: cs_switch waits
            scene.player.getInventory().setItem(5, ItemStack.EMPTY);
            state.setAmmo(100);
            CreamStandActionAbility disguise = scene.ability(FrostTravelerAddon.CS_DSG_ITEM_NAME, Operation.DISGUISE_ITEM);
            EntityActionInstance charge = scene.holdWith(disguise);
            helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "setup: cs_dsg_item is not charging: " + charge.getPhase());
            BufferingState duringCharge = scene.pressWith(FrostTravelerAddon.CS_SWITCH_NAME, InputMethod.CLICK);
            helper.assertTrue(duringCharge.shouldBuffer && !duringCharge.isActionSuccess
                            && scene.stand().getCurStandAction() == charge && !charge.isOver(),
                    "1.16 let no action through the charging cs_dsg_item (holdToFire, heldAllowsOtherAction false),"
                            + " which the input buffer keeps: buffered " + duringCharge.shouldBuffer + ", the Stand's action "
                            + scene.stand().getCurStandAction());
            scene.clearHolds();
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void switchIsAOneTickStandTaskWithItsCooldownAndNoWorldSound(GameTestHelper helper) {
        AtomicInteger shakes = new AtomicInteger();
        Consumer<PlayLevelSoundEvent.AtPosition> shake = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == FrostTravelerAddon.CS_SHAKE.get()) {
                shakes.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, shake);
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility change = scene.ability(FrostTravelerAddon.CS_SWITCH_NAME, Operation.SWITCH);
            CreamStarterState state = CreamStarterService.state(scene.player);
            helper.assertTrue(state.mode() == SprayMode.ATTACK, "setup: the sprayer is not in attack mode");
            EntityActionInstance action = scene.press(change, InputMethod.CLICK);
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && action.getCurPhaseLength() == 1.0F
                            && scene.power.getAbilityCooldown(change.name())
                                    == CreamStandActionAbility.SWITCH_COOLDOWN,
                    "1.16 cs_switch was a one-tick Stand task with cooldown(10) from the press; got "
                            + action.getPhase() + " " + action.getCurPhaseLength() + ", cooldown "
                            + scene.power.getAbilityCooldown(change.name()));
            scene.runToEnd(action);
            helper.assertTrue(state.mode() == SprayMode.HEAL,
                    "1.16 ChangeMode.standTickPerform switched the sprayer to heal mode; mode " + state.mode());
            helper.assertTrue(shakes.get() == 0,
                    "1.16 standSound(CS_SHAKE) played at the Stand for those who hear Stands, not as a sound"
                            + " for the whole world; world sounds " + shakes.get());
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(shake);
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void theFillsWorkOnEveryHeldTickUntilReleased(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility itemFill = scene.ability(FrostTravelerAddon.CS_ITEM_FILL_NAME, Operation.ITEM_FILL);
            CreamStandActionAbility entityFill = scene.ability(
                    FrostTravelerAddon.CS_ENTITY_FILL_NAME, Operation.ENTITY_FILL);
            CreamStarterState state = CreamStarterService.state(scene.player);
            ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 3);
            scene.player.getInventory().setItem(5, flesh);
            int ammo = state.ammo();
            EntityActionInstance action = scene.press(itemFill, InputMethod.HOLD);
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && itemFill.isActionHeld(action),
                    "1.16 cs_item_fill (holdType()) performed on its task while held; got " + action.getPhase());
            scene.tick(2);
            helper.assertTrue(state.ammo() == ammo + 60 && flesh.getCount() == 1 && !action.isOver(),
                    "1.16 FillMeatItems.standTickPerform used one rotten flesh (30) on each held tick; ammo "
                            + (state.ammo() - ammo) + ", flesh " + flesh.getCount());
            scene.release();
            helper.assertTrue(action.isOver() && flesh.getCount() == 1,
                    "1.16 holdType: the released fill went to its recovery and stopped");
            scene.player.getInventory().setItem(5, ItemStack.EMPTY);

            // looking at nothing, the entity fill takes from the user
            scene.turn(0.0F, -90.0F);
            ammo = state.ammo();
            action = scene.press(entityFill, InputMethod.HOLD);
            int expected = ammo;
            for (int tick = 0; tick < 2; tick++) {
                expected += Math.round(scene.player.getHealth());
                scene.tick(1);
            }
            helper.assertTrue(state.ammo() == expected && !action.isOver(),
                    "1.16 FillingEntities.standTickPerform filled from the user on each of 2 held ticks; ammo "
                            + state.ammo() + ", expected " + expected);
            scene.release();
            helper.assertTrue(action.isOver(), "1.16 holdType: the released entity fill stopped");
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void theDisguiseItemChargesOnTheStandThenMakesTheMask(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility disguise = scene.ability(
                    FrostTravelerAddon.CS_DSG_ITEM_NAME, Operation.DISGUISE_ITEM);
            CreamStarterState state = CreamStarterService.state(scene.player);
            int ammo = state.ammo();
            EntityActionInstance dropped = scene.press(disguise, InputMethod.HOLD);
            helper.assertTrue(dropped.getPhase() == ActionPhase.BUTTON_CHARGE && dropped.getCurPhaseLength() == 15.0F,
                    "1.16 cs_dsg_item was holdToFire(15, false) on the Stand's task; got " + dropped.getPhase() + " "
                            + dropped.getCurPhaseLength());
            scene.tick(5);
            scene.release();
            helper.assertTrue(dropped.isOver() && scene.player.getOffhandItem().isEmpty() && state.ammo() == ammo,
                    "1.16 holdToFire(15, false): a disguise let go of after 5 ticks never went off");
            EntityActionInstance action = scene.press(disguise, InputMethod.HOLD);
            scene.tick(14);
            helper.assertTrue(scene.player.getOffhandItem().isEmpty() && !action.isOver(),
                    "1.16 DisguiseItemAction performed only once the 15-tick charge was done");
            scene.runToEnd(action);
            helper.assertTrue(scene.player.getOffhandItem().is(FrostTravelerAddon.MEAT_DGS_ITEM.get()),
                    "1.16 DisguiseItemAction.standPerform put a meat mask in the offhand; offhand "
                            + scene.player.getOffhandItem());
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void deformAndHealWarnNoCreamWithoutACreamStarter(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility deform = scene.ability(FrostTravelerAddon.CS_DEFORM_NAME, Operation.DEFORM);
            CreamStandActionAbility heal = scene.ability(FrostTravelerAddon.CS_HEAL_NAME, Operation.HEAL);
            BlockPos bars = scene.block(2, 13, 1);
            helper.getLevel().setBlockAndUpdate(bars, Blocks.IRON_BARS.defaultBlockState());
            scene.turn(-90.0F, 0.0F);
            helper.assertTrue(deform.checkConditions(scene.power).isPositive(),
                    "setup: cs_deform cannot go through the bars with the Cream Starter in hand");
            ItemStack sprayer = scene.player.getMainHandItem();
            scene.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ConditionCheck atBars = deform.checkConditions(scene.power);
            helper.assertTrue(!atBars.isPositive() && "jojo.message.action_condition.no_cream".equals(key(atBars)),
                    "1.16 DeformAction.checkTarget warned no_cream at a block with no Cream Starter in either hand;"
                            + " got " + key(atBars));
            ConditionCheck healCheck = heal.checkConditions(scene.power);
            helper.assertTrue(!healCheck.isPositive()
                            && "jojo.message.action_condition.no_cream".equals(key(healCheck)),
                    "1.16 Healing warned no_cream with no Cream Starter in either hand; got " + key(healCheck));
            scene.turn(-90.0F, -90.0F);
            ConditionCheck atSky = deform.checkConditions(scene.power);
            helper.assertTrue(!atSky.isPositive() && atSky.getWarning() == null,
                    "1.16 asked DeformAction.checkTarget only for a block target; at the sky it warned "
                            + key(atSky));
            scene.player.setItemInHand(InteractionHand.MAIN_HAND, sprayer);
            helper.getLevel().setBlockAndUpdate(bars, Blocks.AIR.defaultBlockState());
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void impairmentShowsNoParticlesOrIconAndAnEyeIsUsedWithoutAStronghold(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility impair = scene.ability(FrostTravelerAddon.CS_DEF_NAME, Operation.DEFENSE);
            // the cow south of the user faces it; the user aims at its eyes, then at its legs
            Cow cow = scene.addCow(scene.player.position().add(0.0D, 0.0D, 2.0D));
            cow.setYRot(180.0F);
            cow.yRotO = 180.0F;
            cow.setYHeadRot(180.0F);
            cow.yBodyRot = 180.0F;
            try {
                scene.turn(0.0F, 11.7F);
                scene.runToEnd(scene.press(impair, InputMethod.HOLD));
                hidden(helper, cow.getEffect(MobEffects.BLINDNESS), "Blindness");
                scene.player.getCooldowns().removeCooldown(FrostTravelerAddon.CREAM_STARTER_ITEM.get());
                scene.turn(0.0F, 40.0F);
                scene.runToEnd(scene.press(impair, InputMethod.HOLD));
                hidden(helper, cow.getEffect(MobEffects.MOVEMENT_SLOWDOWN), "Slowness");
            }
            finally {
                cow.discard();
            }

            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "setup: the test server has no Nether");
            Cow netherCow = EntityType.COW.create(nether);
            helper.assertTrue(netherCow != null, "setup: could not create a cow in the Nether");
            netherCow.moveTo(0.5D, 64.0D, 0.5D, 0.0F, 0.0F);
            scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ENDER_EYE, 2));
            boolean used = CreamOperations.injectOffhandItem(scene.player, netherCow);
            helper.assertTrue(used && scene.player.getOffhandItem().getCount() == 1,
                    "1.16 PutItemAction's item table fired on the ender eye alone: with no stronghold the eye was"
                            + " still used up; used " + used + ", eyes left " + scene.player.getOffhandItem().getCount());
            scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            netherCow.discard();
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void giveCsCanBeAddedInTheHudEditorButIsOffTheDefaultHotbar(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility give = scene.ability(FrostTravelerAddon.GIVE_CS_NAME, Operation.GIVE);
            CreamStandActionAbility entityFill = scene.ability(
                    FrostTravelerAddon.CS_ENTITY_FILL_NAME, Operation.ENTITY_FILL);
            helper.assertTrue(give.addToControlSchemeEditing(),
                    "1.16 put give_cs on the right-click hotbar, where the HUD editor could turn it on");
            helper.assertTrue(!entityFill.addToControlSchemeEditing(),
                    "1.16 cs_entity_fill was the item fill's Shift variation, not a slot of its own");
            for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbarSlots(scene)) {
                helper.assertTrue(slot.values().stream().noneMatch(
                                byInput -> byInput.containsValue(FrostTravelerAddon.GIVE_CS_NAME)),
                        "1.16 GiveCreamStarter.enabledInHudDefault was false: give_cs is off the default hotbar; slot "
                                + slot);
            }
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void shiftOnTheItemFillSlotUsesTheEntityFill(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            Map<InputKey.Modifier, Map<InputMethod, String>> itemFillSlot = null;
            for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbarSlots(scene)) {
                Map<InputMethod, String> base = slot.get(null);
                if (base != null && FrostTravelerAddon.CS_ITEM_FILL_NAME.equals(base.get(InputMethod.HOLD))) {
                    itemFillSlot = slot;
                }
            }
            helper.assertTrue(itemFillSlot != null, "setup: no hotbar slot holds cs_item_fill");
            Map<InputMethod, String> shift = itemFillSlot.get(InputKey.Modifier.SHIFT);
            helper.assertTrue(shift != null && FrostTravelerAddon.CS_ENTITY_FILL_NAME.equals(shift.get(InputMethod.HOLD)),
                    "1.16 cs_entity_fill was shiftVariationOf(CS_ITEM_FILL): Shift on that slot holds the entity fill;"
                            + " got " + shift);
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void switchWarnsNoCreamWithoutACreamStarter(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility change = scene.ability(FrostTravelerAddon.CS_SWITCH_NAME, Operation.SWITCH);
            ItemStack sprayer = scene.player.getMainHandItem();
            scene.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ConditionCheck empty = change.checkConditions(scene.power);
            helper.assertTrue(!empty.isPositive() && "jojo.message.action_condition.no_cream".equals(key(empty)),
                    "1.16 ChangeMode warned no_cream with no Cream Starter in either hand; got " + key(empty));
            scene.player.setItemInHand(InteractionHand.OFF_HAND, sprayer);
            helper.assertTrue(change.checkConditions(scene.power).isPositive(),
                    "1.16 ChangeMode took the Cream Starter in the off hand too");
            scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            scene.player.setItemInHand(InteractionHand.MAIN_HAND, sprayer);
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void disguiseItemWarnsAsDisguiseItemActionDid(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility disguise = scene.ability(
                    FrostTravelerAddon.CS_DSG_ITEM_NAME, Operation.DISGUISE_ITEM);
            CreamStarterState state = CreamStarterService.state(scene.player);
            int ammo = state.ammo();
            helper.assertTrue(disguise.checkConditions(scene.power).isPositive(),
                    "setup: cs_dsg_item cannot be used with the sprayer, a free off hand and " + ammo + " cream");

            scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
            ConditionCheck hand = disguise.checkConditions(scene.power);
            helper.assertTrue("jojo.message.action_condition.hand".equals(key(hand)),
                    "1.16 DisguiseItemAction.checkHeldItems warned \"hand\" with a full off hand; got " + key(hand));
            scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

            state.setAmmo(CreamOperations.DISGUISE_ITEM_AMMO - 1);
            ConditionCheck low = disguise.checkConditions(scene.power);
            helper.assertTrue(!low.isPositive() && "jojo.message.action_condition.".equals(key(low)),
                    "1.16 DisguiseItemAction.checkHeldItems gave conditionMessage(\"\") under 50 cream; got "
                            + key(low));
            state.setAmmo(ammo);

            ItemStack sprayer = scene.player.getMainHandItem();
            scene.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            scene.player.setItemInHand(InteractionHand.OFF_HAND, sprayer);
            ConditionCheck main = disguise.checkConditions(scene.power);
            helper.assertTrue("jojo.message.action_condition.no_cream.main".equals(key(main)),
                    "1.16 DisguiseItemAction.checkHeldItems warned no_cream.main without the Cream Starter in the"
                            + " main hand; got " + key(main));
            scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            scene.player.setItemInHand(InteractionHand.MAIN_HAND, sprayer);
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void theEnderEyeLaunchSoundHasTheVanillaPitch(GameTestHelper helper) {
        List<Float> pitches = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> launch = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == SoundEvents.ENDER_EYE_LAUNCH) {
                pitches.add(event.getOriginalPitch());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, launch);
        try (Scene scene = new Scene(helper)) {
            Cow cow = scene.addCow(scene.player.position().add(0.0D, 0.0D, 2.0D));
            try {
                for (int launchCount = 0; launchCount < 20; launchCount++) {
                    CreamOperations.playEnderEyeLaunch(helper.getLevel(), cow);
                }
            }
            finally {
                cow.discard();
            }
            helper.assertTrue(pitches.size() == 20, "setup: heard " + pitches.size() + " of 20 launches");
            float low = 0.4F / 1.2F - EPS;
            float high = 0.4F / 0.8F + EPS;
            helper.assertTrue(pitches.stream().allMatch(pitch -> pitch >= low && pitch <= high)
                            && pitches.stream().distinct().count() > 1,
                    "1.16 PutItemAction.enderEyeFlight pitched the launch 0.4 / (random * 0.4 + 0.8); got " + pitches);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(launch);
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void deformFailsWhenACreatureIsBeforeTheBarrier(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility deform = scene.ability(FrostTravelerAddon.CS_DEFORM_NAME, Operation.DEFORM);
            BlockPos near = scene.block(4, 13, 1);
            BlockPos far = scene.block(5, 13, 1);
            scene.turn(-90.0F, 0.0F);
            helper.getLevel().setBlockAndUpdate(near, Blocks.IRON_BARS.defaultBlockState());
            Cow cow = scene.addCow(scene.at(3.0D, 12.5D, 1.5D));
            Vec3 start = scene.player.position();
            try {
                ConditionCheck blocked = deform.checkConditions(scene.power);
                helper.assertTrue(!blocked.isPositive() && blocked.getWarning() == null,
                        "1.16 cs_deform took the vanilla crosshair target: a cow before the bars made it an entity,"
                                + " and the BLOCK action refused it silently; got " + blocked.isPositive() + " "
                                + key(blocked));
                ItemStack sprayer = scene.player.getMainHandItem();
                scene.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                ConditionCheck noCream = deform.checkConditions(scene.power);
                helper.assertTrue(noCream.getWarning() == null,
                        "1.16 asked DeformAction.checkTarget (no_cream) only for a block target; with a cow in front it"
                                + " warned " + key(noCream));
                scene.player.setItemInHand(InteractionHand.MAIN_HAND, sprayer);

                cow.moveTo(scene.at(3.0D, 12.5D, 3.5D));
                helper.assertTrue(deform.checkConditions(scene.power).isPositive(),
                        "a cow off the line of sight must not stop cs_deform");

                // past the 3-block entity reach vanilla made the crosshair a miss
                helper.getLevel().setBlockAndUpdate(near, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(far, Blocks.IRON_BARS.defaultBlockState());
                helper.assertTrue(deform.checkConditions(scene.power).isPositive(),
                        "setup: cs_deform cannot go through the farther bars");
                cow.moveTo(scene.at(5.1D, 12.5D, 1.5D));
                helper.assertTrue(!deform.checkConditions(scene.power).isPositive(),
                        "1.16's crosshair missed with a cow past the entity reach before the bars; cs_deform went on");
                helper.getLevel().setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());

                // a creature that steps in during the charge ends it unfired
                helper.getLevel().setBlockAndUpdate(near, Blocks.IRON_BARS.defaultBlockState());
                cow.moveTo(scene.at(3.0D, 12.5D, 3.5D));
                EntityActionInstance action = scene.press(deform, InputMethod.HOLD);
                helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "setup: cs_deform did not charge; got " + action.getPhase());
                scene.tick(2);
                cow.moveTo(scene.at(3.0D, 12.5D, 1.5D));
                scene.tick(2);
                helper.assertTrue(action.isOver() && scene.player.position().distanceToSqr(start) < 1.0E-6D,
                        "1.16 tickHeldAction rechecked the crosshair target and stopped the held cs_deform; over "
                                + action.isOver() + ", user at " + scene.player.position());
                scene.release();
            }
            finally {
                cow.discard();
                helper.getLevel().setBlockAndUpdate(near, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void entityFillAddsTheTargetsRoundedHealth(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStarterState state = CreamStarterService.state(scene.player);
            scene.turn(0.0F, 0.0F);
            Cow cow = scene.addCow(scene.player.position().add(0.0D, 0.5D, 1.5D));
            try {
                cow.setHealth(6.6F);
                int ammo = state.ammo();
                helper.assertTrue(CreamOperations.fillFromEntity(scene.player) && state.ammo() == ammo + 7,
                        "1.16 FillingEntities added Math.round(6.6) = 7; got " + (state.ammo() - ammo));
                cow.setHealth(0.4F);
                ammo = state.ammo();
                CreamOperations.fillFromEntity(scene.player);
                helper.assertTrue(state.ammo() == ammo,
                        "1.16 FillingEntities added Math.round(0.4) = 0; got " + (state.ammo() - ammo));
            }
            finally {
                cow.discard();
            }
            helper.succeed();
        }
    }

    // 1.16 FillingEntities took an OUTLINE ray: a short grass within 3 blocks was a block hit, which fills nothing;
    // only a ray that missed filled from the user.
    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void entityFillStopsAtTheGrassItLooksAt(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStarterState state = CreamStarterService.state(scene.player);
            scene.turn(-90.0F, 0.0F);
            BlockPos grass = scene.block(3, 13, 1);
            try {
                helper.getLevel().setBlockAndUpdate(grass, Blocks.SHORT_GRASS.defaultBlockState());
                int ammo = state.ammo();
                helper.assertTrue(!CreamOperations.fillFromEntity(scene.player) && state.ammo() == ammo,
                        "1.16's OUTLINE ray met the grass in front and filled nothing; filled "
                                + (state.ammo() - ammo));
                helper.getLevel().setBlockAndUpdate(grass, Blocks.AIR.defaultBlockState());
                helper.assertTrue(CreamOperations.fillFromEntity(scene.player),
                        "setup: a ray that missed no longer fills from the user");
            }
            finally {
                helper.getLevel().setBlockAndUpdate(grass, Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        }
    }

    private static List<Map<InputKey.Modifier, Map<InputMethod, String>>> hotbarSlots(Scene scene) {
        List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots = new ArrayList<>();
        ControlSchemeTemplate template = scene.power.getMoveset().controlScheme;
        for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
            for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
                slots.addAll(hotbar.slots);
            }
        }
        return slots;
    }

    private static void hidden(GameTestHelper helper, MobEffectInstance effect, String name) {
        helper.assertTrue(effect != null && !effect.isAmbient() && !effect.isVisible() && !effect.showIcon(),
                "1.16 DeformTargetAction gave " + name + " with ambient, visible and showIcon false; got " + effect
                        + (effect != null ? " visible " + effect.isVisible() + " icon " + effect.showIcon() : ""));
    }

    private static String key(ConditionCheck check) {
        Component warning = check.getWarning();
        return warning != null && warning.getContents() instanceof TranslatableContents contents
                ? contents.getKey()
                : null;
    }

    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final Player player;
        private final StandPower power;
        private ConditionCheck lastCheck;
        private HeldInputEntry lastHeld;

        private Scene(GameTestHelper helper) {
            this(helper, false);
        }

        private Scene(GameTestHelper helper, boolean serverPlayer) {
            this.helper = helper;
            if (serverPlayer) {
                FakePlayer fake = FakePlayerFactory.get(helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "FrostStandTaskServer"));
                fake.setGameMode(GameType.SURVIVAL);
                player = fake;
            }
            else {
                player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                        new GameProfile(UUID.randomUUID(), "FrostStandTask")) {
                    @Override
                    public boolean isSpectator() {
                        return false;
                    }

                    @Override
                    public boolean isCreative() {
                        return false;
                    }
                };
            }
            player.setNoGravity(true);
            player.setPos(at(1.5D, 12.0D, 1.5D));
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Cream Starter user");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(FrostTravelerAddon.CREAM_STARTER.get());
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            player.setItemInHand(InteractionHand.MAIN_HAND, CreamStarterService.createOwnedSprayer(player));
            helper.assertTrue(FrostTravelerAddon.CREAM_STARTER.get().summon(player, power)
                            && FrostTravelerAddon.controller(player) != null,
                    "Could not summon the Cream Starter");
            stand().summonLockTicks = 0;
        }

        private StandEntity stand() {
            return power.getSummonedStandEntity();
        }

        private Cow addCow(Vec3 pos) {
            Cow added = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(added != null, "Could not create a cow");
            added.setNoAi(true);
            added.setNoGravity(true);
            inArea(pos);
            added.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(added), "Could not add a cow");
            return added;
        }

        private Vec3 at(double x, double y, double z) {
            return inArea(helper.absoluteVec(new Vec3(x, y, z)));
        }

        private BlockPos block(int x, int y, int z) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            inArea(Vec3.atCenterOf(pos));
            return pos;
        }

        // beyond the structure's chunks a fixture may stand where entities are not visible to world queries yet
        private Vec3 inArea(Vec3 pos) {
            AABB area = helper.getBounds();
            helper.assertTrue(pos.x >= area.minX && pos.x < area.maxX && pos.z >= area.minZ && pos.z < area.maxZ,
                    "Fixture: " + pos + " lies outside the test structure " + area
                            + ", whose chunks alone the framework loads before the test");
            return pos;
        }

        private void clearOwnedSprayers() {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (CreamStarterService.isOwnedSprayer(player, player.getInventory().getItem(slot))) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
        }

        private int ownedSprayers() {
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (CreamStarterService.isOwnedSprayer(player, player.getInventory().getItem(slot))) {
                    count++;
                }
            }
            return count;
        }

        private CreamStandActionAbility ability(String name, Operation operation) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(ability instanceof CreamStandActionAbility action && action.operation() == operation,
                    "1.16 " + name + " was a StandEntityAction; the moveset has " + ability);
            return (CreamStandActionAbility) ability;
        }

        private void turn(float yaw, float pitch) {
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;
            player.setXRot(pitch);
            player.xRotO = pitch;
        }

        /**
         * A press as ClAbilityInputPacket handles it: the condition check of the move the slot resolves to
         * (AbilityInput.withConditionCheck), and only if that lets it through, the key press. True if admitted.
         */
        private boolean pressAsServer(String baseName, InputMethod input) {
            forgetCachedMoves();
            Ability base = power.getAbility(baseName);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(baseName);
            helper.assertTrue(base != null && selected != null, baseName + " is not in the moveset");
            lastCheck = selected.conditionCheck;
            lastHeld = null;
            if (!AbilityInput.withConditionCheck(selected, player, input)) {
                return false;
            }
            lastHeld = AbilityInput.keyPress(KEY, selected.ability, player, null, input, 0,
                    BufferingState.clickCanBuffer(), base.getAbilityId());
            return true;
        }

        /**
         * 1.16 StandEntityAction.afterClick unsummoned the Stand a refused press had summoned. The sprayer actions
         * were refused because the sprayer exists only with the Stand out.
         */
        private void refusedWithTheStandAway(String baseName, InputMethod input, String warning) {
            boolean admitted = pressAsServer(baseName, input);
            helper.assertTrue(!admitted && !power.isSummoned() && power.getSummonedStandEntity() == null,
                    "1.16 refused " + baseName + " pressed with the Stand away here, and afterClick unsummoned the"
                            + " Stand its press had summoned; admitted " + admitted + ", summoned " + power.isSummoned());
            helper.assertTrue(warning == null || warning.equals(key(lastCheck)),
                    "1.16 " + baseName + " pressed with the Stand away warned " + warning + "; got " + key(lastCheck));
        }

        // the moves and their condition checks are cached for the tick
        private void forgetCachedMoves() {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
        }

        // pressed by its key and kept held (the key's resolved variation is not asked, as press does)
        private EntityActionInstance holdWith(CreamStandActionAbility ability) {
            power.setStamina(power.getMaxStamina());
            ConditionCheck check = ability.checkConditions(power);
            helper.assertTrue(check.isPositive(), "setup: " + ability.name() + " cannot be used here: " + key(check));
            BufferingState state = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(KEY, ability, player, null, InputMethod.HOLD, 0, state, ability.getAbilityId());
            EntityActionInstance action = stand().getCurStandAction();
            helper.assertTrue(state.isActionSuccess && action != null && action.ability == ability && isHeldByKey(KEY)
                            && (action.getPhase() == ActionPhase.PERFORM || action.getPhase() == ActionPhase.BUTTON_CHARGE),
                    "setup: " + ability.name() + " is not held: started " + state.isActionSuccess + ", the Stand's action "
                            + action);
            return action;
        }

        // another key's press as ClAbilityInputPacket handles it: the key's resolved ability, its check, the press
        private BufferingState pressWith(String baseName, InputMethod input) {
            forgetCachedMoves();
            Ability base = power.getAbility(baseName);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(baseName);
            helper.assertTrue(base != null && selected != null, baseName + " is not in the moveset");
            BufferingState state = BufferingState.clickCanBuffer();
            if (AbilityInput.withConditionCheck(selected, player, input)) {
                AbilityInput.keyPress(OTHER_KEY, selected.ability, player, null, input, 0, state, base.getAbilityId());
            }
            AbilityInput.keyRelease(OTHER_KEY, player);
            return state;
        }

        private boolean isHeldByKey(short key) {
            EntityActionInputState input = player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
            return input.heldKeys.containsKey(key) && input.heldKeys.get(key).action != null;
        }

        private void clearHolds() {
            player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            LivingComponentAction.getComponent(stand()).setAction(null, player, SyncType.NO_SYNC);
        }

        private EntityActionInstance press(CreamStandActionAbility ability, InputMethod input) {
            ConditionCheck check = ability.checkConditions(power);
            helper.assertTrue(check.isPositive(), "setup: " + ability.name() + " cannot be used here: "
                    + key(check));
            HeldInputEntry held = AbilityInput.keyPress(KEY, ability, player, null, input, 0,
                    BufferingState.clickOnly(), ability.getAbilityId());
            EntityActionInstance action = stand().getCurStandAction();
            helper.assertTrue(action != null && action.ability == ability
                            && (input != InputMethod.HOLD || held != null && held.action == action),
                    "1.16 " + ability.name() + " set a task on the Stand; got " + action);
            return action;
        }

        private void tick(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                stand().getStandActionComponent().tick();
            }
        }

        // the release reaches the action now; the Stand clears a stopped action on its next tick
        private void release() {
            AbilityInput.keyRelease(KEY, player);
            if (stand().getCurStandAction() != null && stand().getCurStandAction().isOver()) {
                stand().getStandActionComponent().tick();
            }
        }

        private void runToEnd(EntityActionInstance action) {
            for (int tick = 0; tick < 40 && stand().getCurStandAction() == action; tick++) {
                stand().getStandActionComponent().tick();
            }
            helper.assertTrue(stand().getCurStandAction() != action && action.isOver(),
                    "The " + action.ability + " Stand action did not end");
            release();
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, player);
            if (power.isSummoned()) {
                FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(player, power);
            }
            player.discard();
        }
    }
}
