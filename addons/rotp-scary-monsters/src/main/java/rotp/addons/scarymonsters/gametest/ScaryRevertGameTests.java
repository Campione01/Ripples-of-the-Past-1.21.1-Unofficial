package rotp.addons.scarymonsters.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import rotp.addons.scarymonsters.gametest.ScaryOwnerDeathGameTests.OnlinePlayer;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16.5 DinosaurifyRevertAction and its registration (InitStands "dinosaurify_revert"): holdToFire(5, false),
 * 15 stamina, and full walk speed in every phase (only a Stand's own guard slows its user). Every case presses the ability
 * through the server input entry and ticks the Stand's action.
 */
@GameTestHolder("scary_monsters_owner_death")
@PrefixGameTestTemplate(false)
public final class ScaryRevertGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 7;
    private static final String REVERT = "dinosaurify_revert";
    private static final float REVERT_STAMINA = 15.0F;
    private static final int REVERT_CHARGE_TICKS = 5;
    private static final double MAX_OFFSET_SQR = 9.0D;

    private ScaryRevertGameTests() {}

    // 1.16 dinosaur form put no dinosaur in the world, so the user's own mount was never a revert candidate.
    @GameTest(template = "empty", batch = "scary_s22_revert_in_form", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void revertWhileInDinosaurFormTakesTheTargetBelowTheHorizon(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> revertWhileInDinosaurFormTakesTheTargetBelowTheHorizonBody(h));
    }

    private static void revertWhileInDinosaurFormTakesTheTargetBelowTheHorizonBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "S22Form")) {
            Vec3 targetSpot = f.feet.add(6.0D, 0.0D, 0.0D);
            ScaryDinosaurEntity target = f.dinosaurifiedCow(targetSpot);

            ScaryMonstersService.enableDinosaurForm(f.owner);
            helper.assertTrue(ScaryMonstersAddon.state(f.owner).dinosaurForm()
                            && f.owner.getVehicle() instanceof ScaryDinosaurEntity,
                    "Dinosaur form did not mount the user on its dinosaur");
            ScaryDinosaurEntity mount = (ScaryDinosaurEntity) f.owner.getVehicle();
            f.hold(mount);
            mount.positionRider(f.owner);
            f.owner.lookAt(EntityAnchorArgument.Anchor.EYES, target.position());

            double mountAlong = f.along(mount);
            String geometry = "pitch=" + f.owner.getXRot() + ", mountAlong=" + mountAlong
                    + ", mountOffsetSqr=" + f.offsetSqr(mount) + ", targetAlong=" + f.along(target)
                    + ", targetOffsetSqr=" + f.offsetSqr(target);
            helper.assertTrue(mount.isPlayerControlled() && f.owner.getXRot() > 0.0F
                            && mountAlong > 0.0D && mountAlong < f.along(target)
                            && f.offsetSqr(mount) <= MAX_OFFSET_SQR && f.offsetSqr(target) <= MAX_OFFSET_SQR,
                    "The fixture does not put the user's own mount ahead of the target on the look ray: " + geometry);

            float stamina = f.power.getStamina();
            helper.assertTrue(f.fireRevert(), "Revert did not start and fire while riding the form dinosaur");
            String outcome = geometry + ", cow=" + f.level.getEntity(f.cowUuid()) + ", targetRemoved="
                    + target.isRemoved() + ", mountRemoved=" + mount.isRemoved() + ", vehicle=" + f.owner.getVehicle()
                    + ", stamina=" + stamina + "->" + f.power.getStamina();
            helper.assertTrue(Math.abs(stamina - f.power.getStamina() - REVERT_STAMINA) < 0.01F,
                    "The revert did not cost 15 stamina: " + outcome);
            helper.assertTrue(f.cowIsBackAt(targetSpot) && target.isRemoved(),
                    "Looking down at a dinosaurified creature from dinosaur form did not revert it: " + outcome);
            helper.assertTrue(!mount.isRemoved() && f.owner.getVehicle() == mount
                            && ScaryMonstersAddon.state(f.owner).dinosaurForm(),
                    "The revert touched the user's own dinosaur form: " + outcome);
            LOGGER.info("R764_SCARY_REVERT_PASS in_form {}", outcome);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scary_s22_revert_other_form", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void revertPassesAnotherPlayersFormDinosaurInTheLineOfSight(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> revertPassesAnotherPlayersFormDinosaurInTheLineOfSightBody(h));
    }

    private static void revertPassesAnotherPlayersFormDinosaurInTheLineOfSightBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "S22Other")) {
            Vec3 targetSpot = f.feet.add(6.0D, 0.0D, 0.0D);
            ScaryDinosaurEntity target = f.dinosaurifiedCow(targetSpot);

            OnlinePlayer other = f.player("S22OtherB", f.feet.add(3.0D, 0.0D, 0.0D));
            ScaryMonstersService.enableDinosaurForm(other);
            helper.assertTrue(other.getVehicle() instanceof ScaryDinosaurEntity,
                    "Dinosaur form did not mount the other player on a dinosaur");
            ScaryDinosaurEntity otherMount = (ScaryDinosaurEntity) other.getVehicle();
            f.hold(otherMount);
            otherMount.positionRider(other);
            f.owner.lookAt(EntityAnchorArgument.Anchor.EYES, target.position());

            String geometry = "otherAlong=" + f.along(otherMount) + ", otherOffsetSqr=" + f.offsetSqr(otherMount)
                    + ", targetAlong=" + f.along(target) + ", targetOffsetSqr=" + f.offsetSqr(target);
            helper.assertTrue(otherMount.isPlayerControlled() && f.along(otherMount) > 0.0D
                            && f.along(otherMount) < f.along(target)
                            && f.offsetSqr(otherMount) <= MAX_OFFSET_SQR && f.offsetSqr(target) <= MAX_OFFSET_SQR,
                    "The fixture does not put the other player's mount ahead of the target on the look ray: "
                            + geometry);

            helper.assertTrue(f.fireRevert(), "Revert did not start and fire");
            String outcome = geometry + ", cow=" + f.level.getEntity(f.cowUuid()) + ", targetRemoved="
                    + target.isRemoved() + ", otherMountRemoved=" + otherMount.isRemoved();
            helper.assertTrue(f.cowIsBackAt(targetSpot) && target.isRemoved(),
                    "Another player's form dinosaur in the line of sight blocked the revert: " + outcome);
            helper.assertTrue(!otherMount.isRemoved() && other.getVehicle() == otherMount
                            && ScaryMonstersAddon.state(other).dinosaurForm(),
                    "The revert touched the other player's dinosaur form: " + outcome);
            LOGGER.info("R764_SCARY_REVERT_PASS other_form {}", outcome);
        }
        helper.succeed();
    }

    // Owner rule of 2026-10-15: only a Stand's own guard slows its user, so no Scary Monsters action does.
    @GameTest(template = "empty", batch = "scary_s22_walk_speed", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void userKeepsFullWalkSpeedInEveryPhase(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> userKeepsFullWalkSpeedInEveryPhaseBody(h));
    }

    private static void userKeepsFullWalkSpeedInEveryPhaseBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "S22Walk")) {
            float stamina = f.power.getStamina();
            Map<ActionPhase, Float> held = f.walkSpeeds(REVERT, InputMethod.HOLD);
            // The hold fires in its 5th tick (1.16 tickHeldAction) and the Stand's task runs the 1-tick perform in its
            // next tick, so the perform is seen after the 5th tick and the recovery after the 6th, with its 15 stamina.
            helper.assertTrue(held.containsKey(ActionPhase.BUTTON_CHARGE) && held.containsKey(ActionPhase.PERFORM)
                            && held.containsKey(ActionPhase.RECOVERY)
                            && f.walked.phasesLength.getFloat(ActionPhase.PERFORM) == 1.0F
                            && f.walkTicks == REVERT_CHARGE_TICKS + 1
                            && Math.abs(stamina - f.power.getStamina() - REVERT_STAMINA) < 0.01F,
                    "Revert did not run through its charge, perform and recovery: " + held + ", ticks=" + f.walkTicks
                            + ", stamina=" + stamina + "->" + f.power.getStamina());
            helper.assertTrue(held.get(ActionPhase.BUTTON_CHARGE) == 1.0F,
                    "The user is slowed during the revert charge: " + held);
            helper.assertTrue(held.get(ActionPhase.PERFORM) == 1.0F,
                    "The user is slowed during the revert perform: " + held);
            helper.assertTrue(held.get(ActionPhase.RECOVERY) == 1.0F,
                    "The user is slowed during the revert recovery: " + held);

            f.reset();
            Map<ActionPhase, Float> clicked = f.walkSpeeds("dynamic_vision_close", InputMethod.CLICK);
            helper.assertTrue(clicked.containsKey(ActionPhase.PERFORM) && clicked.containsKey(ActionPhase.RECOVERY),
                    "Dynamic Vision (close) did not run through its perform and recovery: " + clicked);
            helper.assertTrue(clicked.get(ActionPhase.PERFORM) == 1.0F && clicked.get(ActionPhase.RECOVERY) == 1.0F,
                    "A clicked Scary Monsters action slows the user: " + clicked);
            LOGGER.info("R764_SCARY_REVERT_PASS walk_speed held={} clicked={}", held, clicked);
        }
        helper.succeed();
    }

    // 1.16 dragon was holdToFire(80, false) without standRecoveryTicks: the Stand's task ended with the perform that
    // fired, so nothing was left to hold the next press back (StandEntityAction.isCancelable needed no recovery).
    @GameTest(template = "empty", batch = "scary_dragon_no_recovery", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void firedDragonLeavesNoRecoveryAsIn116(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> firedDragonLeavesNoRecoveryAsIn116Body(h));
    }

    private static void firedDragonLeavesNoRecoveryAsIn116Body(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "DragonFire")) {
            Ability dragon = f.power.getAbility("dragon");
            helper.assertTrue(dragon instanceof EntityActionAbility, "Missing Scary Monsters ability dragon");
            EntityActionInstance action = ((EntityActionAbility) dragon)
                    .initActionOnAbilityUse(f.level, f.owner, f.stand, null);
            f.standAction.setAction(action, f.owner, SyncType.NO_SYNC);
            // the 80-tick charge has fired: the perform's tick turns the user into the dragon
            action.setPhase(ActionPhase.PERFORM, 0);
            f.standAction.tick();
            helper.assertTrue(ScaryMonstersAddon.state(f.owner).dragonForm(),
                    "fixture: the dragon perform did not run (phase " + action.getPhase() + ")");
            helper.assertTrue(action.isOver(),
                    "1.16 dragon had no recovery ticks, so its task ended with the perform; the port still runs it in "
                            + action.getPhase());
            LOGGER.info("R770_SCARY_DRAGON_NO_RECOVERY_PASS phase={}", action.getPhase());
        }
        helper.succeed();
    }

    // 1.16 InitStands :77, :80-87: dynamic_vision cooldown(40) with no standRecoveryTicks and no partsRequired; dragon holdToFire(80) with
    // no cooldown, no recovery and no partsRequired; the six dinosaur actions holdToFire(5), standRecoveryTicks(20),
    // partsRequired(ARMS) and no cooldown.
    @GameTest(template = "empty", batch = "scary_donor_timings", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void scaryTimingsAndCooldownsFollowTheDonorAsIn116(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> scaryTimingsAndCooldownsFollowTheDonorAsIn116Body(h));
    }

    private static void scaryTimingsAndCooldownsFollowTheDonorAsIn116Body(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "DonorTimes")) {
            for (String name : List.of("dinosaurify", "dinosaurify_revert", "block_to_dinosaur",
                    "revert_block_to_dinosaur", "dinosaur_form", "dinosaur_form_close", "dragon")) {
                Ability ability = f.power.getAbility(name);
                helper.assertTrue(ability != null, "Missing Scary Monsters ability " + name);
                helper.assertTrue(ability.getCooldown(f.power, 0) == 0,
                        "1.16 " + name + " had no cooldown, the port gives " + ability.getCooldown(f.power, 0));
            }
            Ability vision = f.power.getAbility("dynamic_vision");
            helper.assertTrue(vision != null && vision.getCooldown(f.power, 0) == 40,
                    "1.16 dynamic_vision had cooldown(40), the port gives "
                            + (vision != null ? vision.getCooldown(f.power, 0) : "no ability"));

            // dynamic vision: its 1-tick perform ends the task
            helper.assertTrue(f.press("dynamic_vision", InputMethod.CLICK), "dynamic_vision did not start");
            EntityActionInstance visionAction = f.standAction.getAction();
            List<ActionPhase> visionPhases = new ArrayList<>();
            for (int tick = 0; tick < 12 && !visionAction.isOver(); tick++) {
                visionPhases.add(visionAction.getPhase());
                f.standAction.tick();
            }
            helper.assertTrue(visionAction.isOver() && !visionPhases.contains(ActionPhase.RECOVERY)
                            && f.power.isAbilityOnCooldown("dynamic_vision"),
                    "1.16 dynamic_vision ended with its perform, with no recovery, its cooldown set: phases "
                            + visionPhases + ", over " + visionAction.isOver() + ", on cooldown "
                            + f.power.isAbilityOnCooldown("dynamic_vision"));
            ScaryMonstersService.disableDynamicVision(f.owner);
            f.reset();

            // the dinosaur actions keep their recovery and leave no cooldown
            helper.assertTrue(f.fireRevert(), "Revert did not start and fire");
            float revertRecovery = f.standAction.getAction().phasesLength.getFloat(ActionPhase.RECOVERY);
            helper.assertTrue(revertRecovery == 20.0F && !f.power.isAbilityOnCooldown(REVERT),
                    "1.16 dinosaurify_revert ran standRecoveryTicks(20) and set no cooldown: recovery "
                            + revertRecovery + ", on cooldown " + f.power.isAbilityOnCooldown(REVERT));
            f.reset();

            // dragon and dynamic vision need no Stand arms; the dinosaur actions do
            StandInstance instance = f.power.getStandInstance().orElseThrow();
            instance.removePart(StandInstance.StandPart.ARMS);
            try {
                Ability dragon = f.power.getAbility("dragon");
                ConditionCheck dragonCheck = dragon.checkConditions(f.power);
                helper.assertTrue(dragonCheck.isPositive(),
                        "1.16 dragon had no partsRequired: the press by a Stand without arms must pass, the port answered "
                                + dragonCheck.getWarning());
                f.power.setAbilityCooldown("dynamic_vision", 0);
                ConditionCheck visionCheck = vision.checkConditions(f.power);
                helper.assertTrue(visionCheck.isPositive(),
                        "1.16 dynamic_vision had no partsRequired (InitStands :77): the press by a Stand without arms"
                                + " must pass, the port answered " + visionCheck.getWarning());
                Ability dinosaurify = f.power.getAbility("dinosaurify");
                helper.assertTrue(!dinosaurify.checkConditions(f.power).isPositive(),
                        "1.16 dinosaurify had partsRequired(ARMS): the press by a Stand without arms must be refused");
            }
            finally {
                instance.addPart(StandInstance.StandPart.ARMS);
            }

            // the fired dragon sets no cooldown
            helper.assertTrue(f.press("dragon", InputMethod.HOLD), "dragon did not start");
            EntityActionInstance dragonAction = f.standAction.getAction();
            for (int tick = 0; tick < 100 && !dragonAction.isOver(); tick++) {
                f.standAction.tick();
            }
            helper.assertTrue(dragonAction.isOver() && ScaryMonstersAddon.state(f.owner).dragonForm()
                            && !f.power.isAbilityOnCooldown("dragon"),
                    "1.16 dragon fired after 80 held ticks and set no cooldown: over " + dragonAction.isOver()
                            + ", dragon form " + ScaryMonstersAddon.state(f.owner).dragonForm() + ", on cooldown "
                            + f.power.isAbilityOnCooldown("dragon"));
            LOGGER.info("R770_SCARY_DONOR_TIMINGS_PASS vision={}", visionPhases);
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 feet;
        private final List<OnlinePlayer> players = new ArrayList<>();
        private final List<Entity> spawned = new ArrayList<>();
        private OnlinePlayer owner;
        private StandPower power;
        private ScaryMonstersStandEntity stand;
        private LivingComponentAction standAction;
        private Cow cow;
        private EntityActionInstance walked;
        private int walkTicks;

        private Fixture(GameTestHelper helper, String name) {
            this.helper = helper;
            level = helper.getLevel();
            // GameTest keeps only the template's own chunk loaded for sure, so everything stays inside it.
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            feet = new Vec3(chunk.getMinBlockX() + 2.5D, template.getY(), chunk.getMinBlockZ() + 8.5D);
            helper.assertTrue(level.isPositionEntityTicking(BlockPos.containing(feet)),
                    "the fixture's chunk does not tick entities: " + chunk);
            try {
                owner = player(name + "A", feet);
                power = PowerClass.STAND.attachGet(owner);
                helper.assertTrue(StandPowerTransitions.insert(power,
                                new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                                == StandPowerTransitions.Status.APPLIED,
                        "Could not grant Scary Monsters");
                helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(owner, power),
                        "Production Scary Monsters summon failed");
                stand = ScaryMonstersService.activeStand(owner);
                helper.assertTrue(stand != null, "The summoned Stand is not out");
                standAction = LivingComponentAction.getComponent(stand);
                helper.assertTrue(power.getMaxStamina() >= REVERT_STAMINA && !power.isStaminaInfinite(),
                        "The test player cannot pay stamina: max=" + power.getMaxStamina());
                reset();
            }
            catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        private OnlinePlayer player(String name, Vec3 position) {
            OnlinePlayer player = ScaryOwnerDeathGameTests.onlinePlayer(helper, name, position, GameType.SURVIVAL);
            players.add(player);
            // Looking east, level.
            player.moveTo(position.x, position.y, position.z, -90.0F, 0.0F);
            player.setYHeadRot(-90.0F);
            player.setNoGravity(true);
            return player;
        }

        private void hold(ScaryDinosaurEntity dinosaur) {
            dinosaur.setNoAi(true);
            dinosaur.setNoGravity(true);
        }

        /** A cow turned by the production Dinosaurify, then put at the given spot. */
        private ScaryDinosaurEntity dinosaurifiedCow(Vec3 spot) {
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create the source creature");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(stand.position().add(1.0D, 0.0D, 0.0D));
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the source creature");
            spawned.add(cow);
            ScaryMonstersService.dinosaurifyNearest(owner);
            List<ScaryDinosaurEntity> made = level.getEntitiesOfClass(ScaryDinosaurEntity.class,
                    owner.getBoundingBox().inflate(16.0D), dinosaur -> dinosaur.getSourceEntityNbt().hasUUID("UUID")
                            && cow.getUUID().equals(dinosaur.getSourceEntityNbt().getUUID("UUID")));
            helper.assertTrue(cow.isRemoved() && made.size() == 1,
                    "Production Dinosaurify did not turn the source creature into one dinosaur");
            ScaryDinosaurEntity dinosaur = made.getFirst();
            spawned.add(dinosaur);
            hold(dinosaur);
            dinosaur.moveTo(spot);
            return dinosaur;
        }

        private UUID cowUuid() {
            return cow.getUUID();
        }

        private boolean cowIsBackAt(Vec3 spot) {
            Entity restored = level.getEntity(cow.getUUID());
            if (restored != null && restored != cow) spawned.add(restored);
            return restored instanceof Cow back && back.isAlive() && back.position().distanceToSqr(spot) < 1.0E-6D;
        }

        // The 1.16 selection terms: distance along the look vector and squared distance from it.
        private double along(Entity entity) {
            return entity.position().subtract(owner.getEyePosition()).dot(owner.getLookAngle().normalize());
        }

        private double offsetSqr(Entity entity) {
            Vec3 look = owner.getLookAngle().normalize();
            Vec3 to = entity.position().subtract(owner.getEyePosition());
            return to.subtract(look.scale(to.dot(look))).lengthSqr();
        }

        /** No action, no held key, no cooldown, full stamina and no summon lock (the Stand's own tick is not run). */
        private void reset() {
            owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            standAction.setAction(null, SyncType.NO_SYNC);
            stand.summonLockTicks = 0;
            power.setAbilityCooldown(REVERT, 0);
            power.setStamina(power.getMaxStamina());
        }

        /** The server side of a key press: the fresh condition check, then the key press itself. */
        private boolean press(String abilityName, InputMethod method) {
            Ability found = power.getAbility(abilityName);
            helper.assertTrue(found instanceof EntityActionAbility, "Missing Scary Monsters ability " + abilityName);
            AbilityConditionCheck check = power.updateAvailableMoves().getCheckedContainerFor(power, found);
            if (!AbilityInput.withConditionCheck(check, owner, method)) {
                return false;
            }
            AbilityInput.keyPress(KEY, check.ability, owner, null, method, 0.0F,
                    BufferingState.clickCanBuffer(), found.abilityId);
            return standAction.getAction() != null;
        }

        /** Holds the revert key through the 5-tick charge, which fires in its last tick, and the perform tick. */
        private boolean fireRevert() {
            if (!press(REVERT, InputMethod.HOLD)) {
                return false;
            }
            EntityActionInstance action = standAction.getAction();
            for (int tick = 0; tick < 12 && !action.isOver() && action.getPhase() != ActionPhase.RECOVERY; tick++) {
                standAction.tick();
            }
            return !action.isOver() && action.getPhase() == ActionPhase.RECOVERY;
        }

        /**
         * The walk speed factor the action carries in each phase it is in between two ticks while its key stays down.
         * Leaves the action in walked and the ticks it ran in walkTicks.
         */
        private Map<ActionPhase, Float> walkSpeeds(String abilityName, InputMethod method) {
            Map<ActionPhase, Float> speeds = new EnumMap<>(ActionPhase.class);
            helper.assertTrue(press(abilityName, method), abilityName + " did not start");
            EntityActionInstance action = standAction.getAction();
            walked = action;
            walkTicks = 0;
            for (int tick = 0; tick < 40 && !action.isOver(); tick++) {
                speeds.put(action.getPhase(), action.userWalkSpeed);
                if (action.getPhase() == ActionPhase.RECOVERY) {
                    break;
                }
                standAction.tick();
                walkTicks++;
            }
            return speeds;
        }

        @Override
        public void close() {
            if (owner != null) {
                owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            }
            if (standAction != null) {
                standAction.setAction(null, SyncType.NO_SYNC);
            }
            for (ServerPlayer player : players) {
                ScaryMonstersService.cleanupAll(player);
            }
            for (Entity entity : spawned) {
                entity.discard();
            }
            if (owner != null && power != null && power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(owner, power);
            }
            for (int index = players.size() - 1; index >= 0; index--) {
                players.get(index).close();
            }
        }
    }
}
