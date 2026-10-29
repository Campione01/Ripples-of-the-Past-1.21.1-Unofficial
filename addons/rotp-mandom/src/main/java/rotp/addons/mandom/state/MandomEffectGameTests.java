package rotp.addons.mandom.state;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.mandom.MandomAddon;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;

@GameTestHolder(MandomAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MandomEffectGameTests {
    private MandomEffectGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_m7_vampire_identical", timeoutTicks = 180)
    public static void vampireIdenticalPassiveRestoresOrdinaryEffects(GameTestHelper helper) {
        runCase(helper, true, false, Failure.NONE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_m7_zombie_identical", timeoutTicks = 180)
    public static void zombieIdenticalPassiveRestoresOrdinaryEffects(GameTestHelper helper) {
        runCase(helper, false, false, Failure.NONE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_m7_vampire_differing", timeoutTicks = 180)
    public static void vampireDifferingKeptPassiveUsesDonorMerge(GameTestHelper helper) {
        runCase(helper, true, true, Failure.NONE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_m7_zombie_differing", timeoutTicks = 180)
    public static void zombieDifferingKeptPassiveUsesDonorMerge(GameTestHelper helper) {
        runCase(helper, false, true, Failure.NONE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_m7_blocked_removal", timeoutTicks = 180)
    public static void blockedOrdinaryRemovalRollsBackEffectsAndAttributes(GameTestHelper helper) {
        runCase(helper, true, true, Failure.REMOVE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_m7_blocked_replacement", timeoutTicks = 180)
    public static void deniedOrdinaryReplacementPreservesEffectsWithoutAddedNotification(GameTestHelper helper) {
        runCase(helper, true, true, Failure.REPLACE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_current_effect_applicability", timeoutTicks = 180)
    public static void currentOnlyEffectIsRemovedBeforeHistoricalHarmfulApplication(GameTestHelper helper) {
        runCase(helper, true, true, Failure.CURRENT_EFFECT_VETO);
    }

    private static void runCase(
            GameTestHelper helper, boolean vampire, boolean differing, Failure failure) {
        var level = helper.getLevel();
        Cow user = EntityType.COW.create(level);
        helper.assertTrue(user != null, "Missing rewind user fixture");
        FakePlayer target = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MandomEffects"));
        Fixtures fixtures = new Fixtures(user, target);
        guard(fixtures, () -> {
            MandomRewindService.clear(level.getServer());
            user.setNoAi(true);
            user.setNoGravity(true);
            user.setInvulnerable(true);
            user.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
            helper.assertTrue(level.addFreshEntity(user), "Could not add rewind user");
            target.setNoGravity(true);
            target.setInvulnerable(true);
            target.setPos(user.position().add(2, 0, 0));
            level.addNewPlayer(target);
            helper.assertTrue(level.getEntity(target.getUUID()) == target,
                    "The powered player is not in the production entity timeline");
            var power = PowerClass.PLAYER_POWER.attachGet(target);
            power.setPowerType(vampire ? ModPlayerPowers.VAMPIRISM.get() : ModPlayerPowers.ZOMBIE.get());
            if (differing) {
                target.forceAddEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, 600, 0, false, false, false), null);
            }
            MobEffectInstance passive = target.getEffect(MobEffects.NIGHT_VISION);
            helper.assertTrue(passive != null && kept(target, passive),
                    "The real vampire/zombie power did not grant a kept passive");
            helper.assertTrue(target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 600, 4, false, true, true)),
                    "Could not add historical ordinary Speed");
            helper.assertTrue(target.addEffect(new MobEffectInstance(
                    MobEffects.ABSORPTION, 600, 2, false, true, true)),
                    "Could not add historical ordinary Absorption");
            helper.assertTrue(target.addEffect(new MobEffectInstance(
                    MobEffects.HEALTH_BOOST, 600, 20, false, true, true)),
                    "Could not add historical ordinary Health Boost");
            if (failure == Failure.CURRENT_EFFECT_VETO) {
                helper.assertTrue(!target.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                        "The applicability blocker must be absent from historical state");
                helper.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0)),
                        "Could not add historical harmful Poison");
            }
            target.setAbsorptionAmount(9.0F);
            target.setHealth(target.getMaxHealth() - 3.0F);
            captureAtSample(helper, fixtures, differing, failure);
        });
    }

    private static void captureAtSample(
            GameTestHelper helper, Fixtures fixtures, boolean differing, Failure failure) {
        guard(fixtures, () -> {
            var level = helper.getLevel();
            if (level.getGameTime() % 20L != 0L) {
                helper.runAfterDelay(1, () -> captureAtSample(helper, fixtures, differing, failure));
                return;
            }
            MandomRewindService.tick(level.getServer());
            long historicalTick = level.getGameTime();
            Effects historical = Effects.capture(fixtures.target());
            // Keep this tick's automatic sample historical; mutate on the following tick.
            helper.runAfterDelay(1, () -> guard(fixtures, () -> {
                FakePlayer target = fixtures.target();
                target.forceAddEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 400, 6, true, true, true), null);
                target.forceAddEffect(new MobEffectInstance(
                        MobEffects.ABSORPTION, 400, 0, true, true, true), null);
                target.forceAddEffect(new MobEffectInstance(
                        MobEffects.HEALTH_BOOST, 400, 16, true, true, true), null);
                helper.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0)),
                        "Could not add the non-historical ordinary removal control");
                if (failure == Failure.CURRENT_EFFECT_VETO) {
                    helper.assertTrue(target.removeEffect(MobEffects.POISON),
                            "Could not remove historical Poison before enabling the current-only blocker");
                    helper.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0)),
                            "Could not add the current-only beneficial applicability blocker");
                }
                target.setAbsorptionAmount(1.0F);
                target.setHealth(target.getMaxHealth() - 7.0F);
                if (differing) {
                    target.forceAddEffect(new MobEffectInstance(
                            MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false), null);
                }
                helper.runAfterDelay(119, () -> guard(fixtures, () -> verifyRewind(
                        helper, fixtures, historicalTick, historical, differing, failure)));
            }));
        });
    }

    private static void verifyRewind(
            GameTestHelper helper,
            Fixtures fixtures,
            long historicalTick,
            Effects historical,
            boolean differing,
            Failure failure) {
        FakePlayer target = fixtures.target();
        Effects inverse = Effects.capture(target);
        Tag inverseExtra = target.getEffect(MobEffects.CONFUSION).save();
        int[] rejected = {0};
        int[] speedAdded = {0};
        int[] absorptionAdded = {0};
        int[] healthBoostAdded = {0, 0, 0};
        Consumer<MobEffectEvent.Remove> blockRemoval = event -> {
            if (failure == Failure.REMOVE && event.getEntity() == target
                    && event.getEffectInstance() != null
                    && event.getEffectInstance().is(MobEffects.CONFUSION)) {
                rejected[0]++;
                event.setCanceled(true);
            }
        };
        Consumer<MobEffectEvent.Applicable> blockReplacement = event -> {
            if (failure == Failure.REPLACE && event.getEntity() == target
                    && event.getEffectInstance().is(MobEffects.MOVEMENT_SPEED)
                    && event.getEffectInstance().getAmplifier() == 4) {
                rejected[0]++;
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
            if (failure == Failure.CURRENT_EFFECT_VETO && event.getEntity() == target
                    && target.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                    && event.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                rejected[0]++;
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        };
        Consumer<MobEffectEvent.Added> observeAdded = event -> {
            if (event.getEntity() == target) {
                if (event.getEffectInstance().is(MobEffects.MOVEMENT_SPEED)) {
                    speedAdded[0]++;
                }
                if (event.getEffectInstance().is(MobEffects.ABSORPTION)) {
                    absorptionAdded[0]++;
                }
                if (event.getEffectInstance().is(MobEffects.HEALTH_BOOST)) {
                    Tag state = event.getEffectInstance().save();
                    int phase = state.equals(historical.healthBoost()) ? 0
                            : state.equals(inverse.healthBoost()) ? 1 : 2;
                    healthBoostAdded[phase]++;
                }
            }
        };
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobEffectEvent.Remove.class, blockRemoval);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobEffectEvent.Applicable.class, blockReplacement);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobEffectEvent.Added.class, observeAdded);
            helper.assertTrue(helper.getLevel().getGameTime() == historicalTick + 120L,
                    "The test did not reach the production six-second rewind target");
            helper.assertTrue(kept(target, target.getEffect(MobEffects.NIGHT_VISION)),
                    "The inverse fixture no longer has a genuinely protected passive");
            helper.assertTrue(historical.health() > inverse.maxHealth(),
                    "The fixture does not require HP restoration after a max-health cap increase");
            if (failure == Failure.CURRENT_EFFECT_VETO) {
                helper.assertTrue(target.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && !target.hasEffect(MobEffects.POISON),
                        "The current-only blocker/historical-missing-effect fixture was lost");
                helper.assertTrue(!target.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0))
                                && rejected[0] == 1 && !target.hasEffect(MobEffects.POISON),
                        "The actual Applicable listener did not veto a harmful addition while the blocker was present");
                rejected[0] = 0;
            }
            MandomRewindService.Result result = MandomRewindService.rewind(
                    helper.getLevel(), fixtures.user(), null);
            if (failure == Failure.CURRENT_EFFECT_VETO) {
                helper.assertTrue(result == MandomRewindService.Result.SUCCESS,
                        "Current-only applicability blocker prevented historical harmful restore: " + result
                                + "; denied rewind applications=" + rejected[0]);
                helper.assertTrue(rejected[0] == 0 && !target.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                        "Rewind applied historical harmful effects before removing the current-only blocker");
            }
            if (failure == Failure.NONE || failure == Failure.CURRENT_EFFECT_VETO) {
                helper.assertTrue(result == MandomRewindService.Result.SUCCESS,
                        "A protected passive rejected rewind: " + result);
                historical.assertOrdinary(helper, target);
                Tag expectedPassive = differing ? inverse.nightVision() : historical.nightVision();
                helper.assertTrue(expectedPassive.equals(target.getEffect(MobEffects.NIGHT_VISION).save()),
                        "Kept passive did not follow donor no-change merge semantics");
                helper.assertTrue(!target.hasEffect(MobEffects.CONFUSION),
                        "A removable non-historical ordinary effect survived rewind");
                helper.assertTrue(speedAdded[0] == 1 && absorptionAdded[0] == 1
                                && healthBoostAdded[0] == 1 && healthBoostAdded[1] == 0
                                && healthBoostAdded[2] == 0,
                        "Successful ordinary replacements did not publish exactly one Added notification each");
            }
            else {
                helper.assertTrue(result == MandomRewindService.Result.COMMIT_FAILED && rejected[0] > 0,
                        "A genuine API rejection was not reported as a failed transaction");
                inverse.assertOrdinary(helper, target);
                helper.assertTrue(inverse.nightVision().equals(target.getEffect(MobEffects.NIGHT_VISION).save()),
                        "Rollback changed the original protected passive");
                helper.assertTrue(target.hasEffect(MobEffects.CONFUSION)
                                && inverseExtra.equals(target.getEffect(MobEffects.CONFUSION).save()),
                        "Rollback lost the original ordinary effect that blocked removal");
                if (failure == Failure.REPLACE) {
                    helper.assertTrue(speedAdded[0] == 0,
                            "A denied ordinary replacement incorrectly published an Added notification");
                }
                helper.assertTrue(healthBoostAdded[2] == 0
                                && healthBoostAdded[0] == healthBoostAdded[1]
                                && healthBoostAdded[0] <= 1
                                && (failure != Failure.REMOVE || healthBoostAdded[0] == 0),
                        "Health Boost did not publish exactly one notification per applied forward/inverse replacement");
            }
            helper.assertTrue(!MandomRewindService.isApplying(), "Rewind leaked its application guard");
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(blockRemoval);
            NeoForge.EVENT_BUS.unregister(blockReplacement);
            NeoForge.EVENT_BUS.unregister(observeAdded);
            fixtures.close();
        }
    }

    private static boolean kept(FakePlayer target, MobEffectInstance effect) {
        return effect != null && (VampirismData.isKeptPassiveEffect(target, effect)
                || ZombieData.isKeptPassiveEffect(target, effect));
    }

    private static void guard(Fixtures fixtures, Runnable action) {
        try {
            action.run();
        }
        catch (RuntimeException | Error exception) {
            fixtures.close();
            throw exception;
        }
    }

    private enum Failure { NONE, REMOVE, REPLACE, CURRENT_EFFECT_VETO }

    private record Fixtures(Cow user, FakePlayer target) {
        private void close() {
            target.discard();
            user.discard();
            MandomRewindService.clear(user.getServer());
        }
    }

    private record Effects(
            Tag speed, Tag absorption, Tag healthBoost, Tag nightVision, Tag poison,
            float absorptionAmount, float health,
            double movementSpeed, double maxAbsorption, double maxHealth) {
        private static Effects capture(FakePlayer target) {
            return new Effects(
                    target.getEffect(MobEffects.MOVEMENT_SPEED).save(),
                    target.getEffect(MobEffects.ABSORPTION).save(),
                    target.getEffect(MobEffects.HEALTH_BOOST).save(),
                    target.getEffect(MobEffects.NIGHT_VISION).save(),
                    target.hasEffect(MobEffects.POISON) ? target.getEffect(MobEffects.POISON).save() : null,
                    target.getAbsorptionAmount(),
                    target.getHealth(),
                    target.getAttributeValue(Attributes.MOVEMENT_SPEED),
                    target.getAttributeValue(Attributes.MAX_ABSORPTION),
                    target.getAttributeValue(Attributes.MAX_HEALTH));
        }

        private void assertOrdinary(GameTestHelper helper, FakePlayer target) {
            helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SPEED)
                            && speed.equals(target.getEffect(MobEffects.MOVEMENT_SPEED).save()),
                    "Ordinary Speed flags, amplifier, duration or nested state changed");
            helper.assertTrue(target.hasEffect(MobEffects.ABSORPTION)
                            && absorption.equals(target.getEffect(MobEffects.ABSORPTION).save()),
                    "Ordinary Absorption flags, amplifier, duration or nested state changed");
            helper.assertTrue(target.hasEffect(MobEffects.HEALTH_BOOST)
                            && healthBoost.equals(target.getEffect(MobEffects.HEALTH_BOOST).save()),
                    "Ordinary Health Boost flags, amplifier, duration or nested state changed");
            helper.assertTrue(poison == null ? !target.hasEffect(MobEffects.POISON)
                            : target.hasEffect(MobEffects.POISON)
                                    && poison.equals(target.getEffect(MobEffects.POISON).save()),
                    "Historical harmful Poison state was not restored exactly");
            helper.assertTrue(Math.abs(target.getAttributeValue(Attributes.MOVEMENT_SPEED) - movementSpeed) < 0.000001D
                            && Math.abs(target.getAttributeValue(Attributes.MAX_ABSORPTION) - maxAbsorption) < 0.000001D
                            && Math.abs(target.getAttributeValue(Attributes.MAX_HEALTH) - maxHealth) < 0.000001D,
                    "Effect attribute modifiers did not match the snapshot");
            helper.assertTrue(Math.abs(target.getAbsorptionAmount() - absorptionAmount) < 0.000001F,
                    "Effect-start callbacks overwrote the snapshot absorption amount");
            helper.assertTrue(Math.abs(target.getHealth() - health) < 0.000001F,
                    "Effect callbacks or max-health clamping overwrote the snapshot final HP");
        }
    }
}
