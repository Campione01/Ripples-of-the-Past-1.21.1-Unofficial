package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.item.TommyGunItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TommyGunMotionGameTests {
    private static final double EPSILON = 1.0E-5D;

    private TommyGunMotionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "tommy_item_air_motion", timeoutTicks = 100)
    public static void oneReloadedTommyRoundKeepsDonorAirMotion(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
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

    private record Before(long time, int age, Vec3 position, Vec3 velocity, boolean noGravity,
            AABB query, List<UUID> candidates, HitResult.Type block) {}
    private record Sample(Before before, Vec3 position, Vec3 velocity, boolean noGravity,
            boolean deflected, boolean removed) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Object> listeners = new ArrayList<>();
        private final List<TommyGunBulletEntity> shots = new ArrayList<>();
        private final List<Sample> samples = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private ItemStack gun;
        private ItemStack nugget;
        private ItemStack powder;
        private TommyGunBulletEntity bullet;
        private Vec3 userPosition;
        private Vec3 initialPosition;
        private Vec3 initialVelocity;
        private AABB column;
        private Before before;
        private RuntimeException observerFailure;
        private int userTicks;
        private int reloadUserTicks;
        private int expiryUserTicks = -1;
        private int useTicks;
        private int impacts;
        private int remainingBefore;
        private long userTickTime;
        private boolean reloaded;
        private boolean firing;
        private boolean inUseTick;
        private boolean consumed;
        private boolean stopped;
        private boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX() + 5, y + 1, chunk.getMinBlockZ() + 5);
            BlockPos max = new BlockPos(chunk.getMinBlockX() + 11, y + 28, chunk.getMinBlockZ() + 11);
            column = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, column).isEmpty(), "Tommy fixture column is unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Tommy fixture column is obstructed");
            }
            userPosition = new Vec3(chunk.getMinBlockX() + 8.5D, y + 4, chunk.getMinBlockZ() + 8.5D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, -90);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Tommy user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            gun = new ItemStack(ModItems.TOMMY_GUN.get());
            nugget = new ItemStack(Items.IRON_NUGGET);
            powder = new ItemStack(Items.GUNPOWDER);
            user.setItemInHand(InteractionHand.MAIN_HAND, gun);
            user.getInventory().setItem(1, nugget);
            user.getInventory().setItem(2, powder);
            registerObservers();
            log("setup user=" + user.getUUID() + " feet=" + userPosition + " column=" + column
                    + " power=NONE survival=true noGravity=true yaw=0 pitch=-90");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = joinEvent -> {
                if (closed || joinEvent.getLevel() != level
                        || !(joinEvent.getEntity() instanceof TommyGunBulletEntity created) || created.getOwner() != user) return;
                shots.add(created);
                observe(() -> {
                    helper.assertTrue(firing && inUseTick && useTicks == 1 && remainingBefore == 100
                                    && userTickTime == level.getGameTime() && bullet == null && shots.size() == 1 && !joinEvent.isCanceled()
                                    && TommyGunItem.getAmmo(gun) == 1,
                            "Tommy shot did not come from the first real item-use tick");
                    bullet = created;
                    initialPosition = bullet.position();
                    initialVelocity = bullet.getDeltaMovement();
                    Vec3 look = user.getLookAngle();
                    Vec3 muzzle = user.getEyePosition(1).subtract(0, bullet.getBbHeight() / 2.0D, 0).add(look);
                    helper.assertTrue(bullet.getType() == ModEntityTypes.TOMMY_GUN_BULLET.get()
                                    && bullet.ticksLifespan() == 40 && bullet.isNoGravity() && !deflected()
                                    && near(initialPosition, muzzle) && Math.abs(initialVelocity.length() - 2) < EPSILON
                                    && near(initialVelocity, look.scale(2)) && initialVelocity.y > 1.999D,
                            "Registered Tommy shot lost its actual muzzle, owner or speed-two launch");
                    log("join bullet=" + bullet.getUUID() + " owner=" + user.getUUID() + " eye=" + user.getEyePosition(1)
                            + " look=" + look + " position=" + initialPosition + " velocity=" + initialVelocity
                            + " width=" + bullet.getBbWidth() + " height=" + bullet.getBbHeight() + " ammoAtJoin=" + TommyGunItem.getAmmo(gun));
                });
            };
            Consumer<EntityTickEvent.Pre> pre = preEvent -> observe(() -> {
                if (preEvent.getEntity() == user && firing && !stopped) {
                    requireUser();
                    helper.assertTrue(!preEvent.isCanceled() && !inUseTick && user.isUsingItem()
                                    && user.getUsedItemHand() == InteractionHand.MAIN_HAND && user.getUseItem().is(ModItems.TOMMY_GUN.get()),
                            "Tommy use tick lost its ordinary active hand");
                    inUseTick = true;
                    userTickTime = level.getGameTime();
                    remainingBefore = user.getUseItemRemainingTicks();
                    useTicks++;
                    helper.assertTrue(useTicks <= 2 && remainingBefore == 101 - useTicks
                                    && TommyGunItem.getAmmo(gun) == (useTicks == 1 ? 1 : 0),
                            "Tommy single-round use did not reach the expected natural ammo state");
                    log("use-pre count=" + useTicks + " remaining=" + remainingBefore + " ammo=" + TommyGunItem.getAmmo(gun));
                }
                if (preEvent.getEntity() != bullet || samples.size() >= 8) return;
                helper.assertTrue(!preEvent.isCanceled() && before == null && bullet.tickCount == samples.size() + 1
                                && bullet.getOwner() == user && !bullet.isRemoved() && !deflected()
                                && !bullet.isInWater() && !bullet.isInLava()
                                && level.isPositionEntityTicking(bullet.blockPosition()),
                        "Tommy sample lost its natural dry-air tick or ownership");
                Vec3 position = bullet.position();
                Vec3 velocity = bullet.getDeltaMovement();
                AABB query = bullet.getBoundingBox().expandTowards(velocity).inflate(1);
                List<UUID> candidates = level.getEntities(bullet, query, entity -> entity != user
                                && !entity.isSpectator() && entity.canBeHitByProjectile()).stream().map(Entity::getUUID).toList();
                HitResult.Type block = level.clip(new ClipContext(position, position.add(velocity),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet)).getType();
                before = new Before(level.getGameTime(), bullet.tickCount, position, velocity, bullet.isNoGravity(), query, candidates, block);
                log("pre " + before);
                helper.assertTrue(contained(query) && candidates.isEmpty() && block == HitResult.Type.MISS,
                        "Tommy motion sample is not an unobstructed owned-column step");
            });
            Consumer<ProjectileImpactEvent> impact = impactEvent -> observe(() -> {
                if (impactEvent.getProjectile() != bullet) return;
                impacts++;
                log("unexpected-impact type=" + impactEvent.getRayTraceResult().getType()
                        + " point=" + impactEvent.getRayTraceResult().getLocation() + " canceled=" + impactEvent.isCanceled());
                helper.assertTrue(false, "Tommy empty-air fixture received a real projectile impact");
            });
            Consumer<EntityTickEvent.Post> post = postEvent -> observe(() -> {
                if (postEvent.getEntity() == user) {
                    userTicks++;
                    if (reloaded && !firing) {
                        int elapsed = userTicks - reloadUserTicks;
                        float fraction = cooldown();
                        boolean active = user.getCooldowns().isOnCooldown(ModItems.TOMMY_GUN.get());
                        log("reload-post elapsed=" + elapsed + " fraction=" + fraction + " active=" + active);
                        if (elapsed <= 2) helper.assertTrue(elapsed >= 1 && Math.abs(fraction - (2 - elapsed) / 2.0F) < EPSILON
                                        && active == (elapsed < 2), "Tommy reload did not expire after two natural user ticks");
                        if (elapsed == 2) expiryUserTicks = userTicks;
                    }
                    if (inUseTick) {
                        log("use-post count=" + useTicks + " remaining=" + user.getUseItemRemainingTicks()
                                + " ammo=" + TommyGunItem.getAmmo(gun) + " using=" + user.isUsingItem());
                        helper.assertTrue(bullet != null && shots.size() == 1 && TommyGunItem.getAmmo(gun) == 0,
                                "Tommy first shot did not consume its one reloaded round");
                        if (useTicks == 1) {
                            helper.assertTrue(user.isUsingItem() && user.getUseItemRemainingTicks() == 99,
                                    "Tommy first natural shot tick changed its use progression");
                            consumed = true;
                        }
                        else {
                            helper.assertTrue(!user.isUsingItem(), "Tommy did not naturally stop after ammo became zero");
                            stopped = true;
                        }
                        inUseTick = false;
                    }
                }
                if (postEvent.getEntity() != bullet || samples.size() >= 8) return;
                helper.assertTrue(before != null && before.time == level.getGameTime() && before.age == bullet.tickCount,
                        "Tommy bullet lacks its matching natural Pre/Post pair");
                Sample sample = new Sample(before, bullet.position(), bullet.getDeltaMovement(), bullet.isNoGravity(), deflected(), bullet.isRemoved());
                samples.add(sample);
                log("post " + sample);
                before = null;
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

        private void requireUser() {
            helper.assertTrue(user.isAlive() && near(user.position(), userPosition) && user.getYRot() == 0 && user.getXRot() == -90
                            && !user.isCreative() && !user.getAbilities().instabuild && power.getPowerType() == null
                            && user.getMainHandItem() == gun && !user.isOnFire()
                            && level.isPositionEntityTicking(user.blockPosition()),
                    "Tommy user lost its stable Survival/NONE admission state");
        }

        private void reload() {
            requireUser();
            helper.assertTrue(TommyGunItem.getAmmo(gun) == 0 && nugget.getCount() == 1 && powder.getCount() == 1
                            && !user.isUsingItem() && !user.getCooldowns().isOnCooldown(ModItems.TOMMY_GUN.get()),
                    "Tommy reload lacks its ordinary empty gun and ingredients");
            user.setShiftKeyDown(true);
            try {
                var result = gun.use(level, user, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.getResult().consumesAction() && !user.isUsingItem()
                                && nugget.isEmpty() && powder.isEmpty() && TommyGunItem.getAmmo(gun) == 1
                                && user.getCooldowns().isOnCooldown(ModItems.TOMMY_GUN.get()) && cooldown() == 1,
                        "Registered sneak-use did not reload exactly one paid round");
            }
            finally { user.setShiftKeyDown(false); }
            reloadUserTicks = userTicks;
            reloaded = true;
            log("reloaded userTicks=" + userTicks + " ammo=1 ironDebit=1 gunpowderDebit=1 cooldown=" + cooldown());
        }

        private void beginUse() {
            requireUser();
            helper.assertTrue(expiryUserTicks == reloadUserTicks + 2 && !user.isShiftKeyDown() && cooldown() == 0
                            && !user.getCooldowns().isOnCooldown(ModItems.TOMMY_GUN.get()) && TommyGunItem.getAmmo(gun) == 1,
                    "Tommy fire admission preceded natural reload cooldown expiry");
            var result = gun.use(level, user, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult().consumesAction() && user.isUsingItem()
                            && user.getUsedItemHand() == InteractionHand.MAIN_HAND && user.getUseItemRemainingTicks() == 100
                            && shots.isEmpty(), "Registered Tommy use did not start its natural firing route");
            firing = true;
            log("fire-input userTicks=" + userTicks + " ammo=" + TommyGunItem.getAmmo(gun));
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Tommy motion fixture watchdog expired");
                if (samples.size() == 8) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                requireUser();
                if (!reloaded && userTicks >= 2) reload();
                else if (reloaded && !firing && expiryUserTicks >= 0) beginUse();
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            log("result bullet=" + id(bullet) + " samples=" + samples + " useTicks=" + useTicks
                    + " consumed=" + consumed + " stopped=" + stopped + " impacts=" + impacts);
            helper.assertTrue(samples.size() == 8 && shots.size() == 1 && consumed && stopped && useTicks == 2
                            && TommyGunItem.getAmmo(gun) == 0 && impacts == 0
                            && samples.stream().noneMatch(sample -> sample.removed || sample.deflected),
                    "Tommy motion evidence lacks eight live natural steps and single-round completion");
            helper.assertTrue(samples.stream().allMatch(sample -> sample.noGravity == (sample.before.age < 5)),
                    "Tommy's existing tick-five gravity flag transition changed");
            // Evaluate motion only after all eight real samples, including the later gravity branch, have been logged.
            helper.assertTrue(samples.stream().allMatch(sample -> near(sample.before.velocity, initialVelocity)
                            && near(sample.velocity, initialVelocity)
                            && near(sample.position, initialPosition.add(initialVelocity.scale(sample.before.age)))),
                    "Tommy bullet did not retain donor constant air motion");
        }

        private boolean deflected() { return bullet.saveWithoutId(new CompoundTag()).getBoolean("IsDeflected"); }
        private float cooldown() { return user.getCooldowns().getCooldownPercent(ModItems.TOMMY_GUN.get(), 0); }
        private boolean contained(AABB box) {
            return column.contains(new Vec3(box.minX, box.minY, box.minZ)) && column.contains(new Vec3(box.maxX, box.maxY, box.maxZ));
        }
        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }
        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Tommy item motion observer failure", error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("TOMMY-ITEM-MOTION {}", message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            before = null;
            inUseTick = false;
            try {
                if (user != null) {
                    user.setShiftKeyDown(false);
                    user.stopUsingItem();
                }
                for (TommyGunBulletEntity shot : shots) if (!shot.isRemoved()) shot.discard();
            }
            finally {
                if (user != null) {
                    user.getInventory().clearContent();
                    user.discard();
                }
                log("cleanup listeners=0 shots=" + shots.size() + " worldBlocksChanged=0");
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
