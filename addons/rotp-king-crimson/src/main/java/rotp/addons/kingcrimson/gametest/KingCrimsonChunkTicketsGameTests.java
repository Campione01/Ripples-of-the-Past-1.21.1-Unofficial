package rotp.addons.kingcrimson.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonChunkTicketsGameTests {
    private KingCrimsonChunkTicketsGameTests() {}

    /**
     * Fixtures hold their chunks with tickets of their own: one that releases late, as a listener does once the next
     * batch has started, takes no chunk from another fixture that holds the same ones.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_chunk_tickets")
    public static void aFixtureReleasingItsChunksLeavesAnotherFixtureItsOwn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // away from the chunks the test structure and the other tests force
        Vec3 far = Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)).add(160.0D, 0.0D, 160.0D);
        KingCrimsonChunkTickets first = new KingCrimsonChunkTickets(level);
        KingCrimsonChunkTickets second = new KingCrimsonChunkTickets(level);
        try {
            first.force(far);
            second.force(far);
            first.release();
        }
        catch (RuntimeException | Error failure) {
            first.release();
            second.release();
            throw failure;
        }
        helper.runAfterDelay(10, () -> {
            List<ChunkPos> idle = second.notTickingEntities();
            second.release();
            helper.assertTrue(idle.isEmpty(),
                    "the first fixture's release took the chunks " + idle + " from the second one that holds them");
            helper.succeed();
        });
    }
}
