package rotp.addons.heavensdoor.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import rotp.core.api.client.render.FirstPersonPostArmLayers;
import rotp.core.client.entityrender.stand.StandVisualContext;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(
        modid = HeavensDoorAddon.MOD_ID,
        value = Dist.CLIENT)
public final class HeavensDoorBookOverlayRenderer {
    private static final Map<ResourceLocation, TextureSize>
            TEXTURE_SIZE_CACHE = new HashMap<>();

    private HeavensDoorBookOverlayRenderer() {}

    public static void renderFirstPersonBook(
            FirstPersonPostArmLayers.Context arm) {
        if (!(arm.entity() instanceof AbstractClientPlayer player)
                || player.isSpectator()) {
            return;
        }
        BookState state = HeavensDoorAddon.bookState(player);
        if (!state.active()) {
            return;
        }
        StandVisualContext context =
                HeavensDoorVisuals.fromBookTarget(
                        player, arm.partialTick());
        if (!HeavensDoorVisuals.canRender(context)) {
            return;
        }

        int stage = Math.clamp(
                state.bookAmplifier() + 1, 1, 3);
        ResourceLocation texture = HeavensDoorAddon.id(
                "textures/layer/book_effect_layer/t64x64/"
                        + stage + ".png");
        VertexConsumer vertices = arm.buffer().getBuffer(
                RenderType.entityTranslucent(texture));
        arm.renderArm(vertices);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideNearbyEntities(
            RenderLivingEvent.Pre<?, ?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && event.getEntity() != minecraft.player
                && minecraft.player.hasEffect(
                        HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT)
                && minecraft.player.distanceToSqr(
                        event.getEntity()) <= 100.0D) {
            event.setCanceled(true);
        }
    }

    /**
     * The book pages drawn over the target's own body.
     *
     * <p>This used to run from {@code RenderLivingEvent.Post}, but NeoForge fires that event after
     * {@code LivingEntityRenderer.render} has already popped the pose it set up, so the model was re-rendered outside
     * the entity's transform and never appeared. 1.16's Heaven's Door hung its BookLayer on the renderer;
     * a {@link RenderLayer} is the same thing here and runs inside the body transform.
     */
    public static final class Layer<
            T extends LivingEntity, M extends EntityModel<T>>
            extends RenderLayer<T, M> {
        private final RenderLayerParent<T, M> parent;

        public Layer(RenderLayerParent<T, M> parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public void render(
                PoseStack poseStack,
                MultiBufferSource buffer,
                int packedLight,
                T entity,
                float limbSwing,
                float limbSwingAmount,
                float partialTick,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            LivingEntity stateTarget =
                    entity instanceof StandEntity stand
                            && stand.getUser() != null
                            ? stand.getUser()
                            : entity;
            BookState state =
                    HeavensDoorAddon.bookState(stateTarget);
            if (!state.active() || entity.isInvisible()) {
                return;
            }
            StandVisualContext context =
                    HeavensDoorVisuals.fromBookTarget(
                            stateTarget, partialTick);
            if (!HeavensDoorVisuals.canRender(context)) {
                return;
            }

            ResourceLocation baseTexture =
                    parent.getTextureLocation(entity);
            TextureSize size = TEXTURE_SIZE_CACHE.computeIfAbsent(
                    baseTexture,
                    HeavensDoorBookOverlayRenderer
                            ::readTextureSize);
            int stage = Math.clamp(
                    state.bookAmplifier() + 1, 1, 3);
            ResourceLocation texture = HeavensDoorAddon.id(
                    "textures/layer/book_effect_layer/t"
                            + size.width
                            + "x"
                            + size.height
                            + "/"
                            + stage
                            + ".png");
            VertexConsumer vertices = buffer.getBuffer(
                    RenderType.entityTranslucent(texture));
            float poseScale = 1.002F;
            int packedOverlay =
                    LivingEntityRenderer.getOverlayCoords(
                            entity, 0.0F);
            int color = 0xFFFFFFFF;
            poseStack.pushPose();
            poseStack.scale(poseScale, poseScale, poseScale);
            getParentModel().renderToBuffer(
                    poseStack,
                    vertices,
                    packedLight,
                    packedOverlay,
                    color);
            poseStack.popPose();
            HeavensDoorBookRenderDiagnostics.recordOverlayDraw(
                    stateTarget,
                    entity,
                    state,
                    stage,
                    baseTexture,
                    texture,
                    size.width(),
                    size.height(),
                    parent.getClass().getName(),
                    getParentModel().getClass().getName(),
                    packedLight,
                    packedOverlay,
                    poseScale,
                    color,
                    context.effectiveAlpha());
        }
    }

    private static TextureSize readTextureSize(
            ResourceLocation texture) {
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(texture)
                .map(HeavensDoorBookOverlayRenderer::readTextureSize)
                .orElse(TextureSize.DEFAULT);
    }

    private static TextureSize readTextureSize(Resource resource) {
        try (InputStream stream = resource.open();
                NativeImage image = NativeImage.read(stream)) {
            int width = closestBase(image.getWidth());
            int height = image.getHeight() * 4 >= image.getWidth() * 3
                    ? width
                    : width / 2;
            return new TextureSize(width, height);
        }
        catch (IOException | RuntimeException ignored) {
            return TextureSize.DEFAULT;
        }
    }

    private static int closestBase(int value) {
        if (value <= 96) {
            return 64;
        }
        if (value <= 192) {
            return 128;
        }
        return 256;
    }

    private record TextureSize(int width, int height) {
        private static final TextureSize DEFAULT =
                new TextureSize(64, 64);
    }
}
