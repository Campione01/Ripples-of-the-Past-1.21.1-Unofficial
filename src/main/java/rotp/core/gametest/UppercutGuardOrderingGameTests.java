package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility.Uppercut;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Serial scenes because the damage multiplier is a server-wide setting. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UppercutGuardOrderingGameTests {
    private static final short ATTACK_KEY = 121;
    private static final short GUARD_KEY = 122;
    private static final short CONTROL_KEY = 123;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(UppercutGuardOrderingGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static Suite active;
    private enum Kind { IDLE, GUARDED, HURT_REFUSED, ZERO_M, IMMUNITY, SHIELD_FRONT,
        SHIELD_REFUSED, SHIELD_REAR, ORDINARY_HEAVY, GRAB_UPPERCUT, GRAB_HEAVY }

    private UppercutGuardOrderingGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 700, batch = "uppercut_guard_ordering")
    public static void registeredUppercutBreaksGuardAfterRawAdmission(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY production=" + sha(Uppercut.class) + " fixture=" + sha(UppercutGuardOrderingGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "uppercut_guard_ordering")
    public static void releaseUppercutGuardBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        final double oldMultiplier;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException postFailure;
        int index;
        int checks;
        float idleLoss;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping uppercut guard suites");
            this.helper = helper;
            oldMultiplier = multiplier.get();
            helper.testInfo.addListener(this);
            active = this;
        }

        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (scene != null && !closed) scene.incoming(event);
            };
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && scene.caster != null && scene.target != null && event.getEntity() == scene.caster.stand)
                    scene.immunityBeforeTick = StandEntity.isStandAttackInvulnerable(scene.target);
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed && postFailure == null) {
                    try { scene.post(event.getEntity()); }
                    catch (RuntimeException error) { postFailure = error; }
                }
            };
            listeners.add(incoming); listeners.add(pre); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        void tick() {
            if (closed) return;
            try {
                if (postFailure != null) throw postFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == Kind.values().length) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "Uppercut guard ordering failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, Kind.values()[index++]);
                    scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < 140, "Native uppercut scene did not finish: " + scene.kind);
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.kind + " " + detail);
            if (!passed) failures.add(scene.kind + ": " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            if (scene != null) scene.close();
            multiplier.set(oldMultiplier);
            if (active == this) active = null;
            log("CLEANUP listeners=0 M=" + config().standDamageMultiplier.get());
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final ServerLevel level;
        final List<Actor> actors = new ArrayList<>();
        Actor caster;
        Actor defender;
        Actor primer;
        Player shieldPlayer;
        LivingEntity target;
        EntityActionInstance action;
        EntityActionInstance primeAction;
        float healthBefore;
        float incomingAmount;
        boolean guardAtIncoming;
        boolean immunityBeforeTick;
        boolean hitSeen;
        int incomingCalls;
        int primeCalls;
        int hitTargetAge;
        int hitCasterAge;
        int worldTicks;
        int cooldownNativeTicks;
        boolean done;
        boolean closed;

        Scene(Suite suite, Kind kind) { this.suite = suite; this.kind = kind; level = suite.helper.getLevel(); }

        void prepare() {
            suite.multiplier.set(kind == Kind.ZERO_M ? 0D : 1D);
            premise(config().standDamageMultiplier.get() == (kind == Kind.ZERO_M ? 0D : 1D), "actual server M premise");
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            caster = actor(origin, 0, 28);
            if (shield()) {
                shieldPlayer = player(origin.add(0, 0, 1.5), kind == Kind.SHIELD_FRONT ? 180 : 0);
                shieldPlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                shieldPlayer.startUsingItem(InteractionHand.OFF_HAND);
                target = shieldPlayer;
                // Let the ordinary use-item ticks establish the shield before pressing the attack.
            }
            else {
                defender = actor(origin.add(0, 0, 1.5), 180, 14);
                target = defender.stand;
                press(defender, "manual_control", InputMethod.CLICK, CONTROL_KEY);
                premise(defender.stand.isManuallyControlled(), "real manual control excludes passive auto-guard in both idle and guarding controls");
                if (kind != Kind.IDLE) {
                    press(defender, "guard", InputMethod.HOLD, GUARD_KEY);
                    premise(defender.stand.isStandBlocking() && defender.stand.canBlockFromAngle(caster.stand.position()),
                            "front-facing real held guard with stamina=" + defender.power.getStamina());
                }
                if (kind == Kind.IMMUNITY) {
                    primer = actor(origin.add(-0.5, 0, 0), 0, 32);
                    aim(primer);
                    primeAction = press(primer, "heavy_punch", InputMethod.CLICK, ATTACK_KEY);
                    premise(!(primeAction instanceof Uppercut), "prior native ordinary heavy has no uppercut opt-in");
                }
                else beginAttack();
            }
            log("START " + kind + " nativeTarget=" + target.getId() + " M=" + config().standDamageMultiplier.get());
        }

        void beginAttack() {
            if (!ordinary()) {
                unlock(caster, "uppercut");
                caster.stand.setFinisherMeter(0.75F);
            }
            if (grab()) {
                unlock(caster, "grab_throw");
                caster.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(target);
            }
            aim(caster);
            healthBefore = victimHealth();
            action = press(caster, "heavy_punch", InputMethod.CLICK, ATTACK_KEY);
            premise((action instanceof Uppercut) == !ordinary(), "registered finisher selection");
            premise((action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB) == grab(), "actual grab variation selection");
            premise(caster.stand.getAttackDamage() == 15 && caster.stand.getAttackSpeed() == 28,
                    "positive native RAW strength/speed");
            if (kind == Kind.IMMUNITY) premise(StandEntity.isStandAttackInvulnerable(target)
                    && defender.stand.isStandBlocking() && primeCalls > 0, "real prior Stand hit established live immunity without breaking guard");
            log("PRESS " + kind + " selected=" + ((Ability) action.ability).name() + " windup=" + action.phasesLength
                    + " guard=" + (defender != null && defender.stand.isStandBlocking()));
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() != target) return;
            Entity direct = event.getSource().getDirectEntity();
            if (primer != null && direct == primer.stand) { primeCalls++; return; }
            if (caster == null || direct != caster.stand) return;
            incomingCalls++;
            incomingAmount = event.getAmount();
            guardAtIncoming = defender != null && defender.stand.isStandBlocking();
            log("INCOMING " + kind + " amount=" + incomingAmount + " guard=" + guardAtIncoming);
            if (kind == Kind.HURT_REFUSED || kind == Kind.SHIELD_REFUSED) event.setCanceled(true);
        }

        void post(Entity entity) {
            if (done || closed) return;
            if (shield() && action == null && entity == shieldPlayer && shieldPlayer.isBlocking()) beginAttack();
            if (primer != null && action == null && entity == primer.stand && primeAction.punchedTarget != null) beginAttack();
            if (action != null && entity == caster.stand && !hitSeen && action.punchedTarget != null) {
                premise(action.punchedTarget.getMainEntity() == target, "native Uppercut/ordinary primary identity");
                hitSeen = true;
                hitTargetAge = target.tickCount;
                hitCasterAge = caster.stand.tickCount;
                verifyImpact();
                if (kind != Kind.SHIELD_REAR) done = true;
            }
            if (kind == Kind.SHIELD_REAR && hitSeen && entity == shieldPlayer) {
                int elapsed = ++cooldownNativeTicks;
                if (elapsed == 1 || elapsed == 99 || elapsed == 100) log("SHIELD-CLOCK nativePlayerTicks=" + elapsed
                        + " cooldown=" + shieldPlayer.getCooldowns().getCooldownPercent(Items.SHIELD, 0));
                if (elapsed == 99) suite.check(shieldPlayer.getCooldowns().isOnCooldown(Items.SHIELD), "real shield cooldown remains at tick99");
                if (elapsed == 100) {
                    suite.check(!shieldPlayer.getCooldowns().isOnCooldown(Items.SHIELD), "real shield cooldown ends at tick100");
                    done = true;
                }
            }
        }

        void verifyImpact() {
            float loss = healthBefore - victimHealth();
            if (shield()) {
                if (kind == Kind.SHIELD_REAR) {
                    suite.check(loss > 0 && incomingCalls == 1, "successful rear shield hit loss=" + loss);
                    suite.check(!shieldPlayer.isUsingItem() && shieldPlayer.getCooldowns().isOnCooldown(Items.SHIELD)
                            && near(shieldPlayer.getCooldowns().getCooldownPercent(Items.SHIELD, 0), 1), "success-only native100 shield cooldown");
                }
                else {
                    suite.check(loss == 0 && incomingCalls == 1, "front/refused shield hit has no health loss");
                    suite.check(shieldPlayer.isBlocking() && !shieldPlayer.getCooldowns().isOnCooldown(Items.SHIELD), "failed hurt keeps raised shield without cooldown");
                }
            }
            else if (kind == Kind.IMMUNITY) {
                suite.check(immunityBeforeTick && StandEntity.isStandAttackInvulnerable(target), "real immunity window active at native impact");
                suite.check(incomingCalls == 0 && loss == 0, "RAW immunity rejection occurs before actual hurt");
                suite.check(defender.stand.isStandBlocking() && defender.stand.canStartBlocking(), "rejected raw hit leaves guard intact");
            }
            else if (ordinary()) {
                suite.check(incomingCalls == 1 && guardAtIncoming, "ordinary/no-opt-in source reaches Incoming with guard active");
                suite.check(defender.stand.isStandBlocking(), "ordinary heavy keeps real held guard");
            }
            else {
                if (kind != Kind.ZERO_M) {
                    suite.check(incomingCalls == 1 && !guardAtIncoming, "admitted Uppercut breaks before Incoming/guard reduction");
                    suite.check(near(incomingAmount, 15), "RAW/M1 reaches actual hurt once amount=" + incomingAmount);
                }
                suite.check(!defender.stand.isStandBlocking() && defender.stand.canStartBlocking() == (kind == Kind.IDLE)
                        && !defender.stand.hasEffect(ModStatusEffects.STUN), "guard break/lock without Stand crash STUN");
                if (kind == Kind.HURT_REFUSED || kind == Kind.ZERO_M) suite.check(loss == 0,
                        "positive RAW breaks guard despite actual hurt refusal or M0; health loss=" + loss);
                else if (kind == Kind.IDLE) {
                    suite.idleLoss = loss;
                    suite.check(loss > 0, "idle native linked-user damage baseline=" + loss);
                }
                else suite.check(near(loss, suite.idleLoss), "linked-user damage matches identical idle control actual=" + loss + " idle=" + suite.idleLoss);
            }
            log("IMPACT " + kind + " incoming=" + incomingCalls + " loss=" + loss + " targetAge=" + hitTargetAge
                    + " casterAge=" + hitCasterAge + " guard=" + (defender != null && defender.stand.isStandBlocking()));
        }

        boolean ordinary() { return kind == Kind.ORDINARY_HEAVY || kind == Kind.GRAB_HEAVY; }
        boolean grab() { return kind == Kind.GRAB_UPPERCUT || kind == Kind.GRAB_HEAVY; }
        boolean shield() { return kind == Kind.SHIELD_FRONT || kind == Kind.SHIELD_REFUSED || kind == Kind.SHIELD_REAR; }
        float victimHealth() { return defender != null ? defender.user.getHealth() : shieldPlayer.getHealth(); }
        void aim(Actor actor) { LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(new ActionTarget(target)); }

        EntityActionInstance press(Actor actor, String name, InputMethod method, short key) {
            Ability base = actor.power.getAbility(name);
            premise(base != null, "registered ability exists " + name);
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, method), "real input admission " + name);
            BufferingState buffering = BufferingState.clickOnly();
            var input = AbilityInput.keyPress(key, selected.ability, actor.user, null, method, 0, buffering, base.getAbilityId());
            premise(buffering.isActionSuccess, "native input succeeded " + name);
            return input != null && input.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }

        Actor actor(Vec3 position, float yaw, double speed) {
            Player user = player(position, yaw);
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "grant registered Star Platinum");
            power.setResolveLevel(2);
            premise(type.summon(user, power), "registered full summon");
            StandEntity stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "real owner binding");
            Actor actor = new Actor(user, power, type, stand);
            actors.add(actor);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(position.x, position.y, position.z, yaw, 0); stand.setYHeadRot(yaw);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            return actor;
        }

        Player player(Vec3 position, float yaw) {
            Player player = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            player.getAbilities().invulnerable = false;
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); player.setHealth(100);
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            player.setNoGravity(true); player.noPhysics = true;
            player.moveTo(position.x, position.y, position.z, yaw, 0); player.setYHeadRot(yaw);
            premise(level.addFreshEntity(player), "damageable native player added");
            return player;
        }

        void unlock(Actor actor, String skill) {
            var result = UNLOCKS.forceUnlock(actor.power, skill);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public skill unlock " + skill);
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, kind + " premise: " + detail); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Actor actor : actors) {
                AbilityInput.keyRelease(ATTACK_KEY, actor.user); AbilityInput.keyRelease(GUARD_KEY, actor.user); AbilityInput.keyRelease(CONTROL_KEY, actor.user);
                actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
                if (actor.user.hasData(ModDataAttachmentTypes.CONTROLLER)) actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
                if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power);
                actor.user.discard();
            }
            if (shieldPlayer != null) { shieldPlayer.stopUsingItem(); shieldPlayer.discard(); }
        }
    }

    private static JojoModConfig.Common config() { return JojoModConfig.getCommonConfigInstance(false); }
    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.0001; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-MELEE-01 " + message); }
}
