package rotp.addons.diegosworld.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class BleedingProtectionGameTests {
    private BleedingProtectionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bleedingUsesDonorMagicResistance(GameTestHelper helper) {
        ChunkHold chunks = new ChunkHold(helper);
        Witch witch = spawn(helper, chunks, EntityType.WITCH, 1);
        Cow cow = spawn(helper, chunks, EntityType.COW, 2);
        prepare(witch, 0.0D);
        prepare(cow, 0.0D);
        bleed(helper, witch);
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, witch, 199.85F, "Witch magic resistance");
                assertHealth(helper, cow, 200.0F - 1.0F, "Unprotected control");
                helper.succeed();
            }
            finally {
                witch.discard();
                cow.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bleedingRetainsArmorBypass(GameTestHelper helper) {
        Cow cow = spawn(helper, new ChunkHold(helper), EntityType.COW, 1);
        prepare(cow, 20.0D);
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, cow, 200.0F - 1.0F, "Armor20 bypass");
                helper.succeed();
            }
            finally { cow.discard(); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bleedingRetainsTwentyTickPeriod(GameTestHelper helper) {
        Cow cow = spawn(helper, new ChunkHold(helper), EntityType.COW, 1);
        prepare(cow, 0.0D);
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> assertHealth(helper, cow, 199.0F, "First tick"));
        helper.runAfterDelay(19, () -> assertHealth(helper, cow, 199.0F, "Before next interval"));
        helper.runAfterDelay(22, () -> {
            try {
                assertHealth(helper, cow, 198.0F, "Second tick");
                helper.succeed();
            }
            finally { cow.discard(); }
        });
    }

    private static <T extends Mob> T spawn(GameTestHelper helper, ChunkHold chunks, EntityType<T> type, int x) {
        BlockPos at = new BlockPos(x, 2, 1);
        chunks.force(helper.absoluteVec(Vec3.atBottomCenterOf(at)));
        return helper.spawn(type, at);
    }

    /**
     * Keeps every chunk within 3 blocks of a position loaded by a ticket, because only a ticketed
     * chunk ticks its entities here and an effect runs in the tick of its
     * mob. A newly ticketed chunk ticks entities only after the ring around it
     * has generated, which takes real time under load, so force() returns
     * once all of them tick. The game test server does not pace its ticks,
     * hence the budget is real time and the server thread runs the chunk
     * tasks meanwhile. The chunks are given back when the test ends.
     */
    private static final class ChunkHold implements GameTestListener {
        private static final TicketType<UUID> TICKET = TicketType.create(
                "rotp_theworld_bleeding", Comparator.<UUID>naturalOrder());
        private static final double MARGIN = 3.0D;
        private static final long WAIT_NANOS = 30_000_000_000L;
        private static final long PAUSE_NANOS = 1_000_000L;
        private final ServerLevel level;
        private final UUID ticketId = UUID.randomUUID();
        private final Set<ChunkPos> chunks = new LinkedHashSet<>();

        private ChunkHold(GameTestHelper helper) {
            level = helper.getLevel();
            helper.testInfo.addListener(this);
        }

        private void force(Vec3 position) {
            int minX = SectionPos.blockToSectionCoord(Mth.floor(position.x - MARGIN));
            int maxX = SectionPos.blockToSectionCoord(Mth.floor(position.x + MARGIN));
            int minZ = SectionPos.blockToSectionCoord(Mth.floor(position.z - MARGIN));
            int maxZ = SectionPos.blockToSectionCoord(Mth.floor(position.z + MARGIN));
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    if (chunks.add(chunk)) {
                        level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketId);
                        level.getChunk(chunk.x, chunk.z);
                    }
                }
            }
            awaitEntityTicking();
        }

        private void awaitEntityTicking() {
            ServerChunkCache cache = level.getChunkSource();
            long start = System.nanoTime();
            List<ChunkPos> idle;
            while (!(idle = notTickingEntities()).isEmpty()) {
                long waited = System.nanoTime() - start;
                if (waited > WAIT_NANOS) {
                    throw new GameTestAssertException("The ticketed chunks " + idle
                            + " do not tick entities after " + waited / 1_000_000L + " ms");
                }
                if (!cache.pollTask()) {
                    LockSupport.parkNanos(PAUSE_NANOS);
                }
            }
        }

        private List<ChunkPos> notTickingEntities() {
            List<ChunkPos> idle = new ArrayList<>();
            for (ChunkPos chunk : chunks) {
                if (!level.isPositionEntityTicking(chunk.getWorldPosition())) {
                    idle.add(chunk);
                }
            }
            return idle;
        }

        private void release() {
            for (ChunkPos chunk : chunks) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketId);
            }
            chunks.clear();
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            release();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            release();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
            release();
        }
    }

    private static void prepare(Mob mob, double armor) {
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        mob.setHealth(200.0F);
    }

    private static void bleed(GameTestHelper helper, Mob mob) {
        helper.assertTrue(mob.addEffect(new MobEffectInstance(DiegosTheWorldAddon.BLEEDING, 40, 0)),
                "Registered bleeding effect rejected");
    }

    private static void assertHealth(GameTestHelper helper, Mob mob, float expected, String label) {
        helper.assertTrue(Math.abs(mob.getHealth() - expected) < 0.001F,
                label + ": expected " + expected + ", got " + mob.getHealth());
    }
}
