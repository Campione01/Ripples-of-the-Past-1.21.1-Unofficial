package rotp.core.impl.stands.magiciansred.client;

import rotp.core.client.VisualPipelineDiagnostics;
import rotp.core.impl.stands.magiciansred.MRFireballEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class MRFireballRenderer extends ThrownItemRenderer<MRFireballEntity> {

	public MRFireballRenderer(EntityRendererProvider.Context context) {
		super(context, 1.0F, true);
	}

	@Override
	public void render(MRFireballEntity entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		VisualPipelineDiagnostics.logEntityVisibilityOnce("mr_fireball_render_gate", entity, "MR fireball renderer gate reached");
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			VisualPipelineDiagnostics.logOnce("mr_fireball_render",
					"MR fireball renderer reached: entityId={}, item={}, pos={}.",
					entity.getId(), entity.getItem(), entity.position());
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}
}
