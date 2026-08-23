package com.github.standobyte.jojo.client.shader.core;

import java.io.IOException;

import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/** A core shader whose caller, rather than the world pipeline, owns the draw target. */
public final class PrivateTargetShaderInstance extends ShaderInstance {
    public PrivateTargetShaderInstance(
            ResourceProvider resourceProvider,
            ResourceLocation path,
            VertexFormat vertexFormat) throws IOException {
        super(resourceProvider, path, vertexFormat);
    }

    /**
     * Iris detects this method reflectively and leaves the caller-bound FBO in
     * place. The method has no Iris type dependency and is inert without Iris.
     */
    public boolean iris$skipDraw() {
        return true;
    }
}
