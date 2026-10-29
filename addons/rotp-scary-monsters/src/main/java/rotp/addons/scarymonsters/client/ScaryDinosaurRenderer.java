package rotp.addons.scarymonsters.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.parsemodel.loader.ResourceModelEntry;
import rotp.core.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import rotp.core.core.JojoMod;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The 1.16 renderer switched to the {@code scarymonsters} geometry, texture and clips while a
 * player was controlling the dinosaur; a wild one used the {@code dinosaur} set. It also scaled the
 * whole model by the source creature's bounding box (see {@link ScaryDinosaurEntity#getSourceWidth()}).
 */
public final class ScaryDinosaurRenderer extends SimpleEntityRenderer<
        ScaryDinosaurEntity, ScaryDinosaurModel> {
    private static final ResourceLocation DINOSAUR_TEXTURE =
            ScaryMonstersAddon.id("textures/entity/dinosaur.png");
    private static final ResourceLocation CONTROLLED_TEXTURE =
            ScaryMonstersAddon.id("textures/entity/scarymonsters.png");
    /** The 1.16 reference box (1.2 x 1.8) that maps to the 0.6 base scale, clamped to [0.3, 3]. */
    private static final float REFERENCE_WIDTH = 1.2F;
    private static final float REFERENCE_HEIGHT = 1.8F;
    private static final float BASE_SCALE = 0.6F;
    private static final float MIN_SCALE = 0.3F;
    private static final float MAX_SCALE = 3.0F;

    private final ResourceModelEntry controlledModel;

    public ScaryDinosaurRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(DINOSAUR_TEXTURE, false);
        initResourceModel(
                JojoMod.resLoc("scary_monsters_dinosaur"),
                ScaryDinosaurModel::new,
                false);
        controlledModel = RotpGeckoModelLoader.getInstance()
                .getModelContainer(JojoMod.resLoc("scary_monsters_dinosaur_controlled"));
        controlledModel.rendererInit(
                root -> new ScaryDinosaurModel(root, ScaryDinosaurModel.CONTROLLED_ANIMS));
    }

    /** A dinosaur with a player on it is drawn as a controlled one too. */
    private static boolean isPlayerControlled(ScaryDinosaurEntity entity) {
        return entity.isPlayerControlled() || entity.getFirstPassenger() instanceof Player;
    }

    @Override
    protected ScaryDinosaurModel getEntityModel(ScaryDinosaurEntity entity) {
        if (isPlayerControlled(entity)) {
            ScaryDinosaurModel model = controlledModel.getModel();
            if (model != null) {
                return model;
            }
        }
        return super.getEntityModel(entity);
    }

    @Override
    public ResourceLocation getTextureLocation(ScaryDinosaurEntity entity) {
        return isPlayerControlled(entity) ? CONTROLLED_TEXTURE : DINOSAUR_TEXTURE;
    }

    public static float sourceScale(ScaryDinosaurEntity entity) {
        float width = entity.getSourceWidth();
        float height = entity.getSourceHeight();
        if (width <= 0.0F || height <= 0.0F) {
            return 1.0F;
        }
        float areaRatio = width * height / (REFERENCE_WIDTH * REFERENCE_HEIGHT);
        return Mth.clamp(Mth.sqrt(areaRatio) * BASE_SCALE, MIN_SCALE, MAX_SCALE);
    }

    @Override
    public void render(
            ScaryDinosaurEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float scale = sourceScale(entity);
        if (scale == 1.0F) {
            super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
