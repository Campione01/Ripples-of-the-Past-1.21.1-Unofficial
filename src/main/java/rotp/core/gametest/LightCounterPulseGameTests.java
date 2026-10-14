package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.ObjectEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightCounterPulseGameTests {
    private static final String BATCH = "light_counter_pulses";
    private static final short CONTROL = 181, GUARD = 182, PUNCH = 183;
    private static final Vec3 INITIAL = new Vec3(0.2, 0.08, -0.1);
    private static Suite active;

    private LightCounterPulseGameTests() {}

    private enum Kind { SUCCESS, REFUSED, M0, RESISTANCE, AIR, BOAT, BOAT_M0, ZERO, ZERO_REFUSED, ZERO_BOAT }
    private record Spec(String stand, Kind kind, String ability) {
        Spec(String stand, Kind kind) { this(stand, kind, "punch"); }
        boolean counter() { return kind != Kind.ZERO && kind != Kind.ZERO_REFUSED && kind != Kind.ZERO_BOAT; }
        boolean boat() { return kind == Kind.BOAT || kind == Kind.BOAT_M0 || kind == Kind.ZERO_BOAT; }
        boolean refused() { return kind == Kind.REFUSED || kind == Kind.ZERO_REFUSED; }
        double multiplier() { return kind == Kind.M0 || kind == Kind.BOAT_M0 ? 0 : 1; }
        String id() { return stand + "/" + ability + "/" + kind; }
    }
    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}
    private record Pulse(DamageSource source, float strength, Vec3 before) {}

    @GameTest(template = "empty", timeoutTicks = 2400, batch = BATCH)
    public static void registeredGuardCounterLightUsesSeparateDonorPulses(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try { suite.identity(); suite.listen(); helper.onEachTick(suite::tick); }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = BATCH)
    public static void releaseLightCounterBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        final double oldMultiplier;
        final List<Spec> cases = new ArrayList<>();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException eventFailure;
        int index, checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping light-counter suites");
            this.helper = helper;
            oldMultiplier = multiplier.get();
            for (String stand : new String[] {"star_platinum", "the_world"})
                for (Kind kind : Kind.values()) cases.add(new Spec(stand, kind));
            for (String ability : new String[] {"punch2", "punch3", "punch4"})
                cases.add(new Spec("star_platinum", Kind.SUCCESS, ability));
            cases.add(new Spec("the_world", Kind.SUCCESS, "punch2"));
            // 1.16 StandEntityLightAttack.addKnockback(guardCounter) for every humanoid Stand's light punch
            for (String stand : new String[] {"crazy_diamond", "gold_experience", "magicians_red"})
                for (Kind kind : new Kind[] {Kind.SUCCESS, Kind.BOAT}) cases.add(new Spec(stand, kind));
            for (String ability : new String[] {"punch2", "punch3", "punch4"})
                cases.add(new Spec("crazy_diamond", Kind.SUCCESS, ability));
            cases.add(new Spec("gold_experience", Kind.SUCCESS, "punch2"));
            active = this;
            helper.testInfo.addListener(this);
        }

        void identity() {
            for (Class<?> type : new Class<?>[] {StandEntityPunchAbility.class, StandEntityPunch.class,
                    StandEntity.class, DamageSourceModified.class, AbilityInput.class, LightCounterPulseGameTests.class}) {
                String actual = sha(type);
                var origin = type.getProtectionDomain().getCodeSource();
                String expected = System.getProperty("rotp.lightCounter.expected." + type.getName());
                helper.assertTrue(origin != null && (expected == null || actual.equalsIgnoreCase(expected)), "Light-counter class binding " + type.getName());
                log("LOADED " + type.getName() + " source=" + origin.getLocation() + " sha256=" + actual);
            }
        }

        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = e -> { if (scene != null && !closed) scene.incoming(e); };
            Consumer<LivingKnockBackEvent> knock = e -> { if (scene != null && !closed) scene.knock(e); };
            Consumer<EntityTickEvent.Pre> pre = e -> {
                if (scene != null && !closed && eventFailure == null)
                    try { scene.pre(e.getEntity()); } catch (RuntimeException error) { eventFailure = error; }
            };
            Consumer<EntityTickEvent.Post> post = e -> {
                if (scene != null && !closed && eventFailure == null)
                    try { scene.post(e.getEntity()); } catch (RuntimeException error) { eventFailure = error; }
            };
            listeners.add(incoming); listeners.add(knock); listeners.add(pre); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knock);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == cases.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close(); helper.assertTrue(failures.isEmpty(), "Light counter pulse failures; see SUMMARY"); helper.succeed(); return;
                    }
                    scene = new Scene(this, cases.get(index++));
                    scene.prepare();
                }
                scene.tick();
                helper.assertTrue(++scene.ticks < 60, "Light-counter scene timed out " + scene.spec.id()
                        + " casterTicks=" + scene.casterTicks + " primerTicks=" + scene.primerTicks + " incoming=" + scene.primeIncoming);
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.spec.id() + " " + detail);
            if (!passed) failures.add(scene.spec.id() + " " + detail);
        }
        void close() {
            if (closed) return;
            closed = true;
            listeners.forEach(NeoForge.EVENT_BUS::unregister);
            listeners.clear();
            if (scene != null) scene.close();
            multiplier.set(oldMultiplier);
            if (active == this) active = null;
            log("CLEANUP listeners=0 M=" + multiplier.get());
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final ServerLevel level;
        final List<Entity> entities = new ArrayList<>();
        final List<Actor> actors = new ArrayList<>();
        final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        final Map<ChunkPos, Boolean> chunks = new LinkedHashMap<>();
        final List<Pulse> pulses = new ArrayList<>();
        Actor caster, primer;
        Vec3 origin;
        Entity target;
        StandEntityPunch action;
        Vec3 beforeHurt, afterHurt, standAtHurt;
        boolean grounded, hurtResult, observing, done, closed;
        float counter, counterAtHurt, resistance;
        int ticks, hurtCalls, incomingCalls, primeIncoming, casterTicks, primerTicks;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.helper.getLevel(); }

        void prepare() {
            suite.multiplier.set(1D);
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            forceChunk(chunk);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 4; z++) {
                BlockPos pos = BlockPos.containing(origin).offset(x, -1, z);
                floor.put(pos, level.getBlockState(pos)); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            caster = actor(origin);
            press("manual_control", InputMethod.CLICK, CONTROL);
            premise(caster.stand.isManuallyControlled(), "Real manual control excludes passive guarding");
            if (spec.counter()) {
                press("guard", InputMethod.HOLD, GUARD);
                primer = actor(origin.add(0, 0, 1.5));
                primer.user.setYRot(180); primer.user.setYHeadRot(180);
                primer.stand.setYRot(180); primer.stand.setYHeadRot(180);
                premise(caster.stand.isStandBlocking() && caster.stand.canBlockFromAngle(primer.stand.position()), "Real front-facing held guard");
                ActionTarget guardTarget = new ActionTarget(caster.stand);
                LivingComponentAction.getComponent(primer.user).entityAim.setTarget(guardTarget);
                LivingComponentAction.getComponent(primer.stand).entityAim.setTarget(guardTarget);
                press(primer, "heavy_punch", InputMethod.CLICK, PUNCH);
            }
            else { premise(caster.stand.guardCounter() == 0, "Unprimed zero-counter control"); startPunch(); }
        }

        void tick() {
            if (action == null && primer != null && primeIncoming > 0) {
                counter = caster.stand.guardCounter();
                premise(caster.stand.isStandBlocking() && counter > 0 && counter <= 1 && caster.user.isAlive(),
                        "Actual registered Stand strike into guard generated counter=" + counter);
                startPunch();
            }
        }

        void startPunch() {
            suite.multiplier.set(spec.multiplier());
            target = spec.boat() ? new ObservedBoat(this) : new ObservedCow(this);
            configure(target, origin.add(0, spec.kind == Kind.AIR ? 0.7 : 0, 1.5));
            if (target instanceof LivingEntity living) {
                living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); living.setHealth(200);
                resistance = spec.kind == Kind.RESISTANCE ? 0.25F : 0;
                living.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
            }
            ActionTarget aim = new ActionTarget(target);
            LivingComponentAction.getComponent(caster.user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(aim);
            caster.power.setStamina(caster.power.getMaxStamina());
            target.setDeltaMovement(INITIAL);
            if (target instanceof LivingEntity living) living.setOnGround(spec.kind != Kind.AIR);
            observing = true;
            EntityActionInstance actual = press(spec.ability, InputMethod.CLICK, PUNCH);
            premise(actual instanceof StandEntityPunch && ((Ability) actual.ability).name().equals(spec.ability), "Exact registered light member " + spec.ability);
            action = (StandEntityPunch) actual;
            premise(near(caster.stand.guardCounter(), counter), "Real guard-to-punch transition retained counter");
            log("READY " + spec.id() + " counter=" + counter + " W=" + action.phasesLength.getFloat(rotp.core.powersystem.entityaction.ActionPhase.WINDUP));
            if (action.punchedTarget != null) post(caster.stand);
        }

        void pre(Entity entity) {
            if (primer != null && entity == primer.stand) primerTicks++;
            if (caster != null && entity == caster.stand) casterTicks++;
            if (caster == null || entity != caster.stand || action == null || done) return;
            if (action.punchedTarget == null) {
                target.setDeltaMovement(INITIAL);
                if (target instanceof LivingEntity living) living.setOnGround(spec.kind != Kind.AIR);
                observing = true;
            }
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (primer != null && event.getEntity() == caster.stand && event.getSource().getDirectEntity() == primer.stand)
                primeIncoming++;
            if (!observing || event.getEntity() != target) return;
            incomingCalls++;
            if (spec.refused()) event.setCanceled(true);
        }

        void knock(LivingKnockBackEvent event) {
            if (observing && event.getEntity() == target)
                pulses.add(new Pulse(DamageSourceModified.currentKnockbackSource(event.getEntity()), event.getStrength(), target.getDeltaMovement()));
        }

        void beginHurt(Entity entity) {
            if (!observing || entity != target) return;
            hurtCalls++;
            beforeHurt = entity.getDeltaMovement();
            standAtHurt = caster.stand.position();
            counterAtHurt = caster.stand.guardCounter();
            grounded = entity instanceof LivingEntity living && living.onGround();
        }
        void endHurt(Entity entity, boolean result) {
            if (!observing || entity != target) return;
            hurtResult = result; afterHurt = entity.getDeltaMovement();
        }

        void post(Entity entity) {
            if (caster == null || entity != caster.stand || action == null || done || action.punchedTarget == null) return;
            observing = false;
            premise(hurtCalls == 1 && beforeHurt != null && afterHurt != null, "One delegated native hurt");
            suite.check(near(counterAtHurt, counter), "Positive counter survives until actual impact=" + counterAtHurt);
            suite.check(target == action.punchedTarget.getMainEntity(), "Exact native target snapshot");
            Vec3 outward = new Vec3(target.getX() - standAtHurt.x, 0, target.getZ() - standAtHurt.z).normalize();
            double half = counter * 0.5;
            Vec3 expected = beforeHurt;
            if (target instanceof LivingEntity) {
                List<Pulse> nativePulses = pulses.stream().filter(p -> p.source != null).toList();
                suite.check(nativePulses.size() <= 1, "At most one native hurt event");
                if (!nativePulses.isEmpty()) {
                    suite.check(near(nativePulses.getFirst().strength, 0.4), "Native base stays0.4, counter is independent");
                    expected = donorKnockback(expected, 0.4 * (1 - resistance), outward, grounded);
                }
                int extras = counter > 0 ? (hurtResult ? 2 : 1) : 0;
                List<Pulse> detached = pulses.stream().filter(p -> p.source == null).toList();
                suite.check(detached.size() == extras, "Successful extra plus unconditional tail count=" + detached.size() + " expected=" + extras);
                for (Pulse pulse : detached) suite.check(near(pulse.strength, half), "Separate donor half-counter event strength");
                for (int i = 0; i < extras; i++) expected = donorKnockback(expected, half * (1 - resistance), outward, grounded);
                suite.check(near(target.getDeltaMovement(), expected), "Donor composed living velocity expected=" + expected + " actual=" + target.getDeltaMovement());
                suite.check(incomingCalls == 1, "One real incoming callback, including refused/M0");
            }
            else {
                if (counter > 0) expected = outward.scale(half);
                else expected = afterHurt;
                suite.check(near(target.getDeltaMovement(), expected), "Donor nonliving tail replaces velocity expected=" + expected + " actual=" + target.getDeltaMovement());
                suite.check(pulses.isEmpty(), "Nonliving route is an explicit velocity, not a living event");
            }
            suite.check(near(caster.stand.getFinisherMeter(), 0.2), "Attempt and target finisher bookkeeping unchanged");
            log("IMPACT " + spec.id() + " counter=" + counter + " hurt=" + hurtResult + " pulses=" + pulses.size()
                    + " before=" + beforeHurt + " afterHurt=" + afterHurt + " final=" + target.getDeltaMovement());
            done = true;
        }

        Actor actor(Vec3 origin) {
            Player user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            configure(user, origin);
            user.getAbilities().invulnerable = false;
            user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); user.setHealth(200);
            user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(spec.stand));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED, "Native Stand grant");
            power.setResolveLevel(4);
            premise(type.summon(user, power), "Native full summon");
            StandEntity stand = power.getSummonedStandEntity();
            premise(stand != null && !stand.isArmsOnlyMode(), "Full owner body");
            entities.add(stand);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0); stand.setYHeadRot(0);
            stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(16);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            Actor actor = new Actor(user, power, type, stand);
            actors.add(actor);
            return actor;
        }

        EntityActionInstance press(String name, InputMethod method, short key) {
            return press(caster, name, method, key);
        }

        EntityActionInstance press(Actor actor, String name, InputMethod method, short key) {
            Ability base = actor.power.getAbility(name);
            premise(base != null, "Registered ability " + name);
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, method), "Actual input admission " + name);
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, actor.user, null, method, 0, buffer, base.getAbilityId());
            premise(buffer.isActionSuccess, "Actual input success " + name);
            return held != null && held.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }
        void configure(Entity entity, Vec3 pos) {
            entities.add(entity); entity.setNoGravity(true); entity.noPhysics = true;
            if (entity instanceof Cow cow) cow.setNoAi(true);
            entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
            premise(level.addFreshEntity(entity), "Real level entity added");
            forceChunk(entity.chunkPosition());
        }
        void forceChunk(ChunkPos chunk) {
            chunks.computeIfAbsent(chunk, key -> {
                boolean already = level.getForcedChunks().contains(key.toLong());
                if (!already) level.setChunkForced(key.x, key.z, true);
                level.getChunk(key.x, key.z);
                return already;
            });
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, "LIGHT-COUNTER PREMISE " + spec.id() + " " + detail); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Actor actor : actors) {
                AbilityInput.keyRelease(PUNCH, actor.user); AbilityInput.keyRelease(GUARD, actor.user); AbilityInput.keyRelease(CONTROL, actor.user);
                if (actor.user.hasData(ModDataAttachmentTypes.CONTROLLER)) actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
                if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power);
            }
            entities.forEach(Entity::discard);
            // A Gold Experience primer knocks a tooth out of the guarding user.
            if (origin != null)
                for (ObjectEntity tooth : level.getEntitiesOfClass(ObjectEntity.class, new AABB(origin, origin).inflate(8))) tooth.discard();
            floor.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
            chunks.forEach((chunk, already) -> { if (!already) level.setChunkForced(chunk.x, chunk.z, false); });
            log("SCENE-CLEANUP " + spec.id() + " actorsRemoved=" + entities.stream().allMatch(Entity::isRemoved));
        }
    }

    private static final class ObservedCow extends Cow {
        final Scene scene;
        ObservedCow(Scene scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            scene.beginHurt(this); boolean result = super.hurt(source, amount); scene.endHurt(this, result); return result;
        }
    }
    private static final class ObservedBoat extends Boat {
        final Scene scene;
        ObservedBoat(Scene scene) { super(EntityType.BOAT, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            scene.beginHurt(this); boolean result = super.hurt(source, amount); scene.endHurt(this, result); return result;
        }
    }

    private static Vec3 donorKnockback(Vec3 before, double strength, Vec3 outward, boolean grounded) {
        return strength > 0 ? new Vec3(before.x * 0.5 + outward.x * strength,
                grounded ? Math.min(0.4, before.y * 0.5 + strength) : before.y,
                before.z * 0.5 + outward.z * strength) : before;
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.0002; }
    private static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 0.0002; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing light counter class bytes");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Light counter class binding", error); }
    }
    private static void log(String value) { JojoMod.getLogger().info("LIGHT-COUNTER " + value); }
}
