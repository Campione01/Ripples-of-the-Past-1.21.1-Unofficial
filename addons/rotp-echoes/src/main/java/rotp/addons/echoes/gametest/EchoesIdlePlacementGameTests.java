package rotp.addons.echoes.gametest;

import java.util.List;
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
import rotp.addons.echoes.init.EchoesStands;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

@GameTestHolder("rotp_echoes_pilot")
@PrefixGameTestTemplate(false)
public final class EchoesIdlePlacementGameTests {
    private static final double EPS = 1.0E-4D;

    private EchoesIdlePlacementGameTests() {}

    /**
     * The Echoes donors keep 1.16 StandEntity's default offsets: idle
     * withYOffset(-0.75, 0.2, -0.75) and arms-only withYOffset(0, 0, 0.15),
     * (left, up, forward) from the user's feet, turned by the view yaw.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void idleEchoesActsStandWhereTheDonorsPutThem(GameTestHelper helper) {
        for (StandType type : List.<StandType>of(EchoesStands.ECHOES_ACT1.get(),
                EchoesStands.ECHOES_ACT2.get(), EchoesStands.ECHOES_ACT3.get())) {
            checkAct(helper, type);
        }
        helper.succeed();
    }

    private static void checkAct(GameTestHelper helper, StandType type) {
        String act = String.valueOf(type.getId());
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "echoes-idle")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the " + act + " user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant " + act);
            helper.assertTrue(type.summon(user, power), "Could not summon " + act);
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode()
                            && stand.offsetFromUser.isIdle(),
                    "setup: " + act + " is not an idle full Stand following its user");

            turn(user, 0.0F, 50.0F);
            assertAt(helper, act, user, stand, -0.75D, 0.2D, -0.75D,
                    "view yaw 0, body yaw 50");
            turn(user, 90.0F, 0.0F);
            assertAt(helper, act, user, stand, 0.75D, 0.2D, -0.75D,
                    "view yaw 90, body yaw 0");

            turn(user, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertAt(helper, act, user, stand, 0.0D, 0.0D, 0.15D,
                    "arms-only, view yaw 0");
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

    private static void assertAt(GameTestHelper helper, String act, Player user,
            StandEntity stand, double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS
                        && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put an idle " + act + " at its user's feet + (" + dx + ", "
                        + dy + ", " + dz + ") with " + when + ", got " + actual
                        + " (offset " + stand.offsetFromUser.getRelativeOffset()
                        + " " + stand.offsetFromUser.getRotations() + ")");
    }
}
