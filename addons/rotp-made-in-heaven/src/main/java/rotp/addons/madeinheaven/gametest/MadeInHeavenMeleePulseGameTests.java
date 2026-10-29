package rotp.addons.madeinheaven.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputKey;
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

/** Registered inputs and natural entity ticks; observers delegate hurt and movement to vanilla. */
@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenMeleePulseGameTests {
    private static final short ATTACK = 143;
    private static final short PRIME = 144;
    private static final Vec3 SEED = new Vec3(0.8, 0.2, -0.6);
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            MadeInHeavenAddon.id("melee_pulse_tests"));
    private static Suite active;
    private enum Move { HEAVY, CHOP, GRAB, BASIC }
    private enum Kind { HURT, REFUSED, IMMUNE, ZERO_STRENGTH, BOAT, BOAT_REFUSED, SHIELD }
    private record Spec(Move move, Kind kind, double strength, float meter, double multiplier, double resistance) {
        String id() { return move + "/" + kind + "/S" + strength + "/F" + meter + "/M" + multiplier + "/R" + resistance; }
        boolean detached() { return move == Move.HEAVY || move == Move.CHOP; }
        boolean boat() { return kind == Kind.BOAT || kind == Kind.BOAT_REFUSED; }
    }
    private record Actor(ObservedPlayer user, StandPower power, StandType type, StandEntity stand) {}
    private record Motion(Vec3 before, Vec3 after, int order) {}
    private static final class Hurt {
        final DamageSource source;
        final float amount;
        final int before;
        int after;
        boolean result;
        Hurt(DamageSource source, float amount, int before) { this.source = source; this.amount = amount; this.before = before; }
    }
    private static final class Pulse {
        final LivingKnockBackEvent event;
        final Vec3 before;
        final Vec3 standPos;
        final Vec3 targetPos;
        final boolean ground;
        final boolean nativeHurt;
        final int order;
        final List<Motion> writes = new ArrayList<>();
        double strength;
        double x;
        double z;
        Pulse(Scene scene, LivingKnockBackEvent event) {
            this.event = event; before = scene.target.getDeltaMovement();
            standPos = scene.caster.stand.position(); targetPos = scene.target.position();
            ground = ((LivingEntity) scene.target).onGround();
            nativeHurt = scene.hurt != null && scene.hurt.after == 0;
            order = ++scene.order;
        }
    }
    private MadeInHeavenMeleePulseGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mih_melee", timeoutTicks = 2400, batch = "mihMeleePulse01")
    public static void ordinaryHeavyAndChopUseDetachedAdmission(GameTestHelper helper) { start(helper, false); }

    @GameTest(template = "empty", templateNamespace = "rotp_mih_melee", timeoutTicks = 2400, batch = "mihMeleeMagnitude02")
    public static void chopAdditionalKnockbackIsFixedAcrossStrengthAndMeter(GameTestHelper helper) { start(helper, true); }

    @AfterBatch(batch = "mihMeleePulse01")
    public static void releasePulseBatch(ServerLevel level) { release(); }
    @AfterBatch(batch = "mihMeleeMagnitude02")
    public static void releaseMagnitudeBatch(ServerLevel level) { release(); }
    private static void release() { if (active != null) active.close(); log("BATCH-CLEANUP active=" + (active != null)); }
    private static void start(GameTestHelper helper, boolean magnitude) {
        Suite suite = new Suite(helper, magnitude);
        try {
            for (Class<?> type : new Class<?>[] {MadeInHeavenAddon.class, MadeInHeavenChopAbility.class,
                    MadeInHeavenChopAbility.MadeInHeavenChop.class, StandEntityHeavyPunchAbility.class,
                    StandEntityHeavyPunch.class, StandEntity.class, AbilityInput.class, MadeInHeavenMeleePulseGameTests.class}) {
                String actual = sha(type), expected = System.getProperty("mih.melee.expected." + type.getName());
                helper.assertTrue(expected == null || expected.equalsIgnoreCase(actual), "Native class mismatch " + type.getName());
                log("IDENTITY " + type.getName() + "=" + actual);
            }
            suite.listen(); helper.onEachTick(suite::tick);
        } catch (RuntimeException | Error failure) { suite.close(); throw failure; }
    }
    private static List<Spec> cases(boolean magnitude) {
        List<Spec> result = new ArrayList<>();
        if (magnitude) {
            for (double strength : new double[] {8, 15, 24}) for (float meter : new float[] {0.5F, 0.75F, 1F})
                for (Kind kind : new Kind[] {Kind.HURT, Kind.REFUSED, Kind.BOAT})
                    result.add(new Spec(Move.CHOP, kind, strength, meter, 1, 0));
        } else {
            for (Move move : new Move[] {Move.HEAVY, Move.CHOP}) {
                float meter = move == Move.CHOP ? 0.75F : 0.25F;
                for (Kind kind : Kind.values()) result.add(new Spec(move, kind, 15, meter, 1, 0));
                result.add(new Spec(move, Kind.HURT, 15, meter, 0, 0));
                result.add(new Spec(move, Kind.HURT, 15, meter, 1, 0.25));
            }
            for (Move move : new Move[] {Move.GRAB, Move.BASIC})
                for (double strength : new double[] {8, 24}) result.add(new Spec(move, Kind.HURT, strength, 0.25F, 1, 0));
        }
        return result;
    }
    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final boolean magnitude;
        final List<Spec> specs;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        final double oldMultiplier;
        Scene scene;
        RuntimeException eventFailure;
        int index;
        int checks;
        boolean closed;
        Suite(GameTestHelper helper, boolean magnitude) {
            helper.assertTrue(active == null, "Overlapping MIH suites");
            this.helper = helper; this.magnitude = magnitude; specs = cases(magnitude);
            oldMultiplier = multiplier.get(); active = this; helper.testInfo.addListener(this);
        }
        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (scene != null && scene.observing && event.getEntity() == scene.target
                        && event.getSource().getDirectEntity() == scene.caster.stand && scene.spec.kind == Kind.REFUSED)
                    event.setCanceled(true);
            };
            Consumer<LivingKnockBackEvent> begin = event -> {
                if (scene != null && scene.observing && event.getEntity() == scene.target) {
                    scene.current = new Pulse(scene, event); scene.pulses.add(scene.current);
                }
            };
            Consumer<LivingKnockBackEvent> end = event -> {
                if (scene != null && scene.observing && scene.current != null && scene.current.event == event) {
                    scene.current.strength = event.getStrength(); scene.current.x = event.getRatioX(); scene.current.z = event.getRatioZ();
                }
            };
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && eventFailure == null) try { scene.pre(event.getEntity()); }
                catch (RuntimeException failure) { eventFailure = failure; }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed && eventFailure == null) try { scene.post(event.getEntity()); }
                catch (RuntimeException failure) { eventFailure = failure; }
            };
            listeners.addAll(List.of(incoming, begin, end, pre, post));
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, true, LivingKnockBackEvent.class, begin);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, end);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }
        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY fix=" + (magnitude ? "02" : "01") + " scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close(); helper.assertTrue(failures.isEmpty(), "MIH melee failed; see SUMMARY"); helper.succeed(); return;
                    }
                    scene = new Scene(this, specs.get(index++)); scene.prepare();
                }
                scene.tick();
                helper.assertTrue(++scene.ticks < 64, "Natural action timeout " + scene.spec.id());
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void check(boolean passed, String detail) {
            checks++; log((passed ? "PASS " : "FAIL ") + scene.spec.id() + " " + detail);
            if (!passed) failures.add(scene.spec.id() + ": " + detail);
        }
        void close() {
            if (closed) return; closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear(); if (scene != null) scene.close(); multiplier.set(oldMultiplier); active = null;
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
        final List<Actor> actors = new ArrayList<>();
        final List<Entity> loose = new ArrayList<>();
        final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        final List<Pulse> pulses = new ArrayList<>();
        final List<Motion> motions = new ArrayList<>();
        Actor caster;
        Actor primer;
        Entity target;
        EntityActionInstance action;
        EntityActionInstance primeAction;
        Hurt hurt;
        Pulse current;
        Vec3 before;
        boolean immune;
        boolean observing;
        boolean done;
        boolean closed;
        int order;
        int ticks;
        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.helper.getLevel(); }
        void prepare() {
            suite.multiplier.set(spec.multiplier);
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++) {
                BlockPos pos = BlockPos.containing(origin.add(x, -1, z)); floor.put(pos, level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            caster = actor(MadeInHeavenAddon.MADE_IN_HEAVEN.get(), origin, spec.strength, 28);
            Vec3 targetPos = origin.add(0, 0.7, 1.5);
            if (spec.boat()) {
                target = new ObservedBoat(this); configure(target, targetPos);
                if (spec.kind == Kind.BOAT_REFUSED) target.setInvulnerable(true);
            } else if (spec.kind == Kind.SHIELD) {
                ObservedPlayer player = player(targetPos, 180);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                player.startUsingItem(InteractionHand.OFF_HAND); target = player;
            } else {
                ObservedCow cow = new ObservedCow(this);
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200);
                cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); configure(cow, targetPos); target = cow;
            }
            if (target instanceof LivingEntity living) living.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(spec.resistance);
            if (spec.kind == Kind.IMMUNE) {
                primer = actor(JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum")), origin.add(-0.5, 0, 0), 15, 32);
                LivingComponentAction.getComponent(primer.stand).entityAim.setTarget(new ActionTarget(target));
                primeAction = press(primer, "heavy_punch", PRIME);
            }
            log("START " + spec.id());
        }
        void tick() {
            if (action != null || primer != null || ticks < 3) return;
            if (spec.kind == Kind.SHIELD && !((Player) target).isBlocking()) return;
            begin();
        }
        void begin() {
            if (spec.move == Move.CHOP) unlock(caster, "chop");
            caster.stand.setFinisherMeter(spec.meter);
            if (spec.move == Move.GRAB) {
                unlock(caster, "grab_throw");
                caster.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget((LivingEntity) target);
            }
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(new ActionTarget(target));
            action = press(caster, spec.move == Move.BASIC ? "punch" : "heavy_punch", ATTACK);
            premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop) == (spec.move == Move.CHOP), "ordinary/chop factory");
            premise((action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB) == (spec.move == Move.GRAB), "ordinary/grab distinction");
            if (spec.move != Move.BASIC) premise(action instanceof StandEntityHeavyPunch, "real heavy action");
            if (spec.kind == Kind.ZERO_STRENGTH) {
                caster.user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(0);
                StandEntity.updateStandStatAttributes(caster.stand, caster.user); caster.stand.updateStrengthMultipliers();
            }
            log("PRESS " + spec.id() + " ability=" + ((Ability) action.ability).name() + " strength=" + caster.stand.getAttackDamage());
        }
        void pre(Entity entity) {
            if (entity != caster.stand || action == null || done || closed) return;
            observing = false; target.setDeltaMovement(SEED); before = target.getDeltaMovement();
            immune = target instanceof LivingEntity living && StandEntity.isStandAttackInvulnerable(living);
            hurt = null; current = null; pulses.clear(); motions.clear(); order = 0; observing = true;
        }
        void post(Entity entity) {
            if (done || closed) return;
            if (primer != null && action == null && entity == primer.stand && primeAction.punchedTarget != null) {
                premise(StandEntity.isStandAttackInvulnerable((LivingEntity) target), "natural primer established RAW immunity");
                begin();
            }
            if (entity != caster.stand || action == null) return;
            observing = false;
            if (action.punchedTarget == null) { target.setDeltaMovement(Vec3.ZERO); return; }
            premise(action.punchedTarget.getMainEntity() == target, "natural impact primary identity");
            verify(); done = true;
        }
        void verify() {
            List<Pulse> detached = pulses.stream().filter(p -> !p.nativeHurt).toList();
            List<Pulse> nativePulses = pulses.stream().filter(p -> p.nativeHurt).toList();
            if (spec.kind == Kind.IMMUNE) {
                premise(immune, "immunity persists to actual impact");
                suite.check(hurt == null && pulses.isEmpty() && near(target.getDeltaMovement(), before), "nonadmitted RAW immune has no hurt or pulses");
                return;
            }
            premise(hurt != null, "observed native hurt return");
            if (spec.kind == Kind.ZERO_STRENGTH) {
                premise(caster.stand.getAttackDamage() == 0, "real zero strength at impact");
                // Both donors and the port floor heavy raw damage at 1, so zero strength is admitted.
                suite.check(hurt.result && near(hurt.amount, 1), "zero strength preserves admitted donor damage floor of 1");
            }
            log("OBSERVED " + spec.id() + " hurt=" + hurt.result + " native=" + nativePulses.size() + " detached=" + detached.size() + " amount=" + hurt.amount);
            if (!spec.detached()) {
                suite.check(detached.isEmpty() && !nativePulses.isEmpty(), "unchanged basic/grab has only native hurt pulses");
                return;
            }
            double strength = spec.kind == Kind.ZERO_STRENGTH ? 0 : spec.strength;
            double half = spec.move == Move.CHOP ? 0.25 : (0.5 + strength / (8 - spec.meter * 4)) / 2;
            if (spec.boat()) {
                Vec3 direction = outward(target.position(), caster.stand.position());
                suite.check(!motions.isEmpty() && motions.getLast().order > hurt.after, "admitted nonliving tail after real hurt return");
                suite.check(hurt.result == (spec.kind != Kind.BOAT_REFUSED), "native vehicle hurt/refusal preserved");
                if (suite.magnitude || spec.move == Move.HEAVY)
                    suite.check(near(target.getDeltaMovement(), direction.scale(half)), "nonliving replacement fixed donor magnitude " + target.getDeltaMovement());
                return;
            }
            suite.check(detached.size() == (hurt.result ? 2 : 1), "successful living extra+tail; admitted no-hurt tail count=" + detached.size());
            suite.check(nativePulses.size() == (spec.kind == Kind.REFUSED ? 0 : spec.kind == Kind.SHIELD ? 2 : 1), "native hurt/shield/cancel pulse count=" + nativePulses.size());
            suite.check(((DamageSourceModified) hurt.source).jojo_ripples$addKnockback() == 0, "native source does not fold detached extra");
            for (Pulse pulse : pulses) {
                if (pulse.nativeHurt) {
                    double requested = spec.kind == Kind.SHIELD && near(pulse.event.getOriginalStrength(), 0.5) ? 0.5 : 0.4;
                    suite.check(near(pulse.strength, requested) && pulse.order < hurt.after, "native vanilla strength/order=" + pulse.strength);
                } else {
                    suite.check(pulse.order > hurt.after, "detached pulse after virtual hurt return");
                    if (suite.magnitude || spec.move == Move.HEAVY)
                        suite.check(near(pulse.strength, half), "donor half-additional=" + half + " actual=" + pulse.strength);
                }
                if (!pulse.writes.isEmpty()) {
                    double effective = pulse.strength * (1 - spec.resistance);
                    Vec3 ratio = new Vec3(pulse.x, 0, pulse.z).normalize().scale(effective);
                    Vec3 expected = new Vec3(pulse.before.x / 2 - ratio.x,
                            pulse.ground ? Math.min(0.4, pulse.before.y / 2 + effective) : pulse.before.y,
                            pulse.before.z / 2 - ratio.z);
                    suite.check(near(pulse.writes.getFirst().after, expected), "actual velocity after individual native/detached pulse");
                } else suite.check(false, "missing actual pulse velocity write");
            }
            if (spec.kind == Kind.SHIELD) suite.check(!hurt.result && ((Player) target).isBlocking()
                    && !((Player) target).getCooldowns().isOnCooldown(Items.SHIELD), "blocked tail preserves known-unfixed shield behavior");
        }
        Hurt beforeHurt(Entity entity, DamageSource source, float amount) {
            if (!observing || entity != target) return null;
            hurt = new Hurt(source, amount, ++order); return hurt;
        }
        void afterHurt(Hurt observation, boolean result) {
            if (observation != null) { observation.result = result; observation.after = ++order; }
        }
        void movement(Entity entity, Vec3 previous, Vec3 next) {
            if (!observing || entity != target) return;
            Motion motion = new Motion(previous, next, ++order); motions.add(motion);
            if (current != null) current.writes.add(motion);
        }
        EntityActionInstance press(Actor actor, String name, short key) {
            Ability base = actor.power.getAbility(name); premise(base != null, "registered " + name);
            if (actor == caster && name.equals("heavy_punch")) {
                var binding = actor.power.getMoveset().controlScheme.defaultGroup.separateBinds.get(name);
                premise(binding != null && binding.getFirst() == InputMethod.CLICK && binding.getSecond() instanceof InputKey input
                        && input.device == InputKey.RMB.device && input.keyCode == InputKey.RMB.keyCode, "registered RMB CLICK");
            }
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, InputMethod.CLICK),
                    "registered condition-checked admission " + name + " selected=" + (selected == null ? "null" : selected.ability.name()));
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, actor.user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
            premise(buffer.isActionSuccess, "native keyPress succeeded");
            return held != null && held.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }
        Actor actor(StandType type, Vec3 position, double strength, double speed) {
            ObservedPlayer user = player(position, 0);
            StandPower power = PowerClass.STAND.attachGet(user);
            premise(StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED, "public Stand grant");
            power.setResolveLevel(6); premise(type.summon(user, power), "native summon");
            StandEntity stand = power.getSummonedStandEntity();
            Actor actor = new Actor(user, power, type, stand); actors.add(actor);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0; stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(position.x, position.y, position.z, 0, 0); stand.setYHeadRot(0);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(strength);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user); power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            return actor;
        }
        ObservedPlayer player(Vec3 position, float yaw) {
            ObservedPlayer player = new ObservedPlayer(this); player.noPhysics = true;
            player.getAbilities().invulnerable = false; player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); player.setHealth(200);
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            configure(player, position); player.setYRot(yaw); player.setYHeadRot(yaw); return player;
        }
        void configure(Entity entity, Vec3 position) {
            entity.setNoGravity(true); entity.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(entity), "native entity added"); loose.add(entity);
        }
        void unlock(Actor actor, String name) {
            var result = UNLOCKS.forceUnlock(actor.power, name);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public unlock " + name);
        }
        void premise(boolean condition, String detail) { suite.helper.assertTrue(condition, spec.id() + " premise: " + detail); }
        @Override public void close() {
            if (closed) return; closed = true; observing = false;
            for (Actor actor : actors) {
                AbilityInput.keyRelease(ATTACK, actor.user); AbilityInput.keyRelease(PRIME, actor.user);
                actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
                if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power);
            }
            for (Entity entity : loose) entity.discard(); floor.forEach(level::setBlockAndUpdate);
        }
    }
    private static final class ObservedCow extends Cow {
        final Scene scene;
        ObservedCow(Scene scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hurt observation = scene.beforeHurt(this, source, amount); boolean result = super.hurt(source, amount);
            scene.afterHurt(observation, result); return result;
        }
        @Override public void setDeltaMovement(Vec3 value) {
            Vec3 previous = getDeltaMovement(); super.setDeltaMovement(value);
            if (scene != null) scene.movement(this, previous, getDeltaMovement());
        }
    }
    private static final class ObservedPlayer extends Player {
        final Scene scene;
        ObservedPlayer(Scene scene) { super(scene.level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "mih-melee-test")); this.scene = scene; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hurt observation = scene.beforeHurt(this, source, amount); boolean result = super.hurt(source, amount);
            scene.afterHurt(observation, result); return result;
        }
        @Override public void setDeltaMovement(Vec3 value) {
            Vec3 previous = getDeltaMovement(); super.setDeltaMovement(value);
            if (scene != null) scene.movement(this, previous, getDeltaMovement());
        }
    }
    private static final class ObservedBoat extends Boat {
        final Scene scene;
        ObservedBoat(Scene scene) { super(EntityType.BOAT, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hurt observation = scene.beforeHurt(this, source, amount); boolean result = super.hurt(source, amount);
            scene.afterHurt(observation, result); return result;
        }
        @Override public void setDeltaMovement(Vec3 value) {
            Vec3 previous = getDeltaMovement(); super.setDeltaMovement(value);
            if (scene != null) scene.movement(this, previous, getDeltaMovement());
        }
    }
    private static Vec3 outward(Vec3 target, Vec3 stand) { return new Vec3(target.x - stand.x, 0, target.z - stand.z).normalize(); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.0002; }
    private static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 0.0002; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (Exception failure) { throw new IllegalStateException("Missing native class " + type.getName(), failure); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-MIH-MELEE " + message); }
}
