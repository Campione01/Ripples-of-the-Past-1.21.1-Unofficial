package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersSupportRemovalGameTests {
    private static final double EPS = 1.0E-6D;
    private ClackersSupportRemovalGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_natural_support_loss", timeoutTicks = 100)
    public static void registeredClackersReleaseAfterTheirActualSupportIsRemoved(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper); helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record Frame(long time, int age, Vec3 position, Vec3 velocity, boolean grounded,
            boolean removed, Entity.RemovalReason reason, UUID owner, boolean ownerAlive,
            boolean ticking, boolean ownerTicking, boolean noGravity, boolean noPhysics,
            boolean creativeOnly, int savedAge, boolean probeClear, BlockState atPosition, BlockState atImpact) {}
    private record Step(Frame before, Frame after) {}
    private record Removal(Frame before, Frame after, AABB probe, boolean destroyed) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<ClackersEntity> shots = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<Step> releaseSteps = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private HamonData hamon;
        private ClackersEntity shot;
        private AABB room;
        private Vec3 ownerFeet;
        private BlockPos impactPos;
        private BlockState impactState;
        private Frame pending, landing, lastAfter, leave;
        private Removal removal;
        private Throwable observerFailure;
        private int userPosts, useStartPosts, usePres, impacts, pairs, stablePairs;
        private boolean using, releasing, released, done, closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }
        private void premise(boolean value, String text) { helper.assertTrue(value, "CLACKERS-SUPPORT-PREMISE " + text); }
        private void oracle(boolean value, String text) { helper.assertTrue(value, "CLACKERS-SUPPORT-ORACLE " + text); }
        private void log(String text) { System.out.println("[CLACKERS-SUPPORT] " + text); }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO); ChunkPos chunk = new ChunkPos(origin);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = origin.getY() + 24;
            room = new AABB(x + 3, y - 1, z + 1, x + 13, y + 5, z + 13);
            ownerFeet = new Vec3(x + 8.5D, y, z + 3.5D);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY <= level.getMaxBuildHeight(), "build bounds");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null && level.isPositionEntityTicking(pos)
                        && new ChunkPos(pos).equals(chunk), "room not empty/ticking at " + pos);
            }
            premise(level.getEntities((Entity) null, room).isEmpty(), "foreign room entity");
            for (int bx = x + 3; bx < x + 13; bx++) for (int bz = z + 1; bz < z + 13; bz++) {
                place(new BlockPos(bx, y - 1, bz)); place(new BlockPos(bx, y + 4, bz));
            }
            for (int bx = x + 6; bx <= x + 10; bx++) for (int by = y; by < y + 4; by++) {
                BlockPos pos = new BlockPos(bx, by, z + 9); wall.add(pos); place(pos);
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.moveTo(ownerFeet.x, ownerFeet.y + 0.2D, ownerFeet.z, 0, 0); user.setYHeadRot(0); user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
            premise(level.addFreshEntity(user), "ordinary owner admission");
            power = PowerClass.PLAYER_POWER.attachGet(user); power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
            hamon.setBreathStability(hamon.getMaxBreathStability()); hamon.setEnergy(hamon.getMaxEnergy());
            observers();
            log("SETUP owner=" + user.getUUID() + " room=" + room + " cells=" + originals.size()
                    + " ordinaryGravity=true supportRemovalDropBlock=false timeout=100");
        }
        private void place(BlockPos pos) {
            originals.put(pos.immutable(), level.getBlockState(pos));
            premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned stone placement");
        }

        private void observers() {
            add((EntityJoinLevelEvent event) -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof ClackersEntity created && created.getOwner() == user) {
                    shots.add(created);
                    observe(() -> {
                        premise(releasing && shot == null && !event.isCanceled(), "unexpected owned emission"); shot = created;
                        CompoundTag tag = shot.saveWithoutId(new CompoundTag());
                        ItemStack pickup = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("PickupItem"));
                        log("JOIN uuid=" + shot.getUUID() + " frame=" + frame() + " pickup=" + pickup + " usePres=" + usePres);
                        premise(shot.getType() == ModEntityTypes.CLACKERS.get() && shot.tickCount == 0 && usePres == 20
                                && tag.contains("CreativeOnlyPickup", 1) && !tag.getBoolean("CreativeOnlyPickup")
                                && pickup.is(ModItems.CLACKERS.get()) && pickup.getCount() == 1 && tag.getFloat("HamonSpent") == 100F,
                                "registered twenty-tick recoverable throw");
                    });
                }
                else if (event.getEntity() instanceof ItemEntity item && pending != null && shot != null && item.getOwner() == user
                        && item.getItem().is(ModItems.CLACKERS.get()) && item.position().distanceToSqr(shot.position()) <= 1D
                        && room.contains(item.position())) {
                    drops.add(item); log("OWNED_PROJECTILE_ITEM uuid=" + item.getUUID() + " pre=" + pending);
                }
            }, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Pre event) -> observe(() -> {
                if (event.getEntity() == user && using && !released) {
                    premise(!event.isCanceled() && user.isUsingItem() && user.getUseItem().is(ModItems.CLACKERS.get()), "use tick admission"); usePres++;
                }
                if (event.getEntity() == shot && !done) {
                    premise(!event.isCanceled() && pending == null, "canceled/unpaired projectile Pre");
                    pending = frame(); ownerAndRoom(); shotGeometry();
                    premise(pending.age == pairs + 1 && pending.savedAge == pending.age && !pending.removed
                            && (lastAfter == null || pending.time == lastAfter.time + 1), "natural age/time sequence");
                    if (landing != null && removal == null) stable(pending);
                }
            }), EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Post event) -> observe(() -> {
                if (event.getEntity() == user) userPosts++;
                if (event.getEntity() == shot && !done) {
                    Frame after = frame();
                    premise(pending != null && pending.age == after.age && pending.time == after.time, "Post lacks its natural Pre");
                    Step step = new Step(pending, after); pairs++;
                    if (landing == null && after.grounded && impacts == 1) { landing = after; log("LANDING " + after); }
                    if (removal == null && landing != null) {
                        stable(after);
                        if (pending.grounded && after.age > landing.age) { stablePairs++; log("STABLE " + step); }
                    }
                    if (removal != null) {
                        releaseSteps.add(step); log("SUPPORT_STEP " + step);
                        // A stuck first update is the selected oracle, not a movement-watchdog failure.
                        if (releaseSteps.size() == 1 && (after.grounded || after.removed) || releaseSteps.size() == 3) done = true;
                    }
                    else premise(!after.removed, "projectile disappeared before support removal");
                    lastAfter = after; pending = null;
                }
            }), EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add((ProjectileImpactEvent event) -> observe(() -> {
                if (event.getProjectile() != shot) return;
                impacts++;
                log("IMPACT age=" + shot.tickCount + " hit=" + event.getRayTraceResult() + " canceled=" + event.isCanceled());
                premise(pending != null && impacts == 1 && landing == null && removal == null && !event.isCanceled()
                        && event.getRayTraceResult() instanceof BlockHitResult, "unexpected impact context");
                BlockHitResult hit = (BlockHitResult) event.getRayTraceResult();
                premise(wall.contains(hit.getBlockPos()) && hit.getDirection() == Direction.NORTH, "not the owned NORTH wall");
                impactPos = hit.getBlockPos().immutable(); impactState = level.getBlockState(impactPos);
                premise(impactState.is(Blocks.STONE) && level.getFluidState(impactPos).isEmpty(), "non-inert support");
            }), ProjectileImpactEvent.class, EventPriority.LOWEST, true);
            add((EntityLeaveLevelEvent event) -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() == shot) { leave = frame(); log("LEAVE " + leave + " pre=" + pending); }
            }), EntityLeaveLevelEvent.class, EventPriority.LOWEST, false);
        }

        private AABB pointProbe() { return new AABB(shot.position(), shot.position()).inflate(0.06D); }
        private Frame frame() {
            CompoundTag tag = shot.saveWithoutId(new CompoundTag()); Entity owner = shot.getOwner();
            return new Frame(level.getGameTime(), shot.tickCount, shot.position(), shot.getDeltaMovement(), shot.isInGround(),
                    shot.isRemoved(), shot.getRemovalReason(), owner == null ? null : owner.getUUID(), user.isAlive(),
                    level.isPositionEntityTicking(shot.blockPosition()), level.isPositionEntityTicking(user.blockPosition()),
                    shot.isNoGravity(), shot.noPhysics, tag.getBoolean("CreativeOnlyPickup"), tag.getInt("Age"),
                    level.noCollision(pointProbe()), level.getBlockState(shot.blockPosition()), impactPos == null ? null : level.getBlockState(impactPos));
        }
        private int inventoryCount() { return user.getInventory().countItem(ModItems.CLACKERS.get()); }
        private void ownerAndRoom() {
            premise(user.isAlive() && !user.isRemoved() && level.getEntity(user.getUUID()) == user
                    && !user.isCreative() && !user.getAbilities().instabuild && !user.isInvulnerable() && !user.getAbilities().invulnerable
                    && !user.isNoGravity() && !user.noPhysics && user.onGround() && user.position().distanceToSqr(ownerFeet) < EPS * EPS
                    && user.getDeltaMovement().horizontalDistanceSqr() < EPS * EPS && level.isPositionEntityTicking(user.blockPosition()),
                    "ordinary live grounded owner changed");
            premise(originals.keySet().stream().allMatch(pos -> level.getBlockEntity(pos) == null
                    && (removal != null && pos.equals(impactPos) ? level.isEmptyBlock(pos) : level.getBlockState(pos).is(Blocks.STONE))),
                    "owned support/roof/floor changed outside the declared destruction");
            premise(level.getEntities((Entity) null, room).stream().allMatch(entity -> entity == user || shots.contains(entity)
                    || drops.contains(entity)), "foreign or unattributed room entity");
        }
        private void shotGeometry() {
            AABB query = shot.getBoundingBox().expandTowards(shot.getDeltaMovement()).inflate(1D);
            premise(query.minX >= room.minX && query.maxX < room.maxX && query.minY >= room.minY && query.maxY < room.maxY
                    && query.minZ >= room.minZ && query.maxZ < room.maxZ, "actual projectile query left ready room");
            premise(shot.getOwner() == user && !shot.isNoGravity() && !shot.noPhysics && !shot.isInWaterOrBubble()
                    && level.isPositionEntityTicking(shot.blockPosition()), "projectile ownership/physics/ticking");
            if (landing != null) premise(!shot.getBoundingBox().inflate(1D, 0.5D, 1D).intersects(user.getBoundingBox()), "owner pickup-touch volume overlaps");
        }
        private void stable(Frame value) {
            premise(value.grounded && !value.removed && value.position.distanceToSqr(landing.position) < EPS * EPS
                    && value.velocity.lengthSqr() < EPS * EPS && !value.noGravity && !value.noPhysics && !value.creativeOnly
                    && value.ownerAlive && value.ticking && value.ownerTicking && user.getUUID().equals(value.owner), "stable grounded control changed: " + value);
        }
        private void removeSupport() {
            ownerAndRoom(); shotGeometry();
            premise(stablePairs >= 2 && impacts == 1 && impactPos != null && removal == null && pending == null
                    && lastAfter != null && lastAfter.age == shot.tickCount, "support removal not between qualified natural updates");
            Frame before = frame(); stable(before); AABB probe = pointProbe();
            premise(before.atImpact == impactState && !before.probeClear && level.getFluidState(impactPos).isEmpty(),
                    "captured support is not the collider at the retained point");
            // Explicit world-removal transport: no mining/loot claim, no game-rule change.
            boolean destroyed = level.destroyBlock(impactPos, false, user);
            Frame after = frame(); removal = new Removal(before, after, probe, destroyed);
            log("DESTROY block=" + impactPos + " impactState=" + impactState + " dropBlock=false result=" + removal);
            premise(destroyed && after.atImpact.isAir() && after.atPosition != impactState && after.probeClear
                    && level.getFluidState(impactPos).isEmpty() && !after.removed && after.grounded
                    && after.position.distanceToSqr(before.position) < EPS * EPS && after.velocity.lengthSqr() < EPS * EPS,
                    "actual support change/clear point did not qualify before a projectile update");
            ownerAndRoom();
        }

        private void validate() {
            ownerAndRoom();
            premise(released && usePres == 20 && shots.size() == 1 && impacts == 1 && stablePairs >= 2 && removal != null
                    && removal.destroyed && !removal.before.probeClear && removal.after.probeClear && !releaseSteps.isEmpty()
                    && inventoryCount() == 0, "missing actual throw/control/removal chain");
            Step first = releaseSteps.getFirst();
            premise(first.before.age == removal.after.age + 1 && first.before.time >= removal.after.time
                    && first.before.grounded && !first.before.removed && first.before.probeClear && first.before.atImpact.isAir()
                    && first.before.atPosition != impactState && first.before.ownerAlive && first.before.ticking && first.before.ownerTicking
                    && !first.before.noGravity && !first.before.noPhysics && !first.before.creativeOnly
                    && user.getUUID().equals(first.before.owner), "first observed tick was not the eligible grounded support-loss update");
            log("BOUNDARY first=" + first + " removal=" + removal + " releasePairs=" + releaseSteps.size() + " leave=" + leave);
            oracle(!first.after.grounded && !first.after.removed && first.after.reason == null,
                    "first eligible natural update kept IN_GROUND after actual support loss");
            oracle(first.after.position.distanceToSqr(first.before.position) < EPS * EPS, "release update moved the projectile before the next flight tick");
            oracle(releaseSteps.size() == 3 && releaseSteps.stream().allMatch(step -> !step.after.grounded && !step.after.removed
                    && !step.after.noGravity && !step.after.noPhysics && step.after.ownerAlive && user.getUUID().equals(step.after.owner)),
                    "natural resumed updates or identity were lost");
            Frame end = releaseSteps.getLast().after;
            oracle(end.position.distanceToSqr(removal.after.position) > EPS * EPS && end.velocity.lengthSqr() > EPS * EPS
                    && shot.isAlive() && level.getEntity(shot.getUUID()) == shot && drops.isEmpty(), "projectile did not naturally resume after releasing support");
            log("RESULT clearFlagFirst=true naturalResume=true releasePairs=3 exactReleaseVectorNotClaimed=true native=false");
        }
        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                if (done) { validate(); close(); helper.succeed(); return; }
                premise(helper.getTick() < 80, "finite watchdog pairs=" + pairs + " stable=" + stablePairs + " release=" + releaseSteps.size());
                if (!using) {
                    premise(helper.getTick() < 40, "ordinary readiness deadline");
                    if (userPosts >= 3 && user.onGround() && user.position().distanceToSqr(ownerFeet) < EPS * EPS) {
                        ownerAndRoom();
                        premise(hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && hamon.getEnergy() > 200
                                && inventoryCount() == 1 && user.getMainHandItem().getCount() == 1, "skill/energy/plain item");
                        premise(user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
                                && user.isUsingItem(), "registered use rejected");
                        using = true; useStartPosts = userPosts; log("USE userPosts=" + userPosts);
                    }
                }
                else if (!released) {
                    ownerAndRoom(); premise(user.isUsingItem(), "use stopped before natural release");
                    if (user.getTicksUsingItem() >= 20) {
                        premise(user.getTicksUsingItem() == 20 && usePres == 20 && userPosts - useStartPosts == 20, "not exactly20 natural use ticks");
                        releasing = true; try { user.releaseUsingItem(); } finally { releasing = false; }
                        released = true;
                        premise(shots.size() == 1 && shot != null && shot.isAlive() && level.getEntity(shot.getUUID()) == shot
                                && !user.isUsingItem() && inventoryCount() == 0, "throw/consumption failed");
                        log("RELEASE measuredUsePres=" + usePres + " UUID=" + shot.getUUID());
                    }
                }
                else {
                    ownerAndRoom(); premise(!shot.isRemoved(), "unpaired disappearance: " + leave);
                    premise(landing != null || shot.tickCount <= 8, "no natural owned-wall landing by age8");
                    if (removal == null && stablePairs >= 2) removeSupport();
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private <T extends Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable action) {
            if (closed || observerFailure != null) return;
            try { action.run(); } catch (RuntimeException | Error error) { observerFailure = error; log("OBSERVER_FAILURE " + error + " pre=" + pending); }
        }
        private void cleanup(Runnable action, List<Throwable> failures) {
            try { action.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures); listeners.clear();
            cleanup(() -> { if (user != null) user.stopUsingItem(); }, failures);
            for (ClackersEntity entity : shots) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (ItemEntity item : drops) cleanup(() -> { if (!item.isRemoved()) item.discard(); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (user != null) user.getInventory().clearContent(); }, failures);
            cleanup(() -> { if (user != null && !user.isRemoved()) user.discard(); }, failures);
            for (var entry : originals.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = originals.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean gone = shots.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null)
                        && drops.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null)
                        && (user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null);
                boolean inputOff = user == null || !user.isUsingItem();
                log("CLEANUP cells=" + originals.size() + " exactRestore=" + restored + " ownedGone=" + gone + " inputOff=" + inputOff
                        + " shots=" + shots.size() + " projectileItems=" + drops.size() + " listeners=0");
                if (!restored || !gone || !inputOff) throw new IllegalStateException("Clackers support cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("Clackers support cleanup failed");
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
