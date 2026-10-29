package rotp.addons.diverdown.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

/**
 * 1.16 diver_down_glide (047 InitStands:70) resolveLevelToUnlock(1): StandType.unlockNewActions unlocked it once the
 * user reached Resolve 1 (StandAction.canBeUnlocked), not before.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownUnlockGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";

    private DiverDownUnlockGameTests() {}

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_unlock", timeoutTicks = 40)
    public static void wallGlideUnlocksAtResolveOneAsIn116(GameTestHelper helper) {
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "Fixture: no user");
        user.setNoAi(true);
        user.setNoGravity(true);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.setResolveLevel(0);
            StandTypePersistentData data = power.getCurTypeData();
            Ability glide = power.getAbility(DiverDownAddon.WALL_GLIDE_NAME);
            helper.assertTrue(data != null && glide instanceof WallGlideAbility && power.getHighestResolveLevel() == 0,
                    "Fixture: Diver Down at Resolve 0 has no wall glide");
            data.unlockOriginalResolveSkills(power);
            helper.assertTrue(!glide.isAbilityUnlocked(power) && !data.isSkillUnlocked(DiverDownAddon.WALL_GLIDE_NAME),
                    "1.16 locked diver_down_glide below Resolve 1 (InitStands:70 resolveLevelToUnlock(1)); the port"
                            + " unlocked it at Resolve 0 (skill " + data.isSkillUnlocked(DiverDownAddon.WALL_GLIDE_NAME)
                            + ")");
            power.setResolveLevel(1);
            helper.assertTrue(data.isSkillUnlocked(DiverDownAddon.WALL_GLIDE_NAME) && glide.isAbilityUnlocked(power),
                    "1.16 unlocked diver_down_glide at Resolve 1; the port keeps it locked (skill "
                            + data.isSkillUnlocked(DiverDownAddon.WALL_GLIDE_NAME) + ")");
            power.setResolveLevel(0);
            helper.assertTrue(glide.isAbilityUnlocked(power),
                    "1.16 kept diver_down_glide unlocked after the Resolve level dropped back to 0");
        }
        finally {
            user.discard();
        }
        helper.succeed();
    }
}
