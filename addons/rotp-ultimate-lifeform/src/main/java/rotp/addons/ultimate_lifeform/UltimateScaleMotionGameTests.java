package rotp.addons.ultimate_lifeform;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
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
import rotp.core.util.functions.MathUtil;

@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateScaleMotionGameTests {
    private UltimateScaleMotionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_scale_hold_motion", timeoutTicks = 100)
    public static void registeredHoldScaleKeepsAimOnlyConstantMotion(GameTestHelper helper) {
        start(helper, Mode.HOLD);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_scale_split_motion", timeoutTicks = 100)
    public static void registeredSplitScalesHaveNoAdditionalYawFan(GameTestHelper helper) {
        start(helper, Mode.SPLIT);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_scale_engine_motion", timeoutTicks = 100)
    public static void factoryScaleUsesActualDeflectionAndImpactVeto(GameTestHelper helper) {
        start(helper, Mode.ENGINE);
    }

    private enum Mode { HOLD, SPLIT, ENGINE }
    private record Seed(Projectile entity, Vec3 eye, Vec3 delta, Vec3 known, float yaw, float pitch,
            float speed, float spread, int heldTicks) {}
    private record State(int age, int savedAge, Vec3 pos, Vec3 velocity, AABB box, float yaw, float pitch,
            boolean removed, boolean shot, boolean leftOwner, UUID owner) {}
    private record Frame(long time, State before, State after) {}

    private static final class Shot {
        final Projectile entity;
        final Seed seed;
        final boolean veto;
        final List<Frame> frames = new ArrayList<>();
        final List<ProjectileImpactEvent> impacts = new ArrayList<>();
        State joined;
        CompoundTag joinTag;
        State before;
        long preTime;
        Shot(Projectile entity, Seed seed, boolean veto) {
            this.entity = entity; this.seed = seed; this.veto = veto;
        }
        boolean complete(int count) { return frames.size() >= count || entity.isRemoved(); }
    }

    private static void start(GameTestHelper helper, Mode mode) {
        Fixture fixture = new Fixture(helper, mode);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 29;
        private static final long RNG_SEED = 716_2026L;
        private static final double EPS = 1.0E-5D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Mode mode;
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final Map<UUID, Seed> seeds = new LinkedHashMap<>();
        private final Map<UUID, Shot> shots = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private UltimateLifeformData data;
        private Ability ability;
        private EntityActionInstance action;
        private Deflector deflector;
        private ChunkPos chunk;
        private AABB space;
        private Vec3 startPosition;
        private int userPosts;
        private int incoming;
        private boolean inUserPost, inPress, pressed, released, started, closed;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, Mode mode) {
            this.helper = helper; this.level = helper.getLevel(); this.mode = mode;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "ULF-SCALE-PREMISE " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, "ULF-SCALE-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("ULF-SCALE {} {}", mode, message); }
        private int count() { return mode == Mode.SPLIT ? 5 : mode == Mode.ENGINE ? 2 : 1; }
        private int frames() { return mode == Mode.ENGINE ? 2 : 8; }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(origin);
            double x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = origin.getY() + 28.0D;
            space = new AABB(x + 0.25D, y - (mode == Mode.ENGINE ? 4 : 2), z + 0.25D,
                    x + 15.75D, y + 21, z + 15.75D);
            premise(space.minY >= level.getMinBuildHeight() && space.maxY < level.getMaxBuildHeight(), "room build bounds");
            premise(level.getEntities((Entity) null, space).isEmpty(), "room has foreign entities");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "room is not empty: " + pos);
            }
            user = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "UlfScaleFixture")) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return false; }
            };
            owned.add(user);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.moveTo(x + (mode == Mode.ENGINE ? 8.5D : 12.5D), y,
                    z + (mode == Mode.ENGINE ? 8.5D : 3.5D), mode == Mode.ENGINE ? 0 : 45, mode == Mode.ENGINE ? -90 : -60);
            user.setYHeadRot(user.getYRot()); user.yBodyRot = user.getYRot();
            premise(level.addFreshEntity(user), "owner add failed");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            premise(power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()), "ULF grant failed");
            data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
            // a new Ultimate Lifeform starts with no energy (1.16); this fixture starts full
            data.setCommandEnergy(user, UltimateLifeformData.MAX_ENERGY);
            power.updateAvailableMoves();
            startPosition = user.position();
            observeEvents();
            log("setup user=" + user.getUUID() + " pos=" + startPosition + " chunk=" + chunk
                    + " noGravityActor=true energy=" + data.getEnergy() + " ownedBlocks=0");
        }

        private void observeEvents() {
            Consumer<EntityEvent.EntityConstructing> constructing = event -> observe(() -> {
                Entity entity = event.getEntity();
                if (mode == Mode.ENGINE || !pressed || entity.level() != level
                        || entity.getType() != UltimateLifeformAddon.SCALE_PROJECTILE.get() || !(inUserPost || inPress)) return;
                owned.add(entity);
                EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                premise(current != null && current.ability == ability && current.getPowerUser() == user
                        && current.getPhase() == ActionPhase.PERFORM, "constructor lacks owned registered PERFORM");
                premise(entity instanceof Projectile, "registered scale is not Projectile");
                Projectile projectile = (Projectile) entity;
                projectile.getRandom().setSeed(RNG_SEED);
                float spread = mode == Mode.HOLD ? 4F : 8F;
                seeds.put(entity.getUUID(), new Seed(projectile, user.getEyePosition(), user.getDeltaMovement(),
                        user.getKnownMovement(), user.getYRot(), user.getXRot(),
                        (float) (2.0D + user.getDeltaMovement().length()), spread, data.getHeldTicks()));
            });
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() != level || !(event.getEntity() instanceof Projectile projectile)
                        || projectile.getType() != UltimateLifeformAddon.SCALE_PROJECTILE.get() || projectile.getOwner() != user) return;
                owned.add(projectile);
                Shot shot = shots.computeIfAbsent(projectile.getUUID(), id -> new Shot(projectile, seeds.get(id), false));
                premise(!event.isCanceled() && shot.joined == null, "scale join canceled or duplicated");
                if (mode != Mode.ENGINE) premise(shot.seed != null && shot.seed.entity == projectile,
                        "owned scale missed registered constructor/RNG bracket");
                shot.joined = state(projectile); shot.joinTag = tag(projectile);
                log("join uuid=" + projectile.getUUID() + " class=" + projectile.getClass().getName()
                        + " state=" + shot.joined + " seed=" + shot.seed + " energy=" + data.getEnergy());
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user) {
                    premise(!event.isCanceled() && user.isAlive() && !user.isRemoved() && !user.isCreative(), "owner tick eligibility");
                    ready(user.getBoundingBox());
                }
                Shot shot = shots.get(event.getEntity().getUUID());
                if (shot == null || shot.entity != event.getEntity() || shot.complete(frames())) return;
                premise(!event.isCanceled() && shot.before == null && shot.joined != null, "unpaired scale Pre");
                shot.before = state(shot.entity); shot.preTime = level.getGameTime();
                AABB query = shot.before.box.expandTowards(shot.before.velocity).inflate(1.0D);
                ready(query);
                premise(level.clip(new ClipContext(shot.before.pos, shot.before.pos.add(shot.before.velocity),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot.entity)).getType() == HitResult.Type.MISS,
                        "unexpected block in actual scale segment");
                List<Entity> candidates = level.getEntities(shot.entity, query,
                        entity -> entity != user && entity.canBeHitByProjectile());
                if (mode != Mode.ENGINE) premise(candidates.isEmpty(), "free-flight query has a hittable entity");
                else if (shot.frames.isEmpty()) {
                    premise(candidates.size() == 1 && candidates.get(0) == deflector
                            && deflector.getBoundingBox().inflate(0.3F)
                                    .clip(shot.before.pos, shot.before.pos.add(shot.before.velocity)).isPresent(),
                            "engine first-contact deflector/query premise");
                }
                else premise(candidates.isEmpty(), "engine second sweep still has a hittable candidate");
            });
            Consumer<EntityTickEvent.Post> bracket = event -> observe(() -> {
                if (event.getEntity() == user) { premise(!inUserPost, "nested owner Post"); inUserPost = true; }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) { premise(inUserPost, "owner Post bracket absent"); inUserPost = false; userPosts++; }
                Shot shot = shots.get(event.getEntity().getUUID());
                if (shot == null || shot.entity != event.getEntity() || shot.before == null) return;
                premise(shot.preTime == level.getGameTime(), "scale Pre/Post crossed world time");
                Frame frame = new Frame(shot.preTime, shot.before, state(shot.entity));
                shot.frames.add(frame); shot.before = null;
                log("frame uuid=" + shot.entity.getUUID() + " n=" + shot.frames.size() + " veto=" + shot.veto + " " + frame);
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Shot shot = shots.get(event.getProjectile().getUUID());
                if (shot == null || shot.entity != event.getProjectile() || shot.complete(frames())) return;
                shot.impacts.add(event);
                premise(mode == Mode.ENGINE && shot.before != null && event.getRayTraceResult() instanceof EntityHitResult hit
                        && hit.getEntity() == deflector, "unexpected owned scale impact");
                if (shot.veto) event.setCanceled(true);
                log("impact uuid=" + shot.entity.getUUID() + " veto=" + shot.veto + " type=" + event.getRayTraceResult().getType());
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() == user || event.getEntity() == deflector) incoming++;
            });
            add(constructing, EntityEvent.EntityConstructing.class, EventPriority.LOWEST, false);
            add(join, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add(pre, EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add(bracket, EntityTickEvent.Post.class, EventPriority.HIGHEST, false);
            add(post, EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add(impact, ProjectileImpactEvent.class, EventPriority.LOWEST, true);
            add(damage, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
        }

        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void ready(AABB box) {
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(space.minX <= box.minX && space.maxX >= box.maxX && space.minY <= box.minY && space.maxY >= box.maxY
                    && space.minZ <= box.minZ && space.maxZ >= box.maxZ && new ChunkPos(min).equals(chunk)
                    && new ChunkPos(max).equals(chunk) && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max),
                    "actual owner/projectile query left the owned ready chunk: " + box);
        }

        private void press() {
            UltimateLifeformSpec spec = mode == Mode.HOLD ? UltimateLifeformSpec.SCALE_SHOT : UltimateLifeformSpec.SCALE_SPLIT_SHOT;
            ability = power.getAbility(spec.id);
            premise(ability instanceof UltimateLifeformAbility ulf && ulf.spec() == spec, "registered scale ability absent");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            InputMethod method = mode == Mode.HOLD ? InputMethod.HOLD : InputMethod.CLICK;
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, method), "scale input admission failed");
            user.setDeltaMovement(0.12D, 0.0D, 0.0D);
            pressed = true; inPress = true;
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            try {
                BufferingState buffering = BufferingState.clickOnly();
                var input = AbilityInput.keyPress(KEY, ability, user, extra, method, 0, buffering, ability.getAbilityId());
                action = LivingComponentAction.getCurEntityAction(user);
                premise(input != null && input.action == action && action != null && action.ability == ability
                        && action.getPowerUser() == user && AbilityInput.isHeldByKey(user, action), "registered action/input identity failed");
            }
            finally { inPress = false; extra.release(); }
            log("registered-input spec=" + spec + " action=" + action + " controlledOwnerMotionOnce=true energy=" + data.getEnergy());
        }

        private Projectile factoryScale(boolean veto) {
            Entity entity = UltimateLifeformAddon.SCALE_PROJECTILE.get().create(level);
            premise(entity instanceof Projectile, "registered scale factory is not Projectile");
            Projectile projectile = (Projectile) entity; owned.add(projectile);
            try {
                projectile.getClass().getMethod("initialize", LivingEntity.class, float.class, boolean.class, float.class, float.class)
                        .invoke(projectile, user, 5F, false, 3F, 0F);
            }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof RuntimeException cause) throw cause;
                if (error.getCause() instanceof Error cause) throw cause;
                throw new IllegalStateException("scale initializer failed", error.getCause());
            }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("shared old/new initialize API missing", error); }
            shots.put(projectile.getUUID(), new Shot(projectile, null, veto));
            return projectile;
        }

        private void startEngine() {
            Projectile reverse = factoryScale(false), veto = factoryScale(true);
            premise(reverse.position().distanceTo(veto.position()) < EPS && reverse.getDeltaMovement().distanceTo(veto.getDeltaMovement()) < EPS
                            && Math.abs(reverse.getDeltaMovement().length() - 3.0D) < EPS,
                    "engine pair is not the same ordinary factory launch");
            deflector = new Deflector(level, reverse); owned.add(deflector);
            deflector.setNoAi(true); deflector.setNoGravity(true);
            deflector.setPos(reverse.getX(), reverse.getY() + 0.5D, reverse.getZ());
            premise(!deflector.getBoundingBox().intersects(user.getBoundingBox()) && deflector.getHealth() == 10F
                            && Math.abs(deflector.getBoundingBox().getYsize() - 1.4D) < EPS
                            && deflector.getBoundingBox().inflate(0.3F).maxY < reverse.position().add(reverse.getDeltaMovement()).y,
                    "ordinary deflector overlaps owner or has nondefault health");
            premise(level.addFreshEntity(deflector) && level.addFreshEntity(reverse) && level.addFreshEntity(veto), "engine actor admission failed");
            log("ENGINE-FACTORY not registered input: reverse=" + reverse.getUUID() + " veto=" + veto.getUUID() + " deflector=" + deflector.getUUID());
        }

        private void validate() {
            oracle(shots.size() == count(), "owned emission count=" + shots.size());
            for (Shot shot : shots.values()) {
                oracle(shot.joined != null && shot.frames.size() == frames() && !shot.entity.isRemoved(), "missing live natural flight window");
                oracle(shot.joinTag.getFloat("Damage") == 5F && !shot.joinTag.getBoolean("Hamon")
                        && shot.joinTag.getInt("LifeTicks") == 360 && user.getUUID().equals(shot.joined.owner), "scale scalar/owner drift");
                oracle(!shot.joined.shot, "fresh parent HasBeenShot was already true");
                for (int i = 0; i < shot.frames.size(); i++) {
                    Frame frame = shot.frames.get(i);
                    oracle(frame.after.age == frame.before.age && frame.after.savedAge == frame.before.savedAge + 1
                            && frame.after.shot && user.getUUID().equals(frame.after.owner), "parent tick/age/owner state drift");
                    if (i > 0) oracle(frame.before.age == shot.frames.get(i - 1).after.age + 1, "nonconsecutive natural projectile ages");
                    Vec3 expected = mode == Mode.ENGINE && !shot.veto && i == 0 ? frame.before.velocity.scale(-0.5D) : frame.before.velocity;
                    oracle(frame.after.pos.distanceTo(frame.before.pos.add(expected)) < EPS, "not exactly one post-interaction movement step");
                    oracle(frame.after.velocity.distanceTo(expected) < EPS, "current post-interaction velocity was damped, accelerated or restored");
                }
                if (mode != Mode.ENGINE) validateRegistered(shot);
                else {
                    oracle(shot.impacts.size() == 1 && shot.impacts.get(0).isCanceled() == shot.veto,
                            "engine actual first impact/veto witness differs");
                    oracle(shot.frames.get(1).before.velocity.distanceTo(shot.frames.get(0).after.velocity) < EPS,
                            "deflected/veto motion was replaced between ticks");
                }
            }
            oracle(incoming == 0 && user.isAlive(), "unexpected owner/deflector damage attempt");
            if (mode == Mode.ENGINE) oracle(deflector.calls == 1 && deflector.getHealth() == 10F && deflector.isAlive(), "deflection callback or no-damage control failed");
            else {
                oracle(user.position().distanceTo(startPosition) > 1.0E-4D, "controlled actor never naturally moved");
                oracle(released && !AbilityInput.isHeldByKey(user, action), "registered input release failed");
            }
            log("RESULT qualified=true count=" + shots.size() + " frames=" + frames() + " native=false energy=" + data.getEnergy());
        }

        private void validateRegistered(Shot shot) {
            Seed seed = shot.seed;
            premise(seed != null && seed.known.lengthSqr() > 1.0E-8D && seed.delta.lengthSqr() > 1.0E-8D,
                    "moving-owner launch was not an owner-motion discriminator");
            RandomSource random = RandomSource.create(RNG_SEED);
            double noise = 0.0172275D * seed.spread;
            Vec3 expected = Vec3.directionFromRotation(seed.pitch, seed.yaw).normalize()
                    .add(random.triangle(0, noise), random.triangle(0, noise), random.triangle(0, noise)).scale(seed.speed);
            oracle(shot.joined.pos.distanceTo(seed.eye.add(0, -0.3D, 0)) < EPS, "registered origin is not eye minus0.3");
            oracle(Math.abs(shot.joined.box.getXsize() - 0.6D) < EPS && Math.abs(shot.joined.box.getYsize() - 0.4D) < EPS,
                    "registered scale dimensions differ from0.6x0.4");
            oracle(shot.joined.velocity.distanceTo(expected) < EPS, "aim/spread differs: owner motion or fixed yaw fan was injected");
            oracle(Math.abs(Mth.wrapDegrees(shot.joined.yaw - MathUtil.yRotDegFromVec(expected))) < 0.002F
                    && Math.abs(Mth.wrapDegrees(shot.joined.pitch - MathUtil.xRotDegFromVec(expected))) < 0.002F, "launch rotation is not donor vector convention");
            if (mode == Mode.HOLD) oracle(seed.heldTicks == 5, "first held emission is not actual held tick5");
            oracle(shot.impacts.isEmpty() && shot.frames.get(7).after.savedAge >= 8 && shot.frames.get(7).after.leftOwner,
                    "free-flight or parent owner-exit witness absent");
            for (Frame frame : shot.frames) oracle(Math.abs(Mth.wrapDegrees(frame.after.yaw - shot.joined.yaw)) < 0.002F
                    && Math.abs(Mth.wrapDegrees(frame.after.pitch - shot.joined.pitch)) < 0.002F, "free-flight rotation changed after launch");
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 80, "finite input/flight watchdog; shots=" + shots.size());
                if (!started && userPosts >= 4) { started = true; if (mode == Mode.ENGINE) startEngine(); else press(); }
                if (pressed && !released && shots.size() >= count()) { AbilityInput.keyRelease(KEY, user); released = true; }
                if (started && shots.size() >= count() && shots.values().stream().allMatch(shot -> shot.complete(frames()))) {
                    validate(); close(); helper.succeed(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private CompoundTag tag(Projectile projectile) { CompoundTag tag = new CompoundTag(); projectile.saveWithoutId(tag); return tag; }
        private State state(Projectile projectile) {
            CompoundTag tag = tag(projectile);
            return new State(projectile.tickCount, tag.getInt("Age"), projectile.position(), projectile.getDeltaMovement(),
                    projectile.getBoundingBox(), projectile.getYRot(), projectile.getXRot(), projectile.isRemoved(),
                    tag.getBoolean("HasBeenShot"), tag.getBoolean("LeftOwner"), projectile.getOwner() == null ? null : projectile.getOwner().getUUID());
        }
        private void observe(Runnable operation) {
            if (closed || observerFailure != null) return;
            try { operation.run(); }
            catch (RuntimeException | Error error) { observerFailure = error; JojoMod.LOGGER.error("ULF scale observation failed", error); }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(KEY, user); }, failures);
            cleanup(() -> { if (user != null && user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(input -> !input.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("ULF input remains held"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            cleanup(() -> { boolean gone = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                log("cleanup owned=" + owned.size() + " removed=" + gone + " listeners=0 restoredBlocks=0");
                if (!gone) throw new IllegalStateException("ULF scale cleanup incomplete"); }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("ULF scale cleanup failed");
                failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable primary) {
            try { close(); } catch (RuntimeException | Error cleanup) { primary.addSuppressed(cleanup); }
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

    private static final class Deflector extends Cow {
        private final Projectile reverse;
        private int calls;
        Deflector(ServerLevel level, Projectile reverse) { super(EntityType.COW, level); this.reverse = reverse; }
        @Override public ProjectileDeflection deflection(Projectile projectile) {
            if (projectile == reverse) { calls++; return ProjectileDeflection.REVERSE; }
            return super.deflection(projectile);
        }
    }
}
