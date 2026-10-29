package rotp.addons.diverdown.ability;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;

/**
 * 1.16 diver_down_protection (DiverDownInsideProtection :39, holdType in its constructor) and diver_down_glide
 * (InitStands :70, holdType) were hold-type Stand actions with no recovery: StandEntityAction.isCancelable let a
 * pressed Stand action replace them at once, and any other action of the power that went off ended their hold
 * (PowerBaseImpl stopHeldAction(false)). Neither had a cooldown.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownHoldGivesWayGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;
    private static final short HOLD_KEY = 71;
    private static final short OTHER_KEY = 72;

    private DiverDownHoldGivesWayGameTests() {}

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_protection_gives_way",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void insideProtectionGivesWayToAPunchAtOnceAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
                Cow host = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(host != null, "Could not create the cow Diver Down dives into");
                scene.prepareMob(host, new Vec3(scene.origin.x + 1.5D, scene.origin.y, scene.origin.z));
                Ability protection = scene.power.getAbility(DiverDownAddon.INSIDE_PROTECTION_NAME);
                Ability punch = scene.power.getAbility(DiverDownAddon.PUNCH);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                helper.assertTrue(protection instanceof InsideProtectionAbility && punch != null && phasing != null,
                        "Fixture: Inside Protection, the punch or Entity Phasing is missing");

                // a punch pressed during the hold replaces it at once
                EntityActionInstance hold = holdProtection(scene, host, protection);
                BufferingState pressed = press(scene, punch, InputMethod.CLICK, OTHER_KEY);
                EntityActionInstance now = standAction.getAction();
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && now != null && now != hold
                                && now.ability == punch && hold.isOver(),
                        "1.16 let a punch pressed during Inside Protection replace it at once (isCancelable, no "
                                + "recovery): started " + pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer
                                + ", the Stand's action " + now + ", the protection over " + hold.isOver() + " in "
                                + hold.getPhase());
                helper.assertTrue(scene.power.getAbilityCooldown(DiverDownAddon.INSIDE_PROTECTION_NAME) == 0,
                        "1.16 diver_down_protection had no cooldown, yet the replaced hold set "
                                + scene.power.getAbilityCooldown(DiverDownAddon.INSIDE_PROTECTION_NAME));
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                standAction.setAction(null, scene.owner, SyncType.NO_SYNC);

                // letting go ends the hold at once: no recovery to wait through
                hold = holdProtection(scene, host, protection);
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                helper.assertTrue(hold.isOver(),
                        "1.16 ended a let-go Inside Protection at once (no recovery ticks): phase " + hold.getPhase());
                standAction.setAction(null, scene.owner, SyncType.NO_SYNC);

                // Retract, a plain ability of the power (a StandEntityAction in 1.16), goes off and ends the hold
                hold = holdProtection(scene, host, protection);
                AbilityConditionCheck key = resolve(scene, phasing);
                helper.assertTrue(key != null && key.ability instanceof RetractAbility,
                        "Fixture: the Entity Phasing key does not hold Retract while Diver Down is inside");
                BufferingState retract = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(retract.isActionSuccess && !scene.stand.isEmbedded(),
                        "1.16 let Retract through Inside Protection (heldAllowsOtherAction of a hold-type action): "
                                + "went off " + retract.isActionSuccess + ", Diver Down inside "
                                + scene.stand.isEmbedded());
                helper.assertTrue((hold.isOver() || hold.getPhase() == ActionPhase.RECOVERY)
                                && !AbilityInput.isHeldByKey(scene.owner, hold),
                        "1.16 stopHeldAction(false) ended Inside Protection once Retract went off: phase "
                                + hold.getPhase());
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_glide_ends",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void wallGlideEndsWhenAnotherAbilityOfThePowerGoesOffAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
                Ability glide = scene.power.getAbility(DiverDownAddon.WALL_GLIDE_NAME);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                helper.assertTrue(glide instanceof WallGlideAbility && phasing instanceof EntityPhasingAbility,
                        "Fixture: Wall Glide or Entity Phasing is missing");
                Cow cow = scene.mob(2.0D, 0.5D);

                // held off a wall the glide goes on, paused (NEGATIVE_CONTINUE_HOLD)
                BufferingState held = press(scene, glide, InputMethod.HOLD, HOLD_KEY);
                EntityActionInstance hold = standAction.getAction();
                helper.assertTrue(held.isActionSuccess && hold instanceof WallGlideAbility.WallGlideAction
                                && hold.getPhase() == ActionPhase.PERFORM && AbilityInput.isHeldByKey(scene.owner, hold),
                        "Fixture: Wall Glide is not held: " + hold);

                BufferingState dive = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(dive.isActionSuccess && scene.stand.getEmbeddedTarget() == cow,
                        "1.16 let Entity Phasing through Wall Glide (a hold-type action): went off "
                                + dive.isActionSuccess + ", Diver Down inside " + scene.stand.getEmbeddedTarget());
                helper.assertTrue((hold.isOver() || hold.getPhase() == ActionPhase.RECOVERY)
                                && !AbilityInput.isHeldByKey(scene.owner, hold),
                        "1.16 stopHeldAction(false) ended Wall Glide once Entity Phasing went off: phase "
                                + hold.getPhase() + ", its key holds it " + AbilityInput.isHeldByKey(scene.owner, hold));
                helper.assertTrue(scene.power.getAbilityCooldown(DiverDownAddon.WALL_GLIDE_NAME) == 0,
                        "1.16 diver_down_glide had no cooldown, yet the ended hold set "
                                + scene.power.getAbilityCooldown(DiverDownAddon.WALL_GLIDE_NAME));
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownEntityPhasing was a StandEntityAction: pressed while Diver Down's task could not be cancelled (a heavy
    // punch's windup), StandEntityAction.checkTaskCancelling queued it (canBeQueued, queueNextAction) and the Stand
    // clicked it again when the task was over (checkInputBuffer). The port's plain ability dived at once.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_phasing_queued",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingPressedInAnUncancellableWindupWaitsBehindItAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
                Ability heavy = scene.power.getAbility(DiverDownAddon.HEAVY_PUNCH);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                helper.assertTrue(heavy != null && phasing instanceof EntityPhasingAbility,
                        "Fixture: the heavy punch or Entity Phasing is missing");
                Cow cow = scene.mob(2.0D, 0.5D);
                scene.stand.setFinisherMeter(0.0F);

                BufferingState punch = press(scene, heavy, InputMethod.CLICK, HOLD_KEY);
                EntityActionInstance windup = standAction.getAction();
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                helper.assertTrue(punch.isActionSuccess && windup != null && windup.getPhase() == ActionPhase.WINDUP
                                && !windup.canBeCancelledInto((EntityActionType) phasing),
                        "Fixture: the heavy punch is not in a windup nothing can cancel: " + windup);

                BufferingState dive = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(!dive.isActionSuccess && dive.shouldBuffer && scene.stand.getEmbeddedTarget() == null
                                && standAction.getAction() == windup,
                        "1.16 queued Entity Phasing pressed in the heavy punch's windup (checkTaskCancelling); went off "
                                + dive.isActionSuccess + ", queued " + dive.shouldBuffer + ", Diver Down inside "
                                + scene.stand.getEmbeddedTarget() + ", the Stand's action " + standAction.getAction());

                // the Stand clicked the queued action again once its task was over
                standAction.setAction(null, scene.owner, SyncType.NO_SYNC);
                resolve(scene, phasing);
                ActionInputBuffer.get(scene.owner).tickInputBuffer(
                        scene.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()));
                helper.assertTrue(scene.stand.getEmbeddedTarget() == cow,
                        "1.16 dived once the queued Entity Phasing was clicked again; Diver Down is inside "
                                + scene.stand.getEmbeddedTarget());
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownRetract (InitStands :75) was a StandEntityAction too: pressed while Diver Down was inside and its task
    // could not be cancelled (a heavy punch's windup), checkTaskCancelling queued it and the Stand clicked it again when
    // the task was over. The port's plain ability retracted at once.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_retract_queued",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void retractPressedInAnUncancellableWindupWaitsBehindItAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
                Ability heavy = scene.power.getAbility(DiverDownAddon.HEAVY_PUNCH);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                helper.assertTrue(heavy != null && phasing instanceof EntityPhasingAbility,
                        "Fixture: the heavy punch or Entity Phasing is missing");
                Cow cow = scene.mob(2.0D, 0.5D);
                scene.stand.setFinisherMeter(0.0F);

                BufferingState punch = press(scene, heavy, InputMethod.CLICK, HOLD_KEY);
                EntityActionInstance windup = standAction.getAction();
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                helper.assertTrue(scene.stand.setEmbeddedTarget(cow),
                        "Fixture: could not embed Diver Down in the cow");
                AbilityConditionCheck key = resolve(scene, phasing);
                helper.assertTrue(punch.isActionSuccess && windup != null && windup.getPhase() == ActionPhase.WINDUP
                                && key != null && key.ability instanceof RetractAbility
                                && !windup.canBeCancelledInto((EntityActionType) key.ability),
                        "Fixture: the Entity Phasing key does not hold Retract over a heavy punch's windup nothing can "
                                + "cancel: " + windup + ", " + (key == null ? null : key.ability));

                BufferingState retract = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(!retract.isActionSuccess && retract.shouldBuffer && scene.stand.getEmbeddedTarget() == cow
                                && standAction.getAction() == windup,
                        "1.16 queued Retract pressed in the heavy punch's windup (checkTaskCancelling); went off "
                                + retract.isActionSuccess + ", queued " + retract.shouldBuffer + ", Diver Down inside "
                                + scene.stand.getEmbeddedTarget() + ", the Stand's action " + standAction.getAction());

                // the Stand clicked the queued action again once its task was over
                standAction.setAction(null, scene.owner, SyncType.NO_SYNC);
                // ending the task retracted the Stand (StandEntity.onActionSet): the queued click finds it outside
                helper.assertTrue(!scene.stand.isEmbedded(),
                        "Fixture: ending the heavy punch did not retract Diver Down");
                resolve(scene, phasing);
                ActionInputBuffer.get(scene.owner).tickInputBuffer(
                        scene.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()));
                helper.assertTrue(!scene.stand.isEmbedded() && scene.stand.getEmbeddedTarget() == null,
                        "1.16 clicked the queued Retract again, not Entity Phasing, and refused it (not inside); "
                                + "Diver Down dived back into " + scene.stand.getEmbeddedTarget());
                // the refused replay used the queue up: a press of the key later is a plain Entity Phasing dive
                BufferingState later = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(later.isActionSuccess && scene.stand.getEmbeddedTarget() == cow,
                        "A press of the Entity Phasing key after the refused queued Retract did not dive: went off "
                                + later.isActionSuccess + ", Diver Down inside " + scene.stand.getEmbeddedTarget());
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // the Entity Phasing key pressed again once Diver Down is out queues Entity Phasing in the Retract's place
    // (queueNextAction replaced the queued action), and that one dives when the task is over
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_retract_replaced",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void aQueuedRetractReplacedByAQueuedEntityPhasingStillDivesAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
                Ability heavy = scene.power.getAbility(DiverDownAddon.HEAVY_PUNCH);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                helper.assertTrue(heavy != null && phasing instanceof EntityPhasingAbility,
                        "Fixture: the heavy punch or Entity Phasing is missing");
                Cow cow = scene.mob(2.0D, 0.5D);
                scene.stand.setFinisherMeter(0.0F);

                BufferingState punch = press(scene, heavy, InputMethod.CLICK, HOLD_KEY);
                EntityActionInstance windup = standAction.getAction();
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                helper.assertTrue(punch.isActionSuccess && windup != null && windup.getPhase() == ActionPhase.WINDUP,
                        "Fixture: the heavy punch did not start: " + windup);
                helper.assertTrue(scene.stand.setEmbeddedTarget(cow), "Fixture: could not embed Diver Down in the cow");
                BufferingState retract = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(retract.shouldBuffer && resolve(scene, phasing).ability instanceof RetractAbility,
                        "Fixture: the Retract was not queued");

                scene.stand.clearEmbeddedTarget();
                BufferingState dive = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                helper.assertTrue(dive.shouldBuffer && resolve(scene, phasing).ability == phasing,
                        "Fixture: Entity Phasing was not queued with Diver Down out: queued " + dive.shouldBuffer);

                standAction.setAction(null, scene.owner, SyncType.NO_SYNC);
                resolve(scene, phasing);
                ActionInputBuffer.get(scene.owner).tickInputBuffer(
                        scene.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()));
                helper.assertTrue(scene.stand.getEmbeddedTarget() == cow,
                        "1.16 queued Entity Phasing in the Retract's place and clicked it once the task was over; "
                                + "Diver Down is inside " + scene.stand.getEmbeddedTarget());
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 queued the Retract itself (checkTaskCancelling -> queueNextAction(this)) and clicked it again before the Stand
    // retracted (StandEntity.checkInputBuffer): Bone Shard Bomb and Spring Legs had taken Diver Down out of the mob
    // (setTargetInside(null)) by then, so DiverDownRetract refused it (dd_notinside) and Diver Down stayed out. The core
    // buffer keeps the key's base ability (Entity Phasing) and resolves it again, which dived into the mob under the
    // crosshair.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_retract_after_bomb",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void retractQueuedBehindBoneShardBombDoesNotDiveAgainAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> queuedRetractBehindAnEmbeddedOnlyMove(
                helper, placement, DiverDownAddon.BONE_SHARD_BOMB_NAME));
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_retract_after_legs",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void retractQueuedBehindSpringLegsDoesNotDiveAgainAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> queuedRetractBehindAnEmbeddedOnlyMove(
                helper, placement, DiverDownAddon.SPRING_LEGS_NAME));
    }

    private static void queuedRetractBehindAnEmbeddedOnlyMove(GameTestHelper helper, GameTestPlacement placement,
            String moveName) {
        DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
        try {
            scene.look(0.0F);
            LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
            Ability move = scene.power.getAbility(moveName);
            Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
            helper.assertTrue(move != null && phasing instanceof EntityPhasingAbility,
                    "Fixture: " + moveName + " or Entity Phasing is missing");
            Cow cow = scene.mob(2.0D, 0.5D);
            scene.stand.setFinisherMeter(0.0F);
            helper.assertTrue(scene.stand.setEmbeddedTarget(cow), "Fixture: could not embed Diver Down in the cow");
            // a dive says its shout; the bomb's retraction takes the embedding away again at once, the shout stays
            List<PlayLevelSoundEvent.AtPosition> dives = new ArrayList<>();
            Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
                if (event.getLevel() == helper.getLevel() && event.getSound() != null
                        && event.getSound().value() == DiverDownAddon.ANASUI_DIVER_ENTITY_PHASING.get()) {
                    dives.add(event);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayLevelSoundEvent.AtPosition.class, listener);
            scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener));

            BufferingState started = press(scene, move, InputMethod.CLICK, HOLD_KEY);
            EntityActionInstance windup = standAction.getAction();
            AbilityInput.keyRelease(HOLD_KEY, scene.owner);
            AbilityConditionCheck key = resolve(scene, phasing);
            helper.assertTrue(started.isActionSuccess && windup != null && windup.getPhase() == ActionPhase.WINDUP
                            && key != null && key.ability instanceof RetractAbility
                            && !windup.canBeCancelledInto((EntityActionType) key.ability),
                    "Fixture: " + moveName + " is not in a windup nothing can cancel, with Retract on the Entity "
                            + "Phasing key: " + windup + ", " + (key == null ? null : key.ability));

            BufferingState retract = press(scene, phasing, InputMethod.CLICK, OTHER_KEY);
            AbilityInput.keyRelease(OTHER_KEY, scene.owner);
            helper.assertTrue(!retract.isActionSuccess && retract.shouldBuffer && scene.stand.getEmbeddedTarget() == cow,
                    "1.16 queued Retract pressed in the " + moveName + " windup; went off " + retract.isActionSuccess
                            + ", queued " + retract.shouldBuffer + ", Diver Down inside "
                            + scene.stand.getEmbeddedTarget());

            untilDone(helper, scene, () -> standAction.getAction() == windup, 0, () -> {
                resolve(scene, phasing);
                ActionInputBuffer.get(scene.owner).tickInputBuffer(
                        scene.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()));
                helper.runAfterDelay(3, () -> {
                    try {
                        boolean marked = cow.hasEffect(moveName.equals(DiverDownAddon.SPRING_LEGS_NAME)
                                ? DiverDownAddon.SPRING_LEGS_EFFECT : DiverDownAddon.BONE_SHARD_BOMB_EFFECT);
                        helper.assertTrue(marked && scene.stand.getEmbeddedTarget() == null && dives.isEmpty(),
                                "1.16 refused the queued Retract once " + moveName + " had taken Diver Down out of the "
                                        + "cow: the cow is marked " + marked + ", Diver Down inside "
                                        + scene.stand.getEmbeddedTarget() + ", dive shouts " + dives.size());
                        scene.close();
                        helper.succeed();
                    } catch (Throwable error) {
                        scene.close();
                        throw error;
                    }
                });
            });
        } catch (Throwable error) {
            scene.close();
            throw error;
        }
    }

    // waits on the Stand's real ticks while the condition holds, then runs next
    private static void untilDone(GameTestHelper helper, DiverDownWallsGameTests.Scene scene,
            BooleanSupplier waiting, int waited, Runnable next) {
        helper.runAfterDelay(1, () -> {
            try {
                if (waiting.getAsBoolean() && waited < OWN_TICKS - 20) {
                    untilDone(helper, scene, waiting, waited + 1, next);
                    return;
                }
                helper.assertTrue(!waiting.getAsBoolean(), "The Stand's move did not finish in time");
                next.run();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 StandEntityAction.canClickDuringTask :495-497 refused the running action's own ability pressed again
    // (clickedAction == this, isChainable false; DiverDownWallGlide overrode neither): no message, not queued, the
    // running glide left alone, under any key.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_glide_pressed_again",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void wallGlidePressedAgainIsRefusedSilentlyAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);
                Ability glide = scene.power.getAbility(DiverDownAddon.WALL_GLIDE_NAME);
                helper.assertTrue(glide instanceof WallGlideAbility, "Fixture: Wall Glide is missing");
                BlockPos wall = scene.wall(1.0D, 0);
                scene.wall(1.0D, 1);
                scene.owner.setPos(scene.origin.x, scene.origin.y,
                        wall.getZ() - scene.owner.getBbWidth() / 2.0D - 0.005D);
                BufferingState held = press(scene, glide, InputMethod.HOLD, HOLD_KEY);
                EntityActionInstance hold = standAction.getAction();
                helper.assertTrue(held.isActionSuccess && hold instanceof WallGlideAbility.WallGlideAction
                                && hold.getPhase() == ActionPhase.PERFORM && AbilityInput.isHeldByKey(scene.owner, hold),
                        "Fixture: Wall Glide is not held on the wall: " + hold);
                AbilityConditionCheck again = resolve(scene, glide);
                helper.assertTrue(again != null && again.ability == glide && !again.conditionCheck.isPositive()
                                && again.conditionCheck.getWarning() == null,
                        "1.16 refused Wall Glide pressed again during the glide without a word, the port's check was "
                                + (again == null ? "nothing" : again.conditionCheck.isPositive() ? "positive"
                                        : "a refusal with " + again.conditionCheck.getWarning()));
                for (InputMethod method : new InputMethod[] {InputMethod.CLICK, InputMethod.HOLD}) {
                    BufferingState second = press(scene, glide, method, OTHER_KEY);
                    EntityActionInstance now = standAction.getAction();
                    AbilityInput.keyRelease(OTHER_KEY, scene.owner);
                    helper.assertTrue(!second.isActionSuccess && !second.shouldBuffer && now == hold && !hold.isOver(),
                            "1.16 neither restarted nor queued Wall Glide pressed again (" + method + "): started "
                                    + second.isActionSuccess + ", buffered " + second.shouldBuffer
                                    + ", the Stand's action " + now + " (was " + hold + ")");
                }
                AbilityInput.keyRelease(HOLD_KEY, scene.owner);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    private static EntityActionInstance holdProtection(DiverDownWallsGameTests.Scene scene, Cow host,
            Ability protection) {
        GameTestHelper helper = scene.helper;
        if (!scene.stand.isEmbedded()) {
            helper.assertTrue(scene.stand.setEmbeddedTarget(host), "Fixture: could not embed Diver Down in the cow");
        }
        scene.power.setStamina(scene.power.getMaxStamina());
        BufferingState held = press(scene, protection, InputMethod.HOLD, HOLD_KEY);
        EntityActionInstance hold = LivingComponentAction.getComponent(scene.stand).getAction();
        helper.assertTrue(held.isActionSuccess && hold instanceof InsideProtectionAbility.ProtectionAction
                        && hold.getPhase() == ActionPhase.PERFORM && AbilityInput.isHeldByKey(scene.owner, hold),
                "Fixture: Inside Protection is not held in its perform: " + hold);
        return hold;
    }

    // as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
    private static BufferingState press(DiverDownWallsGameTests.Scene scene, Ability base, InputMethod method,
            short keyId) {
        BufferingState state = BufferingState.clickCanBuffer();
        AbilityConditionCheck key = resolve(scene, base);
        if (key != null && AbilityInput.withConditionCheck(key, scene.owner, method)) {
            AbilityInput.keyPress(keyId, key.ability, scene.owner, null, method, 0.0F, state, base.getAbilityId());
        }
        return state;
    }

    // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
    private static AbilityConditionCheck resolve(DiverDownWallsGameTests.Scene scene, Ability base) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(scene.power, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        return scene.power.updateAvailableMoves().getContextVariationContainer(base);
    }
}
