package com.github.standobyte.jojo.client.itemrender;

import org.joml.Matrix4f;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModMapDecorationTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.MapDecorationTextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.map.RegisterMapDecorationRenderersEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class PillarmanTempleMapDecorationRenderer {
    private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/map/pillarman_temple.png");

    private PillarmanTempleMapDecorationRenderer() {}

    @SubscribeEvent
    public static void registerMapDecorationRenderer(RegisterMapDecorationRenderersEvent event) {
        event.register(ModMapDecorationTypes.PILLARMAN_TEMPLE.get(), PillarmanTempleMapDecorationRenderer::render);
    }

    private static boolean render(MapDecoration decoration, PoseStack poseStack, MultiBufferSource bufferSource,
            MapItemSavedData mapData, MapDecorationTextureManager decorationTextures, boolean inItemFrame,
            int packedLight, int index) {
        poseStack.pushPose();
        poseStack.translate((float) decoration.x() / 2.0F + 64.0F, (float) decoration.y() / 2.0F + 64.0F, -0.02F);
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) (decoration.rot() * 360) / 16.0F));
        poseStack.scale(4.0F, 4.0F, 3.0F);
        poseStack.translate(-0.125F, 0.125F, 0.0F);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.text(TEXTURE));
        float z = (float) index * -0.001F;
        vertices.addVertex(matrix, -2.0F, 2.0F, z).setColor(-1).setUv(1.0F, 0.0F).setLight(packedLight);
        vertices.addVertex(matrix, 2.0F, 2.0F, z).setColor(-1).setUv(0.0F, 0.0F).setLight(packedLight);
        vertices.addVertex(matrix, 2.0F, -2.0F, z).setColor(-1).setUv(0.0F, 1.0F).setLight(packedLight);
        vertices.addVertex(matrix, -2.0F, -2.0F, z).setColor(-1).setUv(1.0F, 1.0F).setLight(packedLight);
        poseStack.popPose();
        return true;
    }
}
