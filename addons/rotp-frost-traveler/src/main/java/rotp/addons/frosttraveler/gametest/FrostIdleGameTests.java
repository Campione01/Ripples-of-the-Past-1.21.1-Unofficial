package rotp.addons.frosttraveler.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.entity.CreamStarterStandEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 CSEntity.offsetDefault, StandRelativeOffset.withYOffset(0, 0, 0), kept the idle Stand at its user's feet.
 */
@GameTestHolder("frost_injection")
@PrefixGameTestTemplate(false)
public final class FrostIdleGameTests {
    private FrostIdleGameTests() {}

    @GameTest(template = "fixture_area", templateNamespace = "frost_injection", required = true, timeoutTicks = 20)
    public static void theIdleStandStandsAtItsUsersFeetAsIn116(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FrostIdle"));
        StandPower power = null;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            Vec3 user = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            AABB area = helper.getBounds();
            // the framework forces and waits for only the chunks of the structure's bounds
            helper.assertTrue(user.x >= area.minX && user.x < area.maxX && user.z >= area.minZ && user.z < area.maxZ,
                    "Fixture: " + user + " lies outside the test structure " + area
                            + ", whose chunks alone the framework loads before the test");
            player.setPos(user);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Cream Starter user");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(FrostTravelerAddon.CREAM_STARTER.get());
            helper.assertTrue(FrostTravelerAddon.CREAM_STARTER.get().summon(player, power),
                    "Cream Starter could not be summoned");
            StandEntity stand = FrostTravelerAddon.controller(player);
            helper.assertTrue(stand instanceof CreamStarterStandEntity && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle(),
                    "setup: the summoned Cream Starter is not an idle full Stand following its user: " + stand);
            helper.assertTrue(stand.Y_OFFSET == 0.0D,
                    "1.16 Cream Starter's default y, which its tasks used, was 0: Y_OFFSET " + stand.Y_OFFSET);
            stand.updatePosition(player);
            Vec3 offset = stand.position().subtract(player.position());
            helper.assertTrue(offset.lengthSqr() < 1.0E-8D,
                    "1.16 put the idle Cream Starter at its user's feet at view yaw 0, got " + offset + " (offset "
                            + stand.offsetFromUser.getRelativeOffset() + ")");
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(player, power);
            }
            player.discard();
        }
        helper.succeed();
    }
}
