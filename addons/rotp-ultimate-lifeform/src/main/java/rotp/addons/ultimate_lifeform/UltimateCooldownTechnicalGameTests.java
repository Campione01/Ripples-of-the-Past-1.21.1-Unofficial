package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional), which a Stand-bearing mob
 * waited (+ 10) before its next pick. The only Ultimate Lifeform registration with a technical part is the Tentacles'
 * cooldown(80, 0) (040 UltimateLifeformActions.java:81, Action.getCooldownTechnical applied in creative too); every
 * other move is a plain cooldown(n) or none. The cooldown itself lives in the spec, not in Ability.cooldown.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateCooldownTechnicalGameTests {
    private static final int DONOR_TENTACLES_TECHNICAL = 80;

    private UltimateCooldownTechnicalGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void onlyTheTentaclesReportATechnicalCooldownAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerPlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f21"), "UltimateCooldownTechnical"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(helper.getLevel().addFreshEntity(player), "setup: could not add the user");
        try {
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            helper.assertTrue(power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()),
                    "setup: could not give the Ultimate Lifeform power");
            power.updateAvailableMoves();
            for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
                Ability ability = power.getAbility(spec.id);
                helper.assertTrue(ability != null, "setup: no ability " + spec.id);
                int expected = spec == UltimateLifeformSpec.TENTACLES ? DONOR_TENTACLES_TECHNICAL : 0;
                int reported = ability.getCooldownTechnical(power);
                if (reported != expected) {
                    wrong.add(spec.id + " reports technical " + reported + ", the donor's " + expected);
                }
            }
            UltimateLifeformSpec tentacles = UltimateLifeformSpec.TENTACLES;
            if (tentacles.cooldownTicks(false, -1) != DONOR_TENTACLES_TECHNICAL
                    || tentacles.cooldownTicks(true, -1) != DONOR_TENTACLES_TECHNICAL) {
                wrong.add("tentacles take " + tentacles.cooldownTicks(false, -1) + " ticks in survival and "
                        + tentacles.cooldownTicks(true, -1) + " in creative, the donor's cooldown(80, 0) both");
            }
        }
        finally {
            player.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Ultimate Lifeform cooldown(technical, additional): "
                + String.join("; ", wrong));
        helper.succeed();
    }
}
