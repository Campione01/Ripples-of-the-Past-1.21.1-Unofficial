package rotp.core.mixin.client.v1_21_1_modelanim.barrage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.mixin.client.v1_21_1_modelanim.player.AgeableModelMixinSuperclass;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin extends AgeableModelMixinSuperclass {

	@Override
	public void jojo_ripples$thenRenderBarrageSwings(PoseStack poseStack, VertexConsumer buffer, 
			int packedLight, int packedOverlay, int color, CallbackInfo ci) {
		if (BarrageSwings.currentlyRendering != null) {
			BarrageSwings.currentlyRendering.renderLayerBarrage((EntityModel<?>) (Object) this, 
					poseStack, buffer, packedLight, packedOverlay, color,
					RenderStateCrutches.currentEntityRenderState != null
							? RenderStateCrutches.currentEntityRenderState.xRot : 0);
		}
	}
}
