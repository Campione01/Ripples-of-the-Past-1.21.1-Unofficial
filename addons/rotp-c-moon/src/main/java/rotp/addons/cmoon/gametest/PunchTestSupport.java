package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CMoonPunchAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

final class PunchTestSupport {
    enum Facet { ENTITY, FINISHER, COUNTER, PHASE }
    enum Kind {
        SUCCESS, BOAT, CART, MISS, BLOCK, FRIENDLY, CANCELLED, REFUSED, IMMUNE, M0,
        ZERO_RESOLVE, CAPPED, RESOLVE, BOAT_M0, CART_REFUSED, ZERO, ZERO_CANCELLED, ZERO_BOAT,
        ARMS, EXPIRE, CAPPED_MISS, FAST_SKIP, ARMS_CAPPED, GRAB_REFUSAL, CHARGED_RELEASE
    }
    private static final short CONTROL = 291, GUARD = 292, PUNCH = 293, SEED = 294;
    private static final Vec3 INITIAL = new Vec3(0.2, 0.08, -0.1);
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(CMoonAddon.id("punch_gametest"));
    private static Suite active;

    private PunchTestSupport() {}

    static void run(GameTestHelper helper, Facet facet) {
        GravityTestSupport.identity(helper, CMoonAddon.class, CMoonPunchAbility.class,
                CMoonPunchAbility.CMoonPunch.class, CMoonStandEntity.class,
                StandEntityPunchAbility.class, EntityActionInstance.class, StandEntity.class,
                StandStatFormulas.class, StandPower.class, AbilityInput.class, LivingComponentAction.class,
                DamageSourceModified.class, GravityTestSupport.class, GravityTestSupport.Actor.class,
                PunchTestSupport.class, ObservedCow.class, ObservedBoat.class, ObservedCart.class);
        Suite suite = new Suite(helper, facet);
        try { suite.listen(); helper.onEachTick(suite::tick); }
        catch (RuntimeException | Error failure) { suite.closeAfterFailure(failure); throw failure; }
    }

    static void release() { if (active != null) active.close(); }

    static void runTeamOwnership(GameTestHelper helper) {
        GravityTestSupport.identity(helper, PunchTestSupport.class, TeamOwnershipProbe.class, Scene.class,
                Suite.class, GravityTestSupport.Actor.class, CMoonPunchAbility.class,
                CMoonPunchAbility.CMoonPunch.class, CMoonAddon.class, Scoreboard.class, PlayerTeam.class);
        TeamOwnershipProbe probe = new TeamOwnershipProbe(helper);
        Throwable failure = null;
        try { probe.run(); }
        catch (RuntimeException | Error error) { failure = error; }
        try { probe.close(); }
        catch (RuntimeException | Error error) { failure = preserve(failure, error); }
        rethrow(failure);
        log("OWNERSHIP SUMMARY scenes=2 checks=" + probe.checks + " failures=" + probe.failures.size()
                + " details=" + probe.failures);
        helper.assertTrue(probe.failures.isEmpty(), "CM punch team ownership failed; see OWNERSHIP SUMMARY");
        helper.succeed();
    }

    private static final class TeamOwnershipProbe implements AutoCloseable {
        final GameTestHelper helper;
        final Scoreboard scoreboard;
        final List<PlayerTeam> teams = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final PlayerTeam originalMemberTeam;
        Suite suite;
        PlayerTeam prior;
        int checks;

        TeamOwnershipProbe(GameTestHelper helper) {
            this.helper = helper;
            scoreboard = helper.getLevel().getScoreboard();
            originalMemberTeam = scoreboard.getPlayersTeam("cm-gravity");
        }

        void run() {
            prior = create("cmr1p" + UUID.randomUUID().toString().substring(0, 8));
            scoreboard.addPlayerToTeam("cm-gravity", prior);
            suite = new Suite(helper, Facet.ENTITY);
            for (boolean collision : new boolean[] {true, false}) {
                Scene scene = new Scene(suite, Kind.FRIENDLY);
                suite.scene = scene;
                scene.prepare();
                String name = "cmp" + scene.caster.user.getId();
                String sentinel = "cmr1s" + scene.caster.user.getId();
                helper.assertTrue(scoreboard.getPlayerTeam(name) == null
                        && scoreboard.getPlayersTeam(sentinel) == null, "Ownership probe requires fresh fixture names");
                PlayerTeam foreign = null;
                if (collision) {
                    foreign = create(name);
                    foreign.setAllowFriendlyFire(true);
                    foreign.setSeeFriendlyInvisibles(false);
                    foreign.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
                    foreign.setCollisionRule(Team.CollisionRule.NEVER);
                    scoreboard.addPlayerToTeam(sentinel, foreign);
                }
                String context = collision ? "COLLISION" : "FREE";
                check(scene.originalTeams.get(scene.caster) == prior && scene.caster.user.getTeam() == prior,
                        context + " actual original actor team captured");
                boolean refused = false;
                try { scene.startPunch(); }
                catch (IllegalStateException error) {
                    if (!collision || !error.getMessage().equals("CM punch test team already exists: " + name)) throw error;
                    refused = true;
                }
                PlayerTeam created = scene.team;
                check(collision ? refused && !scene.pressed : !refused && scene.pressed,
                        context + " actual helper admission/early collision refusal");
                check(collision ? scoreboard.getPlayerTeam(name) == foreign
                        : created != null && scoreboard.getPlayerTeam(name) == created,
                        context + " actual team instance retained after setup");
                check(collision ? unchanged(foreign, sentinel)
                        : !created.isAllowFriendlyFire() && created.getPlayers().equals(Set.of(
                                scene.caster.user.getScoreboardName(), scene.target.getScoreboardName())),
                        context + " flags and exact members after setup");
                scene.close();
                check(scene.closed && scene.actors.stream().allMatch(a -> a.user.isRemoved() && a.stand.isRemoved())
                        && scene.targets.stream().allMatch(Entity::isRemoved), context + " actual scene actors/target cleaned");
                check(collision ? scoreboard.getPlayerTeam(name) == foreign : scoreboard.getPlayerTeam(name) == null,
                        context + " foreign team preserved/owned team removed after cleanup");
                check(collision ? unchanged(foreign, sentinel) && scoreboard.getPlayersTeam(sentinel) == foreign
                        : created.getPlayers().contains(scene.caster.user.getScoreboardName())
                                && scoreboard.getPlayersTeam(scene.target.getScoreboardName()) == null,
                        context + " foreign flags/membership preserved or owned mappings removed");
                check(scene.teamRestored && scene.caster.user.getTeam() == prior
                        && scoreboard.getPlayersTeam("cm-gravity") == prior,
                        context + " actual original actor membership restored");
                log("OWNERSHIP STATE " + context + " refused=" + refused + " pressed=" + scene.pressed
                        + " foreignPresent=" + (foreign != null && scoreboard.getPlayerTeam(name) == foreign)
                        + " foreignMembers=" + (foreign == null ? "none" : foreign.getPlayers())
                        + " actorTeam=" + scene.caster.user.getTeam().getName());
            }
        }

        PlayerTeam create(String name) {
            helper.assertTrue(scoreboard.getPlayerTeam(name) == null, "Probe will not reuse a foreign team");
            PlayerTeam team = scoreboard.addPlayerTeam(name);
            teams.add(team);
            return team;
        }

        boolean unchanged(PlayerTeam team, String sentinel) {
            return team.isAllowFriendlyFire() && !team.canSeeFriendlyInvisibles()
                    && team.getNameTagVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS
                    && team.getCollisionRule() == Team.CollisionRule.NEVER
                    && team.getPlayers().equals(Set.of(sentinel));
        }

        void check(boolean passed, String detail) {
            checks++;
            log("OWNERSHIP " + (passed ? "PASS " : "FAIL ") + detail);
            if (!passed) failures.add(detail);
        }

        @Override public void close() {
            Throwable failure = null;
            try { if (suite != null) suite.close(); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                if (scoreboard.getPlayersTeam("cm-gravity") == prior) {
                    if (originalMemberTeam == null) scoreboard.removePlayerFromTeam("cm-gravity");
                    else scoreboard.addPlayerToTeam("cm-gravity", originalMemberTeam);
                }
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            for (PlayerTeam team : teams) try {
                if (scoreboard.getPlayerTeam(team.getName()) == team) scoreboard.removePlayerTeam(team);
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                helper.assertTrue(scoreboard.getPlayersTeam("cm-gravity") == originalMemberTeam
                        && teams.stream().noneMatch(t -> scoreboard.getPlayerTeam(t.getName()) == t),
                        "Ownership probe restores previous membership and removes only its own fixture teams");
                log("OWNERSHIP CLEANUP originalMemberTeam=" + originalMemberTeam + " memberRestored=true ownedTeamsRemoved=true");
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            rethrow(failure);
        }
    }

    private record Pulse(int order, DamageSource source, float strength, double x, double z, Vec3 before) {}
    private record PhaseSample(long time, ActionPhase phase, float tick, float length,
            double speed, float meter, boolean arms, int userTick, int standTick) {}

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final Facet facet;
        final List<Kind> kinds;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final ModConfigSpec.ConfigValue<Double> multiplier;
        final double oldMultiplier;
        Scene scene;
        Throwable observerFailure;
        int index, checks;
        boolean closed;

        Suite(GameTestHelper helper, Facet facet) {
            helper.assertTrue(active == null, "Punch-only batches must not overlap");
            this.helper = helper;
            this.facet = facet;
            multiplier = JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", "standPowerMultiplier"));
            helper.assertTrue(multiplier != null, "Actual damage multiplier config exists");
            oldMultiplier = multiplier.get();
            kinds = switch (facet) {
                case ENTITY -> List.of(Kind.BOAT, Kind.CART, Kind.SUCCESS, Kind.MISS, Kind.BLOCK, Kind.FRIENDLY);
                case FINISHER -> List.of(Kind.SUCCESS, Kind.CANCELLED, Kind.REFUSED, Kind.IMMUNE, Kind.M0,
                        Kind.MISS, Kind.BLOCK, Kind.FRIENDLY, Kind.ZERO_RESOLVE, Kind.CAPPED, Kind.RESOLVE);
                case COUNTER -> List.of(Kind.SUCCESS, Kind.CANCELLED, Kind.REFUSED, Kind.M0, Kind.BOAT, Kind.CART,
                        Kind.BOAT_M0, Kind.CART_REFUSED, Kind.IMMUNE, Kind.FRIENDLY, Kind.MISS,
                        Kind.ZERO, Kind.ZERO_CANCELLED, Kind.ZERO_BOAT, Kind.GRAB_REFUSAL);
                case PHASE -> List.of(Kind.SUCCESS, Kind.ARMS, Kind.EXPIRE, Kind.CAPPED_MISS, Kind.FAST_SKIP, Kind.ARMS_CAPPED, Kind.CHARGED_RELEASE);
            };
            active = this;
            helper.testInfo.addListener(this);
        }

        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> scene.pre(event.getEntity()));
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> scene.post(event.getEntity()));
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> scene.incoming(event));
            Consumer<LivingKnockBackEvent> knock = event -> observe(() -> scene.knock(event));
            listeners.addAll(List.of(pre, post, incoming, knock));
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knock);
        }

        void observe(Runnable call) {
            if (scene != null && !closed && observerFailure == null) {
                try { call.run(); } catch (RuntimeException | Error failure) { observerFailure = failure; }
            }
        }

        void tick() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException failure) throw failure;
                if (observerFailure instanceof Error failure) throw failure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == kinds.size()) {
                        log("SUMMARY facet=" + facet + " scenes=" + index + " checks=" + checks
                                + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "CM punch facet failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, kinds.get(index++));
                    scene.prepare();
                }
                scene.tick();
                helper.assertTrue(++scene.ticks < 180, "Native punch scene timeout " + scene.kind
                        + " userTicks=" + scene.caster.user.tickCount + " standTicks=" + scene.caster.stand.tickCount
                        + " primeIncoming=" + scene.primeIncoming + " seed=" + scene.seedReturned);
            } catch (RuntimeException | Error failure) { closeAfterFailure(failure); throw failure; }
        }

        void check(boolean passed, String detail) {
            checks++;
            String name = facet + "/" + scene.kind;
            log((passed ? "PASS " : "FAIL ") + name + " " + detail);
            if (!passed) failures.add(name + " " + detail);
        }

        void closeAfterFailure(Throwable failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }

        void close() {
            if (closed) return;
            closed = true;
            Throwable failure = null;
            for (Object listener : listeners) try { NeoForge.EVENT_BUS.unregister(listener); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            listeners.clear();
            try { if (scene != null) scene.close(); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try { multiplier.set(oldMultiplier); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            if (active == this) active = null;
            log("CLEANUP listeners=0 M=" + multiplier.get() + " originalM=" + oldMultiplier);
            rethrow(failure);
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final ServerLevel level;
        final List<GravityTestSupport.Actor> actors = new ArrayList<>();
        final List<Entity> targets = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Map<GravityTestSupport.Actor, PlayerTeam> originalTeams = new LinkedHashMap<>();
        final List<Pulse> pulses = new ArrayList<>();
        final List<PhaseSample> timeline = new ArrayList<>();
        GravityTestSupport.Actor caster, primer, seed;
        Entity target;
        LivingEntity standLastBefore, userLastBefore;
        PlayerTeam team;
        EntityActionInstance action;
        JsonObject originalSpeed;
        Vec3 beforeHurt, afterHurt, standAtHurt, afterImpact;
        PhaseSample perform, recovery;
        float initialMeter, counter, counterAtHurt, healthBefore, healthAfter, damageBefore, damageAfter;
        float initialW, initialP, initialR;
        double initialSpeed;
        float initialStamina;
        int heldTicks;
        int ticks, hurtCalls, incomingCalls, order, returnOrder, primeIncoming, previousUserTick = -1, previousStandTick = -1;
        boolean priming, seedStarted, seedReturned, seedSucceeded, pressed, tracking, impact, done, closed;
        boolean hurtResult, grounded, samePhaseFrozen = true, naturalUser = true, naturalStand = true, resolveExpired;
        boolean preImmune, permission, getterWitness;
        boolean releasedCharge, teamRestored, teamOwned;
        double passedAmount = Double.NaN, sourceAdd = Double.NaN, sourceMultiply = Double.NaN;
        ActionPhase previousPhase;
        float previousLength;

        Scene(Suite suite, Kind kind) { this.suite = suite; this.kind = kind; level = suite.helper.getLevel(); }
        boolean counterCase() { return suite.facet == Facet.COUNTER && !kind.name().startsWith("ZERO"); }
        boolean boat() { return kind == Kind.BOAT || kind == Kind.BOAT_M0 || kind == Kind.ZERO_BOAT; }
        boolean cart() { return kind == Kind.CART || kind == Kind.CART_REFUSED; }
        boolean empty() { return kind == Kind.MISS || kind == Kind.CAPPED_MISS; }
        boolean cancelled() { return kind == Kind.CANCELLED || kind == Kind.ZERO_CANCELLED; }
        boolean nativeRefused() { return kind == Kind.REFUSED || kind == Kind.CART_REFUSED; }
        boolean arms() { return kind == Kind.ARMS || kind == Kind.ARMS_CAPPED; }
        boolean capped() { return kind == Kind.CAPPED || kind == Kind.CAPPED_MISS || kind == Kind.ARMS_CAPPED || kind == Kind.FAST_SKIP; }
        boolean multiplierZero() { return kind == Kind.M0 || kind == Kind.BOAT_M0; }
        boolean mainEntity() { return !empty() && kind != Kind.BLOCK && kind != Kind.FRIENDLY; }
        boolean charged() { return kind == Kind.CHARGED_RELEASE; }
        boolean grab() { return kind == Kind.GRAB_REFUSAL; }

        void prepare() {
            suite.multiplier.set(1D);
            if (kind == Kind.FAST_SKIP) {
                StandStats stats = CMoonAddon.C_MOON.get().getStandStats();
                originalSpeed = new JsonObject();
                originalSpeed.addProperty("speed", stats.getValue(StandStats.StatWithValue.SPEED));
                originalSpeed.addProperty("speedMax", stats.getValue(StandStats.StatWithValue.SPEED_MAX));
                JsonObject configured = new JsonObject(); configured.addProperty("speed", 24); configured.addProperty("speedMax", 24);
                stats.applyConfig(configured);
                log("CONFIG-SPEED original=" + originalSpeed + " actual=" + stats.speed() + "/" + stats.speed(1) + " power=" + stats.power());
            }
            caster = actor(kind == Kind.RESOLVE || kind == Kind.EXPIRE, kind == Kind.EXPIRE ? 3 : 100);
            if (kind == Kind.RESOLVE || kind == Kind.EXPIRE) caster.power.setResolveLevel(caster.power.getMaxResolveLevel());
            caster.stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            if (arms()) caster.stand.setArmsOnlyMode(true, true);
            if (kind == Kind.EXPIRE) caster.debitTo(caster.power.getMaxStamina() * 0.2F);
            if (kind == Kind.ZERO_RESOLVE) {
                caster.power.setResolveLevel(0);
                premise(StandEntity.isFinisherMechanicUnlocked(caster.power), "actual starting finisher keeps CM meter unlocked at Resolve0");
            }
            caster.stand.setFinisherMeter(capped() ? 1 : 0);
            if (counterCase()) {
                press(caster, "manual_control", InputMethod.CLICK, CONTROL);
                premise(caster.stand.isManuallyControlled(), "actual manual control before held guard");
                press(caster, "guard", InputMethod.HOLD, GUARD);
                primer = actor(false, 0);
                place(primer, caster.origin.add(0, 0, 1.5), 180);
            }
            if (!empty() && kind != Kind.BLOCK) {
                target = boat() ? new ObservedBoat(this) : cart() ? new ObservedCart(this) : new ObservedCow(this);
                targets.add(target);
                target.setNoGravity(true); target.noPhysics = true;
                target.moveTo(caster.origin.x, caster.origin.y, caster.origin.z + 1.5, 0, 0);
                if (target instanceof Cow cow) {
                    cow.setNoAi(true); cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200);
                    cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                }
                target.setInvulnerable(nativeRefused());
                premise(level.addFreshEntity(target), "actual target added to level");
                premise(target.isPickable() && (!(target instanceof Boat boat) || !boat.isVehicle()), "pickable empty native target");
            }
            if (kind == Kind.BLOCK) {
                BlockPos pos = BlockPos.containing(caster.origin.add(0, 1, 1.5));
                blocks.put(pos, level.getBlockState(pos)); level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            }
            if (kind == Kind.IMMUNE) {
                seed = actor(false, 0);
                place(seed, caster.origin.add(0.4, 0, -0.1), 0);
            }
        }

        GravityTestSupport.Actor actor(boolean resolve, int duration) {
            GravityTestSupport.Actor actor = new GravityTestSupport.Actor(suite.helper, false, false, resolve, duration);
            actors.add(actor);
            originalTeams.put(actor, actor.user.getTeam());
            actor.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            return actor;
        }

        void place(GravityTestSupport.Actor actor, Vec3 pos, float yaw) {
            actor.user.moveTo(pos.x, pos.y, pos.z, yaw, 0); actor.user.setYHeadRot(yaw);
            actor.stand.moveTo(pos.x, pos.y, pos.z, yaw, 0); actor.stand.setYHeadRot(yaw);
        }

        void tick() {
            if (pressed) {
                if (charged() && !releasedCharge && ++heldTicks >= 3) {
                    premise(action.getPhase() == ActionPhase.BUTTON_CHARGE, "charged control natural HOLD before release");
                    AbilityInput.keyRelease(PUNCH, caster.user); releasedCharge = true;
                }
                return;
            }
            if (caster.user.tickCount < 2 || caster.stand.tickCount < 2) return;
            if (seed != null && !seedReturned) {
                if (!seedStarted) {
                    aim(seed, new ActionTarget(target));
                    press(seed, CMoonAddon.HEAVY_PUNCH, InputMethod.CLICK, SEED);
                    seedStarted = true;
                }
                return;
            }
            if (seed != null) premise(seedSucceeded && StandEntity.isStandAttackInvulnerable((LivingEntity) target), "actual registered heavy seeded live raw Stand immunity");
            if (counterCase()) {
                if (!priming) {
                    premise(caster.stand.isStandBlocking() && caster.stand.canBlockFromAngle(primer.stand.position()), "actual front-facing held guard");
                    aim(primer, new ActionTarget(caster.stand));
                    press(primer, CMoonAddon.PUNCH, InputMethod.CLICK, SEED);
                    priming = true;
                    return;
                }
                if (primeIncoming == 0 || caster.stand.guardCounter() <= 0) return;
                premise(caster.stand.isStandBlocking() && caster.user.isAlive(), "guard remains held until real replacement");
                // pressed at nothing, or at a boat or cart it does not keep, the manually controlled Stand aims from its
                // own eyes (1.16 aimWithThisOrUser), where the primer stands; a miss or that target needs it out of that aim
                if (empty() || boat() || cart()) place(primer, caster.origin.add(0, 0, -3), 0);
            }
            startPunch();
        }

        void startPunch() {
            suite.multiplier.set(multiplierZero() ? 0D : 1D);
            premise(caster.stand.getAttackDamage() == 0, "canonical default CM strength0; punch still uses positive raw1");
            initialMeter = caster.stand.getFinisherMeter(); initialSpeed = caster.stand.getAttackSpeed();
            initialStamina = caster.power.getStamina();
            standLastBefore = caster.stand.getLastHurtMob(); userLastBefore = caster.user.getLastHurtMob();
            counter = caster.stand.guardCounter();
            premise(counterCase() ? counter > 0 : counter == 0, "actual guard counter=" + counter);
            if (kind == Kind.EXPIRE) premise(caster.user.hasEffect(ModStatusEffects.RESOLVE)
                    && caster.power.getStamina() < caster.power.getMaxStamina() * 0.5F && initialSpeed == 11,
                    "real short Resolve, low finite budget and full entry getter before WINDUP");
            if (kind == Kind.FAST_SKIP) premise(initialSpeed == 24 && CMoonAddon.C_MOON.get().getStandStats().power() == 0,
                    "configured full speed24 survived real grant/summon and natural refresh with strength0");
            if (kind == Kind.FRIENDLY) {
                String name = "cmp" + caster.user.getId();
                if (level.getScoreboard().getPlayerTeam(name) != null)
                    throw new IllegalStateException("CM punch test team already exists: " + name);
                team = level.getScoreboard().addPlayerTeam(name);
                teamOwned = true;
                team.setAllowFriendlyFire(false);
                level.getScoreboard().addPlayerToTeam(caster.user.getScoreboardName(), team);
                level.getScoreboard().addPlayerToTeam(target.getScoreboardName(), team);
                premise(caster.user.getTeam() == team && target.getTeam() == team
                        && (primer == null || primer.user.getTeam() == team), "actual shared mock scoreboard-name effects, after native guard prime");
                log("TEAM before=" + originalTeams + " sharedScoreboardName=" + caster.user.getScoreboardName()
                        + " caster=" + caster.user.getTeam().getName() + " primer=" + (primer == null ? "none" : primer.user.getTeam().getName()));
            }
            if (grab()) {
                premise(caster.power.getAbility("grab_punch") == null, "actual CM has no grab-punch variation; no invented registration");
                caster.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget((LivingEntity) target);
                Ability base = caster.power.getAbility(CMoonAddon.PUNCH);
                var variation = caster.power.updateAvailableMoves().getContextVariationContainer(base);
                boolean admitted = variation != null && AbilityInput.withConditionCheck(variation, caster.user, InputMethod.CLICK);
                EntityActionInstance previous = caster.stand.getCurStandAction();
                BufferingState buffer = BufferingState.clickOnly();
                AbilityInput.keyPress(PUNCH, base, caster.user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
                verifyGrabRefusal(admitted, buffer.isActionSuccess, previous);
                done = true;
                return;
            }
            if (charged()) {
                var unlocked = UNLOCKS.forceUnlock(caster.power, "heavy_charged");
                premise(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                        "public registered charged skill unlocked");
            }
            ActionTarget aimed = empty() ? ActionTarget.EMPTY : kind == Kind.BLOCK
                    ? new ActionTarget(BlockPos.containing(caster.origin.add(0, 1, 1.5)), Direction.NORTH)
                    : new ActionTarget(target);
            aim(caster, aimed);
            if (boat() || cart()) {
                // 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget) and the strike aimed
                // again with the precision ray, from the user's eyes or the manually controlled Stand's: it looks at it
                Entity aiming = caster.stand.isManuallyControlled() ? caster.stand : caster.user;
                aiming.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            }
            permission = target == null || caster.stand.canAttackEntity(target);
            if (kind == Kind.FRIENDLY) premise(!permission, "actual team permission veto");
            if (target != null) {
                target.setDeltaMovement(INITIAL);
                if (target instanceof LivingEntity living) living.setOnGround(true);
            }
            healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0;
            damageBefore = entityDamage();
            tracking = true; pressed = true;
            EntityActionInstance instance = press(caster, charged() ? "heavy_charged" : CMoonAddon.PUNCH,
                    charged() ? InputMethod.HOLD : InputMethod.CLICK, PUNCH);
            premise(instance != null && (grab() ? instance.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB
                    && ((Ability) instance.ability).name().equals("grab_punch") : charged()
                    ? instance.ability == caster.power.getAbility("heavy_charged")
                    : instance.getClass() == CMoonPunchAbility.CMoonPunch.class && instance.ability == caster.power.getAbility(CMoonAddon.PUNCH)),
                    "actual registered ordinary/grab/charged factory and context");
            action = instance;
            GravityTestSupport.identity(suite.helper, action.getClass());
            initialW = action.phasesLength.getFloat(ActionPhase.WINDUP);
            initialP = action.phasesLength.getFloat(ActionPhase.PERFORM);
            initialR = action.phasesLength.getFloat(ActionPhase.RECOVERY);
            premise(charged() || near(initialP, StandStatFormulas.getLightAttackRecovery(initialSpeed, initialMeter))
                    && near(initialR, initialP * (caster.stand.isArmsOnlyMode() ? 2 : 4)), "initial configuration caches source formula");
            log("READY " + suite.facet + "/" + kind + " target=" + (target == null ? "none" : target.getClass().getName())
                    + " rawStrength=" + caster.stand.getAttackDamage() + " M=" + suite.multiplier.get() + " K=" + counter
                    + " speed=" + initialSpeed + " meter=" + initialMeter + " W/P/R=" + initialW + "/" + initialP + "/" + initialR);
        }

        EntityActionInstance press(GravityTestSupport.Actor actor, String name, InputMethod method, short key) {
            Ability base = actor.power.getAbility(name);
            premise(base != null, "registered ability " + name);
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, method), "real input condition " + name);
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, actor.user, null, method, 0, buffer, base.getAbilityId());
            premise(buffer.isActionSuccess, "registered input succeeded " + name);
            return held != null && held.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }

        void aim(GravityTestSupport.Actor actor, ActionTarget target) {
            LivingComponentAction.getComponent(actor.user).entityAim.setTarget(target);
            LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(target);
        }

        void pre(Entity entity) {
            if (caster != null && entity == caster.user) {
                naturalUser &= previousUserTick < 0 || caster.user.tickCount == previousUserTick + 1;
                previousUserTick = caster.user.tickCount;
            }
            if (caster == null || entity != caster.stand) return;
            naturalStand &= previousStandTick < 0 || caster.stand.tickCount == previousStandTick + 1;
            previousStandTick = caster.stand.tickCount;
            if (action == null || impact) return;
            preImmune = target instanceof LivingEntity living && StandEntity.isStandAttackInvulnerable(living);
            if (target != null) {
                target.setDeltaMovement(INITIAL);
                if (target instanceof LivingEntity living) living.setOnGround(true);
            }
        }

        void post(Entity entity) {
            if (caster == null || entity != caster.stand || action == null || done) return;
            if (!caster.user.hasEffect(ModStatusEffects.RESOLVE)) resolveExpired = true;
            ActionPhase phase = action.getPhase();
            if (phase != null) {
                float length = action.phasesLength.getFloat(phase);
                PhaseSample sample = new PhaseSample(level.getGameTime(), phase, action.getPhaseTick(), length,
                        caster.stand.getAttackSpeed(), caster.stand.getFinisherMeter(), caster.stand.isArmsOnlyMode(),
                        caster.user.tickCount, caster.stand.tickCount);
                timeline.add(sample);
                if (phase == previousPhase) samePhaseFrozen &= near(length, previousLength);
                previousPhase = phase; previousLength = length;
                if (phase == ActionPhase.PERFORM && perform == null) perform = sample;
                if (phase == ActionPhase.RECOVERY && recovery == null) recovery = sample;
            }
            if (!impact && action.punchedTarget != null) {
                impact = true; tracking = false;
                afterImpact = target != null ? target.getDeltaMovement() : Vec3.ZERO;
                healthAfter = target instanceof LivingEntity living ? living.getHealth() : 0;
                damageAfter = entityDamage();
                if (suite.facet != Facet.PHASE) { verify(); done = true; return; }
            }
            if (suite.facet == Facet.PHASE && impact && action.isOver()) { verify(); done = true; }
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (primer != null && event.getEntity() == caster.stand && event.getSource().getDirectEntity() == primer.stand && event.getAmount() > 0)
                primeIncoming++;
            if (!tracking || event.getEntity() != target || event.getSource().getDirectEntity() != caster.stand) return;
            incomingCalls++;
            if (cancelled()) event.setCanceled(true);
        }

        void knock(LivingKnockBackEvent event) {
            if (tracking && event.getEntity() == target) pulses.add(new Pulse(++order,
                    DamageSourceModified.currentKnockbackSource(event.getEntity()), event.getStrength(),
                    event.getRatioX(), event.getRatioZ(), target.getDeltaMovement()));
        }

        void beginHurt(Entity entity, DamageSource source, float amount) {
            if (!tracking || entity != target || source.getDirectEntity() != caster.stand) return;
            hurtCalls++; ++order; beforeHurt = entity.getDeltaMovement(); standAtHurt = caster.stand.position();
            counterAtHurt = caster.stand.guardCounter(); passedAmount = amount;
            grounded = entity instanceof LivingEntity living && living.onGround();
            EntityActionInstance current = caster.stand.getCurStandAction();
            if (perform == null && current != null && current.getPhase() == ActionPhase.PERFORM) {
                perform = new PhaseSample(level.getGameTime(), current.getPhase(), current.getPhaseTick(),
                        current.phasesLength.getFloat(ActionPhase.PERFORM), caster.stand.getAttackSpeed(),
                        caster.stand.getFinisherMeter(), caster.stand.isArmsOnlyMode(), caster.user.tickCount, caster.stand.tickCount);
            }
            if (source instanceof DamageSourceModified modified) {
                sourceAdd = modified.jojo_ripples$addKnockback(); sourceMultiply = modified.jojo_ripples$knockbackMultiplier();
            }
        }

        void endHurt(Entity entity, DamageSource source, boolean result) {
            if (seed != null && entity == target && source.getDirectEntity() == seed.stand) { seedReturned = true; seedSucceeded = result; }
            if (!tracking || entity != target || source.getDirectEntity() != caster.stand) return;
            hurtResult = result; returnOrder = ++order; afterHurt = entity.getDeltaMovement();
        }

        float entityDamage() { return target instanceof Boat boat ? boat.getDamage() : target instanceof Minecart cart ? cart.getDamage() : 0; }

        void verify() {
            if (charged()) { verifyCharged(); return; }
            boolean delegated = mainEntity() && kind != Kind.IMMUNE && !grab();
            List<Pulse> nativePulses = pulses.stream().filter(p -> p.source != null).toList();
            List<Pulse> detached = pulses.stream().filter(p -> p.source == null).toList();
            boolean actualEntity = action.punchedTarget.getType() == ActionTarget.TargetType.ENTITY;
            float expectedMeter = Math.min(1,
                    initialMeter + (actualEntity ? 0.2F : 0.1F) * (kind == Kind.RESOLVE ? 2 : 1));
            double half = counterAtHurt * 0.5;
            int extraCount = delegated && target instanceof LivingEntity && counter > 0 ? (hurtResult ? 2 : 1) : 0;
            Vec3 expected = beforeHurt != null ? beforeHurt : INITIAL;
            Vec3 outward = standAtHurt != null && target != null ? target.position().subtract(standAtHurt).multiply(1, 0, 1).normalize() : Vec3.ZERO;
            boolean pulseShape = detached.size() == extraCount && detached.stream().allMatch(p -> near(p.strength, half)
                    && p.order > returnOrder && near(new Vec3(-p.x, 0, -p.z).normalize(), outward))
                    && nativePulses.stream().allMatch(p -> near(p.strength, 0.4));
            if (target instanceof LivingEntity) {
                for (Pulse pulse : nativePulses) expected = vanillaPush(expected, pulse.strength, new Vec3(-pulse.x, 0, -pulse.z).normalize(), grounded);
                for (int i = 0; i < extraCount; i++) expected = vanillaPush(expected, half, outward, grounded);
            } else if (delegated && counter > 0) expected = outward.scale(half);
            else if (afterHurt != null) expected = afterHurt;
            boolean velocity = !delegated || afterImpact != null && near(afterImpact, expected);
            int pOracle = perform != null ? StandStatFormulas.getLightAttackRecovery(perform.speed, initialMeter) : -1;
            int rOracle = recovery != null ? StandStatFormulas.getLightAttackRecovery(recovery.speed, recovery.meter) * (recovery.arms ? 2 : 4) : -1;
            boolean phase = suite.facet == Facet.PHASE;
            boolean recordsLast = target instanceof LivingEntity && hurtResult && !caster.stand.isManuallyControlled();
            boolean bookkeeping = caster.stand.getLastHurtMob() == (recordsLast ? target : standLastBefore)
                    && caster.user.getLastHurtMob() == (recordsLast ? target : userLastBefore);
            // Sixteen outcome checks per case; observers never add per-tick assertions to this count.
            suite.check(naturalUser && naturalStand && caster.user.tickCount >= 2 && caster.stand.tickCount >= 2, "natural user/Stand entity ticks");
            suite.check((grab() ? action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB && action.getClass() != CMoonPunchAbility.CMoonPunch.class
                    : action.getClass() == CMoonPunchAbility.CMoonPunch.class && action.ability == caster.power.getAbility(CMoonAddon.PUNCH))
                    && (!(target instanceof LivingEntity) || bookkeeping), "registered factory/member and shared last-hurt bookkeeping");
            suite.check(caster.stand.getAttackDamage() == 0 && CMoonPunchAbility.CANONICAL_DAMAGE == 1, "canonical strength0/fixed positive raw1");
            suite.check(actualEntity == mainEntity() && (!actualEntity || action.punchedTarget.getMainEntity() == target), "actual resolved entity versus empty/block/permission boundary");
            suite.check(hurtCalls == (delegated ? 1 : 0), "exact delegated native hurt count=" + hurtCalls);
            suite.check(!delegated || near(passedAmount, multiplierZero() ? 0 : 1), "actual native amount after global M=" + passedAmount);
            suite.check(!delegated || hurtResult == !(cancelled() || nativeRefused()), "actual native hurt return=" + hurtResult);
            suite.check(phase || suite.facet == Facet.ENTITY || near(caster.stand.getFinisherMeter(), expectedMeter), "base plus unconditional entity attempt meter=" + caster.stand.getFinisherMeter());
            suite.check(!multiplierZero() || near(healthAfter, healthBefore) && near(damageAfter, damageBefore) && hurtResult, "M0 actual hurttrue/no HP or nonliving damage");
            suite.check(!delegated || target instanceof LivingEntity || near(damageAfter - damageBefore, multiplierZero() || nativeRefused() ? 0 : 10), "native empty boat/cart damage field");
            suite.check(suite.facet != Facet.COUNTER || !delegated || near(sourceAdd, 0) && near(sourceMultiply, 1), "counter detached from native source metadata");
            suite.check(suite.facet != Facet.COUNTER || pulseShape && velocity && nativePulses.size() <= 1, "detached halves/order/composed velocity and admitted tail");
            suite.check(kind != Kind.IMMUNE || seedSucceeded && preImmune && hurtCalls == 0 && detached.isEmpty(), "real prior registered heavy/raw immunity veto, distinct from M0");
            suite.check(!phase || perform != null && near(perform.length, pOracle), "later PERFORM entry length actual=" + (perform == null ? -1 : perform.length) + " expected=" + pOracle);
            suite.check(!phase || recovery != null && near(recovery.length, rOracle), "later RECOVERY entry length actual=" + (recovery == null ? -1 : recovery.length) + " expected=" + rOracle);
            suite.check(grab() ? StandStatFormulas.getLightAttackDamage(caster.stand.getAttackDamage()) == 0 && hurtCalls == 0 && pulses.isEmpty()
                    : !phase || samePhaseFrozen && near(action.phasesLength.getFloat(ActionPhase.WINDUP), initialW)
                    && (kind != Kind.EXPIRE || resolveExpired && perform != null && perform.speed != initialSpeed)
                    && (kind != Kind.FAST_SKIP || initialW == 0), "startup retained, live getter witness/zero skip/within-phase freeze");
            log("IMPACT " + suite.facet + "/" + kind + " target=" + action.punchedTarget.getType() + " hurtCalls=" + hurtCalls
                    + " hurt=" + hurtResult + " amount=" + passedAmount + " K=" + counter + " sourceAdd=" + sourceAdd
                    + " nativePulses=" + nativePulses + " detached=" + detached + " before=" + beforeHurt + " afterHurt=" + afterHurt
                    + " after=" + afterImpact + " HP=" + healthBefore + "->" + healthAfter + " entityDamage=" + damageBefore + "->" + damageAfter
                    + " meter=" + caster.stand.getFinisherMeter() + " expectedMeter=" + expectedMeter
                    + " bookkeeping=" + bookkeeping + " standLast=" + caster.stand.getLastHurtMob() + " userLast=" + caster.user.getLastHurtMob() + " phases=" + timeline);
        }

        void verifyCharged() {
            List<Pulse> nativePulses = pulses.stream().filter(p -> p.source != null).toList();
            suite.check(naturalUser && naturalStand, "charged natural user/Stand ticks");
            suite.check(action.ability == caster.power.getAbility("heavy_charged"), "actual registered charged member");
            suite.check(action.getClass() != CMoonPunchAbility.CMoonPunch.class, "charged factory excluded from CM light hooks");
            suite.check(releasedCharge && heldTicks >= 3, "natural HOLD then public key release");
            suite.check(perform != null && recovery != null, "charged natural later entries observed");
            suite.check(perform != null && near(perform.length, initialP), "charged original PERFORM cache retained");
            suite.check(recovery != null && near(recovery.length, initialR), "charged original RECOVERY cache retained");
            suite.check(initialR == 12, "charged default recovery12, not light formula");
            suite.check(hurtCalls == 1 && hurtResult, "charged one delegated real hurttrue");
            suite.check(near(passedAmount, StandStatFormulas.getChargedHeavyAttackDamage(caster.stand.getAttackDamage())), "charged SDK source strength0 damage1.5 retained");
            suite.check(near(sourceAdd, 2.5) && near(sourceMultiply, 1), "charged native source metadata retained");
            suite.check(nativePulses.size() == 1 && pulses.size() == 1, "charged native-only push, no CM counter tail");
            suite.check(near(initialStamina - caster.power.getStamina(), 100), "charged actual natural100 debit retained");
            suite.check(caster.stand.getFinisherMeter() == 0, "charged consumption retained");
            suite.check(samePhaseFrozen && action.isOver(), "charged phase freeze and natural end");
            suite.check(caster.stand.getAttackDamage() == 0, "charged controlled role still canonical CM strength0");
            log("CHARGED role=" + action.getClass().getName() + " amount=" + passedAmount + " P/R=" + initialP + "/" + initialR
                    + " debit=" + (initialStamina - caster.power.getStamina()) + " phases=" + timeline);
        }

        void verifyGrabRefusal(boolean admitted, boolean accepted, EntityActionInstance previous) {
            suite.check(naturalUser && naturalStand, "controlled grab state uses naturally ticked actual actors");
            suite.check(caster.power.getAbility("grab_punch") == null, "actual CM grab variant absent");
            suite.check(previous != null && ((Ability) previous.ability).name().equals("guard"), "actual held guard before refused CLICK");
            suite.check(counter > 0 && primeIncoming > 0, "real positive guard counter retained, not injected");
            suite.check(!admitted, "actual grabbed-context condition refuses ordinary punch");
            suite.check(!accepted, "registered keyPress refuses actual input");
            suite.check(caster.stand.getCurStandAction() == previous, "no CM punch factory installed");
            suite.check(caster.stand.isStandBlocking(), "prior guard remains active");
            suite.check(hurtCalls == 0 && incomingCalls == 0, "no attempted native target transport");
            suite.check(pulses.isEmpty(), "no native/detached target push");
            suite.check(caster.stand.getFinisherMeter() == initialMeter, "no finisher admission side effect");
            suite.check(caster.power.getStamina() == initialStamina, "no punch payment on refused input");
            suite.check(caster.stand.getAttackDamage() == 0, "canonical default CM strength0 retained");
            suite.check(target != null && target.isAlive(), "real live controlled grab target");
            suite.check(LivingComponentGrab.getEntityGrabbedBy(caster.stand) == target, "public owned grab link observed");
            suite.check(caster.stand.getCurStandAction().getClass() != CMoonPunchAbility.CMoonPunch.class, "grab context excluded from new normal-light hooks");
            log("GRAB-REFUSAL variantAbsent=true controlledPublicComponent=true naturalGrabAcquisitionUNRUN=true literalRaw0TransportUNRUN=true");
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, "CM-PUNCH PREMISE " + suite.facet + "/" + kind + " " + detail); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            Throwable failure = null;
            for (GravityTestSupport.Actor actor : actors) for (short key : new short[] {PUNCH, GUARD, CONTROL, SEED})
                try { AbilityInput.keyRelease(key, actor.user); } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            for (Entity entity : targets) try { entity.discard(); } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                if (teamOwned && level.getScoreboard().getPlayerTeam(team.getName()) == team)
                    level.getScoreboard().removePlayerTeam(team);
                teamOwned = false;
            }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                for (var entry : originalTeams.entrySet()) {
                    PlayerTeam original = entry.getValue();
                    if (original != null) level.getScoreboard().addPlayerToTeam(entry.getKey().user.getScoreboardName(), original);
                }
                teamRestored = originalTeams.entrySet().stream().allMatch(e -> e.getKey().user.getTeam() == e.getValue());
                premise(teamRestored, "actual original per-actor scoreboard teams restored");
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            for (var entry : blocks.entrySet()) try { level.setBlockAndUpdate(entry.getKey(), entry.getValue()); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            for (GravityTestSupport.Actor actor : actors) try { actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            for (int i = actors.size() - 1; i >= 0; i--) try { actors.get(i).close(); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                if (originalSpeed != null) {
                    StandStats stats = CMoonAddon.C_MOON.get().getStandStats(); stats.applyConfig(originalSpeed);
                    premise(stats.getValue(StandStats.StatWithValue.SPEED) == originalSpeed.get("speed").getAsDouble()
                            && stats.getValue(StandStats.StatWithValue.SPEED_MAX) == originalSpeed.get("speedMax").getAsDouble()
                            && stats.power() == 0, "actual original speed/baseMax restored; strength0 unchanged");
                    log("CONFIG-SPEED-RESTORED actual=" + stats.speed() + "/" + stats.speed(1) + " original=" + originalSpeed);
                }
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            log("SCENE-CLEANUP " + suite.facet + "/" + kind + " targetsRemoved=" + targets.stream().allMatch(Entity::isRemoved)
                    + " usersRemoved=" + actors.stream().allMatch(a -> a.user.isRemoved() && a.stand.isRemoved()) + " teamsRestored=" + teamRestored);
            rethrow(failure);
        }
    }

    private static final class ObservedCow extends Cow {
        final Scene scene;
        ObservedCow(Scene scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            scene.beginHurt(this, source, amount); boolean result = super.hurt(source, amount); scene.endHurt(this, source, result); return result;
        }
    }
    private static final class ObservedBoat extends Boat {
        final Scene scene;
        ObservedBoat(Scene scene) { super(EntityType.BOAT, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            scene.beginHurt(this, source, amount); boolean result = super.hurt(source, amount); scene.endHurt(this, source, result); return result;
        }
    }
    private static final class ObservedCart extends Minecart {
        final Scene scene;
        ObservedCart(Scene scene) { super(EntityType.MINECART, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            scene.beginHurt(this, source, amount); boolean result = super.hurt(source, amount); scene.endHurt(this, source, result); return result;
        }
    }
    private static Vec3 vanillaPush(Vec3 before, double strength, Vec3 outward, boolean grounded) {
        return strength <= 0 ? before : new Vec3(before.x * 0.5 + outward.x * strength,
                grounded ? Math.min(0.4, before.y * 0.5 + strength) : before.y, before.z * 0.5 + outward.z * strength);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.0002; }
    private static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 0.0002; }
    private static Throwable preserve(Throwable original, Throwable next) { if (original == null) return next; if (next != original) original.addSuppressed(next); return original; }
    private static void rethrow(Throwable failure) { if (failure instanceof RuntimeException error) throw error; if (failure instanceof Error error) throw error; }
    private static void log(String value) { rotp.core.core.JojoMod.getLogger().info("CM-PUNCH " + value); }
}
