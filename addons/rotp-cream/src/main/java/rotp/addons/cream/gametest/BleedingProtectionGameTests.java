package rotp.addons.cream.gametest;

import rotp.addons.cream.CreamAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Witch;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_cream_bleeding")
@PrefixGameTestTemplate(false)
public final class BleedingProtectionGameTests {
    private BleedingProtectionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bleedingUsesDonorMagicResistance(GameTestHelper helper) {
        Witch witch = helper.spawn(EntityType.WITCH, new BlockPos(1, 2, 1));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        prepare(witch, 0.0D);
        prepare(cow, 0.0D);
        bleed(helper, witch);
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, witch, 199.925F, "Witch magic resistance");
                assertHealth(helper, cow, 200.0F - 0.5F, "Unprotected control");
                helper.succeed();
            }
            finally {
                witch.discard();
                cow.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bleedingRetainsArmorBypass(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        prepare(cow, 20.0D);
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, cow, 200.0F - 0.5F, "Armor20 bypass");
                helper.succeed();
            }
            finally { cow.discard(); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bleedingRetainsResistanceProtection(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        prepare(cow, 0.0D);
        cow.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0));
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, cow, 199.6F, "Resistance0 control");
                helper.succeed();
            }
            finally { cow.discard(); }
        });
    }

    private static void prepare(Mob mob, double armor) {
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        mob.setHealth(200.0F);
    }

    private static void bleed(GameTestHelper helper, Mob mob) {
        helper.assertTrue(mob.addEffect(new MobEffectInstance(CreamAddon.BLEEDING, 40, 1)),
                "Registered bleeding effect rejected");
    }

    private static void assertHealth(GameTestHelper helper, Mob mob, float expected, String label) {
        helper.assertTrue(Math.abs(mob.getHealth() - expected) < 0.001F,
                label + ": expected " + expected + ", got " + mob.getHealth());
    }
}
