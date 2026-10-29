package rotp.addons.echoes;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import rotp.addons.echoes.init.EchoesStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_owner_decisions")
@PrefixGameTestTemplate(false)
public final class OwnerDecisionEchoesGameTests {
    private OwnerDecisionEchoesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void actThreeRetainsDonorAboveCapLevels(GameTestHelper helper) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0,
                new GameProfile(UUID.randomUUID(), "echoes-gate-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return getAbilities().instabuild; }
        };
        try {
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(power.getMaxResolveLevel() == 4, "Do not invent a six-level progression curve");
            power.setResolveLevel(4);
            Ability finisher = power.getAbility("finisher");
            Ability degrade = power.getAbility("degrade_to_act2");
            helper.assertTrue(finisher.getRequiredResolveLevel(power) == 5
                    && degrade.getRequiredResolveLevel(power) == 6, "Donor thresholds must be 5 and 6");
            helper.assertTrue(!finisher.getResolveUnlockConditionCheck(power).isPositive()
                    && !degrade.getResolveUnlockConditionCheck(power).isPositive(),
                    "Ordinary survival Resolve 4 must not bypass either gate");
            player.getAbilities().instabuild = true;
            helper.assertTrue(finisher.getResolveUnlockConditionCheck(power).isPositive()
                    && degrade.getResolveUnlockConditionCheck(power).isPositive(),
                    "Creative must retain access to the above-cap actions");
        }
        finally { player.discard(); }
        helper.succeed();
    }
}
