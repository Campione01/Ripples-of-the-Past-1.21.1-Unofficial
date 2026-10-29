package rotp.addons.harvest.gametest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 HarvestStandEntity.offsetDefault, StandRelativeOffset.withYOffset(0, 1.0, 0), put the idle Stand's feet 1.0
 * above its user's feet whatever the view yaw, and a sneaking user with no task brought it down to the feet.
 */
@GameTestHolder(HarvestAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestIdleGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double EPS = 1.0E-4D;

    private HarvestIdleGameTests() {}

    @GameTest(template = "empty", batch = "harvest_idle", timeoutTicks = 100)
    public static void idleHarvestStandsOneBlockAboveItsUsersFeetAsIn116(GameTestHelper helper) {
        ServerPlayer owner = HarvestWallsGameTests.makeServerPlayer(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 8, 1)));
        StandPower power = null;
        try {
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(HarvestAddon.HARVEST_STAND.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Harvest");
            helper.assertTrue(HarvestAddon.HARVEST_STAND.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "Production Harvest summon failed");
            HarvestStandEntity stand = (HarvestStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle(),
                    "setup: the summoned Harvest is not an idle full Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == 1.0D,
                    "1.16 Harvest's default y, which its tasks used, was 1.0: Y_OFFSET " + stand.Y_OFFSET);

            owner.setYRot(0.0F);
            owner.setYHeadRot(0.0F);
            owner.yBodyRot = 0.0F;
            Vec3 standing = assertAt(helper, owner, stand, 0.0D, 1.0D, 0.0D, "a standing user at view yaw 0");
            owner.setShiftKeyDown(true);
            assertAt(helper, owner, stand, 0.0D, 0.0D, 0.0D, "a sneaking user with no task at view yaw 0");
            owner.setShiftKeyDown(false);
            LOGGER.info("R770_HARVEST_IDLE_PASS offset={}", standing);
        }
        finally {
            if (power != null && power.isSummoned()) {
                HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            }
            owner.discard();
        }
        helper.succeed();
    }

    private static Vec3 assertAt(GameTestHelper helper, ServerPlayer owner, HarvestStandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(owner);
        Vec3 actual = stand.position().subtract(owner.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put the idle Harvest at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with " + when
                        + ", got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + ")");
        return actual;
    }
}
