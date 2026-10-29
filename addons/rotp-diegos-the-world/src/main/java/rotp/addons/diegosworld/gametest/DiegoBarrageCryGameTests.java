package rotp.addons.diegosworld.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 the_world_barrage (THEWORLDBarrage) registered its cry with the one-argument standSound, which also played
 * while the Stand was arms-only (StandEntityAction.getPhaseStandSounds); the core Stands' cries do not.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class DiegoBarrageCryGameTests {
    private DiegoBarrageCryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotpDiegoBarrageCry")
    public static void barrageCriesWhileTheStandIsArmsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "diego-cry")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.setNoGravity(true);
        user.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        StandPower power = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(DiegosTheWorldAddon.THE_WORLD.get());
            helper.assertTrue(DiegosTheWorldAddon.THE_WORLD.get().summon(user, power),
                    "Could not summon Diego's The World.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned Diego's The World");
            stand.setArmsOnlyMode(true, false);
            helper.assertTrue(stand.isArmsOnlyMode(), "Setup: the Stand is not arms-only");
            for (String name : new String[] { DiegosTheWorldAddon.BARRAGE, "grab_barrage" }) {
                Ability barrage = power.getAbility(name);
                helper.assertTrue(barrage instanceof StandEntityBarrageAbility,
                        "Setup: Diego's The World has no " + name + ": " + barrage);
                helper.assertTrue(new CryProbe((StandEntityBarrageAbility) barrage).cries(level, stand),
                        "1.16 standSound(THE_WORLD_MUDA_MUDA_MUDA) plays the " + name
                                + " cry while the Stand is arms-only; the port keeps it silent");
            }
        } finally {
            if (power != null && power.isSummoned()) {
                DiegosTheWorldAddon.THE_WORLD.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    // Reads the core's arms-only cry rule for a barrage ability.
    private static final class CryProbe extends StandEntityBarrageAbility.StandEntityBarrage {
        private CryProbe(StandEntityBarrageAbility ability) {
            super(ability);
        }

        private boolean cries(ServerLevel level, StandEntity stand) {
            return playsBarrageCry(level, stand);
        }
    }
}
