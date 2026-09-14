package rotp.core.client.ui.marker;

import java.util.List;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.ClientsideAim;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class StandAimMarker extends MarkerRenderer {
	public static final GuiIcon icon = new GuiIcon(JojoMod.resLoc("textures/hud/stand_aim_marker.png"), 31, 31);
	
	public StandAimMarker(Minecraft mc) {
		super(icon, null, mc);
	}

	@Override
	protected boolean shouldRender() {
		if (!ClientModSettings.getSettingsReadOnly().standAimMarker || mc.player == null) return false;
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		return standPower != null && standPower.getSummonedStandEntity() != null;
	}

	@Override
	protected void updatePositions(List<MarkerInstance> list, float partialTick) {
		ActionTarget target = ClientsideAim.standAim.getTarget();
		switch (target.getType()) {
			case BLOCK -> list.add(new MarkerInstance(Vec3.atCenterOf(target.getBlockPos())));
			case ENTITY -> {
				Entity entity = target.getEntity();
				list.add(new MarkerInstance(entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2, 0)));
			}
			default -> {}
		}
	}

	@Override
	protected void renderAt(PoseStack poseStack, MarkerInstance marker, Camera camera, 
			Vec3 diff, float partialTick, StandSkin standSkin, int color) {
		poseStack.pushPose();

		double distance = diff.length();
		float scale = Math.min((float) Math.pow(2, (16 - Math.min(distance, 32)) / 16) * (float) distance / 256, 1);

		poseStack.translate(diff.x, diff.y, diff.z);
		poseStack.scale(-scale * 1.25f, -scale * 1.25f, 1);

		icon.render(poseStack, -icon.width / 2, -icon.height / 2);

		poseStack.popPose();
	}

}
