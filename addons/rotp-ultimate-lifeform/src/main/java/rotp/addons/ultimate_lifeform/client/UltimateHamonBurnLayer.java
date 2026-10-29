package rotp.addons.ultimate_lifeform.client;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.layer.HamonBurnLayer.TextureSize;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 UltimateHamonBurnLayer, added to every living renderer: the core's hamon_burn texture of the burn level
 * (rotp_ul:hamon_spread, up to IV) over the entity, and on the first-person arm.
 */
public final class UltimateHamonBurnLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> implements FirstPersonModelLayer {
    private static final Map<TextureSize, ResourceLocation[]> TEXTURES = new EnumMap<>(TextureSize.class);

    static {
        for (TextureSize size : TextureSize.values()) {
            // the core's textures: hamon_burn/t64x32 ... t256x256
            String dir = "textures/entity/layer/hamon_burn/t" + size.name().substring(1);
            ResourceLocation[] levels = new ResourceLocation[4];
            for (int level = 0; level < 4; level++) {
                levels[level] = ResourceLocation.fromNamespaceAndPath("jojo_ripples", dir + "/" + (level + 1) + ".png");
            }
            TEXTURES.put(size, levels);
        }
    }

    public UltimateHamonBurnLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        ResourceLocation texture = getTexture(getParentModel(), entity);
        if (texture == null) {
            return;
        }
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(texture)),
                packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
    }

    @Nullable
    private static ResourceLocation getTexture(EntityModel<?> model, LivingEntity entity) {
        MobEffectInstance burn = entity.getEffect(UltimateLifeformAddon.HAMON_SPREAD);
        if (burn == null) {
            return null;
        }
        return TEXTURES.get(TextureSize.getClosestTexSize(model))[Math.min(burn.getAmplifier(), 3)];
    }

    @Override
    public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer, int light,
            LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
        if (entityRenderer.getModel() instanceof HumanoidModel<?> model) {
            FirstPersonModelLayer.defaultRender(side, poseStack, buffer, light, entity, entityRenderer,
                    model, getTexture(model, entity), partialTick);
        }
    }
}
