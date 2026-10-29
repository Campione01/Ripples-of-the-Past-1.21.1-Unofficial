package rotp.addons.stickyfingers.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererScaleInvoker {
    @Invoker("scale")
    void stickyFingers$invokeScale(
            LivingEntity target, PoseStack poseStack, float partialTick);
}
