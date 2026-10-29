package rotp.addons.tusk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.tusk.ability.TuskChargedNailShotAbility;
import rotp.addons.tusk.ability.TuskNailShotAbility;
import rotp.addons.tusk.entity.NailEntity;
import rotp.addons.tusk.entity.WormholeArmEntity;
import rotp.addons.tusk.entity.WormholeEntity;
import rotp.addons.tusk.entity.WormholePortalEntity;
import rotp.addons.tusk.state.TuskNailState;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

// Template and enabled namespace are rotp_t (runTuskGameTest). One batch per test: the shots must not meet.
@GameTestHolder(TuskAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TuskNailShotGameTests {
    private static final String NAIL_SHOT = "tusk_nail_shot";
    private static final String CHARGED_NAIL_SHOT = "tusk_charged_nail_shot";
    private static final String NAIL_SCRATCH = "tusk_nail_scratch";
    private static final String WORMHOLE = "wormhole";
    private static final short SHOT_KEY = 91;
    // The work of a test takes DEADLINE_TICK ticks from the moment its chunks tick; the wait before has its own budget.
    private static final int TIMEOUT_TICKS = 600 + GameTestChunkTickets.WAIT_TICKS;
    private static final int DEADLINE_TICK = 560;
    private static final int CHARGE_TICKS = 10;
    private static final int STRAIGHT_FLIGHT_TICKS = 20;
    // Projectile.getMovementToShoot spreads the unit aim by up to 0.0172275 per axis: under 1.8 degrees and 3% of speed.
    private static final double AIM_DOT = Math.cos(Math.toRadians(2.0D));
    private static final Vec3 ARM_MOTION = new Vec3(1.2D, 0.7D, 1.6D);

    private TuskNailShotGameTests() {}

    @GameTest(template = "empty", batch = "tusk_nail_free", timeoutTicks = TIMEOUT_TICKS)
    public static void normalNailShotStartsAtZeroStaminaAndDoesNotDrain(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskFreeShot", 32);
        EntityActionInstance[] held = {null};
        fixture.drive(() -> {
            if (held[0] == null) {
                fixture.power.setStamina(0.0F);
                helper.assertTrue(fixture.ability(NAIL_SHOT).checkSpecificConditions(fixture.power).isPositive(),
                        "The normal nail shot refuses a user at zero stamina.");
                held[0] = fixture.hold(NAIL_SHOT);
                fixture.power.setStamina(50.0F);
                fixture.stage = "checking free nail-shot ticks";
            }
            helper.assertTrue(fixture.isHolding(held[0]), "The free nail shot stopped early.");
            float before = fixture.power.getStamina();
            float regen = fixture.power.getStaminaTickGain();
            helper.assertTrue(!fixture.power.isStaminaInfinite() && fixture.power.usesStamina()
                            && Float.isFinite(regen) && regen >= 0.0F
                            && fixture.power.consumeStamina(regen, true),
                    "The fixture cannot cancel its passive stamina regeneration.");
            held[0].actionTick();
            // Ticking payments are deferred: settle them through the production tick, with regen cancelled.
            fixture.power.tick();
            helper.assertTrue(!fixture.power.isPausedInStoppedTime(),
                    "Stopped time skipped the production stamina settlement tick.");
            helper.assertTrue(fixture.power.getStamina() == before,
                    "A normal nail-shot tick consumed stamina after queued-cost settlement: " + before + " -> "
                            + fixture.power.getStamina());
            if (held[0].getPhaseTick() >= 6) {
                helper.assertTrue(!fixture.nails().isEmpty(), "The free shot spawned no nail.");
                fixture.pass();
            }
        });
    }

    // 1.16 PowerBaseImpl.tickHeldAction counts the held tick before TuskNailShot.holdTick tests ticksHeld % 5.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_cadence",
            timeoutTicks = TIMEOUT_TICKS)
    public static void firstNailLeavesOnTheFifthHeldTick(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskCadence", 32);
        EntityActionInstance[] held = {null};
        fixture.drive(() -> {
            if (held[0] == null) {
                held[0] = fixture.hold(NAIL_SHOT);
                fixture.stage = "counting the nails of a held shot";
            }
            EntityActionInstance action = held[0];
            int heldTicks = Mth.floor(action.getPhaseTick());
            int fired = TuskNailState.MAX_NAILS
                    - TuskAddon.nailState(fixture.owner).nails();
            helper.assertTrue(
                    fixture.isHolding(action),
                    "The held nail shot ended after " + heldTicks
                            + " held tick(s).");
            helper.assertTrue(
                    fired == heldTicks
                            / TuskNailShotAbility.SHOT_INTERVAL_TICKS,
                    fired + " nail(s) had left after " + heldTicks
                            + " held tick(s); 1.16 fires on held ticks "
                            + "5, 10 and 15.");
            if (heldTicks >= 12) {
                fixture.pass();
            }
        });
    }

    // 1.16 (056 mixin/PlayerModelMixin.rotateArmsForNailShot :50-72) raised the nail arm while the Stand power held the
    // shot. The port draws tusk_player from the user's Stand-power slot, the pose slot while its own slot is empty.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_player_clip",
            timeoutTicks = TIMEOUT_TICKS)
    public static void nailShotAloneDrawsItsPlayerClipAsIn116(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskPlayerClip", 32);
        EntityActionInstance[] held = {null};
        fixture.drive(() -> {
            ServerPlayer owner = fixture.owner;
            if (held[0] == null) {
                held[0] = fixture.hold(NAIL_SHOT);
                fixture.stage = "reading the pose of a held nail shot";
            }
            EntityActionInstance action = held[0];
            helper.assertTrue(
                    fixture.isHolding(action)
                            && LivingComponentAction.getCurEntityAction(owner) == null,
                    "The held nail shot is not alone in its user's Stand-power slot: own slot "
                            + LivingComponentAction.getCurEntityAction(owner));
            LivingComponentAction pose = LivingComponentAction.getPoseComponent(owner);
            helper.assertTrue(
                    pose == LivingComponentAction.getComponent(owner, PowerClass.STAND)
                            && pose.getAction() == action
                            && action.getPerformer() == owner,
                    "The user's pose does not come from the held nail shot: "
                            + (pose != null ? pose.getAction() : null));
            ResourceLocation animSet = action.ability.getEntityAnimSet(owner);
            ActionAnimIdentifier anim = action.getEntityAnim();
            ActionAnimIdentifier expected = ActionAnimIdentifier.getOrCreate(
                    owner.getMainArm() == HumanoidArm.LEFT
                            ? "tusk_nail_shot_left"
                            : "tusk_nail_shot_right",
                    false);
            helper.assertTrue(
                    ResourceLocation.fromNamespaceAndPath(
                                    "jojo_ripples", "addons/rotp_t/tusk_player")
                                    .equals(animSet)
                            && expected.equals(anim),
                    "The held nail shot draws " + animSet + " " + anim
                            + "; 1.16 raised the nail arm (" + expected + ").");
            if (Mth.floor(action.getPhaseTick()) >= 6) {
                fixture.pass();
            }
        });
    }

    // 1.16 TuskNailShot.checkSpecificConditions: "... || Creative" passes whatever the hands hold.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_creative",
            timeoutTicks = TIMEOUT_TICKS)
    public static void creativeUserFiresWithAnItemInTheNailHand(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskCreative", 32);
        EntityActionInstance[] held = {null};
        int[] setupTick = {0};
        fixture.drive(() -> {
            ServerPlayer owner = fixture.owner;
            if (held[0] == null) {
                // The power answers one preflight per tick from its cache, so each change waits for the next tick.
                int tick = setupTick[0]++;
                if (tick == 0) {
                    // 10 nails: the main hand is the nail hand.
                    owner.setItemInHand(
                            InteractionHand.MAIN_HAND,
                            new ItemStack(Items.STICK));
                    return;
                }
                if (tick == 1) {
                    helper.assertTrue(
                            !fixture.preflight(NAIL_SHOT).isPositive(),
                            "A Survival user may fire with an item in the "
                                    + "nail hand.");
                    owner.setGameMode(GameType.CREATIVE);
                    return;
                }
                ConditionCheck creative = fixture.preflight(NAIL_SHOT);
                helper.assertTrue(
                        creative.isPositive(),
                        "A Creative user holding an item in the nail hand "
                                + "was refused (" + describe(creative)
                                + "); 1.16 lets Creative skip the hand "
                                + "rule.");
                held[0] = fixture.hold(NAIL_SHOT);
                fixture.stage = "holding a Creative user's nail shot";
                return;
            }
            EntityActionInstance action = held[0];
            int heldTicks = Mth.floor(action.getPhaseTick());
            helper.assertTrue(
                    fixture.isHolding(action),
                    "A Creative user's held shot stopped after "
                            + heldTicks + " held tick(s) with an item in "
                            + "the nail hand.");
            if (heldTicks >= 6) {
                helper.assertTrue(
                        !fixture.nails().isEmpty(),
                        "A Creative user's held shot fired no nail in "
                                + heldTicks + " held ticks.");
                helper.assertTrue(
                        TuskAddon.nailState(owner).nails()
                                == TuskNailState.MAX_NAILS
                                && owner.getMainHandItem().is(Items.STICK),
                        "A Creative shot spent a nail or the held item.");
                fixture.pass();
            }
        });
    }

    // 1.16 TuskNailShot.checkSpecificConditions admits Creative whatever the nail count; holdTick shoots only with nails left.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_creative_empty",
            timeoutTicks = TIMEOUT_TICKS)
    public static void creativeUserHoldsTheShotWithNoNailsLeft(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskCreativeEmpty", 32);
        EntityActionInstance[] held = {null};
        int[] setupTick = {0};
        boolean[] refilled = {false};
        fixture.drive(() -> {
            ServerPlayer owner = fixture.owner;
            if (held[0] == null) {
                int tick = setupTick[0]++;
                if (tick == 0) {
                    TuskAddon.nailState(owner).applySnapshot(0, 0, false);
                    return;
                }
                if (tick == 1) {
                    helper.assertTrue(
                            !fixture.preflight(NAIL_SHOT).isPositive(),
                            "A Survival user with no nails was admitted.");
                    owner.setGameMode(GameType.CREATIVE);
                    return;
                }
                ConditionCheck creative = fixture.preflight(NAIL_SHOT);
                helper.assertTrue(
                        creative.isPositive(),
                        "A Creative user with no nails was refused ("
                                + describe(creative) + "); 1.16 admits "
                                + "Creative whatever the nail count.");
                held[0] = fixture.hold(NAIL_SHOT);
                fixture.stage = "holding a Creative user's shot at 0 nails";
                return;
            }
            EntityActionInstance action = held[0];
            int heldTicks = Mth.floor(action.getPhaseTick());
            helper.assertTrue(
                    fixture.isHolding(action),
                    "A Creative user's hold ended after " + heldTicks
                            + " held tick(s) at 0 nails; 1.16 keeps it.");
            if (!refilled[0]) {
                helper.assertTrue(
                        fixture.nails().isEmpty()
                                && TuskAddon.nailState(owner).nails() == 0,
                        "The hold at 0 nails shot a nail.");
                if (heldTicks >= 12) {
                    TuskAddon.nailState(owner).applySnapshot(
                            1, TuskNailState.REGEN_TICKS, false);
                    refilled[0] = true;
                    fixture.stage = "waiting for the nail of a refilled hold";
                }
                return;
            }
            if (!fixture.nails().isEmpty()) {
                helper.assertTrue(
                        TuskAddon.nailState(owner).nails() == 1,
                        "A Creative shot spent a nail.");
                fixture.pass();
            }
        });
    }

    // 1.16 NailEntity never overrides constVelocity(): its 1.5 gravity never applies.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_straight",
            timeoutTicks = TIMEOUT_TICKS)
    public static void nailFliesStraightAtItsLaunchVelocity(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskStraight", 72);
        Flight flight = new Flight();
        fixture.drive(() -> {
            if (flight.nail == null) {
                flight.launch(fixture, NAIL_SHOT);
                return;
            }
            NailEntity nail = flight.nail;
            int age = ++flight.ticks;
            helper.assertTrue(
                    !nail.isRemoved() && nail.tickCount == age,
                    "The nail was lost in open air: removed="
                            + nail.isRemoved() + " after " + age
                            + " tick(s), its own age " + nail.tickCount
                            + ", at " + nail.position() + ".");
            Vec3 expected = flight.start.add(flight.velocity.scale(age));
            double drift = nail.position().distanceTo(expected);
            helper.assertTrue(
                    drift < 1.0E-6D
                            && nail.getDeltaMovement()
                                    .distanceTo(flight.velocity) < 1.0E-9D,
                    "The nail left its straight line after " + age
                            + " tick(s): " + drift + " blocks off, velocity "
                            + nail.getDeltaMovement() + " instead of "
                            + flight.velocity + "; the 1.16 nail keeps its "
                            + "launch velocity.");
            if (age >= STRAIGHT_FLIGHT_TICKS) {
                fixture.pass();
            }
        });
    }

    // 1.16 ModdedProjectileEntity.onHitBlock always ends in breakProjectile, which removes the nail.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_soft_block",
            timeoutTicks = TIMEOUT_TICKS)
    public static void nailIsRemovedByItsFirstSoftBlock(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskSoftBlock", 32);
        BlockPos near = fixture.feet.offset(0, 1, 4);
        BlockPos far = fixture.feet.offset(0, 1, 9);
        Flight flight = new Flight();
        fixture.drive(() -> {
            ServerLevel level = fixture.level;
            if (flight.nail == null) {
                fixture.setBlock(near, Blocks.GLASS.defaultBlockState());
                fixture.setBlock(far, Blocks.GLASS.defaultBlockState());
                flight.launch(fixture, NAIL_SHOT);
                return;
            }
            NailEntity nail = flight.nail;
            if (flight.ticksAfterContact < 0) {
                if (!level.getBlockState(near).isAir()) {
                    helper.assertTrue(
                            !nail.isRemoved(),
                            "The nail was removed before it reached the "
                                    + "glass.");
                    return;
                }
                helper.assertTrue(
                        nail.isRemoved(),
                        "The nail survived breaking a soft block: still "
                                + "flying at " + nail.position()
                                + " with velocity "
                                + nail.getDeltaMovement() + "; the 1.16 "
                                + "nail is removed on its first block "
                                + "contact.");
                flight.ticksAfterContact = 0;
            }
            helper.assertTrue(
                    level.getBlockState(far).is(Blocks.GLASS),
                    "The nail kept tunnelling: it also broke the glass "
                            + "five blocks behind the first.");
            if (++flight.ticksAfterContact >= 10) {
                fixture.pass();
            }
        });
    }

    // 1.16 NailEntity.breakProjectile opens the wormhole on every block contact, a broken soft block included.
    @GameTest(
            template = "empty",
            batch = "tusk_nail_soft_block_charged",
            timeoutTicks = TIMEOUT_TICKS)
    public static void chargedNailOpensItsWormholeAtTheFirstSoftBlock(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskChargedSoft", 32);
        BlockPos glass = fixture.feet.offset(0, 1, 4);
        Flight flight = new Flight();
        fixture.drive(() -> {
            if (flight.nail == null) {
                fixture.setBlock(glass, Blocks.GLASS.defaultBlockState());
                flight.launch(fixture, CHARGED_NAIL_SHOT);
                if (flight.nail != null) {
                    helper.assertTrue(
                            flight.nail.getSpinCharge()
                                    == CHARGE_TICKS / 5,
                            "A " + CHARGE_TICKS + "-tick charge gave spin "
                                    + flight.nail.getSpinCharge() + ".");
                }
                return;
            }
            NailEntity nail = flight.nail;
            if (!fixture.level.getBlockState(glass).isAir()) {
                helper.assertTrue(
                        !nail.isRemoved(),
                        "The charged nail was removed before it reached "
                                + "the glass.");
                flight.start = nail.position();
                return;
            }
            List<? extends WormholeEntity> wormholes = fixture.wormholes();
            helper.assertTrue(
                    nail.isRemoved()
                            && wormholes.size() == 1
                            && wormholes.getFirst().position()
                                    .distanceTo(flight.start) < 1.0E-6D,
                    "A charged nail that broke a soft block left "
                            + wormholes.size() + " wormhole(s) where it "
                            + "touched it (nail removed: "
                            + nail.isRemoved() + "); the 1.16 nail opens "
                            + "one wormhole at its first block contact.");
            fixture.pass();
        });
    }

    // 1.16 WormholeArmEntity.tick: nail.shootFromRotation(this, 2 + own motion, 1), 0.5 above the arm, sound at the user.
    @GameTest(
            template = "empty",
            batch = "tusk_arm_shot",
            timeoutTicks = TIMEOUT_TICKS)
    public static void remoteArmShotLeavesAlongTheArmsAim(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskArmShot", 32);
        ArmShot shot = new ArmShot();
        fixture.drive(() -> shot.step(fixture, NAIL_SHOT));
    }

    @GameTest(
            template = "empty",
            batch = "tusk_arm_charged_shot",
            timeoutTicks = TIMEOUT_TICKS)
    public static void remoteArmChargedShotLeavesAlongTheArmsAim(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskArmCharged", 32);
        ArmShot shot = new ArmShot();
        fixture.drive(() -> shot.step(fixture, CHARGED_NAIL_SHOT));
    }

    // 1.16 WormholeArmEntity.tick: the arm closes when nailCount - nailsShot == 0 and a nail was shot through it.
    @GameTest(
            template = "empty",
            batch = "tusk_arm_shot_count",
            timeoutTicks = TIMEOUT_TICKS)
    public static void remoteArmClosesAfterAsManyShotsAsNailsAreLeft(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskArmCount", 32);
        ArmShotCount count = new ArmShotCount(false, 5);
        fixture.drive(() -> count.step(fixture));
    }

    // 1.16 TuskCapability.useNail returns early in Creative: the count stays at 10, so the arm closes after 10 shots.
    @GameTest(
            template = "empty",
            batch = "tusk_arm_shot_count_creative",
            timeoutTicks = TIMEOUT_TICKS)
    public static void creativeRemoteArmClosesAfterTenShots(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskArmCountC", 32);
        ArmShotCount count = new ArmShotCount(true, 10);
        fixture.drive(() -> count.step(fixture));
    }

    // 1.16 AbstractWormholeEntity.tick: every wormhole moves by its motion plus (0, -0.7, 0) each tick.
    @GameTest(
            template = "empty",
            batch = "tusk_arm_sink",
            timeoutTicks = TIMEOUT_TICKS)
    public static void idleRemoteArmSinksOntoTheGroundAndFollowsIt(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskArmSink", 16);
        BlockPos floor = fixture.feet.offset(4, 0, 4);
        double floorTop = floor.getY() + 1.0D;
        WormholeArmEntity[] armHolder = {null};
        Vec3[] start = {null};
        int[] holeTick = {-1};
        double[] lastY = {0.0D};
        fixture.drive(() -> {
            TuskPortalOperations operations =
                    TuskPortalOperations.get(fixture.level.getServer());
            if (armHolder[0] == null) {
                fixture.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState());
                armHolder[0] = fixture.openArm(new Vec3(
                        floor.getX() + 0.5D,
                        floorTop + 4.0D,
                        floor.getZ() + 0.5D));
                start[0] = armHolder[0].position();
                fixture.stage = "watching an idle remote arm sink";
                return;
            }
            WormholeArmEntity arm = armHolder[0];
            int ticks = arm.tickCount;
            helper.assertTrue(
                    !arm.isRemoved(),
                    "The remote arm closed after " + ticks
                            + " tick(s) while " + fixture.stage + ".");
            helper.assertTrue(
                    Math.abs(arm.getX() - start[0].x) < 1.0E-9D
                            && Math.abs(arm.getZ() - start[0].z) < 1.0E-9D,
                    "An idle remote arm moved sideways to "
                            + arm.position() + ".");
            if (holeTick[0] < 0) {
                double expected =
                        Math.max(floorTop, start[0].y - 0.7D * ticks);
                helper.assertTrue(
                        Math.abs(arm.getY() - expected) < 1.0E-6D,
                        "An idle remote arm opened 4 blocks above a floor "
                                + "is at y=" + arm.getY() + " after " + ticks
                                + " tick(s), not at y=" + expected
                                + "; the 1.16 wormhole sinks 0.7 a tick "
                                + "until it rests on the ground (floor top y="
                                + floorTop + ").");
                Vec3 saved = operations.getOwned(fixture.owner.getUUID())
                        .armPosition();
                helper.assertTrue(
                        saved.distanceTo(arm.position()) < 1.0E-6D,
                        "The saved arm position " + saved + " is not the "
                                + "arm's " + arm.position() + ".");
                if (ticks >= 16) {
                    fixture.level.setBlockAndUpdate(
                            floor, Blocks.AIR.defaultBlockState());
                    holeTick[0] = ticks;
                    lastY[0] = arm.getY();
                    fixture.stage = "watching the arm sink into a hole";
                }
                return;
            }
            // The tick after the floor goes may still be the step-up of a resting arm: up 0.7 and down 0.7.
            int sinceHole = ticks - holeTick[0];
            double fallen = lastY[0] - arm.getY();
            lastY[0] = arm.getY();
            if (sinceHole >= 2) {
                helper.assertTrue(
                        Math.abs(fallen - 0.7D) < 1.0E-6D,
                        "With the floor gone the arm moved " + (-fallen)
                                + " in its tick " + sinceHole + " over the "
                                + "hole, not -0.7.");
            }
            if (sinceHole >= 7) {
                fixture.pass();
            }
        });
    }

    // 1.16 WormholeEntity extends AbstractWormholeEntity: the damaging wormhole makes the same per-tick move.
    @GameTest(
            template = "empty",
            batch = "tusk_damaging_wormhole_sink",
            timeoutTicks = TIMEOUT_TICKS)
    public static void targetlessDamagingWormholeSinksOntoTheGround(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskHoleSink", 16);
        BlockPos floor = fixture.feet.offset(4, 0, 4);
        double floorTop = floor.getY() + 1.0D;
        WormholeEntity[] holder = {null};
        Vec3[] start = {null};
        int[] holeTick = {-1};
        double[] lastY = {0.0D};
        fixture.drive(() -> {
            if (holder[0] == null) {
                fixture.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState());
                holder[0] = fixture.openWormhole(
                        new Vec3(
                                floor.getX() + 0.5D,
                                floorTop + 3.0D,
                                floor.getZ() + 0.5D),
                        null);
                start[0] = holder[0].position();
                fixture.stage = "watching a targetless wormhole sink";
                return;
            }
            WormholeEntity wormhole = holder[0];
            int ticks = wormhole.tickCount;
            helper.assertTrue(
                    !wormhole.isRemoved(),
                    "The damaging wormhole was removed after " + ticks
                            + " tick(s) while " + fixture.stage + ".");
            helper.assertTrue(
                    Math.abs(wormhole.getX() - start[0].x) < 1.0E-9D
                            && Math.abs(wormhole.getZ() - start[0].z)
                                    < 1.0E-9D,
                    "A targetless damaging wormhole moved sideways to "
                            + wormhole.position() + ".");
            if (holeTick[0] < 0) {
                double expected =
                        Math.max(floorTop, start[0].y - 0.7D * ticks);
                helper.assertTrue(
                        Math.abs(wormhole.getY() - expected) < 1.0E-6D,
                        "A targetless damaging wormhole opened 3 blocks "
                                + "above a floor is at y=" + wormhole.getY()
                                + " after " + ticks + " tick(s), not at y="
                                + expected + "; the 1.16 wormhole sinks 0.7 "
                                + "a tick until it rests on the ground "
                                + "(floor top y=" + floorTop + ").");
                if (ticks >= 12) {
                    fixture.level.setBlockAndUpdate(
                            floor, Blocks.AIR.defaultBlockState());
                    holeTick[0] = ticks;
                    lastY[0] = wormhole.getY();
                    fixture.stage = "watching the wormhole sink into a hole";
                }
                return;
            }
            // The tick after the floor goes may still be the step-up of a resting wormhole: up 0.7 and down 0.7.
            int sinceHole = ticks - holeTick[0];
            double fallen = lastY[0] - wormhole.getY();
            lastY[0] = wormhole.getY();
            if (sinceHole >= 2) {
                helper.assertTrue(
                        Math.abs(fallen - 0.7D) < 1.0E-6D,
                        "With the floor gone the wormhole moved "
                                + (-fallen) + " in its tick " + sinceHole
                                + " over the hole, not -0.7.");
            }
            if (sinceHole >= 6) {
                fixture.pass();
            }
        });
    }

    // 1.16 WormholeEntity.tick: after the sink, move(motion + (target - position) * (0.15, 1, 0.15)), then hurt within 0.2.
    @GameTest(
            template = "empty",
            batch = "tusk_damaging_wormhole_homing",
            timeoutTicks = TIMEOUT_TICKS)
    public static void damagingWormholeHomesWithTheDonorStep(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskHoleHoming", 16);
        WormholeEntity[] holder = {null};
        Pig[] targetHolder = {null};
        Vec3[] targetStart = {null};
        Vec3[] last = {null};
        int[] ticksInRange = {0};
        fixture.drive(() -> {
            if (holder[0] == null) {
                Pig pig = fixture.spawnStillPig(new Vec3(
                        fixture.feet.getX() + 4.5D,
                        fixture.feet.getY() + 3.0D,
                        fixture.feet.getZ() + 6.5D));
                targetHolder[0] = pig;
                targetStart[0] = pig.position();
                holder[0] = fixture.openWormhole(
                        pig.position().add(-2.0D, 1.5D, -1.0D), pig);
                last[0] = holder[0].position();
                fixture.stage = "watching a damaging wormhole home";
                return;
            }
            WormholeEntity wormhole = holder[0];
            Pig pig = targetHolder[0];
            int ticks = wormhole.tickCount;
            helper.assertTrue(
                    !wormhole.isRemoved() && pig.isAlive(),
                    "The damaging wormhole (removed: " + wormhole.isRemoved()
                            + ") or its target (alive: " + pig.isAlive()
                            + ") was lost after " + ticks + " tick(s).");
            helper.assertTrue(
                    pig.position().distanceTo(targetStart[0]) < 1.0E-9D,
                    "The still target moved to " + pig.position() + ".");
            Vec3 target = targetStart[0];
            Vec3 before = last[0];
            Vec3 now = wormhole.position();
            last[0] = now;
            Vec3 expected = new Vec3(
                    before.x + 0.15D * (target.x - before.x),
                    target.y,
                    before.z + 0.15D * (target.z - before.z));
            helper.assertTrue(
                    now.distanceTo(expected) < 1.0E-6D,
                    "In its tick " + ticks + " the damaging wormhole went "
                            + "from " + before + " to " + now + ", not to "
                            + expected + " (target at " + target + "); 1.16 "
                            + "sinks it 0.7 and then moves it by the way to "
                            + "its target scaled by (0.15, 1, 0.15).");
            boolean inRange = pig.distanceToSqr(wormhole)
                    < WormholeEntity.DAMAGE_DISTANCE_SQR;
            if (inRange) {
                ticksInRange[0]++;
            }
            boolean hurt = pig.getHealth() < pig.getMaxHealth();
            helper.assertTrue(
                    hurt == ticksInRange[0] > 0,
                    "After " + ticks + " tick(s), " + ticksInRange[0]
                            + " of them ending within the damage distance, "
                            + "the target's health is " + pig.getHealth()
                            + " of " + pig.getMaxHealth() + "; 1.16 hurts "
                            + "it in every tick that ends closer than 0.2 "
                            + "(squared) and not before.");
            helper.assertTrue(
                    ticks <= 40,
                    "The damaging wormhole did not reach its target in "
                            + ticks + " ticks.");
            if (ticksInRange[0] >= 3) {
                fixture.pass();
            }
        });
    }

    // 1.16 WormholeTeleporterEntity.tick sets the EXIT's motion while MOVE is held and never clears it.
    @GameTest(
            template = "empty",
            batch = "tusk_portal_move_ticks",
            timeoutTicks = TIMEOUT_TICKS)
    public static void heldPortalMoveMakesOneDonorMoveATickAndKeepsItsMotion(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskPortalHeld", 32);
        PortalMove move = new PortalMove();
        fixture.drive(() -> move.step(fixture));
    }

    // 1.16 WormholeTeleporterEntity.tick teleports an occupant only if it is not an AbstractWormholeEntity.
    @GameTest(
            template = "empty",
            batch = "tusk_portal_occupants",
            timeoutTicks = TIMEOUT_TICKS)
    public static void linkedPortalCarriesAMobButNoWormhole(
            GameTestHelper helper) {
        Fixture fixture = new Fixture(
                helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskPortalOcc", 16);
        Fixture armUser;
        try {
            armUser = new Fixture(
                    helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                    "TuskPortalOccArm", 16);
        }
        catch (RuntimeException failure) {
            fixture.close();
            throw failure;
        }
        PortalOccupants occupants = new PortalOccupants(armUser);
        fixture.drive(() -> {
            try {
                occupants.step(fixture);
            }
            catch (RuntimeException failure) {
                armUser.close();
                throw failure;
            }
        });
    }

    // 1.16 had no Tusk stamina condition: StandAction.onPerform and StandEntityTask paid each cost with the result
    // ignored, and StandPower.consumeStamina drained a short bar to 0.
    @GameTest(template = "empty", batch = "tusk_stamina_charged", timeoutTicks = TIMEOUT_TICKS)
    public static void chargedNailShotFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskShortCharged", 32);
        Flight flight = new Flight();
        float[] atShot = {-1.0F};
        fixture.drive(() -> {
            if (flight.action == null) {
                fixture.power.setStamina(50.0F);
                helper.assertTrue(fixture.preflight(CHARGED_NAIL_SHOT).isPositive(),
                        "1.16 had no stamina condition: the charged shot must be admitted at 50 stamina, got "
                                + describe(fixture.preflight(CHARGED_NAIL_SHOT)));
                fixture.listen(EntityJoinLevelEvent.class, event -> {
                    if (atShot[0] < 0.0F && event.getEntity() instanceof NailEntity nail
                            && nail.getOwner() == fixture.owner) {
                        atShot[0] = fixture.power.getStamina();
                    }
                });
            }
            if (flight.nail == null) {
                flight.launch(fixture, CHARGED_NAIL_SHOT);
                return;
            }
            helper.assertTrue(atShot[0] == 0.0F,
                    "the 175 cost drains a 50 bar to 0 before the nail leaves, the bar held " + atShot[0]);
            fixture.pass();
        });
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran TuskChargedNailShot.checkHeldItems on every held tick: an item in a
    // hand ended the charge with the "hands" message, and stoppedHolding fired the nail charged so far.
    @GameTest(template = "empty", batch = "tusk_charged_hands", timeoutTicks = TIMEOUT_TICKS)
    public static void chargedNailShotFiresWhenAHandFillsAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskChargedHands", 32);
        EntityActionInstance[] held = {null};
        int[] ticksAfterItem = {-1};
        fixture.drive(() -> {
            ServerPlayer owner = fixture.owner;
            if (held[0] == null) {
                held[0] = fixture.hold(CHARGED_NAIL_SHOT);
                fixture.stage = "charging the nail shot";
                return;
            }
            EntityActionInstance action = held[0];
            if (ticksAfterItem[0] < 0) {
                helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE && fixture.nails().isEmpty(),
                        "Setup: the charge ended or fired with free hands: " + action.getPhase());
                if (action.getPhaseTick() >= CHARGE_TICKS) {
                    fixture.forgetSentPackets();
                    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
                    ticksAfterItem[0] = 0;
                    fixture.stage = "waiting for the charge to end";
                }
                return;
            }
            if (++ticksAfterItem[0] < 2) {
                return;
            }
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(action.getPhase() != ActionPhase.BUTTON_CHARGE,
                    "1.16 ended the charge once a hand held an item; it still charges.");
            NailEntity nail = fixture.firstNail();
            helper.assertTrue(nail != null && fixture.nails().size() == 1,
                    "1.16 stoppedHolding fired the nail when the item ended the charge; nails in the level: "
                            + fixture.nails().size());
            helper.assertTrue(nail.getSpinCharge() == CHARGE_TICKS / TuskChargedNailShotAbility.TICKS_PER_SPIN_CHARGE,
                    "The nail fired at the stop has spin charge " + nail.getSpinCharge() + " after a "
                            + CHARGE_TICKS + "-tick charge.");
            helper.assertTrue(TuskAddon.nailState(owner).nails() == TuskNailState.MAX_NAILS - 1,
                    "The stopped charge spent " + (TuskNailState.MAX_NAILS - TuskAddon.nailState(owner).nails())
                            + " nail(s), not 1.");
            fixture.channel.flushOutbound();
            boolean handsMessage = false;
            for (Object message : fixture.channel.outboundMessages()) {
                if (message instanceof ClientboundSystemChatPacket chat && chat.overlay()
                        && chat.content().getContents() instanceof TranslatableContents key
                        && key.getKey().equals("jojo.message.action_condition.hands")) {
                    handsMessage = true;
                }
            }
            helper.assertTrue(handsMessage, "1.16 sent the \"hands\" message when the item ended the charge.");
            fixture.pass();
        });
    }

    // 1.16 TuskChargedNailShot.checkSpecificConditions was positive for nails > 0 or a Creative player: a Creative user
    // with no nails started the charge, and stoppedHolding (nails > 0 only) shot nothing on the release.
    @GameTest(template = "empty", batch = "tusk_charged_creative_empty", timeoutTicks = TIMEOUT_TICKS)
    public static void creativeUserChargesTheShotWithNoNailsLeftAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskChargedCrea", 32);
        EntityActionInstance[] held = {null};
        int[] setupTick = {0};
        int[] ticksAfterRelease = {-1};
        fixture.drive(() -> {
            ServerPlayer owner = fixture.owner;
            if (held[0] == null) {
                int tick = setupTick[0]++;
                if (tick == 0) {
                    TuskAddon.nailState(owner).applySnapshot(0, 0, false);
                    return;
                }
                if (tick == 1) {
                    helper.assertTrue(!fixture.preflight(CHARGED_NAIL_SHOT).isPositive(),
                            "A Survival user with no nails was admitted to the charged shot.");
                    owner.setGameMode(GameType.CREATIVE);
                    return;
                }
                ConditionCheck creative = fixture.preflight(CHARGED_NAIL_SHOT);
                helper.assertTrue(creative.isPositive(),
                        "A Creative user with no nails was refused the charged shot (" + describe(creative)
                                + "); 1.16 admitted Creative whatever the nail count.");
                held[0] = fixture.hold(CHARGED_NAIL_SHOT);
                fixture.stage = "charging the nail shot of a Creative user at 0 nails";
                return;
            }
            EntityActionInstance action = held[0];
            if (ticksAfterRelease[0] < 0) {
                helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE
                                && LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND) == action,
                        "A Creative user's charge at 0 nails ended after " + Mth.floor(action.getPhaseTick())
                                + " tick(s) in " + action.getPhase() + "; 1.16 keeps it.");
                if (action.getPhaseTick() >= CHARGE_TICKS) {
                    fixture.release();
                    ticksAfterRelease[0] = 0;
                    fixture.stage = "waiting for the released charge to end";
                }
                return;
            }
            helper.assertTrue(fixture.nails().isEmpty() && TuskAddon.nailState(owner).nails() == 0,
                    "The released charge at 0 nails shot a nail.");
            helper.assertTrue(fixture.power.getStamina() >= fixture.power.getMaxStamina(),
                    "The released charge at 0 nails paid the 175 stamina: " + fixture.power.getStamina());
            if (++ticksAfterRelease[0] >= 4) {
                helper.assertTrue(action.getPhase() != ActionPhase.BUTTON_CHARGE,
                        "The release did not end the Creative user's charge.");
                fixture.pass();
            }
        });
    }

    // 1.16 Action.checkConditions asked checkHeldItems (the hands) before the nail count, and the nail scratch's target
    // (PowerBaseImpl.checkTarget) before both: with no nails left a busy hand is refused with "hands", not "no nails".
    @GameTest(template = "empty", batch = "tusk_refusal_order", timeoutTicks = TIMEOUT_TICKS)
    public static void busyHandIsRefusedBeforeTheNailCountAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_2.get(), GameType.SURVIVAL,
                "TuskRefusalOrder", 16);
        Pig[] target = {null};
        int[] stage = {0};
        String hands = "jojo.message.action_condition.hands";
        String noNails = "rotp_t.message.action_condition.no_nails";
        String scratchTarget = "rotp_t.message.action_condition.scratch_target";
        fixture.drive(() -> {
            ServerPlayer owner = fixture.owner;
            // The power answers one preflight per tick from its cache, so each change waits for the next tick.
            switch (stage[0]++) {
                case 0 -> {
                    target[0] = fixture.spawnStillPig(Vec3.atBottomCenterOf(fixture.feet.offset(0, 0, 2)));
                    LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(target[0]));
                    TuskAddon.nailState(owner).applySnapshot(0, 0, false);
                    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
                }
                case 1 -> {
                    expectRefusal(helper, fixture, NAIL_SHOT, hands, "busy hands and no nails");
                    expectRefusal(helper, fixture, CHARGED_NAIL_SHOT, hands, "busy hands and no nails");
                    expectRefusal(helper, fixture, NAIL_SCRATCH, hands, "busy hands and no nails");
                    LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.EMPTY);
                }
                case 2 -> {
                    expectRefusal(helper, fixture, NAIL_SCRATCH, scratchTarget, "busy hands, no nails, no target");
                    LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(target[0]));
                    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
                case 3 -> {
                    expectRefusal(helper, fixture, NAIL_SHOT, noNails, "free hands and no nails");
                    expectRefusal(helper, fixture, CHARGED_NAIL_SHOT, noNails, "free hands and no nails");
                    expectRefusal(helper, fixture, NAIL_SCRATCH, noNails, "free hands and no nails");
                    fixture.pass();
                }
                default -> fixture.pass();
            }
        });
    }

    private static void expectRefusal(GameTestHelper helper, Fixture fixture, String ability, String messageKey,
            String setup) {
        ConditionCheck check = fixture.preflight(ability);
        Component warning = check.getWarning();
        String key = warning != null && warning.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey() : "";
        helper.assertTrue(!check.isPositive() && key.equals(messageKey),
                ability + " with " + setup + " answered " + (check.isPositive() ? "positive" : "'" + key + "'")
                        + "; 1.16 refused with '" + messageKey + "'.");
    }

    @GameTest(template = "empty", batch = "tusk_stamina_scratch", timeoutTicks = TIMEOUT_TICKS)
    public static void nailScratchHitsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK.get(), GameType.SURVIVAL,
                "TuskShortScratch", 16);
        Pig[] target = {null};
        float[] atHit = {-1.0F};
        fixture.drive(() -> {
            if (target[0] == null) {
                Pig pig = target[0] = fixture.spawnStillPig(Vec3.atBottomCenterOf(fixture.feet.offset(0, 0, 2)));
                LivingComponentAction.getComponent(fixture.owner).entityAim.setTarget(new ActionTarget(pig));
                fixture.listen(LivingIncomingDamageEvent.class, event -> {
                    if (atHit[0] < 0.0F && event.getEntity() == pig) {
                        atHit[0] = fixture.power.getStamina();
                    }
                });
                fixture.power.setStamina(50.0F);
                fixture.click(NAIL_SCRATCH);
                fixture.stage = "waiting for the scratch to hit";
                return;
            }
            if (atHit[0] < 0.0F) {
                return;
            }
            helper.assertTrue(atHit[0] == 0.0F,
                    "the 100 cost drains a 50 bar to 0 before the scratch hits, the bar held " + atHit[0]);
            fixture.pass();
        });
    }

    @GameTest(template = "empty", batch = "tusk_stamina_portal", timeoutTicks = TIMEOUT_TICKS)
    public static void portalPairOpensOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_3.get(), GameType.SURVIVAL,
                "TuskShortPortal", 16);
        boolean[] pressed = {false};
        float[] atOpen = {-1.0F};
        fixture.drive(() -> {
            if (!pressed[0]) {
                pressed[0] = true;
                fixture.setBlock(fixture.feet.below(2), Blocks.OBSIDIAN.defaultBlockState());
                WormholeEntity source = new WormholeEntity(fixture.owner, fixture.level, 200, null);
                source.setPos(fixture.feet.getX() + 4.5D, fixture.feet.getY() + 3.5D, fixture.feet.getZ() + 2.5D);
                helper.assertTrue(fixture.level.addFreshEntity(source),
                        "Could not add the wormhole the pair opens from.");
                fixture.listen(EntityJoinLevelEvent.class, event -> {
                    if (atOpen[0] < 0.0F && event.getEntity() instanceof WormholePortalEntity) {
                        atOpen[0] = fixture.power.getStamina();
                    }
                });
                fixture.power.setStamina(50.0F);
                fixture.click(WORMHOLE);
                fixture.stage = "waiting for the portal pair";
                return;
            }
            if (atOpen[0] < 0.0F) {
                return;
            }
            helper.assertTrue(atOpen[0] == 0.0F,
                    "the 150 cost drains a 50 bar to 0 before the pair opens, the bar held " + atOpen[0]);
            fixture.pass();
        });
    }

    @GameTest(template = "empty", batch = "tusk_stamina_rotation", timeoutTicks = TIMEOUT_TICKS)
    public static void infiniteRotationStrikesOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_4.get(), GameType.SURVIVAL,
                "TuskShortRotation", 16);
        Pig[] target = {null};
        float[] atStrike = {-1.0F};
        fixture.drive(() -> {
            if (target[0] == null) {
                StandEntity stand = fixture.power.getSummonedStandEntity();
                helper.assertTrue(stand != null, "Tusk Act 4 is not summoned.");
                stand.summonLockTicks = 0;
                stand.gradualSummonWeaknessTicks = 0;
                TuskAddon.nailState(fixture.owner).setInfiniteRotationCharged(true);
                // The pig floats on the Stand's level dash line, 3 blocks ahead, so the sweep itself strikes it.
                Pig pig = target[0] = fixture.spawnStillPig(Vec3.atBottomCenterOf(fixture.feet.offset(0, 0, 3))
                        .with(Direction.Axis.Y, stand.getEyeY() - 0.45D));
                // The server reads both aims; a mock player sends none, so both are set here.
                LivingComponentAction.getComponent(fixture.owner).entityAim.setTarget(new ActionTarget(pig));
                LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(pig));
                fixture.listen(MobEffectEvent.Added.class, event -> {
                    if (atStrike[0] < 0.0F && event.getEntity() == pig
                            && event.getEffectInstance().is(TuskAddon.INFINITE_ROTATION)) {
                        atStrike[0] = fixture.power.getStamina();
                    }
                });
                fixture.power.setStamina(100.0F);
                Ability rotation = fixture.ability(TuskAddon.INFINITE_ROTATION_NAME);
                ConditionCheck check = rotation.checkConditions(fixture.power);
                helper.assertTrue(check.isPositive(),
                        "1.16 had no stamina condition: Infinite Rotation must be admitted at 100 stamina, got "
                                + describe(check));
                BufferingState buffering = BufferingState.clickOnly();
                rotation.onKeyPress(fixture.level, fixture.owner, null, InputMethod.CLICK, 0.0F, buffering);
                helper.assertTrue(buffering.isActionSuccess, "Infinite Rotation did not start on the admitted press.");
                fixture.stage = "waiting for Infinite Rotation to reach the pig";
                return;
            }
            if (atStrike[0] < 0.0F) {
                return;
            }
            helper.assertTrue(atStrike[0] == 0.0F,
                    "the 425 cost drains a 100 bar to 0 before the strike lands, the bar held " + atStrike[0]);
            fixture.pass();
        });
    }

    // A port addition the owner kept (owner answers 2026-10-16, Wave 15 Q2; 1.16 TuskInfiniteRotation.checkStandTarget
    // refused every pressed target): the Stand turns to the mob it was pressed at and hands it the spin by the dash's
    // end, also when that mob stands beside the dash line.
    @GameTest(template = "empty", batch = "tusk_rotation_pressed_mob", timeoutTicks = TIMEOUT_TICKS)
    public static void infiniteRotationHandsItsSpinToTheMobItWasPressedAt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_4.get(), GameType.SURVIVAL,
                "TuskRotationPressedMob", 16);
        Pig[] target = {null};
        EntityActionInstance[] action = {null};
        boolean[] turned = {false};
        fixture.drive(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            if (target[0] == null) {
                helper.assertTrue(stand != null, "Tusk Act 4 is not summoned.");
                stand.summonLockTicks = 0;
                stand.gradualSummonWeaknessTicks = 0;
                TuskAddon.nailState(fixture.owner).setInfiniteRotationCharged(true);
                // 4 blocks beside the user, level with its feet: far off the dash along the Stand's look (south).
                Pig pig = target[0] = fixture.spawnStillPig(Vec3.atBottomCenterOf(fixture.feet.offset(4, 0, 2)));
                helper.assertTrue(stand.canAttack(pig) && !pig.hasEffect(TuskAddon.INFINITE_ROTATION),
                        "Setup: the pig is not a fresh target Tusk Act 4 may attack.");
                // The server reads both aims; a mock player sends none, so both are set here.
                LivingComponentAction.getComponent(fixture.owner).entityAim.setTarget(new ActionTarget(pig));
                LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(pig));
                Ability rotation = fixture.ability(TuskAddon.INFINITE_ROTATION_NAME);
                BufferingState buffering = BufferingState.clickOnly();
                rotation.onKeyPress(fixture.level, fixture.owner, null, InputMethod.CLICK, 0.0F, buffering);
                action[0] = stand.getCurStandAction();
                helper.assertTrue(buffering.isActionSuccess && action[0] != null && action[0].ability == rotation,
                        "Infinite Rotation did not start on the press: " + action[0]);
                turned[0] = action[0].standRotationTarget != null
                        && action[0].standRotationTarget.getMainEntity() == pig;
                fixture.stage = "waiting for the Infinite Rotation dash to end";
                return;
            }
            if (stand != null && stand.getCurStandAction() == action[0] && !action[0].isOver()
                    && action[0].getPhase() != ActionPhase.RECOVERY) {
                return;
            }
            boolean spun = target[0].hasEffect(TuskAddon.INFINITE_ROTATION);
            helper.assertTrue(turned[0] && spun,
                    "Infinite Rotation pressed at a pig 4 blocks beside its dash line turns the Stand to it and hands"
                            + " it the spin (the port addition the owner kept): turned to it " + turned[0]
                            + ", spin given " + spun);
            fixture.pass();
        });
    }

    // 1.16 TuskOpenSpace.standPerform: aimWithThisOrUser(6.0) traces 6 blocks from the user's eyes, past the reach of
    // the Act 4 Stand standing behind the user.
    @GameTest(template = "empty", batch = "tusk_stamina_open_space", timeoutTicks = TIMEOUT_TICKS)
    public static void openSpaceOpensOnAShortBarAndDrainsIt(GameTestHelper helper) {
        openSpaceScene(helper, "TuskShortOpenSpace", 3, PressAim.WALL, true);
    }

    // The press has a block target, so the perform re-aims (1.16 aimWithThisOrUser(6.0)) and the 6-block ray from the
    // user's eyes meets the wall whose face is 5.5 blocks away, past the 5 blocks a press itself could aim.
    @GameTest(template = "empty", batch = "tusk_open_space_far", timeoutTicks = TIMEOUT_TICKS)
    public static void openSpaceOpensAWallSixBlocksAhead(GameTestHelper helper) {
        openSpaceScene(helper, "TuskFarOpenSpace", 6, PressAim.SIDE_BLOCK, true);
    }

    // The wall's face is 6.5 blocks from the user's eyes and further from the Stand's: out of the re-aim's 6-block ray.
    @GameTest(template = "empty", batch = "tusk_open_space_too_far", timeoutTicks = TIMEOUT_TICKS)
    public static void openSpaceDoesNotReachAWallSevenBlocksAhead(GameTestHelper helper) {
        openSpaceScene(helper, "TuskTooFarOpenSpace", 7, PressAim.SIDE_BLOCK, false);
    }

    // 1.16 TuskOpenSpace.getTargetRequirement BLOCK: with no block in the client's pick nor in the Stand's precision
    // ray at the user's 5-block reach, PowerBaseImpl.checkTarget refused the press without a message, before any cost.
    @GameTest(template = "empty", batch = "tusk_open_space_no_target", timeoutTicks = TIMEOUT_TICKS)
    public static void openSpaceRefusesAPressWithNoBlockInReach(GameTestHelper helper) {
        openSpaceScene(helper, "TuskNoTargetOpenSpace", 6, PressAim.NONE, false);
    }

    /** What the user's synced aim (the client's pick) holds at the press. */
    private enum PressAim { WALL, SIDE_BLOCK, NONE }

    /**
     * 1.16 TuskEntity.getDefaultOffsetFromUser: withYOffset(-0.75, 1.5 / 0.75 / 0.2, -0.75) for ACT1-3 and
     * withYOffset(0, 0.5, -1.5) for ACT4, (left, up, forward) from the user's feet, turned by the view yaw, and
     * the default arms-only withYOffset(0, 0, 0.15); the act's y was the default y of task offsets without their
     * own. The core grab offset (-1, y, 1.5) is kept for ACT4's grab skills.
     */
    @GameTest(template = "empty", batch = "tusk_idle_placement", timeoutTicks = 40)
    public static void idleTuskActsStandWhereTheDonorPutThem(GameTestHelper helper) {
        checkIdleAct(helper, TuskAddon.TUSK.get(), -0.75D, 1.5D, -0.75D);
        checkIdleAct(helper, TuskAddon.TUSK_ACT_2.get(), -0.75D, 0.75D, -0.75D);
        checkIdleAct(helper, TuskAddon.TUSK_ACT_3.get(), -0.75D, 0.2D, -0.75D);
        checkIdleAct(helper, TuskAddon.TUSK_ACT_4.get(), 0.0D, 0.5D, -1.5D);
        helper.succeed();
    }

    private static void checkIdleAct(GameTestHelper helper, StandType type, double left, double y, double forward) {
        String act = String.valueOf(type.getId());
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "tusk-idle")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the " + act + " user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "Could not grant " + act);
            helper.assertTrue(type.summon(user, power), "Could not summon " + act);
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser() && !stand.isArmsOnlyMode()
                    && stand.offsetFromUser.isIdle(), "setup: " + act + " is not an idle full Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == y
                            && new Vec3(-1.0D, y, 1.5D).equals(stand.offsetFromUser.grabIdleOffset),
                    "1.16 " + act + " task offsets without a y took " + y + ", got " + stand.Y_OFFSET
                            + "; grab offset " + stand.offsetFromUser.grabIdleOffset);
            turnIdleUser(user, 0.0F, 50.0F);
            assertIdleAt(helper, act, user, stand, left, y, forward, "view yaw 0, body yaw 50");
            turnIdleUser(user, 90.0F, 0.0F);
            assertIdleAt(helper, act, user, stand, -forward, y, left, "view yaw 90, body yaw 0");
            turnIdleUser(user, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertIdleAt(helper, act, user, stand, 0.0D, 0.0D, 0.15D, "arms-only, view yaw 0");
        } finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    private static void turnIdleUser(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertIdleAt(GameTestHelper helper, String act, Player user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put an idle " + act + " at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with "
                        + when + ", got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static void openSpaceScene(GameTestHelper helper, String name, int blocksAhead, PressAim pressAim,
            boolean opens) {
        Fixture fixture = new Fixture(helper, TuskAddon.TUSK_ACT_4.get(), GameType.SURVIVAL, name, 16);
        BlockPos wall = fixture.feet.offset(0, 1, blocksAhead);
        // Off the user's look, 3.5 blocks from its eyes and out of the reach of the Stand behind it.
        BlockPos side = fixture.feet.offset(3, 1, 3);
        int[] step = {0};
        float[] atOpen = {-1.0F};
        int[] pressTick = {-1};
        fixture.drive(() -> {
            if (step[0] == 0) {
                step[0] = 1;
                StandEntity stand = fixture.power.getSummonedStandEntity();
                helper.assertTrue(stand != null, "Tusk Act 4 is not summoned.");
                stand.summonLockTicks = 0;
                stand.gradualSummonWeaknessTicks = 0;
                fixture.setBlock(wall, Blocks.STONE.defaultBlockState());
                fixture.setBlock(side, Blocks.STONE.defaultBlockState());
                // A client's block pick reaches 4.5 blocks; the Stand's own aim is left empty.
                LivingComponentAction.getComponent(fixture.owner).entityAim.setTarget(switch (pressAim) {
                    case WALL -> new ActionTarget(wall, Direction.NORTH);
                    case SIDE_BLOCK -> new ActionTarget(side, Direction.WEST);
                    case NONE -> ActionTarget.EMPTY;
                });
                fixture.power.setStamina(100.0F);
                // The power answers one preflight per tick from its cache, so the press waits for the next tick.
                return;
            }
            if (step[0] == 1) {
                step[0] = 2;
                pressTick[0] = (int) helper.getTick();
                if (pressAim == PressAim.NONE) {
                    ConditionCheck check = fixture.preflight(TuskAddon.OPEN_SPACE_NAME);
                    helper.assertTrue(!check.isPositive() && check.getWarning() == null,
                            "1.16 refused Open Space without a message when no block was aimed within the user's"
                                    + " 5 blocks, the port answered " + (check.isPositive() ? "admitted" : describe(check)));
                    fixture.stage = "watching the refused Open Space press";
                    return;
                }
                fixture.click(TuskAddon.OPEN_SPACE_NAME);
                fixture.stage = "waiting for Open Space to open the wall " + blocksAhead + " blocks ahead";
                return;
            }
            helper.assertTrue(fixture.level.getBlockState(side).is(Blocks.STONE),
                    "Open Space opened the block the press aimed at instead of re-aiming along the user's look");
            if (!opens) {
                helper.assertTrue(fixture.level.getBlockState(wall).is(Blocks.STONE),
                        pressAim == PressAim.NONE
                                ? "Open Space opened a wall after a press with no block target"
                                : "Open Space opened a wall whose face is 6.5 blocks away, past its 6-block ray");
                if (helper.getTick() - pressTick[0] < 20) {
                    return;
                }
                helper.assertTrue(fixture.power.getStamina() >= 100.0F,
                        "Open Space paid without a wall to open, the bar held " + fixture.power.getStamina());
                fixture.pass();
                return;
            }
            // The Stand acts until the recovery ends, and the bar does not regenerate while it acts.
            if (atOpen[0] < 0.0F && fixture.level.getBlockState(wall).isAir()) {
                atOpen[0] = fixture.power.getStamina();
            }
            if (atOpen[0] < 0.0F) {
                return;
            }
            helper.assertTrue(atOpen[0] == 0.0F,
                    "the 225 cost drains a 100 bar to 0 when the wall opens, the bar held " + atOpen[0]);
            fixture.pass();
        });
    }

    private static String describe(ConditionCheck check) {
        Component warning = check.getWarning();
        return warning != null ? warning.getString() : "no message";
    }

    private static double degrees(double dot) {
        return Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0D, 1.0D)));
    }

    // The table sines of a view vector leave it a little short of unit length.
    private static Vec3 look(Entity entity) {
        return entity.getLookAngle().normalize();
    }

    /** Holds a shot until its first nail is in the level, then lets go so that no second nail follows. */
    private static final class Flight {
        private EntityActionInstance action;
        private boolean released;
        private NailEntity nail;
        private Vec3 start;
        private Vec3 velocity;
        private int ticks;
        private int ticksAfterContact = -1;

        private void launch(Fixture fixture, String abilityName) {
            if (action == null) {
                action = fixture.hold(abilityName);
                fixture.stage = "waiting for the nail of " + abilityName;
                return;
            }
            if (CHARGED_NAIL_SHOT.equals(abilityName) && !released) {
                if (action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && action.getPhaseTick() >= CHARGE_TICKS) {
                    fixture.release();
                    released = true;
                }
                return;
            }
            NailEntity first = fixture.firstNail();
            if (first == null) {
                return;
            }
            fixture.release();
            fixture.helper.assertTrue(
                    first.tickCount == 0,
                    "The nail had already ticked " + first.tickCount
                            + " time(s) when it was first seen.");
            nail = first;
            start = first.position();
            velocity = first.getDeltaMovement();
            fixture.stage = "following the nail of " + abilityName;
        }
    }

    private static final class ArmShot {
        private WormholeArmEntity arm;
        private int armTicksAtMotion = -1;
        private final Flight flight = new Flight();

        private void step(Fixture fixture, String abilityName) {
            GameTestHelper helper = fixture.helper;
            ServerPlayer owner = fixture.owner;
            BlockPos aimed = fixture.feet.offset(0, 1, 8);
            if (arm == null) {
                fixture.setBlock(aimed, Blocks.OBSIDIAN.defaultBlockState());
                WormholeEntity source = new WormholeEntity(
                        owner, fixture.level, 200, null);
                source.setPos(
                        fixture.feet.getX() + 4.5D,
                        fixture.feet.getY() + 3.0D,
                        fixture.feet.getZ() + 1.5D);
                helper.assertTrue(
                        fixture.level.addFreshEntity(source),
                        "Could not add the wormhole the arm opens from.");
                TuskPortalOperations operations =
                        TuskPortalOperations.get(fixture.level.getServer());
                helper.assertTrue(
                        operations.createRemoteArm(owner),
                        "Production remote-arm creation failed.");
                arm = operations.resolveArmForShot(owner);
                helper.assertTrue(
                        arm != null,
                        "The remote arm did not resolve for a shot.");
                fixture.stage = "waiting for the remote arm to turn";
                return;
            }
            helper.assertTrue(
                    !arm.isRemoved(),
                    "The remote arm closed while " + fixture.stage + ".");
            if (armTicksAtMotion < 0) {
                if (arm.tickCount < 1) {
                    return;
                }
                Vec3 aimedPoint = new Vec3(
                        owner.getX(), owner.getEyeY(), aimed.getZ());
                double offAim = degrees(look(arm).dot(
                        aimedPoint.subtract(arm.getEyePosition())
                                .normalize()));
                helper.assertTrue(
                        offAim < 0.1D,
                        "The remote arm looks " + offAim + " degrees off "
                                + "the point " + aimedPoint + " its owner "
                                + "aims at; 1.16 turns it to the exact "
                                + "point the owner's 100-block ray hits.");
                arm.setDeltaMovement(ARM_MOTION);
                owner.setDeltaMovement(Vec3.ZERO);
                armTicksAtMotion = arm.tickCount;
                return;
            }
            if (flight.action == null) {
                // 1.16: an arm that is not being moved keeps only its vertical motion.
                if (arm.tickCount <= armTicksAtMotion) {
                    return;
                }
                fixture.forgetSentPackets();
            }
            if (flight.nail == null) {
                flight.launch(fixture, abilityName);
            }
            NailEntity nail = flight.nail;
            if (nail == null) {
                return;
            }

            List<String> faults = new ArrayList<>();
            Vec3 fromArm = nail.position().subtract(arm.position());
            if (fromArm.distanceTo(new Vec3(0.0D, 0.5D, 0.0D)) > 1.0E-6D) {
                faults.add("it started " + fromArm + " from the arm, not "
                        + "0.5 above it");
            }
            Vec3 direction = nail.getDeltaMovement().normalize();
            if (direction.dot(look(arm)) < AIM_DOT) {
                faults.add("it left "
                        + degrees(direction.dot(look(arm)))
                        + " degrees off the arm's aim and "
                        + degrees(direction.dot(look(owner)))
                        + " off its owner's view");
            }
            double speed = nail.getDeltaMovement().length();
            if (Math.abs(speed - 2.7D) > 0.1D) {
                faults.add("its speed is " + speed + ", not 2.0 plus the "
                        + "arm's 0.7 vertical motion");
            }
            String soundFault = fixture.shotSoundFault();
            if (soundFault != null) {
                faults.add(soundFault);
            }
            helper.assertTrue(
                    faults.isEmpty(),
                    "The nail shot through the remote arm differs from "
                            + "1.16: " + String.join("; ", faults) + ".");
            fixture.pass();
        }
    }

    /** Holds a nail shot with the remote arm out and watches when the arm closes. */
    private static final class ArmShotCount {
        private final boolean creative;
        private final int shotsToClose;
        private WormholeArmEntity arm;
        private EntityActionInstance action;
        private int ticksAfterLastShot = -1;

        private ArmShotCount(boolean creative, int shotsToClose) {
            this.creative = creative;
            this.shotsToClose = shotsToClose;
        }

        private void step(Fixture fixture) {
            GameTestHelper helper = fixture.helper;
            ServerPlayer owner = fixture.owner;
            TuskPortalOperations operations =
                    TuskPortalOperations.get(fixture.level.getServer());
            if (arm == null) {
                // The nails leave 0.5 above the arm: a two-block wall ends them.
                fixture.setBlock(
                        fixture.feet.offset(0, 1, 8),
                        Blocks.OBSIDIAN.defaultBlockState());
                fixture.setBlock(
                        fixture.feet.offset(0, 2, 8),
                        Blocks.OBSIDIAN.defaultBlockState());
                BlockPos floor = fixture.feet.offset(4, 0, 1);
                fixture.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState());
                arm = fixture.openArm(new Vec3(
                        floor.getX() + 0.5D,
                        floor.getY() + 1.0D,
                        floor.getZ() + 0.5D));
                if (creative) {
                    owner.setGameMode(GameType.CREATIVE);
                }
                return;
            }
            if (action == null) {
                action = fixture.hold(NAIL_SHOT);
                fixture.stage = "counting the shots through the remote arm";
                return;
            }
            int nails = TuskAddon.nailState(owner).nails();
            String state = " with " + nails + " nail(s) left; 1.16 closes "
                    + "the arm when the nails left minus the shots through "
                    + "it reach zero.";
            if (ticksAfterLastShot < 0) {
                int shots = creative
                        ? Mth.floor(action.getPhaseTick())
                                / TuskNailShotAbility.SHOT_INTERVAL_TICKS
                        : TuskNailState.MAX_NAILS - nails;
                if (shots < shotsToClose) {
                    helper.assertTrue(
                            !arm.isRemoved()
                                    && operations.hasActiveArm(owner),
                            "The remote arm closed after only " + shots
                                    + " shot(s) through it" + state);
                    helper.assertTrue(
                            fixture.isHolding(action),
                            "The held nail shot ended after " + shots
                                    + " shot(s).");
                    return;
                }
                ticksAfterLastShot = 0;
            }
            // The arm tests the count on its own next tick.
            if (ticksAfterLastShot++ < 2) {
                return;
            }
            helper.assertTrue(
                    nails == (creative
                            ? TuskNailState.MAX_NAILS
                            : TuskNailState.MAX_NAILS - shotsToClose),
                    "Unexpected nail count " + nails + " after "
                            + shotsToClose + " shot(s).");
            helper.assertTrue(
                    arm.isRemoved() && !operations.hasActiveArm(owner),
                    "The remote arm is still open 2 ticks after shot "
                            + shotsToClose + " through it" + state);
            fixture.pass();
        }
    }

    /** Holds the production portal move toward an open-air ray end, then lets go. */
    private static final class PortalMove {
        private static final String MOVE_WORMHOLE = "move_wormhole";
        private WormholePortalEntity exit;
        private EntityActionInstance action;
        private Vec3 target;
        private Vec3 last;
        private int ticksBeforeFirstMove;
        private int heldMoves;
        private int ticksSinceRelease = -1;
        private Vec3 keptMotion;

        private void step(Fixture fixture) {
            GameTestHelper helper = fixture.helper;
            ServerPlayer owner = fixture.owner;
            if (exit == null) {
                // The ENTRY opens on the owner and sinks; this block catches it.
                fixture.setBlock(
                        fixture.feet.below(2),
                        Blocks.OBSIDIAN.defaultBlockState());
                WormholeEntity source = new WormholeEntity(
                        owner, fixture.level, 200, null);
                source.setPos(
                        fixture.feet.getX() + 4.5D,
                        fixture.feet.getY() + 3.5D,
                        fixture.feet.getZ() + 2.5D);
                helper.assertTrue(
                        fixture.level.addFreshEntity(source),
                        "Could not add the wormhole the pair opens from.");
                TuskPortalOperations operations =
                        TuskPortalOperations.get(fixture.level.getServer());
                helper.assertTrue(
                        operations.createPortalPair(owner),
                        "Production portal-pair creation failed.");
                Entity member = fixture.level.getEntity(
                        operations.getOwned(owner.getUUID()).exitUuid());
                helper.assertTrue(
                        member instanceof WormholePortalEntity,
                        "The EXIT portal entity is missing.");
                exit = (WormholePortalEntity) member;
                target = owner.getEyePosition().add(
                        owner.getViewVector(1.0F)
                                .scale(TuskPortalOperations.MOVE_RANGE));
                fixture.stage = "waiting to hold the portal move";
                return;
            }
            helper.assertTrue(
                    !exit.isRemoved(),
                    "The portal pair closed while " + fixture.stage + ".");
            if (action == null) {
                action = fixture.hold(MOVE_WORMHOLE);
                fixture.stage = "holding the portal move";
                return;
            }
            Vec3 now = exit.position();
            double restY = target.y - 0.7D;
            if (ticksSinceRelease < 0) {
                helper.assertTrue(
                        fixture.isHolding(action),
                        "The held portal move ended after " + heldMoves
                                + " move(s).");
                if (last == null) {
                    // An open-air target gets no ground lift: the first owned move ends 0.7 below it.
                    if (Math.abs(now.y - restY) < 1.0E-6D) {
                        last = now;
                        return;
                    }
                    helper.assertTrue(
                            ++ticksBeforeFirstMove <= 5,
                            "After " + ticksBeforeFirstMove + " held ticks "
                                    + "the EXIT is at y=" + now.y + ", not "
                                    + "at y=" + restY + ", 0.7 below its "
                                    + "open-air target; a held move and the "
                                    + "per-tick sink must make one move a "
                                    + "tick between them.");
                    return;
                }
                double before = horizontalDistance(last, target);
                double after = horizontalDistance(now, target);
                helper.assertTrue(
                        Math.abs(after - 0.75D * before) < 1.0E-6D
                                && Math.abs(now.y - restY) < 1.0E-6D,
                        "Held move " + (heldMoves + 1) + " took the EXIT "
                                + "from " + last + " to " + now + ": "
                                + after + " blocks short of the target "
                                + "instead of " + 0.75D * before + " at y="
                                + restY + "; 1.16 makes one move of a "
                                + "quarter of the way a tick.");
                last = now;
                if (++heldMoves >= 8) {
                    fixture.release();
                    ticksSinceRelease = 0;
                    fixture.stage = "watching the released EXIT";
                }
                return;
            }
            Vec3 moved = now.subtract(last);
            last = now;
            ticksSinceRelease++;
            if (ticksSinceRelease == 2) {
                keptMotion = exit.getDeltaMovement();
                helper.assertTrue(
                        Math.abs(keptMotion.y - 0.7D) < 1.0E-6D
                                && keptMotion.horizontalDistance() > 0.01D,
                        "The released EXIT lost the motion of its last "
                                + "move: " + keptMotion + ".");
                return;
            }
            if (ticksSinceRelease < 3) {
                return;
            }
            Vec3 expected = new Vec3(keptMotion.x, 0.0D, keptMotion.z);
            helper.assertTrue(
                    moved.distanceTo(expected) < 1.0E-6D,
                    "The released EXIT moved " + moved + " in tick "
                            + ticksSinceRelease + " after the release, not "
                            + expected + "; 1.16 never clears the EXIT's "
                            + "motion " + keptMotion + " and moves it by "
                            + "that motion plus (0, -0.7, 0) every tick.");
            if (ticksSinceRelease >= 8) {
                fixture.pass();
            }
        }

        private static double horizontalDistance(Vec3 from, Vec3 to) {
            return Math.hypot(to.x - from.x, to.z - from.z);
        }
    }

    /** Rests a mob, a damaging wormhole and another user's remote arm in the ENTRY of a linked pair for over 60 ticks. */
    private static final class PortalOccupants {
        private static final int WATCHED_TICKS =
                WormholePortalEntity.OCCUPANT_DELAY_TICKS + 10;
        private final Fixture armUser;
        private WormholePortalEntity entry;
        private WormholePortalEntity exit;
        private Pig pig;
        private WormholeEntity wormhole;
        private WormholeArmEntity arm;
        private int setupTicks;
        private int ticksTogether = -1;
        private boolean pigCarried;

        private PortalOccupants(Fixture armUser) {
            this.armUser = armUser;
        }

        private void step(Fixture fixture) {
            GameTestHelper helper = fixture.helper;
            ServerPlayer owner = fixture.owner;
            BlockPos entryFloor = fixture.feet.below(4);
            BlockPos exitFloor = fixture.feet.offset(4, -4, 4);
            Vec3 rest = new Vec3(
                    entryFloor.getX() + 0.5D,
                    entryFloor.getY() + 1.0D,
                    entryFloor.getZ() + 0.5D);
            TuskPortalOperations operations =
                    TuskPortalOperations.get(fixture.level.getServer());
            if (entry == null) {
                fixture.setBlock(
                        entryFloor, Blocks.OBSIDIAN.defaultBlockState());
                fixture.setBlock(
                        exitFloor, Blocks.OBSIDIAN.defaultBlockState());
                armUser.owner.moveTo(
                        fixture.feet.getX() + 3.5D,
                        fixture.feet.getY(),
                        fixture.feet.getZ() + 0.5D,
                        0.0F,
                        0.0F);
                armUser.start();
                WormholeEntity source = new WormholeEntity(
                        owner, fixture.level, 200, null);
                source.setPos(
                        exitFloor.getX() + 0.5D,
                        exitFloor.getY() + 4.5D,
                        exitFloor.getZ() + 0.5D);
                helper.assertTrue(
                        fixture.level.addFreshEntity(source),
                        "Could not add the wormhole the pair opens from.");
                helper.assertTrue(
                        operations.createPortalPair(owner),
                        "Production portal-pair creation failed.");
                TuskPortalOperations.OperationRecord record =
                        operations.getOwned(owner.getUUID());
                helper.assertTrue(
                        fixture.level.getEntity(record.entryUuid())
                                        instanceof WormholePortalEntity
                                && fixture.level.getEntity(record.exitUuid())
                                        instanceof WormholePortalEntity,
                        "The portal pair entities are missing.");
                entry = (WormholePortalEntity)
                        fixture.level.getEntity(record.entryUuid());
                exit = (WormholePortalEntity)
                        fixture.level.getEntity(record.exitUuid());
                fixture.stage = "waiting for the portal pair to rest";
                return;
            }
            helper.assertTrue(
                    !entry.isRemoved() && !exit.isRemoved(),
                    "The portal pair closed while " + fixture.stage + ".");
            if (pig == null) {
                // The pair has taken its source; a wormhole added now is not one it opens from.
                pig = fixture.spawnStillPig(rest);
                pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
                pig.setHealth(100.0F);
                Vec3 above = rest.add(0.0D, 1.0D, 0.0D);
                wormhole = fixture.openWormhole(above, null);
                arm = armUser.openArm(above);
                fixture.forgetSentPackets();
                fixture.stage = "waiting for the occupants to rest in the ENTRY";
                return;
            }
            String where = " (ENTRY at " + entry.position() + ", EXIT at "
                    + exit.position() + ")";
            helper.assertTrue(
                    !wormhole.isRemoved() && !arm.isRemoved()
                            && pig.isAlive(),
                    "An occupant was lost while " + fixture.stage
                            + ": wormhole removed=" + wormhole.isRemoved()
                            + ", arm removed=" + arm.isRemoved()
                            + ", pig alive=" + pig.isAlive() + ".");
            if (ticksTogether < 0) {
                if (entry.position().distanceTo(rest) < 1.0E-6D
                        && wormhole.position().distanceTo(rest) < 1.0E-6D
                        && arm.position().distanceTo(rest) < 1.0E-6D
                        && entry.contains(pig)
                        && entry.contains(wormhole)
                        && entry.contains(arm)
                        && Math.abs(exit.getY() - rest.y) < 1.0E-6D) {
                    ticksTogether = 0;
                    fixture.stage = "watching the occupants of the ENTRY";
                    return;
                }
                helper.assertTrue(
                        ++setupTicks <= 20,
                        "After " + setupTicks + " ticks the occupants do not "
                                + "rest in the ENTRY: wormhole at "
                                + wormhole.position() + ", arm at "
                                + arm.position() + ", pig at "
                                + pig.position() + where + ".");
                return;
            }
            ticksTogether++;
            helper.assertTrue(
                    wormhole.position().distanceTo(rest) < 1.0E-6D,
                    "After " + ticksTogether + " tick(s) in the ENTRY the "
                            + "damaging wormhole is at " + wormhole.position()
                            + ", not at " + rest + where + "; 1.16 never "
                            + "teleports an AbstractWormholeEntity through a "
                            + "linked portal.");
            helper.assertTrue(
                    arm.position().distanceTo(rest) < 1.0E-6D,
                    "After " + ticksTogether + " tick(s) in the ENTRY "
                            + "another user's remote arm is at "
                            + arm.position() + ", not at " + rest + where
                            + "; 1.16 never teleports an "
                            + "AbstractWormholeEntity through a linked "
                            + "portal.");
            Vec3 carried = exit.position().add(0.0D, 0.1D, 0.0D);
            if (!pigCarried) {
                pigCarried = pig.position().distanceTo(carried) < 1.0E-6D;
            }
            helper.assertTrue(
                    pigCarried
                            ? pig.position().distanceTo(carried) < 1.0E-6D
                            : pig.position().distanceTo(rest) < 1.0E-6D,
                    "After " + ticksTogether + " tick(s) the pig is at "
                            + pig.position() + ", neither resting in the "
                            + "ENTRY nor 0.1 above the EXIT" + where + ".");
            if (ticksTogether < WATCHED_TICKS) {
                return;
            }
            int sounds = fixture.teleportSoundsOn(exit);
            helper.assertTrue(
                    pigCarried && sounds == 1,
                    "After " + ticksTogether + " ticks in the ENTRY the pig "
                            + "was " + (pigCarried ? "" : "not ")
                            + "carried to the EXIT and " + sounds
                            + " teleport sound(s) played on the EXIT; a mob "
                            + "is carried after 60 ticks with one sound, the "
                            + "wormholes beside it never.");
            armUser.close();
            fixture.pass();
        }
    }

    /** A logged-in mock Tusk user above the test origin, looking along +Z, with the chunks ahead kept ticking. */
    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final StandType standType;
        private final ServerPlayer owner;
        private final EmbeddedChannel channel;
        private final BlockPos feet;
        private final GameTestChunkTickets tickets;
        private final Map<BlockPos, BlockState> replacedBlocks =
                new LinkedHashMap<>();
        private final List<Entity> extras = new ArrayList<>();
        private final List<Runnable> cleanups = new ArrayList<>();
        private StandPower power;
        private String stage = "starting the fixture";
        private boolean started;
        private long startedTick;
        private boolean closed;

        private Fixture(
                GameTestHelper helper,
                StandType standType,
                GameType gameMode,
                String name,
                int blocksAhead) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.tickets = new GameTestChunkTickets(helper);
            this.standType = standType;
            this.feet = helper.absolutePos(new BlockPos(1, 12, 1));
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            CommonListenerCookie cookie =
                    CommonListenerCookie.createInitial(profile, false);
            this.owner = new ServerPlayer(
                    level.getServer(),
                    level,
                    profile,
                    cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            this.channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            level.getServer().getPlayerList()
                    .placeNewPlayer(connection, owner, cookie);
            try {
                owner.setGameMode(gameMode);
                owner.moveTo(
                        feet.getX() + 0.5D,
                        feet.getY(),
                        feet.getZ() + 0.5D,
                        0.0F,
                        0.0F);
                owner.setYHeadRot(0.0F);
                owner.yHeadRotO = 0.0F;
                owner.setDeltaMovement(Vec3.ZERO);
                owner.setNoGravity(true);
                watchChunks(blocksAhead);
            }
            catch (RuntimeException failure) {
                close();
                throw failure;
            }
        }

        // The nails fly ahead of the user and the wormholes, portals and mobs of a scene stand within 8 blocks of it.
        private void watchChunks(int blocksAhead) {
            tickets.coverBox(
                    feet.getX() - 8,
                    feet.getZ() - 8,
                    feet.getX() + 8,
                    feet.getZ() + blocksAhead);
        }

        private void start() {
            power = PowerClass.STAND.attachGet(owner);
            StandPowerTransitions.Result inserted =
                    StandPowerTransitions.insert(
                            power, new StandInstance(standType));
            helper.assertTrue(
                    inserted.status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant " + standType.getId() + ": "
                            + inserted.status());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(
                    standType.summon(owner, power),
                    "Could not summon " + standType.getId() + ".");
            TuskAddon.nailState(owner).applySnapshot(
                    TuskNailState.MAX_NAILS, 0, false);
        }

        /** Runs the step every tick once the chunks tick; a failure or the deadline cleans up before it fails the test. */
        private void drive(Runnable step) {
            tickets.onEachTickWhenTicking(this::close, () -> {
                if (closed) {
                    return;
                }
                try {
                    if (!started) {
                        start();
                        started = true;
                        startedTick = helper.getTick();
                    }
                    if (helper.getTick() - startedTick > DEADLINE_TICK) {
                        helper.fail("Timed out while " + stage + ".");
                    }
                    step.run();
                }
                catch (RuntimeException failure) {
                    close();
                    throw failure;
                }
            });
        }

        private void pass() {
            close();
            helper.succeed();
        }

        private Ability ability(String abilityName) {
            Ability ability = power.getAbility(abilityName);
            helper.assertTrue(
                    ability != null,
                    "The production moveset has no " + abilityName + ".");
            return ability;
        }

        private ConditionCheck preflight(String abilityName) {
            AbilityConditionCheck preflight = power.updateAvailableMoves()
                    .getContextVariationContainer(ability(abilityName));
            helper.assertTrue(
                    preflight != null,
                    abilityName + " is not available to the user.");
            return preflight.conditionCheck;
        }

        private EntityActionInstance hold(String abilityName) {
            Ability ability = ability(abilityName);
            AbilityConditionCheck preflight = power.updateAvailableMoves()
                    .getContextVariationContainer(ability);
            helper.assertTrue(
                    preflight != null
                            && preflight.ability == ability
                            && AbilityInput.withConditionCheck(
                                    preflight, owner, InputMethod.HOLD),
                    "The production HOLD preflight refused " + abilityName
                            + ".");
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(
                        SHOT_KEY,
                        ability,
                        owner,
                        input,
                        InputMethod.HOLD,
                        0.0F,
                        BufferingState.clickCanBuffer(),
                        ability.abilityId);
            }
            finally {
                input.release();
            }
            EntityActionInstance action =
                    LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND);
            helper.assertTrue(
                    action != null && action.ability == ability,
                    "The production HOLD input did not start "
                            + abilityName + ".");
            return action;
        }

        private void click(String abilityName) {
            Ability ability = ability(abilityName);
            AbilityConditionCheck preflight = power.updateAvailableMoves()
                    .getContextVariationContainer(ability);
            helper.assertTrue(
                    preflight != null
                            && AbilityInput.withConditionCheck(
                                    preflight, owner, InputMethod.CLICK),
                    "The production CLICK preflight refused " + abilityName
                            + ": " + (preflight != null
                                    ? describe(preflight.conditionCheck)
                                    : "not available"));
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(
                    SHOT_KEY,
                    preflight.ability,
                    owner,
                    null,
                    InputMethod.CLICK,
                    0.0F,
                    buffering,
                    ability.abilityId);
            release();
            helper.assertTrue(
                    buffering.isActionSuccess,
                    "The production CLICK input did not start "
                            + abilityName + ".");
        }

        private <E extends Event> void listen(
                Class<E> eventType, Consumer<E> listener) {
            NeoForge.EVENT_BUS.addListener(
                    EventPriority.LOWEST, eventType, listener);
            cleanups.add(() -> NeoForge.EVENT_BUS.unregister(listener));
        }

        private boolean isHolding(EntityActionInstance action) {
            return LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND) == action
                    && action.getPhase() == ActionPhase.PERFORM;
        }

        private void release() {
            AbilityInput.keyRelease(SHOT_KEY, owner);
        }

        private List<? extends NailEntity> nails() {
            return level.getEntities(
                    TuskAddon.NAIL_ENTITY.get(),
                    nail -> nail.getOwner() == owner);
        }

        private NailEntity firstNail() {
            List<? extends NailEntity> nails = nails();
            return nails.isEmpty() ? null : nails.getFirst();
        }

        private List<? extends WormholeEntity> wormholes() {
            return level.getEntities(
                    TuskAddon.WORMHOLE_ENTITY.get(),
                    wormhole -> owner.getUUID().equals(
                            wormhole.getOwnerUuid()));
        }

        private WormholeEntity openWormhole(
                Vec3 position, Pig target) {
            WormholeEntity wormhole =
                    new WormholeEntity(owner, level, 200, target);
            wormhole.setPos(position);
            helper.assertTrue(
                    level.addFreshEntity(wormhole),
                    "Could not add the damaging wormhole.");
            return wormhole;
        }

        // A mob without AI makes no travel step: it stays where it is put, in the air too.
        private Pig spawnStillPig(Vec3 position) {
            Pig pig = EntityType.PIG.create(level);
            helper.assertTrue(pig != null, "Could not create the target.");
            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setPersistenceRequired();
            pig.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            extras.add(pig);
            helper.assertTrue(
                    level.addFreshEntity(pig),
                    "Could not add the target.");
            return pig;
        }

        private WormholeArmEntity openArm(Vec3 position) {
            WormholeEntity source =
                    new WormholeEntity(owner, level, 200, null);
            source.setPos(position);
            helper.assertTrue(
                    level.addFreshEntity(source),
                    "Could not add the wormhole the arm opens from.");
            TuskPortalOperations operations =
                    TuskPortalOperations.get(level.getServer());
            helper.assertTrue(
                    operations.createRemoteArm(owner),
                    "Production remote-arm creation failed.");
            WormholeArmEntity arm = operations.resolveArmForShot(owner);
            helper.assertTrue(
                    arm != null,
                    "The remote arm did not resolve for a shot.");
            return arm;
        }

        private void setBlock(BlockPos pos, BlockState state) {
            if (replacedBlocks.putIfAbsent(
                    pos.immutable(), level.getBlockState(pos)) == null) {
                level.setBlockAndUpdate(pos, state);
            }
        }

        private void forgetSentPackets() {
            channel.flushOutbound();
            channel.outboundMessages().clear();
        }

        private int teleportSoundsOn(Entity entity) {
            channel.flushOutbound();
            int count = 0;
            for (Object message : channel.outboundMessages()) {
                if (message instanceof ClientboundSoundEntityPacket sound
                        && sound.getSound().value()
                                == TuskAddon.WORMHOLE_TELEPORT.get()
                        && sound.getId() == entity.getId()) {
                    count++;
                }
            }
            return count;
        }

        // A sound packet stores x and z as a float of eighths: far from the origin they round to about half a block.
        private String shotSoundFault() {
            channel.flushOutbound();
            int atUser = 0;
            List<String> elsewhere = new ArrayList<>();
            for (Object message : channel.outboundMessages()) {
                if (message instanceof ClientboundSoundPacket sound
                        && sound.getSound().value()
                                == TuskAddon.NAIL_SHOT_SOUND.get()) {
                    double horizontal = Math.hypot(
                            sound.getX() - owner.getX(),
                            sound.getZ() - owner.getZ());
                    if (horizontal < 1.0D && Math.abs(
                            sound.getY() - owner.getEyeY()) < 0.2D) {
                        atUser++;
                    } else {
                        elsewhere.add("at " + sound.getX() + ", "
                                + sound.getY() + ", " + sound.getZ());
                    }
                } else if (message
                        instanceof ClientboundSoundEntityPacket sound
                        && sound.getSound().value()
                                == TuskAddon.NAIL_SHOT_SOUND.get()) {
                    elsewhere.add("on entity #" + sound.getId());
                }
            }
            return atUser == 1 && elsewhere.isEmpty()
                    ? null
                    : "its sound played " + atUser + " time(s) at the "
                            + "user's eyes and " + elsewhere + " elsewhere";
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            cleanups.forEach(Runnable::run);
            release();
            TuskPortalOperations.get(level.getServer())
                    .closeOwned(owner.getUUID());
            nails().forEach(Entity::discard);
            wormholes().forEach(Entity::discard);
            extras.forEach(Entity::discard);
            replacedBlocks.forEach(level::setBlockAndUpdate);
            if (power != null && power.isSummoned()) {
                standType.forceUnsummon(owner, power);
            }
            owner.connection.disconnect(Component.literal(
                    "Tusk nail shot GameTest complete"));
            tickets.release();
        }
    }
}
