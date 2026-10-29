package rotp.addons.killerqueenlion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenGameTests;
import rotp.addons.killerqueenlion.client.SheerHeartAttackVisuals;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;

@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SheerHeartTimingGameTests {
    private SheerHeartTimingGameTests() {}

    @GameTest(template = "empty", templateNamespace = KillerQueenLionAddon.MOD_ID, timeoutTicks = 20)
    public static void sheerHeartIdleVisualKeepsBlastPulseAndWindup(GameTestHelper helper) {
        for (int swell : new int[] { 0, 7, 14, 15 }) {
            for (int idle : new int[] { 0, 200, 199, 1 }) {
                float progress = SheerHeartAttackVisuals.swellProgress(swell, idle);
                float expected = idle == 199 || idle == 1 ? 0.0F : swell / 15.0F;
                helper.assertTrue(Math.abs(progress - expected) < 0.00001F,
                        "Incorrect rendered progress at swell=" + swell + ", idle=" + idle);
                float eased = progress * progress * (3.0F - 2.0F * progress);
                float expectedEased = expected * expected * (3.0F - 2.0F * expected);
                helper.assertTrue(Math.abs((1.0F + 0.6F * eased)
                                        - (1.0F + 0.6F * expectedEased)) < 0.00001F
                                && Math.abs((1.0F + 0.25F * eased)
                                        - (1.0F + 0.25F * expectedEased)) < 0.00001F,
                        "Incorrect rendered scale at swell=" + swell + ", idle=" + idle);
            }
        }
        helper.assertTrue(SheerHeartAttackVisuals.swellProgress(-1, 0) == 0.0F
                        && SheerHeartAttackVisuals.swellProgress(16, 0) == 1.0F,
                "Visual progress lost its existing clamp");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = KillerQueenLionAddon.MOD_ID,
            batch = "r639_retained_visual", timeoutTicks = 360)
    public static void retainedChargeStaysVisuallyIdleAfterTargetRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RetainedVisual"));
        Creeper target = EntityType.CREEPER.create(level);
        SheerHeartAttackEntity sheer = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(level);
        helper.assertTrue(target != null && sheer != null, "Missing retained-visual fixtures");
        Difficulty oldDifficulty = level.getDifficulty();
        boolean oldBreakRule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        List<Long> blasts = new ArrayList<>();
        RetainedVisualTrace trace = new RetainedVisualTrace();
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == owner) {
                blasts.add(level.getGameTime());
            }
        };
        Runnable cleanup = () -> {
            if (!finished.compareAndSet(false, true)) return;
            NeoForge.EVENT_BUS.unregister(listener);
            KillerQueenStateService.recallSheerHeart(owner);
            sheer.discard();
            target.discard();
            var power = PowerClass.STAND.get(owner);
            if (power != null && power.hasPower()) power.getPowerType().forceUnsummon(owner, power);
            owner.getInventory().clearContent();
            owner.discard();
            KillerQueenGameTests.sheerHeartPad(helper, false);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldBreakRule, level.getServer());
            level.getServer().setDifficulty(oldDifficulty, true);
        };
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(false, level.getServer());
            BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
            owner.setNoGravity(true);
            owner.setPos(Vec3.atBottomCenterOf(pad));
            level.addNewPlayer(owner);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
            power.setResolveLevel(4);
            helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                    "Could not summon the retained-visual caster");
            var stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing retained-visual caster Stand");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            target.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            target.setHealth(2000.0F);
            sheer.bind(owner, stand, target);
            helper.assertTrue(!sheer.isRemoved(), "Retained-visual public binding failed");
            sheer.setNoGravity(true);
            // Like the existing timing probe, this isolates presentation from travel, not movement behavior.
            sheer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            sheer.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            sheer.setPos(owner.position().add(8, 0, -3));
            Vec3 nearby = sheer.position().add(0, 0, 2.75D);
            Vec3 withdrawn = owner.position().add(-1, 0, -3);
            target.setPos(nearby);
            helper.assertTrue(level.isPositionEntityTicking(sheer.blockPosition())
                            && level.isPositionEntityTicking(BlockPos.containing(nearby))
                            && level.isPositionEntityTicking(BlockPos.containing(withdrawn))
                            && withdrawn.distanceToSqr(sheer.position()) > SheerHeartAttackEntity.SWELL_KEEP_DISTANCE_SQR,
                    "Retained-visual fixture left the ticking pad or failed to withdraw beyond seven blocks");
            level.addFreshEntity(target);
            level.addFreshEntity(sheer);
            helper.assertTrue(KillerQueenStateService.linkSheerHeart(owner, sheer), "Retained-visual link failed");
            NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, listener);
            helper.onEachTick(() -> {
                if (finished.get()) return;
                try {
                    helper.assertTrue(sheer.isAlive() && !sheer.isNoAi(), "Retained-visual entity lost authority or AI");
                    int swell = sheer.swellTicks();
                    int idle = sheer.postExplosionIdleTicks();
                    float visual = SheerHeartAttackVisuals.swellProgress(sheer.visualSwellTicks(), idle);
                    if (blasts.isEmpty()) {
                        helper.assertTrue(target.isAlive() && idle == 0, "Pre-blast control unexpectedly entered idle");
                        if (swell > 0) {
                            helper.assertTrue(sheer.visualSwellTicks() == swell && visual > 0,
                                    "Ordinary initial charge or its aborted windup was hidden");
                            trace.windupSamples++;
                        }
                        if (trace.phase == 0 && swell >= 7) {
                            helper.assertTrue(swell < SheerHeartAttackEntity.SWELL_TICKS,
                                    "Could not withdraw the target before the first blast");
                            trace.freshSwell = swell;
                            trace.freshSave = sheer.saveWithoutId(new CompoundTag());
                            target.setPos(withdrawn);
                            trace.phase = 1;
                        }
                        else if (trace.phase == 1) {
                            if (swell > 0 && swell < trace.previousSwell) trace.abortedSamples++;
                            if (swell == 0) {
                                helper.assertTrue(trace.abortedSamples > 0, "Cancelled fresh charge did not visibly drain");
                                LogUtils.getLogger().info("R639 fresh/aborted controls: windupSamples={} abortedSamples={}",
                                        trace.windupSamples, trace.abortedSamples);
                                target.setPos(nearby);
                                trace.phase = 2;
                            }
                        }
                        trace.previousSwell = swell;
                        return;
                    }
                    helper.assertTrue(blasts.size() == 1 && trace.phase == 2,
                            "Unexpected blast before the fresh-abort control or after target removal");
                    if (!trace.blastPulseSeen) {
                        helper.assertTrue(idle == SheerHeartAttackEntity.POST_EXPLOSION_IDLE_TICKS
                                        && swell == SheerHeartAttackEntity.SWELL_TICKS && visual == 1.0F,
                                "Real first-blast tick200 pulse was not observed at full visual swell");
                        trace.blastPulseSeen = true;
                        LogUtils.getLogger().info("R639 actual blast pulse: idle={} swell={} visual={}", idle, swell, visual);
                        trace.blastSave = sheer.saveWithoutId(new CompoundTag());
                        target.discard();
                    }
                    helper.assertTrue(target.isRemoved(), "Target removal during post-blast idle did not persist");
                    if (idle > 0) {
                        helper.assertTrue(swell == SheerHeartAttackEntity.SWELL_TICKS,
                                "Authoritative retained charge changed during idle");
                        if (idle < SheerHeartAttackEntity.POST_EXPLOSION_IDLE_TICKS) {
                            helper.assertTrue(visual == 0.0F, "Existing idle199..1 visual suppression regressed");
                        }
                        if (idle == 1) trace.idleSave = sheer.saveWithoutId(new CompoundTag());
                        trace.previousIdle = idle;
                        return;
                    }
                    if (trace.drainSamples == 0) {
                        helper.assertTrue(trace.previousIdle == 1 && swell == SheerHeartAttackEntity.SWELL_TICKS,
                                "Did not observe the natural idle1->0 transition with retained15 charge");
                    }
                    helper.assertTrue(sheer.getTarget() == null || !sheer.getTarget().isAlive(),
                            "An unrelated live target contaminated the no-new-blast control");
                    int expectedSwell = SheerHeartAttackEntity.SWELL_TICKS - trace.drainSamples;
                    helper.assertTrue(swell == expectedSwell,
                            "Authoritative no-target drain changed: expected=" + expectedSwell + ", actual=" + swell);
                    LogUtils.getLogger().info("R639 retained drain: sample={} idle={} swell={} visual={} blasts={}",
                            trace.drainSamples, idle, swell, visual, blasts.size());
                    helper.assertTrue(visual == 0.0F,
                            "Retained no-new-blast charge re-inflated at idle0: swell=" + swell + ", visual=" + visual);
                    // These non-ticking reload checks run only after the original visual failure point.
                    if (trace.drainSamples == 0) {
                        assertVisualSave(helper, trace.freshSave, trace.freshSwell, 0,
                                trace.freshSwell, false, trace.freshSwell, "fresh windup");
                        assertVisualSave(helper, trace.blastSave, 15, 200, 15, true, 15, "blast pulse");
                        assertVisualSave(helper, trace.idleSave, 15, 1, 15, true, 15, "idle ending");
                        assertVisualSave(helper, sheer.saveWithoutId(new CompoundTag()),
                                15, 0, 0, true, 0, "retained full charge");
                    }
                    else if (trace.drainSamples == 1) {
                        assertVisualSave(helper, sheer.saveWithoutId(new CompoundTag()),
                                14, 0, 0, true, 14, "legacy ambiguous partial drain");
                    }
                    else if (trace.drainSamples == SheerHeartAttackEntity.SWELL_TICKS) {
                        assertVisualSave(helper, sheer.saveWithoutId(new CompoundTag()),
                                0, 0, 0, false, 0, "drained charge");
                    }
                    trace.drainSamples++;
                    if (trace.drainSamples == SheerHeartAttackEntity.SWELL_TICKS + 1) {
                        helper.assertTrue(trace.windupSamples > 0 && trace.abortedSamples > 0 && trace.blastPulseSeen,
                                "Missing positive fresh-charge, aborted-charge or actual-blast control");
                        cleanup.run();
                        helper.succeed();
                    }
                }
                catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            helper.runAfterDelay(350, () -> {
                if (!finished.get()) {
                    cleanup.run();
                    helper.fail("Retained-visual test did not complete natural windup, blast, idle and fifteen drain ticks");
                }
            });
        }
        catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static void assertVisualSave(GameTestHelper helper, CompoundTag saved, int swell,
            int idle, int visual, boolean retained, int legacyVisual, String label) {
        helper.assertTrue(saved != null && saved.contains("RetainedSwell", Tag.TAG_BYTE)
                        && saved.getBoolean("RetainedSwell") == retained,
                "Natural " + label + " save lost its retained-charge marker");
        SheerHeartAttackEntity loaded = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Missing registered save/load fixture");
        // Do not add or tick this same-UUID copy: this checks decoding, not a second gameplay actor.
        loaded.load(saved.copy());
        helper.assertTrue(loaded.swellTicks() == swell && loaded.postExplosionIdleTicks() == idle
                        && loaded.visualSwellTicks() == visual
                        && loaded.saveWithoutId(new CompoundTag()).getBoolean("RetainedSwell") == retained,
                "Saved " + label + " changed counters or presentation on load");
        CompoundTag legacy = saved.copy();
        legacy.remove("RetainedSwell");
        loaded.load(legacy);
        helper.assertTrue(loaded.swellTicks() == swell && loaded.postExplosionIdleTicks() == idle
                        && loaded.visualSwellTicks() == legacyVisual,
                "Legacy " + label + " changed counters or its conservative missing-flag interpretation");
        LogUtils.getLogger().info("R639 NBT {}: swell={} idle={} markedVisual={} legacyVisual={}",
                label, swell, idle, visual, legacyVisual);
    }

    private static final class RetainedVisualTrace {
        private int phase;
        private int previousSwell;
        private int previousIdle = -1;
        private int windupSamples;
        private int abortedSamples;
        private int drainSamples;
        private boolean blastPulseSeen;
        private int freshSwell;
        private CompoundTag freshSave;
        private CompoundTag blastSave;
        private CompoundTag idleSave;
    }

    @GameTest(template = "empty", templateNamespace = KillerQueenLionAddon.MOD_ID,
            batch = "r320_repeat_timing", timeoutTicks = 450)
    public static void sheerHeartRepeatBlastProbe(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BlastTimeProbe"));
        Creeper target = EntityType.CREEPER.create(level);
        SheerHeartAttackEntity sheer = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(level);
        helper.assertTrue(target != null && sheer != null, "Missing blast fixtures");
        Difficulty oldDifficulty = level.getDifficulty();
        boolean oldBreakRule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        List<Long> blasts = new ArrayList<>();
        int[] idleSwell = {-1};
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == owner) {
                blasts.add(level.getGameTime());
            }
        };
        Runnable cleanup = () -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            NeoForge.EVENT_BUS.unregister(listener);
            KillerQueenStateService.recallSheerHeart(owner);
            sheer.discard();
            target.discard();
            var power = PowerClass.STAND.get(owner);
            if (power != null && power.hasPower()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.getInventory().clearContent();
            owner.discard();
            KillerQueenGameTests.sheerHeartPad(helper, false);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldBreakRule, level.getServer());
            level.getServer().setDifficulty(oldDifficulty, true);
        };
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(false, level.getServer());
            BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
            owner.setPos(Vec3.atBottomCenterOf(pad));
            level.addNewPlayer(owner);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
            power.setResolveLevel(4);
            helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                    "Could not summon the blast caster");
            var stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing caster Stand");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            target.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            target.setHealth(2000.0F);
            sheer.bind(owner, stand, target);
            helper.assertTrue(!sheer.isRemoved(), "Public blast binding failed");
            sheer.setNoGravity(true);
            sheer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            sheer.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            sheer.setPos(owner.position().add(8, 0, -3));
            target.setPos(sheer.position().add(0, 0, 1.25D));
            helper.assertTrue(level.isPositionEntityTicking(sheer.blockPosition())
                            && level.isPositionEntityTicking(target.blockPosition()),
                    "Repeat-blast fixtures left the test's entity-ticking chunk");
            level.addFreshEntity(target);
            level.addFreshEntity(sheer);
            helper.assertTrue(KillerQueenStateService.linkSheerHeart(owner, sheer), "Public blast link failed");
            Vec3 targetPosition = target.position();
            Vec3 sheerPosition = sheer.position();
            NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, listener);
            helper.onEachTick(() -> {
                if (finished.get()) {
                    return;
                }
                try {
                    helper.assertTrue(target.isAlive() && !sheer.isRemoved(), "Blast fixture died or lost authority");
                    helper.assertTrue(target.position().distanceToSqr(targetPosition) < 1.0E-5D
                                    && sheer.position().distanceToSqr(sheerPosition) < 1.0E-5D,
                            "Stationary blast fixture moved");
                    if (blasts.size() == 1 && sheer.postExplosionIdleTicks() > 0 && idleSwell[0] < 0) {
                        idleSwell[0] = sheer.swellTicks();
                    }
                    if (blasts.size() >= 2) {
                        long interval = blasts.get(1) - blasts.get(0);
                        LogUtils.getLogger().info("R320 native blasts={} interval={} idle-swell={}",
                                blasts, interval, idleSwell[0]);
                        helper.assertTrue(interval >= SheerHeartAttackEntity.POST_EXPLOSION_IDLE_TICKS
                                        && interval < SheerHeartAttackEntity.POST_EXPLOSION_IDLE_TICKS
                                                + SheerHeartAttackEntity.SWELL_TICKS
                                        && idleSwell[0] == SheerHeartAttackEntity.SWELL_TICKS,
                                "Repeat burst added a fresh windup or lost the donor's retained charge");
                        cleanup.run();
                        helper.succeed();
                    }
                } catch (Throwable error) {
                    cleanup.run();
                    throw error;
                }
            });
            helper.runAfterDelay(430, () -> {
                if (!finished.get()) {
                    cleanup.run();
                    helper.fail("Two native blasts were not observed within the bounded probe");
                }
            });
        } catch (Throwable error) {
            cleanup.run();
            throw error;
        }
    }
}
