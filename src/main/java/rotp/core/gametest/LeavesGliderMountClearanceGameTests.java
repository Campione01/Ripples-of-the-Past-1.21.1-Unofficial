package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonLifeMagnetismAbility;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.init.ModDataAttachmentTypes;
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
public final class LeavesGliderMountClearanceGameTests {
    private LeavesGliderMountClearanceGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "leaves_glider_ground_mount", timeoutTicks = 100)
    public static void carriedLeavesGroundMountUsesActualCollisionClearance(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "leaves_glider_air_mount", timeoutTicks = 100)
    public static void carriedLeavesUnobstructedAirMountAddsNoClearanceLift(GameTestHelper helper) {
        start(helper, false);
    }

    private record Probe(long time, int playerAge, Vec3 bornFeet, AABB bareBox,
            float riderHeight, boolean riderOnGround, double groundGap, double requestedLift,
            double allowedLift, Vec3 expectedFeet, int downwardShapes, int upwardShapes) {}

    private static void start(GameTestHelper helper, boolean ground) {
        Fixture fixture = new Fixture(helper, ground);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 40;
        private static final double EPS = 1.0E-5D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean ground;
        private final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private HamonData hamon;
        private Ability ability;
        private EntityActionInstance action;
        private LeavesGliderEntity glider;
        private EntityMountEvent mountEvent;
        private Probe probe;
        private ChunkPos chunk;
        private Vec3 joinFeet, originalUserPosition, resizeFeet;
        private AABB joinBox;
        private double floorTop;
        private long generation, ownerPostTime, joinTime;
        private int userPosts, windupPosts, joins, mounts, gliderPreTicks, resizes;
        private float resizeOldHeight, resizeNewHeight;
        private boolean pressed, ownerPostOpen, joinOnGround, done, closed;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, boolean ground) {
            this.helper = helper; this.level = helper.getLevel(); this.ground = ground;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "GLIDER-MOUNT-PREMISE " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, "GLIDER-MOUNT-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("GLIDER-MOUNT {} {}", ground ? "GROUND" : "AIR", message); }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO); chunk = new ChunkPos(template);
            floorTop = template.getY() + 32D;
            double x = chunk.getMinBlockX() + 8.5D, z = chunk.getMinBlockZ() + 8.5D;
            AABB room = new AABB(x - 4, floorTop - 1, z - 4, x + 4, floorTop + 14, z + 4);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight(), "room build bounds");
            premise(level.getEntities((Entity) null, room).isEmpty(), "room contains foreign entities");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "room is not empty air: " + pos);
            }
            BlockPos center = BlockPos.containing(x, floorTop - 1, z);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2))) {
                floor.put(pos.immutable(), level.getBlockState(pos));
                premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned support placement failed");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL); owned.add(user);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            originalUserPosition = new Vec3(x, floorTop + (ground ? 0.2D : 10D), z);
            user.moveTo(originalUserPosition.x, originalUserPosition.y, originalUserPosition.z, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LEAVES));
            premise(level.addFreshEntity(user), "owned player join failed");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            premise(!power.hasPower() && !PowerClass.STAND.attachGet(user).hasPower()
                    && power.trySetPowerType(ModPlayerPowers.HAMON.get()), "fresh Hamon grant failed");
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.WALL_CLIMBING.get());
            hamon.learnSkill(ModHamonSkills.LIFE_MAGNETISM.get());
            hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
            hamon.setBreathStability(hamon.getMaxBreathStability()); hamon.setEnergy(hamon.getMaxEnergy());
            ability = power.getAbility("life_magnetism");
            premise(ability instanceof HamonLifeMagnetismAbility && ability.abilityType == HamonPowerType.HAMON_LIFE_MAGNETISM.get(),
                    "actual registered Life Magnetism absent");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            observeEvents();
            log("setup player=" + user.getUUID() + " pos=" + originalUserPosition + " floorTop=" + floorTop
                    + " gravity=ordinary supportCells=" + floor.size() + " leaves=" + user.getMainHandItem());
        }

        private void observeEvents() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user) {
                    premise(!event.isCanceled() && user.isAlive() && !user.isOnFire(), "owner natural tick eligibility");
                    ready(user.getBoundingBox());
                }
                if (event.getEntity() == glider) gliderPreTicks++;
            });
            Consumer<EntityTickEvent.Post> opening = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(!ownerPostOpen, "nested owner Post");
                ownerPostOpen = true; ownerPostTime = level.getGameTime();
                if (pressed && action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.WINDUP) windupPosts++;
            });
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() != level || event.getEntity().getType() != ModEntityTypes.LEAVES_GLIDER.get()
                        || !pressed || !ownerPostOpen) return;
                owned.add(event.getEntity()); joins++;
                premise(event.getEntity() instanceof LeavesGliderEntity && joins == 1, "unexpected owned glider count/type");
                glider = (LeavesGliderEntity) event.getEntity();
                joinFeet = glider.position(); joinBox = glider.getBoundingBox(); joinOnGround = glider.onGround(); joinTime = level.getGameTime();
                log("join uuid=" + glider.getUUID() + " feet=" + joinFeet + " box=" + joinBox + " onGround=" + joinOnGround
                        + " playerFeet=" + user.position() + " playerOnGround=" + user.onGround() + " ownerPostTime=" + ownerPostTime);
                premise(!event.isCanceled() && joinTime == ownerPostTime && action == LivingComponentAction.getCurEntityAction(user)
                        && action.ability == ability && action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() < 1
                        && glider.tickCount == 0 && gliderPreTicks == 0 && !glider.isVehicle() && !user.isPassenger()
                        && joinFeet.distanceTo(user.position()) < EPS && !joinOnGround
                        && Math.abs(joinBox.getXsize() - 2.5D) < EPS && Math.abs(joinBox.getYsize() - 0.125D) < EPS
                        && glider.getLeavesBlock().is(Blocks.OAK_LEAVES)
                        && user.getMainHandItem().is(Items.OAK_LEAVES) && user.getMainHandItem().getCount() == 1
                        && LivingComponentAction.getAim(user).getTarget().getType() == ActionTarget.TargetType.EMPTY,
                        "join is not the real fresh carried-leaf PERFORM emitter");
            });
            Consumer<EntityMountEvent> mount = event -> observe(() -> {
                if (event.getEntityMounting() != user || event.getEntityBeingMounted() != glider) return;
                mounts++; mountEvent = event;
                premise(mounts == 1 && event.isMounting() && !event.isCanceled() && event.getLevel() == level
                        && ownerPostOpen && level.getGameTime() == joinTime && level.getEntity(glider.getUUID()) == glider
                        && glider.tickCount == 0 && gliderPreTicks == 0 && !glider.isVehicle() && !user.isPassenger()
                        && glider.position().distanceTo(joinFeet) < EPS && user.getPose() == Pose.STANDING,
                        "first mount event lacks the owned untouched emitter");
                probe = collisionProbe();
                log("mount-probe target=" + glider.getUUID() + " generation=" + generation + " " + probe);
                premise(ground ? user.onGround() && Math.abs(user.getY() - floorTop) < EPS && Math.abs(probe.groundGap) < EPS
                        && probe.requestedLift > 2.7D && Math.abs(probe.allowedLift - probe.requestedLift) < EPS
                        : !user.onGround() && user.getY() - floorTop > 1.1D && Math.abs(probe.groundGap - 1D) < EPS
                                && probe.requestedLift == 0 && probe.allowedLift == 0,
                        "actual downward/upward block probes do not qualify the selected clearance case");
            });
            Consumer<EntityTickEvent.Post> closing = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(ownerPostOpen && ownerPostTime == level.getGameTime(), "owner Post bracket missing");
                userPosts++;
                if (glider != null) {
                    premise(probe != null && mountEvent != null && mounts == 1 && !mountEvent.isCanceled()
                            && joinTime == level.getGameTime() && glider.tickCount == 0 && gliderPreTicks == 0
                            && !glider.isRemoved() && user.getVehicle() == glider && glider.getPassengers().size() == 1
                            && glider.getControllingPassenger() == user && glider.hasPassenger(user)
                            && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
                            && action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.PERFORM
                            && action.getPhaseTick() >= 1 && windupPosts > 0 && user.getHealth() == 20F && user.isAlive(),
                            "same-Post first mount/leaf consumption did not complete before vehicle physics");
                    Vec3 actual = glider.position();
                    AABB expectedMountedBox = probe.bareBox.move(0, probe.allowedLift, 0).expandTowards(0, probe.riderHeight, 0);
                    ready(expectedMountedBox); ready(glider.getBoundingBox());
                    premise(resizes == 1 && resizeFeet != null && resizeFeet.distanceTo(actual) < EPS
                            && Math.abs(resizeOldHeight - probe.bareBox.getYsize()) < EPS
                            && Math.abs(resizeNewHeight - (resizeOldHeight + probe.riderHeight)) < EPS
                            && Math.abs(glider.getBoundingBox().getYsize() - resizeNewHeight) < EPS
                            && blockShapes(expectedMountedBox).isEmpty() && blockShapes(glider.getBoundingBox()).isEmpty(),
                            "mount resize introduced relocation or the tall mounted clearance is obstructed");
                    log("first-mount-complete uuid=" + glider.getUUID() + " probe=" + probe + " actualGliderFeet=" + actual
                            + " riderFeet=" + user.position() + " riderVehicle=" + user.getVehicle().getUUID()
                            + " postMountBox=" + glider.getBoundingBox() + " preResizeFeet=" + resizeFeet + " resizes=" + resizes
                            + " leaves=" + user.getMainHandItem()
                            + " energy=" + glider.getEnergy() + " naturalWindupPosts=" + windupPosts);
                    oracle(actual.distanceTo(probe.expectedFeet) < EPS,
                            "first mounted glider feet differ from donor collision clearance");
                    done = true;
                }
                ownerPostOpen = false;
            });
            Consumer<EntityEvent.Size> resize = event -> observe(() -> {
                if (event.getEntity() != glider || glider == null) return;
                premise(ownerPostOpen && mountEvent != null && mounts == 1 && gliderPreTicks == 0 && glider.tickCount == 0,
                        "glider dimension event is outside first mount");
                resizes++; resizeFeet = glider.position();
                resizeOldHeight = event.getOldSize().height(); resizeNewHeight = event.getNewSize().height();
                log("mount-resize uuid=" + glider.getUUID() + " preResizeFeet=" + resizeFeet
                        + " oldHeight=" + resizeOldHeight + " newHeight=" + resizeNewHeight);
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() == user) premise(false, "owner took unexpected damage before first-mount observation");
            });
            add(pre, EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add(opening, EntityTickEvent.Post.class, EventPriority.HIGHEST, false);
            add(join, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add(mount, EntityMountEvent.class, EventPriority.LOWEST, true);
            add(resize, EntityEvent.Size.class, EventPriority.LOWEST, false);
            add(closing, EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add(damage, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
        }

        private Probe collisionProbe() {
            AABB box = glider.getBoundingBox();
            ready(box.expandTowards(0, -1, 0).expandTowards(0, user.getBbHeight() + 1D, 0));
            premise(level.getWorldBorder().isWithinBounds(box.inflate(4))
                    && level.getEntities(glider, box.inflate(4), entity -> entity != user).isEmpty()
                    && floor.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)),
                    "foreign collider/border or changed owned support");
            List<VoxelShape> below = blockShapes(box.expandTowards(0, -1, 0));
            double gap = -Shapes.collide(Direction.Axis.Y, box, below, -1D);
            double lift = gap < 1D ? user.getBbHeight() + (1F - (float) gap) : 0D;
            List<VoxelShape> above = blockShapes(box.expandTowards(0, lift, 0));
            double allowed = Shapes.collide(Direction.Axis.Y, box, above, lift);
            return new Probe(level.getGameTime(), user.tickCount, joinFeet, box, user.getBbHeight(), user.onGround(),
                    gap, lift, allowed, joinFeet.add(0, allowed, 0), below.size(), above.size());
        }
        private List<VoxelShape> blockShapes(AABB query) {
            List<VoxelShape> shapes = new ArrayList<>();
            for (VoxelShape shape : level.getBlockCollisions(glider, query)) shapes.add(shape);
            return shapes;
        }
        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void ready(AABB box) {
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "actor/probe outside ready chunk");
        }
        private void press() {
            premise(user.isAlive() && user.getHealth() == 20F && !user.isCreative() && !user.isSpectator() && !user.isPassenger()
                    && !user.isNoGravity() && user.getPose() == Pose.STANDING && !user.isShiftKeyDown()
                    && hamon.isSkillLearned(ModHamonSkills.LIFE_MAGNETISM.get()) && hamon.isSkillLearned(ModHamonSkills.WALL_CLIMBING.get())
                    && hamon.getEnergy() > 200F && user.getMainHandItem().is(Items.OAK_LEAVES) && user.getMainHandItem().getCount() == 1
                    && user.getOffhandItem().isEmpty() && user.getY() < originalUserPosition.y
                    && (ground ? user.onGround() && Math.abs(user.getY() - floorTop) < EPS : !user.onGround() && user.getY() - floorTop > 5D),
                    "ordinary skill/item/gravity readiness changed");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK), "registered CLICK admission failed");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK, 0, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof HamonLifeMagnetismAbility.LifeMagnetismInstance, "CLICK lacks actual Life Magnetism action");
            action = (EntityActionInstance) input.action; generation = input.generation;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.ability == ability && generation > 0,
                    "registered action/generation identity differs");
            log("input generation=" + generation + " phase=" + action.getPhase() + " playerFeet=" + user.position()
                    + " grounded=" + user.onGround() + " delta=" + user.getDeltaMovement() + " energy=" + hamon.getEnergy());
        }
        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 80, "finite actor/ability/mount watchdog");
                if (!pressed && userPosts >= 3 && (!ground || user.onGround())) press();
                if (done) {
                    premise(joins == 1 && mounts == 1 && probe != null, "first-mount receipt incomplete");
                    long released = AbilityInput.keyReleaseAndGetGeneration(KEY, user);
                    premise(released == generation && !AbilityInput.isHeldByKey(user, action), "owned input generation release failed");
                    log("RESULT qualified=true joins=1 mounts=1 expectedLift=" + probe.allowedLift + " native=false");
                    close(); helper.succeed(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }
        private void observe(Runnable operation) {
            if (closed || done || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(KEY, user); }, failures);
            cleanup(() -> { if (user != null) user.stopRiding(); }, failures);
            cleanup(() -> { if (user != null && user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(input -> !input.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned held input remains"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (user != null) user.getInventory().clearContent(); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var entry : floor.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = floor.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean removed = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                log("cleanup supportCells=" + floor.size() + " exactRestore=" + restored + " ownedRemoved=" + removed + " listeners=0");
                if (!restored || !removed || user != null && user.isPassenger()) throw new IllegalStateException("glider fixture cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("glider mount cleanup failed");
                failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
