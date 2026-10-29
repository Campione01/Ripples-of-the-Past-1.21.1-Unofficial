package rotp.addons.hermitpurple.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 HermitPurpleEntity.offsetDefault, StandRelativeOffset.withYOffset(0, 0, 0), kept the idle Stand at its user's
 * feet, and its vine attacks' standOffsetFromUser(0, 0) took that default y.
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitIdleGameTests {
    private HermitIdleGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 20, batch = "hp_idle")
    public static void theIdleStandStandsAtItsUsersFeetAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPIdle"));
        StandPower power = null;
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setNoGravity(true);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            level.addNewPlayer(player);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.skipProgression();
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            HermitPurpleStandEntity stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null && stand.isFollowingUser() && stand.offsetFromUser.isIdle(),
                    "setup: the summoned Hermit Purple is not an idle Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == 0.0D,
                    "1.16 Hermit Purple's default y, which its tasks used, was 0: Y_OFFSET " + stand.Y_OFFSET);
            stand.updatePosition(player);
            Vec3 offset = stand.position().subtract(player.position());
            helper.assertTrue(offset.lengthSqr() < 1.0E-8D,
                    "1.16 put the idle Hermit Purple at its user's feet at view yaw 0, got " + offset + " (offset "
                            + stand.offsetFromUser.getRelativeOffset() + ")");
            LogUtils.getLogger().info("R770_HP_IDLE_PASS idle");
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.discard();
        }
        helper.succeed();
    }
}
