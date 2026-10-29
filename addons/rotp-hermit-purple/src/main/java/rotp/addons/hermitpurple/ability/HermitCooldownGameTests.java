package rotp.addons.hermitpurple.ability;

import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional, resolveMultiplier), which a
 * Stand-bearing mob waited (+ 10) before its next pick and Resolve never shortened. The Hermit Purple registrations of
 * 061 InitStands.java: hp_vine_attack cooldown(10, 5, 0.5f) (:69) and hp_vine_shift cooldown(30, 35, 0.5f) (:70), whose
 * second part Resolve halves, and the plain cooldown(300), (200), (100) of cringe, doxx and the camera (:80, :82, :83).
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitCooldownGameTests {
    private HermitCooldownGameTests() {}

    private record Row(String ability, int technical, int additional, float resolveMultiplier) {}

    private static final List<Row> ROWS = List.of(
            new Row(HermitPurpleAddon.HP_VINE_ATTACK_NAME, 10, 5, 0.5F),
            new Row(HermitPurpleAddon.HP_VINE_SHIFT_NAME, 30, 35, 0.5F),
            new Row(HermitPurpleAddon.CRINGE_NAME, 0, 300, 0.0F),
            new Row(HermitPurpleAddon.HP_DOXX_NAME, 0, 200, 0.0F),
            new Row(HermitPurpleAddon.HP_CAMERA_NAME, 0, 100, 0.0F));

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_cooldown_split")
    public static void vineAttackAndShiftKeepTheDonorsTechnicalAndAdditionalCooldown(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            for (Row row : ROWS) {
                Ability ability = power.getAbility(row.ability());
                helper.assertTrue(ability != null, "setup: Hermit Purple has no " + row.ability());
                int technical = ability.getCooldownTechnical(power);
                int full = ability.getCooldown(power, -1);
                if (technical != row.technical() || full != row.technical() + row.additional()) {
                    wrong.add(row.ability() + " reports technical " + technical + " of a full " + full + ", the donor's "
                            + row.technical() + " of " + (row.technical() + row.additional()));
                }
            }
            user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
            for (Row row : ROWS) {
                int inResolve = power.getAbility(row.ability()).getCooldown(power, -1);
                int expected = row.technical() + (int) ((float) row.additional() * row.resolveMultiplier());
                if (inResolve != expected) {
                    wrong.add(row.ability() + " takes " + inResolve + " ticks in Resolve, the donor's " + expected);
                }
            }
        }
        finally {
            user.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Hermit Purple cooldown(technical, additional, multiplier): "
                + String.join("; ", wrong));
        LogUtils.getLogger().info("R770_HP_COOLDOWN_PASS {}", "vine_split");
        helper.succeed();
    }
}
