package rotp.addons.ger.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class GerRtzRenderTypes extends RenderType {
    private static final RenderType PARTICLE_STORM = create(
            GoldExperienceRequiemAddon.MOD_ID
                    + ":rtz_particle_storm",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            BIG_BUFFER_SIZE,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    @Deprecated
    private GerRtzRenderTypes(
            String name,
            VertexFormat format,
            VertexFormat.Mode mode,
            int bufferSize,
            boolean affectsCrumbling,
            boolean sortOnUpload,
            Runnable setupState,
            Runnable clearState) {
        super(
                name,
                format,
                mode,
                bufferSize,
                affectsCrumbling,
                sortOnUpload,
                setupState,
                clearState);
    }

    static RenderType particleStorm() {
        return PARTICLE_STORM;
    }
}
