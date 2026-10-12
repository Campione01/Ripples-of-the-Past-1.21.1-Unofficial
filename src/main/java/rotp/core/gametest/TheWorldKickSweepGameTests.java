package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

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
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheWorldKickSweepGameTests {
    private static final short ATTACK = 125;
    private static final short DEFEND = 126;
    private static final short CONTROL = 127;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(TheWorldKickSweepGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static Suite active;
    private enum Kind { LIVING, BOAT, SHIELD_PRIMARY, REFUSED_PRIMARY, IMMUNE_PRIMARY,
        BEYOND_REACH, LEGACY_EDGE, UNPICKABLE, SPECTATOR, FRIENDLY, GUARD_SECONDARY, REFUSED_SECONDARY,
        IMMUNE_SECONDARY, IMMUNE_GUARD_PRIMARY, SHIELD_FRONT_SECONDARY, SHIELD_REAR_SECONDARY,
        BOOKKEEPING, GRAB }
    private record Spec(Kind kind, double multiplier) { String id() { return kind + "/M" + multiplier; } }
    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}

    private TheWorldKickSweepGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "the_world_kick_sweep")
    public static void registeredKickRestoresEntityPrimaryAndAdmittedSweep(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY production=" + sha(TheWorldKick.class) + " fixture=" + sha(TheWorldKickSweepGameTests.class));
            suite.listen(); helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "the_world_kick_sweep")
    public static void releaseKickSweepBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        List<Spec> result = new ArrayList<>();
        for (double m : new double[] {1, 2}) {
            for (Kind kind : new Kind[] {Kind.LIVING, Kind.BOAT, Kind.SHIELD_PRIMARY, Kind.REFUSED_PRIMARY,
                    Kind.IMMUNE_PRIMARY, Kind.GUARD_SECONDARY, Kind.SHIELD_FRONT_SECONDARY, Kind.SHIELD_REAR_SECONDARY})
                result.add(new Spec(kind, m));
        }
        for (Kind kind : new Kind[] {Kind.BEYOND_REACH, Kind.LEGACY_EDGE, Kind.UNPICKABLE, Kind.SPECTATOR, Kind.FRIENDLY,
                Kind.REFUSED_SECONDARY, Kind.IMMUNE_SECONDARY, Kind.IMMUNE_GUARD_PRIMARY, Kind.BOOKKEEPING, Kind.GRAB})
            result.add(new Spec(kind, 1));
        for (Kind kind : new Kind[] {Kind.LIVING, Kind.BOAT, Kind.GUARD_SECONDARY}) result.add(new Spec(kind, 0));
        result.add(new Spec(Kind.GUARD_SECONDARY, -1));
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
            helper.assertTrue(active == null, "Overlapping Kick sweep suites");
            this.helper = helper; oldMultiplier = multiplier.get();
            helper.testInfo.addListener(this); active = this;
        }

        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (scene != null && !closed) scene.incoming(event);
            };
            Consumer<EntityInvulnerabilityCheckEvent> probe = event -> {
                if (scene != null && !closed && scene.caster != null
                        && event.getSource().getDirectEntity() == scene.caster.stand)
                    scene.sources.putIfAbsent(event.getEntity(), event.getSource());
            };
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && scene.caster != null && event.getEntity() == scene.caster.stand) {
                    scene.primaryImmuneBeforeTick = scene.primary instanceof LivingEntity living && StandEntity.isStandAttackInvulnerable(living);
                    scene.secondaryImmuneBeforeTick = scene.secondary != null && StandEntity.isStandAttackInvulnerable(scene.secondary);
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed && eventFailure == null) {
                    try { scene.post(event.getEntity()); }
                    catch (RuntimeException error) { eventFailure = error; }
                }
            };
            listeners.add(incoming); listeners.add(probe); listeners.add(pre); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityInvulnerabilityCheckEvent.class, probe);
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
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close(); helper.assertTrue(failures.isEmpty(), "Kick primary/sweep failed; see SUMMARY"); helper.succeed(); return;
                    }
                    scene = new Scene(this, specs.get(index++)); scene.prepare();
                }
                scene.tick();
                helper.assertTrue(++scene.worldTicks < 40, "Native Kick scene did not finish " + scene.spec.id());
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
        final Map<Entity, DamageSource> sources = new IdentityHashMap<>();
        final Map<Entity, Float> amounts = new IdentityHashMap<>();
        Actor caster;
        Actor primer;
        Actor primaryActor;
        Actor secondaryActor;
        Entity primary;
        LivingEntity secondary;
        Cow sentinel;
        EntityActionInstance action;
        EntityActionInstance primeAction;
        EntityActionInstance primaryBarrage;
        EntityActionInstance secondaryBarrage;
        PlayerTeam friendlyTeam;
        Vec3 origin;
        float primaryHealthBefore;
        float secondaryHealthBefore;
        boolean primaryGuardAtIncoming;
        boolean secondaryGuardAtIncoming;
        boolean primaryImmuneBeforeTick;
        boolean secondaryImmuneBeforeTick;
        int worldTicks;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.helper.getLevel(); }

        void prepare() {
            suite.multiplier.set(spec.multiplier);
            premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == spec.multiplier, "actual server multiplier");
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            caster = actor("the_world", origin, origin, 28, false);
            sentinel = cow(origin.add(-3, 0.4, -3), false);
            caster.stand.setLastHurtMob(sentinel); caster.user.setLastHurtMob(sentinel);
            if (spec.kind == Kind.BOAT) {
                Boat boat = EntityType.BOAT.create(level); premise(boat != null, "vanilla boat created");
                boat.setNoGravity(true); boat.noPhysics = true; boat.moveTo(origin.x, origin.y, origin.z + 1.5, 0, 0);
                premise(level.addFreshEntity(boat), "vanilla unoccupied pickable boat added"); loose.add(boat); primary = boat;
                premise(boat.isPickable() && boat.getControllingPassenger() == null, "vanilla nonliving primary admission");
            }
            else if (spec.kind == Kind.SHIELD_PRIMARY) primary = shield(origin.add(0, 0, 1.5), 180);
            else if (spec.kind == Kind.IMMUNE_GUARD_PRIMARY || spec.kind == Kind.BOOKKEEPING) {
                primaryActor = actor("star_platinum", origin.add(4, 0, 0), origin.add(0, 0, 1.5), 14, true);
                primary = primaryActor.stand;
                if (spec.kind == Kind.BOOKKEEPING) primaryBarrage = press(primaryActor, "barrage", InputMethod.HOLD, DEFEND);
                else press(primaryActor, "guard", InputMethod.HOLD, DEFEND);
            }
            else primary = cow(origin.add(0, 0.4, spec.kind == Kind.BEYOND_REACH || spec.kind == Kind.LEGACY_EDGE ? 2.6 : 1.5), false);
            if (spec.kind == Kind.GUARD_SECONDARY || spec.kind == Kind.REFUSED_SECONDARY || spec.kind == Kind.IMMUNE_SECONDARY
                    || spec.kind == Kind.BOOKKEEPING) {
                secondaryActor = actor("star_platinum", origin.add(4, 0, 3), origin.add(0.6, 0, 1.7), 14, true);
                secondary = secondaryActor.stand;
                if (spec.kind == Kind.BOOKKEEPING) secondaryBarrage = press(secondaryActor, "barrage", InputMethod.HOLD, DEFEND);
                else {
                    press(secondaryActor, "guard", InputMethod.HOLD, DEFEND);
                    premise(secondaryActor.stand.isStandBlocking() && secondaryActor.stand.canBlockFromAngle(caster.stand.position()), "real secondary front-facing held guard");
                }
            }
            else if (spec.kind == Kind.SHIELD_FRONT_SECONDARY || spec.kind == Kind.SHIELD_REAR_SECONDARY)
                secondary = shield(origin.add(0.8, 0, 1.5), spec.kind == Kind.SHIELD_FRONT_SECONDARY ? 180 : 0);
            else if (spec.kind == Kind.SPECTATOR) secondary = player(origin.add(0.8, 0, 1.5), 0, GameType.SPECTATOR);
            else if (spec.kind == Kind.FRIENDLY) {
                secondary = player(origin.add(0.8, 0, 1.5), 0, GameType.SURVIVAL);
                friendlyTeam = level.getScoreboard().addPlayerTeam("kick_fixture_" + caster.user.getId());
                friendlyTeam.setAllowFriendlyFire(false);
                level.getScoreboard().addPlayerToTeam(caster.user.getScoreboardName(), friendlyTeam);
                level.getScoreboard().addPlayerToTeam(secondary.getScoreboardName(), friendlyTeam);
                premise(!caster.stand.canAttackEntity(secondary), "real friendly-team harm refusal");
            }
            else secondary = cow(origin.add(spec.kind == Kind.BEYOND_REACH || spec.kind == Kind.LEGACY_EDGE ? 0 : 0.8, 0.4,
                    spec.kind == Kind.BEYOND_REACH ? 3.5 : spec.kind == Kind.LEGACY_EDGE ? 3.15 : 1.5), spec.kind == Kind.UNPICKABLE);
            premise(primary.getBoundingBox().inflate(0.5, 0, 0.5).intersects(secondary.getBoundingBox()), "secondary inside donor inflated primary bounds");
            if (spec.kind == Kind.LEGACY_EDGE) premise(!HitResultUtil.isTargetWithinRange(new ActionTarget(secondary), caster.stand, level,
                    caster.stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), caster.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)),
                    "legacy sweep edge remains outside accepted primary nearest-box reach");
            if (spec.kind == Kind.UNPICKABLE) premise(!secondary.isPickable() && secondary.isAlive()
                    && caster.stand.canAttackEntity(secondary), "native unpickable but otherwise harm-admitted living control");
            if (spec.kind == Kind.SPECTATOR) premise(secondary.isSpectator(), "native spectator control");
            if (spec.kind == Kind.BOOKKEEPING) {
                primaryActor.user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                primaryActor.stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                secondaryActor.user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                secondaryActor.stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            }
            if (immune()) {
                primer = actor("star_platinum", origin.add(-0.5, 0, 0), origin.add(-0.5, 0, 0), 32, false);
                LivingEntity primed = (LivingEntity) (spec.kind == Kind.IMMUNE_SECONDARY ? secondary : primary);
                LivingComponentAction.getComponent(primer.stand).entityAim.setTarget(new ActionTarget(primed));
                primeAction = press(primer, "heavy_punch", InputMethod.CLICK, ATTACK);
                premise(!(primeAction instanceof TheWorldKick), "genuine prior ordinary Stand hit");
            }
            log("START " + spec.id() + " primary=" + primary.getId() + " secondary=" + secondary.getId());
        }

        void tick() {
            if (action != null || primer != null) return;
            if (primary instanceof Player player && !player.isBlocking()) return;
            if ((spec.kind == Kind.SHIELD_FRONT_SECONDARY || spec.kind == Kind.SHIELD_REAR_SECONDARY)
                    && !secondary.isBlocking()) return;
            beginKick();
        }

        void beginKick() {
            unlock(caster, "kick"); caster.stand.setFinisherMeter(0.75F);
            if (spec.kind == Kind.GRAB) {
                unlock(caster, "grab_throw");
                caster.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget((LivingEntity) primary);
            }
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(new ActionTarget(primary));
            primaryHealthBefore = health(primary, primaryActor); secondaryHealthBefore = health(secondary, secondaryActor);
            action = press(caster, "heavy_punch", InputMethod.CLICK, ATTACK);
            premise(action instanceof TheWorldKick && (action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB) == (spec.kind == Kind.GRAB), "real finisher/grab Kick selection");
            premise(caster.stand.getAttackDamage() == 15 && caster.stand.getAttackSpeed() == 28, "resolved positive RAW15 and native cadence");
            if (immune()) premise(StandEntity.isStandAttackInvulnerable((LivingEntity) (spec.kind == Kind.IMMUNE_SECONDARY ? secondary : primary)), "real prior hit established live Stand immunity");
            log("PRESS " + spec.id() + " selected=" + ((Ability) action.ability).name() + " phases=" + action.phasesLength);
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (caster == null || event.getSource().getDirectEntity() != caster.stand) return;
            Entity target = event.getEntity();
            sources.putIfAbsent(target, event.getSource()); amounts.put(target, event.getAmount());
            if (target == primary && primaryActor != null) primaryGuardAtIncoming = primaryActor.stand.isStandBlocking();
            if (target == secondary && secondaryActor != null) secondaryGuardAtIncoming = secondaryActor.stand.isStandBlocking();
            if (spec.kind == Kind.REFUSED_PRIMARY && target == primary || spec.kind == Kind.REFUSED_SECONDARY && target == secondary)
                event.setCanceled(true);
            log("INCOMING " + spec.id() + " target=" + target.getId() + " amount=" + event.getAmount());
        }

        void post(Entity entity) {
            if (done || closed) return;
            if (primer != null && action == null && entity == primer.stand && primeAction.punchedTarget != null) beginKick();
            if (action != null && entity == caster.stand && action.punchedTarget != null) {
                premise(action.punchedTarget.getMainEntity() == primary, "native primary target identity");
                verify(); done = true;
            }
        }

        void verify() {
            if (primary instanceof Boat boat) {
                suite.check(near(boat.getDamage(), 150 * spec.multiplier) && boat.isRemoved() == (spec.multiplier > 0), "vanilla boat actual damage=" + boat.getDamage() + " removed=" + boat.isRemoved());
                suite.check(boat.getHurtTime() > 0 && sources.containsKey(boat), "nonliving actual hurt invoked even at M0");
                source(primary, "nonliving primary");
                log("OPEN-FACET B-S13-03 " + spec.id() + " nonlivingVelocity=" + boat.getDeltaMovement() + "; no extra/tail pulse closure");
            }
            else if (spec.kind == Kind.IMMUNE_PRIMARY || spec.kind == Kind.IMMUNE_GUARD_PRIMARY) {
                suite.check(primaryImmuneBeforeTick && StandEntity.isStandAttackInvulnerable((LivingEntity) primary)
                        && !amounts.containsKey(primary) && health(primary, primaryActor) == primaryHealthBefore, "real prior-hit raw immunity rejects primary");
                if (primaryActor != null) suite.check(primaryActor.stand.isStandBlocking(), "raw-rejected guarding primary is not broken");
            }
            else {
                suite.check(near(amounts.getOrDefault(primary, Float.NaN), 15 * spec.multiplier), "primary RAW15 M once amount=" + amounts.get(primary));
                if (spec.kind == Kind.REFUSED_PRIMARY || spec.kind == Kind.SHIELD_PRIMARY)
                    suite.check(health(primary, primaryActor) == primaryHealthBefore, "primary actual hurt refusal/block leaves health");
                else if (spec.multiplier > 0) suite.check(health(primary, primaryActor) < primaryHealthBefore, "living primary actual damage");
                source(primary, "living primary");
            }
            boolean excluded = spec.kind == Kind.BEYOND_REACH || spec.kind == Kind.UNPICKABLE || spec.kind == Kind.SPECTATOR || spec.kind == Kind.FRIENDLY;
            if (excluded) suite.check(!sources.containsKey(secondary) && !amounts.containsKey(secondary)
                    && health(secondary, secondaryActor) == secondaryHealthBefore, "reach/pickability/spectator/friendly secondary excluded");
            else if (spec.kind == Kind.IMMUNE_SECONDARY) {
                suite.check(secondaryImmuneBeforeTick && StandEntity.isStandAttackInvulnerable(secondary)
                        && !amounts.containsKey(secondary) && health(secondary, secondaryActor) == secondaryHealthBefore, "real secondary raw immunity rejected");
                suite.check(secondaryActor.stand.isStandBlocking(), "secondary raw rejection preserves guard");
            }
            else if (spec.multiplier <= 0 && secondaryActor != null) {
                suite.check(!secondaryActor.stand.isStandBlocking() && !secondaryActor.stand.canStartBlocking(), "positive secondary RAW breaks guard independent of M0/negative scaled hurt");
                suite.check(health(secondary, secondaryActor) == secondaryHealthBefore, "M0/negative secondary keeps health");
            }
            else {
                suite.check(near(amounts.getOrDefault(secondary, Float.NaN), 7.5 * spec.multiplier), "unconditional secondary half RAW M once amount=" + amounts.get(secondary));
                source(secondary, "secondary");
                if (spec.kind == Kind.REFUSED_SECONDARY || spec.kind == Kind.SHIELD_FRONT_SECONDARY || spec.multiplier == 0)
                    suite.check(health(secondary, secondaryActor) == secondaryHealthBefore, "refused/blocked/M0 secondary keeps health");
                else {
                    suite.check(health(secondary, secondaryActor) < secondaryHealthBefore, "admitted secondary actually hurt");
                    suite.check(StandEntity.isStandAttackInvulnerable(secondary), "secondary native Stand immunity10 established");
                }
                if (secondaryActor != null && spec.kind != Kind.BOOKKEEPING)
                    suite.check(!secondaryGuardAtIncoming && !secondaryActor.stand.isStandBlocking(), "admitted secondary guard breaks before actual hurt/refusal");
            }
            if (spec.kind == Kind.SHIELD_PRIMARY || spec.kind == Kind.SHIELD_FRONT_SECONDARY) {
                Player shield = (Player) (spec.kind == Kind.SHIELD_PRIMARY ? primary : secondary);
                suite.check(shield.isBlocking() && !shield.getCooldowns().isOnCooldown(Items.SHIELD), "blocked shield remains raised without success cooldown");
            }
            if (spec.kind == Kind.SHIELD_REAR_SECONDARY) {
                Player shield = (Player) secondary;
                suite.check(!shield.isUsingItem() && near(shield.getCooldowns().getCooldownPercent(Items.SHIELD, 0), 1), "successful secondary rear hit applies native100 shield cooldown");
            }
            boolean primaryFailed = spec.kind == Kind.SHIELD_PRIMARY || spec.kind == Kind.REFUSED_PRIMARY || spec.kind == Kind.IMMUNE_PRIMARY || spec.kind == Kind.IMMUNE_GUARD_PRIMARY;
            Entity expectedLast = primaryFailed ? sentinel : primary instanceof LivingEntity ? primary : null;
            if (spec.multiplier <= 0 && primary instanceof LivingEntity) {
                Entity observed = caster.stand.getLastHurtMob();
                suite.check(observed != secondary && observed == caster.user.getLastHurtMob()
                        && (observed == primary || observed == sentinel), "nonpositive M preserves native primary hurt-result bookkeeping, never secondary");
            }
            else suite.check(caster.stand.getLastHurtMob() == expectedLast && caster.user.getLastHurtMob() == expectedLast,
                    "secondary does not replace primary-only lastHurtMob expected=" + expectedLast);
            if (spec.kind == Kind.BOOKKEEPING) {
                suite.check(primaryBarrage.getPhase() == ActionPhase.RECOVERY && secondaryBarrage.getPhase() == ActionPhase.PERFORM, "only primary barrage gets heavy interruption");
                var primaryImpact = KnockbackCollisionImpact.getExistingHandler(primaryActor.user);
                var secondaryImpact = KnockbackCollisionImpact.getExistingHandler(secondaryActor.user);
                suite.check(primaryImpact != null && primaryImpact.isActive(), "native successful primary wall-impact armed");
                suite.check(secondaryActor.user.getDeltaMovement().lengthSqr() > 0 && (secondaryImpact == null || !secondaryImpact.isActive()), "damaged/moving secondary has no primary wall-impact registration");
            }
        }

        void source(Entity target, String label) {
            DamageSource source = sources.get(target);
            boolean properties = source instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() == caster.power
                    && modified.jojo_ripples$standInvulTicks() == 10 && near(modified.jojo_ripples$knockbackYRotDeg(), 60)
                    && near(modified.jojo_ripples$addKnockback(), 3.5) && modified.jojo_ripples$breaksStandGuard();
            suite.check(properties, label + " owning power/invul10/Y60/extra3.5/raw-admitted guard properties");
        }

        boolean immune() { return spec.kind == Kind.IMMUNE_PRIMARY || spec.kind == Kind.IMMUNE_GUARD_PRIMARY || spec.kind == Kind.IMMUNE_SECONDARY; }
        float health(Entity target, Actor actor) { return actor != null ? actor.user.getHealth() : target instanceof LivingEntity living ? living.getHealth() : 0; }

        EntityActionInstance press(Actor actor, String name, InputMethod method, short key) {
            Ability base = actor.power.getAbility(name); premise(base != null, "registered ability " + name);
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, method), "real input admission " + name);
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, actor.user, null, method, 0, buffer, base.getAbilityId());
            premise(buffer.isActionSuccess, "native input succeeded " + name);
            return held != null && held.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }

        Actor actor(String standName, Vec3 userPos, Vec3 standPos, double speed, boolean manual) {
            Player user = player(userPos, manual ? 180 : 0, GameType.SURVIVAL);
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED, "grant registered " + standName);
            power.setResolveLevel(2); premise(type.summon(user, power), "native full summon");
            StandEntity stand = power.getSummonedStandEntity(); premise(stand != null && stand.getUser() == user, "native owner binding");
            Actor actor = new Actor(user, power, type, stand); actors.add(actor);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(standPos.x, standPos.y, standPos.z, manual ? 180 : 0, 0); stand.setYHeadRot(manual ? 180 : 0);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user); power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            if (manual) {
                press(actor, "manual_control", InputMethod.CLICK, CONTROL);
                premise(stand.isManuallyControlled(), "native manual control excludes passive guard");
            }
            return actor;
        }

        Player player(Vec3 position, float yaw, GameType type) {
            Player player = GameTestPlayers.makeServerMockPlayer(suite.helper, type);
            player.getAbilities().invulnerable = false; player.setNoGravity(true); player.noPhysics = true;
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); player.setHealth(100);
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            player.moveTo(position.x, position.y, position.z, yaw, 0); player.setYHeadRot(yaw);
            premise(level.addFreshEntity(player), "native player added"); loose.add(player); return player;
        }

        Player shield(Vec3 position, float yaw) {
            Player player = player(position, yaw, GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); player.startUsingItem(InteractionHand.OFF_HAND); return player;
        }

        Cow cow(Vec3 position, boolean unpickable) {
            Cow cow = unpickable ? new Cow(EntityType.COW, level) { @Override public boolean isPickable() { return false; } }
                    : EntityType.COW.create(level);
            premise(cow != null, "native cow created");
            cow.setNoAi(true); cow.setNoGravity(true); cow.noPhysics = true;
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(cow), "cow native hurt target added"); loose.add(cow); return cow;
        }

        void unlock(Actor actor, String skill) {
            var result = UNLOCKS.forceUnlock(actor.power, skill);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public skill unlock " + skill);
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, spec.id() + " premise: " + detail); }

        @Override public void close() {
            if (closed) return; closed = true;
            for (Actor actor : actors) {
                AbilityInput.keyRelease(ATTACK, actor.user); AbilityInput.keyRelease(DEFEND, actor.user); AbilityInput.keyRelease(CONTROL, actor.user);
                actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
                if (actor.user.hasData(ModDataAttachmentTypes.CONTROLLER)) actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
                if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power);
            }
            for (Entity entity : loose) entity.discard();
            if (friendlyTeam != null) level.getScoreboard().removePlayerTeam(friendlyTeam);
        }
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.0001; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }
    private static void log(String text) { JojoMod.getLogger().info("D-MELEE-02-03 " + text); }
}
