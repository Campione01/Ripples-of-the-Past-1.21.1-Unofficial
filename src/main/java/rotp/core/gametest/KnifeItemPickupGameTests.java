package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.itemtracking.ItemTracking;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnifeItemPickupGameTests {
    private KnifeItemPickupGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "knife_item_pickup_creative", timeoutTicks = 60)
    public static void ordinaryCreativeKnifeKeepsCreativeOnlyPickup(GameTestHelper helper) {
        start(helper, GameType.CREATIVE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "knife_item_pickup_survival", timeoutTicks = 60)
    public static void ordinarySurvivalKnifeKeepsAllowedPickup(GameTestHelper helper) {
        start(helper, GameType.SURVIVAL);
    }

    private static void start(GameTestHelper helper, GameType mode) {
        Fixture fixture = new Fixture(helper, mode);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.close(); throw error; }
    }

    private record Snapshot(UUID knife, UUID owner, String type, AbstractArrow.Pickup pickup,
            int handCount, int pickupCount, Vec3 position, Vec3 velocity, int age) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameType mode;
        private final List<Object> listeners = new ArrayList<>();
        private final List<KnifeEntity> shots = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private StandPower stand;
        private ItemStack held;
        private KnifeEntity knife;
        private Vec3 userPosition;
        private AABB column;
        private Snapshot joined;
        private Snapshot afterUse;
        private Snapshot afterFlight;
        private RuntimeException observerFailure;
        private int userTicks;
        private int throwUserTicks;
        private int expiryTicks = -1;
        private long flightTime;
        private int flightAge;
        private boolean usingItem;
        private boolean thrown;
        private boolean flightActive;
        private boolean closed;

        Fixture(GameTestHelper helper, GameType mode) {
            this.helper = helper;
            level = helper.getLevel();
            this.mode = mode;
        }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(origin);
            int y = origin.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX() + 5, y + 1, chunk.getMinBlockZ() + 5);
            BlockPos max = new BlockPos(chunk.getMinBlockX() + 11, y + 16, chunk.getMinBlockZ() + 11);
            column = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, column).isEmpty(), "Knife pickup column is unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Knife pickup column is obstructed");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, mode);
            mode.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            userPosition = new Vec3(chunk.getMinBlockX() + 8.5D, y + 4, chunk.getMinBlockZ() + 8.5D);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, -90);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the knife pickup user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            stand = PowerClass.STAND.attachGet(user);
            held = new ItemStack(ModItems.KNIFE.get());
            user.setItemInHand(InteractionHand.MAIN_HAND, held);
            registerObservers();
            log("setup user=" + user.getUUID() + " mode=" + mode + " instabuild=" + user.getAbilities().instabuild
                    + " feet=" + userPosition + " column=" + column + " power=NONE stand=NONE");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = joinEvent -> {
                if (closed || joinEvent.getLevel() != level || !(joinEvent.getEntity() instanceof KnifeEntity created)
                        || created.getOwner() != user) return;
                shots.add(created);
                observe(() -> {
                    helper.assertTrue(usingItem && knife == null && shots.size() == 1 && !joinEvent.isCanceled()
                                    && created.getType() == ModEntityTypes.KNIFE.get(), "Unexpected knife item emission");
                    knife = created;
                    joined = snapshot();
                    log("join " + joined);
                    helper.assertTrue(joined.handCount == 1 && joined.pickupCount == 1
                                    && ItemTracking.getTrackerId(knife.getPickupItem()) == null,
                            "Knife item emission lost its plain one-stack input");
                });
            };
            Consumer<EntityTickEvent.Pre> pre = preEvent -> observe(() -> {
                if (preEvent.getEntity() != knife || afterFlight != null) return;
                helper.assertTrue(thrown && !preEvent.isCanceled() && !flightActive && knife.tickCount == 1
                                && knife.getOwner() == user && !knife.isRemoved()
                                && level.isPositionEntityTicking(knife.blockPosition()), "Knife first natural flight tick is unavailable");
                AABB query = knife.getBoundingBox().expandTowards(knife.getDeltaMovement()).inflate(1);
                List<UUID> candidates = level.getEntities(knife, query, entity -> entity != user
                                && !entity.isSpectator() && entity.canBeHitByProjectile()).stream().map(Entity::getUUID).toList();
                HitResult.Type block = level.clip(new ClipContext(knife.position(), knife.position().add(knife.getDeltaMovement()),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, knife)).getType();
                log("flight-pre " + snapshot() + " query=" + query + " candidates=" + candidates + " block=" + block);
                helper.assertTrue(column.contains(new Vec3(query.minX, query.minY, query.minZ))
                                && column.contains(new Vec3(query.maxX, query.maxY, query.maxZ))
                                && candidates.isEmpty() && block == HitResult.Type.MISS, "Knife pickup sample is not clear natural flight");
                flightTime = level.getGameTime();
                flightAge = knife.tickCount;
                flightActive = true;
            });
            Consumer<ProjectileImpactEvent> impact = impactEvent -> observe(() -> {
                if (impactEvent.getProjectile() == knife) helper.assertTrue(false, "Knife pickup fixture received an unexpected impact");
            });
            Consumer<EntityTickEvent.Post> post = postEvent -> observe(() -> {
                if (postEvent.getEntity() == user) {
                    userTicks++;
                    if (thrown) {
                        int elapsed = userTicks - throwUserTicks;
                        float fraction = cooldown();
                        boolean active = user.getCooldowns().isOnCooldown(ModItems.KNIFE.get());
                        log("cooldown elapsed=" + elapsed + " fraction=" + fraction + " active=" + active);
                        if (elapsed <= 3) helper.assertTrue(elapsed >= 1 && Math.abs(fraction - (3 - elapsed) / 3.0F) < 1.0E-5F
                                        && active == (elapsed < 3), "Knife item cooldown did not follow three natural user ticks");
                        if (elapsed == 3) expiryTicks = userTicks;
                    }
                }
                if (postEvent.getEntity() != knife || afterFlight != null) return;
                helper.assertTrue(flightActive && flightTime == level.getGameTime() && flightAge == knife.tickCount
                                && !knife.isRemoved(), "Knife pickup sample lacks its natural Pre/Post pair");
                afterFlight = snapshot();
                log("flight-post " + afterFlight);
                flightActive = false;
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

        private void requireActor() {
            boolean creative = mode == GameType.CREATIVE;
            helper.assertTrue(user.isAlive() && user.position().distanceToSqr(userPosition) < 1.0E-10D
                            && user.isCreative() == creative && user.getAbilities().instabuild == creative
                            && user.getAbilities().invulnerable == creative && !user.isShiftKeyDown()
                            && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && power.getPowerType() == null
                            && stand.getPowerType() == null && !stand.hasPower() && stand.getSummonedStandEntity() == null
                            && level.isPositionEntityTicking(user.blockPosition()), "Knife user lost its declared ordinary admission state");
        }

        private void throwItem() {
            requireActor();
            helper.assertTrue(user.getMainHandItem() == held && held.is(ModItems.KNIFE.get()) && held.getCount() == 1
                            && ItemTracking.getTrackerId(held) == null && !held.has(DataComponents.INTANGIBLE_PROJECTILE)
                            && !user.isUsingItem() && !user.getCooldowns().isOnCooldown(ModItems.KNIFE.get()),
                    "Knife input is not a plain untracked one-stack throw");
            usingItem = true;
            try {
                var result = held.use(level, user, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.getResult().consumesAction() && knife != null && shots.size() == 1,
                        "Registered knife use did not emit exactly one owned knife");
            }
            finally { usingItem = false; }
            afterUse = snapshot();
            log("after-use " + afterUse + " cooldown=" + cooldown());
            helper.assertTrue(afterUse.handCount == (mode == GameType.CREATIVE ? 1 : 0)
                            && afterUse.pickupCount == 1 && cooldown() == 1
                            && user.getCooldowns().isOnCooldown(ModItems.KNIFE.get()),
                    "Knife item use changed its ordinary count or cooldown cost");
            throwUserTicks = userTicks;
            thrown = true;
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 40, "Knife pickup fixture watchdog expired");
                requireActor();
                if (!thrown && userTicks >= 2) throwItem();
                if (afterFlight != null && expiryTicks >= 0) {
                    Snapshot current = snapshot();
                    log("result join=" + joined + " afterUse=" + afterUse + " flight=" + afterFlight + " current=" + current);
                    helper.assertTrue(expiryTicks == throwUserTicks + 3 && cooldown() == 0 && !knife.isRemoved()
                                    && shots.size() == 1 && user.getUUID().equals(current.owner), "Knife admission did not remain owned through cooldown expiry");
                    AbstractArrow.Pickup expected = mode == GameType.CREATIVE
                            ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
                    helper.assertTrue(joined.pickup == expected && afterUse.pickup == expected
                                    && afterFlight.pickup == expected && current.pickup == expected,
                            "Ordinary knife throw lost its donor game-mode pickup policy");
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        private Snapshot snapshot() {
            Entity owner = knife.getOwner();
            return new Snapshot(knife.getUUID(), owner == null ? null : owner.getUUID(),
                    knife.getType().builtInRegistryHolder().key().location().toString(), knife.pickup,
                    user.getMainHandItem().getCount(), knife.getPickupItem().getCount(), knife.position(), knife.getDeltaMovement(), knife.tickCount);
        }
        private float cooldown() { return user.getCooldowns().getCooldownPercent(ModItems.KNIFE.get(), 0); }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) { observerFailure = error; JojoMod.LOGGER.error("Knife pickup observer failure: " + mode, error); }
        }
        private void log(String message) { JojoMod.LOGGER.info("KNIFE-ITEM-PICKUP {} {}", mode, message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            for (KnifeEntity shot : shots) if (!shot.isRemoved()) shot.discard();
            if (user != null) { user.stopUsingItem(); user.getInventory().clearContent(); user.discard(); }
            log("cleanup listeners=0 knives=" + shots.size() + " worldBlocksChanged=0");
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
