package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonDonorGameplaySpec;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.JojoModConfig;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 King Crimson (062 init/InitStands.java:71-84) registered every cooldown as a plain StandAction cooldown(n), the
 * KCConfig value: barrage and doppio barrage 60, chop, impale, desperate eyejab, time skip, time erase, epitaph and both
 * projectile throws their config; no class overrode a cooldown getter. So the technical part was 0 (Action :369-371),
 * Resolve's multiplier 0 emptied each one (StandAction.getCooldownAdditional :181-188) and Creative too (Action
 * :373-375), while the core's standStamina setting did not touch any of them. The time erase's dynamic cooldown was
 * computed into an unused local (KingCrimsonTimeErase.onTaskStopped :245-252), so every erase, short or full, set the
 * builder's timeEraseCooldown (200).
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonCooldownSplitGameTests {
    private static final int SHORT_ERASE_TICKS = 30;
    private static final int REAL_ERASE_TICKS = 10;

    private KingCrimsonCooldownSplitGameTests() {}

    private record Row(String ability, int ticksHeld, int survival) {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cooldown_split")
    public static void everyKingCrimsonCooldownIsAPlainDonorCooldownAsIn116(GameTestHelper helper) {
        FakePlayer player = newPlayer(helper, "kc-cooldown-split");
        List<String> wrong = new ArrayList<>();
        try {
            StandPower power = kingCrimson(player);
            List<Row> rows = rows(helper);
            for (Row row : rows) {
                Ability ability = power.getAbility(row.ability());
                helper.assertTrue(ability != null, "Setup: King Crimson has no " + row.ability());
                helper.assertTrue(row.survival() > 0, "Setup: " + row.ability() + " has no configured cooldown");
                int technical = ability.getCooldownTechnical(power);
                if (technical != 0) {
                    wrong.add(row.ability() + " reports technical " + technical + ", the donor's 0");
                }
            }
            collect(power, rows, "in Survival", false, wrong);
            player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
            collect(power, rows, "in Resolve", true, wrong);
            player.removeEffect(ModStatusEffects.RESOLVE);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(power.isUserCreative(), "Setup: the King Crimson user is not in Creative");
            collect(power, rows, "as a Creative user", true, wrong);
        } finally {
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 King Crimson plain cooldown(n): " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 Action.getCooldownAdditional skipped only a Creative user (:373-375), never infinite stamina
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cooldown_stamina_off")
    public static void everyKingCrimsonCooldownStaysWithStandStaminaOffAsIn116(GameTestHelper helper) {
        FakePlayer player = newPlayer(helper, "kc-cooldown-stamina");
        ModConfigSpec.BooleanValue staminaSetting = (ModConfigSpec.BooleanValue)
                JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", "standStamina"));
        boolean oldStamina = staminaSetting.get();
        List<String> wrong = new ArrayList<>();
        try {
            StandPower power = kingCrimson(player);
            List<Row> rows = rows(helper);
            staminaSetting.set(false);
            helper.assertTrue(!JojoModConfig.getCommonConfigInstance(false).standStamina.get()
                            && power.isStaminaInfinite() && !power.isUserCreative(),
                    "Setup: Stand stamina is not switched off for a Survival user");
            collect(power, rows, "with Stand stamina off", false, wrong);
        } finally {
            staminaSetting.set(oldStamina);
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 King Crimson cooldowns with Stand stamina off: "
                + String.join("; ", wrong));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "kc_cooldown_real_erase")
    public static void aShortTimeEraseSetsTheFixedTimeEraseCooldownAsIn116(GameTestHelper helper) {
        FakePlayer player = newPlayer(helper, "kc-cooldown-erase");
        StandPower power = kingCrimson(player);
        helper.assertTrue(KingCrimsonConfig.timeEraseCooldownTicks()
                        == KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS,
                "Setup: timeEraseCooldown is not its default");
        helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(player), "Setup: the time erase did not start");
        helper.runAfterDelay(REAL_ERASE_TICKS, () -> {
            int cooldown;
            try {
                KingCrimsonTemporalStateService.terminate(player);
                cooldown = power.getAbilityCooldown("kingcrimson_timeerase");
            } finally {
                KingCrimsonTemporalStateService.terminate(player);
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            }
            helper.assertTrue(cooldown == KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS,
                    "1.16 set the builder's timeEraseCooldown after a " + REAL_ERASE_TICKS + "-tick erase: "
                            + cooldown + " ticks, the donor's " + KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS);
            helper.succeed();
        });
    }

    private static FakePlayer newPlayer(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static StandPower kingCrimson(FakePlayer player) {
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        return power;
    }

    private static List<Row> rows(GameTestHelper helper) {
        int erase = KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS;
        helper.assertTrue(KingCrimsonConfig.timeEraseCooldownTicks() == erase,
                "Setup: timeEraseCooldown is not its default " + erase);
        return List.of(
                new Row("barrage", -1, KingCrimsonDonorGameplaySpec.BARRAGE_COOLDOWN_TICKS),
                new Row("kingcrimson_doppio_barrage", -1, KingCrimsonDonorGameplaySpec.BARRAGE_COOLDOWN_TICKS),
                new Row("kingcrimson_chop", -1, KingCrimsonConfig.chopCooldownTicks()),
                new Row("kingcrimson_impale", -1, KingCrimsonConfig.impaleCooldownTicks()),
                new Row("kingcrimson_projectile_throw", -1, KingCrimsonConfig.projectileCooldownTicks()),
                new Row("kingcrimson_projectile_throw_tripple", -1, KingCrimsonConfig.projectileCooldownTicks()),
                new Row("kingcrimson_epitaph", -1, KingCrimsonConfig.epitaphCooldownTicks()),
                new Row("kingcrimson_desperate_eyejab", -1, KingCrimsonConfig.eyejabCooldownTicks()),
                new Row("kingcrimson_timeskip", -1, KingCrimsonConfig.timeSkipCooldownTicks()),
                new Row("kingcrimson_timeerase", SHORT_ERASE_TICKS, erase),
                new Row("kingcrimson_timeerase", KingCrimsonConfig.timeEraseDurationTicks(), erase));
    }

    private static void collect(StandPower power, List<Row> rows, String when, boolean none, List<String> wrong) {
        for (Row row : rows) {
            int expected = none ? 0 : row.survival();
            int actual = power.getAbility(row.ability()).getCooldown(power, row.ticksHeld());
            if (actual != expected) {
                wrong.add(row.ability() + (row.ticksHeld() >= 0 ? " held " + row.ticksHeld() : "") + " takes " + actual
                        + " ticks " + when + ", the donor's " + expected);
            }
        }
    }
}
