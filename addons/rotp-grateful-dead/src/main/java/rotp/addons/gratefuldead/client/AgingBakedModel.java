package rotp.addons.gratefuldead.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

final class AgingBakedModel
        extends BakedModelWrapper<BakedModel> {
    private static final int VERTEX_STRIDE = 8;
    private static final int VERTEX_COUNT = 4;
    private static final int COLOR_OFFSET = 3;
    private static final ModelProperty<Float> AGING =
            new ModelProperty<>();

    AgingBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos position,
            BlockState state,
            ModelData modelData) {
        ModelData originalData = originalModel.getModelData(
                level, position, state, modelData);
        float aging = AgingVisuals.blockCurve(
                AgingBlockClientCache.getProgress(position));
        return aging > 0.0F
                ? originalData.derive()
                        .with(AGING, aging)
                        .build()
                : originalData;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType) {
        List<BakedQuad> original = originalModel.getQuads(
                state, side, random, modelData, renderType);
        Float aging = modelData.get(AGING);
        if (aging == null
                || aging <= 0.0F
                || original.isEmpty()) {
            return original;
        }
        boolean surfaceOnly = state != null
                && (state.is(Blocks.GRASS_BLOCK)
                        || state.is(Blocks.MYCELIUM));
        List<BakedQuad> tinted =
                new ArrayList<>(original.size());
        for (BakedQuad quad : original) {
            if (quad.isTinted()
                    || surfaceOnly
                            && quad.getDirection()
                                    != Direction.UP) {
                tinted.add(quad);
            }
            else {
                tinted.add(tintUntintedQuad(quad, aging));
            }
        }
        return tinted;
    }

    private static BakedQuad tintUntintedQuad(
            BakedQuad quad, float aging) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0;
                vertex < VERTEX_COUNT;
                vertex++) {
            int colorIndex =
                    vertex * VERTEX_STRIDE + COLOR_OFFSET;
            if (colorIndex >= vertices.length) {
                break;
            }
            int packed = vertices[colorIndex];
            int alpha = packed >>> 24;
            int originalRgb =
                    (packed & 0xFF) << 16
                            | (packed >> 8 & 0xFF) << 8
                            | packed >> 16 & 0xFF;
            int tintedRgb = AgingVisuals.tintBlockRgb(
                    originalRgb, aging);
            vertices[colorIndex] =
                    alpha << 24
                            | (tintedRgb & 0xFF) << 16
                            | (tintedRgb >> 8 & 0xFF) << 8
                            | tintedRgb >> 16 & 0xFF;
        }
        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade());
    }
}
