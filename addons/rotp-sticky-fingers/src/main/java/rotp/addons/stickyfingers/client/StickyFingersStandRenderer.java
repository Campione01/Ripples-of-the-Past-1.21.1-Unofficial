package rotp.addons.stickyfingers.client;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import rotp.addons.stickyfingers.ability.StickyFingersExtendedPunchAbility.ExtendedPunchAction;
import rotp.addons.stickyfingers.entity.StickyFingersStandEntity;
import rotp.core.client.entityrender.ModelPartWithName;
import rotp.core.client.entityrender.ModelWithExtraFeatures;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandEntityRenderState;

public final class StickyFingersStandRenderer extends StandEntityRenderer<
        StickyFingersStandEntity, StandEntityRenderState,
        StandEntityModel<StickyFingersStandEntity, StandEntityRenderState>> {
    public StickyFingersStandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void doRender(StickyFingersStandEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        // The donor restores the forearm when the task stops, not when its projectile disappears.
        boolean detached = entity.getCurStandAction() instanceof ExtendedPunchAction;
        withForearmHidden(detached,
                detached ? modelPart(model, "right_arm_bend") : null,
                detached ? modelPart(model, "right_arm_joint") : null,
                () -> super.doRender(entity, entityYaw, partialTicks, poseStack, bufferSource, light));
    }

    @Nullable
    private static ModelPart modelPart(@Nullable StandEntityModel<?, ?> model, String name) {
        if (model == null) return null;
        ModelPartWithName[] path = ((ModelWithExtraFeatures) model).jojo_ripples$getPathToModelPart(name);
        return path != null && path.length > 0 ? path[path.length - 1].part() : null;
    }

    static void withForearmHidden(boolean detached, @Nullable ModelPart forearm,
            @Nullable ModelPart joint, Runnable render) {
        if (!detached) {
            render.run();
            return;
        }
        Map<ModelPart, Boolean> previous = new IdentityHashMap<>();
        try {
            // setupAnim and obstruction passes reset visible, but leave skipDraw intact.
            suppressTree(forearm, previous);
            suppressTree(joint, previous);
            render.run();
        }
        finally {
            previous.forEach((part, skipDraw) -> part.skipDraw = skipDraw);
        }
    }

    private static void suppressTree(@Nullable ModelPart part, Map<ModelPart, Boolean> previous) {
        if (part == null || previous.containsKey(part)) return;
        previous.put(part, part.skipDraw);
        part.skipDraw = true;
        for (ModelPart child : part.children.values()) suppressTree(child, previous);
    }
}
