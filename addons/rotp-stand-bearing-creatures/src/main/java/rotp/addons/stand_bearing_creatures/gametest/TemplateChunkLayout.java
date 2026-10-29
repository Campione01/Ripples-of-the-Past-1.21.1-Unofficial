package rotp.addons.stand_bearing_creatures.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The "empty" template is a single block, and the GameTest framework loads and
 * ticks only the chunks of a template's bounding box before a test runs. A
 * fixture standing in the next chunk may not tick, nor be found by its id, so
 * a test whose fixtures must keeps them in the template's chunk.
 */
public final class TemplateChunkLayout {
    private TemplateChunkLayout() {}

    /**
     * The absolute position of a template-relative point of a layout whose
     * fixtures, their Stands included, stay within the given relative x and z
     * bounds, the whole layout moved by as few whole blocks as keep it inside
     * the template's chunk.
     */
    public static Vec3 absolute(GameTestHelper helper, Vec3 relative,
            double minX, double maxX, double minZ, double maxZ) {
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        return helper.absoluteVec(relative).add(
                shift(template.getX(), minX, maxX),
                0.0D,
                shift(template.getZ(), minZ, maxZ));
    }

    private static int shift(int template, double min, double max) {
        int chunkStart = SectionPos.sectionToBlockCoord(
                SectionPos.blockToSectionCoord(template));
        int lowest = Mth.ceil(chunkStart - template - min);
        int highest = Mth.floor(
                chunkStart + SectionPos.SECTION_SIZE - template - max);
        return Mth.clamp(0, lowest, highest);
    }
}
