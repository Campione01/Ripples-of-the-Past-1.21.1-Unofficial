package rotp.addons.purplehaze.client;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.entity.PurpleHazeCloudEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class PurpleHazeCloudRenderer
        extends EntityRenderer<PurpleHazeCloudEntity> {
    private static final ResourceLocation TEXTURE =
            PurpleHazeAddon.id(
                    "textures/particle/purple_haze_virus.png");

    public PurpleHazeCloudRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            PurpleHazeCloudEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            PurpleHazeCloudEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        // The donor cloud is particle-only; an extra entity quad casts a rectangular shadow.
    }
}
