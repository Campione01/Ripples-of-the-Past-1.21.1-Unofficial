package rotp.addons.cmoon.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.addons.cmoon.ability.GravityCounterAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

final class GravityTestSupport {
    static final short KEY = 211;
    static FloorSuite active;

    private GravityTestSupport() {}

    record Spec(String name, float budget, boolean resolve, boolean warm, boolean creative,
            boolean configOff, boolean admitted) {}

    static List<Spec> floorSpecs(float floor) {
        return List.of(
                new Spec("cold-low", floor - 1, false, false, false, false, false),
                new Spec("cold-zero", 0, false, false, false, false, false),
                new Spec("resolve-cold-low", floor - 1, true, false, false, false, false),
                new Spec("resolve-cold-zero", 0, true, false, false, false, false),
                new Spec("warm-low", floor - 1, false, true, false, false, false),
                new Spec("warm-zero", 0, false, true, false, false, false),
                new Spec("resolve-warm-low", floor - 1, true, true, false, false, false),
                new Spec("resolve-warm-zero", 0, true, true, false, false, false),
                new Spec("literal-floor", floor, false, false, false, false, true),
                new Spec("resolve-literal-floor", floor, true, false, false, false, true),
                new Spec("creative-stored-zero", 0, false, false, true, false, true),
                new Spec("config-off-stored-zero", 0, false, false, false, true, true));
    }

    static final class Actor implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final Player user;
        final StandPower power;
        final CMoonStandEntity stand;
        final Vec3 origin;
        final ChunkPos chunk;
        final boolean wasForced;
        final boolean oldStaminaSetting;
        final ModConfigSpec.ConfigValue<Boolean> staminaConfig;
        boolean closed;

        Actor(GameTestHelper helper, boolean creative, boolean configOff, boolean resolve) {
            this(helper, creative, configOff, resolve, 400);
        }

        Actor(GameTestHelper helper, boolean creative, boolean configOff, boolean resolve, int resolveDuration) {
            this.helper = helper;
            level = helper.getLevel();
            staminaConfig = staminaConfig();
            oldStaminaSetting = staminaConfig.get();
            chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            wasForced = level.getForcedChunks().contains(chunk.toLong());
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5,
                    helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            Player ownedUser = null;
            StandPower ownedPower = null;
            StandEntity ownedStand = null;
            try {
                level.setChunkForced(chunk.x, chunk.z, true);
                user = ownedUser = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "cm-gravity")) {
                    @Override public boolean isCreative() { return creative; }
                    @Override public boolean isSpectator() { return false; }
                };
                staminaConfig.set(!configOff);
                user.getAbilities().instabuild = creative;
                user.getAbilities().invulnerable = false;
                user.setNoGravity(true);
                user.noPhysics = true;
                user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                user.setHealth(200);
                user.moveTo(origin.x, origin.y, origin.z, 0, 0);
                premise(level.addFreshEntity(user), "actual native player added");
                power = ownedPower = PowerClass.STAND.attachGet(user);
                premise(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                        == StandPowerTransitions.Status.APPLIED, "public registered CM grant");
                power.setResolveLevel(power.getMaxResolveLevel());
                premise(CMoonAddon.C_MOON.get().summon(user, power), "actual registered CM summon factory");
                ownedStand = power.getSummonedStandEntity();
                premise(ownedStand instanceof CMoonStandEntity,
                        "actual CM manifestation");
                stand = (CMoonStandEntity) power.getSummonedStandEntity();
                stand.summonLockTicks = 0;
                stand.gradualSummonWeaknessTicks = 0;
                stand.setCanFollowUser(false);
                stand.setNoGravity(true);
                stand.noPhysics = true;
                stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
                if (resolve) {
                    premise(user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, resolveDuration, 1)),
                            "real amplifier1 Resolve initializer");
                }
                premise(stand.getClass() == CMoonStandEntity.class && stand.getUser() == user
                        && stand.getUserPower() == power
                        && stand.getStandType().equals(CMoonAddon.C_MOON.getId())
                        && power.getResolveLevel() >= 2,
                        "actual factory/owner/registry and unlock after effect initializer");
                premise(user.hasEffect(ModStatusEffects.RESOLVE) == resolve
                        && power.isStaminaInfinite() == (creative || configOff),
                        "actual finite/infinite getters and real Resolve effect");
                power.setStamina(power.getMaxStamina());
            } catch (RuntimeException | Error failure) {
                rollback(failure, ownedUser, ownedPower, ownedStand);
                throw failure;
            }
        }

        void premise(boolean passed, String message) { helper.assertTrue(passed, "Premise: " + message); }
        float stored() { return power.serializeNBT(level.registryAccess()).getFloat("Stamina"); }

        void debitTo(float budget) {
            if (power.isStaminaInfinite()) {
                power.setStamina(budget);
                premise(stored() == budget && power.getStamina() == power.getMaxStamina(),
                        "raw storage differs from actual infinite getter");
            } else {
                float before = power.getStamina();
                premise(before >= budget && power.consumeStamina(before - budget), "public finite debit");
                premise(power.getStamina() == budget && stored() == budget, "actual finite literal budget");
            }
        }

        AbilityConditionCheck resolved(Ability ability) {
            var checked = power.updateAvailableMoves().getContextVariationContainer(ability);
            premise(checked != null && checked.ability == ability, "actual registered context variation");
            return checked;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            Throwable failure = rollback(null, user, power, stand);
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }

        void closeAfterFailure(Throwable original) {
            if (closed) return;
            closed = true;
            rollback(original, user, power, stand);
        }

        private Throwable rollback(Throwable failure, Player ownedUser, StandPower ownedPower, StandEntity ownedStand) {
            try { if (ownedUser != null) AbilityInput.keyRelease(KEY, ownedUser); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                if (ownedUser != null && ownedUser.hasData(ModDataAttachmentTypes.CONTROLLER))
                    ownedUser.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                if (ownedPower == null && ownedUser != null) ownedPower = PowerClass.STAND.get(ownedUser);
                if (ownedStand == null && ownedPower != null) ownedStand = ownedPower.getSummonedStandEntity();
                if (ownedUser != null && ownedPower != null && ownedPower.isSummoned())
                    CMoonAddon.C_MOON.get().forceUnsummon(ownedUser, ownedPower);
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try { if (ownedStand != null) ownedStand.discard(); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try { if (ownedUser != null) ownedUser.discard(); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try { staminaConfig.set(oldStaminaSetting); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try { level.setChunkForced(chunk.x, chunk.z, wasForced); }
            catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            try {
                premise(JojoModConfig.getCommonConfigInstance(false).standStamina.get() == oldStaminaSetting
                        && level.getForcedChunks().contains(chunk.toLong()) == wasForced,
                        "config and forced chunk restored");
            } catch (RuntimeException | Error error) { failure = preserve(failure, error); }
            return failure;
        }

        private static Throwable preserve(Throwable original, Throwable cleanup) {
            if (original == null) return cleanup;
            if (original != cleanup) original.addSuppressed(cleanup);
            return original;
        }
    }

    static ModConfigSpec.ConfigValue<Boolean> staminaConfig() {
        ModConfigSpec.ConfigValue<Boolean> value = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standStamina"));
        if (value == null) value = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "Hamon settings", "standStamina"));
        if (value == null) throw new IllegalStateException("Missing actual server stamina config value");
        log("CONFIG actualPath=" + value.getPath() + " value=" + value.get());
        return value;
    }

    static final class FloorSuite implements GameTestListener {
        final GameTestHelper helper;
        final boolean center;
        final List<Spec> specs;
        final List<String> failures = new ArrayList<>();
        FloorScene scene;
        int index, checks;
        boolean closed;

        FloorSuite(GameTestHelper helper, boolean center) {
            helper.assertTrue(active == null, "Overlapping CM floor suite");
            this.helper = helper;
            this.center = center;
            specs = floorSpecs(center ? 100 : 75);
            active = this;
            helper.testInfo.addListener(this);
        }

        void tick() {
            if (closed) return;
            try {
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY role=" + (center ? "center" : "counter") + " scenes=" + index
                                + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "CM literal floor failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new FloorScene(this, specs.get(index++));
                }
                scene.tick();
                helper.assertTrue(++scene.ticks < 60, "Natural CM floor scene timeout");
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void check(boolean passed, String message) {
            checks++;
            String detail = scene.spec.name() + ": " + message;
            log((passed ? "PASS " : "FAIL ") + detail);
            if (!passed) failures.add(detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            if (scene != null) scene.close();
            active = null;
            log("FLOOR-CLEANUP actors=0 configRestored=true chunkRestored=true");
        }
        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }

    static final class FloorScene implements AutoCloseable {
        final FloorSuite suite;
        final Spec spec;
        final Actor actor;
        final Ability ability;
        EntityActionInstance action;
        int ticks, pressUserTick = -1;
        long pressWorldTick;
        boolean released, done;

        FloorScene(FloorSuite suite, Spec spec) {
            this.suite = suite;
            this.spec = spec;
            Actor ownedActor = null;
            try {
                actor = ownedActor = new Actor(suite.helper, spec.creative(), spec.configOff(), spec.resolve());
                ability = actor.power.getAbility(suite.center ? CMoonAddon.CENTER_OF_GRAVITY : CMoonAddon.GRAVITY_COUNTER);
                actor.premise(ability != null && ability.getClass() == (suite.center
                        ? CenterOfGravityAbility.class : GravityCounterAbility.class), "actual registered gravity ability");
            } catch (RuntimeException | Error failure) {
                if (ownedActor != null) ownedActor.closeAfterFailure(failure);
                throw failure;
            }
        }

        void tick() {
            if (pressUserTick < 0) { if (ticks >= 3) press(); return; }
            int elapsed = actor.user.tickCount - pressUserTick;
            if (suite.center && !released && action != null && action.getPhase() == ActionPhase.PERFORM) {
                AbilityInput.keyRelease(KEY, actor.user);
                released = true;
            }
            if (elapsed == 2) {
                EntityActionInstance current = actor.stand.getCurStandAction();
                suite.check((current != null) == spec.admitted(), "natural +2 action admission retained");
                suite.check(actor.stand.isCounterActive() == (!suite.center && spec.admitted()),
                        "natural +2 counter flag; no invalid arm");
                suite.check(actor.power.getAbilityCooldown(ability.name()) == expectedCooldown(elapsed, false),
                        "natural +2 cooldown/refusal remains literal");
            }
            if (elapsed >= (suite.center ? 16 : 25)) {
                EntityActionInstance current = actor.stand.getCurStandAction();
                suite.check(suite.center ? (current != null && current.getPhase() == ActionPhase.PERFORM) == spec.admitted()
                        : current == null, "bounded natural charge/expiry and invalid-input tail");
                suite.check(!actor.stand.isCounterActive(), "no lingering counter flag at bounded end");
                suite.check(actor.power.getAbilityCooldown(ability.name()) == expectedCooldown(elapsed, true),
                        "bounded cooldown; invalid action must never install one");
                log("FLOOR-END role=" + (suite.center ? "center" : "counter") + " scene=" + spec.name()
                        + " worldDelta=" + (actor.level.getGameTime() - pressWorldTick) + " userDelta=" + elapsed
                        + " action=" + current + " stored=" + actor.stored() + " effective=" + actor.power.getStamina());
                done = true;
            }
        }

        void press() {
            actor.premise(actor.user.tickCount >= 3 && actor.stand.tickCount >= 3
                    && !actor.power.isPausedInStoppedTime(), "actual world user and Stand ticks, not manual phase driver");
            actor.premise(actor.power.getStamina() >= (suite.center ? 100 : 75)
                    && actor.power.getResolveLevel() >= 2 && ability.checkMainModLogicConditions(actor.power).isPositive(),
                    "healthy unlocked input with no unrelated cooldown/main-condition refusal");
            AbilityConditionCheck checked;
            if (spec.warm()) {
                checked = actor.resolved(ability);
                actor.premise(checked.conditionCheck.isPositive(), "genuine high-budget positive cache");
                actor.debitTo(spec.budget());
                actor.premise(actor.resolved(ability) == checked && checked.conditionCheck.isPositive(),
                        "same-tick positive cache survived real public debit");
            } else {
                actor.debitTo(spec.budget());
                checked = actor.resolved(ability);
            }
            actor.premise(actor.user.hasEffect(ModStatusEffects.RESOLVE) == spec.resolve()
                    && actor.power.getResolveLevel() >= 2
                    && actor.power.isStaminaInfinite() == (spec.creative() || spec.configOff()),
                    "post-initializer effect, unlock and actual infinite getter");
            suite.check(ability.checkSpecificConditions(actor.power).isPositive() == spec.admitted(),
                    "local literal floor independent of Resolve permission");
            suite.check(checked.conditionCheck.isPositive() == (spec.warm() || spec.admitted()),
                    "actual available-container cold/warm condition");
            boolean eligible = AbilityInput.withConditionCheck(checked, actor.user, InputMethod.HOLD);
            suite.check(eligible == (spec.warm() || spec.admitted()), "actual checked HOLD eligibility");
            BufferingState buffering = BufferingState.clickOnly();
            if (eligible) AbilityInput.keyPress(KEY, checked.ability, actor.user, null, InputMethod.HOLD,
                    0, buffering, ability.getAbilityId());
            action = actor.stand.getCurStandAction();
            suite.check(buffering.isActionSuccess == spec.admitted(), "live keyPress action-success veto before super");
            suite.check((action != null) == spec.admitted(), "actual registered action installed only above literal floor");
            suite.check(action == null || action.ability == ability, "no wrong action substituted");
            suite.check(actor.stand.isCounterActive() == (!suite.center && spec.admitted()),
                    "counter flag only for admitted real counter");
            suite.check(actor.power.getAbilityCooldown(ability.name()) == expectedCooldown(0, false),
                    "pre/post input cooldown: invalid must remain zero");
            pressUserTick = actor.user.tickCount;
            pressWorldTick = actor.level.getGameTime();
            if (!suite.center) { AbilityInput.keyRelease(KEY, actor.user); released = true; }
            log("FLOOR-PRESS role=" + (suite.center ? "center" : "counter") + " scene=" + spec.name()
                    + " worldTick=" + pressWorldTick + " userTick=" + pressUserTick + " nativeStandTick=" + actor.stand.tickCount
                    + " Resolve=" + spec.resolve() + " unlock=" + actor.power.getResolveLevel()
                    + " warm=" + spec.warm() + " stored=" + actor.stored() + " effective=" + actor.power.getStamina()
                    + " infinite=" + actor.power.isStaminaInfinite() + " checked=" + checked.conditionCheck.isPositive()
                    + " eligible=" + eligible + " success=" + buffering.isActionSuccess + " phase="
                    + (action == null ? null : action.getPhase()) + " cooldown=" + actor.power.getAbilityCooldown(ability.name()));
        }

        int expectedCooldown(int elapsed, boolean fired) {
            if (!spec.admitted() || suite.center && !fired) return 0;
            // 1.16 Action.getCooldown: the technical 150 / 100 for everyone, the additional 125 / 75 neither in
            // Resolve (StandAction's multiplier 0) nor for a Creative user (Action.getCooldownAdditional).
            boolean technicalOnly = spec.resolve() || spec.creative();
            int total = suite.center ? 150 + (technicalOnly ? 0 : 125) : 100 + (technicalOnly ? 0 : 75);
            // Center fires in the 10th tick of its BUTTON_CHARGE and takes its cooldown there (1.16
            // PowerBaseImpl.performAction), a tick before its perform; later sampling verifies the actual offset.
            return Math.max(total - (suite.center ? elapsed - 10 : elapsed), 0);
        }
        @Override public void close() { actor.close(); }
    }

    static void identity(GameTestHelper helper, Class<?>... classes) {
        String prefix = "cm.gravity.expected.";
        String flagKey = "cm.gravity.bound";
        var properties = System.getProperties();
        String flag = properties.getProperty(flagKey);
        if (properties.containsKey(flagKey)) helper.assertTrue("true".equals(flag) || "false".equals(flag),
                "Malformed explicit CM gravity bound flag: expected exact true or false");
        boolean anyExpected = properties.keySet().stream()
                .anyMatch(key -> key instanceof String name && name.startsWith(prefix));
        boolean bound = "true".equals(flag) || anyExpected;
        helper.assertTrue(classes != null && classes.length > 0, "No CM gravity identity classes requested");
        log("IDENTITY-MODE " + (bound ? "BOUND" : "NORMAL") + " explicit=" + flag
                + " anyExpected=" + anyExpected + " requested=" + classes.length);
        for (Class<?> type : classes) {
            helper.assertTrue(type != null, "Missing CM gravity identity class");
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                helper.assertTrue(stream != null, "Missing actual class resource " + type.getName());
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                var source = type.getProtectionDomain().getCodeSource();
                helper.assertTrue(source != null && source.getLocation() != null, "Missing actual CodeSource " + type.getName());
                log("IDENTITY " + type.getName() + "=" + hash + " codeSource="
                        + source.getLocation().toURI().toASCIIString());
                if (bound) {
                    String expected = properties.getProperty(prefix + type.getName());
                    helper.assertTrue(expected != null && expected.matches("[0-9a-fA-F]{64}")
                            && expected.equalsIgnoreCase(hash), "Native class binding " + type.getName());
                }
            } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }

    static void log(String message) { JojoMod.getLogger().info("T4-CM-GRAVITY " + message); }
}
