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
public final class ClackersCreativeGroundExpiryGameTests {
    private static final double EPS = 1.0E-6D;
    private static final int LAST_GROUNDED_UPDATE = 1200;
    private static final int MIN_LANDING_AGE = 2;
    private static final int MAX_LANDING_AGE = 8;
    private static final int OWNED_CELLS = 260;

    private ClackersCreativeGroundExpiryGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_creative_ground_expiry", timeoutTicks = 1300)
    public static void creativeClackersExpireOnTheir1200thGroundedUpdate(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record Frame(long time, int age, Vec3 pos, Vec3 delta, AABB box, UUID owner,
            boolean ownerAlive, boolean ownerTicking, boolean ticking, boolean chunksReady,
            boolean grounded, boolean noGravity, boolean noPhysics, boolean removed,
            Entity.RemovalReason reason, boolean creativeOnly, int savedAge, int inventoryCount, int heldCount) {}
    private record Step(Frame before, Frame after) {}
    private record Impact(long time, int age, BlockPos block, Direction face, Vec3 location,
            Vec3 start, Vec3 delta, double fraction) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<BlockPos> chunkChecks = new ArrayList<>();
        private final List<ChunkPos> chunks = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<ClackersEntity> shots = new ArrayList<>();
        private final List<ItemEntity> actorOwnedItems = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private HamonData hamon;
        private ClackersEntity shot;
        private AABB room;
        private Vec3 ownerFeet;
        private Frame pending;
        private Frame landing;
        private Frame leave;
        private Impact impact;
        private Step ground1199;
        private Step terminal;
        private Throwable observerFailure;
        private long lastOwnerPostTime = Long.MIN_VALUE;
        private int userPosts;
        private int useStartPosts;
        private int usePres;
        private int impacts;
        private int groundPairs;
        private boolean using;
        private boolean releasing;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }
        private void premise(boolean value, String text) { helper.assertTrue(value, "CLACKERS-CREATIVE-EXPIRY-PREMISE " + text); }
        private void oracle(boolean value, String text) { helper.assertTrue(value, "CLACKERS-CREATIVE-EXPIRY-ORACLE " + text); }
        private void log(String text) { System.out.println("[CLACKERS-CREATIVE-EXPIRY] " + text); }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            ChunkPos first = new ChunkPos(origin);
            int x = first.getMinBlockX(), z = first.getMinBlockZ(), y = origin.getY() + 24;
            chunks.add(first);
            chunkChecks.add(new BlockPos(x + 8, y, z + 8));
            room = new AABB(x + 3, y - 1, z + 1, x + 13, y + 5, z + 13);
            ownerFeet = new Vec3(x + 8.5D, y, z + 3.5D);
            premise(chunks.size() == 1, "one declared template chunk");
            premise(room.minY >= level.getMinBuildHeight() && room.maxY <= level.getMaxBuildHeight(), "actual room build bounds");
            requireChunks();
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(chunks.contains(new ChunkPos(pos)) && level.isPositionEntityTicking(pos), "room cell is not already ticking: " + pos);
                premise(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "room cell is not empty: " + pos);
            }
            premise(level.getEntities((Entity) null, room).isEmpty(), "foreign entity in declared room");
            for (int bx = x + 3; bx < x + 13; bx++) for (int bz = z + 1; bz < z + 13; bz++) {
                place(new BlockPos(bx, y - 1, bz)); place(new BlockPos(bx, y + 4, bz));
            }
            for (int bx = x + 6; bx <= x + 10; bx++) for (int by = y; by < y + 4; by++) {
                BlockPos pos = new BlockPos(bx, by, z + 9); wall.add(pos); place(pos);
            }
            premise(originals.size() == OWNED_CELLS && wall.size() == 20, "declared owned-cell count changed");
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.CREATIVE);
            GameType.CREATIVE.updatePlayerAbilities(user.getAbilities());
            user.moveTo(ownerFeet.x, ownerFeet.y + 0.2D, ownerFeet.z, 0, 0);
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
            premise(level.addFreshEntity(user), "could not add Creative owner");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
            hamon.setBreathStability(hamon.getMaxBreathStability()); hamon.setEnergy(hamon.getMaxEnergy());
            observers();
            log("SETUP owner=" + user.getUUID() + " chunks=" + chunks + " room=" + room + " cells=" + originals.size()
                    + " landingAge=[2,8] groundDeadline=" + LAST_GROUNDED_UPDATE + " timeout=1300");
        }

        private boolean chunkReady(int index) {
            ChunkPos chunk = chunks.get(index);
            return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                    && level.isPositionEntityTicking(chunkChecks.get(index));
        }
        private boolean chunksReady() { return chunkReady(0); }
        private void requireChunks() {
            premise(chunkReady(0), "declared template chunk must already be loaded and entity-ticking: " + chunks);
        }
        private void place(BlockPos pos) {
            premise(!originals.containsKey(pos), "duplicate owned cell " + pos);
            originals.put(pos.immutable(), level.getBlockState(pos));
            premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned stone placement failed");
        }

        private void observers() {
            add((EntityJoinLevelEvent event) -> {
                if (closed || event.getLevel() != level || user == null) return;
                if (event.getEntity() instanceof ClackersEntity created && created.getOwner() == user) {
                    shots.add(created);
                    observe(() -> {
                        premise(releasing && shot == null && !event.isCanceled(), "unexpected/canceled owned emission");
                        shot = created;
                        CompoundTag tag = shot.saveWithoutId(new CompoundTag());
                        ItemStack pickup = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("PickupItem"));
                        log("JOIN uuid=" + shot.getUUID() + " frame=" + frame() + " pickup=" + pickup
                                + " hamonSpent=" + tag.getFloat("HamonSpent") + " usePres=" + usePres);
                        premise(shot.getType() == ModEntityTypes.CLACKERS.get() && shot.tickCount == 0 && shot.getOwner() == user
                                && tag.contains("CreativeOnlyPickup", 1) && tag.getBoolean("CreativeOnlyPickup")
                                && pickup.is(ModItems.CLACKERS.get()) && pickup.getCount() == 1
                                && tag.getFloat("HamonSpent") == 100.0F && !shot.isNoGravity() && !shot.noPhysics,
                                "registered Creative/20-tick emission unavailable");
                    });
                }
                else if (event.getEntity() instanceof ItemEntity item && item.getOwner() == user) {
                    // Exact actor ownership is for cleanup only, not a projectile replacement claim.
                    actorOwnedItems.add(item);
                }
            }, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Pre event) -> observe(() -> {
                if (event.getEntity() == user && using && !released) {
                    premise(!event.isCanceled() && user.isUsingItem() && user.getUseItem().is(ModItems.CLACKERS.get()), "use tick admission");
                    usePres++;
                }
                if (event.getEntity() == shot && terminal == null) {
                    premise(!event.isCanceled() && pending == null, "canceled/unpaired projectile tick");
                    requireOwner(); requireGeometry();
                    pending = frame();
                    premise(pending.age == steps.size() + 1 && pending.savedAge == pending.age && !pending.removed,
                            "non-natural age sequence: " + pending);
                    if (!steps.isEmpty()) premise(pending.time == steps.getLast().after.time + 1, "missing natural projectile tick");
                    if (landing != null) {
                        requireGrounded(pending);
                        if (groundPairs == LAST_GROUNDED_UPDATE - 1) log("DEADLINE_PRE completedG=" + groundPairs + " " + pending);
                    }
                    else premise(!pending.grounded && impacts == 0, "grounding occurred outside the admitted impact");
                }
            }), EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Post event) -> observe(() -> {
                if (event.getEntity() == user) {
                    long time = level.getGameTime();
                    premise(lastOwnerPostTime == Long.MIN_VALUE || time == lastOwnerPostTime + 1, "owner did not tick continuously");
                    lastOwnerPostTime = time; userPosts++;
                }
                if (event.getEntity() == shot && terminal == null) {
                    Frame after = frame();
                    premise(pending != null && pending.age == after.age && pending.time == after.time, "Post lacks matching Pre");
                    Step step = new Step(pending, after); steps.add(step);
                    premise(after.chunksReady && after.savedAge == after.age, "Post chunk readiness/saved Age changed");
                    if (landing == null) {
                        if (after.grounded) {
                            premise(impact != null && impacts == 1 && !pending.grounded && after.age >= MIN_LANDING_AGE
                                    && after.age <= MAX_LANDING_AGE && impact.age == after.age
                                    && after.pos.distanceToSqr(impact.location) < EPS * EPS, "unqualified natural wall landing");
                            landing = after;
                            requireGrounded(after);
                            log("LANDING K=" + landing.age + " G=0 impact=" + impact + " frame=" + landing);
                        }
                        else premise(impacts == 0 && after.age < MAX_LANDING_AGE, "no admitted wall landing by age8");
                    }
                    else {
                        requireGrounded(pending); requireGrounded(after);
                        groundPairs++;
                        premise(groundPairs == after.age - landing.age, "non-continuous natural grounded count");
                    }
                    if (groundPairs == LAST_GROUNDED_UPDATE - 1) ground1199 = step;
                    if (after.removed || groundPairs >= 1198 || groundPairs > 0 && groundPairs % 200 == 0)
                        log("STEP K=" + (landing == null ? -1 : landing.age) + " G=" + groundPairs + " " + step);
                    if (after.removed || groundPairs == LAST_GROUNDED_UPDATE) terminal = step;
                    pending = null;
                }
            }), EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add((ProjectileImpactEvent event) -> observe(() -> {
                if (event.getProjectile() != shot || terminal != null) return;
                impacts++;
                premise(pending != null && landing == null && impacts == 1 && !event.isCanceled()
                        && event.getRayTraceResult() instanceof BlockHitResult, "not the sole uncanceled natural BLOCK impact");
                BlockHitResult hit = (BlockHitResult) event.getRayTraceResult();
                premise(wall.contains(hit.getBlockPos()) && hit.getDirection() == Direction.NORTH
                        && pending.age >= MIN_LANDING_AGE && pending.age <= MAX_LANDING_AGE,
                        "not the declared NORTH wall at age2..8: " + hit);
                premise(shot.tickCount == pending.age && shot.position().distanceToSqr(pending.pos) < EPS * EPS
                        && shot.getDeltaMovement().distanceToSqr(pending.delta) < EPS * EPS && pending.delta.z > 0,
                        "impact did not use the observed natural move segment");
                double fraction = (hit.getLocation().z - pending.pos.z) / pending.delta.z;
                premise(fraction >= 0 && fraction <= 1 && pending.pos.add(pending.delta.scale(fraction))
                        .distanceToSqr(hit.getLocation()) < EPS * EPS, "actual impact is outside the observed segment");
                impact = new Impact(level.getGameTime(), shot.tickCount, hit.getBlockPos().immutable(), hit.getDirection(),
                        hit.getLocation(), pending.pos, pending.delta, fraction);
                log("IMPACT " + impact);
            }), ProjectileImpactEvent.class, EventPriority.LOWEST, true);
            add((EntityLeaveLevelEvent event) -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() == shot) {
                    leave = frame(); log("LEAVE frame=" + leave + " pending=" + pending + " G=" + groundPairs);
                }
            }), EntityLeaveLevelEvent.class, EventPriority.LOWEST, false);
        }

        private Frame frame() {
            CompoundTag tag = shot.saveWithoutId(new CompoundTag());
            Entity owner = shot.getOwner();
            return new Frame(level.getGameTime(), shot.tickCount, shot.position(), shot.getDeltaMovement(), shot.getBoundingBox(),
                    owner == null ? null : owner.getUUID(), owner != null && owner.isAlive(),
                    level.isPositionEntityTicking(user.blockPosition()), level.isPositionEntityTicking(shot.blockPosition()), chunksReady(),
                    shot.isInGround(), shot.isNoGravity(), shot.noPhysics, shot.isRemoved(), shot.getRemovalReason(),
                    tag.getBoolean("CreativeOnlyPickup"), tag.getInt("Age"), inventoryCount(), user.getMainHandItem().getCount());
        }
        private int inventoryCount() { return user.getInventory().countItem(ModItems.CLACKERS.get()); }
        private void requireOwner() {
            requireChunks();
            long time = level.getGameTime();
            premise(user.isAlive() && !user.isRemoved() && level.getEntity(user.getUUID()) == user && !user.isPassenger()
                    && user.isCreative() && user.getAbilities().instabuild && user.getAbilities().invulnerable
                    && !user.getAbilities().flying && !user.isNoGravity() && !user.noPhysics
                    && user.onGround() && user.position().distanceToSqr(ownerFeet) < EPS * EPS
                    && level.isPositionEntityTicking(user.blockPosition()), "owner departed declared stationary Creative state");
            premise(lastOwnerPostTime == time || lastOwnerPostTime == time - 1, "owner has no recent natural Post");
            premise(user.getDeltaMovement().horizontalDistanceSqr() < EPS * EPS, "owner horizontal motion");
            premise(originals.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)
                    && level.getBlockEntity(pos) == null), "owned support changed");
            premise(level.getEntities((Entity) null, room).stream().allMatch(entity -> entity == user || shots.contains(entity)
                    || actorOwnedItems.contains(entity)), "foreign entity entered declared room");
            premise(inventoryCount() == 1 && user.getMainHandItem().is(ModItems.CLACKERS.get())
                    && user.getMainHandItem().getCount() == 1, "Creative held stack was consumed/changed");
        }
        private void requireGeometry() {
            AABB query = shot.getBoundingBox().expandTowards(shot.getDeltaMovement()).inflate(1.0D);
            premise(query.minX >= room.minX && query.maxX < room.maxX && query.minY >= room.minY && query.maxY < room.maxY
                    && query.minZ >= room.minZ && query.maxZ < room.maxZ, "projectile/query left declared room: " + query);
            premise(shot.getOwner() == user && !shot.isNoGravity() && !shot.noPhysics && !shot.isInWaterOrBubble()
                    && !shot.isPassenger() && level.isPositionEntityTicking(shot.blockPosition()), "projectile owner/physics/ticking changed");
            if (landing != null) premise(!shot.getBoundingBox().inflate(1.0D, 0.5D, 1.0D).intersects(user.getBoundingBox()),
                    "owner pickup-touch volume overlaps after landing");
        }
        private void requireGrounded(Frame current) {
            premise(current.grounded && current.pos.distanceToSqr(landing.pos) < EPS * EPS && current.delta.lengthSqr() < EPS * EPS
                    && !current.noGravity && !current.noPhysics && current.creativeOnly && current.ownerAlive && current.ownerTicking
                    && current.ticking && current.chunksReady && user.getUUID().equals(current.owner)
                    && current.inventoryCount == 1 && current.heldCount == 1, "continuous ground premise changed: " + current);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                if (terminal != null) { validate(); close(); helper.succeed(); return; }
                premise(helper.getTick() < 1270, "finite watchdog; pairs=" + steps.size() + " G=" + groundPairs + " pending=" + pending + " leave=" + leave);
                requireChunks();
                if (!using) {
                    premise(helper.getTick() < 40, "initial natural grounding/readiness deadline");
                    if (userPosts >= 3 && user.onGround() && user.position().distanceToSqr(ownerFeet) < EPS * EPS) {
                        requireOwner();
                        premise(hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && hamon.getEnergy() > 200,
                                "skill/initial energy unavailable");
                        premise(user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
                                && user.isUsingItem(), "registered Creative item use rejected");
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
                                && !user.isUsingItem() && inventoryCount() == 1 && user.getMainHandItem().getCount() == 1,
                                "Creative emission/nonconsumption failed");
                        log("RELEASE usePres=" + usePres + " ownerPosts=" + userPosts + " held=1 uuid=" + shot.getUUID());
                    }
                }
                else {
                    requireOwner();
                    premise(shot != null && !shot.isRemoved(), "removal outside the observed terminal Post: " + leave);
                    premise(landing != null || shot.tickCount < MAX_LANDING_AGE, "no natural owned-wall landing by age8");
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void validate() {
            requireOwner();
            premise(released && usePres == 20 && shots.size() == 1 && impacts == 1 && impact != null && landing != null
                    && landing.age >= MIN_LANDING_AGE && landing.age <= MAX_LANDING_AGE && originals.size() == OWNED_CELLS,
                    "final expiry lacks genuine Creative throw/qualified wall landing");
            log("ENDPOINT_OBSERVED K=" + landing.age + " G=" + groundPairs + " pairs=" + steps.size()
                    + " ground1199=" + ground1199 + " terminal=" + terminal + " leave=" + leave
                    + " actorOwnedItemRefs=" + actorOwnedItems.size());
            premise(ground1199 != null && !ground1199.after.removed
                    && ground1199.after.age == landing.age + LAST_GROUNDED_UPDATE - 1
                    && groundPairs == LAST_GROUNDED_UPDATE && steps.size() == landing.age + LAST_GROUNDED_UPDATE
                    && terminal.before.age == landing.age + LAST_GROUNDED_UPDATE
                    && terminal.after.age == terminal.before.age && !terminal.before.removed
                    && terminal.before.time == ground1199.after.time + 1,
                    "G1199-alive/G1200-eligible endpoint unavailable; earlier total-age loss is not this expiry control");
            for (Step step : steps) {
                if (step.after.age >= landing.age) requireGrounded(step.after);
                if (step.before.age > landing.age) requireGrounded(step.before);
            }
            premise(terminal.after.savedAge == terminal.after.age && terminal.after.inventoryCount == 1
                    && terminal.after.heldCount == 1 && shot.getOwner() == user,
                    "saved Age, live owner or unchanged Creative held stack lost at deadline");
            oracle(terminal.after.removed && terminal.after.reason == Entity.RemovalReason.DISCARDED
                    && !shot.isAlive() && level.getEntity(shot.getUUID()) == null,
                    "Creative projectile did not discard on its FIRST1200th admitted grounded update");
            oracle(actorOwnedItems.isEmpty(), "expiry emitted an owned item instead of discarding without replacement");
            log("RESULT exactGroundExpiry=1200 landingK=" + landing.age + " totalAge=" + terminal.after.age
                    + " held=1 actualDiscarded=true noOwnedItems=true native=false");
        }

        private <T extends Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable action) {
            if (closed || observerFailure != null) return;
            try { action.run(); } catch (RuntimeException | Error error) {
                observerFailure = error; log("OBSERVATION_FAILURE " + error + " pending=" + pending + " G=" + groundPairs);
            }
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
            for (ItemEntity item : actorOwnedItems) cleanup(() -> { if (!item.isRemoved()) item.discard(); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (user != null) user.getInventory().clearContent(); }, failures);
            cleanup(() -> { if (user != null && !user.isRemoved()) user.discard(); }, failures);
            for (var entry : originals.entrySet()) cleanup(() -> {
                ChunkPos chunk = new ChunkPos(entry.getKey());
                if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                    throw new IllegalStateException("Owned restore chunk unavailable; refusing forced load at " + entry.getKey());
                }
                level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            }, failures);
            cleanup(() -> {
                boolean restored = originals.entrySet().stream().allMatch(entry -> {
                    ChunkPos chunk = new ChunkPos(entry.getKey());
                    return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                            && level.getBlockState(entry.getKey()).equals(entry.getValue()) && level.getBlockEntity(entry.getKey()) == null;
                });
                boolean gone = shots.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null)
                        && actorOwnedItems.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null)
                        && (user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null);
                boolean inputOff = user == null || !user.isUsingItem();
                log("CLEANUP cells=" + originals.size() + " exactRestore=" + restored + " ownedGone=" + gone
                        + " inputOff=" + inputOff + " shots=" + shots.size() + " actorOwnedItems=" + actorOwnedItems.size() + " listeners=0");
                if (!restored || !gone || !inputOff) throw new IllegalStateException("Creative Clackers cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) {
                IllegalStateException error = new IllegalStateException("Creative Clackers cleanup failed");
                failures.forEach(error::addSuppressed); throw error;
            }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
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
