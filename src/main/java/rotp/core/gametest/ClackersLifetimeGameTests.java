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
public final class ClackersLifetimeGameTests {
    private static final double EPS = 1.0E-6D;
    private static final int LAST_AGE = 1202;

    private ClackersLifetimeGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_recoverable_lifetime", timeoutTicks = 1300)
    public static void liveOwnerSurvivalClackersSurviveTheTotalAgeBoundary(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record Frame(long time, int age, Vec3 pos, Vec3 delta, AABB box, UUID owner,
            boolean ownerAlive, boolean ownerTicking, boolean ticking, boolean grounded,
            boolean noGravity, boolean noPhysics, boolean removed, Entity.RemovalReason reason,
            boolean creativeOnly, int savedAge, int inventoryCount) {}
    private record Step(Frame before, Frame after) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<ClackersEntity> shots = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private HamonData hamon;
        private ClackersEntity shot;
        private AABB room;
        private ChunkPos chunk;
        private Vec3 ownerFeet;
        private Frame pending;
        private Frame landing;
        private Frame leave;
        private Step terminal;
        private Throwable observerFailure;
        private int userPosts;
        private int useStartPosts;
        private int usePres;
        private int impacts;
        private boolean using;
        private boolean releasing;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }
        private void premise(boolean value, String text) { helper.assertTrue(value, "CLACKERS-LIFE-PREMISE " + text); }
        private void oracle(boolean value, String text) { helper.assertTrue(value, "CLACKERS-LIFE-ORACLE " + text); }
        private void log(String text) { System.out.println("[CLACKERS-LIFE] " + text); }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(origin);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = origin.getY() + 24;
            room = new AABB(x + 3, y - 1, z + 1, x + 13, y + 5, z + 13);
            ownerFeet = new Vec3(x + 8.5D, y, z + 3.5D);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY <= level.getMaxBuildHeight(), "room build bounds");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null
                        && level.isPositionEntityTicking(pos) && new ChunkPos(pos).equals(chunk), "room not empty and ticking at " + pos);
            }
            premise(level.getEntities((Entity) null, room).isEmpty(), "foreign entity in room");
            for (int bx = x + 3; bx < x + 13; bx++) for (int bz = z + 1; bz < z + 13; bz++) {
                place(new BlockPos(bx, y - 1, bz)); place(new BlockPos(bx, y + 4, bz));
            }
            for (int bx = x + 6; bx <= x + 10; bx++) for (int by = y; by < y + 4; by++) {
                BlockPos pos = new BlockPos(bx, by, z + 9); wall.add(pos); place(pos);
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.moveTo(ownerFeet.x, ownerFeet.y + 0.2D, ownerFeet.z, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
            premise(level.addFreshEntity(user), "could not add ordinary owner");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
            hamon.setBreathStability(hamon.getMaxBreathStability()); hamon.setEnergy(hamon.getMaxEnergy());
            observers();
            log("SETUP owner=" + user.getUUID() + " chunk=" + chunk + " room=" + room + " cells=" + originals.size()
                    + " ordinaryGravity=" + !user.isNoGravity() + " lifetimeEnd=" + LAST_AGE + " timeout=1300");
        }

        private void place(BlockPos pos) {
            originals.put(pos.immutable(), level.getBlockState(pos));
            premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned stone placement failed");
        }

        private void observers() {
            add((EntityJoinLevelEvent event) -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof ClackersEntity created && created.getOwner() == user) {
                    shots.add(created);
                    observe(() -> {
                        premise(releasing && shot == null && !event.isCanceled(), "unexpected/canceled owned emission");
                        shot = created;
                        CompoundTag tag = shot.saveWithoutId(new CompoundTag());
                        ItemStack pickup = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("PickupItem"));
                        log("JOIN uuid=" + shot.getUUID() + " frame=" + frame() + " pickup=" + pickup
                                + " hamonSpent=" + tag.getFloat("HamonSpent") + " usePres=" + usePres);
                        premise(shot.getType() == ModEntityTypes.CLACKERS.get() && shot.tickCount == 0
                                && tag.contains("CreativeOnlyPickup", 1) && !tag.getBoolean("CreativeOnlyPickup") && pickup.is(ModItems.CLACKERS.get())
                                && pickup.getCount() == 1 && tag.getFloat("HamonSpent") == 100.0F,
                                "ordinary registered pickup/20-tick emission unavailable");
                    });
                }
                else if (event.getEntity() instanceof ItemEntity item && pending != null && shot != null
                        && item.getOwner() == user && item.getItem().is(ModItems.CLACKERS.get())
                        && room.contains(item.position()) && item.position().distanceToSqr(shot.position()) <= 1.0D) {
                    // Ownership is tied to this projectile's active natural tick, not a room sweep.
                    drops.add(item);
                    log("OWNED_ITEM uuid=" + item.getUUID() + " time=" + level.getGameTime() + " pre=" + pending
                            + " stack=" + item.getItem() + " canceled=" + event.isCanceled());
                }
            }, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Pre event) -> observe(() -> {
                if (event.getEntity() == user && using && !released) {
                    premise(!event.isCanceled() && user.isUsingItem() && user.getUseItem().is(ModItems.CLACKERS.get()), "use tick admission");
                    usePres++;
                }
                if (event.getEntity() == shot && terminal == null) {
                    premise(!event.isCanceled() && pending == null, "canceled/unpaired projectile tick");
                    pending = frame();
                    requireOwner(); requireGeometry();
                    premise(pending.age == steps.size() + 1 && pending.savedAge == pending.age && !pending.removed,
                            "non-natural age sequence: " + pending);
                    if (!steps.isEmpty()) premise(pending.time == steps.getLast().after.time + 1, "missing natural tick");
                    if (landing != null) requireGrounded(pending);
                }
            }), EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Post event) -> observe(() -> {
                if (event.getEntity() == user) userPosts++;
                if (event.getEntity() == shot && terminal == null) {
                    Frame after = frame();
                    premise(pending != null && pending.age == after.age && pending.time == after.time, "Post lacks matching Pre");
                    Step step = new Step(pending, after); steps.add(step);
                    if (landing == null && after.grounded && impacts == 1) {
                        landing = after; log("LANDING " + after);
                    }
                    if (after.removed || after.age >= 1199 || after.age % 200 == 0) log("STEP " + step);
                    if (after.removed || after.age == LAST_AGE) terminal = step;
                    pending = null;
                }
            }), EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add((ProjectileImpactEvent event) -> observe(() -> {
                if (event.getProjectile() != shot || terminal != null) return;
                impacts++;
                log("IMPACT age=" + shot.tickCount + " result=" + event.getRayTraceResult() + " canceled=" + event.isCanceled());
                premise(pending != null && landing == null && impacts == 1 && !event.isCanceled()
                        && event.getRayTraceResult() instanceof BlockHitResult hit && wall.contains(hit.getBlockPos())
                        && hit.getDirection() == Direction.NORTH, "not the sole natural owned-wall impact");
            }), ProjectileImpactEvent.class, EventPriority.LOWEST, true);
            add((EntityLeaveLevelEvent event) -> observe(() -> {
                if (!closed && event.getLevel() == level && event.getEntity() == shot) {
                    leave = frame(); log("LEAVE frame=" + leave + " pending=" + pending + " completedPairs=" + steps.size());
                }
            }), EntityLeaveLevelEvent.class, EventPriority.LOWEST, false);
        }

        private Frame frame() {
            CompoundTag tag = shot.saveWithoutId(new CompoundTag());
            Entity owner = shot.getOwner();
            return new Frame(level.getGameTime(), shot.tickCount, shot.position(), shot.getDeltaMovement(), shot.getBoundingBox(),
                    owner == null ? null : owner.getUUID(), user.isAlive(), level.isPositionEntityTicking(user.blockPosition()),
                    level.isPositionEntityTicking(shot.blockPosition()), shot.isInGround(), shot.isNoGravity(), shot.noPhysics,
                    shot.isRemoved(), shot.getRemovalReason(), tag.getBoolean("CreativeOnlyPickup"), tag.getInt("Age"), inventoryCount());
        }
        private int inventoryCount() { return user.getInventory().countItem(ModItems.CLACKERS.get()); }
        private void requireOwner() {
            premise(user.isAlive() && !user.isRemoved() && level.getEntity(user.getUUID()) == user
                    && !user.isCreative() && !user.getAbilities().instabuild && !user.isInvulnerable()
                    && !user.getAbilities().invulnerable && !user.isNoGravity() && !user.noPhysics
                    && user.onGround() && user.position().distanceToSqr(ownerFeet) < EPS * EPS
                    && level.isPositionEntityTicking(user.blockPosition()), "owner departed ordinary live/ticking state");
            premise(user.getDeltaMovement().horizontalDistanceSqr() < EPS * EPS, "owner horizontal motion");
            premise(originals.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)
                    && level.getBlockEntity(pos) == null), "owned support changed");
            premise(level.getEntities((Entity) null, room).stream().allMatch(entity -> entity == user || shots.contains(entity)
                    || drops.contains(entity)), "foreign entity entered owned room");
        }
        private void requireGeometry() {
            AABB query = shot.getBoundingBox().expandTowards(shot.getDeltaMovement()).inflate(1.0D);
            premise(query.minX >= room.minX && query.maxX < room.maxX && query.minY >= room.minY && query.maxY < room.maxY
                    && query.minZ >= room.minZ && query.maxZ < room.maxZ, "projectile/query left owned room: " + query);
            premise(shot.getOwner() == user && !shot.isNoGravity() && !shot.noPhysics && !shot.isInWaterOrBubble()
                    && level.isPositionEntityTicking(shot.blockPosition()), "projectile owner/physics/ticking changed");
            if (landing != null) premise(!shot.getBoundingBox().inflate(1.0D, 0.5D, 1.0D).intersects(user.getBoundingBox()),
                    "owner pickup-touch volume overlaps after landing");
        }
        private void requireGrounded(Frame current) {
            premise(current.grounded && current.pos.distanceToSqr(landing.pos) < EPS * EPS
                    && current.delta.lengthSqr() < EPS * EPS && !current.noGravity && !current.noPhysics && !current.creativeOnly && current.ownerAlive
                    && current.ownerTicking && current.ticking && user.getUUID().equals(current.owner), "grounded lifetime premise changed: " + current);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                if (terminal != null) { validate(); close(); helper.succeed(); return; }
                premise(helper.getTick() < 1270, "finite watchdog; pairs=" + steps.size() + " pending=" + pending + " leave=" + leave);
                if (!using) {
                    premise(helper.getTick() < 40, "ordinary ground/warmup readiness deadline");
                    if (userPosts >= 3 && user.onGround() && user.position().distanceToSqr(ownerFeet) < EPS * EPS) {
                        requireOwner();
                        premise(hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && hamon.getEnergy() > 200
                                && inventoryCount() == 1 && user.getMainHandItem().getCount() == 1, "skill/energy/plain stack missing");
                        premise(user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
                                && user.isUsingItem(), "registered item use rejected");
                        using = true; useStartPosts = userPosts; log("USE posts=" + userPosts + " energy=" + hamon.getEnergy());
                    }
                }
                else if (!released) {
                    requireOwner(); premise(user.isUsingItem(), "charge stopped before real release");
                    if (user.getTicksUsingItem() >= 20) {
                        premise(user.getTicksUsingItem() == 20 && usePres == 20 && userPosts - useStartPosts == 20,
                                "release did not follow exactly20 natural use ticks");
                        releasing = true;
                        try { user.releaseUsingItem(); } finally { releasing = false; }
                        released = true;
                        premise(shots.size() == 1 && shot != null && shot.isAlive() && level.getEntity(shot.getUUID()) == shot
                                && !user.isUsingItem() && inventoryCount() == 0, "throw/consumption failed");
                        log("RELEASE usePres=" + usePres + " ownerPosts=" + userPosts + " consumed=1 uuid=" + shot.getUUID());
                    }
                }
                else {
                    requireOwner();
                    premise(shot != null && (!shot.isRemoved() || terminal != null), "removal outside observed natural Post: " + leave);
                    premise(landing != null || shot.tickCount <= 8, "no natural owned-wall landing by age8");
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void validate() {
            requireOwner();
            premise(released && usePres == 20 && shots.size() == 1 && impacts == 1 && landing != null && landing.age <= 8,
                    "terminal sample lacks registered throw and sole natural landing");
            premise(steps.size() >= 1201 && steps.get(1199).after.age == 1200 && !steps.get(1199).after.removed
                    && terminal.before.age >= 1201 && terminal.before.age <= LAST_AGE && !terminal.before.removed,
                    "loss did not reach the qualified original age boundary: " + terminal);
            for (Step step : steps) {
                if (step.after.age >= landing.age) requireGrounded(step.after);
                if (step.before.age > landing.age) requireGrounded(step.before);
            }
            premise(terminal.after.inventoryCount == 0 && terminal.after.savedAge == terminal.after.age,
                    "inventory recovery or synthetic age at terminal sample");
            log("BOUNDARY pairs=" + steps.size() + " age1200=" + steps.get(1199) + " terminal=" + terminal
                    + " leave=" + leave + " exactOwnedReplacementItems=" + drops.size());
            oracle(!terminal.after.removed && terminal.after.reason == null && shot.isAlive()
                    && level.getEntity(shot.getUUID()) == shot && drops.isEmpty(), "recoverable projectile lost at total-age boundary");
            oracle(terminal.after.age == LAST_AGE && steps.size() == LAST_AGE, "did not retain the projectile through age1202");
            log("RESULT donorProjectileRetained=true naturalPairs=" + steps.size() + " liveOwner=true native=false");
        }

        private <T extends Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable action) {
            if (closed || observerFailure != null) return;
            try { action.run(); } catch (RuntimeException | Error error) { observerFailure = error; log("OBSERVATION_FAILURE " + error + " pending=" + pending); }
        }
        private void cleanup(Runnable action, List<Throwable> failures) {
            try { action.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
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
                log("CLEANUP cells=" + originals.size() + " exactRestore=" + restored + " ownedGone=" + gone
                        + " inputOff=" + inputOff + " shots=" + shots.size() + " drops=" + drops.size() + " listeners=0");
                if (!restored || !gone || !inputOff) throw new IllegalStateException("Clackers lifetime cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("Clackers lifetime cleanup failed");
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
