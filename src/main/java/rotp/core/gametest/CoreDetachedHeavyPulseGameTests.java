package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility.Uppercut;
import rotp.core.impl.stands.theworld.TheWorldHeavyPunchAbility.TheWorldHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.KnockbackCollisionImpact;
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
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreDetachedHeavyPulseGameTests {
    private static final short ATTACK = 133;
    private static final short PRIME = 134;
    private static final short CONTROL = 135;
    private static final Vec3 SEED = new Vec3(0.8, 0.2, -0.6);
    private static final double SIN_60 = Math.sqrt(3) / 2;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(CoreDetachedHeavyPulseGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static Suite active;

    private enum Move {
        SP, TW, UPPERCUT, KICK, OTHER;
        String stand() { return this == TW || this == KICK ? "the_world" : this == OTHER ? "crazy_diamond" : "star_platinum"; }
        String finisher() { return this == UPPERCUT ? "uppercut" : this == KICK ? "kick" : null; }
        double halfAdditional() { return switch (this) { case TW -> 3; case UPPERCUT -> 0.6015625; case KICK -> 1.75; default -> 1.1875; }; }
        double horizontal() { return this == UPPERCUT ? 0.30078125 : halfAdditional(); }
        double upward() { return this == UPPERCUT ? 0.6015625 * SIN_60 : 0; }
    }
    private enum Kind { SUCCESS, REFUSED, IMMUNE, BOAT, BOAT_REFUSED, ANGLE, STOPPED, LETHAL, TOTEM,
        OWNER, SHIELD_FRONT, SHIELD_REAR, SECONDARY, SECONDARY_REFUSED, PRIMARY_REFUSED_SECONDARY,
        SECONDARY_IMMUNE, GRAB, DEFAULT_OFF }
    private record Spec(Move move, Kind kind, double multiplier, double resistance, boolean ground) {
        String id() { return move + "/" + kind + "/M" + multiplier + "/R" + resistance + "/" + (ground ? "ground" : "air"); }
        boolean detached() { return kind != Kind.GRAB && kind != Kind.DEFAULT_OFF; }
        boolean vehicle() { return kind == Kind.BOAT || kind == Kind.BOAT_REFUSED; }
        boolean secondary() { return kind == Kind.SECONDARY || kind == Kind.SECONDARY_REFUSED
                || kind == Kind.PRIMARY_REFUSED_SECONDARY || kind == Kind.SECONDARY_IMMUNE; }
    }
    private record Actor(ObservedPlayer user, StandPower power, StandType type, StandEntity stand) {}
    private record Motion(Vec3 before, Vec3 after, int order) {}
    private static final class Pulse {
        final LivingKnockBackEvent event;
        final Entity target;
        final DamageSource source;
        final boolean nativeHurt;
        final Vec3 before;
        final Vec3 standPos;
        final Vec3 primaryPos;
        final Vec3 targetPos;
        final boolean ground;
        final boolean shieldRaised;
        final boolean shieldCooldown;
        final int order;
        final List<Motion> writes = new ArrayList<>();
        float strength;
        double x;
        double z;
        boolean canceled;
        Pulse(Scene scene, LivingKnockBackEvent event) {
            this.event = event; target = event.getEntity();
            source = DamageSourceModified.currentKnockbackSource(event.getEntity());
            Hurt hurt = scene.hurts.get(target);
            nativeHurt = hurt != null ? hurt.after == 0 : near(event.getOriginalStrength(), 0.4);
            before = target.getDeltaMovement(); standPos = scene.caster.stand.position();
            primaryPos = scene.primary.position(); targetPos = target.position();
            ground = event.getEntity().onGround(); shieldRaised = event.getEntity().isUsingItem();
            shieldCooldown = target instanceof Player player && player.getCooldowns().isOnCooldown(Items.SHIELD);
            order = ++scene.order;
        }
    }
    private static final class Hurt {
        final DamageSource source;
        final float amount;
        final int before;
        boolean result;
        int after;
        Hurt(DamageSource source, float amount, int before) { this.source = source; this.amount = amount; this.before = before; }
    }

    private CoreDetachedHeavyPulseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 4000, batch = "r769_core_detached_heavy_pulses")
    public static void registeredRmbSeparatesNativeExtraAndAdmittedTail(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY heavy=" + sha(StandEntityHeavyPunch.class) + " uppercut=" + sha(Uppercut.class)
                    + " world=" + sha(TheWorldHeavyPunch.class) + " kick=" + sha(TheWorldKick.class)
                    + " fixture=" + sha(CoreDetachedHeavyPulseGameTests.class));
            suite.listen(); helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "r769_core_detached_heavy_pulses")
    public static void releaseDetachedHeavyBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        List<Spec> result = new ArrayList<>();
        for (Move move : new Move[] {Move.SP, Move.TW, Move.UPPERCUT, Move.KICK}) {
            for (double multiplier : new double[] {1, 2, 0}) result.add(new Spec(move, Kind.SUCCESS, multiplier, 0, true));
            for (Kind kind : new Kind[] {Kind.REFUSED, Kind.IMMUNE, Kind.BOAT, Kind.BOAT_REFUSED, Kind.ANGLE, Kind.STOPPED, Kind.LETHAL, Kind.TOTEM})
                result.add(new Spec(move, kind, 1, 0, kind != Kind.TOTEM));
            result.add(new Spec(move, Kind.BOAT, 0, 0, false));
            result.add(new Spec(move, Kind.SUCCESS, 1, 0.25, true));
            result.add(new Spec(move, Kind.SUCCESS, 1, 1, false));
            result.add(new Spec(move, Kind.SUCCESS, 1, 0, false));
            result.add(new Spec(move, Kind.GRAB, 1, 0, false));
        }
        for (Move move : new Move[] {Move.UPPERCUT, Move.KICK}) {
            result.add(new Spec(move, Kind.SHIELD_FRONT, 1, 0, false));
            result.add(new Spec(move, Kind.SHIELD_REAR, 1, 0, false));
        }
        result.add(new Spec(Move.UPPERCUT, Kind.OWNER, 1, 0.25, false));
        for (Kind kind : new Kind[] {Kind.SECONDARY, Kind.SECONDARY_REFUSED, Kind.PRIMARY_REFUSED_SECONDARY, Kind.SECONDARY_IMMUNE})
            result.add(new Spec(Move.KICK, kind, 1, 0, false));
        result.add(new Spec(Move.KICK, Kind.SECONDARY, 0, 0, false));
        result.add(new Spec(Move.OTHER, Kind.DEFAULT_OFF, 1, 0, false));
        return result;
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        final double oldMultiplier;
        final List<Spec> specs = cases();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException eventFailure;
        int index;
        int checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping detached pulse suites");
            this.helper = helper; oldMultiplier = multiplier.get(); active = this; helper.testInfo.addListener(this);
        }

        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> { if (scene != null && !closed) scene.incoming(event); };
            Consumer<LivingKnockBackEvent> begin = event -> { if (scene != null && !closed) scene.beginPulse(event); };
            Consumer<LivingKnockBackEvent> end = event -> { if (scene != null && !closed) scene.endPulse(event); };
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && eventFailure == null) {
                    try { scene.pre(event.getEntity()); } catch (RuntimeException error) { eventFailure = error; }
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed && eventFailure == null) {
                    try { scene.post(event.getEntity()); } catch (RuntimeException error) { eventFailure = error; }
                }
            };
            Consumer<PlayLevelSoundEvent.AtPosition> sound = event -> {
                if (scene != null && !closed && event.getLevel() == scene.level && event.getSound() != null
                        && event.getSound().value() == ModSoundEvents.THE_WORLD_PUNCH_HEAVY_TS_IMPACT.get())
                    scene.deferredSounds.add(++scene.order);
            };
            listeners.add(incoming); listeners.add(begin); listeners.add(end); listeners.add(pre); listeners.add(post); listeners.add(sound);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, true, LivingKnockBackEvent.class, begin);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, end);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, sound);
        }

        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close(); helper.assertTrue(failures.isEmpty(), "Detached pulses failed; see SUMMARY"); helper.succeed(); return;
                    }
                    scene = new Scene(this, specs.get(index++)); scene.prepare();
                }
                scene.tick();
                helper.assertTrue(++scene.ticks < 48, "Native pulse scene did not finish " + scene.spec.id()
                        + " nativeAges=" + scene.caster.stand.tickCount + "/" + scene.primary.tickCount
                        + " ground=" + (scene.primary instanceof LivingEntity living && living.onGround()));
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++; log((passed ? "PASS " : "FAIL ") + scene.spec.id() + " " + detail);
            if (!passed) failures.add(scene.spec.id() + ": " + detail);
        }
        void close() {
            if (closed) return; closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear(); if (scene != null) scene.close(); multiplier.set(oldMultiplier);
            if (active == this) active = null;
            log("CLEANUP listeners=0 M=" + JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get());
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
        final Map<Entity, Hurt> hurts = new IdentityHashMap<>();
        final Map<Entity, List<Pulse>> pulses = new IdentityHashMap<>();
        final Map<Entity, Pulse> currentPulse = new IdentityHashMap<>();
        final Map<Entity, Float> incomingAmounts = new IdentityHashMap<>();
        final Map<Entity, List<Motion>> motions = new IdentityHashMap<>();
        final List<Integer> deferredSounds = new ArrayList<>();
        Actor caster;
        Actor primer;
        Actor ownerTarget;
        Entity primary;
        LivingEntity secondary;
        EntityActionInstance action;
        EntityActionInstance primeAction;
        Vec3 origin;
        Vec3 primaryBefore;
        Vec3 secondaryBefore;
        TimeStopState stop;
        DamageContainer primerContext;
        DamageSource primerSource;
        int stopId;
        int ticks;
        int order;
        boolean observing;
        boolean primaryImmune;
        boolean secondaryImmune;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.helper.getLevel(); }

        void prepare() {
            suite.multiplier.set(spec.multiplier);
            premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == spec.multiplier, "actual server M");
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++) {
                BlockPos position = BlockPos.containing(origin.add(x, -1, z));
                floor.put(position, level.getBlockState(position)); level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
            }
            caster = actor(spec.move.stand(), origin, origin, false);
            Vec3 targetPos = origin.add(spec.kind == Kind.ANGLE ? 1.2 : 0, spec.ground ? 0 : 0.7, spec.kind == Kind.ANGLE ? 0.9 : 1.5);
            if (spec.vehicle()) {
                primary = new ObservedBoat(this); configure(primary, targetPos); loose.add(primary);
                if (spec.kind == Kind.BOAT_REFUSED) primary.setInvulnerable(true);
                premise(primary.isPickable() && primary.getControllingPassenger() == null, "real unoccupied native boat");
            }
            else if (spec.kind == Kind.OWNER) {
                ownerTarget = actor("star_platinum", origin.add(4, 0.7, 0), targetPos, true); primary = ownerTarget.stand;
                ownerTarget.stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(spec.resistance);
                ownerTarget.user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.4);
            }
            else if (spec.kind == Kind.SHIELD_FRONT || spec.kind == Kind.SHIELD_REAR || spec.kind == Kind.TOTEM) {
                ObservedPlayer player = player(targetPos, spec.kind == Kind.SHIELD_FRONT ? 180 : 0);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(spec.kind == Kind.TOTEM ? Items.TOTEM_OF_UNDYING : Items.SHIELD));
                if (spec.kind == Kind.TOTEM) player.setHealth(5);
                else player.startUsingItem(InteractionHand.OFF_HAND);
                primary = player;
            }
            else primary = cow(targetPos);
            if (spec.kind == Kind.LETHAL) ((LivingEntity) primary).setHealth(5);
            if (primary instanceof LivingEntity living) living.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(spec.resistance);
            if (spec.secondary()) secondary = cow(origin.add(0.8, 0.7, 1.5));
            if (spec.kind == Kind.IMMUNE || spec.kind == Kind.SECONDARY_IMMUNE) {
                primer = actor("star_platinum", origin.add(-0.5, 0, 0), origin.add(-0.5, 0, 0), false);
                primer.user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(32);
                StandEntity.updateStandStatAttributes(primer.stand, primer.user);
                LivingComponentAction.getComponent(primer.stand).entityAim.setTarget(new ActionTarget(spec.kind == Kind.IMMUNE ? primary : secondary));
                primeAction = press(primer, "heavy_punch", PRIME);
            }
            else if (cancelledPrimer()) {
                primer = actor("crazy_diamond", origin.add(-0.5, 0, 0), origin.add(-0.5, 0, 0), false);
                primer.user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(32);
                StandEntity.updateStandStatAttributes(primer.stand, primer.user);
                LivingComponentAction.getComponent(primer.stand).entityAim.setTarget(new ActionTarget(primary));
                primeAction = press(primer, "heavy_punch", PRIME);
            }
            log("START " + spec.id() + " primary=" + primary.getId() + " secondary=" + (secondary != null ? secondary.getId() : -1));
        }

        void tick() {
            if (action != null || primer != null || ticks < 3) return;
            if (ticks == 3) log("READY " + spec.id() + " ages=" + caster.stand.tickCount + "/" + primary.tickCount
                    + " position=" + primary.position() + " ground=" + (primary instanceof LivingEntity living && living.onGround()));
            if (primary instanceof Cow living && spec.ground && !living.onGround()) return;
            if ((spec.kind == Kind.SHIELD_FRONT || spec.kind == Kind.SHIELD_REAR) && !((Player) primary).isBlocking()) return;
            begin();
        }

        void begin() {
            if (spec.move.finisher() != null) unlock(caster, spec.move.finisher());
            caster.stand.setFinisherMeter(spec.move.finisher() != null ? 0.75F : 0);
            if (spec.kind == Kind.GRAB) {
                unlock(caster, "grab_throw");
                caster.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget((LivingEntity) primary);
            }
            if (spec.kind == Kind.STOPPED) {
                unlock(caster, "time_stop");
                premise(caster.power.getAbility("time_stop").isAbilityUnlocked(caster.power), "native stopped-scene lifecycle skill unlocked");
                stop = level.getData(ModDataAttachmentTypes.TIME_STOP.get()); stopId = caster.user.getId();
                caster.user.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200, 0));
                premise(stop.tryPutInstance(new TimeStopState.Instance(stopId, 200, 200, new ChunkPos(primary.blockPosition()), 1,
                        caster.user.getId(), "r769_core_detached_pulses")), "native stop lifecycle admitted");
                premise(stop.shouldFreeze(primary) && !stop.shouldFreeze(caster.stand), "native target frozen and caster ticks");
            }
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(new ActionTarget(primary));
            action = press(caster, "heavy_punch", ATTACK);
            premise(action instanceof StandEntityHeavyPunch, "registered RMB heavy action");
            premise((spec.move == Move.UPPERCUT) == (action instanceof Uppercut)
                    && (spec.move == Move.KICK) == (action instanceof TheWorldKick)
                    && (spec.move == Move.TW) == (action instanceof TheWorldHeavyPunch), "resolved original ordinary/finisher factory");
            premise((action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB) == (spec.kind == Kind.GRAB), "registered grab distinction");
            premise(caster.stand.getAttackDamage() == 15 && caster.stand.getAttackSpeed() == 28, "native strength/cadence getters");
            log("PRESS " + spec.id() + " ability=" + ((Ability) action.ability).name() + " phases=" + action.phasesLength);
        }

        void pre(Entity entity) {
            if (action == null || entity != caster.stand || done || closed) return;
            observing = false;
            if (cancelledPrimer()) premise(((LivingEntity) primary).damageContainers.isEmpty()
                    && primerContext != null && primerContext.getSource() == primerSource,
                    "prior canceled-hit container is released before the next native attempt");
            if (spec.kind == Kind.STOPPED) premise(stop.shouldFreeze(primary) && !stop.shouldFreeze(caster.stand),
                    "native stop remains active at actual action tick");
            if (primary instanceof Cow living) premise(living.onGround() == spec.ground, "actual native floor/air state before action tick");
            primary.setDeltaMovement(SEED); if (secondary != null) secondary.setDeltaMovement(SEED);
            if (ownerTarget != null) ownerTarget.user.setDeltaMovement(SEED);
            primaryBefore = primary.getDeltaMovement(); secondaryBefore = secondary != null ? secondary.getDeltaMovement() : null;
            primaryImmune = primary instanceof LivingEntity living && StandEntity.isStandAttackInvulnerable(living);
            secondaryImmune = secondary != null && StandEntity.isStandAttackInvulnerable(secondary);
            hurts.clear(); pulses.clear(); currentPulse.clear(); incomingAmounts.clear(); motions.clear(); order = 0; observing = true;
        }

        void post(Entity entity) {
            if (done || closed) return;
            if (primer != null && action == null && entity == primer.stand && primeAction.punchedTarget != null) {
                if (cancelledPrimer()) premise(primerContext != null && ((LivingEntity) primary).damageContainers.isEmpty()
                        && !StandEntity.isStandAttackInvulnerable((LivingEntity) primary),
                        "canceled default-off primer is cleaned without establishing RAW immunity");
                else premise(StandEntity.isStandAttackInvulnerable((LivingEntity) (spec.kind == Kind.IMMUNE ? primary : secondary)), "real primer established active RAW immunity");
                begin();
            }
            if (action == null || entity != caster.stand) return;
            if (action.punchedTarget == null) {
                observing = false; primary.setDeltaMovement(primary instanceof Cow && spec.ground ? new Vec3(0, -0.08, 0) : Vec3.ZERO);
                if (secondary != null) secondary.setDeltaMovement(Vec3.ZERO);
                if (ownerTarget != null) ownerTarget.user.setDeltaMovement(Vec3.ZERO); return;
            }
            observing = false;
            premise(action.punchedTarget.getMainEntity() == primary, "native impact retains primary identity");
            verify(primary, true); if (secondary != null) verify(secondary, false);
            if (ownerTarget != null) verifyOwner();
            if (spec.kind == Kind.STOPPED && spec.move == Move.TW) {
                suite.check(deferredSounds.isEmpty(), "TW impact sound remains deferred during native stopped pulses");
                int afterTailAndWall = order;
                stop.removeInstance(stopId); stop = null;
                suite.check(deferredSounds.size() == 1 && deferredSounds.get(0) > afterTailAndWall,
                        "TW actual impact sound plays on native resume after tail/wall capture");
            }
            done = true;
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (cancelledPrimer() && primer != null && event.getEntity() == primary && event.getSource().getDirectEntity() == primer.stand) {
                premise(!((LivingEntity) primary).damageContainers.isEmpty(), "native incoming primer has its own active container");
                primerSource = event.getSource(); primerContext = ((LivingEntity) primary).damageContainers.peek();
                event.setCanceled(true);
                log("CANCELED-PRIMER " + spec.id() + " source=" + primerSource.getMsgId()
                        + " add=" + ((DamageSourceModified) primerSource).jojo_ripples$addKnockback());
                return;
            }
            if (!observing || event.getSource().getDirectEntity() != caster.stand) return;
            incomingAmounts.put(event.getEntity(), event.getOriginalAmount());
            if (event.getEntity() == primary && (spec.kind == Kind.REFUSED || spec.kind == Kind.PRIMARY_REFUSED_SECONDARY)
                    || event.getEntity() == secondary && spec.kind == Kind.SECONDARY_REFUSED) event.setCanceled(true);
            log("INCOMING " + spec.id() + " target=" + event.getEntity().getId() + " amount=" + event.getAmount());
        }

        void beginPulse(LivingKnockBackEvent event) {
            if (!observing || !tracked(event.getEntity())) return;
            Pulse pulse = new Pulse(this, event);
            pulses.computeIfAbsent(event.getEntity(), ignored -> new ArrayList<>()).add(pulse); currentPulse.put(event.getEntity(), pulse);
        }
        void endPulse(LivingKnockBackEvent event) {
            if (!observing) return;
            Pulse pulse = currentPulse.get(event.getEntity());
            if (pulse == null || pulse.event != event) return;
            pulse.strength = event.getStrength(); pulse.x = event.getRatioX(); pulse.z = event.getRatioZ(); pulse.canceled = event.isCanceled();
            log("EVENT " + spec.id() + " target=" + pulse.target.getId() + " order=" + pulse.order + " native=" + pulse.nativeHurt
                    + " requested=" + event.getOriginalStrength() + " strength=" + pulse.strength + " ratios=" + pulse.x + "," + pulse.z + " before=" + pulse.before
                    + " canceled=" + pulse.canceled + " shield=" + pulse.shieldRaised + "/" + pulse.shieldCooldown);
        }
        void movement(Entity entity, Vec3 before, Vec3 after) {
            if (!observing || !tracked(entity)) return;
            Motion motion = new Motion(before, after, ++order); motions.computeIfAbsent(entity, ignored -> new ArrayList<>()).add(motion);
            Pulse pulse = currentPulse.get(entity); if (pulse != null) pulse.writes.add(motion);
            log("VELOCITY " + spec.id() + " target=" + entity.getId() + " order=" + motion.order + " before=" + before + " after=" + after);
        }
        Hurt beforeHurt(Entity entity, DamageSource source, float amount) {
            if (!observing || !tracked(entity)) return null;
            Hurt hurt = new Hurt(source, amount, ++order); hurts.put(entity, hurt); return hurt;
        }
        void afterHurt(Hurt hurt, boolean result) {
            if (hurt == null) return; hurt.result = result; hurt.after = ++order;
            log("HURT-RETURN " + spec.id() + " amount=" + hurt.amount + " result=" + result + " order=" + hurt.after);
        }
        boolean tracked(Entity entity) { return entity == primary || entity == secondary || ownerTarget != null && entity == ownerTarget.user; }

        void verify(Entity target, boolean main) {
            boolean immune = main ? primaryImmune : secondaryImmune;
            List<Pulse> all = pulses.getOrDefault(target, List.of());
            List<Pulse> detached = all.stream().filter(pulse -> !pulse.nativeHurt).toList();
            List<Pulse> nativePulses = all.stream().filter(pulse -> pulse.nativeHurt).toList();
            Hurt hurt = hurts.get(target);
            if (immune) {
                suite.check(all.isEmpty() && hurt == null && !incomingAmounts.containsKey(target), "real pre-impact RAW immunity rejects all pulses target=" + target.getId());
                suite.check(near(target.getDeltaMovement(), main ? primaryBefore : secondaryBefore), "RAW rejection preserves seeded movement"); return;
            }
            if (target instanceof Boat boat) {
                premise(hurt != null, "native boat hurt was observed");
                suite.check(near(hurt.amount, 15 * spec.multiplier), "boat M once before real hurt amount=" + hurt.amount);
                suite.check(hurt.result == (spec.kind != Kind.BOAT_REFUSED)
                        && near(boat.getDamage(), spec.kind == Kind.BOAT_REFUSED ? 0 : 150 * spec.multiplier), "actual native vehicle hurt result/damage");
                suite.check(boat.isRemoved() == (spec.multiplier > 0 && spec.kind != Kind.BOAT_REFUSED), "real vehicle removal/refusal/M0");
                Vec3 direction = outward(target.position(), caster.stand.position());
                Vec3 expected = new Vec3(direction.x * spec.move.halfAdditional(), spec.move.upward(), direction.z * spec.move.halfAdditional());
                if (spec.move == Move.UPPERCUT) expected = new Vec3(direction.x * 0.30078125, spec.move.upward(), direction.z * 0.30078125);
                if (spec.move == Move.KICK) expected = rotateKick(direction).scale(1.75);
                suite.check(near(boat.getDeltaMovement(), expected), "nonliving tail replaces seed after native return/removal/M0 velocity=" + boat.getDeltaMovement());
                List<Motion> writes = motions.getOrDefault(target, List.of());
                suite.check(!writes.isEmpty() && writes.get(writes.size() - 1).order > hurt.after,
                        "nonliving replacement occurs after actual hurt return=" + hurt.result);
                suite.check(nativePulses.isEmpty() && detached.isEmpty(), "nonliving replacement is not a fake Living knockback event"); return;
            }
            if (spec.kind == Kind.OWNER) { verifyOwnerTarget(all, detached, nativePulses); return; }
            premise(hurt != null, "native living hurt return observed");
            if (caster.stand instanceof ObservedAttackerStand observed) {
                suite.check(observed.calls == 1 && observed.target == target && near(observed.raw, 15)
                        && !observed.result, "single custom virtual attacker dispatch delegates actual canceled hurt");
            }
            if (cancelledPrimer()) suite.check(((LivingEntity) target).damageContainers.isEmpty()
                    && primerContext.getSource() == primerSource
                    && near(((DamageSourceModified) primerSource).jojo_ripples$addKnockback(), 2.375),
                    "both native attempts release only their own containers; prior source metadata is unchanged");
            double raw = main ? 15 : 7.5;
            suite.check(near(hurt.amount, raw * spec.multiplier), "actual primary/half-RAW secondary M once amount=" + hurt.amount);
            if (!spec.detached()) {
                suite.check(detached.isEmpty() && nativePulses.size() == 1, "default-off grab/other caller has only original native event");
                suite.check(hurt.source instanceof DamageSourceModified modified
                        && near(modified.jojo_ripples$addKnockback(), spec.move.halfAdditional() * 2), "default-off existing additional metadata preserved"); return;
            }
            suite.check(detached.size() == (hurt.result ? 2 : 1), "actual hurt-return controls extra; admitted tail unconditional return=" + hurt.result + " detached=" + detached.size());
            suite.check(nativePulses.size() == (spec.kind == Kind.REFUSED || spec.kind == Kind.PRIMARY_REFUSED_SECONDARY && main
                    || spec.kind == Kind.SECONDARY_REFUSED && !main ? 0 : spec.kind == Kind.SHIELD_FRONT ? 2 : 1),
                    "native hurt count retains vanilla shield recoil0.5 plus hurt0.4; canceled Incoming has neither");
            suite.check(hurt.source instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() == caster.power
                    && modified.jojo_ripples$standInvulTicks() == 10 && near(modified.jojo_ripples$addKnockback(), 0)
                    && near(modified.jojo_ripples$knockbackXRotDeg(), 0) && near(modified.jojo_ripples$knockbackYRotDeg(), 0)
                    && near(modified.jojo_ripples$knockbackXRotAppliedStrength(), 0)
                    && modified.jojo_ripples$breaksStandGuard() == (spec.move == Move.UPPERCUT || spec.move == Move.KICK),
                    "only native pulse fields neutralized; power/invul10/guard intact");
            for (Pulse pulse : nativePulses) {
                double requested = spec.kind == Kind.SHIELD_FRONT && near(pulse.event.getOriginalStrength(), 0.5) ? 0.5 : 0.4;
                suite.check(near(pulse.event.getOriginalStrength(), requested) && near(pulse.strength, requested),
                        "native recoil/hurt remains unangled " + requested + ", not folded additional=" + pulse.strength);
                checkDirection(pulse, false, false); checkVelocity(pulse, 0, spec.resistance);
                suite.check(pulse.order < hurt.after, "native event happens inside real hurt");
            }
            for (int i = 0; i < detached.size(); i++) {
                Pulse pulse = detached.get(i); boolean tail = i == detached.size() - 1;
                suite.check(near(pulse.strength, spec.move.horizontal()), "detached captured virtual additional/angle strength=" + pulse.strength);
                checkDirection(pulse, true, tail); checkVelocity(pulse, spec.move.upward(), spec.resistance);
                suite.check(pulse.order > hurt.after, "detached event follows actual hurt return");
                if (cancelledPrimer()) suite.check(pulse.source == null,
                        "actual detached event is outside the cleaned native damage context");
                if (!main && spec.move == Move.KICK && !tail)
                    suite.check(!near(outward(pulse.targetPos, pulse.standPos), outward(pulse.primaryPos, pulse.standPos)), "secondary success and PRIMARY tail origins are genuinely distinct");
            }
            if (spec.kind == Kind.SHIELD_REAR && detached.size() == 2) {
                suite.check(detached.get(0).shieldRaised && !detached.get(0).shieldCooldown
                        && !detached.get(1).shieldRaised && detached.get(1).shieldCooldown, "successful shield disable lies strictly between extra and tail");
                suite.check(near(((Player) target).getCooldowns().getCooldownPercent(Items.SHIELD, 0), 1), "success-only native100 shield cooldown");
            }
            if (spec.kind == Kind.SHIELD_FRONT) suite.check(!hurt.result && ((Player) target).isBlocking()
                    && !((Player) target).getCooldowns().isOnCooldown(Items.SHIELD), "blocked shield gets tail only, remains raised");
            if (spec.kind == Kind.LETHAL) suite.check(hurt.result && !target.isAlive() && detached.size() == 2, "lethal hurt does not suppress admitted tail");
            if (spec.kind == Kind.TOTEM) suite.check(hurt.result && target.isAlive() && ((Player) target).getOffhandItem().isEmpty()
                    && detached.size() == 2, "actual consumed totem does not suppress admitted tail");
            var impact = KnockbackCollisionImpact.getExistingHandler(target);
            if (!main) {
                suite.check(impact == null || !impact.isActive(), "secondary receives no primary wall registration");
                Hurt primaryHurt = hurts.get(primary);
                Entity expectedLast = primaryHurt != null && primaryHurt.result ? primary : null;
                suite.check(caster.stand.getLastHurtMob() == expectedLast && caster.user.getLastHurtMob() == expectedLast,
                        "secondary does not replace native primary hurt-result bookkeeping");
            }
            else if (hurt.result && spec.resistance < 1) checkImpact(target, impact);
            else suite.check(impact == null || !impact.isActive(), "refused/resistant primary does not invent active wall impact");
            if (spec.kind == Kind.STOPPED) suite.check(all.stream().allMatch(pulse -> pulse.canceled)
                    && stop.shouldFreeze(target), "each pulse uses established native canceled/stacked stop path");
        }

        void checkDirection(Pulse pulse, boolean detached, boolean tail) {
            Vec3 expected = outward(detached && tail ? pulse.primaryPos : pulse.targetPos, pulse.standPos);
            if (!detached && spec.kind == Kind.SHIELD_FRONT && near(pulse.event.getOriginalStrength(), 0.5)) expected = expected.scale(-1);
            if (detached && spec.move == Move.KICK) expected = rotateKick(expected);
            Vec3 actual = new Vec3(-pulse.x, 0, -pulse.z).normalize();
            suite.check(near(actual, expected), (detached ? tail ? "tail PRIMARY" : "extra attempted-target" : "native unrotated") + " direction=" + actual + " expected=" + expected);
        }

        void checkVelocity(Pulse pulse, double upward, double resistance) {
            double effective = pulse.strength * (1 - resistance);
            if (effective == 0) {
                suite.check(pulse.writes.isEmpty(), "full resistance rejects actual horizontal/upward velocity writes"); return;
            }
            if (pulse.writes.isEmpty()) { suite.check(false, "missing actual velocity write for pulse order=" + pulse.order); return; }
            Motion horizontal = pulse.writes.get(0);
            double divisor = spec.kind == Kind.STOPPED ? 1 : 2;
            Vec3 ratio = new Vec3(pulse.x, 0, pulse.z).normalize().scale(effective);
            double y = spec.kind == Kind.STOPPED ? Math.min(0.4, pulse.before.y + effective)
                    : pulse.ground ? Math.min(0.4, pulse.before.y / 2 + effective) : pulse.before.y;
            Vec3 expected = new Vec3(pulse.before.x / divisor - ratio.x, y, pulse.before.z / divisor - ratio.z);
            suite.check(near(horizontal.before, pulse.before) && near(horizontal.after, expected), "actual horizontal pulse velocity=" + horizontal.after + " expected=" + expected);
            if (upward != 0) {
                suite.check(pulse.writes.size() == 2 && near(pulse.writes.get(1).before, horizontal.after)
                        && near(pulse.writes.get(1).after, horizontal.after.add(0, upward * (1 - resistance), 0)),
                        "explicit resisted upward write follows this horizontal pulse");
            }
            else suite.check(pulse.writes.size() == 1, "unangled pulse has exactly one actual velocity write");
        }

        void checkImpact(Entity target, KnockbackCollisionImpact impact) {
            boolean active = impact != null && impact.isActive(); suite.check(active, "successful primary wall registration after tail");
            if (!active) return;
            var saved = impact.serializeNBT(level.registryAccess());
            Vec3 direction = Vec3.CODEC.parse(NbtOps.INSTANCE, saved.get("Vec")).result().orElse(Vec3.ZERO);
            double resistance = ((LivingEntity) target).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            suite.check(near(direction, target.getDeltaMovement().normalize())
                    && near(saved.getDouble("Power"), target.getDeltaMovement().length() * (1 - resistance)),
                    "wall capture sees final post-tail direction and magnitude");
        }

        void verifyOwnerTarget(List<Pulse> all, List<Pulse> detached, List<Pulse> nativePulses) {
            suite.check(detached.size() == 2 && nativePulses.size() == 1, "native Stand target gets native plus both detached pulses");
            for (Pulse pulse : all) checkDirection(pulse, !pulse.nativeHurt, pulse == all.get(all.size() - 1));
            for (Pulse pulse : detached) suite.check(near(pulse.strength, 0.30078125), "Stand horizontal detached cosine retained");
            for (int i = 0; i < all.size(); i++) {
                Pulse pulse = all.get(i);
                Vec3 after = i + 1 < all.size() ? all.get(i + 1).before : primary.getDeltaMovement();
                double effective = pulse.strength * 0.75;
                Vec3 ratio = new Vec3(pulse.x, 0, pulse.z).normalize().scale(effective);
                double y = pulse.ground ? Math.min(0.4, pulse.before.y / 2 + effective) : pulse.before.y;
                Vec3 expected = new Vec3(pulse.before.x / 2 - ratio.x, y + (pulse.nativeHurt ? 0 : spec.move.upward() * 0.75),
                        pulse.before.z / 2 - ratio.z);
                suite.check(near(after, expected), "actual Stand body velocity after individual native/detached pulse=" + after);
            }
        }

        void verifyOwner() {
            List<Pulse> ownerPulses = pulses.getOrDefault(ownerTarget.user, List.of());
            suite.check(ownerPulses.size() == 3, "each native Stand horizontal call forwards to real owner");
            for (Pulse pulse : ownerPulses) {
                if (pulse.writes.isEmpty()) { suite.check(false, "owner horizontal write missing"); continue; }
                double effective = pulse.strength * 0.6;
                Vec3 ratio = new Vec3(pulse.x, 0, pulse.z).normalize().scale(effective);
                Motion horizontal = pulse.writes.get(0);
                suite.check(near(horizontal.after, new Vec3(pulse.before.x / 2 - ratio.x, pulse.before.y, pulse.before.z / 2 - ratio.z)),
                        "owner uses actual native forwarded horizontal resistance");
            }
            List<Motion> ownerWrites = motions.getOrDefault(ownerTarget.user, List.of());
            long upwardWrites = ownerWrites.stream().filter(motion -> near(motion.before.x, motion.after.x)
                    && near(motion.before.z, motion.after.z) && near(motion.after.y - motion.before.y, spec.move.upward() * 0.75 * 0.6)).count();
            suite.check(upwardWrites == 2 && ownerTarget.user.hurtMarked, "both explicit upwards pass through Stand resistance then owner resistance");
            checkImpact(ownerTarget.user, KnockbackCollisionImpact.getExistingHandler(ownerTarget.user));
        }

        EntityActionInstance press(Actor actor, String name, short key) {
            Ability base = actor.power.getAbility(name); premise(base != null, "registered " + name);
            if (name.equals("heavy_punch")) {
                var binding = actor.power.getMoveset().controlScheme.defaultGroup.separateBinds.get(name);
                premise(binding != null && binding.getFirst() == InputMethod.CLICK && binding.getSecond() instanceof InputKey input
                        && input.device == InputKey.RMB.device && input.keyCode == InputKey.RMB.keyCode && input.modifier == null,
                        "actual registered plain RMB CLICK binding");
            }
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, InputMethod.CLICK), "native CLICK admission " + name);
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, actor.user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
            premise(buffer.isActionSuccess, "native keyPress succeeded " + name);
            return held != null && held.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }
        Actor actor(String standName, Vec3 userPos, Vec3 standPos, boolean manual) {
            ObservedPlayer user = player(userPos, manual ? 180 : 0);
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED, "grant native " + standName);
            power.setResolveLevel(2);
            if (actors.isEmpty() && spec.kind == Kind.STOPPED)
                power.setResolveLevel(Math.max(2, power.getAbility("time_stop").getRequiredResolveLevel(power)));
            premise(type.summon(user, power), "native summon");
            StandEntity stand = power.getSummonedStandEntity(); premise(stand != null && stand.getUser() == user, "real owner binding");
            if (actors.isEmpty() && spec.move == Move.SP && spec.kind == Kind.REFUSED) {
                stand.discard();
                stand = new ObservedAttackerStand(this).withStandType(type);
                power.setSummonedStand(stand); stand.copySharedEffectsFromUser(user);
                ((EntityStandType) type).finalizeStandSummonFromAction(user, power, stand, true);
                premise(stand.getUser() == user && power.getSummonedStandEntity() == stand && stand.isAddedToLevel(),
                        "public native owner/summon path installs virtual attacker observer");
            }
            Actor actor = new Actor(user, power, type, stand); actors.add(actor);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0; stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(standPos.x, standPos.y, standPos.z, manual ? 180 : 0, 0); stand.setYHeadRot(manual ? 180 : 0);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(28);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user); power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            if (manual) { press(actor, "manual_control", CONTROL); premise(stand.isManuallyControlled(), "native manual target excludes passive guard"); }
            return actor;
        }
        ObservedPlayer player(Vec3 position, float yaw) {
            ObservedPlayer player = new ObservedPlayer(this); player.getAbilities().invulnerable = false;
            player.noPhysics = true; player.setNoGravity(true); player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); player.setHealth(100);
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            player.moveTo(position.x, position.y, position.z, yaw, 0); player.setYHeadRot(yaw);
            premise(level.addFreshEntity(player), "observed native player added"); loose.add(player); return player;
        }
        ObservedCow cow(Vec3 position) {
            ObservedCow cow = new ObservedCow(this); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(spec.resistance);
            configure(cow, position); cow.setNoGravity(!spec.ground);
            cow.setDeltaMovement(0, spec.ground ? -0.08 : 0, 0); loose.add(cow); return cow;
        }
        void configure(Entity entity, Vec3 position) {
            entity.setNoGravity(true); entity.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(entity), "real native entity added");
        }
        void unlock(Actor actor, String skill) {
            var result = UNLOCKS.forceUnlock(actor.power, skill);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public unlock " + skill);
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, spec.id() + " premise: " + detail); }
        boolean cancelledPrimer() {
            return spec.move == Move.SP && (spec.kind == Kind.REFUSED
                    || spec.kind == Kind.SUCCESS && spec.multiplier == 1 && spec.resistance == 0 && spec.ground);
        }
        @Override public void close() {
            if (closed) return; closed = true; observing = false;
            if (stop != null) stop.removeInstance(stopId);
            for (Actor actor : actors) {
                AbilityInput.keyRelease(ATTACK, actor.user); AbilityInput.keyRelease(PRIME, actor.user); AbilityInput.keyRelease(CONTROL, actor.user);
                actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
                if (actor.user.hasData(ModDataAttachmentTypes.CONTROLLER)) actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
                if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power);
            }
            for (Entity entity : loose) entity.discard();
            floor.forEach(level::setBlockAndUpdate);
        }
    }

    // Observers delegate every hurt/velocity operation to vanilla; they never implement pulse physics.
    private static final class ObservedCow extends Cow {
        final Scene scene;
        ObservedCow(Scene scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hurt hurt = scene.beforeHurt(this, source, amount); boolean result = super.hurt(source, amount); scene.afterHurt(hurt, result); return result;
        }
        @Override public void setDeltaMovement(Vec3 value) {
            Vec3 before = getDeltaMovement(); super.setDeltaMovement(value); if (scene != null) scene.movement(this, before, getDeltaMovement());
        }
    }
    private static final class ObservedPlayer extends Player {
        final Scene scene;
        ObservedPlayer(Scene scene) { super(scene.level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "pulse-mock-user")); this.scene = scene; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hurt hurt = scene.beforeHurt(this, source, amount); boolean result = super.hurt(source, amount); scene.afterHurt(hurt, result); return result;
        }
        @Override public void setDeltaMovement(Vec3 value) {
            Vec3 before = getDeltaMovement(); super.setDeltaMovement(value); if (scene != null) scene.movement(this, before, getDeltaMovement());
        }
    }
    private static final class ObservedBoat extends Boat {
        final Scene scene;
        ObservedBoat(Scene scene) { super(EntityType.BOAT, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hurt hurt = scene.beforeHurt(this, source, amount); boolean result = super.hurt(source, amount); scene.afterHurt(hurt, result); return result;
        }
        @Override public void setDeltaMovement(Vec3 value) {
            Vec3 before = getDeltaMovement(); super.setDeltaMovement(value); if (scene != null) scene.movement(this, before, getDeltaMovement());
        }
    }

    private static final class ObservedAttackerStand extends StandEntity {
        final Scene scene;
        int calls;
        Entity target;
        float raw;
        boolean result;
        ObservedAttackerStand(Scene scene) { super(ModEntityTypes.STAR_PLATINUM_STAND.get(), scene.level); this.scene = scene; }
        @Override public boolean hurtWithStandAttack(Entity target, DamageSource source, float raw) {
            calls++; this.target = target; this.raw = raw;
            result = super.hurtWithStandAttack(target, source, raw);
            return result;
        }
    }

    private static Vec3 outward(Vec3 target, Vec3 stand) { return new Vec3(target.x - stand.x, 0, target.z - stand.z).normalize(); }
    private static Vec3 rotateKick(Vec3 direction) { return new Vec3(direction.x * 0.5 - direction.z * SIN_60, 0, direction.x * SIN_60 + direction.z * 0.5); }
    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.0002; }
    private static boolean near(Vec3 actual, Vec3 expected) { return actual.distanceTo(expected) < 0.0002; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }
    private static void log(String text) { JojoMod.getLogger().info("B-S13-03 " + text); }
}
