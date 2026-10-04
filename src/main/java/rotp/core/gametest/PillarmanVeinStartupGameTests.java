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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanErraticBlazeKingAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanGiantCarthwheelPrisonAbility;
import rotp.core.init.ModEntityTypes;
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

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanVeinStartupGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final double DIRECTION_EPSILON = 2.0E-4D;

    private PillarmanVeinStartupGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "pillarman_prison_startup", timeoutTicks = 110)
    public static void registeredPrisonKeepsDonorInitialTipBeforeNaturalMovement(GameTestHelper helper) {
        start(helper, Route.PRISON);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pillarman_erratic_startup", timeoutTicks = 110)
    public static void registeredErraticKeepsDonorInitialTipBeforeNaturalMovement(GameTestHelper helper) {
        start(helper, Route.ERRATIC);
    }

    private enum Route {
        PRISON("pillarman_giant_carthwheel_prison", InputMethod.HOLD, 32, 30, 125.0F, 8.0D,
                List.of(new Vec3(0.0D, -0.5D, 0.0D))),
        ERRATIC("pillarman_erratic_blaze_king", InputMethod.CLICK, 10, 10, 20.0F, 4.0D,
                List.of(new Vec3(-0.4D, -0.45D, 1.0D), new Vec3(0.425D, -0.575D, 1.0D)));

        final String abilityName;
        final InputMethod input;
        final int count;
        final int minimumTicks;
        final float energyCost;
        final double startZ;
        final List<Vec3> origins;

        Route(String abilityName, InputMethod input, int count, int minimumTicks, float energyCost,
                double startZ, List<Vec3> origins) {
            this.abilityName = abilityName;
            this.input = input;
            this.count = count;
            this.minimumTicks = minimumTicks;
            this.energyCost = energyCost;
            this.startZ = startZ;
            this.origins = origins;
        }
    }

    private static void start(GameTestHelper helper, Route route) {
        Fixture fixture = new Fixture(helper, route);
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

    private record State(long time, int age, Vec3 eye, Vec3 root, Vec3 tip, Vec3 delta,
            double distance, boolean retracting, boolean removed) {}

    private static final class Tracked {
        final PillarmanVeinEntity vein;
        final State join;
        final Vec3 originOffset;
        final float pitchOffset;
        final float yawOffset;
        State pendingPre;
        State firstPre;
        State firstPost;
        int postTicks;
        double maxDistance;
        boolean retracted;

        Tracked(PillarmanVeinEntity vein, State join, CompoundTag nbt, Vec3 originOffset) {
            this.vein = vein;
            this.join = join;
            this.originOffset = originOffset;
            pitchOffset = nbt.getFloat("XRotOffset");
            yawOffset = nbt.getFloat("YRotOffset");
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 30;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Route route;
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private final Map<UUID, Tracked> veins = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private PillarmanData data;
        private Ability ability;
        private EntityActionInstance action;
        private Vec3 userPosition;
        private RuntimeException observerFailure;
        private int userTicks;
        private int pressTicks;
        private int spawnAfterTicks = -1;
        private boolean pressed;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper, Route route) {
            this.helper = helper;
            level = helper.getLevel();
            this.route = route;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX(), y - 8, chunk.getMinBlockZ());
            BlockPos max = new BlockPos(chunk.getMaxBlockX(), y + 9, chunk.getMaxBlockZ());
            helper.assertTrue(max.getY() < level.getMaxBuildHeight(), "Vein fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Vein sky fixture is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(),
                    "Vein fixture contains another entity");
            userPosition = new Vec3(chunk.getMinBlockX() + 8.0D, y, chunk.getMinBlockZ() + route.startZ);
            BlockPos shade = BlockPos.containing(userPosition).above(8);
            for (BlockPos pos : BlockPos.betweenClosed(shade.offset(-1, 0, -1), shade.offset(1, 0, 1))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "Could not place vein fixture shade");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;
            helper.assertTrue(level.addFreshEntity(user), "Could not add vein user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, user);
            data.setMode(PillarmanMode.HEAT, user);
            data.setEnergy(user, 300.0F);
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility(route.abilityName);
            boolean registered = route == Route.PRISON
                    ? ability instanceof PillarmanGiantCarthwheelPrisonAbility
                            && ability.abilityType == PillarmanPowerType.PILLAR_MAN_GIANT_CARTHWHEEL_PRISON.get()
                    : ability instanceof PillarmanErraticBlazeKingAbility
                            && ability.abilityType == PillarmanPowerType.PILLAR_MAN_ERRATIC_BLAZE_KING.get();
            helper.assertTrue(registered && !data.isStoneFormEnabled(), "Registered vein ability or HEAT fixture is invalid");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            registerObservers();
            log("setup user=" + user.getUUID() + " pos=" + userPosition + " chunk=" + chunk
                    + " noGravity=true shadeBlocks=" + roof.size() + " energy=" + data.getEnergy());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() != level || !(event.getEntity() instanceof PillarmanVeinEntity vein) || vein.getOwner() != user) return;
                helper.assertTrue(!event.isCanceled() && pressed && action != null
                                && LivingComponentAction.getCurEntityAction(user) == action && action.getPhase() == ActionPhase.PERFORM,
                        "Vein spawn was not the registered action perform");
                helper.assertTrue(userTicks - pressTicks >= route.minimumTicks && vein.getType() == ModEntityTypes.PILLAR_MAN_VEINS.get()
                                && vein.ticksLifespan() == 25 && vein.getSpeedFactor() == 1.0D,
                        "Vein spawn timing, type or lifespan is invalid");
                if (route == Route.ERRATIC) {
                    helper.assertTrue(action.getPhaseTick() == 10.0F, "Erratic veins were not emitted at natural perform tick10");
                }
                CompoundTag nbt = vein.saveWithoutId(new CompoundTag());
                Vec3 offset = new Vec3(nbt.getDouble("XOriginOffset"), nbt.getDouble("YOriginOffset"), nbt.getDouble("ZOriginOffset"));
                helper.assertTrue(route.origins.stream().anyMatch(expected -> near(offset, expected)), "Vein authored origin changed");
                Tracked tracked = new Tracked(vein, state(vein), nbt, offset);
                helper.assertTrue(veins.put(vein.getUUID(), tracked) == null && veins.size() <= route.count, "Duplicate or extra vein");
                if (spawnAfterTicks < 0) {
                    spawnAfterTicks = userTicks - pressTicks;
                    log("spawn naturalInputTicks=" + spawnAfterTicks + " phaseTick=" + action.getPhaseTick()
                            + " energyAfterDebit=" + data.getEnergy());
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                Tracked tracked = veins.get(event.getEntity().getUUID());
                if (tracked == null) return;
                helper.assertTrue(!event.isCanceled() && tracked.pendingPre == null
                                && level.isPositionEntityTicking(tracked.vein.blockPosition())
                                && tracked.vein.getOwner() == user, "Vein natural tick or ownership is invalid");
                tracked.pendingPre = state(tracked.vein);
                if (tracked.firstPre == null) tracked.firstPre = tracked.pendingPre;
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                helper.assertTrue(!veins.containsKey(event.getProjectile().getUUID()),
                        "Empty vein startup fixture produced a real impact");
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                Tracked tracked = veins.get(event.getEntity().getUUID());
                if (tracked == null) return;
                State after = state(tracked.vein);
                State before = tracked.pendingPre;
                helper.assertTrue(before != null && before.time == after.time && before.age == after.age,
                        "Vein post tick lacks its natural pre tick");
                tracked.postTicks++;
                tracked.maxDistance = Math.max(tracked.maxDistance, after.distance);
                tracked.retracted |= after.retracting;
                if (tracked.firstPost == null) {
                    tracked.firstPost = after;
                    log("first uuid=" + tracked.vein.getUUID() + " offset=" + tracked.originOffset
                            + " angles=" + tracked.pitchOffset + "," + tracked.yawOffset
                            + " join=" + tracked.join + " pre=" + before + " post=" + after);
                }
                tracked.pendingPre = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private State state(PillarmanVeinEntity vein) {
            CompoundTag nbt = vein.saveWithoutId(new CompoundTag());
            return new State(level.getGameTime(), vein.tickCount, user.getEyePosition(1.0F), vein.getOriginPoint(1.0F),
                    vein.position(), vein.getDeltaMovement(), nbt.getDouble("Distance"), nbt.getBoolean("IsRetracting"), vein.isRemoved());
        }

        private void press() {
            helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()) && !user.isCreative()
                            && !user.getAbilities().instabuild && !data.isStoneFormEnabled() && data.getEnergy() >= route.energyCost
                            && !user.isOnFire() && !level.canSeeSky(BlockPos.containing(user.getEyePosition())),
                    "Vein player is not eligible, shaded and naturally ticking");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, route.input),
                    "Registered vein input failed admission");
            pressed = true;
            pressTicks = userTicks;
            var input = AbilityInput.keyPress(KEY, ability, user, null, route.input,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof EntityActionInstance started
                            && started.ability == ability && started == LivingComponentAction.getCurEntityAction(user),
                    "Registered input did not install its real vein action");
            action = (EntityActionInstance) input.action;
            if (route == Route.PRISON) {
                helper.assertTrue(action instanceof PillarmanGiantCarthwheelPrisonAbility.GiantCarthwheelPrisonInstance
                                && action.getPhase() == ActionPhase.BUTTON_CHARGE && action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) == 30.0F,
                        "Registered Prison did not install its 30-tick charge");
            }
            else {
                helper.assertTrue(action instanceof PillarmanErraticBlazeKingAbility.ErraticBlazeKingInstance
                                && action.getPhase() == ActionPhase.PERFORM && action.phasesLength.getFloat(ActionPhase.PERFORM) == 40.0F,
                        "Registered Erratic did not install its zero-windup perform action");
            }
            log("press ability=" + ability.getAbilityId() + " type=" + ability.abilityType.registryKey + " warmTicks=" + userTicks);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 95, "Vein watchdog: ticks=" + userTicks + " spawned=" + veins.size());
                helper.assertTrue(user.isAlive() && near(user.position(), userPosition) && !user.isOnFire()
                                && user.getYRot() == 0.0F && user.getXRot() == 0.0F && user.yBodyRot == 0.0F,
                        "Vein owner moved, rotated, died or burned");
                if (!pressed && userTicks >= 2) press();
                if (veins.size() == route.count && !released) {
                    AbilityInput.keyRelease(KEY, user);
                    released = true;
                }
                if (veins.size() == route.count && veins.values().stream().allMatch(t -> t.vein.isRemoved())) {
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

        private void validate() {
            for (Vec3 origin : route.origins) {
                long count = veins.values().stream().filter(t -> near(t.originOffset, origin)).count();
                helper.assertTrue(count == route.count / route.origins.size(), "Vein volley lost an authored origin group");
            }
            double maxJoinEyeError = 0.0D;
            for (Tracked tracked : veins.values()) {
                State join = tracked.join;
                State pre = tracked.firstPre;
                State post = tracked.firstPost;
                helper.assertTrue(pre != null && post != null && join.age == 0 && pre.age == join.age + 1
                                && pre.time >= join.time && post.time == pre.time && !join.removed && !pre.removed && !post.removed,
                        "Vein spawn/first-natural-tick ordering is invalid");
                Vec3 expectedRoot = join.eye.add(tracked.originOffset);
                helper.assertTrue(near(join.root, expectedRoot) && near(pre.root, expectedRoot)
                                && near(post.root, expectedRoot) && near(pre.eye, join.eye)
                                && near(pre.tip, join.tip) && near(join.delta, Vec3.ZERO) && near(pre.delta, Vec3.ZERO)
                                && join.distance == 0.0D && pre.distance == 0.0D && !join.retracting && !pre.retracting,
                        "Vein pre-tick geometry was mutated or its authored root changed");
                double pitch = Math.toRadians(tracked.pitchOffset);
                double yaw = Math.toRadians(tracked.yawOffset);
                double firstDistance = (double) (16.0F / 25.0F);
                Vec3 expectedTip = expectedRoot.add(-Math.sin(yaw) * Math.cos(pitch) * firstDistance,
                        -Math.sin(pitch) * firstDistance, Math.cos(yaw) * Math.cos(pitch) * firstDistance);
                // The independent trigonometric oracle allows vanilla's float sine-table precision.
                helper.assertTrue(post.tip.distanceTo(expectedTip) < DIRECTION_EPSILON && near(post.delta, post.tip.subtract(pre.tip))
                                && Math.abs(post.distance - firstDistance) < EPSILON,
                        "First natural vein movement does not match the authored root/direction");
                helper.assertTrue(tracked.postTicks > 2 && tracked.retracted && tracked.maxDistance > post.distance
                                && tracked.vein.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                        "Vein did not extend/retract/expire naturally");
                maxJoinEyeError = Math.max(maxJoinEyeError, join.tip.distanceTo(join.eye));
            }
            log("result veins=" + route.count + " naturalInputTicks=" + spawnAfterTicks + " allExpired=true maxJoinEyeError=" + maxJoinEyeError
                    + " postTicks=" + veins.values().stream().map(t -> t.postTicks).distinct().toList()
                    + " peakDistance=" + veins.values().stream().mapToDouble(t -> t.maxDistance).max().orElseThrow());
            // Donor construction samples the eye before offset fields are assigned; its factory does not reset the tip.
            helper.assertTrue(maxJoinEyeError < EPSILON,
                    route + " initial tip differs from donor eye before its first natural tick: error=" + maxJoinEyeError);
        }

        private static boolean near(Vec3 a, Vec3 b) {
            return a.distanceToSqr(b) < EPSILON * EPSILON;
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Vein startup observer failure: " + route, error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("VEIN-STARTUP {} {}", route, message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (user != null) AbilityInput.keyRelease(KEY, user);
            }
            finally {
                try {
                    for (Tracked tracked : veins.values()) if (!tracked.vein.isRemoved()) tracked.vein.discard();
                    if (user != null) user.discard();
                }
                finally {
                    for (var entry : roof.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = roof.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                    log("cleanup listeners=0 restored=" + restored + " shadeBlocks=" + roof.size());
                    if (!restored) throw new IllegalStateException("Vein shade was not exactly restored");
                }
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
