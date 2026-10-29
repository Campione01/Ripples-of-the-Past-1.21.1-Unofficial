package rotp.addons.mandom.entity;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.mandom.MandomAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 MandomEntity.offsetDefault, StandRelativeOffset.withYOffset(0, 0, 0), kept the idle Stand at its user's feet.
 */
@GameTestHolder(MandomAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MandomIdleGameTests {
    private MandomIdleGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mandom", batch = "mandom_idle", timeoutTicks = 20)
    public static void theIdleStandStandsAtItsUsersFeetAsIn116(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MandomIdle"));
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            player.setYHeadRot(0.0F);
            player.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Mandom user");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(MandomAddon.MANDOM.get());
            helper.assertTrue(MandomAddon.MANDOM.get().summon(player, power), "Mandom could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand instanceof MandomStandEntity && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle(),
                    "setup: the summoned Mandom is not an idle full Stand following its user: " + stand);
            helper.assertTrue(stand.Y_OFFSET == 0.0D,
                    "1.16 Mandom's default y, which its tasks used, was 0: Y_OFFSET " + stand.Y_OFFSET);
            stand.updatePosition(player);
            Vec3 offset = stand.position().subtract(player.position());
            helper.assertTrue(offset.lengthSqr() < 1.0E-8D,
                    "1.16 put the idle Mandom at its user's feet at view yaw 0, got " + offset + " (offset "
                            + stand.offsetFromUser.getRelativeOffset() + ")");
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                MandomAddon.MANDOM.get().forceUnsummon(player, power);
            }
            player.discard();
        }
        helper.succeed();
    }
}
