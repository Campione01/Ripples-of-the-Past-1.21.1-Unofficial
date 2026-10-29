package rotp.addons.heavensdoor;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorIdlePlacementGameTests {
    private static final double EPS = 1.0E-4D;

    private HeavensDoorIdlePlacementGameTests() {}

    /**
     * 1.16 HeavensDoorEntity: idle withYOffset(-0.5, 0.75, -0.65) and
     * arms-only withYOffset(0, 0.35, 0.15), (left, up, forward) from the
     * user's feet, turned by the view yaw; 0.75 is the default y of task
     * offsets without their own.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "heavens_door_idle_placement")
    public static void idleHeavensDoorStandsWhereTheDonorPutIt(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "hd-idle")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = HeavensDoorAddon.HEAVENS_DOOR.get();
        StandPower power = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Heaven's Door user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Heaven's Door");
            helper.assertTrue(type.summon(user, power),
                    "Could not summon Heaven's Door");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode()
                            && stand.offsetFromUser.isIdle(),
                    "setup: Heaven's Door is not an idle full Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == 0.75D,
                    "1.16 Heaven's Door task offsets without a y took 0.75, got "
                            + stand.Y_OFFSET);

            turn(user, 0.0F, 50.0F);
            assertAt(helper, user, stand, -0.5D, 0.75D, -0.65D,
                    "view yaw 0, body yaw 50");
            turn(user, 90.0F, 0.0F);
            assertAt(helper, user, stand, 0.65D, 0.75D, -0.5D,
                    "view yaw 90, body yaw 0");

            turn(user, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertAt(helper, user, stand, 0.0D, 0.35D, 0.15D,
                    "arms-only, view yaw 0");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    private static void turn(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertAt(GameTestHelper helper, Player user,
            StandEntity stand, double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS
                        && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put an idle Heaven's Door at its user's feet + (" + dx + ", "
                        + dy + ", " + dz + ") with " + when + ", got " + actual
                        + " (offset " + stand.offsetFromUser.getRelativeOffset()
                        + " " + stand.offsetFromUser.getRotations() + ")");
    }
}
