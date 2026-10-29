package rotp.addons.nextalbum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class AjaStoneCurioRenderer implements ICurioRenderer {
    private final AjaStoneNecklaceRenderer renderer =
            new AjaStoneNecklaceRenderer();

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>>
            void render(
                    ItemStack stack,
                    SlotContext slotContext,
                    PoseStack poseStack,
                    RenderLayerParent<T, M> renderLayerParent,
                    MultiBufferSource bufferSource,
                    int light,
                    float limbSwing,
                    float limbSwingAmount,
                    float partialTicks,
                    float ageInTicks,
                    float netHeadYaw,
                    float headPitch) {
        if (!(renderLayerParent.getModel()
                instanceof HumanoidModel<?> humanoid)
                || !(stack.getItem()
                        instanceof AjaStoneNecklaceItem)) {
            return;
        }
        renderer.prepForRender(
                slotContext.entity(),
                stack,
                EquipmentSlot.HEAD,
                humanoid,
                bufferSource,
                partialTicks,
                limbSwing,
                limbSwingAmount,
                netHeadYaw,
                headPitch);
        renderer.renderToBuffer(
                poseStack,
                null,
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
