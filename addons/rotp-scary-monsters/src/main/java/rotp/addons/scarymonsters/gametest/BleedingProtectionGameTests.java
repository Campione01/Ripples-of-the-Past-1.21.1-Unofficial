package rotp.addons.scarymonsters.gametest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import rotp.addons.scarymonsters.ScaryMonstersAddon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("scary_monsters_bleeding")
@PrefixGameTestTemplate(false)
public final class BleedingProtectionGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BleedingProtectionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void bleedingDoesNotUseWitchMagicResistance(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> bleedingDoesNotUseWitchMagicResistanceBody(h));
    }

    private static void bleedingDoesNotUseWitchMagicResistanceBody(GameTestHelper helper) {
        Witch witch = helper.spawn(EntityType.WITCH, inChunk(helper, 1, 2, 1));
        prepare(helper, witch, 200.0F, 0.0D);
        bleed(helper, witch);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, witch, 198.0F, "Witch wither-source control");
                var source = witch.getLastDamageSource();
                helper.assertTrue(source != null && source.is(DamageTypes.WITHER)
                                && !source.is(DamageTypeTags.WITCH_RESISTANT_TO),
                        "Registered bleeding did not actually damage the Witch with WITHER");
                helper.succeed();
            }
            finally {
                witch.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void bleedingRetainsDonorArmorBypass(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> bleedingRetainsDonorArmorBypassBody(h));
    }

    private static void bleedingRetainsDonorArmorBypassBody(GameTestHelper helper) {
        Cow plain = helper.spawn(EntityType.COW, inChunk(helper, 1, 2, 1));
        Cow armored = helper.spawn(EntityType.COW, inChunk(helper, 2, 2, 1));
        prepare(helper, plain, 200.0F, 0.0D);
        prepare(helper, armored, 200.0F, 20.0D);
        bleed(helper, plain);
        bleed(helper, armored);
        helper.runAfterDelay(2, () -> {
            try {
                assertHealth(helper, plain, 198.0F, "Unarmored control");
                assertHealth(helper, armored, 198.0F, "Armor20 bypass control");
                helper.succeed();
            }
            finally {
                plain.discard();
                armored.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void bleedingRetainsFloorAndTwentyTickInterval(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> bleedingRetainsFloorAndTwentyTickIntervalBody(h));
    }

    private static void bleedingRetainsFloorAndTwentyTickIntervalBody(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, inChunk(helper, 1, 2, 1));
        prepare(helper, cow, 30.0F, 0.0D);
        bleed(helper, cow);
        helper.runAfterDelay(2, () -> assertHealth(helper, cow, 29.5F, "First floor tick"));
        helper.runAfterDelay(19, () -> assertHealth(helper, cow, 29.5F, "Before next interval"));
        helper.runAfterDelay(22, () -> {
            try {
                assertHealth(helper, cow, 29.0F, "Second floor tick");
                helper.succeed();
            }
            finally {
                cow.discard();
            }
        });
    }

    // Each test sits at its own grid origin, which can be anywhere in its chunk. Past the chunk edge an entity never
    // ticks, so offsets that would cross it are mirrored; the largest offset any fixture here uses is 2.
    private static BlockPos inChunk(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int signX = (origin.getX() & 15) + 2 > 15 ? -1 : 1;
        int signZ = (origin.getZ() & 15) + 2 > 15 ? -1 : 1;
        return new BlockPos(x * signX, y, z * signZ);
    }

    private static void prepare(GameTestHelper helper, Mob mob, float health, double armor) {
        ChunkPos testChunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(new ChunkPos(mob.blockPosition()).equals(testChunk),
                "the bleeding target stands outside the chunk of its test origin: " + mob.blockPosition()
                        + " is not in " + testChunk);
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(mob.blockPosition()),
                "the target's chunk does not tick entities: " + new ChunkPos(mob.blockPosition()));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LOGGER.info("SCARY_BLEEDING_FIXTURE originLocal=({}, {}) mobLocal=({}, {}) mirroredX={} mirroredZ={}",
                origin.getX() & 15, origin.getZ() & 15, mob.blockPosition().getX() & 15,
                mob.blockPosition().getZ() & 15, (origin.getX() & 15) + 2 > 15, (origin.getZ() & 15) + 2 > 15);
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        mob.setHealth(health);
    }

    private static void bleed(GameTestHelper helper, Mob mob) {
        helper.assertTrue(mob.addEffect(new MobEffectInstance(ScaryMonstersAddon.BLEEDING, 40, 0)),
                "Registered bleeding effect was rejected");
    }

    private static void assertHealth(GameTestHelper helper, Mob mob, float expected, String label) {
        helper.assertTrue(Math.abs(mob.getHealth() - expected) < 0.001F,
                label + ": expected " + expected + ", got " + mob.getHealth());
    }
}
