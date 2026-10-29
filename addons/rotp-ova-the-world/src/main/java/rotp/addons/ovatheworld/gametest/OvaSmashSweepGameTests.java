package rotp.addons.ovatheworld.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ovatheworld.OvaTheWorldAddon;
import rotp.addons.ovatheworld.ability.OvaSmashAbility;
import rotp.addons.ovatheworld.ability.OvaUltraPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.util.functions.MathUtil;

/**
 * 1.16 TheWorldOvaSmash is a StandEntityPunch with sweepingAttack(0.5, 0, 0.5, damage * 0.5), and
 * StandEntityPunch.doHit sweeps after every attempt on the main target, whatever that hit did and whatever the main
 * target is: a blocked or soaked hit and a non-living target still hurt the entities beside it.
 */
@GameTestHolder(OvaTheWorldAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OvaSmashSweepGameTests {
    private OvaSmashSweepGameTests() {}

    // The scenes stand in the middle of their test's chunk, where the frame the test runner builds around another
    // test (a barrier cage up to two blocks over the floor) may be; above it, its roof cannot stop the user's aim.
    private static final double SCENE_HEIGHT = 5.0D;

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashBoat")
    public static void smashSweepsBesideANonLivingTarget(GameTestHelper helper) {
        new SmashScene(helper, Primary.BOAT).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashShield")
    public static void smashSweepsBesideARaisedShield(GameTestHelper helper) {
        new SmashScene(helper, Primary.SHIELD).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashAbsorption")
    public static void smashSweepsWhenAbsorptionTakesTheHit(GameTestHelper helper) {
        new SmashScene(helper, Primary.ABSORPTION).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaHeavyPulses")
    public static void heavyPunchPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new StrikeScene(helper, Strike.HEAVY).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashPulses")
    public static void smashPerformsOneTickAndSweepsWithItsOwnPunch(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashRolls")
    public static void smashSweepRollsShieldAndGuardBreaks(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH_ROLLS).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashBoatRolls")
    public static void smashSweepBesideABoatRollsShieldAndGuardBreaks(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH_ROLLS_BOAT).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashReachRolls")
    public static void smashTakesItsBreakChanceFromTheReachNotTheEffectiveRange(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH_ROLLS_REACH).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashMainShieldBreaks")
    public static void smashBreaksTheMainTargetsShieldAfterAHitThatHurt(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH_SHIELD_BREAKS).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashMainShieldHolds")
    public static void smashRollsTheMainTargetsShieldWithTheTargetsRandom(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH_SHIELD_HOLDS).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaHeavyRecovery")
    public static void heavyPunchTakesTheRecoveryOfTheSpeedItEntersRecoveryWith(GameTestHelper helper) {
        new StrikeScene(helper, Strike.HEAVY_RAISED_SPEED).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaUltraStamina")
    public static void ultraPunchStartsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        new StrikeScene(helper, Strike.ULTRA_LOW_STAMINA).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaUltraMainShieldBreaks")
    public static void ultraPunchBreaksTheMainTargetsShieldAfterAHitThatHurt(GameTestHelper helper) {
        new StrikeScene(helper, Strike.ULTRA_SHIELD_BREAKS).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaUltraMainShieldHolds")
    public static void ultraPunchRollsTheMainTargetsShieldWithTheTargetsRandom(GameTestHelper helper) {
        new StrikeScene(helper, Strike.ULTRA_SHIELD_HOLDS).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaSmashRecovery")
    public static void smashTakesTheRecoveryOfTheSpeedItEntersRecoveryWith(GameTestHelper helper) {
        new StrikeScene(helper, Strike.SMASH_RAISED_SPEED).start();
    }

    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpOvaUltraRecovery")
    public static void ultraPunchPerformsOneTickWithDetachedPulsesAndTheRecoveryOfItsSpeed(GameTestHelper helper) {
        new StrikeScene(helper, Strike.ULTRA_RAISED_SPEED).start();
    }

    /**
     * 1.16 the_world_ova_barrage registered its cry with the one-argument standSound, which also played while the
     * Stand was arms-only (StandEntityAction.getPhaseStandSounds); the core Stands' cries do not.
     */
    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 40,
            batch = "rotpOvaBarrageCry")
    public static void barrageCriesWhileTheStandIsArmsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(base);
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "ova-cry")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.setNoGravity(true);
        user.moveTo(chunk.getMiddleBlockX() + 0.5D, base.getY() + SCENE_HEIGHT, chunk.getMiddleBlockZ() + 0.5D,
                0.0F, 0.0F);
        StandPower power = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the OVA The World user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(OvaTheWorldAddon.THE_WORLD_OVA.get());
            helper.assertTrue(OvaTheWorldAddon.THE_WORLD_OVA.get().summon(user, power),
                    "Could not summon OVA The World.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned OVA The World");
            stand.setArmsOnlyMode(true, false);
            helper.assertTrue(stand.isArmsOnlyMode(), "Setup: the Stand is not arms-only");
            for (String name : new String[] { OvaTheWorldAddon.BARRAGE, "grab_barrage" }) {
                Ability barrage = power.getAbility(name);
                helper.assertTrue(barrage instanceof StandEntityBarrageAbility,
                        "Setup: OVA The World has no " + name + ": " + barrage);
                helper.assertTrue(new CryProbe((StandEntityBarrageAbility) barrage).cries(level, stand),
                        "1.16 standSound(THE_WORLD_OVA_BARRAGE) plays the " + name
                                + " cry while the Stand is arms-only; the port keeps it silent");
            }
        } finally {
            if (power != null && power.isSummoned()) {
                OvaTheWorldAddon.THE_WORLD_OVA.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    /**
     * 1.16 TheWorldOvaEntity: idle withYOffset(0, 0.6, -1) and arms-only withYOffset(0, 0, 0.15), (left, up,
     * forward) from the user's feet, turned by the view yaw; 0.6 is the default y of task offsets without their own.
     */
    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 40,
            batch = "rotpOvaIdlePlacement")
    public static void idleOvaTheWorldStandsWhereTheDonorPutIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "ova-idle")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
        user.setNoGravity(true);
        user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        StandPower power = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the OVA The World user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(OvaTheWorldAddon.THE_WORLD_OVA.get());
            helper.assertTrue(OvaTheWorldAddon.THE_WORLD_OVA.get().summon(user, power),
                    "Could not summon OVA The World.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser() && !stand.isArmsOnlyMode()
                            && stand.offsetFromUser.isIdle(),
                    "Setup: OVA The World is not an idle full Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == 0.6D,
                    "1.16 OVA The World task offsets without a y took 0.6, got " + stand.Y_OFFSET);
            turnIdleUser(user, 0.0F, 50.0F);
            assertIdleAt(helper, user, stand, 0.0D, 0.6D, -1.0D, "view yaw 0, body yaw 50");
            turnIdleUser(user, 90.0F, 0.0F);
            assertIdleAt(helper, user, stand, 1.0D, 0.6D, 0.0D, "view yaw 90, body yaw 0");
            turnIdleUser(user, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertIdleAt(helper, user, stand, 0.0D, 0.0D, 0.15D, "arms-only, view yaw 0");
        } finally {
            if (power != null && power.isSummoned()) {
                OvaTheWorldAddon.THE_WORLD_OVA.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    /**
     * 1.16 StandEntityHeavyAttack.getOffsetFromUser: a full-mode Stand stands at front3dOffset, withXRot(0, front) with
     * front from 0.5 to min(2, max range), the most with no target ahead, turned by the view (lockOnTargetPosition
     * false), whatever offset TheWorldOvaSmash and TheWorldOvaUltraPunch gave their builders.
     */
    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 40,
            batch = "rotpOvaHeavyPlacement")
    public static void smashAndUltraPunchStandAtTheDonorsFrontOffset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "ova-heavy")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
        user.setNoGravity(true);
        user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        StandPower power = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the OVA The World user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(OvaTheWorldAddon.THE_WORLD_OVA.get());
            power.skipProgression();
            helper.assertTrue(OvaTheWorldAddon.THE_WORLD_OVA.get().summon(user, power),
                    "Could not summon OVA The World.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: OVA The World is not a full Stand following its user");
            for (String name : List.of(OvaTheWorldAddon.SMASH, OvaTheWorldAddon.ULTRA_PUNCH)) {
                turnIdleUser(user, 0.0F, 50.0F);
                Ability found = power.getAbility(name);
                helper.assertTrue(found instanceof StandEntityHeavyPunchAbility, "Setup: OVA The World has no " + name);
                EntityActionInstance action = ((StandEntityHeavyPunchAbility) found)
                        .initActionOnAbilityUse(level, user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == action && !action.isOver(),
                        "Setup: " + name + " did not start: " + stand.getCurStandAction());
                double front = Math.min(2.0D, stand.getMaxRange());
                assertIdleAt(helper, user, stand, 0.0D, 0.0D, front, name + " at nothing, view yaw 0, body yaw 50");
                turnIdleUser(user, 90.0F, 0.0F);
                assertIdleAt(helper, user, stand, -front, 0.0D, 0.0D, name + " at nothing, view yaw 90, body yaw 0");
                stand.setPos(user.position());
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
        } finally {
            if (power != null && power.isSummoned()) {
                OvaTheWorldAddon.THE_WORLD_OVA.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    /**
     * 1.16 StandEntityHeavyAttack.getOffsetFromUser: front3dOffset is empty for an arms-only Stand, which falls back to
     * the action's userOffsetArmsOnly, none for TheWorldOvaSmash and TheWorldOvaUltraPunch (their builders gave only the
     * full-mode offset), and so to the Stand's own arms-only offset withYOffset(0, 0, 0.15), turned by the view yaw.
     * The offset the previous action left is replaced.
     */
    @GameTest(template = "empty", templateNamespace = OvaTheWorldAddon.MOD_ID, timeoutTicks = 40,
            batch = "rotpOvaArmsOnlyHeavyPlacement")
    public static void smashAndUltraPunchStandAtTheArmsOnlyIdleOffsetWhileArmsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "ova-arms")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
        user.setNoGravity(true);
        user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        StandPower power = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the OVA The World user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(OvaTheWorldAddon.THE_WORLD_OVA.get());
            power.skipProgression();
            helper.assertTrue(OvaTheWorldAddon.THE_WORLD_OVA.get().summon(user, power),
                    "Could not summon OVA The World.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser(),
                    "Setup: OVA The World is not a Stand following its user");
            stand.setArmsOnlyMode();
            helper.assertTrue(stand.isArmsOnlyMode(), "Setup: the Stand is not arms-only");
            for (String name : List.of(OvaTheWorldAddon.SMASH, OvaTheWorldAddon.ULTRA_PUNCH)) {
                turnIdleUser(user, 90.0F, 0.0F);
                stand.offsetFromUser.setOffset(new Vec3(0.5D, 0.0D, 0.5D), StandOffsetFromUser.Rotations.HEAD);
                Ability found = power.getAbility(name);
                helper.assertTrue(found instanceof StandEntityHeavyPunchAbility, "Setup: OVA The World has no " + name);
                EntityActionInstance action = ((StandEntityHeavyPunchAbility) found)
                        .initActionOnAbilityUse(level, user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == action && !action.isOver(),
                        "Setup: " + name + " did not start: " + stand.getCurStandAction());
                assertIdleAt(helper, user, stand, -0.15D, 0.0D, 0.0D, name + " arms-only, view yaw 90, body yaw 0");
                stand.setPos(user.position());
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
        } finally {
            if (power != null && power.isSummoned()) {
                OvaTheWorldAddon.THE_WORLD_OVA.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    private static void turnIdleUser(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertIdleAt(GameTestHelper helper, Player user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put an idle OVA The World at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with "
                        + when + ", got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    // Reads the core's arms-only cry rule for a barrage ability.
    private static final class CryProbe extends StandEntityBarrageAbility.StandEntityBarrage {
        private CryProbe(StandEntityBarrageAbility ability) {
            super(ability);
        }

        private boolean cries(ServerLevel level, StandEntity stand) {
            return playsBarrageCry(level, stand);
        }
    }

    private enum Primary { BOAT, SHIELD, ABSORPTION }

    private enum Strike {
        HEAVY, HEAVY_RAISED_SPEED, SMASH, SMASH_ROLLS, SMASH_ROLLS_BOAT, SMASH_ROLLS_REACH, SMASH_SHIELD_BREAKS,
        SMASH_SHIELD_HOLDS, SMASH_RAISED_SPEED, ULTRA_LOW_STAMINA, ULTRA_SHIELD_BREAKS, ULTRA_SHIELD_HOLDS,
        ULTRA_RAISED_SPEED;

        private boolean heavy() {
            return this == HEAVY || this == HEAVY_RAISED_SPEED;
        }

        private boolean ultra() {
            return this == ULTRA_LOW_STAMINA || this == ULTRA_SHIELD_BREAKS || this == ULTRA_SHIELD_HOLDS
                    || this == ULTRA_RAISED_SPEED;
        }

        private boolean raisesSpeed() {
            return this == HEAVY_RAISED_SPEED || this == SMASH_RAISED_SPEED || this == ULTRA_RAISED_SPEED;
        }

        private String move() {
            return ultra() ? "ultra punch" : "smash";
        }

        private boolean rolls() {
            return this == SMASH_ROLLS || this == SMASH_ROLLS_BOAT || this == SMASH_ROLLS_REACH;
        }

        private boolean mainShield() {
            return this == SMASH_SHIELD_BREAKS || this == SMASH_SHIELD_HOLDS || this == ULTRA_SHIELD_BREAKS
                    || this == ULTRA_SHIELD_HOLDS;
        }

        private boolean shieldBreaks() {
            return this == SMASH_SHIELD_BREAKS || this == ULTRA_SHIELD_BREAKS;
        }

        private boolean seeded() {
            return rolls() || mainShield();
        }
    }

    /**
     * 1.16 the_world_ova_heavy_punch and the_world_ova_smash are plain StandEntityHeavyAttack builders: they perform
     * for one tick, and StandEntityPunch.doAttack pushes with the native hurt (0.4), then half the extra knockback on a
     * hit and the same half again as the tail. The smash's sweep targets go through the same doAttack with the smash's
     * punch: 10 Stand invulnerability ticks, the 3.5 extra knockback at xRot -10, and the main target's chance to
     * break a Stand's guard (onAttack) and, after a hit that hurt, a player's shield (DamageUtil.disableShield).
     * TheWorldOvaSmash.punchEntity takes that chance from any main target's box (getProximityRatio(Entity): the
     * eye-clip distance over the Stand's reach), a boat's too, and rolls the main target's shield the same way. The
     * heavy punch takes its recovery when recovery starts (StandEntityHeavyAttack.getStandRecoveryTicks).
     * TheWorldOvaUltraPunch.punchEntity goes through the same doAttack, so its main target's shield is also rolled after
     * a hit that hurt, with the target's own random.
     */
    private static final class StrikeScene {
        private static final short ATTACK = 162;
        private static final short GUARD = 163;
        // The stand's random gives 23 floats below 0.54 from this seed, under the roll chance of over 0.6 that the
        // ROLL_REACH gives these scenes.
        private static final long ROLL_SEED = 781516L;
        private static final double ROLL_REACH = 16.0D;
        // A shield bearer's own random: always under the chance, or never.
        private static final float ROLLS_UNDER = 0.0F;
        private static final float ROLLS_OVER = 0.99F;
        private static final float RAISED_SPEED = 28.0F;
        private static final float SHORT_BAR = 100.0F;

        private final GameTestHelper helper;
        private final Strike kind;
        private final ServerLevel level;
        private final List<Float> mainPulses = new ArrayList<>();
        private final List<Float> sweepPulses = new ArrayList<>();
        private final List<Float> sweepHits = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Entity> spawned = new ArrayList<>();
        private Player user;
        private StandPower power;
        private StandEntity stand;
        private Entity primary;
        private Cow bystander;
        private Player shieldBearer;
        private Player guardUser;
        private StandPower guardPower;
        private StandEntity guardStand;
        private EntityActionInstance action;
        private Throwable eventFailure;
        private float primaryAmount = Float.NaN;
        private boolean shieldUpAtHit;
        private boolean guardPressed;
        private boolean speedRaised;
        private boolean done;
        private boolean closed;
        private int ticks;
        private int beganAt;

        private StrikeScene(GameTestHelper helper, Strike kind) {
            this.helper = helper;
            this.kind = kind;
            level = helper.getLevel();
        }

        private void start() {
            try {
                setUp();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void setUp() {
            // The middle of the structure's own chunk, which the test runner keeps loaded.
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(base);
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5D, base.getY() + SCENE_HEIGHT,
                    chunk.getMiddleBlockZ() + 0.5D);
            Vec3 targetPos = origin.add(0.0D, 0.0D, 1.7D);
            user = player(origin, 0.0F, "ova-strike", null);
            power = PowerClass.STAND.attachGet(user);
            power.setStand(OvaTheWorldAddon.THE_WORLD_OVA.get());
            // The ultra punch unlocks at Resolve level 3.
            power.setResolveLevel(kind.ultra() ? 3 : 2);
            helper.assertTrue(OvaTheWorldAddon.THE_WORLD_OVA.get().summon(user, power),
                    "Could not summon OVA The World.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned OVA The World");
            prepare(stand, origin);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15.0D);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16.0D);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12.0D);
            // An effective range of 1 leaves no chance by the box distance over the effective range.
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE)
                    .setBaseValue(kind == Strike.SMASH_ROLLS_REACH ? 1.0D : 16.0D);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(16.0D);
            StandEntity.updateStandStatAttributes(stand, user);
            if (kind.seeded()) {
                stand.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(ROLL_REACH);
            }
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            if (kind == Strike.ULTRA_LOW_STAMINA) {
                helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() > SHORT_BAR,
                        "Setup: the user needs a finite stamina bar above " + SHORT_BAR);
            }

            if (kind.mainShield()) {
                // Its back to the strike, so the hit lands on it with the shield raised.
                shieldBearer = player(targetPos, 0.0F, "ova-main-shield",
                        kind.shieldBreaks() ? ROLLS_UNDER : ROLLS_OVER);
                shieldBearer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(bearerHealth());
                shieldBearer.setHealth(bearerHealth());
                shieldBearer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                shieldBearer.startUsingItem(InteractionHand.OFF_HAND);
                primary = shieldBearer;
            }
            else {
                primary = kind == Strike.SMASH_ROLLS_BOAT ? boat(targetPos) : cow(targetPos);
            }
            if (kind == Strike.SMASH) {
                bystander = cow(targetPos.add(1.0D, 0.0D, 0.0D));
            }
            else if (kind.rolls()) {
                // Inside the sweep around the boat's wider box, outside the box in which a boat pushes or seats
                // the entities beside it.
                double side = kind == Strike.SMASH_ROLLS_BOAT ? 1.3D : 1.0D;
                // Facing away from the smash, so the hit lands with the shield raised.
                shieldBearer = player(targetPos.add(side, 0.0D, 0.0D), 0.0F, "ova-shield", ROLLS_UNDER);
                shieldBearer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
                shieldBearer.setHealth(100.0F);
                shieldBearer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                shieldBearer.startUsingItem(InteractionHand.OFF_HAND);
                guardUser = player(targetPos.add(-side - 2.0D, 0.0D, 0.0D), 0.0F, "ova-guard", null);
                guardPower = PowerClass.STAND.attachGet(guardUser);
                guardPower.setStand(ModStands.STAR_PLATINUM.get());
                helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(guardUser, guardPower),
                        "Could not summon the guarding Star Platinum.");
                guardStand = guardPower.getSummonedStandEntity();
                helper.assertTrue(guardStand != null, "Setup: no guarding Star Platinum");
                // Faces the smash, beside the main target.
                prepare(guardStand, targetPos.add(-side, 0.0D, 0.0D));
                guardStand.setYRot(180.0F);
                guardStand.setYHeadRot(180.0F);
            }
            ActionTarget aim = new ActionTarget(primary);
            LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
            user.setYHeadRot(user.getYRot());
            stand.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
            stand.setYHeadRot(stand.getYRot());
            listen();
        }

        private static void prepare(StandEntity stand, Vec3 pos) {
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
        }

        private void listen() {
            Consumer<LivingKnockBackEvent> knockback = event -> {
                if (event.getEntity() == primary) {
                    mainPulses.add(event.getOriginalStrength());
                }
                else if (bystander != null && event.getEntity() == bystander) {
                    sweepPulses.add(event.getOriginalStrength());
                }
            };
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (event.getSource().getDirectEntity() != stand) {
                    return;
                }
                if (event.getEntity() == primary) {
                    primaryAmount = event.getOriginalAmount();
                    if (kind.mainShield()) {
                        shieldUpAtHit = shieldBearer.isUsingItem()
                                && !shieldBearer.getCooldowns().isOnCooldown(Items.SHIELD);
                    }
                }
                else if (bystander != null && event.getEntity() == bystander) {
                    sweepHits.add(event.getOriginalAmount());
                }
            };
            // Before the core ticks the stand's action in the same event.
            Consumer<EntityTickEvent.Post> seed = event -> {
                if (event.getEntity() == stand && kind.seeded() && !done) {
                    stand.getRandom().setSeed(ROLL_SEED);
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (event.getEntity() == stand && !closed && eventFailure == null) {
                    try {
                        afterStandTick();
                    } catch (RuntimeException | Error failure) {
                        eventFailure = failure;
                    }
                }
            };
            listeners.add(knockback);
            listeners.add(incoming);
            listeners.add(seed);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, seed);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                if (eventFailure instanceof RuntimeException failure) {
                    throw failure;
                }
                if (eventFailure instanceof Error failure) {
                    throw failure;
                }
                if (done) {
                    close();
                    helper.succeed();
                    return;
                }
                ticks++;
                if (action == null && ticks >= 3 && level.isPositionEntityTicking(stand.blockPosition())
                        && level.isPositionEntityTicking(primary.blockPosition())
                        && (!kind.rolls() || ready())
                        && (!kind.mainShield() || shieldBearer.isBlocking())) {
                    begin();
                }
                else if (kind.raisesSpeed() && action != null && !speedRaised
                        && ticks > beganAt + 1 && action.getPhase() == ActionPhase.WINDUP) {
                    raiseSpeed();
                }
                helper.assertTrue(ticks < 100, action == null
                        ? "Setup: the fixture never became ready for the " + kind + " strike"
                        : "The " + kind + " strike did not land, phase " + action.getPhase());
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private boolean ready() {
            if (!level.isPositionEntityTicking(shieldBearer.blockPosition())
                    || !level.isPositionEntityTicking(guardStand.blockPosition())) {
                return false;
            }
            if (!guardPressed) {
                guardPressed = true;
                holdGuard();
            }
            return guardStand.isStandBlocking();
        }

        // The guard as a user holds it.
        private void holdGuard() {
            Ability ability = guardPower.getAbility("guard");
            helper.assertTrue(ability != null, "Setup: Star Platinum has no guard");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(GUARD, ability, guardUser, null, InputMethod.HOLD, 0.0F, buffer,
                    ability.getAbilityId());
        }

        // A stand of the raised speed takes a shorter recovery, if the heavy punch samples it when it enters recovery.
        private void raiseSpeed() {
            speedRaised = true;
            helper.assertTrue(near(action.phasesLength.getFloat(ActionPhase.RECOVERY), heavyRecovery(16.0F, finisher())),
                    "Setup: the " + kind + " did not store the recovery of speed 16: "
                            + action.phasesLength.getFloat(ActionPhase.RECOVERY));
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(RAISED_SPEED);
            StandEntity.updateStandStatAttributes(stand, user);
            helper.assertTrue(near(stand.getAttackSpeed(), RAISED_SPEED),
                    "Setup: the stand speed did not rise to " + RAISED_SPEED + ": " + stand.getAttackSpeed());
        }

        private void begin() {
            beganAt = ticks;
            if (kind.ultra()) {
                beginUltra();
                return;
            }
            stand.setFinisherMeter(kind.heavy() ? 0.0F : 0.75F);
            Ability heavy = power.getAbility(OvaTheWorldAddon.HEAVY_PUNCH);
            helper.assertTrue(heavy != null, "Setup: OVA The World has no heavy punch");
            var selected = power.updateAvailableMoves().getContextVariationContainer(heavy);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "Setup: the heavy key was refused");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(ATTACK, selected.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    heavy.getAbilityId());
            action = stand.getCurStandAction();
            helper.assertTrue(buffer.isActionSuccess && action != null
                            && (kind.heavy()
                                    ? action.ability == power.getAbility(OvaTheWorldAddon.HEAVY_PUNCH)
                                    : action.ability instanceof OvaSmashAbility),
                    "Setup: the heavy key did not start the " + kind + ": "
                            + (action != null ? action.ability : null));
            helper.assertTrue(near(action.phasesLength.getFloat(ActionPhase.PERFORM), 1.0F),
                    "1.16 OVA's " + kind + " performs for the builder's one tick; the port stores PERFORM "
                            + action.phasesLength.getFloat(ActionPhase.PERFORM));
        }

        // 1.16 has no stamina condition on the ultra punch: its staminaCost(350) is paid when it performs, with the
        // result ignored. The user of the low-stamina scene holds the special key with a third of the cost on the bar.
        private void beginUltra() {
            if (kind == Strike.ULTRA_LOW_STAMINA) {
                power.setStamina(SHORT_BAR);
            }
            Ability ultra = power.getAbility(OvaTheWorldAddon.ULTRA_PUNCH);
            helper.assertTrue(ultra != null, "Setup: OVA The World has no ultra punch");
            var selected = power.updateAvailableMoves().getContextVariationContainer(ultra);
            boolean accepted = selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD);
            helper.assertTrue(accepted,
                    "1.16 starts the ultra punch at any stamina; the port refused it with " + power.getStamina()
                            + " of " + OvaUltraPunchAbility.LEGACY_STAMINA + " on the bar");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(ATTACK, selected.ability, user, null, InputMethod.HOLD, 0.0F, buffer,
                    ultra.getAbilityId());
            action = stand.getCurStandAction();
            helper.assertTrue(buffer.isActionSuccess && action != null
                            && action.ability instanceof OvaUltraPunchAbility,
                    "1.16 starts the ultra punch at any stamina; the held key with " + power.getStamina()
                            + " on the bar started " + (action != null ? action.ability : null));
            if (kind == Strike.ULTRA_RAISED_SPEED) {
                helper.assertTrue(near(action.phasesLength.getFloat(ActionPhase.PERFORM), 1.0F),
                        "1.16 TheWorldOvaUltraPunch performs for the builder's one tick; the port stores PERFORM "
                                + action.phasesLength.getFloat(ActionPhase.PERFORM));
            }
        }

        private void afterStandTick() {
            if (done || action == null || action.punchedTarget == null) {
                return;
            }
            done = true;
            helper.assertTrue(action.punchedTarget.getMainEntity() == primary,
                    "Setup: the " + kind + " did not strike the main target");
            helper.assertTrue(primaryAmount > 0.0F, "Setup: the " + kind + " did not hurt the main target");
            if (kind == Strike.ULTRA_LOW_STAMINA) {
                // 1.16 StandPower.consumeStamina drains a bar that cannot pay to 0.
                helper.assertTrue(power.getStamina() == 0.0F,
                        "1.16 pays the ultra punch's 350 from a short bar by draining it to 0; the bar holds "
                                + power.getStamina());
                return;
            }
            float half = kind.heavy()
                    ? 0.5F * (0.5F + 15.0F / 8.0F)
                    : kind.ultra()
                            ? 0.5F * 14.0F * Mth.cos(-2.0F * Mth.DEG_TO_RAD)
                            : 0.5F * 3.5F * Mth.cos(-10.0F * Mth.DEG_TO_RAD);
            // The other ultra punch scenes check only its stamina and its shield roll.
            if (!kind.ultra() || kind == Strike.ULTRA_RAISED_SPEED) {
                helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
                        "1.16: a one-tick perform ends on the impact tick, but the " + kind + " is still in "
                                + action.getPhase() + " after it");
                // A boat takes no LivingKnockBackEvent.
                helper.assertTrue(kind == Strike.SMASH_ROLLS_BOAT || pulses(mainPulses, half),
                        "1.16 StandEntityPunch pushes the " + kind + "'s main target with the native 0.4, then "
                                + half + " on the hit and " + half + " as the tail; the port pushed " + mainPulses);
            }
            if (kind.raisesSpeed()) {
                float expected = heavyRecovery(RAISED_SPEED, finisher());
                helper.assertTrue(speedRaised && near(action.phasesLength.getFloat(ActionPhase.RECOVERY), expected),
                        "1.16 StandEntityHeavyAttack takes the recovery of the speed it enters recovery with, "
                                + expected + "; the port keeps " + action.phasesLength.getFloat(ActionPhase.RECOVERY)
                                + " (speed raised in the windup: " + speedRaised + ")");
            }
            if (kind == Strike.SMASH) {
                helper.assertTrue(sweepHits.size() == 1 && near(sweepHits.get(0), primaryAmount * 0.5F),
                        "Setup: the smash did not sweep the cow beside once for half the " + primaryAmount + ": "
                                + sweepHits);
                helper.assertTrue(pulses(sweepPulses, half),
                        "1.16 doAttack pushes a sweep target as the main one: the native 0.4, then " + half
                                + " on the hit and " + half + " as the tail; the port pushed " + sweepPulses);
                helper.assertTrue(StandEntity.isStandAttackInvulnerable(bystander),
                        "1.16 doAttack gives a sweep target the smash's 10 Stand invulnerability ticks; the cow"
                                + " beside has none");
            }
            else if (kind.mainShield()) {
                float chance = chance(stand, primary);
                helper.assertTrue(chance > 0.6F, "Setup: the " + kind.move() + "'s break chance is only " + chance);
                helper.assertTrue(shieldBearer.getHealth() < bearerHealth(),
                        "Setup: the " + kind.move() + " did not hurt the shield bearer");
                if (kind.shieldBreaks()) {
                    helper.assertTrue(shieldUpAtHit && shieldBearer.getCooldowns().isOnCooldown(Items.SHIELD)
                                    && !shieldBearer.isUsingItem(),
                            "1.16 DamageUtil.disableShield breaks the main target's shield after a hit that hurt; the"
                                    + " shield was " + (shieldUpAtHit ? "up" : "already down") + " when the hit landed"
                                    + " and is " + (shieldBearer.isUsingItem() ? "still up" : "down") + " after it");
                }
                else {
                    helper.assertTrue(!shieldBearer.getCooldowns().isOnCooldown(Items.SHIELD)
                                    && shieldBearer.isUsingItem(),
                            "1.16 DamageUtil.disableShield rolls the " + chance + " chance with the main target's own"
                                    + " random, which never falls under it here; the port broke the shield");
                }
            }
            else if (kind.rolls()) {
                float chance = chance(stand, primary);
                helper.assertTrue(chance > 0.6F, "Setup: the smash's break chance is only " + chance);
                helper.assertTrue(kind != Strike.SMASH_ROLLS_REACH
                                || 1.0D - MathUtil.getAABBDistance(stand.getBoundingBox(), primary.getBoundingBox())
                                        / stand.getEffectiveRange() - 0.25D <= 0.0D,
                        "Setup: the box distance over the effective range also gives the smash a chance");
                String beside = kind == Strike.SMASH_ROLLS_BOAT ? " beside the boat" : " beside";
                helper.assertTrue(shieldBearer.getHealth() < 100.0F,
                        "Setup: the smash did not hurt the shield bearer" + beside);
                helper.assertTrue(shieldBearer.getCooldowns().isOnCooldown(Items.SHIELD)
                                && !shieldBearer.isUsingItem(),
                        "1.16 DamageUtil.disableShield rolls the smash's chance on a sweep target it hurt; the"
                                + " shield of the player" + beside + " is still up");
                helper.assertTrue(!guardStand.isStandBlocking() && !guardStand.canStartBlocking(),
                        "1.16 onAttack rolls the smash's chance to break the guard of a Stand" + beside
                                + "; its guard held");
            }
        }

        // The ultra punch hits much harder than the smash, and the main shield bearer has to live through it.
        private float bearerHealth() {
            return kind.ultra() ? 1000.0F : 100.0F;
        }

        // The recovery of a heavy punch, as StandStatFormulas.getHeavyAttackRecovery.
        private static float heavyRecovery(float speed, float finisher) {
            float max = (40.0F - speed * 1.25F) * 0.75F;
            return (float) Math.floor(max + finisher * (max / 2.0F - max));
        }

        // The finisher snapshot the attack took when it started.
        private float finisher() {
            return ((StandEntityHeavyPunchAbility.StandEntityHeavyPunch) action).finisherValue;
        }

        private static boolean pulses(List<Float> pulses, float half) {
            return pulses.size() == 3 && near(pulses.get(0), 0.4F) && near(pulses.get(1), half)
                    && near(pulses.get(2), half);
        }

        private Player player(Vec3 position, float yaw, String name, @Nullable Float fixedRoll) {
            RandomSource fixed = new LegacyRandomSource(0L) {
                @Override
                public float nextFloat() {
                    return fixedRoll;
                }
            };
            Player player = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), name)) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }

                @Override
                public RandomSource getRandom() {
                    return fixedRoll != null ? fixed : super.getRandom();
                }
            };
            player.setNoGravity(true);
            player.moveTo(position.x, position.y, position.z, yaw, 0.0F);
            player.setYHeadRot(yaw);
            helper.assertTrue(level.addFreshEntity(player), "Setup: a player was not added");
            spawned.add(player);
            return player;
        }

        private Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            // The ultra punch hits much harder, and the cow has to live through it.
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.ultra() ? 1000.0D : 200.0D);
            cow.setHealth(kind.ultra() ? 1000.0F : 200.0F);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
            cow.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: a cow was not added");
            spawned.add(cow);
            return cow;
        }

        private Boat boat(Vec3 position) {
            Boat boat = new Boat(EntityType.BOAT, level) {
                @Override
                public boolean hurt(DamageSource source, float amount) {
                    if (source.getDirectEntity() == stand) {
                        primaryAmount = amount;
                    }
                    return super.hurt(source, amount);
                }
            };
            boat.setNoGravity(true);
            boat.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(boat), "Setup: the boat was not added");
            spawned.add(boat);
            return boat;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Object listener : listeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            listeners.clear();
            if (user != null) {
                AbilityInput.keyRelease(ATTACK, user);
            }
            if (guardUser != null) {
                AbilityInput.keyRelease(GUARD, guardUser);
            }
            for (StandEntity summoned : new StandEntity[] { stand, guardStand }) {
                if (summoned != null) {
                    LivingComponentAction.getComponent(summoned).setAction(null, SyncType.NO_SYNC);
                }
            }
            if (power != null && power.isSummoned()) {
                OvaTheWorldAddon.THE_WORLD_OVA.get().forceUnsummon(user, power);
            }
            if (guardPower != null && guardPower.isSummoned()) {
                ModStands.STAR_PLATINUM.get().forceUnsummon(guardUser, guardPower);
            }
            for (StandEntity summoned : new StandEntity[] { stand, guardStand }) {
                if (summoned != null && !summoned.isRemoved()) {
                    summoned.discard();
                }
            }
            for (Entity entity : spawned) {
                entity.discard();
            }
        }
    }

    private static final class SmashScene {
        private static final short ATTACK = 161;

        private final GameTestHelper helper;
        private final Primary kind;
        private final ServerLevel level;
        private final List<Float> sweepHits = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Entity> spawned = new ArrayList<>();
        private Player user;
        private StandPower power;
        private StandEntity stand;
        private Entity primary;
        private Cow bystander;
        private EntityActionInstance action;
        private Throwable eventFailure;
        private float primaryAmount = Float.NaN;
        private boolean done;
        private boolean closed;
        private int ticks;

        private SmashScene(GameTestHelper helper, Primary kind) {
            this.helper = helper;
            this.kind = kind;
            level = helper.getLevel();
        }

        private void start() {
            try {
                setUp();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void setUp() {
            // The middle of the structure's own chunk, which the test runner keeps loaded.
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(base);
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5D, base.getY() + SCENE_HEIGHT,
                    chunk.getMiddleBlockZ() + 0.5D);
            Vec3 targetPos = origin.add(0.0D, 0.0D, 1.7D);
            user = player(origin, 0.0F);
            power = PowerClass.STAND.attachGet(user);
            power.setStand(OvaTheWorldAddon.THE_WORLD_OVA.get());
            power.setResolveLevel(2);
            helper.assertTrue(OvaTheWorldAddon.THE_WORLD_OVA.get().summon(user, power),
                    "Could not summon OVA The World.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned OVA The World");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15.0D);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16.0D);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12.0D);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(1.0D);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8.0D);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;

            switch (kind) {
                case BOAT -> {
                    primary = new Boat(EntityType.BOAT, level) {
                        @Override
                        public boolean hurt(DamageSource source, float amount) {
                            if (source.getDirectEntity() == stand) {
                                primaryAmount = amount;
                            }
                            return super.hurt(source, amount);
                        }
                    };
                    primary.setNoGravity(true);
                    primary.moveTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
                    helper.assertTrue(level.addFreshEntity(primary), "Setup: the boat was not added");
                    spawned.add(primary);
                }
                case SHIELD -> {
                    Player blocker = player(targetPos, 180.0F);
                    blocker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
                    blocker.setHealth(100.0F);
                    blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                    blocker.startUsingItem(InteractionHand.OFF_HAND);
                    primary = blocker;
                }
                case ABSORPTION -> {
                    Cow soaked = cow(targetPos);
                    soaked.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100.0D);
                    soaked.setAbsorptionAmount(100.0F);
                    primary = soaked;
                }
            }
            bystander = cow(targetPos.add(1.0D, 0.0D, 0.0D));
            ActionTarget aim = new ActionTarget(primary);
            LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
            user.setYHeadRot(user.getYRot());
            stand.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
            stand.setYHeadRot(stand.getYRot());
            listen();
        }

        private void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (event.getSource().getDirectEntity() != stand) {
                    return;
                }
                if (event.getEntity() == bystander) {
                    sweepHits.add(event.getOriginalAmount());
                }
                else if (event.getEntity() == primary) {
                    primaryAmount = event.getOriginalAmount();
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (event.getEntity() == stand && !closed && eventFailure == null) {
                    try {
                        afterStandTick();
                    } catch (RuntimeException | Error failure) {
                        eventFailure = failure;
                    }
                }
            };
            listeners.add(incoming);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                if (eventFailure instanceof RuntimeException failure) {
                    throw failure;
                }
                if (eventFailure instanceof Error failure) {
                    throw failure;
                }
                if (done) {
                    close();
                    helper.succeed();
                    return;
                }
                ticks++;
                if (action == null && ticks >= 3 && level.isPositionEntityTicking(stand.blockPosition())
                        && level.isPositionEntityTicking(primary.blockPosition())
                        && level.isPositionEntityTicking(bystander.blockPosition())
                        && (kind != Primary.SHIELD || ((Player) primary).isBlocking())) {
                    begin();
                }
                helper.assertTrue(ticks < 100, action == null
                        ? "Setup: the fixture never became ready for the " + kind + " smash"
                        : "The " + kind + " smash did not strike, phase " + action.getPhase());
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void begin() {
            stand.setFinisherMeter(0.75F);
            Ability heavy = power.getAbility(OvaTheWorldAddon.HEAVY_PUNCH);
            helper.assertTrue(heavy != null, "Setup: OVA The World has no heavy punch");
            var selected = power.updateAvailableMoves().getContextVariationContainer(heavy);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "Setup: the heavy key was refused");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(ATTACK, selected.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    heavy.getAbilityId());
            action = stand.getCurStandAction();
            helper.assertTrue(buffer.isActionSuccess && action != null
                            && action.ability instanceof OvaSmashAbility,
                    "Setup: the finisher heavy key did not start the smash: "
                            + (action != null ? action.ability : null));
        }

        private void afterStandTick() {
            if (done || action == null || action.punchedTarget == null) {
                return;
            }
            done = true;
            helper.assertTrue(action.punchedTarget.getMainEntity() == primary,
                    "Setup: the smash did not strike the " + kind + " main target");
            helper.assertTrue(primaryAmount > 0.0F, "Setup: the smash did not attempt the " + kind + " main target");
            switch (kind) {
                case SHIELD -> {
                    Player blocker = (Player) primary;
                    helper.assertTrue(blocker.isBlocking() && blocker.getHealth() == 100.0F
                                    && !blocker.getCooldowns().isOnCooldown(Items.SHIELD),
                            "Setup: the raised shield did not block the smash (health " + blocker.getHealth()
                                    + ", blocking " + blocker.isBlocking() + ")");
                    float chance = chance(stand, blocker);
                    helper.assertTrue(chance > 0.0F,
                            "Setup: the smash had no chance to break the shield: " + chance);
                    helper.assertTrue(blocker.isUsingItem(),
                            "1.16 rolls the main target's shield only after a hit that hurt; the blocked smash, with"
                                    + " a chance of " + chance + ", took the shield down");
                }
                case ABSORPTION -> helper.assertTrue(((LivingEntity) primary).getHealth() == 200.0F
                                && ((LivingEntity) primary).getAbsorptionAmount() < 100.0F,
                        "Setup: absorption did not take the smash");
                case BOAT -> {}
            }
            helper.assertTrue(sweepHits.size() == 1 && near(sweepHits.get(0), primaryAmount * 0.5F),
                    "1.16 TheWorldOvaSmash sweeps the cow beside the " + kind + " main target once for half the "
                            + primaryAmount + "; the port hit it " + sweepHits);
        }

        private Player player(Vec3 position, float yaw) {
            Player player = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "ova-smash")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            player.setNoGravity(true);
            player.moveTo(position.x, position.y, position.z, yaw, 0.0F);
            player.setYHeadRot(yaw);
            helper.assertTrue(level.addFreshEntity(player), "Setup: a player was not added");
            spawned.add(player);
            return player;
        }

        private Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            cow.setHealth(200.0F);
            cow.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: a cow was not added");
            spawned.add(cow);
            return cow;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Object listener : listeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            listeners.clear();
            if (user != null) {
                AbilityInput.keyRelease(ATTACK, user);
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                }
                if (power != null && power.isSummoned()) {
                    OvaTheWorldAddon.THE_WORLD_OVA.get().forceUnsummon(user, power);
                }
                if (stand != null && !stand.isRemoved()) {
                    stand.discard();
                }
            }
            for (Entity entity : spawned) {
                entity.discard();
            }
        }
    }

    // 1.16 TheWorldOvaSmash: getProximityRatio(target) - 0.25, the eye-clip distance over the Stand's reach.
    private static float chance(StandEntity stand, Entity target) {
        return (float) (1.0D - ActionTargetRange.getDistance(stand, target.getBoundingBox())
                / stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)) - 0.25F;
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.001D;
    }
}
