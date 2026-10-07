package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismSpaceRipperStingyEyesAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * 1.16 OwnerBoundProjectileEntity.canUpdate: "isBoundToOwner() && getOwner() != null ? getOwner().canUpdate() :
 * super.canUpdate()". A Space Ripper Stingy Eyes beam bound to its owner is stopped in time exactly when that owner is.
 * The beams of an owner who moves in stopped time keep extending, are detached by the shot's last tick after that
 * tick's move, and only then stop like any other projectile. The beams of a stopped owner wait with his paused shot.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismEyeBeamTimeStopGameTests {
    private static final float TICK_COST = 20.0F;
    private static final double EPS = 1.0E-3D;
    private static final double DEG_TO_RAD = Math.PI / 180.0D;
    private static final int MOVES_BEFORE_STOP = 5;
    private static final int HOLD_TICKS = 5;
    private static final int TIMEOUT = 500;
    private static int nextStopId = -7_640_000;
    // One scene at a time: a stop covers its whole chunk column, and two cells of the batch can share a chunk.
    // The other tests of the batch put and remove their stops within their first tick, before these stops begin.
    private static Fixture running;

    private VampirismEyeBeamTimeStopGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = GameTestBatches.TIME_STOP, timeoutTicks = TIMEOUT)
    public static void shotFiredInStoppedTimeByAMovingOwnerExtendsBoundThenStopsDetached(GameTestHelper helper) {
        start(helper, Scenario.FIRED_INSIDE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = GameTestBatches.TIME_STOP, timeoutTicks = TIMEOUT)
    public static void shotBegunBeforeTheStopKeepsExtendingBoundToItsMovingOwner(GameTestHelper helper) {
        start(helper, Scenario.STARTED_BEFORE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = GameTestBatches.TIME_STOP, timeoutTicks = TIMEOUT)
    public static void shotThatRunsDryInStoppedTimeDetachesAfterThatTicksMove(GameTestHelper helper) {
        start(helper, Scenario.RUNS_DRY_INSIDE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = GameTestBatches.TIME_STOP, timeoutTicks = TIMEOUT)
    public static void stoppedOwnersShotAndBoundBeamsWaitForTimeToResume(GameTestHelper helper) {
        start(helper, Scenario.OWNER_STOPPED);
    }

    /**
     * The rule follows the owner, not the beam's own chunk: a bound beam whose tip left the stopped chunks stops with
     * its stopped owner, and a detached one is an ordinary projectile again.
     */
    @GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
    public static void boundBeamOutsideTheStoppedChunkStopsWithItsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeStopState timeStops = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(template);
        float speed = 0.5F + level.getDifficulty().getId() * 0.25F;
        // On the chunk's east edge, looking east: the first move takes the tip into the next chunk.
        Vec3 ownerPos = new Vec3(chunk.getMaxBlockX() + 0.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 8.5D);
        AABB space = new AABB(ownerPos.x - 1.0D, ownerPos.y - 1.0D, ownerPos.z - 1.0D,
                ownerPos.x + 8.0D, ownerPos.y + 3.0D, ownerPos.z + 1.0D);
        helper.assertTrue(space.maxY < level.getMaxBuildHeight() && level.getEntities((Entity) null, space).isEmpty()
                        && BlockPos.betweenClosedStream(space.deflate(0.5D)).allMatch(level::isEmptyBlock),
                "SRSE-TIMESTOP premise: scene is not empty air");
        FakePlayer owner = fakeOwner(level, ownerPos, -90.0F, 0.0F);
        SpaceRipperStingyEyesEntity beam = new SpaceRipperStingyEyesEntity(level, owner, true);
        int stop = nextStopId--;
        try {
            helper.assertTrue(level.addFreshEntity(beam), "SRSE-TIMESTOP premise: beam added");
            level.tickNonPassenger(beam);
            level.tickNonPassenger(beam);
            helper.assertTrue(beam.isBoundToOwner() && beam.tickCount == 2 && Math.abs(beam.getLength() - 2 * speed) < EPS
                            && !new ChunkPos(beam.blockPosition()).equals(chunk),
                    "SRSE-TIMESTOP premise: two moves take the bound tip into the next chunk: age=" + beam.tickCount
                            + " length=" + beam.getLength() + " tip=" + beam.position());
            helper.assertTrue(timeStops.tryPutInstance(new TimeStopState.Instance(stop, 200, 200, chunk, 1, -1,
                            "srse_bound_beam_gametest")) && timeStops.shouldFreeze(owner) && !timeStops.isTimeStopped(beam),
                    "SRSE-TIMESTOP premise: the owner is stopped in time and the tip is outside the stopped chunk");

            Vec3 tip = beam.position();
            level.tickNonPassenger(beam);
            level.tickNonPassenger(beam);
            helper.assertTrue(timeStops.shouldFreeze(beam) && beam.tickCount == 2 && Math.abs(beam.getLength() - 2 * speed) < EPS
                            && beam.position().equals(tip),
                    "A bound eye beam outside the stopped chunk kept extending while its owner was stopped in time: stopped="
                            + timeStops.shouldFreeze(beam) + " age=" + beam.tickCount + " length=" + beam.getLength()
                            + " tip=" + beam.position() + ", 1.16: stopped=true age=2 length=" + 2 * speed + " tip=" + tip);

            owner.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200, 0, false, false, true));
            level.tickNonPassenger(beam);
            helper.assertTrue(!timeStops.shouldFreeze(beam) && beam.isBoundToOwner() && beam.tickCount == 3
                            && Math.abs(beam.getLength() - 3 * speed) < EPS,
                    "A bound eye beam did not extend with an owner who moves in stopped time: stopped="
                            + timeStops.shouldFreeze(beam) + " age=" + beam.tickCount + " length=" + beam.getLength());
            owner.removeEffect(ModStatusEffects.TIME_STOP);

            beam.detach();
            tip = beam.position();
            level.tickNonPassenger(beam);
            helper.assertTrue(timeStops.shouldFreeze(owner) && !beam.isBoundToOwner() && !timeStops.shouldFreeze(beam)
                            && beam.tickCount == 4 && beam.position().distanceTo(tip.add(speed, 0.0D, 0.0D)) < EPS,
                    "A detached eye beam outside the stopped chunk did not fly on: stopped=" + timeStops.shouldFreeze(beam)
                            + " age=" + beam.tickCount + " tip=" + beam.position() + " from=" + tip);
        }
        finally {
            timeStops.removeInstance(stop);
            beam.discard();
            owner.discard();
        }
        helper.succeed();
    }

    /**
     * A beam that is stopped in time makes no move for the shot's last tick to wait for: it is detached at once, where
     * its owner's eyes are, and does not go back to him when time resumes.
     */
    @GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
    public static void beamStoppedInTimeIsDetachedAtOnceWhenItsShotEnds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeStopState timeStops = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(template);
        float speed = 0.5F + level.getDifficulty().getId() * 0.25F;
        Vec3 ownerPos = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 8.5D);
        AABB space = new AABB(ownerPos.x - 1.0D, ownerPos.y - 1.0D, ownerPos.z - 1.0D,
                ownerPos.x + 5.0D, ownerPos.y + 6.0D, ownerPos.z + 4.0D);
        helper.assertTrue(space.maxY < level.getMaxBuildHeight() && level.getEntities((Entity) null, space).isEmpty()
                        && BlockPos.betweenClosedStream(space.deflate(0.5D)).allMatch(level::isEmptyBlock),
                "SRSE-TIMESTOP premise: scene is not empty air");
        FakePlayer owner = fakeOwner(level, ownerPos, 0.0F, -90.0F);
        SpaceRipperStingyEyesEntity moving = new SpaceRipperStingyEyesEntity(level, owner, true);
        SpaceRipperStingyEyesEntity stopped = new SpaceRipperStingyEyesEntity(level, owner, false);
        int stop = nextStopId--;
        try {
            // Normal time: the detach waits for the move of this tick.
            helper.assertTrue(level.addFreshEntity(moving), "SRSE-TIMESTOP premise: beam added");
            moving.detachAfterThisTicksMove();
            helper.assertTrue(moving.isBoundToOwner() && moving.getLength() == 0.0F,
                    "A beam that still moves this tick was detached before that move");
            level.tickNonPassenger(moving);
            helper.assertTrue(!moving.isBoundToOwner() && Math.abs(moving.getLength() - speed) < EPS
                            && moving.getOriginPoint(1.0F).distanceTo(eyeOrigin(owner.getEyePosition(), 0.0F, -90.0F, true)) < EPS,
                    "A beam that still moves this tick was not detached after that move: bound=" + moving.isBoundToOwner()
                            + " length=" + moving.getLength() + " origin=" + moving.getOriginPoint(1.0F));

            helper.assertTrue(timeStops.tryPutInstance(new TimeStopState.Instance(stop, 200, 200, chunk, 1, -1,
                            "srse_stopped_beam_gametest")) && timeStops.shouldFreeze(owner),
                    "SRSE-TIMESTOP premise: the owner is stopped in time");
            helper.assertTrue(level.addFreshEntity(stopped), "SRSE-TIMESTOP premise: beam added in stopped time");
            level.tickNonPassenger(stopped);
            helper.assertTrue(stopped.isBoundToOwner() && stopped.tickCount == 0 && timeStops.shouldFreeze(stopped),
                    "SRSE-TIMESTOP premise: a stopped owner's bound beam is stopped in time: age=" + stopped.tickCount);
            Vec3 origin = eyeOrigin(owner.getEyePosition(), 0.0F, -90.0F, false);
            stopped.detachAfterThisTicksMove();
            helper.assertTrue(!stopped.isBoundToOwner() && stopped.getLength() == 0.0F
                            && stopped.getOriginPoint(1.0F).distanceTo(origin) < EPS,
                    "A beam stopped in time stayed bound to its owner when its shot ended: bound=" + stopped.isBoundToOwner()
                            + " length=" + stopped.getLength() + " origin=" + stopped.getOriginPoint(1.0F)
                            + ", 1.16 detach(): bound=false length=0 origin=" + origin);

            // Time resumes after the owner walked off and turned: neither end of the beam goes to him.
            timeStops.removeInstance(stop);
            owner.moveTo(ownerPos.x + 3.0D, ownerPos.y, ownerPos.z + 2.0D, 90.0F, 0.0F);
            level.tickNonPassenger(stopped);
            helper.assertTrue(!stopped.isBoundToOwner() && stopped.tickCount == 1
                            && stopped.getOriginPoint(1.0F).distanceTo(origin) < EPS && stopped.position().distanceTo(origin) < EPS,
                    "A beam detached in stopped time went back to its owner when time resumed: bound=" + stopped.isBoundToOwner()
                            + " age=" + stopped.tickCount + " origin=" + stopped.getOriginPoint(1.0F) + " tip=" + stopped.position()
                            + ", expected both at " + origin);
        }
        finally {
            timeStops.removeInstance(stop);
            moving.discard();
            stopped.discard();
            owner.discard();
        }
        helper.succeed();
    }

    private static FakePlayer fakeOwner(ServerLevel level, Vec3 pos, float yRot, float xRot) {
        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "EyeBeamStopOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        owner.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
        level.addNewPlayer(owner);
        return owner;
    }

    // 1.16 DamagingEntity.getPos with the eye beam's offsets: 0.09375 to a side, 0.2 down, 0.2 up along the pitch.
    private static Vec3 eyeOrigin(Vec3 eye, float yRot, float xRot, boolean rightEye) {
        Vec3 offset = new Vec3(rightEye ? -0.09375D : 0.09375D, -0.2D, 0.0D)
                .add(new Vec3(0.0D, 0.2D, 0.0D).xRot((float) (-xRot * DEG_TO_RAD)))
                .yRot((float) (-yRot * DEG_TO_RAD));
        return eye.add(offset);
    }

    private static void start(GameTestHelper helper, Scenario scenario) {
        Fixture fixture = new Fixture(helper, scenario);
        helper.testInfo.addListener(fixture);
        helper.runAfterDelay(1, () -> fixture.poll());
    }

    private enum Scenario {
        FIRED_INSIDE("shot fired in stopped time by an owner who moves", true, true, false),
        STARTED_BEFORE("shot begun before the stop by an owner who moves", false, true, false),
        RUNS_DRY_INSIDE("shot that runs out of blood in stopped time", true, true, true),
        OWNER_STOPPED("shot of an owner who is stopped in time", false, false, false);

        final String text;
        final boolean stopBeforeRelease;
        final boolean ownerMoves;
        final boolean exhausted;

        Scenario(String text, boolean stopBeforeRelease, boolean ownerMoves, boolean exhausted) {
            this.text = text;
            this.stopBeforeRelease = stopBeforeRelease;
            this.ownerMoves = ownerMoves;
            this.exhausted = exhausted;
        }
    }

    // One beam tick that ran: its state after the tick, and whether time was stopped or had resumed.
    private record Frame(int age, boolean bound, float length, Vec3 tip, Vec3 origin, boolean stopped, boolean resumed) {}

    // The beam between two level ticks of the stop.
    private record Held(int age, boolean bound, float length, Vec3 tip, Vec3 origin) {}

    private record BeamResult(int movesBeforeStop, int ticksInStop, int boundMoves, int detachAge, boolean detachedAtEye,
            boolean heldInStop, boolean fliesStraightOn) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 40;
        // Release cost, three paid ticks, and a rest the fourth tick cannot pay with.
        private static final float EXHAUSTED_BLOOD = TICK_COST + 3 * TICK_COST + 10.0F;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Scenario scenario;
        private final TimeStopState timeStops;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Map<UUID, List<Frame>> frames = new LinkedHashMap<>();
        private final Map<UUID, List<Held>> held = new LinkedHashMap<>();
        private final Map<UUID, Boolean> rightEye = new LinkedHashMap<>();
        private final List<SpaceRipperStingyEyesEntity> beams = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Float> idleDeltas = new ArrayList<>();
        private final List<Float> shotDeltas = new ArrayList<>();
        private Player user;
        private Player stopper;
        private Vec3 userPosition;
        private Vec3 shotEye;
        private ChunkPos chunk;
        private PlayerPower power;
        private VampirismData vampire;
        private Ability ability;
        private EntityActionInstance action;
        private RuntimeException observerFailure;
        private float speed;
        private float bloodAtPre;
        private float bloodBeforeRelease;
        private float bloodAfterRelease;
        private float bloodAtEnd;
        private float bloodAtStopStart;
        private float bloodAtStopEnd;
        private float phaseTickAtStopStart;
        private float phaseTickAtStopEnd;
        private int userTicks;
        private int poweredAt = -1;
        private int pressedAt;
        private int beamsAtRelease = -1;
        private int stopId;
        private long stopStartedAt;
        private boolean firingAtStopEnd;
        private boolean bloodSampled;
        private boolean pressed;
        private boolean released;
        private boolean ended;
        private boolean stopStarted;
        private boolean stopActive;
        private boolean resumed;
        private boolean ownerMoved;
        private boolean closed;

        Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.scenario = scenario;
            this.timeStops = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        }

        private void premise(boolean value, String message) {
            helper.assertTrue(value, "SRSE-TIMESTOP premise: " + message);
        }

        private void log(String message) {
            JojoMod.LOGGER.info("SRSE-TIMESTOP {} {}", scenario, message);
        }

        private float blood() {
            return VampirismState.get(user).blood().current();
        }

        private void setUp() {
            premise(level.getDifficulty() != Difficulty.PEACEFUL, "non-Peaceful difficulty");
            speed = 0.5F + level.getDifficulty().getId() * 0.25F;
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX() + 8;
            int z = chunk.getMinBlockZ() + 8;
            int y = template.getY() + 48;
            // The owner looks straight down: the moves and the flight stay in the test chunk, in open air under the owner.
            AABB column = new AABB(x - 1, y - 36, z - 1, x + 2, y + 5, z + 2);
            premise(column.minY > template.getY() + 8 && column.maxY < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, column).isEmpty()
                    && BlockPos.betweenClosedStream(column.deflate(0.5D)).allMatch(level::isEmptyBlock),
                    "the column under the owner is empty air");
            // Wide enough to keep the sky light at the owner's eyes below a vampire's sun threshold.
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x - 5, y + 3, z - 5), new BlockPos(x + 5, y + 3, z + 5))) {
                premise(level.isEmptyBlock(pos), "shade cell is free");
                original.put(pos.immutable(), level.getBlockState(pos));
                premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "shade block placed");
            }
            userPosition = new Vec3(x + 0.5D, y, z + 0.5D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0.0F, 90.0F);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            premise(level.addFreshEntity(user), "owner added");
            registerObservers();
        }

        private boolean shadeReady() {
            BlockPos eye = BlockPos.containing(user.getEyePosition());
            BlockPos sun = BlockPos.containing(user.getX(), Math.round(user.getY(1.0D)), user.getZ());
            return !level.canSeeSky(eye) && !level.canSeeSky(sun) && level.getBrightness(LightLayer.SKY, eye) < 12;
        }

        private void grantPower() {
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            vampire = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            vampire.setVampireFullPower(true, user);
            var bloodState = VampirismState.get(user).blood();
            bloodState.setCurrent(bloodState.max());
            vampire.setBloodLevel(bloodState.current());
            premise(bloodState.current() >= 600.0F, "blood for a whole shot, max=" + bloodState.max());
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility("vampirism_space_ripper_stingy_eyes");
            premise(ability instanceof VampirismSpaceRipperStingyEyesAbility, "registered ability");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            poweredAt = userTicks;
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level
                        || !(event.getEntity() instanceof SpaceRipperStingyEyesEntity beam) || beam.getOwner() != user) {
                    return;
                }
                beams.add(beam);
                frames.put(beam.getUUID(), new ArrayList<>());
                held.put(beam.getUUID(), new ArrayList<>());
                rightEye.put(beam.getUUID(), beam.saveWithoutId(new CompoundTag()).getBoolean("IsRightEye"));
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user && power != null) {
                    bloodAtPre = blood();
                    bloodSampled = true;
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                Entity entity = event.getEntity();
                if (entity == user) {
                    userTicks++;
                    if (!bloodSampled) {
                        return;
                    }
                    bloodSampled = false;
                    float delta = bloodAtPre - blood();
                    if (!pressed) {
                        idleDeltas.add(delta);
                    }
                    else if (released && !ended) {
                        shotDeltas.add(delta);
                        if (LivingComponentAction.getCurEntityAction(user) != action) {
                            ended = true;
                            bloodAtEnd = blood();
                        }
                    }
                    return;
                }
                List<Frame> beamFrames = frames.get(entity.getUUID());
                if (beamFrames != null && entity instanceof SpaceRipperStingyEyesEntity beam) {
                    beamFrames.add(new Frame(beam.tickCount, beam.isBoundToOwner(), beam.getLength(), beam.position(),
                            beam.getOriginPoint(1.0F), stopActive, resumed));
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
            }
        }

        private void press() {
            premise(shadeReady() && !user.isOnFire() && vampire.isVampireAtFullPower()
                    && level.isPositionEntityTicking(user.blockPosition()), "Survival vampire in the shade");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "HOLD admitted");
            pressed = true;
            pressedAt = userTicks;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD, 0, BufferingState.clickOnly(),
                    ability.getAbilityId());
            premise(input != null && input.action instanceof VampirismSpaceRipperStingyEyesAbility.SpaceRipperInstance,
                    "the registered input started the action");
            action = (EntityActionInstance) input.action;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.WINDUP,
                    "charging phase");
        }

        private void release() {
            premise(beams.isEmpty(), "no beam before the release");
            if (scenario.exhausted) {
                VampirismState.get(user).blood().setCurrent(EXHAUSTED_BLOOD);
                vampire.setBloodLevel(EXHAUSTED_BLOOD);
            }
            shotEye = user.getEyePosition();
            bloodBeforeRelease = blood();
            released = true;
            AbilityInput.keyRelease(KEY, user);
            bloodAfterRelease = blood();
            beamsAtRelease = beams.size();
        }

        // A stop that lasts through level ticks needs a living user whose Stand has its time stop unlocked.
        private void startStop() {
            if (scenario.ownerMoves) {
                premise(user.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 600, 0, false, false, true)),
                        "the owner may move in stopped time");
            }
            stopper = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            stopper.setNoGravity(true);
            stopper.moveTo(userPosition.x - 4.0D, userPosition.y, userPosition.z - 4.0D, 0.0F, 0.0F);
            premise(level.addFreshEntity(stopper), "time stopper added");
            StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
            StandPower stand = PowerClass.STAND.attachGet(stopper);
            premise(standType != null && StandPowerTransitions.insert(stand, new StandInstance(standType)).status()
                    == StandPowerTransitions.Status.APPLIED, "the time stopper has The World");
            stand.getCurTypeData()._setSkillUnlocked(TimeStopLearning.TIME_STOP, true, false);
            stand.setResolveLevel(stand.getMaxResolveLevel());
            premise(stand.isAbilityUnlocked(TimeStopLearning.TIME_STOP), "the time stopper's time stop is unlocked");
            stopId = nextStopId--;
            premise(timeStops.tryPutInstance(new TimeStopState.Instance(stopId, 600, 600, chunk, 1, stopper.getId(),
                    "srse_time_stop_gametest", Optional.empty(), Optional.empty(), stopper.getId(), stopper.getId(),
                    false, false, 0.0F, 0, false)), "time stopped");
            stopStarted = true;
            stopActive = true;
            stopStartedAt = helper.getTick();
            bloodAtStopStart = blood();
            phaseTickAtStopStart = action.getPhaseTick();
            premise(timeStops.isTimeStopped(user) && timeStops.shouldFreeze(stopper)
                    && timeStops.shouldFreeze(user) == !scenario.ownerMoves
                    && beams.stream().allMatch(timeStops::isTimeStopped), "the owner and the beams are in the stopped chunk");
        }

        private void holdOrResume() {
            premise(timeStops.getInstance(stopId).isPresent() && timeStops.isTimeStopped(chunk), "time is still stopped");
            premise(beams.size() == 2, "both beams were added on the release, beams=" + beams.size());
            if (scenario.ownerMoves && !ended) {
                return;
            }
            for (SpaceRipperStingyEyesEntity beam : beams) {
                held.get(beam.getUUID()).add(new Held(beam.tickCount, beam.isBoundToOwner(), beam.getLength(), beam.position(),
                        beam.getOriginPoint(1.0F)));
            }
            if (scenario.ownerMoves && !ownerMoved) {
                // The owner walks off and turns while his detached beams hang in stopped time.
                ownerMoved = true;
                user.moveTo(userPosition.x + 1.0D, userPosition.y, userPosition.z + 1.0D, 90.0F, 0.0F);
                user.setYHeadRot(90.0F);
                user.yBodyRot = 90.0F;
            }
            if (held.get(beams.get(0).getUUID()).size() >= HOLD_TICKS) {
                resume();
            }
        }

        private void resume() {
            bloodAtStopEnd = blood();
            phaseTickAtStopEnd = action.getPhaseTick();
            firingAtStopEnd = LivingComponentAction.getCurEntityAction(user) == action && !action.isOver()
                    && action.getPhase() == ActionPhase.PERFORM;
            timeStops.removeInstance(stopId);
            stopActive = false;
            resumed = true;
            stopper.discard();
            user.removeEffect(ModStatusEffects.TIME_STOP);
            premise(!timeStops.isTimeStopped(chunk), "time resumed");
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                premise(helper.getTick() < TIMEOUT - 20, "watchdog: waiting=" + (running != this) + " userTicks=" + userTicks
                        + " powered=" + (power != null) + " pressed=" + pressed + " released=" + released
                        + " stopStarted=" + stopStarted + " stopActive=" + stopActive + " ended=" + ended
                        + " beams=" + beams.size() + " shotDeltas=" + shotDeltas + " frames=" + frames.values());
                if (running == null) {
                    running = this;
                }
                if (running != this) {
                    helper.runAfterDelay(1, this::poll);
                    return;
                }
                if (user == null) {
                    setUp();
                    helper.runAfterDelay(1, this::poll);
                    return;
                }
                premise(user.isAlive() && !user.isOnFire(), "the owner is unharmed");
                premise(ownerMoved || user.position().distanceToSqr(userPosition) < 1.0E-10D && user.getXRot() == 90.0F,
                        "the owner stays put, looking down, until his beams are detached");
                if (power == null && userTicks >= 2 && shadeReady()) grantPower();
                if (!pressed && poweredAt >= 0 && userTicks >= poweredAt + 4) press();
                if (!released) {
                    if (pressed && action.getPhase() == ActionPhase.WINDUP && action.getPhaseTick() >= 20
                            && userTicks - pressedAt >= 22) {
                        if (scenario.stopBeforeRelease && !stopStarted) {
                            startStop();
                        }
                        else if (!scenario.stopBeforeRelease || helper.getTick() >= stopStartedAt + 2) {
                            release();
                        }
                    }
                }
                else if (!stopStarted) {
                    if (beams.size() == 2 && beams.stream().allMatch(
                            beam -> frames.get(beam.getUUID()).size() >= MOVES_BEFORE_STOP)) {
                        startStop();
                    }
                }
                else if (stopActive) {
                    holdOrResume();
                }
                else if (ended && beams.size() == 2 && beams.stream().allMatch(this::flewOnAfterResume)) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private boolean flewOnAfterResume(SpaceRipperStingyEyesEntity beam) {
            return frames.get(beam.getUUID()).stream().filter(frame -> frame.resumed() && !frame.bound()).count() >= 3;
        }

        private BeamResult analyse(SpaceRipperStingyEyesEntity beam) {
            List<Frame> beamFrames = frames.get(beam.getUUID());
            int before = (int) beamFrames.stream().filter(frame -> !frame.stopped() && !frame.resumed()).count();
            int inStop = (int) beamFrames.stream().filter(Frame::stopped).count();
            int detachIndex = 0;
            while (detachIndex < beamFrames.size() && beamFrames.get(detachIndex).bound()) detachIndex++;
            if (detachIndex + 2 >= beamFrames.size()) {
                return new BeamResult(before, inStop, -1, -1, false, false, false);
            }
            Frame detach = beamFrames.get(detachIndex);
            int moves = Math.round(detach.length() / speed);
            // The owner shot straight down and did not move before his beams were detached.
            Vec3 eye = eyeOrigin(shotEye, 0.0F, 90.0F, rightEye.get(beam.getUUID()));
            Vec3 down = new Vec3(0.0D, -1.0D, 0.0D);
            boolean atEye = Math.abs(detach.length() - moves * speed) < EPS && detach.origin().distanceTo(eye) < EPS
                    && detach.tip().distanceTo(eye.add(down.scale(detach.length()))) < EPS;
            List<Held> beamHeld = held.get(beam.getUUID());
            boolean heldInStop = beamHeld.size() == HOLD_TICKS;
            for (Held sample : beamHeld) {
                if (scenario.ownerMoves) {
                    // Detached: stopped like any projectile, where the last bound move left it.
                    heldInStop &= !sample.bound() && sample.age() == detach.age()
                            && sample.tip().distanceTo(detach.tip()) < EPS && sample.origin().distanceTo(detach.origin()) < EPS;
                }
                else {
                    // Bound to a stopped owner: stopped with him.
                    heldInStop &= sample.bound() && sample.age() == MOVES_BEFORE_STOP
                            && Math.abs(sample.length() - MOVES_BEFORE_STOP * speed) < EPS
                            && sample.tip().distanceTo(eye.add(down.scale(MOVES_BEFORE_STOP * (double) speed))) < EPS;
                }
            }
            boolean fliesStraightOn = true;
            for (int i = detachIndex + 1; i < beamFrames.size(); i++) {
                Frame frame = beamFrames.get(i);
                Vec3 flown = down.scale((double) speed * (i - detachIndex));
                fliesStraightOn &= !frame.bound() && frame.age() == detach.age() + i - detachIndex
                        && frame.tip().distanceTo(detach.tip().add(flown)) < EPS
                        && frame.origin().distanceTo(detach.origin().add(flown)) < EPS;
            }
            return new BeamResult(before, inStop, moves, detach.age(), atEye, heldInStop, fliesStraightOn);
        }

        private void validate() {
            premise(idleDeltas.size() >= 2 && idleDeltas.stream().allMatch(delta -> Math.abs(delta - idleDeltas.get(0)) < 1.0E-3F)
                    && idleDeltas.get(0) >= 0.0F && idleDeltas.get(0) < 1.0F, "steady passive blood loss, deltas=" + idleDeltas);
            int paidTicks = (int) shotDeltas.stream().filter(delta -> delta > TICK_COST - 0.01F && delta < TICK_COST + 1.0F).count();
            float releaseCost = bloodBeforeRelease - bloodAfterRelease;
            float shotCost = releaseCost + paidTicks * TICK_COST;
            float shotTicksInStop = phaseTickAtStopEnd - phaseTickAtStopStart;
            float bloodPaidInStop = bloodAtStopStart - bloodAtStopEnd;

            // The index of the tick that ended the shot: 20 for a full shot, 3 when the fourth tick cannot pay.
            int lastTick = scenario.exhausted ? 3 : 20;
            int paid = scenario.exhausted ? lastTick : lastTick + 1;
            int movesBeforeStop = scenario.stopBeforeRelease ? 0 : MOVES_BEFORE_STOP;
            BeamResult donor = new BeamResult(movesBeforeStop, scenario.ownerMoves ? lastTick + 1 - movesBeforeStop : 0,
                    lastTick + 1, lastTick + 1, true, true, true);
            List<BeamResult> results = beams.stream().map(this::analyse).toList();

            String measured = "beamsAtRelease=" + beamsAtRelease + " beams=" + results + " releaseCost=" + releaseCost
                    + " paidTicks=" + paidTicks + " shotTicks=" + shotDeltas.size() + " shotCost=" + shotCost
                    + (scenario.exhausted ? " bloodAtEnd=" + bloodAtEnd : "")
                    + (scenario.ownerMoves ? "" : " firingAtStopEnd=" + firingAtStopEnd + " shotTicksInStop=" + shotTicksInStop
                            + " bloodPaidInStop=" + bloodPaidInStop);
            String expected = "beamsAtRelease=2 beams=" + List.of(donor, donor) + " releaseCost=20 paidTicks=" + paid
                    + " shotTicks=" + (lastTick + 1) + " shotCost=" + (20 + paid * 20)
                    + (scenario.exhausted ? " bloodAtEnd=0" : "")
                    + (scenario.ownerMoves ? "" : " firingAtStopEnd=true shotTicksInStop=0.0 bloodPaidInStop=0.0");
            log("measured " + measured + " shotDeltas=" + shotDeltas + " frames=" + frames.values() + " held=" + held.values());
            boolean matches = beamsAtRelease == 2 && results.size() == 2 && results.stream().allMatch(donor::equals)
                    && Math.abs(releaseCost - TICK_COST) < 0.01F && paidTicks == paid && shotDeltas.size() == lastTick + 1
                    && (!scenario.exhausted || bloodAtEnd == 0.0F)
                    && (scenario.ownerMoves || firingAtStopEnd && shotTicksInStop == 0.0F && Math.abs(bloodPaidInStop) < 0.01F);
            helper.assertTrue(matches, "Space Ripper Stingy Eyes " + scenario.text + ": " + measured + ", 1.16: " + expected);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (stopStarted) timeStops.removeInstance(stopId);
                if (user != null) AbilityInput.keyRelease(KEY, user);
                for (SpaceRipperStingyEyesEntity beam : beams) if (!beam.isRemoved()) beam.discard();
                if (stopper != null) stopper.discard();
                if (user != null) user.discard();
            }
            finally {
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                if (running == this) running = null;
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
