package rotp.addons.ger.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerSceneChunkGameTests {
    private static final int SETTLE_TICKS = 10;

    private GerSceneChunkGameTests() {}

    /**
     * The tests of a batch stand 6 blocks apart and their scenes hold the same chunks; heavy/finisher timed out
     * after heavy/finisher_lost_arms, closing first, released the chunk its Stand and cow stood in. A scene holds its
     * chunks with tickets of its own, so one that closes first, or late (a listener closes a failed scene once the
     * next batch has started), leaves the chunks another live scene holds ticking entities.
     */
    @GameTest(template = "empty", timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS, batch = "ger_scene_chunks")
    public static void aClosingSceneKeepsTheChunksAnotherSceneUses(GameTestHelper helper) {
        GerMeleeGameTestScene first = new GerMeleeGameTestScene(helper, "scene/first");
        GerMeleeGameTestScene second = new GerMeleeGameTestScene(helper, "scene/second");
        helper.assertTrue(!second.heldChunks().isEmpty() && first.heldChunks().equals(second.heldChunks()),
                "scene premise: the two scenes hold the same chunks: " + first.heldChunks() + " / "
                        + second.heldChunks());
        long[] closedAt = {-1L};
        helper.succeedWhen(() -> {
            if (closedAt[0] < 0L) {
                List<ChunkPos> idle = second.chunksNotTickingEntities();
                helper.assertTrue(idle.isEmpty(), "the held chunks " + idle + " do not tick entities yet");
                first.close();
                closedAt[0] = helper.getTick();
            }
            helper.assertTrue(helper.getTick() >= closedAt[0] + SETTLE_TICKS,
                    "waiting for the level to settle after scene/first closed");
            List<ChunkPos> idle = second.chunksNotTickingEntities();
            second.close();
            if (!idle.isEmpty()) {
                // a failed assertion would only be retried until the timeout
                GameTestAssertException failure = new GameTestAssertException(
                        "scene/first took " + idle + " as it closed, which scene/second still holds");
                helper.testInfo.fail(failure);
                throw failure;
            }
        });
    }
}
