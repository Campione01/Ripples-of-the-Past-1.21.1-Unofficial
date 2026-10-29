package rotp.addons.stonefree.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional, resolveMultiplier), which a
 * Stand-bearing mob waited (+ 10) before its next pick and Resolve never shortened. The Stone Free registrations of
 * 017 InitStands.java:155-167: the three binds cooldown(24, 100, 0.5f), the whip cooldown(10, 20), the extended punch
 * cooldown(10, 100) and the pick cooldown(16, 100), the last three with StandAction's multiplier 0.
 */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeCooldownTechnicalGameTests {
    private StoneFreeCooldownTechnicalGameTests() {}

    private record Row(String ability, int technical, int additional, float resolveMultiplier) {}

    private static final List<Row> ROWS = List.of(
            new Row(StoneFreeAddon.STRING_CAPTURE_NAME, 24, 100, 0.5F),
            new Row(StoneFreeAddon.STRING_BIND_NAME, 24, 100, 0.5F),
            new Row(StoneFreeAddon.USER_STRING_BIND_NAME, 24, 100, 0.5F),
            new Row(StoneFreeAddon.USER_STRING_WHIP_NAME, 10, 20, 0.0F),
            new Row(StoneFreeAddon.EXTENDED_PUNCH_NAME, 10, 100, 0.0F),
            new Row(StoneFreeAddon.USER_STRING_PICK_NAME, 16, 100, 0.0F),
            new Row("punch", 0, 0, 0.0F),
            new Row("barrage", 0, 0, 0.0F),
            new Row(StoneFreeAddon.USER_STRING_ATTACK_NAME, 0, 0, 0.0F),
            new Row(StoneFreeAddon.USER_STRING_SWEEP_NAME, 0, 0, 0.0F));

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 40)
    public static void theStringsKeepTheDonorsTechnicalAndAdditionalCooldown(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(StoneFreeAddon.STONE_FREE.get());
            for (Row row : ROWS) {
                Ability ability = power.getAbility(row.ability());
                helper.assertTrue(ability != null, "setup: Stone Free has no " + row.ability());
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
        helper.assertTrue(wrong.isEmpty(), "1.16 Stone Free cooldown(technical, additional, multiplier): "
                + String.join("; ", wrong));
        helper.succeed();
    }
}
