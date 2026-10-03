package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonCreativeWallClimbGameTests {
    private HamonCreativeWallClimbGameTests() {}

    @GameTest(template = "empty", batch = "hamon_creative_wall_cost")
    public static void creativeKeepsClingingAfterRealResourceExhaustion(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE)) {
            fixture.exhaustThenSelectMode();
            fixture.data.startWallClimbing(fixture.player, 90);
            for (int i = 0; i < 4; i++) {
                fixture.data.tick(fixture.power);
                helper.assertTrue(fixture.data.isHamonWallClimbing(),
                        "Creative resource exhaustion cancelled the existing wall-climb state");
                helper.assertTrue(fixture.data.getEnergy() == 0 && fixture.data.getBreathStability() == 0,
                        "Exhausted fixture unexpectedly recovered or received synthetic resources");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hamon_creative_wall_cost")
    public static void survivalExhaustionStillStopsClinging(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.SURVIVAL)) {
            fixture.exhaustThenSelectMode();
            fixture.data.startWallClimbing(fixture.player, 90);
            fixture.data.tick(fixture.power);
            helper.assertTrue(!fixture.data.isWallClimbing(), "Exhausted Survival player kept clinging");
            helper.assertTrue(fixture.data.getEnergy() == 0 && fixture.data.getBreathStability() == 0,
                    "Survival exhaustion changed resources");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hamon_creative_wall_cost")
    public static void movingAndRestingCostsKeepTheirModeSemantics(GameTestHelper helper) {
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            try (Fixture fixture = new Fixture(helper, mode)) {
                fixture.player.setGameMode(mode);
                fixture.data.setBreathStability(fixture.data.getMaxBreathStability());
                // The public breath tick sets the ordinary passive-decay grace period.
                fixture.data.tickHamonBreath(fixture.player);
                fixture.data.setEnergy(100);
                fixture.data.startWallClimbing(fixture.player, 90);
                fixture.data.tick(fixture.power);
                float resting = mode == GameType.CREATIVE ? 100 : 97.5F;
                helper.assertTrue(fixture.data.isHamonWallClimbing()
                                && Math.abs(fixture.data.getEnergy() - resting) < 0.001F,
                        "Resting wall-climb resource cost changed for " + mode);
                fixture.data.setWallClimbMoving(true);
                fixture.data.tick(fixture.power);
                float moving = mode == GameType.CREATIVE ? 100 : 87.5F;
                helper.assertTrue(fixture.data.isHamonWallClimbing()
                                && Math.abs(fixture.data.getEnergy() - moving) < 0.001F,
                        "Moving wall-climb resource cost changed for " + mode);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hamon_creative_wall_cost")
    public static void creativeStillRequiresTheLearnedSkill(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE)) {
            fixture.exhaustThenSelectMode();
            fixture.data.removeSkill(ModHamonSkills.WALL_CLIMBING.get());
            fixture.data.startWallClimbing(fixture.player, 90);
            fixture.data.tick(fixture.power);
            helper.assertTrue(!fixture.data.isWallClimbing(), "Creative bypassed the wall-climbing skill requirement");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameType mode;
        private final FakePlayer player;
        private final PlayerPower power;
        private final HamonData data;

        private Fixture(GameTestHelper helper, GameType mode) {
            this.helper = helper;
            this.mode = mode;
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HamonClingCost"));
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
            helper.getLevel().addNewPlayer(player);
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
            helper.assertTrue(data.learnSkill(ModHamonSkills.WALL_CLIMBING.get()), "Could not learn registered wall-climbing skill");
        }

        private void exhaustThenSelectMode() {
            data.setEnergy(0);
            data.setBreathStability(10);
            data.consumeEnergy(10000, player);
            helper.assertTrue(data.getEnergy() == 0 && data.getBreathStability() == 0,
                    "Public energy consumption did not exhaust the fixture's breath reserve");
            player.setGameMode(mode);
        }

        @Override
        public void close() {
            data.stopWallClimbing(player);
            player.discard();
        }
    }
}
