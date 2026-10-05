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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.impl.stands.goldexperience.GEStuckObjectsState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.itemtracking.ItemTracking;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnifeItemDamageGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final double AIR_DRAG = (double) 0.99F;

    private KnifeItemDamageGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "knife_item_fractional", timeoutTicks = 60)
    public static void ordinaryKnifeItemUsesFractionalImpactDamage(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "knife_item_refused", timeoutTicks = 60)
    public static void refusedKnifeItemHitRestoresFireAndBounces(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean refuse) {
        Fixture fixture = new Fixture(helper, refuse);
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

    private record Frame(long time, int age, Vec3 position, Vec3 motion, double baseDamage,
            boolean critical, boolean burning, float health, int targetFire, boolean targetCandidate, Vec3 targetClip) {}
    private record Hit(ProjectileImpactEvent event, Vec3 motion, double baseDamage, boolean critical,
            boolean burning, float health, int targetFire, Vec3 point) {}
    private record Incoming(LivingIncomingDamageEvent event, Vec3 motion, double baseDamage, float health, int fire) {}
    private record Attempt(float amount, UUID direct, UUID cause, boolean arrow, boolean canceled,
            Vec3 motion, double baseDamage, float health, int fire) {}
    private record Contact(Frame before, Vec3 impactMotion, double impactBase, boolean critical, boolean burning,
            Vec3 point, boolean impactCanceled, float impactHealth, int oldFire, Attempt attempt,
            Vec3 position, Vec3 motion, boolean removed, Entity.RemovalReason reason, float health, int fire, int stuck) {}
    private record CooldownStep(int userTicks, float fraction, boolean active) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean refuse;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<KnifeEntity> spawned = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<CooldownStep> cooldowns = new ArrayList<>();
        private Player user;
        private Cow target;
        private KnifeEntity knife;
        private ItemStack usedStack;
        private Vec3 userPosition;
        private Vec3 targetPosition;
        private AABB room;
        private BlockPos support;
        private BlockPos fire;
        private Frame before;
        private Hit hit;
        private Incoming incoming;
        private Contact contact;
        private RuntimeException observerFailure;
        private float initialHealth;
        private int userTicks;
        private int targetTicks;
        private int userTicksAtUse;
        private int knifeTicks;
        private int firstBurningPost;
        private boolean usingItem;
        private boolean used;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean refuse) {
            this.helper = helper;
            level = helper.getLevel();
            this.refuse = refuse;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 6, y - 1, z + 1);
            BlockPos max = new BlockPos(x + 10, y + 4, z + 11);
            room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, room).isEmpty(), "Knife fixture room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) helper.assertTrue(level.isEmptyBlock(pos), "Knife room obstructed");
            support = new BlockPos(x + 8, y, z + 4);
            fire = support.above();
            place(support, Blocks.STONE.defaultBlockState());
            place(fire, Blocks.FIRE.defaultBlockState());
            userPosition = new Vec3(x + 8.5D, y, z + 2.5D);
            targetPosition = new Vec3(x + 8.5D, y, z + 8.75D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(user), "Could not add knife user");
            helper.assertTrue(PowerClass.PLAYER_POWER.attachGet(user).getPowerType() == null, "Knife user must have NONE power");
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KNIFE.get()));
            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create knife target");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setPos(targetPosition);
            helper.assertTrue(level.addFreshEntity(target), "Could not add knife target");
            initialHealth = target.getHealth();
            helper.assertTrue(initialHealth > 4 && target.getArmorValue() == 0 && target.getAbsorptionAmount() == 0
                            && GEStuckObjectsState.get(target).getStuckKnives() == 0, "Knife target is not a fresh unarmored control");
            registerObservers();
            requireActors();
            log("setup owner=" + user.getUUID() + " pos=" + userPosition + " target=" + target.getUUID()
                    + " pos=" + targetPosition + " fire=" + fire + " localNoGravityActors=true knifeGravity=natural");
        }

        private void place(BlockPos pos, BlockState state) {
            original.put(pos, level.getBlockState(pos));
            helper.assertTrue(level.setBlockAndUpdate(pos, state), "Could not place owned knife fire fixture");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof ItemEntity item && room.contains(item.position())) drops.add(item);
                if (!(event.getEntity() instanceof KnifeEntity created) || created.getOwner() != user) return;
                spawned.add(created);
                observe(() -> {
                    helper.assertTrue(usingItem && knife == null && spawned.size() == 1 && !event.isCanceled(), "Knife escaped its registered item use");
                    knife = created;
                    Vec3 root = new Vec3(user.getX(), user.getEyeY() - (double) 0.1F, user.getZ());
                    Vec3 motion = knife.getDeltaMovement();
                    helper.assertTrue(knife.getType() == ModEntityTypes.KNIFE.get() && knife.tickCount == 0 && near(knife.position(), root)
                                    && knife.getBaseDamage() == 2.0D && !knife.isCritArrow() && !knife.isNoGravity() && !knife.isOnFire()
                                    && knife.getPierceLevel() == 0 && !knife.isNoPhysics() && !knife.shotFromCrossbow()
                                    && knife.getPickupItem().is(ModItems.KNIFE.get()) && knife.getPickupItem().getCount() == 1
                                    && ItemTracking.getTrackerId(knife.getPickupItem()) == null
                                    && knife.saveWithoutId(new CompoundTag()).getInt("TimeStopTicks") == 5
                                    && motion.z > 1.4D && motion.z < 1.6D && Math.abs(motion.x) < 0.04D && Math.abs(motion.y) < 0.04D,
                            "Knife join changed ordinary noncritical throw state");
                    log("join uuid=" + knife.getUUID() + " root=" + knife.position() + " motion=" + motion + " base=" + knife.getBaseDamage());
                });
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != knife || contact != null) return;
                requireActors();
                helper.assertTrue(!event.isCanceled() && before == null && knife.getOwner() == user && !stopped(knife)
                                && level.isPositionEntityTicking(knife.blockPosition()) && !knife.inGround && !knife.isNoPhysics()
                                && !knife.isNoGravity() && !knife.isInWater() && !knife.isInLava() && !level.isRainingAt(knife.blockPosition())
                                && knife.getPierceLevel() == 0 && !knife.isCritArrow() && knife.getBaseDamage() == 2.0D,
                        "Knife lacks an ordinary naturally ticking air frame");
                Vec3 start = knife.position();
                Vec3 end = start.add(knife.getDeltaMovement());
                AABB query = knife.getBoundingBox().expandTowards(knife.getDeltaMovement()).inflate(1);
                helper.assertTrue(level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, knife))
                                .getType() == HitResult.Type.MISS, "Knife flight acquired a blocking collider");
                before = new Frame(level.getGameTime(), knife.tickCount, start, knife.getDeltaMovement(), knife.getBaseDamage(),
                        knife.isCritArrow(), knife.isOnFire(), target.getHealth(), target.getRemainingFireTicks(),
                        level.getEntities(knife, query, entity -> entity == target).contains(target),
                        target.getBoundingBox().inflate((double) 0.3F).clip(start, end).orElse(null));
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() != knife || contact != null) return;
                helper.assertTrue(before != null && hit == null && !event.isCanceled()
                                && event.getRayTraceResult() instanceof EntityHitResult entityHit && entityHit.getEntity() == target,
                        "Knife first contact is not its owned target");
                hit = new Hit(event, knife.getDeltaMovement(), knife.getBaseDamage(), knife.isCritArrow(), knife.isOnFire(),
                        target.getHealth(), target.getRemainingFireTicks(), event.getRayTraceResult().getLocation());
                log("impact age=" + before.age + " motion=" + hit.motion + " base=" + hit.baseDamage + " burning=" + hit.burning
                        + " donorAmount=" + (float) (hit.motion.length() * hit.baseDamage) + " oldFire=" + hit.targetFire);
            });
            Consumer<LivingIncomingDamageEvent> refusal = event -> observe(() -> {
                if (event.getEntity() != target || contact != null) return;
                helper.assertTrue(before != null && hit != null && event.getSource().getDirectEntity() == knife
                                && event.getSource().getEntity() == user && event.getOriginalAmount() > 0 && !event.isCanceled(),
                        "Knife refusal lacks its real owned impact");
                event.setCanceled(true);
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() != target || contact != null) return;
                helper.assertTrue(before != null && hit != null && incoming == null && event.getSource().getDirectEntity() == knife
                                && event.getSource().getEntity() == user, "Target damage escaped the first knife contact");
                incoming = new Incoming(event, knife.getDeltaMovement(), knife.getBaseDamage(), target.getHealth(), target.getRemainingFireTicks());
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) {
                    userTicks++;
                    if (used && cooldowns.size() < 3) cooldowns.add(new CooldownStep(userTicks, cooldown(),
                            user.getCooldowns().isOnCooldown(ModItems.KNIFE.get())));
                }
                if (event.getEntity() == target) targetTicks++;
                if (event.getEntity() != knife || contact != null) return;
                helper.assertTrue(before != null && before.time == level.getGameTime() && before.age == knife.tickCount,
                        "Knife lacks its matched natural Pre/Post");
                knifeTicks++;
                if (firstBurningPost == 0 && knife.isOnFire()) firstBurningPost = before.age;
                log("step pre=" + before + " post=" + knife.position() + " motion=" + knife.getDeltaMovement()
                        + " burning=" + knife.isOnFire() + " removed=" + knife.isRemoved());
                if (hit != null) contact = new Contact(before, hit.motion, hit.baseDamage, hit.critical, hit.burning, hit.point,
                        hit.event.isCanceled(), hit.health, hit.targetFire, incoming == null ? null : attempt(incoming),
                        knife.position(), knife.getDeltaMovement(), knife.isRemoved(), knife.getRemovalReason(), target.getHealth(),
                        target.getRemainingFireTicks(), GEStuckObjectsState.get(target).getStuckKnives());
                else helper.assertTrue(before.age < 4 && !knife.isRemoved(), "Knife missed the fixed fourth-tick target window");
                before = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            if (refuse) {
                listeners.add(refusal);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingIncomingDamageEvent.class, refusal);
            }
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void requireActors() {
            helper.assertTrue(user.isAlive() && target.isAlive() && near(user.position(), userPosition) && near(target.position(), targetPosition)
                            && user.getYRot() == 0 && user.getXRot() == 0 && near(user.getDeltaMovement(), Vec3.ZERO)
                            && !user.isOnFire() && !target.isOnFire() && target.getHealth() == initialHealth && !target.isInvulnerable()
                            && target.getArmorValue() == 0 && target.getAbsorptionAmount() == 0
                            && target.canBeHitByProjectile() && level.isPositionEntityTicking(user.blockPosition())
                            && level.isPositionEntityTicking(target.blockPosition()) && !stopped(user) && !stopped(target)
                            && level.getBlockState(support).is(Blocks.STONE) && level.getBlockState(fire).is(Blocks.FIRE)
                            && level.getBlockState(fire).canSurvive(level, fire) && level.getBlockState(fire).getCollisionShape(level, fire).isEmpty()
                            && !level.isRainingAt(fire) && !level.isRainingAt(target.blockPosition()), "Knife actor/fire/air prerequisites changed");
        }

        private void useItem() {
            requireActors();
            usedStack = user.getMainHandItem();
            helper.assertTrue(!user.isCreative() && !user.getAbilities().instabuild && !user.isShiftKeyDown()
                            && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && usedStack.is(ModItems.KNIFE.get()) && usedStack.getCount() == 1
                            && ItemTracking.getTrackerId(usedStack) == null && !user.getCooldowns().isOnCooldown(ModItems.KNIFE.get())
                            && StandPower.getOptional(user).map(power -> power.getSummonedStandEntity() == null).orElse(true),
                    "Knife use lacks ordinary Survival single-stack admission");
            usingItem = true;
            try {
                var result = usedStack.use(level, user, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.getResult().consumesAction() && knife != null && spawned.size() == 1 && usedStack.isEmpty()
                                && !user.isUsingItem() && Math.abs(cooldown() - 1) < EPSILON,
                        "Registered knife use did not immediately throw, consume and cool down");
            }
            finally { usingItem = false; }
            userTicksAtUse = userTicks;
            used = true;
            log("item-use count=1->0 cooldown=" + cooldown() + " userTicks=" + userTicks + " charged=false");
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 40, "Knife fixture watchdog expired");
                if (contact != null) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                if (!used && userTicks >= 2 && targetTicks >= 2) useItem();
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            Contact sample = contact;
            Attempt damage = sample.attempt;
            float expected = (float) (sample.impactMotion.length() * sample.impactBase);
            log("result contact=" + sample + " donorFloat=" + expected + " cooldowns=" + cooldowns + " firstBurningPost=" + firstBurningPost);
            helper.assertTrue(spawned.size() == 1 && knifeTicks == 4 && sample.before.age == 4 && sample.before.targetCandidate
                            && sample.before.targetClip != null && !sample.impactCanceled && !sample.critical && sample.impactBase == 2
                            && sample.burning && firstBurningPost > 0 && firstBurningPost < 4 && sample.oldFire <= 0
                            && sample.impactHealth == initialHealth && expected > 2.8F && expected < 2.995F && drops.isEmpty(),
                    "Knife lacks a naturally burning noncritical fractional first contact");
            helper.assertTrue(damage != null && damage.arrow && damage.amount > 0 && knife.getUUID().equals(damage.direct)
                            && user.getUUID().equals(damage.cause) && near(damage.motion, sample.impactMotion)
                            && damage.baseDamage == sample.impactBase && damage.health == initialHealth && damage.fire == 100,
                    "Knife damage lost its measured velocity, source or ignition order");
            helper.assertTrue(cooldowns.size() == 3, "Knife has no three-natural-tick cooldown trace");
            for (int i = 0; i < 3; i++) {
                CooldownStep step = cooldowns.get(i);
                helper.assertTrue(step.userTicks == userTicksAtUse + i + 1 && Math.abs(step.fraction - (2 - i) / 3.0F) < EPSILON
                                && step.active == (i < 2), "Single knife cooldown is not its natural three-tick schedule");
            }
            if (refuse) {
                // The existing arrow tick applies air drag/gravity after Knife's -0.1 rejected-hit reflection.
                Vec3 reflected = sample.impactMotion.scale(-0.1D);
                Vec3 expectedMotion = reflected.scale(AIR_DRAG).add(0, -0.05D, 0);
                helper.assertTrue(damage.canceled && sample.health == initialHealth && sample.fire == sample.oldFire
                                && sample.stuck == 0 && !sample.removed && sample.reason == null
                                && near(sample.motion, expectedMotion) && near(sample.position, sample.before.position.add(reflected)),
                        "Refused natural knife hit changed fire, health, stuck state or bounce");
            }
            else {
                helper.assertTrue(!damage.canceled && sample.removed && sample.reason == Entity.RemovalReason.DISCARDED
                                && Math.abs(initialHealth - sample.health - damage.amount) < EPSILON && sample.fire == 100 && sample.stuck == 1,
                        "Accepted natural knife hit lost damage, ignition, sticking or removal");
                helper.assertTrue(Math.abs(damage.amount - expected) < EPSILON,
                        "Knife rounded its live fractional damage: actual=" + damage.amount + " donor=" + expected);
            }
        }

        private boolean stopped(Entity entity) {
            return level.hasData(ModDataAttachmentTypes.TIME_STOP.get()) && level.getData(ModDataAttachmentTypes.TIME_STOP.get()).isTimeStopped(entity);
        }
        private float cooldown() { return user.getCooldowns().getCooldownPercent(ModItems.KNIFE.get(), 0); }
        private static boolean near(Vec3 first, Vec3 second) { return first.distanceToSqr(second) < EPSILON * EPSILON; }
        private static Attempt attempt(Incoming incoming) {
            var event = incoming.event;
            return new Attempt(event.getOriginalAmount(), event.getSource().getDirectEntity() == null ? null : event.getSource().getDirectEntity().getUUID(),
                    event.getSource().getEntity() == null ? null : event.getSource().getEntity().getUUID(), event.getSource().is(DamageTypes.ARROW),
                    event.isCanceled(), incoming.motion, incoming.baseDamage, incoming.health, incoming.fire);
        }
        private void observe(Runnable action) {
            if (closed || observerFailure != null) return;
            try { action.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Knife item observer failure: refuse=" + refuse, error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("KNIFE-ITEM-DAMAGE refused={} {}", refuse, message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                for (KnifeEntity created : spawned) if (!created.isRemoved()) created.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                if (target != null) target.discard();
                if (user != null) { user.stopUsingItem(); user.getInventory().clearContent(); user.discard(); }
            }
            finally {
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                log("cleanup listeners=0 exactRestore=" + restored + " cells=" + original.size() + " knives=" + spawned.size() + " drops=" + drops.size());
                if (!restored) throw new IllegalStateException("Knife fire cells were not exactly restored");
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
