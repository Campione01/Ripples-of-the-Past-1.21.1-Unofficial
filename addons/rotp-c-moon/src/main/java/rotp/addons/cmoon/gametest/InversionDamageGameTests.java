package rotp.addons.cmoon.gametest;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.CMoonDamageTypes;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InversionDamageGameTests {
    private static final float HEALTH = 200.0F;

    private InversionDamageGameTests() {}

    @GameTest(template = "inversion_fixture", timeoutTicks = 40)
    public static void inversionHasNeutralProtectionTags(GameTestHelper helper) {
        DamageSource source = DamageUtil.make(helper.getLevel(), CMoonDamageTypes.INVERSION);
        helper.assertTrue(source.is(CMoonDamageTypes.INVERSION), "Wrong inversion damage type");
        helper.assertTrue(!source.is(DamageTypeTags.BYPASSES_ARMOR), "Inversion bypasses armor");
        helper.assertTrue(!source.is(DamageTypeTags.BYPASSES_SHIELD), "Inversion bypasses shield rules");
        helper.assertTrue(!source.is(DamageTypeTags.WITCH_RESISTANT_TO), "Inversion uses witch magic resistance");
        helper.assertTrue(!source.is(DamageTypeTags.BYPASSES_EFFECTS), "Inversion bypasses effects");
        helper.assertTrue(!source.is(DamageTypeTags.BYPASSES_COOLDOWN), "Inversion bypasses damage cooldown");
        helper.assertTrue(source.is(DamageTypeTags.NO_KNOCKBACK), "Inversion lost its no-knockback behavior");
        helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null,
                "Inversion acquired an attacker");
        helper.assertTrue("magic".equals(source.type().msgId()), "Existing death caption changed");
        helper.succeed();
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 40)
    public static void inversionStillRespectsArmor(GameTestHelper helper) {
        Cow plain = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        Cow armored = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        prepare(plain, 0.0D);
        prepare(armored, 20.0D);
        applyInversion(helper, plain);
        applyInversion(helper, armored);
        var logger = com.mojang.logging.LogUtils.getLogger();
        java.util.function.Function<Cow, String> diagnosticState = cow -> {
            MobEffectInstance inversion = cow.getEffect(CMoonAddon.CM_INVERSION);
            return "pos=" + cow.position()
                    + ", chunk=" + cow.chunkPosition()
                    + ", ticks=" + cow.tickCount
                    + ", entityTicking=" + helper.getLevel().isPositionEntityTicking(cow.blockPosition())
                    + ", duration=" + (inversion == null ? "absent" : inversion.getDuration())
                    + ", armor=" + cow.getAttributeValue(Attributes.ARMOR)
                    + ", health=" + cow.getHealth()
                    + ", invulnerableTime=" + cow.invulnerableTime;
        };
        logger.info("C-Moon armor control START: plain=[{}], armored=[{}]",
                diagnosticState.apply(plain), diagnosticState.apply(armored));
        helper.runAfterDelay(2, () -> {
            try {
                logger.info("C-Moon armor control ASSERT: plain=[{}], armored=[{}]",
                        diagnosticState.apply(plain), diagnosticState.apply(armored));
                assertDamage(helper, plain, 2.5F, "Unarmored control");
                assertDamage(helper, armored, 0.625F, "Armor20 control");
                helper.succeed();
            }
            finally {
                plain.discard();
                armored.discard();
            }
        });
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 40)
    public static void inversionDoesNotBecomeWitchResistedMagic(GameTestHelper helper) {
        Witch witch = helper.spawn(EntityType.WITCH, new BlockPos(1, 2, 1));
        prepare(witch, 0.0D);
        applyInversion(helper, witch);
        helper.runAfterDelay(2, () -> {
            try {
                assertDamage(helper, witch, 2.5F, "Unflagged witch control");
                helper.succeed();
            }
            finally {
                witch.discard();
            }
        });
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 40)
    public static void registeredInversionPreservesGroundedVelocity(GameTestHelper helper) {
        BlockPos firstFloor = new BlockPos(1, 1, 1);
        BlockPos secondFloor = new BlockPos(3, 1, 1);
        var firstState = helper.getBlockState(firstFloor);
        var secondState = helper.getBlockState(secondFloor);
        Cow stationary = null;
        Cow moving = null;
        try {
            helper.setBlock(firstFloor, Blocks.STONE);
            helper.setBlock(secondFloor, Blocks.STONE);
            stationary = helper.spawn(EntityType.COW, firstFloor.above());
            moving = helper.spawn(EntityType.COW, secondFloor.above());
            prepareGrounded(stationary);
            prepareGrounded(moving);
            Vec3 impulse = new Vec3(0.125D, 0.05D, -0.1D);
            stationary.setDeltaMovement(Vec3.ZERO);
            moving.setDeltaMovement(impulse);
            tickRegisteredInversion(helper, stationary);
            tickRegisteredInversion(helper, moving);
            assertDamage(helper, stationary, 2.5F, "Grounded inversion");
            assertDamage(helper, moving, 2.5F, "Inversion with an existing impulse");
            helper.assertTrue(stationary.getDeltaMovement().equals(Vec3.ZERO),
                    "Accepted inversion added a random horizontal shove or upward hop");
            helper.assertTrue(moving.getDeltaMovement().equals(impulse),
                    "Inversion changed an existing impulse instead of suppressing only its own knockback");
            helper.succeed();
        } finally {
            if (stationary != null) stationary.discard();
            if (moving != null) moving.discard();
            helper.setBlock(firstFloor, firstState);
            helper.setBlock(secondFloor, secondState);
        }
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 40)
    public static void ordinaryHitStillKnocksBackGroundedVictim(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        var original = helper.getBlockState(floor);
        Cow target = null;
        Cow attacker = null;
        try {
            helper.setBlock(floor, Blocks.STONE);
            target = helper.spawn(EntityType.COW, floor.above());
            attacker = helper.spawn(EntityType.COW, new BlockPos(1, 2, 3));
            prepareGrounded(target);
            target.setDeltaMovement(Vec3.ZERO);
            DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
            helper.assertTrue(!source.is(DamageTypeTags.NO_KNOCKBACK),
                    "The inversion-only tag also disabled ordinary mob-hit knockback");
            helper.assertTrue(target.hurt(source, 2.5F), "Ordinary positive-control hit was rejected");
            assertDamage(helper, target, 2.5F, "Ordinary hit control");
            Vec3 movement = target.getDeltaMovement();
            helper.assertTrue(movement.horizontalDistanceSqr() > 0.01D && movement.y > 0.1D,
                    "The grounded ordinary-hit fixture did not actually receive knockback and a hop");
            helper.succeed();
        } finally {
            if (target != null) target.discard();
            if (attacker != null) attacker.discard();
            helper.setBlock(floor, original);
        }
    }

    private static void prepareGrounded(Cow cow) {
        prepare(cow, 0.0D);
        cow.setNoAi(false);
        cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
        cow.setOnGround(true);
    }

    private static void tickRegisteredInversion(GameTestHelper helper, Cow cow) {
        helper.assertTrue(cow.tickCount % 3 / 2 == 0, "Fresh fixture missed the production damage cadence");
        applyInversion(helper, cow);
        // Observe the registered tick's hurt reaction immediately, before travel can alter velocity.
        helper.assertTrue(CMoonAddon.CM_INVERSION.get().applyEffectTick(cow, 3),
                "Registered inversion tick stopped unexpectedly");
    }

    private static void prepare(Mob mob, double armor) {
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        mob.setHealth(HEALTH);
    }

    private static void applyInversion(GameTestHelper helper, Mob mob) {
        helper.assertTrue(mob.addEffect(new MobEffectInstance(CMoonAddon.CM_INVERSION, 8, 3)),
                "Registered inversion effect was rejected");
    }

    private static void assertDamage(GameTestHelper helper, Mob mob, float expected, String label) {
        float actual = HEALTH - mob.getHealth();
        helper.assertTrue(Math.abs(actual - expected) < 0.001F,
                label + ": expected " + expected + " damage, got " + actual);
    }
}
