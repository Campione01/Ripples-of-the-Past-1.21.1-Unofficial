package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional): a Stand-bearing mob waited it
 * (+ 10) before its next pick, Resolve never shortened it (StandAction's multiplier 0 drops only the additional part) and
 * a Creative user kept it (Action.getCooldownAdditional was 0 for one). 053 InitStands :70-73 split the four gravity
 * cooldowns; the punch, the barrage, the heavy punch and Heart Inversion (:65-68) had none.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonCooldownTechnicalGameTests {
    private CMoonCooldownTechnicalGameTests() {}

    private record Row(String ability, int technical, int additional) {}

    private static final List<Row> ROWS = List.of(
            new Row(CMoonAddon.GRAVITY_COUNTER, 100, 75),
            new Row(CMoonAddon.CENTER_OF_GRAVITY, 150, 125),
            new Row(CMoonAddon.GRAVITY_SHIFT, 10, 5),
            new Row(CMoonAddon.DESTROY, 10, 10),
            new Row(CMoonAddon.PUNCH, 0, 0),
            new Row(CMoonAddon.BARRAGE, 0, 0),
            new Row(CMoonAddon.HEAVY_PUNCH, 0, 0),
            new Row(CMoonAddon.HEART_INVERSION, 0, 0));

    @GameTest(template = "empty", batch = "cmCooldownTechnical", timeoutTicks = 40)
    public static void theGravityAbilitiesKeepTheDonorsTechnicalAndAdditionalCooldown(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        GravityTestSupport.Actor actor = new GravityTestSupport.Actor(helper, false, false, false);
        try {
            for (Row row : ROWS) {
                Ability ability = actor.power.getAbility(row.ability());
                helper.assertTrue(ability != null, "Setup: C-Moon has no " + row.ability());
                int technical = ability.getCooldownTechnical(actor.power);
                int full = ability.getCooldown(actor.power, -1);
                if (technical != row.technical() || full != row.technical() + row.additional()) {
                    wrong.add(row.ability() + " reports technical " + technical + " of a full " + full + ", the donor's "
                            + row.technical() + " of " + (row.technical() + row.additional()));
                }
            }
            actor.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 1));
            helper.assertTrue(actor.user.hasEffect(ModStatusEffects.RESOLVE) && !actor.power.isUserCreative(),
                    "Setup: the user is not in Resolve outside Creative");
            for (Row row : ROWS) {
                int inResolve = actor.power.getAbility(row.ability()).getCooldown(actor.power, -1);
                if (inResolve != row.technical()) {
                    wrong.add(row.ability() + " takes " + inResolve + " ticks in Resolve, the donor's " + row.technical()
                            + " (multiplier 0 drops the additional part)");
                }
            }
            actor.user.removeEffect(ModStatusEffects.RESOLVE);
            actor.user.getAbilities().instabuild = true;
            helper.assertTrue(actor.power.isUserCreative() && !actor.user.hasEffect(ModStatusEffects.RESOLVE),
                    "Setup: the user is not in Creative outside Resolve");
            for (Row row : ROWS) {
                int inCreative = actor.power.getAbility(row.ability()).getCooldown(actor.power, -1);
                if (inCreative != row.technical()) {
                    wrong.add(row.ability() + " takes " + inCreative + " ticks in Creative, the donor's technical "
                            + row.technical());
                }
            }
        }
        finally {
            actor.close();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 C-Moon cooldown(technical, additional): " + String.join("; ", wrong));
        helper.succeed();
    }
}
