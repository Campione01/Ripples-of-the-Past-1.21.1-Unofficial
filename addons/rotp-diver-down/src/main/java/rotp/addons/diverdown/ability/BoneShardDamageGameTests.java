package rotp.addons.diverdown.ability;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoneShardDamageGameTests {
    private BoneShardDamageGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 40)
    public static void rejectedHitKeepsBoneBombForAcceptedHit(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> rejectedHitKeepsBoneBombForAcceptedHit(helper, fixture(helper, placement)));
    }

    private static void rejectedHitKeepsBoneBombForAcceptedHit(GameTestHelper helper, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                arm(helper, fixture);
                // Keep the ordinary hurt window open across the remaining windup.
                helper.runAfterDelay(BoneShardBombAbility.WINDUP_TICKS - 2, () -> {
                    try {
                        helper.assertTrue(!fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT),
                                "Bomb armed before the configured windup ended");
                        helper.assertTrue(fixture.target.hurt(helper.getLevel().damageSources().generic(), 4.0F),
                                "Initial unmarked damage was rejected");
                        helper.runAfterDelay(4, () -> {
                            try {
                                assertArmed(helper, fixture);
                                helper.assertTrue(!fixture.target.hurt(helper.getLevel().damageSources().generic(), 4.0F),
                                        "Bone bomb bypassed the ordinary hurt window");
                                helper.assertTrue(fixture.target.getHealth() == 96.0F
                                                && fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT)
                                                && shards(helper, fixture) == 0,
                                        "Rejected damage consumed or detonated the bone bomb");
                                helper.runAfterDelay(12, () -> {
                                    try {
                                        helper.assertTrue(fixture.target.hurt(helper.getLevel().damageSources().generic(), 4.0F),
                                                "Damage after the hurt window was rejected");
                                        helper.assertTrue(Math.abs(fixture.target.getHealth() - 88.0F) < 1.0E-5F
                                                        && !fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT)
                                                        && shards(helper, fixture) >= 4,
                                                "Accepted hit did not double damage and detonate once");
                                        helper.succeed();
                                    } finally {
                                        cleanup(helper, fixture);
                                    }
                                });
                            } catch (Throwable error) {
                                cleanup(helper, fixture);
                                throw error;
                            }
                        });
                    } catch (Throwable error) {
                        cleanup(helper, fixture);
                        throw error;
                    }
                });
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 20)
    public static void boneBombDoublesBeforeArmorReduction(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> boneBombDoublesBeforeArmorReduction(helper, fixture(helper, placement)));
    }

    private static void boneBombDoublesBeforeArmorReduction(GameTestHelper helper, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                fixture.target.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
                fixture.target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0D);
                arm(helper, fixture);
                helper.runAfterDelay(BoneShardBombAbility.WINDUP_TICKS + 2, () -> {
                    try {
                        assertArmed(helper, fixture);
                        helper.assertTrue(fixture.target.hurt(helper.getLevel().damageSources().mobAttack(fixture.owner), 4.0F),
                                "Armored marked damage was rejected");
                        helper.assertTrue(Math.abs(fixture.target.getHealth() - 97.76F) < 1.0E-4F,
                                "Bone damage was doubled after armor instead of before it");
                        helper.assertTrue(!fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT)
                                        && shards(helper, fixture) >= 4,
                                "Armored accepted hit did not consume the bomb");
                        helper.succeed();
                    } finally {
                        cleanup(helper, fixture);
                    }
                });
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 20)
    public static void boneBombRefusesMissingArms(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> boneBombRefusesMissingArms(helper, fixture(helper, placement)));
    }

    private static void boneBombRefusesMissingArms(GameTestHelper helper, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                var power = PowerClass.STAND.get(fixture.owner);
                var instance = power.getStandInstance().orElseThrow();
                var ability = power.getAbility(DiverDownAddon.BONE_SHARD_BOMB_NAME);
                helper.assertTrue(ability instanceof BoneShardBombAbility, "Missing bone-bomb ability");
                helper.assertTrue(fixture.stand.setEmbeddedTarget(fixture.target),
                        "Could not embed Diver Down in the target");
                helper.assertTrue(instance.removePart(StandPart.ARMS), "Caster fixture had no arms to remove");
                helper.assertTrue(!ability.checkConditions(power).isPositive(),
                        "Production bone-bomb conditions accepted missing arms");
                helper.assertTrue(instance.addPart(StandPart.ARMS), "Could not restore caster arms");
                helper.assertTrue(ability.checkConditions(power).isPositive(),
                        "Restoring arms did not restore production cast eligibility");
                float staminaBefore = power.getStamina();
                helper.assertTrue(staminaBefore == power.getMaxStamina()
                                && !DiverDownAuthority.ownerHasAuthority(fixture.owner),
                        "Caster fixture did not start at maximum stamina without authority");
                arm(helper, fixture);
                helper.assertTrue(instance.removePart(StandPart.ARMS), "Could not remove arms during windup");
                helper.runAfterDelay(BoneShardBombAbility.WINDUP_TICKS + 2, () -> {
                    try {
                        helper.assertTrue(!fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT)
                                        && !DiverDownAuthority.ownerHasAuthority(fixture.owner)
                                        && power.getStamina() == staminaBefore
                                        && shards(helper, fixture) == 0,
                                "Missing arms during windup armed, authorized, charged, or detonated the bomb");
                        helper.succeed();
                    } finally {
                        cleanup(helper, fixture);
                    }
                });
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    // 1.16 diver_down_bone_shard_bomb was built with standPose(StandPose.HEAVY_ATTACK): the heavy punch clip.
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 20)
    public static void boneBombUsesTheHeavyAttackPose(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> boneBombUsesTheHeavyAttackPose(helper, fixture(helper, placement)));
    }

    private static void boneBombUsesTheHeavyAttackPose(GameTestHelper helper, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                var ability = PowerClass.STAND.get(fixture.owner).getAbility(DiverDownAddon.BONE_SHARD_BOMB_NAME);
                helper.assertTrue(ability instanceof BoneShardBombAbility, "Missing bone-bomb ability");
                BoneShardBombAbility bomb = (BoneShardBombAbility) ability;
                ActionAnimIdentifier pose = bomb.getEntityAnim(bomb.createActionObj());
                helper.assertTrue(pose == ActionAnimIdentifier.getOrCreate("heavy_punch", false),
                        "1.16 used StandPose.HEAVY_ATTACK for the bone shard bomb, the port plays " + pose);
                helper.succeed();
            } finally {
                cleanup(helper, fixture);
            }
        });
    }

    // The owner and Stand clean-ups discard shards while they look through every entity of the level. Enough shards
    // make the level's id map shrink during that walk, as the bone bomb's 4 to 15 shards did now and then among the
    // other tests' entities ("Index 48 out of bounds for length 33").
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 20)
    public static void cleanupsDiscardEveryShardWhileTheLevelShrinks(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> cleanupsDiscardEveryShardWhileTheLevelShrinks(helper, fixture(helper, placement)));
    }

    private static void cleanupsDiscardEveryShardWhileTheLevelShrinks(GameTestHelper helper, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                spawnShards(helper, fixture, CLEANUP_SHARDS);
                DiverDownAuthority.clearOwnedBy(helper.getLevel().getServer(), fixture.owner.getUUID());
                int left = shards(helper, fixture);
                helper.assertTrue(left == 0, "The owner clean-up left " + left + " of " + CLEANUP_SHARDS + " shards");
                spawnShards(helper, fixture, CLEANUP_SHARDS);
                DiverDownAuthority.clearForStand(helper.getLevel(), fixture.stand.getUUID());
                left = shards(helper, fixture);
                helper.assertTrue(left == 0, "The Stand clean-up left " + left + " of " + CLEANUP_SHARDS + " shards");
                helper.succeed();
            } finally {
                cleanup(helper, fixture);
            }
        });
    }

    private static final int CLEANUP_SHARDS = 400;

    private static void spawnShards(GameTestHelper helper, Fixture fixture, int count) {
        for (int index = 0; index < count; index++) {
            BoneShardEntity shard = new BoneShardEntity(fixture.target, fixture.owner, fixture.stand, helper.getLevel());
            shard.setPos(fixture.target.getEyePosition());
            helper.getLevel().addFreshEntity(shard);
        }
        int spawned = shards(helper, fixture);
        helper.assertTrue(spawned == count, "Spawned " + spawned + " of " + count + " bound shards");
    }

    private static Fixture fixture(GameTestHelper helper, GameTestPlacement placement) {
        Cow owner = EntityType.COW.create(helper.getLevel());
        Cow target = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(owner != null && target != null, "Missing caster/target fixtures");
        owner.setNoAi(true);
        owner.setNoGravity(true);
        target.setNoAi(true);
        target.setNoGravity(true);
        owner.setPos(placement.owner);
        target.setPos(placement.target);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        target.setHealth(100.0F);
        helper.getLevel().addFreshEntity(owner);
        helper.getLevel().addFreshEntity(target);
        try {
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power),
                    "Could not summon the Diver Down caster");
            var stand = (DiverDownStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Summon did not yield the Diver Down controller");
            return new Fixture(owner, target, stand);
        } catch (Throwable error) {
            cleanup(helper, new Fixture(owner, target, null));
            throw error;
        }
    }

    private static void assertTicks(GameTestHelper helper, Fixture fixture) {
        GameTestPlacement.assertTicks(helper, "owner", fixture.owner);
        GameTestPlacement.assertTicks(helper, "target", fixture.target);
        GameTestPlacement.assertTicks(helper, "Stand", fixture.stand);
    }

    private static void arm(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.stand.setEmbeddedTarget(fixture.target),
                "Could not embed Diver Down in the target");
        var ability = PowerClass.STAND.get(fixture.owner).getAbility(DiverDownAddon.BONE_SHARD_BOMB_NAME);
        helper.assertTrue(ability instanceof BoneShardBombAbility, "Missing bone-bomb ability");
        helper.assertTrue(ability.checkConditions(PowerClass.STAND.get(fixture.owner)).isPositive(),
                "Production bone-bomb conditions refused the caster fixture");
        BufferingState buffering = BufferingState.clickOnly();
        ability.onKeyPress(helper.getLevel(), fixture.owner, null,
                InputMethod.CLICK, 0.0F, buffering);
        var action = LivingComponentAction.getCurEntityAction(fixture.stand);
        helper.assertTrue(buffering.isActionSuccess
                        && action instanceof BoneShardBombAbility.BoneShardBombAction
                        && action.getPhase() == ActionPhase.WINDUP
                        && !fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT),
                "Public cast did not enter the production windup");
    }

    private static void assertArmed(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.target.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT)
                        && !fixture.stand.isEmbedded(), "Production cast did not arm and retract");
    }

    private static int shards(GameTestHelper helper, Fixture fixture) {
        return helper.getLevel().getEntitiesOfClass(BoneShardEntity.class,
                fixture.owner.getBoundingBox().inflate(16.0D),
                shard -> fixture.owner.getUUID().equals(shard.getControllerUuid())).size();
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        DiverDownAuthority.clearOwnedBy(helper.getLevel().getServer(), fixture.owner.getUUID());
        var power = PowerClass.STAND.get(fixture.owner);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(fixture.owner, power);
        }
        fixture.target.discard();
        fixture.owner.discard();
    }

    private record Fixture(Cow owner, Cow target, DiverDownStandEntity stand) {}
}
