package rotp.addons.achtungbaby.gametest;

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
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.achtungbaby.AchtungBabyAddon;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Achtung Baby (093 action/*Action.java) registered no builder cooldown and overrode getCooldownAdditional with a
 * constant, without super: ItemInvisibilityAction :99-101 60, BloodSplashAction :42-44 100, ActiveInvisibilityAction
 * :127-129 300, GhostModeAction :93-95 0, PerfectInvisibilityAction :125-127 10, GrantInvisibilityAction :147-159 10
 * outside its penalty window. Action.getCooldown :384-386 added getCooldownTechnical, the builder's 0, so the whole
 * cooldown was additional, yet neither Resolve (StandAction.getCooldownAdditional :181-188) nor Creative (Action
 * :373-375) touched it, both living in the overridden super; a Stand-bearing mob waited getCooldownTechnical + 10.
 */
@GameTestHolder(AchtungBabyPerfectInvisibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AchtungBabyCooldownGameTests {
    private static final String NAMESPACE = AchtungBabyPerfectInvisibilityGameTests.NAMESPACE;

    private AchtungBabyCooldownGameTests() {}

    private record Row(String ability, int total) {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void everyCooldownIsTheDonorsConstantWithNoTechnicalPart(GameTestHelper helper) {
        List<Row> rows = List.of(
                new Row(AchtungBabyAddon.ITEM_INVISIBILITY_NAME, 60),
                new Row(AchtungBabyAddon.BLOOD_SPLASH_NAME, 100),
                new Row(AchtungBabyAddon.ACTIVE_INVISIBILITY_NAME, 300),
                new Row(AchtungBabyAddon.GHOST_MODE_NAME, 0),
                new Row(AchtungBabyAddon.PERFECT_INVISIBILITY_NAME, 10),
                new Row(AchtungBabyAddon.GRANT_INVISIBILITY_NAME, 10));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ABCooldown"));
        player.setPos(AchtungBabyPerfectInvisibilityGameTests.insideOriginChunk(
                helper, new Vec3(0.5D, 2.0D, 0.5D), 0.0D));
        helper.getLevel().addNewPlayer(player);
        List<String> wrong = new ArrayList<>();
        try {
            player.setGameMode(GameType.SURVIVAL);
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(AchtungBabyAddon.ACHTUNG_BABY.get());
            helper.assertTrue(AchtungBabyAddon.isUser(player), "Setup: the test player is not an Achtung Baby user");
            for (Row row : rows) {
                Ability ability = power.getAbility(row.ability());
                helper.assertTrue(ability != null, "Setup: Achtung Baby has no " + row.ability());
                int technical = ability.getCooldownTechnical(power);
                if (technical != 0) {
                    wrong.add(row.ability() + " reports technical " + technical + ", the donor's 0 (no builder cooldown)");
                }
            }
            collect(power, rows, "in Survival", false, wrong);
            player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
            collect(power, rows, "in Resolve", false, wrong);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(power.isUserCreative(), "Setup: the test player is not in Creative");
            collect(power, rows, "as a Creative user in Resolve", false, wrong);
            player.removeEffect(ModStatusEffects.RESOLVE);
            collect(power, rows, "as a Creative user", false, wrong);
            player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
            collect(power, rows, "with the port's no-cooldown setting", true, wrong);
        } finally {
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Achtung Baby getCooldownAdditional constants: " + String.join("; ", wrong));
        helper.succeed();
    }

    private static void collect(StandPower power, List<Row> rows, String when, boolean none, List<String> wrong) {
        for (Row row : rows) {
            int expected = none ? 0 : row.total();
            int actual = power.getAbility(row.ability()).getCooldown(power, -1);
            if (actual != expected) {
                wrong.add(row.ability() + " takes " + actual + " ticks " + when + ", the donor's " + expected);
            }
        }
    }
}
