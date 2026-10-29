package rotp.addons.hermitpurple.ability;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 cringe (061 InitStands:80 HP_OH_NO) resolveLevelToUnlock(4): StandType.unlockNewActions unlocked it once the
 * user reached Resolve 4 (StandAction.canBeUnlocked) and the unlock stayed; before that the slot showed nothing and the
 * server refused the press (PowerBaseImpl.checkRequirements isUnlocked).
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitUnlockGameTests {
    private HermitUnlockGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_unlock")
    public static void cringeUnlocksAtResolveFourAsIn116(GameTestHelper helper) {
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        user.setNoAi(true);
        user.setNoGravity(true);
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            Ability cringe = power.getAbility(HermitPurpleAddon.CRINGE_NAME);
            helper.assertTrue(cringe instanceof HermitNoopAbility && power.getHighestResolveLevel() == 0
                            && !power.wasProgressionSkipped(),
                    "setup: Hermit Purple at Resolve 0 without skipped progression has no cringe");
            for (int level = 0; level < 4; level++) {
                power.setResolveLevel(level);
                helper.assertTrue(!cringe.isAbilityUnlocked(power) && !cringe.isAbilityAvailable(power),
                        "1.16 locked cringe below Resolve 4 (061 InitStands:80 resolveLevelToUnlock(4)); the port"
                                + " unlocked it at Resolve " + level);
            }
            power.setResolveLevel(4);
            helper.assertTrue(cringe.isAbilityUnlocked(power) && cringe.isAbilityAvailable(power),
                    "1.16 unlocked cringe at Resolve 4; the port keeps it locked ("
                            + cringe.getUnlockConditionCheck(power).getWarning() + ")");
            power.setResolveLevel(0);
            helper.assertTrue(cringe.isAbilityUnlocked(power),
                    "1.16 kept cringe unlocked once Resolve 4 was reached; the port locked it again at Resolve 0");
        }
        finally {
            user.discard();
        }
        LogUtils.getLogger().info("R770_HP_UNLOCK_PASS {}", "cringe");
        helper.succeed();
    }
}
