package rotp.addons.thehand.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.thehand.TheHandAddon;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

/**
 * 1.16 the_hand_erasure_barrage (017 InitStands.java:131 cooldown(43)) overrode getCooldownAdditional
 * (TheHandErasureBarrage.java:207-210): ticksHeld < 5 returned 24 outright, so a tap kept 24 ticks in Resolve and in
 * Creative; a longer hold scaled super's value (43, 0 in Resolve by StandAction's multiplier 0 and 0 in Creative by
 * Action.getCooldownAdditional :373-375) by the ticks held over the barrage's length. No technical part (:369-371).
 */
@GameTestHolder(TheHandAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheHandCooldownGameTests {
    private TheHandCooldownGameTests() {}

    @GameTest(
            template = "empty",
            batch = "the_hand_cooldown",
            timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void theErasureBarrageTapKeepsItsCooldownInCreativeAsIn116(GameTestHelper helper) {
        TheHandGameTests.TestContext context = TheHandGameTests.prepareTheHand(helper);
        if (context == null) {
            return;
        }
        List<String> wrong = new ArrayList<>();
        try {
            context.owner().setGameMode(GameType.SURVIVAL);
            Ability barrage = context.power().getAbility(TheHandAddon.ERASURE_BARRAGE_NAME);
            helper.assertTrue(barrage instanceof TheHandErasureBarrageAbility,
                    "Setup: The Hand has no erasure barrage: " + barrage);
            int full = Math.max(StandStatFormulas.getBarrageMaxDuration(
                    context.stand().getDurability() * 0.3D), 1);
            int half = full / 2;
            int technical = barrage.getCooldownTechnical(context.power());
            if (technical != 0) {
                wrong.add("the erasure barrage reports technical " + technical + ", the donor's 0");
            }
            check(context, barrage, "in Survival", 24, 43 * half / full, 43, wrong);
            context.owner().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
            check(context, barrage, "in Resolve", 24, 0, 0, wrong);
            context.owner().setGameMode(GameType.CREATIVE);
            helper.assertTrue(context.power().isUserCreative(), "Setup: The Hand's user is not in Creative");
            check(context, barrage, "as a Creative user in Resolve", 24, 0, 0, wrong);
            context.owner().removeEffect(ModStatusEffects.RESOLVE);
            check(context, barrage, "as a Creative user", 24, 0, 0, wrong);
            context.owner().getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .noStandAbilityCooldown = true;
            check(context, barrage, "with the port's no-cooldown setting", 0, 0, 0, wrong);
            context.owner().getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .noStandAbilityCooldown = false;
            if (wrong.isEmpty()) {
                // the tap's cooldown as the hold ends, through the ability's own setter
                context.power().setAbilityCooldown(barrage.name(), 0);
                barrage.setCooldownOnUse(context.power(), 2);
                int set = context.power().getAbilityCooldown(barrage.name());
                if (set != 24) {
                    wrong.add("a Creative tap set " + set + " ticks, the donor's 24");
                }
            }
        } finally {
            TheHandGameTests.cleanup(context);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 TheHandErasureBarrage.getCooldownAdditional: " + String.join("; ", wrong));
        helper.succeed();
    }

    private static void check(TheHandGameTests.TestContext context, Ability barrage, String when,
            int tap, int half, int full, List<String> wrong) {
        int maximum = Math.max(StandStatFormulas.getBarrageMaxDuration(
                context.stand().getDurability() * 0.3D), 1);
        int tapActual = barrage.getCooldown(context.power(), 4);
        int halfActual = barrage.getCooldown(context.power(), maximum / 2);
        int fullActual = barrage.getCooldown(context.power(), maximum);
        if (tapActual != tap || halfActual != half || fullActual != full) {
            wrong.add(when + ": tap " + tapActual + ", half hold " + halfActual + ", full hold " + fullActual
                    + ", the donor's " + tap + " / " + half + " / " + full);
        }
    }
}
