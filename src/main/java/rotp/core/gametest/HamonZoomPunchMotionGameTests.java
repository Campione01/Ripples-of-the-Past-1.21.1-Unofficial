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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonZoomPunchAbility;
import rotp.core.impl.powers.hamon.entity.HamonZoomPunchEntity;
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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonZoomPunchMotionGameTests {
    private HamonZoomPunchMotionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_zoom_motion_control20", timeoutTicks = 100)
    public static void controlTwentyZoomKeepsDonorTurnAndNaturalLifetime(GameTestHelper helper) {
        start(helper, 20);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_zoom_motion_control60", timeoutTicks = 100)
    public static void controlSixtyZoomKeepsDonorTurnAndNaturalLifetime(GameTestHelper helper) {
        start(helper, 60);
    }

    private static void start(GameTestHelper helper, int control) {
        Fixture fixture = new Fixture(helper, control);
        helper.testInfo.addListener(fixture);
        try {
            fixture.setUp();
            helper.runAfterDelay(1, fixture::poll);
        }
        catch (RuntimeException | Error error) {
            fixture.closeAfterFailure(error);
            throw error;
        }
    }

    private record State(double distance, boolean forward, boolean retract,
            Vec3 tip, Vec3 delta, boolean removed) {}
    private record Step(int age, long time, State before, State after) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short INPUT_KEY = 26;
        private static final double EPS = 2.0E-5D;
        private static final double DIAGONAL = Math.sqrt(0.5D);
        private static final Vec3 LOOK = new Vec3(-DIAGONAL, 0, DIAGONAL);
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final int control;
        private final List<Object> listeners = new ArrayList<>();
        private final Map<UUID, HamonZoomPunchEntity> owned = new LinkedHashMap<>();
        private final List<Step> steps = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private StandPower stand;
        private HamonData hamon;
        private Ability ability;
        private HamonZoomPunchEntity punch;
        private Vec3 userPosition;
        private Vec3 root;
        private AABB room;
        private BlockPos roomMin;
        private BlockPos roomMax;
        private ChunkPos chunk;
        private State joined;
        private State pre;
        private Throwable observerFailure;
        private int userTicks;
        private int preAge;
        private long preTime;
        private float speed;
        private int lifeSpan;
        private double speedFactor;
        private float inputEnergy;
        private float inputEfficiency;
        private boolean inputPressed;
        private boolean inputReleased;
        private boolean roomVerified;
        private boolean closed;

        Fixture(GameTestHelper helper, int control) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.control = control;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            roomMin = new BlockPos(x, y, z);
            roomMax = new BlockPos(x + 15, y + 4, z + 15);
            room = new AABB(x + 0.05D, y, z + 0.05D, x + 15.95D, y + 4, z + 15.95D);
            premise(y >= level.getMinBuildHeight() && roomMax.getY() < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "clear room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMax)) {
                premise(level.isEmptyBlock(pos), "room contains a block");
            }
            roomVerified = true;
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.setMainArm(HumanoidArm.RIGHT);
            userPosition = new Vec3(x + 12.75D, y, z + 3.75D);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 45, 0);
            user.setYHeadRot(45);
            user.yBodyRot = 45;
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            premise(level.addFreshEntity(user), "could not add user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            stand = PowerClass.STAND.attachGet(user);
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.ZOOM_PUNCH.get());
            hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(control), true, true);
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            ability = power.getAbility("zoom_punch");
            premise(ability instanceof HamonZoomPunchAbility && ability.abilityType == HamonPowerType.HAMON_ZOOM_PUNCH.get(),
                    "wrong registered ability");
            registerObservers();
            log("setup user=" + user.getUUID() + " position=" + userPosition + " room=" + room
                    + " fixtureNoGravity=true yaw=45 pitch=0 mainArm=RIGHT blocksChanged=0");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level || !(event.getEntity() instanceof HamonZoomPunchEntity created)
                        || created.getOwner() != user) return;
                // Keep every owned emission before any fallible admission or snapshot check.
                owned.put(created.getUUID(), created);
                observe(() -> {
                    premise(inputPressed && punch == null && owned.size() == 1 && !event.isCanceled()
                            && created.getType() == ModEntityTypes.HAMON_ZOOM_PUNCH.get(), "unexpected emission");
                    punch = created;
                    EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                    premise(current instanceof HamonZoomPunchAbility.ZoomPunchInstance
                            && current.ability == ability && current.getPhase() == ActionPhase.PERFORM,
                            "join lacks registered PERFORM action");
                    CompoundTag nbt = punch.saveWithoutId(new CompoundTag());
                    speed = nbt.getFloat("Speed");
                    lifeSpan = nbt.getInt("LifeSpan");
                    speedFactor = nbt.getDouble("SpeedFactor");
                    float joinEfficiency = hamon.getActionEfficiency(450F, true, ModHamonSkills.ZOOM_PUNCH.get(), user);
                    float emittedEfficiency = nbt.getFloat("Points") / 450F;
                    float length = 4F + (4F + control * 0.1F) * emittedEfficiency;
                    float expectedSpeed = 2F * length / lifeSpan * (0.4F + 0.6F * emittedEfficiency);
                    root = expectedRoot();
                    joined = capture();
                    log("join uuid=" + punch.getUUID() + " owner=" + user.getUUID() + " state=" + joined
                            + " root=" + root + " speed=" + speed + " speedBits=" + Float.floatToIntBits(speed)
                            + " lifespan=" + lifeSpan + " speedFactor=" + speedFactor + " inputEnergy=" + inputEnergy
                            + " joinEnergy=" + hamon.getEnergy() + " inputEfficiency=" + inputEfficiency
                            + " joinEfficiency=" + joinEfficiency + " emittedEfficiency=" + emittedEfficiency
                            + " donorTurn=" + donorTurn());
                    premise(lifeSpan == 14 && speed > 0 && Float.isFinite(speed) && speedFactor == 1D
                            && Float.floatToIntBits(speed) == Float.floatToIntBits(expectedSpeed)
                            && inputEnergy > 900F && hamon.getEnergy() > 450F
                            && inputEfficiency == 1F && joinEfficiency == 1F && emittedEfficiency == 1F,
                            "measured launch inputs differ from the declared full-efficiency case");
                    premise(punch.tickCount == 0 && punch.getSide() == HumanoidArm.RIGHT && joined.distance == 0D
                            && joined.forward && !joined.retract && !joined.removed
                            && joined.tip.distanceTo(root) < EPS && joined.delta.lengthSqr() == 0D,
                            "unexpected initial owner-bound state");
                    premise(donorTurn() == (control == 20 ? 6 : 7), "launch does not distinguish the intended donor chronology");
                });
            };
            Consumer<EntityTickEvent.Pre> before = event -> {
                if (event.getEntity() != punch) return;
                observe(() -> {
                    requireActor();
                    premise(!event.isCanceled() && pre == null && !punch.isRemoved() && punch.getOwner() == user && !punch.canHitOwner()
                            && punch.tickCount == steps.size() + 1, "missing or nonconsecutive natural Pre");
                    pre = capture();
                    preAge = punch.tickCount;
                    preTime = level.getGameTime();
                    premise(steps.isEmpty() || preTime == steps.getLast().time + 1, "projectile skipped a world tick");
                    Vec3 actualRoot = punch.getOriginPoint(1F);
                    Vec3 end = pre.tip.add(pre.delta);
                    AABB query = punch.getBoundingBox().expandTowards(actualRoot.subtract(end)).inflate(1D);
                    List<UUID> candidates = level.getEntities(punch, query, entity -> entity != user)
                            .stream().map(Entity::getUUID).toList();
                    HitResult.Type collider = level.clip(new ClipContext(actualRoot, end, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, punch)).getType();
                    HitResult.Type outline = level.clip(new ClipContext(actualRoot, end, ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE, punch)).getType();
                    boolean ticking = ticking(actualRoot) && ticking(end) && ticking(pre.tip)
                            && ticking(new Vec3(query.minX, query.minY, query.minZ))
                            && ticking(new Vec3(query.maxX, query.maxY, query.maxZ));
                    log("pre age=" + preAge + " time=" + preTime + " state=" + pre + " root=" + actualRoot
                            + " end=" + end + " query=" + query + " foreignCandidates=" + candidates
                            + " clips=" + collider + "/" + outline + " ticking=" + ticking);
                    premise(actualRoot.distanceTo(root) < EPS && expectedRoot().distanceTo(root) < EPS
                            && room.contains(actualRoot) && room.contains(end) && contains(query) && ticking
                            && candidates.isEmpty() && collider == HitResult.Type.MISS && outline == HitResult.Type.MISS,
                            "flight is not an unobstructed ticking ray/query");
                });
            };
            Consumer<ProjectileImpactEvent> impact = event -> {
                if (event.getProjectile() != punch) return;
                observe(() -> {
                    log("unexpected-impact age=" + punch.tickCount + " hit=" + event.getRayTraceResult());
                    premise(false, "unexpected natural impact");
                });
            };
            Consumer<EntityTickEvent.Post> after = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                if (event.getEntity() != punch) return;
                premise(pre != null && preAge == punch.tickCount && preTime == level.getGameTime(),
                        "natural Post has no matching Pre");
                State post = capture();
                steps.add(new Step(preAge, preTime, pre, post));
                log("post age=" + preAge + " state=" + post + " removalReason=" + punch.getRemovalReason());
                pre = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(before);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, before);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(after);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, after);
        }

        private void requireActor() {
            premise(user.isAlive() && !user.isCreative() && !user.isSpectator()
                    && !user.getAbilities().instabuild && !user.getAbilities().invulnerable
                    && user.position().distanceToSqr(userPosition) < 1.0E-10D
                    && user.getYRot() == 45F && user.getXRot() == 0F && user.yBodyRot == 45F && user.getYHeadRot() == 45F
                    && user.getMainArm() == HumanoidArm.RIGHT && !user.isShiftKeyDown() && !user.isUsingItem()
                    && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
                    && power.getPowerType() == ModPlayerPowers.HAMON.get() && stand.getPowerType() == null
                    && hamon.getHamonControlLevel() == control && hamon.isSkillLearned(ModHamonSkills.ZOOM_PUNCH.get())
                    && level.isPositionEntityTicking(user.blockPosition()), "actor lost its declared admission or pose");
        }

        private void press() {
            requireActor();
            inputEnergy = hamon.getEnergy();
            inputEfficiency = hamon.getActionEfficiency(450F, true, ModHamonSkills.ZOOM_PUNCH.get(), user);
            premise(user.getAttackStrengthScale(1F) == 1F && !hamon.isAbilityOnCooldown("zoom_punch"),
                    "normal recharge or cooldown not ready");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK),
                    "registered CLICK was not admitted");
            inputPressed = true;
            var held = AbilityInput.keyPress(INPUT_KEY, ability, user, null, InputMethod.CLICK, 0,
                    BufferingState.clickOnly(), ability.getAbilityId());
            premise(held != null && held.action instanceof HamonZoomPunchAbility.ZoomPunchInstance shot
                    && shot.ability == ability && shot == LivingComponentAction.getCurEntityAction(user),
                    "CLICK did not install the registered Zoom action");
            log("input ability=" + ability.getAbilityId() + " control=" + control + " userTicks=" + userTicks
                    + " energy=" + inputEnergy + " efficiency=" + inputEfficiency);
        }

        private State capture() {
            CompoundTag nbt = punch.saveWithoutId(new CompoundTag());
            premise(Float.floatToIntBits(nbt.getFloat("Speed")) == Float.floatToIntBits(speed)
                    && nbt.getInt("LifeSpan") == lifeSpan && nbt.getDouble("SpeedFactor") == speedFactor,
                    "launch constants changed during natural flight");
            return new State(nbt.getDouble("Distance"), nbt.getBoolean("MovingForward"), nbt.getBoolean("Retracting"),
                    punch.position(), punch.getDeltaMovement(), punch.isRemoved());
        }

        private int donorTurn() {
            // Donor OwnerBoundProjectileEntity:233-244: all maxDistance operands are float.
            float maxDistance = speed * speed * (float) lifeSpan / (speed + speed);
            return (int) ((double) maxDistance / (double) speed);
        }

        private List<State> donorTimeline() {
            List<State> expected = new ArrayList<>();
            double distance = joined.distance;
            boolean forward = joined.forward;
            boolean retract = joined.retract;
            Vec3 tip = joined.tip;
            Vec3 delta = joined.delta;
            for (int age = 1; age <= lifeSpan + 1; age++) {
                boolean removed = age > lifeSpan;
                if (!removed) {
                    // Donor: round each double distance +/- speed*factor to float BEFORE flags.
                    double next = retract ? (float) (distance - (double) speed * speedFactor)
                            : forward ? (float) (distance + (double) speed * speedFactor) : (float) distance;
                    if (forward && age >= donorTurn()) forward = false;
                    if (!retract && age >= donorTurn()) retract = true;
                    removed = retract && next <= 0D;
                    if (!removed) {
                        distance = next;
                        Vec3 nextTip = root.add(LOOK.scale(distance + 0.75D));
                        delta = nextTip.subtract(tip);
                        tip = nextTip;
                    }
                }
                expected.add(new State(distance, forward, retract, tip, delta, removed));
                if (removed) break;
            }
            return expected;
        }

        private void validateMotion() {
            premise(joined != null && pre == null && !steps.isEmpty() && owned.size() == 1
                    && steps.getLast().after.removed && punch.getRemovalReason() == Entity.RemovalReason.DISCARDED
                    && punch.tickCount == steps.size() && user.isAlive(), "full natural removal was not observed");
            List<State> expected = donorTimeline();
            int firstMismatch = 0;
            double maxTipError = 0;
            double maxDeltaError = 0;
            double maxDistanceError = 0;
            for (int i = 0; i < Math.min(steps.size(), expected.size()); i++) {
                Step actual = steps.get(i);
                State wantedPre = i == 0 ? joined : expected.get(i - 1);
                State wantedPost = expected.get(i);
                maxTipError = Math.max(maxTipError, actual.after.tip.distanceTo(wantedPost.tip));
                maxDeltaError = Math.max(maxDeltaError, actual.after.delta.distanceTo(wantedPost.delta));
                maxDistanceError = Math.max(maxDistanceError, Math.abs(actual.after.distance - wantedPost.distance));
                if (firstMismatch == 0 && (!same(actual.before, wantedPre) || !same(actual.after, wantedPost))) {
                    firstMismatch = actual.age;
                }
            }
            if (firstMismatch == 0 && steps.size() != expected.size()) firstMismatch = Math.min(steps.size(), expected.size()) + 1;
            int actualTurn = steps.stream().filter(step -> step.after.retract).mapToInt(Step::age).findFirst().orElse(-1);
            log("result uuid=" + punch.getUUID() + " speedBits=" + Float.floatToIntBits(speed) + " donorTurn=" + donorTurn()
                    + " actualTurn=" + actualTurn + " donorRemoval=" + expected.size() + " actualRemoval=" + steps.size()
                    + " firstMismatch=" + firstMismatch + " maxTipError=" + maxTipError + " maxDeltaError=" + maxDeltaError
                    + " maxDistanceError=" + maxDistanceError + " premises=valid");
            helper.assertTrue(firstMismatch == 0, "Zoom donor motion differs: control=" + control + " age=" + firstMismatch
                    + " turn=" + actualTurn + "/" + donorTurn() + " removal=" + steps.size() + "/" + expected.size());
        }

        private static boolean same(State actual, State expected) {
            return actual.forward == expected.forward && actual.retract == expected.retract && actual.removed == expected.removed
                    && Math.abs(actual.distance - expected.distance) < EPS && actual.tip.distanceTo(expected.tip) < EPS
                    && actual.delta.distanceTo(expected.delta) < EPS;
        }

        private Vec3 expectedRoot() {
            return user.getEyePosition(1F).add(-0.35D * DIAGONAL, -0.47D, -0.35D * DIAGONAL);
        }

        private boolean contains(AABB box) {
            return room.contains(new Vec3(box.minX, box.minY, box.minZ)) && room.contains(new Vec3(box.maxX, box.maxY, box.maxZ));
        }

        private boolean ticking(Vec3 pos) {
            BlockPos block = BlockPos.containing(pos);
            return new ChunkPos(block).equals(chunk) && level.isPositionEntityTicking(block);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 80, "watchdog expired before natural removal");
                requireActor();
                if (!inputPressed && userTicks >= 20) press();
                if (punch != null && !inputReleased) release();
                if (punch != null && punch.isRemoved()) {
                    validateMotion();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                closeAfterFailure(error);
                throw error;
            }
        }

        private void release() {
            if (inputPressed && !inputReleased) {
                AbilityInput.keyRelease(INPUT_KEY, user);
                inputReleased = true;
            }
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Zoom motion premise failed: control=" + control, error);
            }
        }

        private void premise(boolean valid, String message) { helper.assertTrue(valid, "Zoom premise: " + message); }
        private void log(String message) { JojoMod.LOGGER.info("HAMON-ZOOM-MOTION control={} {}", control, message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            int registered = listeners.size();
            try {
                for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
                listeners.clear();
            }
            finally {
                try { release(); }
                finally {
                    try {
                        for (HamonZoomPunchEntity shot : owned.values()) if (!shot.isRemoved()) shot.discard();
                    }
                    finally {
                        if (user != null) { user.stopUsingItem(); user.getInventory().clearContent(); user.discard(); }
                        boolean entitiesGone = owned.entrySet().stream().allMatch(entry -> entry.getValue().isRemoved()
                                && level.getEntity(entry.getKey()) == null)
                                && (user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null);
                        boolean blocksUnchanged = true;
                        if (roomVerified) {
                            for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMax)) {
                                blocksUnchanged &= level.isEmptyBlock(pos);
                            }
                        }
                        log("cleanup unregistered=" + registered + " listeners=" + listeners.size() + " owned=" + owned.keySet()
                                + " entitiesGone=" + entitiesGone + " blocksChanged=0 roomStillEmpty=" + blocksUnchanged);
                        if (!listeners.isEmpty() || !entitiesGone || !blocksUnchanged) {
                            throw new IllegalStateException("Zoom motion cleanup verification failed");
                        }
                    }
                }
            }
        }

        private void closeAfterFailure(Throwable primary) {
            try { close(); }
            catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("Zoom motion cleanup failed; original failure retained", cleanup);
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
            if (test.getError() != null) closeAfterFailure(test.getError()); else close();
        }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
