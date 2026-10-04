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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersDamageGameTests {
    private static final double EPSILON = 1.0E-5D;

    private ClackersDamageGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_refused_damage", timeoutTicks = 100)
    public static void refusedClackersDamageDoesNotTrainOrLatchBoomerang(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_physical_success", timeoutTicks = 100)
    public static void physicalClackersHitStillSucceedsWhenHamonIsRefused(GameTestHelper helper) {
        start(helper, false);
    }

    private static void start(GameTestHelper helper, boolean refuseBoth) {
        Fixture fixture = new Fixture(helper, refuseBoth);
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

    private record Points(int strength, float fraction) {
        double total() {
            return strength + (double) fraction;
        }
    }

    private record Attempt(String channel, float amount, UUID direct, UUID cause, boolean canceled) {}

    private record Contact(long time, int age, float healthBefore, float healthAfter,
            Points pointsBefore, Points pointsAfter, boolean boomerangBefore, boolean boomerangAfter,
            Vec3 velocityBefore, List<Attempt> attempts) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean refuseBoth;
        private final List<Object> listeners = new ArrayList<>();
        private final List<ClackersEntity> spawned = new ArrayList<>();
        private final List<LivingIncomingDamageEvent> incoming = new ArrayList<>();
        private Player user;
        private Cow target;
        private HamonData hamon;
        private ClackersEntity clackers;
        private Contact contact;
        private RuntimeException observerFailure;
        private Points pointsBefore;
        private Vec3 velocityBefore;
        private float healthBefore;
        private float hamonDamage;
        private float spentEnergy;
        private float pointsMultiplier;
        private boolean boomerangBefore;
        private boolean initialBoomerang;
        private long preTime;
        private int preAge;
        private int userTicks;
        private int useStartUserTicks;
        private int measuredUseTicks;
        private boolean using;
        private boolean releasing;
        private boolean released;
        private boolean activeTick;
        private boolean realHit;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean refuseBoth) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.refuseBoth = refuseBoth;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 3.5D);
            AABB space = new AABB(origin.x - 2, origin.y - 1, origin.z - 2,
                    origin.x + 2, origin.y + 6, origin.z + 6);
            helper.assertTrue(space.maxY < level.getMaxBuildHeight(), "Clackers damage fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Clackers damage corridor is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, space).isEmpty(), "Clackers damage corridor contains another entity");
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
            helper.assertTrue(level.addFreshEntity(user), "Could not add Clackers damage user");
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            pointsMultiplier = JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get().floatValue();
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get())
                            && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get()) && hamon.getEnergy() > 200
                            && pointsMultiplier > 0, "Invalid Clacker Volley training setup");
            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create Clackers damage target");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
            target.setPos(origin.x, user.getEyeY() - (double) 0.1F - target.getBbHeight() * 0.5D, origin.z + 2.0D);
            helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && !target.isInvulnerable()
                            && !EntityHamonChargeState.get(target).hasHamonCharge()
                            && PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isEmpty()
                            && Math.abs(HamonAbilityHelpers.hamonDamageMultiplier(target) - 0.2F) < EPSILON
                            && HamonAbilityHelpers.configHamonDamageMultiplier() > 0, "Target is not vulnerable to real Clackers damage");
            registerObservers();
            log("setup owner=" + user.getUUID() + " target=" + target.getUUID() + " targetHamonMultiplier="
                    + HamonAbilityHelpers.hamonDamageMultiplier(target) + " pointsMultiplier=" + pointsMultiplier
                    + " noGravityFixture=true chunk=" + chunk);
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof ClackersEntity entity && entity.getOwner() == user) {
                    spawned.add(entity);
                    if (!releasing || clackers != null || event.isCanceled()) throw new IllegalStateException("Unexpected Clackers damage spawn");
                    clackers = entity;
                    CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
                    hamonDamage = nbt.getFloat("HamonDamage");
                    spentEnergy = nbt.getFloat("HamonSpent");
                    initialBoomerang = nbt.getBoolean("BoomerangHit");
                    if (!(hamonDamage > 0) || !(spentEnergy > 0) || initialBoomerang) {
                        throw new IllegalStateException("Thrown Clackers lacks a fresh positive Hamon budget");
                    }
                    log("spawn uuid=" + entity.getUUID() + " useTicks=" + measuredUseTicks + " hamonDamage=" + hamonDamage
                            + " spentEnergy=" + spentEnergy + " boomerang=" + initialBoomerang + " velocity=" + entity.getDeltaMovement());
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == clackers && contact == null) {
                    if (event.isCanceled() || activeTick || !level.isPositionEntityTicking(clackers.blockPosition())
                            || !level.isPositionEntityTicking(target.blockPosition())) {
                        throw new IllegalStateException("Clackers damage tick is not isolated and ticking");
                    }
                    activeTick = true;
                    realHit = false;
                    incoming.clear();
                    preTime = level.getGameTime();
                    preAge = clackers.tickCount;
                    healthBefore = target.getHealth();
                    pointsBefore = points();
                    velocityBefore = clackers.getDeltaMovement();
                    boomerangBefore = clackers.saveWithoutId(new CompoundTag()).getBoolean("BoomerangHit");
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() == clackers && contact == null) {
                    if (!activeTick || realHit || event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                            || hit.getEntity() != target) throw new IllegalStateException("Clackers did not make its intended first living contact");
                    realHit = true;
                }
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() == target && contact == null) {
                    if (!activeTick || !realHit || event.isCanceled()) throw new IllegalStateException("Target damage is outside the first owned contact");
                    // Scope by real contact so misattributed damage sources remain observable.
                    if (refuseBoth || event.getSource().is(ModDamageTypes.HAMON)) event.setCanceled(true);
                    incoming.add(event);
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                if (event.getEntity() == clackers && contact == null) {
                    if (!activeTick || preTime != level.getGameTime() || preAge != clackers.tickCount) {
                        throw new IllegalStateException("Clackers first contact lacks its paired natural pre tick");
                    }
                    if (realHit) {
                        contact = new Contact(level.getGameTime(), clackers.tickCount, healthBefore, target.getHealth(),
                                pointsBefore, points(), boomerangBefore,
                                clackers.saveWithoutId(new CompoundTag()).getBoolean("BoomerangHit"),
                                velocityBefore, incoming.stream().map(Fixture::attempt).toList());
                        log("first-contact uuid=" + clackers.getUUID() + " " + contact);
                        // Measurement is complete. Teardown now prevents a later retarget from adding another hit.
                        clackers.discard();
                    }
                    activeTick = false;
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private static Attempt attempt(LivingIncomingDamageEvent event) {
            DamageSource source = event.getSource();
            String channel = source.is(ModDamageTypes.HAMON) ? "hamon"
                    : source.is(ModDamageTypes.MOD_PROJECTILE) ? "physical" : "unexpected";
            return new Attempt(channel, event.getOriginalAmount(), id(source.getDirectEntity()), id(source.getEntity()), event.isCanceled());
        }

        private static UUID id(Entity entity) {
            return entity == null ? null : entity.getUUID();
        }

        private Points points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return new Points(nbt.getInt("StrengthPoints"), nbt.getFloat("PointsIncFrac"));
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Clackers damage observer failure", error);
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Clackers damage watchdog: useTicks=" + user.getTicksUsingItem()
                        + " spawned=" + spawned.size() + " firstContact=" + (contact != null));
                if (!using && userTicks >= 2) {
                    helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()), "Clackers user is not entity-ticking");
                    ItemStack held = user.getMainHandItem();
                    helper.assertTrue(held.is(ModItems.CLACKERS.get()) && held.getCount() == 1
                                    && held.use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
                                    && user.isUsingItem(), "Registered Clackers item use failed");
                    using = true;
                    useStartUserTicks = userTicks;
                }
                if (using && !released) {
                    helper.assertTrue(user.isUsingItem(), "Clackers use stopped before measured release");
                    int ticks = user.getTicksUsingItem();
                    if (ticks >= 20) {
                        measuredUseTicks = ticks;
                        helper.assertTrue(ticks == 20 && userTicks - useStartUserTicks >= 20
                                        && user.getDeltaMovement().lengthSqr() < EPSILON * EPSILON, "Invalid natural Clackers charge");
                        releasing = true;
                        try {
                            user.releaseUsingItem();
                        }
                        finally {
                            releasing = false;
                        }
                        released = true;
                        helper.assertTrue(clackers != null && !user.isUsingItem() && user.getMainHandItem().isEmpty(),
                                "Real Clackers release did not throw and consume its item");
                        log("release measuredUseTicks=" + ticks + " naturalUserTicks=" + userTicks);
                    }
                }
                if (contact != null) {
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
            helper.assertTrue(spawned.size() == 1 && clackers.getType() == ModEntityTypes.CLACKERS.get()
                            && clackers.getOwner() == user && user.isAlive() && target.isAlive()
                            && !initialBoomerang && !contact.boomerangBefore, "Invalid initial Clackers success latch or ownership");
            List<Attempt> physical = contact.attempts.stream().filter(a -> a.channel.equals("physical")).toList();
            List<Attempt> hamonAttempts = contact.attempts.stream().filter(a -> a.channel.equals("hamon")).toList();
            helper.assertTrue(physical.size() == 1 && hamonAttempts.size() == 1 && contact.attempts.size() == 2
                            && contact.attempts.stream().allMatch(a -> a.amount > 0 && user.getUUID().equals(a.cause)),
                    "First contact did not attempt both real positive damage channels");
            helper.assertTrue(contact.attempts.stream().allMatch(a -> clackers.getUUID().equals(a.direct)),
                    "Clackers damage source lost its direct projectile identity");
            helper.assertTrue(hamonAttempts.get(0).canceled && physical.get(0).canceled == refuseBoth,
                    "Damage refusal did not match the selected case");
            double awarded = contact.pointsAfter.total() - contact.pointsBefore.total();
            double expectedOneAward = (spentEnergy * pointsMultiplier) / 750.0D;
            helper.assertTrue(expectedOneAward > EPSILON && expectedOneAward < 1.0D
                            && contact.pointsBefore.fraction + expectedOneAward < 1.0D,
                    "Fixture requires a positive sub-point award away from training caps");
            log("result firstHitOnly=true health=" + contact.healthBefore + "->" + contact.healthAfter
                    + " xpDelta=" + awarded + " oneAward=" + expectedOneAward
                    + " boomerang=" + contact.boomerangBefore + "->" + contact.boomerangAfter);
            if (refuseBoth) {
                helper.assertTrue(contact.healthAfter == contact.healthBefore, "Refused Clackers damage changed health");
                helper.assertTrue(contact.pointsAfter.equals(contact.pointsBefore) && !contact.boomerangAfter,
                        "Refused Clackers contact trained or latched: xp=" + awarded + ", boomerang=" + contact.boomerangAfter);
            }
            else {
                helper.assertTrue(contact.healthAfter < contact.healthBefore, "Physical Clackers control dealt no health damage");
                helper.assertTrue(awarded > EPSILON && Math.abs(awarded - expectedOneAward) < EPSILON && contact.boomerangAfter,
                        "Physical success lost OR semantics or its one award: xp=" + awarded + ", boomerang=" + contact.boomerangAfter);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("CLACKERS-DAMAGE {} {}", refuseBoth ? "both-refused" : "physical-only", message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (user != null) user.stopUsingItem();
            }
            finally {
                for (ClackersEntity entity : spawned) if (!entity.isRemoved()) entity.discard();
                if (target != null) target.discard();
                if (user != null) user.discard();
                log("cleanup listeners=0 spawned=" + spawned.size());
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            close();
        }
    }
}
