package rotp.addons.cmoon.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonGravitySetupGameTests {
    private CMoonGravitySetupGameTests() {}

    @GameTest(template = "empty", batch = "cmGravitySetupRollback", timeoutTicks = 80)
    public static void rejectedEntityJoinRestoresSetupOwnershipBeforeSuiteEnrollment(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonAddon.class, GravityTestSupport.class,
                GravityTestSupport.Actor.class, CMoonGravityDurationGameTests.class,
                CMoonStandEntity.class, StandPower.class, JojoModConfig.class,
                CMoonGravitySetupGameTests.class);
        Checks checks = new Checks();
        run(helper, checks, false);
        run(helper, checks, true);
        GravityTestSupport.log("SUMMARY role=setup scenes=2 checks=" + checks.count + " failures="
                + checks.failures.size() + " details=" + checks.failures);
        helper.assertTrue(checks.failures.isEmpty(), "CM setup rollback failed; see measurements and SUMMARY");
        helper.succeed();
    }

    private static void run(GameTestHelper helper, Checks checks, boolean denyCow) {
        String name = denyCow ? "post-Actor-Cow-add-denial" : "Actor-user-add-denial";
        var config = GravityTestSupport.staminaConfig();
        boolean configBefore = config.get();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        boolean forcedBefore = helper.getLevel().getForcedChunks().contains(chunk.toLong());
        Capture capture = new Capture(helper, denyCow);
        Object suite = null;
        Throwable original = null;
        int remainingSuiteListeners = 0;
        boolean measured = false;
        capture.listen();
        try {
            try {
                if (denyCow) {
                    Class<?> suiteType = nested(CMoonGravityDurationGameTests.class, "Suite");
                    Constructor<?> constructor = suiteType.getDeclaredConstructor(GameTestHelper.class);
                    constructor.setAccessible(true);
                    suite = constructor.newInstance(helper);
                    invoke(suite, "listen");
                    helper.assertTrue(listenerCount(suite) == 3, "Probe: actual duration listeners registered");
                    Class<?> kindType = nested(CMoonGravityDurationGameTests.class, "Kind");
                    Object kind = null;
                    for (Object value : kindType.getEnumConstants())
                        if (((Enum<?>) value).name().equals("CONFIG_OFF0")) kind = value;
                    helper.assertTrue(kind != null, "Probe: actual existing duration config-off case");
                    Class<?> sceneType = nested(CMoonGravityDurationGameTests.class, "Scene");
                    Constructor<?> sceneConstructor = sceneType.getDeclaredConstructor(suiteType, kindType);
                    sceneConstructor.setAccessible(true);
                    sceneConstructor.newInstance(suite, kind);
                } else {
                    new GravityTestSupport.Actor(helper, false, true, false);
                }
            } catch (InvocationTargetException failure) {
                original = failure.getCause();
            } catch (RuntimeException | Error failure) {
                original = failure;
            }
            if (suite != null) {
                invoke(suite, "close");
                remainingSuiteListeners = listenerCount(suite);
            }
            capture.close();
            StandPower power = capture.user != null ? PowerClass.STAND.get(capture.user) : null;
            boolean configAfter = JojoModConfig.getCommonConfigInstance(false).standStamina.get();
            boolean forcedAfter = helper.getLevel().getForcedChunks().contains(chunk.toLong());
            GravityTestSupport.log("SETUP-MEASURE case=" + name + " originalType="
                    + (original == null ? null : original.getClass().getName()) + " originalMessage="
                    + (original == null ? null : original.getMessage()) + " suppressed="
                    + (original == null ? -1 : original.getSuppressed().length)
                    + " configBefore=" + configBefore + " configAtDenial=" + capture.configAtDenial
                    + " configAfter=" + configAfter + " forcedBefore=" + forcedBefore
                    + " forcedAtDenial=" + capture.forcedAtDenial + " forcedAfter=" + forcedAfter
                    + " user=" + state(capture.user) + " Stand=" + state(capture.stand)
                    + " target=" + state(capture.target) + " powerSummoned=" + (power != null && power.isSummoned())
                    + " powerLink=" + (power == null ? null : power.getSummonedStandEntity())
                    + " suiteListeners=" + remainingSuiteListeners + " probeListener=" + capture.installed);
            measured = true;
            checks.check(name, original instanceof GameTestAssertException, "original actual assertion type preserved");
            checks.check(name, original != null && original.getMessage().equals(denyCow
                    ? "Premise: actual non-Stand field recipient" : "Premise: actual native player added"),
                    "real failed add premise preserved, not a fabricated gameplay failure");
            checks.check(name, capture.denials == 1, "exact owned EntityJoin denial occurred");
            checks.check(name, Boolean.FALSE.equals(capture.configAtDenial), "actual disabled server config at failure");
            checks.check(name, Boolean.TRUE.equals(capture.forcedAtDenial), "actual Actor forced chunk at failure");
            checks.check(name, capture.user != null, "actual allocated native user observed");
            checks.check(name, capture.user != null && capture.user.isRemoved(), "owned user removed by subject rollback");
            checks.check(name, denyCow ? capture.stand != null && capture.stand.isRemoved() : capture.stand == null,
                    "real registered Stand removed, or no summon before denied user add");
            checks.check(name, denyCow ? capture.target != null && capture.target.isRemoved() : capture.target == null,
                    "provisional Cow discarded, or no target allocated before denied user add");
            checks.check(name, capture.entities.stream().noneMatch(entity -> entity.isAddedToLevel() && !entity.isRemoved()),
                    "no surviving owned level entity before probe recovery");
            checks.check(name, power == null || !power.isSummoned() && power.getSummonedStandEntity() == null,
                    "actual power manifestation link released");
            checks.check(name, configAfter == configBefore, "original actual server config restored");
            checks.check(name, forcedAfter == forcedBefore, "original actual forced-chunk state restored");
            checks.check(name, remainingSuiteListeners == 0 && !capture.installed
                    && original != null && original.getSuppressed().length == 0,
                    "subject listeners cleared, probe unregistered, cleanup did not replace original exception");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Local test-constructor probe could not execute", failure);
        } finally {
            capture.close();
            // OLD leaks are recovered only after the measured state and checks have been recorded.
            if (measured) GravityTestSupport.log("SETUP-PROBE-RECOVERY case=" + name + " afterMeasurement=true");
            StandPower power = capture.user != null ? PowerClass.STAND.get(capture.user) : null;
            try {
                if (capture.user != null && power != null && power.isSummoned())
                    CMoonAddon.C_MOON.get().forceUnsummon(capture.user, power);
            } finally {
                try { capture.entities.forEach(Entity::discard); }
                finally {
                    try { config.set(configBefore); }
                    finally { helper.getLevel().setChunkForced(chunk.x, chunk.z, forcedBefore); }
                }
            }
            helper.assertTrue(config.get() == configBefore
                    && helper.getLevel().getForcedChunks().contains(chunk.toLong()) == forcedBefore
                    && capture.entities.stream().allMatch(Entity::isRemoved),
                    "Probe recovery failed; cannot continue another constructor case");
        }
    }

    private static Class<?> nested(Class<?> owner, String name) {
        for (Class<?> type : owner.getDeclaredClasses()) if (type.getSimpleName().equals(name)) return type;
        throw new IllegalStateException("Missing local test class " + name);
    }

    private static void invoke(Object receiver, String name) throws ReflectiveOperationException {
        Method method = receiver.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(receiver);
    }

    private static int listenerCount(Object suite) throws ReflectiveOperationException {
        var field = suite.getClass().getDeclaredField("listeners");
        field.setAccessible(true);
        return ((List<?>) field.get(suite)).size();
    }

    private static String state(Entity entity) {
        return entity == null ? "null" : entity.getClass().getName() + "/" + entity.getUUID()
                + "/added=" + entity.isAddedToLevel() + "/removed=" + entity.isRemoved();
    }

    private static final class Checks {
        final List<String> failures = new ArrayList<>();
        int count;
        void check(String name, boolean passed, String detail) {
            count++;
            GravityTestSupport.log((passed ? "PASS " : "FAIL ") + "setup " + name + ": " + detail);
            if (!passed) failures.add(name + ": " + detail);
        }
    }

    private static final class Capture implements AutoCloseable {
        final GameTestHelper helper;
        final boolean denyCow;
        final List<Entity> entities = new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> listener;
        Player user;
        CMoonStandEntity stand;
        Cow target;
        int denials;
        Boolean configAtDenial, forcedAtDenial;
        boolean installed;

        Capture(GameTestHelper helper, boolean denyCow) {
            this.helper = helper;
            this.denyCow = denyCow;
            listener = event -> {
                if (event.getLevel() != helper.getLevel()) return;
                Entity entity = event.getEntity();
                boolean deny = false;
                if (entity instanceof Player player && player.getGameProfile().getName().equals("cm-gravity")
                        && new ChunkPos(player.blockPosition()).equals(new ChunkPos(helper.absolutePos(BlockPos.ZERO)))) {
                    user = player;
                    entities.add(player);
                    deny = !denyCow;
                } else if (entity instanceof CMoonStandEntity body && body.getUser() == user && user != null) {
                    stand = body;
                    entities.add(body);
                } else if (entity instanceof Cow cow && user != null && cow.position().distanceToSqr(user.position().add(2, 0, 0)) < 0.0001) {
                    target = cow;
                    entities.add(cow);
                    deny = denyCow;
                }
                if (deny) {
                    denials++;
                    configAtDenial = JojoModConfig.getCommonConfigInstance(false).standStamina.get();
                    ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
                    forcedAtDenial = helper.getLevel().getForcedChunks().contains(chunk.toLong());
                    event.setCanceled(true);
                    GravityTestSupport.log("SETUP-REAL-JOIN-DENIAL cow=" + denyCow + " entity=" + state(entity)
                            + " config=" + configAtDenial + " forced=" + forcedAtDenial);
                }
            };
        }

        void listen() {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, listener);
            installed = true;
        }
        @Override public void close() {
            if (!installed) return;
            NeoForge.EVENT_BUS.unregister(listener);
            installed = false;
        }
    }
}
