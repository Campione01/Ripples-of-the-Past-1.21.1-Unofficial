package rotp.core.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.init.ModStatusEffects;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * 1.16 TimeStopHandler.updateEntityTimeStop also ran for the client world: there a player kept updating with the
 * Time Stop effect or with "canPlayerMoveInStoppedTime(player, false)", which on a client is gamemodeIgnoresTimeStop
 * (Creative or Spectator). The decision the client entity tick asks is TimeStopState.shouldFreezeClientEntity.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopClientMoverRuleGameTests {
    private static int nextStopId = -7_650_000;

    private TimeStopClientMoverRuleGameTests() {}

    @GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
    public static void clientTickRuleLetsCreativeAndSpectatorPlayersMoveInStoppedTime(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        Player survival = playerAt(helper, GameType.SURVIVAL, origin);
        Player adventure = playerAt(helper, GameType.ADVENTURE, origin);
        Player creative = playerAt(helper, GameType.CREATIVE, origin);
        Player spectator = playerAt(helper, GameType.SPECTATOR, origin);
        Player withEffect = playerAt(helper, GameType.SURVIVAL, origin);
        int stop = nextStopId--;
        try {
            helper.assertTrue(!TimeStopState.shouldFreezeClientEntity(survival)
                            && !TimeStopState.shouldFreezeClientEntity(creative),
                    "TS-CLIENT-MOVER premise: nobody is stopped before the client knows a time stop");
            TimeStopState.putClientInstance(new TimeStopState.Instance(stop, 200, 200, chunk, 1, -1,
                    "client_mover_rule_gametest"));
            helper.assertTrue(TimeStopState.getClientDisplayInstance(chunk).isPresent(),
                    "TS-CLIENT-MOVER premise: the client knows a time stop in the test chunk");
            withEffect.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200, 0, false, false, true));

            boolean survivalStopped = TimeStopState.shouldFreezeClientEntity(survival);
            boolean adventureStopped = TimeStopState.shouldFreezeClientEntity(adventure);
            boolean effectStopped = TimeStopState.shouldFreezeClientEntity(withEffect);
            boolean creativeStopped = TimeStopState.shouldFreezeClientEntity(creative);
            boolean spectatorStopped = TimeStopState.shouldFreezeClientEntity(spectator);
            helper.assertTrue(survivalStopped && adventureStopped && !effectStopped,
                    "TS-CLIENT-MOVER premise: Survival and Adventure players are stopped, a player with the effect is not:"
                            + " survival=" + survivalStopped + " adventure=" + adventureStopped + " effect=" + effectStopped);
            helper.assertTrue(!creativeStopped && !spectatorStopped,
                    "The client stopped a player whose game mode ignores stopped time: creativeStopped=" + creativeStopped
                            + " spectatorStopped=" + spectatorStopped
                            + ", 1.16 gamemodeIgnoresTimeStop: creativeStopped=false spectatorStopped=false");

            TimeStopState.removeClientInstance(stop);
            helper.assertTrue(!TimeStopState.shouldFreezeClientEntity(survival),
                    "TS-CLIENT-MOVER: a Survival player stayed stopped after the client's time stop was removed");
        }
        finally {
            TimeStopState.removeClientInstance(stop);
        }
        helper.succeed();
    }

    private static Player playerAt(GameTestHelper helper, GameType gameType, BlockPos pos) {
        Player player = GameTestPlayers.makeServerMockPlayer(helper, gameType);
        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }
}
