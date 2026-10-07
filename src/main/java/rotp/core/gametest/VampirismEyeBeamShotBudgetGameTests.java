package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismSpaceRipperStingyEyesAbility;
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
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Space Ripper Stingy Eyes, one charged shot in Survival.
 * PowerBaseImpl.stopHeldAction -> performAction -> NonStandAction.onPerform: perform() starts the continuous action,
 * whose onStart adds both beams, and then consumeEnergy takes the action's energy cost (20) once.
 * ContinuousActionInstance.tick: playerTick() runs before tick++, so VampirismSpaceRipperStingyEyes.Instance.playerTick
 * runs with tick = 0..20 (TICK_DURATION 20): 21 ticks, each taking 20; the tick whose index is 20 ends the action with
 * a cooldown of 50 * 20 / 20. A tick that cannot pay empties the blood (setEnergy(0)), detaches the beams and ends
 * with a cooldown of 50 * tick / 20.
 * The beams tick in the level before the player ticks: when playerTick runs with index t they have made t + 1 bound
 * moves, and SpaceRipperStingyEyesEntity.tick detaches them itself after the move of tickCount 21.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismEyeBeamShotBudgetGameTests {
    private static final float TICK_COST = 20.0F;

    private VampirismEyeBeamShotBudgetGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "srse_shot_budget_full", timeoutTicks = 200)
    public static void fullShotPaysOnReleaseThenTwentyOneTicksAndBindsTheBeamsForTwentyOneMoves(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "srse_shot_budget_exhausted", timeoutTicks = 200)
    public static void shotThatCannotPayEmptiesTheBloodAndDetachesAfterThatTicksMove(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean exhausted) {
        Fixture fixture = new Fixture(helper, exhausted);
        helper.testInfo.addListener(fixture);
        try {
            fixture.setUp();
            helper.runAfterDelay(1, fixture::poll);
        }
        catch (RuntimeException | Error error) {
            fixture.close();
            throw error;
        }
    }

    private record Frame(int age, boolean bound, float length, Vec3 tip, Vec3 origin) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 39;
        // Release cost, three paid ticks, and a rest the fourth tick cannot pay with.
        private static final float EXHAUSTED_BLOOD = TICK_COST + 3 * TICK_COST + 10.0F;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean exhausted;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Map<UUID, List<Frame>> frames = new LinkedHashMap<>();
        private final List<SpaceRipperStingyEyesEntity> beams = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Float> idleDeltas = new ArrayList<>();
        private final List<Float> chargeDeltas = new ArrayList<>();
        private final List<Float> shotDeltas = new ArrayList<>();
        private Player user;
        private Vec3 userPosition;
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
        private int userTicks;
        private int poweredAt = -1;
        private int pressedAt;
        private int beamsAtRelease = -1;
        private int cooldownAtEnd = -1;
        private boolean bloodSampled;
        private boolean pressed;
        private boolean released;
        private boolean ended;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean exhausted) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.exhausted = exhausted;
        }

        private void premise(boolean value, String message) {
            helper.assertTrue(value, "SRSE-BUDGET premise: " + message);
        }

        private void log(String message) {
            JojoMod.LOGGER.info("SRSE-BUDGET {} {}", exhausted ? "exhausted" : "full", message);
        }

        private float blood() {
            return VampirismState.get(user).blood().current();
        }

        private void setUp() {
            premise(level.getDifficulty() != Difficulty.PEACEFUL, "non-Peaceful difficulty");
            speed = 0.5F + level.getDifficulty().getId() * 0.25F;
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX() + 8;
            int z = chunk.getMinBlockZ() + 8;
            int y = template.getY() + 48;
            // The owner looks straight down: the 21 moves stay in the test chunk, in open air under the owner.
            AABB column = new AABB(x - 1, y - 30, z - 1, x + 2, y + 5, z + 2);
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
                    else if (!released) {
                        chargeDeltas.add(delta);
                    }
                    else if (!ended) {
                        shotDeltas.add(delta);
                        if (LivingComponentAction.getCurEntityAction(user) != action) {
                            ended = true;
                            bloodAtEnd = blood();
                            cooldownAtEnd = vampire.getAbilityCooldown(ability.name());
                        }
                    }
                    return;
                }
                List<Frame> beamFrames = frames.get(entity.getUUID());
                if (beamFrames != null && entity instanceof SpaceRipperStingyEyesEntity beam) {
                    beamFrames.add(new Frame(beam.tickCount, beam.isBoundToOwner(), beam.getLength(), beam.position(),
                            beam.getOriginPoint(1.0F)));
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
            if (exhausted) {
                VampirismState.get(user).blood().setCurrent(EXHAUSTED_BLOOD);
                vampire.setBloodLevel(EXHAUSTED_BLOOD);
            }
            bloodBeforeRelease = blood();
            released = true;
            AbilityInput.keyRelease(KEY, user);
            bloodAfterRelease = blood();
            beamsAtRelease = beams.size();
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                premise(helper.getTick() < 170, "watchdog: userTicks=" + userTicks + " shade=" + shadeReady() + " sky="
                        + level.getBrightness(LightLayer.SKY, BlockPos.containing(user.getEyePosition()))
                        + " powered=" + (power != null) + " pressed=" + pressed + " released=" + released
                        + " ended=" + ended + " beams=" + beams.size() + " shotDeltas=" + shotDeltas + " frames=" + frames.values());
                premise(user.isAlive() && !user.isOnFire() && user.position().distanceToSqr(userPosition) < 1.0E-10D
                        && user.getXRot() == 90.0F, "the owner stays put, looking down");
                if (power == null && userTicks >= 2 && shadeReady()) grantPower();
                if (!pressed && poweredAt >= 0 && userTicks >= poweredAt + 4) press();
                if (pressed && !released && action.getPhase() == ActionPhase.WINDUP && action.getPhaseTick() >= 20
                        && userTicks - pressedAt >= 22) {
                    release();
                }
                if (ended && beams.size() == 2 && beams.stream().allMatch(this::detachedForTwoFrames)) {
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

        private boolean detachedForTwoFrames(SpaceRipperStingyEyesEntity beam) {
            return frames.get(beam.getUUID()).stream().filter(frame -> !frame.bound()).count() >= 2;
        }

        private void validate() {
            premise(idleDeltas.size() >= 2 && idleDeltas.stream().allMatch(delta -> Math.abs(delta - idleDeltas.get(0)) < 1.0E-3F)
                    && idleDeltas.get(0) >= 0.0F && idleDeltas.get(0) < 1.0F, "steady passive blood loss, deltas=" + idleDeltas);
            float passive = idleDeltas.get(0);
            float chargeCost = 0.0F;
            for (float delta : chargeDeltas) chargeCost += delta - passive;
            float releaseCost = bloodBeforeRelease - bloodAfterRelease;
            int paidTicks = 0;
            for (float delta : shotDeltas) {
                if (Math.abs(delta - passive - TICK_COST) < 0.01F) paidTicks++;
            }
            int actionTicks = shotDeltas.size();
            float shotCost = releaseCost + paidTicks * TICK_COST;

            int detachAge = -1;
            int boundMoves = -1;
            boolean straight = true;
            StringBuilder beamReport = new StringBuilder();
            for (SpaceRipperStingyEyesEntity beam : beams) {
                List<Frame> beamFrames = frames.get(beam.getUUID());
                int detachIndex = 0;
                while (beamFrames.get(detachIndex).bound()) detachIndex++;
                Frame detach = beamFrames.get(detachIndex);
                Frame next = beamFrames.get(detachIndex + 1);
                int moves = Math.round(detach.length() / speed);
                Vec3 eyeOrigin = detachIndex > 0 ? beamFrames.get(0).origin() : detach.origin();
                // Detached: the origin stays where the owner's eye was and from then on flies with the tip.
                straight &= Math.abs(detach.length() - moves * speed) < 1.0E-3F
                        && Math.abs(detach.tip().distanceTo(detach.origin()) - detach.length()) < 1.0E-3D
                        && detach.origin().distanceTo(eyeOrigin) < 1.0E-3D
                        && next.origin().subtract(detach.origin()).distanceTo(new Vec3(0.0D, -speed, 0.0D)) < 1.0E-3D
                        && next.tip().subtract(detach.tip()).distanceTo(new Vec3(0.0D, -speed, 0.0D)) < 1.0E-3D;
                premise(detachAge < 0 || detachAge == detach.age() && boundMoves == moves, "both beams agree: " + frames.values());
                detachAge = detach.age();
                boundMoves = moves;
                beamReport.append(" [detachAge=").append(detach.age()).append(" length=").append(detach.length())
                        .append(" origin=").append(detach.origin()).append(" nextOrigin=").append(next.origin()).append(']');
            }

            // The index of the tick that ended the shot: 20 for a full shot, 3 when the fourth tick cannot pay.
            int lastTick = exhausted ? 3 : 20;
            int paid = exhausted ? lastTick : lastTick + 1;
            // The power data and the action tick in no fixed order within the owner's tick, so the cooldown set by the
            // ending tick may or may not have run for that tick when it is read here.
            int cooldown = 50 * lastTick / 20;
            String measured = "beamsAtRelease=" + beamsAtRelease + " chargeCost=" + chargeCost + " releaseCost=" + releaseCost
                    + " paidTicks=" + paidTicks + " actionTicks=" + actionTicks + " shotCost=" + shotCost
                    + " bloodAtEnd=" + bloodAtEnd + " boundMoves=" + boundMoves + " detachAge=" + detachAge
                    + " detachedAtEye=" + straight + " cooldownLeft=" + cooldownAtEnd;
            String donor = "beamsAtRelease=2 chargeCost=0 releaseCost=20 paidTicks=" + paid + " actionTicks=" + (lastTick + 1)
                    + " shotCost=" + (20 + paid * 20) + (exhausted ? " bloodAtEnd=0" : "") + " boundMoves=" + (lastTick + 1)
                    + " detachAge=" + (lastTick + 1) + " detachedAtEye=true cooldownLeft=" + cooldown + " or " + (cooldown - 1);
            log("measured " + measured + " passive=" + passive + " shotDeltas=" + shotDeltas + " beams=" + beamReport);
            boolean matches = beamsAtRelease == 2 && Math.abs(chargeCost) < 0.01F && Math.abs(releaseCost - TICK_COST) < 0.01F
                    && paidTicks == paid && actionTicks == lastTick + 1 && (!exhausted || bloodAtEnd == 0.0F)
                    && boundMoves == lastTick + 1 && detachAge == lastTick + 1 && straight
                    && (cooldownAtEnd == cooldown || cooldownAtEnd == cooldown - 1);
            helper.assertTrue(matches, "Space Ripper Stingy Eyes " + (exhausted ? "shot that runs out of blood" : "full shot")
                    + ": " + measured + ", 1.16: " + donor);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (user != null) AbilityInput.keyRelease(KEY, user);
                for (SpaceRipperStingyEyesEntity beam : beams) if (!beam.isRemoved()) beam.discard();
                if (user != null) user.discard();
            }
            finally {
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
