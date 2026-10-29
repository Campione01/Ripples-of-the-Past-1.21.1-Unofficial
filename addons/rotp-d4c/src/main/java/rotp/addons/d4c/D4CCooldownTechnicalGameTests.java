package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional, resolveMultiplier), which a
 * Stand-bearing mob waited (+ 10) before its next pick and Resolve never shortened. Every D4C registration of 015
 * ai/ah.java:71-88 with a cooldown is cooldown(technical, additional, 0.0f). The light wall's class (aa/ag.java:82)
 * overrode getCooldown with 200 - ticksHeld * 30, so a click set 230 (Resolve and Creative included) instead of the
 * registered 300, and the deactivation's (aa/ah.java:71) 0.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CCooldownTechnicalGameTests {
    private static final String LIGHT_WALL = "love_train_light_wall";
    private static final int LIGHT_WALL_CLICK_COOLDOWN = 230;

    private D4CCooldownTechnicalGameTests() {}

    private record Row(String ability, int technical, int additional) {}

    private static final List<Row> ROWS = List.of(
            new Row("d4c_rapid_punch", 40, 80),
            new Row("summon_player_minion", 100, 200),
            new Row("annihilation_mark", 10, 20),
            new Row("annihilation", 120, 360),
            new Row("parallel_world_travel", 50, 100),
            new Row("flag_parallel_world_travel", 80, 250),
            new Row("dimension_selector", 20, 40),
            new Row("swap_with_minion", 80, 160),
            new Row("love_train", 200, 400),
            new Row("love_train_deactivate", 20, 40),
            new Row(LIGHT_WALL, 200, 100),
            new Row("minion_ai_control", 20, 40),
            new Row("pullin_parallel_world_travel", 80, 160),
            new Row("summon_kick", 60, 120),
            new Row("d4c_item_throw", 20, 40),
            new Row("d4c_hand_chop", 0, 0),
            new Row("love_train_light_wall_deactivate", 0, 0));

    // what getCooldown(power, -1) gave where the donor's class replaced the registered total
    private static final Map<String, Integer> DONOR_CLICK_COOLDOWN = Map.of(LIGHT_WALL, LIGHT_WALL_CLICK_COOLDOWN);

    private static int donorFull(Row row) {
        return DONOR_CLICK_COOLDOWN.getOrDefault(row.ability(), row.technical() + row.additional());
    }

    private static int donorInResolve(Row row) {
        return DONOR_CLICK_COOLDOWN.getOrDefault(row.ability(), row.technical());
    }

    @GameTest(template = "empty", batch = "d4cCooldownTechnical", timeoutTicks = 40)
    public static void everyD4CCooldownKeepsTheDonorsTechnicalAndAdditionalPart(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-cooldown-split")) {
            helper.assertTrue(!scene.power.isUserCreative(), "setup: the D4C user is in Creative");
            for (Row row : ROWS) {
                Ability ability = scene.power.getAbility(row.ability());
                helper.assertTrue(ability != null, "setup: D4C has no " + row.ability());
                int technical = ability.getCooldownTechnical(scene.power);
                int full = ability.getCooldown(scene.power, -1);
                if (technical != row.technical() || full != donorFull(row)) {
                    wrong.add(row.ability() + " reports technical " + technical + " of a full " + full + ", the donor's "
                            + row.technical() + " of " + donorFull(row));
                }
            }
            Ability lightWall = scene.power.getAbility(LIGHT_WALL);
            int held = lightWall.getCooldown(scene.power, 2);
            if (held != 200 - 2 * 30) {
                wrong.add(LIGHT_WALL + " takes " + held + " ticks after 2 held ticks, the donor's 200 - ticksHeld * 30 = 140");
            }
            scene.owner.getAbilities().instabuild = true;
            int creative = lightWall.getCooldown(scene.power, -1);
            scene.owner.getAbilities().instabuild = false;
            if (creative != LIGHT_WALL_CLICK_COOLDOWN) {
                wrong.add(LIGHT_WALL + " takes " + creative + " ticks in Creative, the donor's getCooldown override"
                        + " ignored Creative: " + LIGHT_WALL_CLICK_COOLDOWN);
            }
            scene.owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
            int noCooldown = lightWall.getCooldown(scene.power, -1);
            scene.owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = false;
            if (noCooldown != 0) {
                wrong.add(LIGHT_WALL + " takes " + noCooldown + " ticks with the no-cooldown setting on, the port's"
                        + " setting removes every cooldown");
            }
            scene.owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
            for (Row row : ROWS) {
                int inResolve = scene.power.getAbility(row.ability()).getCooldown(scene.power, -1);
                if (inResolve != donorInResolve(row)) {
                    wrong.add(row.ability() + " takes " + inResolve + " ticks in Resolve, the donor's " + donorInResolve(row)
                            + " (multiplier 0.0f drops the additional part)");
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 D4C cooldown(technical, additional, 0.0f): " + String.join("; ", wrong));
        helper.succeed();
    }
}
