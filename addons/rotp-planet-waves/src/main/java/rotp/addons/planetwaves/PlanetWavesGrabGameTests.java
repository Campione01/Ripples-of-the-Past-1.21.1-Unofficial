package rotp.addons.planetwaves;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.planetwaves.ability.PlanetWavesGrabAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 PlanetWavesGrab: the ENTITY target requirement (the task holds the grabbed entity, and a next action pressed at
 * nothing takes it over), Action.checkRangeAndTarget before the grab's own checkTarget (target_too_far), and
 * standTickPerform turning the Stand to the target's feet on every perform tick, also while it is manually controlled.
 * The grab is holdOnly, so a crosshair that leaves the target never stopped the hold.
 */
@GameTestHolder(PlanetWavesAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetWavesGrabGameTests {
    private static final short KEY = 125;
    private static final short OTHER_KEY = 126;

    private PlanetWavesGrabGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabHoldsItsTargetTurnsToTheFeetAndLetsGoWhenTheTaskDropsIt(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "PwGrabTask", new Vec3(4.5D, 2.0D, 1.5D))) {
            EntityActionInstance grab = scene.start();
            helper.assertTrue(grab.getTaskTarget(scene.owner.level()).getEntity() == scene.cow,
                    "1.16 getTargetRequirement ENTITY: the task holds the aimed cow, it holds "
                            + grab.getTaskTarget(scene.owner.level()));
            grab.setPhaseStart(ActionPhase.PERFORM);
            grab._tickAction();
            helper.assertTrue(scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the grab slows its target");
            helper.assertTrue(scene.stand.getCurStandAction() == grab, "the grab is not the Stand's action");

            scene.stand.setYRot(90.0F);
            scene.stand.setXRot(-40.0F);
            scene.stand.lookAtTaskTarget();
            scene.assertFacesTheFeet(helper, "1.16 standTickPerform turned the Stand to the feet of the grabbed cow");

            scene.stand.setTaskTarget(null);
            helper.assertTrue(grab.getTaskTarget(scene.owner.level()).isEmpty(scene.owner.level()),
                    "setTaskTarget(null) drops the grabbed entity");
            grab._tickAction();
            helper.assertTrue(!scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && grab.getPhase() != ActionPhase.PERFORM,
                    "1.16 let go once the task held no target: " + grab.getPhase() + ", slowed "
                            + scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));
            helper.succeed();
        }
    }

    // 1.16 StandEntityTask skipped only its own rotateStandTowardsTarget under manual control; the grab's
    // standTickPerform lookAt had no such condition.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabTurnsAManuallyControlledStandToTheFeetOfTheGrabbedEntity(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "PwGrabManual", new Vec3(4.5D, 2.0D, 1.5D))) {
            scene.stand.setManuallyControlled(true);
            helper.assertTrue(scene.stand.isManuallyControlled(), "fixture: the Stand is not manually controlled");
            EntityActionInstance grab = scene.start();
            helper.assertTrue(grab.getTaskTarget(scene.owner.level()).getEntity() == scene.cow,
                    "fixture: the task does not hold the cow: " + grab.getTaskTarget(scene.owner.level()));
            scene.stand.setYRot(90.0F);
            scene.stand.setXRot(-40.0F);
            grab.setPhaseStart(ActionPhase.PERFORM);
            grab._tickAction();
            helper.assertTrue(scene.stand.getCurStandAction() == grab && scene.stand.isManuallyControlled(),
                    "fixture: the grab must be running on the manually controlled Stand");
            scene.assertFacesTheFeet(helper,
                    "1.16 standTickPerform turned the Stand to the feet of the grabbed cow under manual control too");

            scene.stand.setYRot(90.0F);
            grab._tickAction();
            scene.assertFacesTheFeet(helper, "the turn must happen on every perform tick");
            helper.succeed();
        }
    }

    // 1.16 PlanetWavesGrab.standTickPerform (065 :92-96) set the Stand 0.85 blocks from the grabbed entity along the
    // entity's own look (pitch included) on every perform tick, manually controlled or not.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabPinsTheStandInFrontOfTheGrabbedEntityAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "PwGrabFront", new Vec3(4.5D, 2.0D, 1.5D))) {
            EntityActionInstance grab = scene.start();
            helper.assertTrue(grab.getTaskTarget(scene.owner.level()).getEntity() == scene.cow,
                    "fixture: the task does not hold the cow: " + grab.getTaskTarget(scene.owner.level()));
            // looking west (towards the user) and 30 degrees down
            scene.turnCow(90.0F, 30.0F);
            grab.setPhaseStart(ActionPhase.PERFORM);
            grab._tickAction();
            double pitch = Math.toRadians(30.0D);
            scene.assertInFront(helper, new Vec3(-0.85D * Math.cos(pitch), -0.85D * Math.sin(pitch), 0.0D),
                    "1.16 standTickPerform set the Stand 0.85 blocks in front of the grabbed cow, along its look");

            // the cow turns south and moves: the next perform tick pins the Stand again
            scene.cow.setPos(helper.absoluteVec(new Vec3(4.5D, 2.0D, 3.5D)));
            scene.turnCow(0.0F, 0.0F);
            grab._tickAction();
            scene.assertInFront(helper, new Vec3(0.0D, 0.0D, 0.85D),
                    "1.16 pinned the Stand in front of the grabbed cow on every perform tick");

            scene.stand.setManuallyControlled(true);
            helper.assertTrue(scene.stand.isManuallyControlled(), "fixture: the Stand is not manually controlled");
            scene.stand.setPos(scene.owner.position());
            scene.turnCow(-90.0F, 0.0F);
            grab._tickAction();
            scene.assertInFront(helper, new Vec3(0.85D, 0.0D, 0.0D),
                    "1.16 pinned a manually controlled Stand in front of the grabbed cow too");
            helper.assertTrue(grab.getPhase() == ActionPhase.PERFORM && !grab.isOver(),
                    "fixture: the grab let go: " + grab.getPhase());
            helper.succeed();
        }
    }

    // 1.16 Action.checkRangeAndTarget ran before PlanetWavesGrab.checkTarget: an entity under the crosshair beyond
    // 8 blocks of the Stand (4 without a line of sight) was target_too_far, whatever it was.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabPressRefusesATargetBeyondTheRangeWithTargetTooFar(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "PwGrabRange", new Vec3(6.5D, 2.0D, 1.5D))) {
            PlanetWavesGrabAbility grab = (PlanetWavesGrabAbility) scene.power.getAbility("planet_waves_grab");
            helper.assertTrue(grab != null, "the grab is not in the moveset");
            String far = "jojo.message.action_condition.target_too_far";
            Cow cow = scene.cow;

            scene.aim(cow);
            ConditionCheck check = grab.checkConditions(scene.power);
            helper.assertTrue(check.isPositive(), "5 blocks in the open was refused: " + key(check));

            cow.setPos(helper.absoluteVec(new Vec3(11.0D, 2.0D, 1.5D)));
            scene.aim(cow);
            check = grab.checkConditions(scene.power);
            helper.assertTrue(!check.isPositive() && far.equals(key(check)),
                    "9.5 blocks is beyond 1.16's 8 blocks, got " + key(check));

            cow.setPos(helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D)));
            wall(helper, Blocks.STONE);
            scene.aim(cow);
            check = grab.checkConditions(scene.power);
            helper.assertTrue(!check.isPositive() && far.equals(key(check)),
                    "5 blocks behind a wall is beyond 1.16's 4 without a line of sight, got " + key(check));

            cow.setPos(helper.absoluteVec(new Vec3(4.5D, 2.0D, 1.5D)));
            scene.aim(cow);
            check = grab.checkConditions(scene.power);
            helper.assertTrue(check.isPositive(), "3 blocks behind a wall was refused: " + key(check));
            wall(helper, Blocks.AIR);

            // a thing the grab cannot hold: too far first, no_target in range
            ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.STICK));
            scene.extras.add(item);
            item.setNoGravity(true);
            // above the barrier layer the test framework puts around the structure
            item.setPos(helper.absoluteVec(new Vec3(11.0D, 3.0D, 1.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(item), "item fixture failed");
            cow.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 20.5D)));
            scene.aim(item);
            check = grab.checkConditions(scene.power);
            helper.assertTrue(!check.isPositive() && far.equals(key(check)),
                    "1.16 checked the range before the kind of entity, got " + key(check));
            item.setPos(helper.absoluteVec(new Vec3(6.5D, 3.0D, 1.5D)));
            check = grab.checkConditions(scene.power);
            helper.assertTrue(!check.isPositive() && PlanetWavesGrabAbility.NO_TARGET.equals(key(check)),
                    "an item in range is no_target, got " + key(check));

            // nothing aimed: the grab's own 7-block clip finds the cow
            item.discard();
            cow.setPos(helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D)));
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(ActionTarget.EMPTY);
            scene.owner.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
            check = grab.checkConditions(scene.power);
            helper.assertTrue(check.isPositive(), "no aim, cow in the look line: " + key(check));
            helper.succeed();
        }
    }

    // The grab is holdOnly: 1.16 checkTarget answered NEGATIVE.setContinueHold(true) once the crosshair left the
    // target, so the hold went on and the task kept the grabbed entity.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabHoldGoesOnWhenTheViewLeavesTheTarget(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "PwGrabHold", new Vec3(4.5D, 2.0D, 1.5D))) {
            scene.aim(scene.cow);
            scene.power.tick(); // moves are cached per tick
            AbilityConditionCheck selected = scene.power.updateAvailableMoves()
                    .getContextVariationContainer(scene.power.getAbility("planet_waves_grab"));
            helper.assertTrue(selected != null, "the grab is not in the moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.HOLD),
                    "the press at the cow was refused: " + selected.conditionCheck.getWarning());
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, scene.owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            helper.assertTrue(press.isActionSuccess && scene.stand.getCurStandAction() != null,
                    "the press did not start the grab");
            EntityActionInstance grab = scene.stand.getCurStandAction();
            for (int tick = 0; tick < 5 && grab.getPhase() != ActionPhase.PERFORM; tick++) {
                grab._tickAction();
            }
            grab._tickAction(); // the perform starts on the tick after the phase changed
            helper.assertTrue(grab.getPhase() == ActionPhase.PERFORM && scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "fixture: the grab is not holding the cow: " + grab.getPhase());

            scene.owner.setXRot(-90.0F);
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(ActionTarget.EMPTY);
            grab._tickAction();
            grab._tickAction();
            helper.assertTrue(!grab.isOver() && grab.getPhase() == ActionPhase.PERFORM
                            && grab.getTaskTarget(scene.owner.level()).getEntity() == scene.cow
                            && scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "1.16 held the grab on when the crosshair left the target (holdOnly); phase " + grab.getPhase()
                            + ", task " + grab.getTaskTarget(scene.owner.level()));
            helper.succeed();
        }
    }

    // 1.16 PlanetWavesGrab was holdType(140) with no recovery: a Stand action pressed during the held grab replaced it
    // at once (StandEntityAction.checkTaskCancelling, isCancelable) and the grab's hold ended with its 200-tick cooldown
    // (PowerBaseImpl.onClickAction -> stopHeldAction(false)); an action of the power the Stand does not perform ended the
    // hold the same way, as letting go of its key did. The core grab release is no such action: 1.16 had no Stand grab
    // chain (a port addition, D-core-07), so like the manual control key it leaves the hold alone (core K5-27 B).
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aPunchPressedDuringTheHeldGrabReplacesItAtOnceAsIn116(GameTestHelper helper) {
        for (String pressed : new String[] { "punch", "heavy_punch" }) {
            try (Scene scene = new Scene(helper, "PwGrabGivesWay", new Vec3(4.5D, 2.0D, 1.5D))) {
                EntityActionInstance grab = scene.holdGrab();
                BufferingState state = scene.press(pressed, InputMethod.CLICK, OTHER_KEY);
                EntityActionInstance now = scene.stand.getCurStandAction();
                helper.assertTrue(state.isActionSuccess && !state.shouldBuffer && now != null && now != grab
                                && now.ability == scene.power.getAbility(pressed),
                        "1.16 let a " + pressed + " pressed during the held grab through at once (holdType, isCancelable):"
                                + " started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
                                + ", the Stand's action " + now);
                helper.assertTrue(grab.isOver() && !scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "1.16 ended the grab replaced by the " + pressed + " and freed its target: over " + grab.isOver()
                                + ", slowed " + scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));
                int cooldown = scene.power.getAbilityCooldown("planet_waves_grab");
                helper.assertTrue(cooldown == PlanetWavesGrabAbility.COOLDOWN_TICKS,
                        "1.16 stopHeldAction(false) set the grab's 200-tick cooldown when the " + pressed
                                + " replaced it: " + cooldown);
            }
        }
        // the port's grab release, a plain ability of the power (the Stand performs nothing), during a core grab
        try (Scene scene = new Scene(helper, "PwGrabKeptThroughRelease", new Vec3(4.5D, 2.0D, 1.5D))) {
            Cow held = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(held != null, "Cow fixture failed");
            scene.extras.add(held);
            held.setNoAi(true);
            held.setNoGravity(true);
            held.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 3.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(held), "Cow fixture could not join the level");
            EntityActionInstance grab = scene.holdGrab();
            scene.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(held);
            helper.assertTrue(StandUtil.getStandGrabTarget(scene.power) == held,
                    "setup: the Stand's core grab does not hold the second cow");
            BufferingState release = scene.press("grab_release", InputMethod.CLICK, OTHER_KEY);
            AbilityInput.keyRelease(OTHER_KEY, scene.owner);
            helper.assertTrue(release.isActionSuccess && StandUtil.getStandGrabTarget(scene.power) == null,
                    "1.16 let any action of the power through a held holdType Stand action (heldAllowsOtherAction), yet"
                            + " the grab release pressed during the held grab did not go off: " + release.isActionSuccess);
            helper.assertTrue(!grab.isOver() && grab.getPhase() == ActionPhase.PERFORM
                            && scene.stand.getCurStandAction() == grab && scene.isHeldByKey(KEY)
                            && scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && scene.power.getAbilityCooldown("planet_waves_grab") == 0,
                    "1.16 had no Stand grab chain, so only the power's own actions ended the held grab"
                            + " (stopHeldAction(false)); the port's grab release, no 1.16 action, ends no held action, as"
                            + " the manual control key (core K5-27 B): phase " + grab.getPhase() + ", the Stand's action "
                            + scene.stand.getCurStandAction() + ", its key holds it " + scene.isHeldByKey(KEY)
                            + ", slowed " + scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + ", cooldown "
                            + scene.power.getAbilityCooldown("planet_waves_grab"));
            AbilityInput.keyRelease(KEY, scene.owner);
            int cooldown = scene.power.getAbilityCooldown("planet_waves_grab");
            helper.assertTrue((grab.isOver() || grab.getPhase() == ActionPhase.RECOVERY)
                            && !scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && cooldown == PlanetWavesGrabAbility.COOLDOWN_TICKS,
                    "1.16 ended the held grab when its key was let go (stopHeldAction(true)), freed the cow and set the"
                            + " 200-tick cooldown: phase " + grab.getPhase() + ", slowed "
                            + scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + ", cooldown " + cooldown);
        }
        helper.succeed();
    }

    // 1.16 StandEntityAction.overrideVanillaMouseTarget, run by PowerBaseImpl.checkTarget before the grab's own checks:
    // the grab (TargetRequirement.ENTITY) pressed at a torch, a block without a collision shape, took what a collision
    // ray from the Stand's eyes met, so the cow in front of the Stand was grabbed though the user's crosshair line passed
    // beside it; with only a cow on the user's line, which the Stand's ray missed, the target stayed a block and the
    // press was refused. The Stand stands a block to the side of its user, its eyes level with the user's, looking the
    // same way.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabPressedAtATorchTakesWhatTheStandsRayMeetsAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "PwGrabTorch", new Vec3(4.5D, 2.0D, 2.5D))) {
            PlanetWavesGrabAbility grab = (PlanetWavesGrabAbility) scene.power.getAbility("planet_waves_grab");
            helper.assertTrue(grab != null, "the grab is not in the moveset");
            // the user looks east, level; the Stand a block south of the user's eye line
            scene.owner.setYRot(-90.0F);
            scene.owner.setYHeadRot(-90.0F);
            scene.owner.setXRot(0.0F);
            Vec3 eyes = scene.owner.getEyePosition();
            scene.stand.setNoGravity(true);
            scene.stand.moveTo(eyes.x + 0.35D, eyes.y - scene.stand.getEyeHeight(), eyes.z + 1.0D, -90.0F, 0.0F);
            scene.stand.setYHeadRot(-90.0F);
            BlockPos torch = BlockPos.containing(eyes).east();
            Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
            try {
                for (int east = 0; east <= 5; east++) {
                    for (int south = 0; south <= 1; south++) {
                        BlockPos at = torch.east(east).south(south);
                        placed.put(at, helper.getLevel().getBlockState(at));
                        helper.getLevel().setBlock(at, east == 0 && south == 0
                                ? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)
                                : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
                helper.assertTrue(helper.getLevel().getBlockState(torch).is(Blocks.WALL_TORCH)
                                && helper.getLevel().getBlockState(torch).getCollisionShape(helper.getLevel(), torch).isEmpty(),
                        "fixture: no torch without a collision shape at " + torch);
                ActionTarget pressedAt = new ActionTarget(torch, Direction.WEST);

                // the cow in front of the Stand, 3 blocks east, beside the user's crosshair line
                scene.cow.setPos(eyes.x + 3.0D, eyes.y - scene.cow.getBbHeight() / 2.0D, eyes.z + 1.0D);
                scene.pressAt(pressedAt);
                ConditionCheck check = grab.checkConditions(scene.power);
                helper.assertTrue(check.isPositive(), "1.16's grab pressed at a torch took the cow the Stand's ray met;"
                        + " the port's refused it (" + key(check) + ")");
                EntityActionInstance started = scene.start();
                helper.assertTrue(started.getTaskTarget(scene.owner.level()).getEntity() == scene.cow,
                        "1.16's grab pressed at a torch held the cow the Stand's ray met; the task holds "
                                + started.getTaskTarget(scene.owner.level()));
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

                // the cow on the user's crosshair line, beside the Stand's ray
                scene.cow.setPos(eyes.x + 3.0D, eyes.y - scene.cow.getBbHeight() / 2.0D, eyes.z);
                scene.pressAt(pressedAt);
                check = grab.checkConditions(scene.power);
                helper.assertTrue(!check.isPositive(), "1.16 refused the grab pressed at a torch when the Stand's ray met"
                        + " nothing past it (the target stayed a block); the port's went on for the cow on the user's line");
            }
            finally {
                placed.forEach((at, state) -> helper.getLevel().setBlock(at, state, Block.UPDATE_CLIENTS));
            }
            helper.succeed();
        }
    }


    // 1.16 pressed the grab at the client's mouse target, EntityStandType.clientHitResult: the vanilla pick, and behind
    // a miss the summoned Stand's precision ray, which the port's client keeps as the Stand's aim. PlanetWavesGrab
    // .checkTarget then took only that entity (no_target for one it could not hold) and looked for no other; the
    // port's 7-block look line, which the owner kept, is only for a press at nothing.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabTakesThePressedTargetOfThe116MouseTarget(GameTestHelper helper) {
        mouseTarget(helper, "PwGrabMouseTarget", Vec3.ZERO);
    }

    // The scene above as the R771 seal ran it (template x mod 16 = 12, z mod 16 = 13: the cows in the chunks east and
    // south-east of the user's), 256 blocks north of the template, where no chunk the framework or another test holds
    // reaches: the scene has only the chunks it holds itself.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabTakesThePressedTargetOfThe116MouseTargetAwayFromTheTemplate(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        mouseTarget(helper, "PwGrabMouseTargetAway", new Vec3(Math.floorMod(12 - origin.getX(), 16), 0.0D,
                Math.floorMod(13 - origin.getZ(), 16) - 256));
    }

    private static void mouseTarget(GameTestHelper helper, String name, Vec3 shift) {
        try (Scene scene = new Scene(helper, name, new Vec3(4.5D, 2.0D, 1.5D), shift)) {
            PlanetWavesGrabAbility grab = (PlanetWavesGrabAbility) scene.power.getAbility("planet_waves_grab");
            helper.assertTrue(grab != null, "the grab is not in the moveset");
            scene.lookEast();
            Vec3 eyes = scene.owner.getEyePosition();
            // the cow on the user's look line, 3 blocks east
            scene.cow.setPos(eyes.x + 3.0D, eyes.y - scene.cow.getBbHeight() / 2.0D, eyes.z);
            Cow beside = scene.extraCow(new Vec3(eyes.x + 3.0D, eyes.y - 0.7D, eyes.z + 2.0D));

            // the vanilla pick missed, the Stand's precision ray met the cow beside the line
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(new ActionTarget(beside));
            ConditionCheck check = grab.checkConditions(scene.power);
            EntityActionInstance started = scene.start();
            helper.assertTrue(check.isPositive() && started.getTaskTarget(scene.owner.level()).getEntity() == beside,
                    "1.16's mouse target behind a missed pick was the Stand's precision ray, the cow beside the line;"
                            + " the port's grab took " + started.getTaskTarget(scene.owner.level()) + " (" + key(check) + ")");
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

            // pressed at a thing it cannot hold, with the cow behind it on the look line
            beside.discard();
            ItemEntity item = new ItemEntity(helper.getLevel(), eyes.x + 1.5D, eyes.y - 0.125D, eyes.z,
                    new ItemStack(Items.STICK));
            item.setNoGravity(true);
            scene.extras.add(item);
            helper.assertTrue(helper.getLevel().addFreshEntity(item), "item fixture failed");
            scene.pressAt(new ActionTarget(item));
            check = grab.checkConditions(scene.power);
            helper.assertTrue(!check.isPositive() && PlanetWavesGrabAbility.NO_TARGET.equals(key(check)),
                    "1.16 refused the grab pressed at a thing it could not hold with no_target and looked for no"
                            + " other; the port's press is " + (check.isPositive() ? "accepted" : "refused with "
                            + key(check)));

            // pressed at nothing: the kept look line still finds the cow
            scene.pressAt(ActionTarget.EMPTY);
            helper.assertTrue(grab.checkConditions(scene.power).isPositive(),
                    "the owner kept the 7-block look line for a press at nothing; the cow on it was refused");
            helper.succeed();
        }
    }

    // 1.16 StandEntityAction.checkStandTarget ran after the grab's own checkTarget: canStandTargetEntity (no
    // friendlyFire) asked StandEntity.canAttack, which refused the user's own tamed animal and an ally whose team has
    // friendly fire off, silently; the Stand was summoned before the check (onClick), and canAttack asks only its user.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabRefusesWhatTheStandMayNotAttackAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "PwGrabStandTarget", new Vec3(1.5D, 2.0D, 20.5D))) {
            PlanetWavesGrabAbility grab = (PlanetWavesGrabAbility) scene.power.getAbility("planet_waves_grab");
            helper.assertTrue(grab != null, "the grab is not in the moveset");
            scene.lookEast();
            Vec3 eyes = scene.owner.getEyePosition();
            Wolf wolf = EntityType.WOLF.create(helper.getLevel());
            helper.assertTrue(wolf != null, "Wolf fixture failed");
            scene.extras.add(wolf);
            wolf.setNoAi(true);
            wolf.setNoGravity(true);
            wolf.tame(scene.owner);
            wolf.setPos(eyes.x + 3.0D, eyes.y - wolf.getBbHeight() / 2.0D, eyes.z);
            helper.assertTrue(helper.getLevel().addFreshEntity(wolf) && wolf.isOwnedBy(scene.owner),
                    "fixture: the wolf is not the user's");

            scene.pressAt(new ActionTarget(wolf));
            ConditionCheck check = grab.checkConditions(scene.power);
            if (check.isPositive() || key(check) != null) {
                wrong.add("the user's own wolf on the crosshair: 1.16 refused silently, the port "
                        + (check.isPositive() ? "grabs it" : "refuses with " + key(check)));
            }
            Cow near = scene.extraCow(new Vec3(eyes.x + 2.0D, eyes.y - 0.7D, eyes.z - 1.5D));
            if (grab.keepsTaskTarget(scene.stand, new ActionTarget(wolf))
                    || !grab.keepsTaskTarget(scene.stand, new ActionTarget(near))) {
                wrong.add("the task keeps the user's wolf " + grab.keepsTaskTarget(scene.stand, new ActionTarget(wolf))
                        + " and a cow " + grab.keepsTaskTarget(scene.stand, new ActionTarget(near))
                        + " (1.16 checkStandTarget: not the wolf)");
            }
            near.discard();

            scene.pressAt(ActionTarget.EMPTY);
            check = grab.checkConditions(scene.power);
            if (check.isPositive() || !PlanetWavesGrabAbility.NO_TARGET.equals(key(check))) {
                wrong.add("pressed at nothing with only the user's wolf on the look line: the grab may take none,"
                        + " so the kept look line finds none; the port " + (check.isPositive() ? "grabs the wolf"
                        : "refuses with " + key(check)));
            }

            // a teammate (here a cow on the user's team) without friendly fire
            Scoreboard scoreboard = helper.getLevel().getScoreboard();
            PlayerTeam team = scoreboard.addPlayerTeam("pw_grab_" + UUID.randomUUID().toString().substring(0, 8));
            try {
                team.setAllowFriendlyFire(false);
                scoreboard.addPlayerToTeam(scene.owner.getScoreboardName(), team);
                Cow ally = scene.extraCow(new Vec3(eyes.x + 3.0D, eyes.y - 0.7D, eyes.z + 1.0D));
                scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
                scene.pressAt(new ActionTarget(ally));
                check = grab.checkConditions(scene.power);
                if (check.isPositive() || key(check) != null) {
                    wrong.add("a teammate without friendly fire: 1.16 refused silently, the port "
                            + (check.isPositive() ? "grabs it" : "refuses with " + key(check)));
                }
                ally.discard();
            }
            finally {
                scoreboard.removePlayerTeam(team);
            }

            // behind a torch, on the Stand's collision ray
            scene.stand.setNoGravity(true);
            scene.stand.moveTo(eyes.x + 0.35D, eyes.y - scene.stand.getEyeHeight(), eyes.z + 1.0D, -90.0F, 0.0F);
            scene.stand.setYHeadRot(-90.0F);
            wolf.setPos(eyes.x + 3.0D, eyes.y - wolf.getBbHeight() / 2.0D, eyes.z + 1.0D);
            BlockPos torch = BlockPos.containing(eyes).east();
            BlockState before = helper.getLevel().getBlockState(torch);
            helper.getLevel().setBlock(torch,
                    Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            try {
                scene.pressAt(new ActionTarget(torch, Direction.WEST));
                check = grab.checkConditions(scene.power);
                if (check.isPositive() || key(check) != null) {
                    wrong.add("the user's wolf behind a torch on the Stand's ray: 1.16 refused silently, the port "
                            + (check.isPositive() ? "grabs it" : "refuses with " + key(check)));
                }
            }
            finally {
                helper.getLevel().setBlock(torch, before, Block.UPDATE_CLIENTS);
            }

            // Planet Waves away: the press is checked before it is summoned, by the same rule from its user
            PlanetWavesAddon.PLANET_WAVES.get().forceUnsummon(scene.owner, scene.power);
            helper.assertTrue(StandUtil.getSummonedStand(scene.owner) == null, "fixture: Planet Waves is still out");
            wolf.setPos(eyes.x + 3.0D, eyes.y - wolf.getBbHeight() / 2.0D, eyes.z);
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(new ActionTarget(wolf));
            check = grab.checkConditions(scene.power);
            if (check.isPositive() || key(check) != null) {
                wrong.add("the user's wolf with Planet Waves away: 1.16 summoned it and refused silently, the port "
                        + (check.isPositive() ? "accepts the press" : "refuses with " + key(check)));
            }
            scene.cow.setPos(eyes.x + 2.0D, eyes.y - scene.cow.getBbHeight() / 2.0D, eyes.z);
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(new ActionTarget(scene.cow));
            check = grab.checkConditions(scene.power);
            if (!check.isPositive()) {
                wrong.add("fixture: a cow with Planet Waves away was refused: " + key(check));
            }
        }
        helper.assertTrue(wrong.isEmpty(), "Planet Waves grab and checkStandTarget: " + wrong);
        helper.succeed();
    }

    // 1.16's HUD (ActionsOverlayGui.actionAvailability) and the client's own press check (PowerBaseImpl.onClickAction
    // -> checkRequirements) checked the grab at the client's mouse target, as the server did with the one the click
    // sent. The port's client never gets its user's aim back (only a Stand's, TrAimTargetPacket): with Planet Waves
    // away it reads the aim it sends, LivingComponentAction.CLIENT_PLAYER_AIM. The user stands in for its own client's
    // copy while isLocalPlayer() answers true.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grabOnTheUsersOwnClientChecksTheAimItSendsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "PwGrabOwnClient", new Vec3(4.5D, 2.0D, 1.5D))) {
            PlanetWavesGrabAbility grab = (PlanetWavesGrabAbility) scene.power.getAbility("planet_waves_grab");
            helper.assertTrue(grab != null, "the grab is not in the moveset");
            PlanetWavesAddon.PLANET_WAVES.get().forceUnsummon(scene.owner, scene.power);
            helper.assertTrue(StandUtil.getSummonedStand(scene.owner) == null, "fixture: Planet Waves is still out");
            LivingComponentAction component = LivingComponentAction.getComponent(scene.owner);
            ActionTarget cow = new ActionTarget(scene.cow);
            BlockPos stoneAt = helper.absolutePos(new BlockPos(1, 2, 4));
            BlockState before = helper.getLevel().getBlockState(stoneAt);
            helper.getLevel().setBlock(stoneAt, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            ActionTarget stone = new ActionTarget(stoneAt, Direction.NORTH);
            try {
                // looking up, so that only the aim gives the grab its cow
                scene.owner.setXRot(-90.0F);
                component.entityAim.setTarget(cow);
                ConditionCheck check = grab.checkConditions(scene.power);
                helper.assertTrue(check.isPositive(), "fixture: on the server the grab at the cow is refused ("
                        + key(check) + ")");
                component.entityAim.setTarget(ActionTarget.EMPTY);
                check = grab.checkConditions(scene.power);
                helper.assertTrue(!check.isPositive(), "fixture: looking up with no aim the grab is usable, so its check"
                        + " needs no aim");

                scene.owner.local = true;
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(cow);
                check = grab.checkConditions(scene.power);
                if (!check.isPositive()) {
                    wrong.add("the client sends the cow: 1.16's HUD showed the grab usable, the port's own client"
                            + " refuses it (" + key(check) + ")");
                }

                // looking at the cow, the client sends the stone it pressed at: 1.16 refused a press at a block
                scene.owner.lookAt(EntityAnchorArgument.Anchor.EYES, scene.cow.getEyePosition());
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(stone);
                check = grab.checkConditions(scene.power);
                if (check.isPositive() || !PlanetWavesGrabAbility.NO_TARGET.equals(key(check))) {
                    wrong.add("the client sends a stone with the cow on the look line: 1.16 refused it with no_target"
                            + " as the server does, the port's own client " + (check.isPositive()
                            ? "takes the cow on its look line" : "refuses with " + key(check)));
                }
            }
            finally {
                scene.owner.local = false;
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
                helper.getLevel().setBlock(stoneAt, before, Block.UPDATE_CLIENTS);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "Planet Waves grab on the user's own client: " + wrong);
        helper.succeed();
    }

    // 1.16 PowerBaseImpl.tickHeldAction counted holdType(140) from the press, so a Stand summoned by the press held its
    // lock's ticks inside that maximum: the grab's perform ran as many ticks fewer as the lock held it. Without the lock the
    // last perform tick is the 140th (tick 139, the phase then ends by its length); with it the hold is cut at the start of
    // the first tick whose held count (lock + perform tick) reaches 140, so that tick does not run.
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void grabCountsTheSummonLockInItsHoldMaximumAsIn116(GameTestHelper helper) {
        int lock = 5;
        int max = PlanetWavesGrabAbility.MAX_HOLD_TICKS;
        int free = lastTickStartedOfTheHeldGrab(helper, "PwGrabNoLock", 0);
        int locked = lastTickStartedOfTheHeldGrab(helper, "PwGrabLock", lock);
        helper.assertTrue(free == max - 1 && locked == max - lock,
                "1.16 held the grab " + max + " ticks from the press, the summon lock's " + lock + " included: the last perform"
                        + " tick should start at " + (max - 1) + " without the lock and the cut at " + (max - lock) + " with it;"
                        + " the port's were " + free + " and " + locked);
        helper.succeed();
    }

    private static int lastTickStartedOfTheHeldGrab(GameTestHelper helper, String name, int lockTicks) {
        try (Scene scene = new Scene(helper, name, new Vec3(4.5D, 2.0D, 1.5D))) {
            scene.aim(scene.cow);
            BufferingState state = scene.press("planet_waves_grab", InputMethod.HOLD, KEY);
            EntityActionInstance grab = scene.stand.getCurStandAction();
            helper.assertTrue(state.isActionSuccess && grab != null && scene.isHeldByKey(KEY),
                    "setup: the grab is not held: started " + state.isActionSuccess + ", the Stand's action " + grab);
            for (int tick = 0; tick < lockTicks; tick++) {
                helper.assertTrue(grab._tickHeldInSummonLock(), "setup: the lock tick " + tick + " did not count");
            }
            helper.assertTrue(grab.getTicksHeldInSummonLock() == lockTicks, "setup: the grab counted "
                    + grab.getTicksHeldInSummonLock() + " lock ticks, not " + lockTicks);
            for (int tick = 0; tick < 5 && grab.getPhase() != ActionPhase.PERFORM; tick++) {
                grab._tickAction();
            }
            helper.assertTrue(grab.getPhase() == ActionPhase.PERFORM, "setup: the grab did not reach its perform");
            int lastStarted = -1;
            for (int ticks = 0; !grab.isOver() && grab.getPhase() == ActionPhase.PERFORM
                    && ticks <= PlanetWavesGrabAbility.MAX_HOLD_TICKS + 5; ticks++) {
                lastStarted = (int) grab.getPhaseTick();
                grab._tickAction();
            }
            helper.assertTrue(grab.isOver() || grab.getPhase() != ActionPhase.PERFORM,
                    "the held grab never left its perform");
            helper.assertTrue(!scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && scene.power.getAbilityCooldown("planet_waves_grab") == PlanetWavesGrabAbility.COOLDOWN_TICKS,
                    "1.16 freed the cow and set the 200-tick cooldown when the hold ran out: slowed "
                            + scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + ", cooldown "
                            + scene.power.getAbilityCooldown("planet_waves_grab"));
            return lastStarted;
        }
    }

    private static void wall(GameTestHelper helper, Block block) {
        for (int y = 0; y <= 4; y++) {
            for (int z = -1; z <= 3; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(3, 1 + y, z)),
                        block.defaultBlockState());
            }
        }
    }

    private static String key(ConditionCheck check) {
        return check.getWarning() != null && check.getWarning().getContents() instanceof TranslatableContents t
                ? t.getKey()
                : null;
    }

    // a server player that answers as its own client's copy (LocalPlayer.isLocalPlayer) while local is set
    private static final class OwnerPlayer extends FakePlayer {
        boolean local;

        OwnerPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isLocalPlayer() {
            return local;
        }
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final OwnerPlayer owner;
        final Cow cow;
        final StandPower power;
        final StandEntity stand;
        final List<Entity> extras = new ArrayList<>();
        final PlanetWavesChunkTickets chunks;

        Scene(GameTestHelper helper, String name, Vec3 cowPosition) {
            this(helper, name, cowPosition, Vec3.ZERO);
        }

        // shift moves the whole scene; the tests that give it none place their own things by the template
        Scene(GameTestHelper helper, String name, Vec3 cowPosition, Vec3 shift) {
            this.helper = helper;
            // the box of every position these tests give an entity or a block
            chunks = new PlanetWavesChunkTickets(helper).hold(
                    helper.absoluteVec(new Vec3(-1.0D, 0.0D, -1.0D)).add(shift),
                    helper.absoluteVec(new Vec3(12.0D, 0.0D, 21.0D)).add(shift));
            owner = new OwnerPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)).add(shift));
            helper.getLevel().addNewPlayer(owner);
            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Cow fixture failed");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(helper.absoluteVec(cowPosition).add(shift));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(PlanetWavesAddon.PLANET_WAVES.get());
            power.setResolveLevel(4);
            helper.assertTrue(PlanetWavesAddon.PLANET_WAVES.get().summon(owner, power), "summon failed");
            stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            aim(cow);
        }

        // the client's vanilla pick as the press packet stores it, for the user and its Stand
        void pressAt(ActionTarget target) {
            LivingComponentAction.getComponent(owner).entityAim.setTarget(target);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
        }

        void lookEast() {
            owner.setYRot(-90.0F);
            owner.setYHeadRot(-90.0F);
            owner.setXRot(0.0F);
        }

        Cow extraCow(Vec3 position) {
            Cow extra = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(extra != null, "Cow fixture failed");
            extras.add(extra);
            extra.setNoAi(true);
            extra.setNoGravity(true);
            extra.setPos(position);
            helper.assertTrue(helper.getLevel().addFreshEntity(extra), "Cow fixture could not join the level");
            return extra;
        }

        void aim(Entity target) {
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(target));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
        }

        EntityActionInstance start() {
            Ability type = power.getAbility("planet_waves_grab");
            helper.assertTrue(type != null, "the grab is not in the moveset");
            EntityActionInstance grab = ((EntityActionType) type).initActionOnAbilityUse(
                    owner.level(), owner, stand, null);
            LivingComponentAction.getComponent(stand).setAction(grab, owner, SyncType.NO_SYNC);
            return grab;
        }

        // pressed by its key as ClAbilityInputPacket does, kept held, and ticked into its perform
        EntityActionInstance holdGrab() {
            aim(cow);
            BufferingState state = press("planet_waves_grab", InputMethod.HOLD, KEY);
            EntityActionInstance grab = stand.getCurStandAction();
            helper.assertTrue(state.isActionSuccess && grab != null
                            && grab.ability == power.getAbility("planet_waves_grab") && isHeldByKey(KEY),
                    "setup: the grab is not held: started " + state.isActionSuccess + ", the Stand's action " + grab);
            for (int tick = 0; tick < 5 && grab.getPhase() != ActionPhase.PERFORM; tick++) {
                grab._tickAction();
            }
            grab._tickAction(); // the perform starts on the tick after the phase changed
            helper.assertTrue(grab.getPhase() == ActionPhase.PERFORM && cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && power.getAbilityCooldown("planet_waves_grab") == 0,
                    "setup: the grab is not holding the cow without a cooldown: " + grab.getPhase());
            return grab;
        }

        // the key's resolved ability, its condition check, then the press
        BufferingState press(String name, InputMethod inputMethod, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            Ability base = power.getAbility(name);
            helper.assertTrue(base != null, "Missing ability " + name);
            AbilityConditionCheck container = resolve(name);
            if (container != null && AbilityInput.withConditionCheck(container, owner, inputMethod)) {
                AbilityInput.keyPress(key, container.ability, owner, null, inputMethod, 0, state, base.getAbilityId());
            }
            return state;
        }

        // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
        AbilityConditionCheck resolve(String name) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
        }

        boolean isHeldByKey(short key) {
            EntityActionInputState input = owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
            return input.heldKeys.containsKey(key) && input.heldKeys.get(key).action != null;
        }

        void turnCow(float yaw, float pitch) {
            cow.setYRot(yaw);
            cow.setYHeadRot(yaw);
            cow.setYBodyRot(yaw);
            cow.setXRot(pitch);
        }

        void assertInFront(GameTestHelper helper, Vec3 offset, String message) {
            Vec3 expected = cow.position().add(offset);
            helper.assertTrue(stand.position().distanceTo(expected) < 1.0E-4D,
                    message + ": expected " + expected + " (the cow at " + cow.position() + "), the Stand is at "
                            + stand.position());
        }

        void assertFacesTheFeet(GameTestHelper helper, String message) {
            Vec3 toFeet = cow.position().subtract(stand.position());
            float yaw = (float) (Math.toDegrees(Math.atan2(toFeet.z, toFeet.x)) - 90.0D);
            float yawDiff = Math.abs(((stand.getYRot() - yaw) % 360.0F + 540.0F) % 360.0F - 180.0F);
            helper.assertTrue(Math.abs(stand.getXRot()) < 1.0F && yawDiff < 1.0F,
                    message + ": rotation " + stand.getYRot() + "/" + stand.getXRot() + ", the feet are at yaw "
                            + yaw + " (stand " + stand.position() + ", cow " + cow.position() + ")");
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, owner);
            owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            if (power != null && power.isSummoned()) {
                PlanetWavesAddon.PLANET_WAVES.get().forceUnsummon(owner, power);
            }
            cow.discard();
            extras.forEach(Entity::discard);
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
            chunks.release();
        }
    }
}
