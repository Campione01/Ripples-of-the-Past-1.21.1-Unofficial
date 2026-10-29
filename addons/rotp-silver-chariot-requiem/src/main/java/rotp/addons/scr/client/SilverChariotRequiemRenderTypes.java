package rotp.addons.scr.client;

import java.util.function.Function;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.scr.SilverChariotRequiemAddon;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Donor-compatible render state for the expanded outline shell.
 */
public final class SilverChariotRequiemRenderTypes extends RenderType {
    private static final RenderStateShard.TexturingStateShard
            FRONT_FACE_CULL = new RenderStateShard.TexturingStateShard(
                    SilverChariotRequiemAddon.MOD_ID
                            + ":front_face_cull",
                    () -> GL11.glCullFace(GL11.GL_FRONT),
                    () -> GL11.glCullFace(GL11.GL_BACK));
    private static final Function<ResourceLocation, RenderType>
            OUTLINE = Util.memoize(texture -> {
                CompositeState state = CompositeState.builder()
                        .setShaderState(
                                RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(
                                new RenderStateShard.TextureStateShard(
                                        texture, false, false))
                        .setTransparencyState(
                                TRANSLUCENT_TRANSPARENCY)
                        .setCullState(CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setTexturingState(FRONT_FACE_CULL)
                        .createCompositeState(false);
                return create(
                        SilverChariotRequiemAddon.MOD_ID
                                + ":outline_back_faces",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        1536,
                        true,
                        true,
                        state);
            });

    @Deprecated
    private SilverChariotRequiemRenderTypes(
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

    public static RenderType outline(ResourceLocation texture) {
        return OUTLINE.apply(texture);
    }
}
