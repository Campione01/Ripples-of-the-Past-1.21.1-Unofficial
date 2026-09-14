package rotp.core.impl.stands.hierophant.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands.hierophant.HGBarrierEntity;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class HierophantGreenBarrierDetectionMarker extends MarkerRenderer {

	public HierophantGreenBarrierDetectionMarker(Minecraft mc) {
		super("barrier", mc);
		renderThroughBlocks = true;
		useStandSkinColor = true;
	}

	@Override
	protected boolean shouldRender() {
		return true;
	}

	@Override
	protected void updatePositions(List<MarkerInstance> list, float partialTick) {
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower == null || !(standPower.getSummonedStandEntity() instanceof HierophantGreenEntity hierophant)) {
			return;
		}

		Set<Vec3> added = new HashSet<>();
		hierophant.getBarriersNet().wasRippedAt().forEach(point -> addMarker(list, added, point));

		if (mc.level == null) {
			return;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof HGBarrierEntity barrier && barrier.wasRipped() && isOwnedBy(barrier, hierophant)) {
				barrier.wasRippedAt().ifPresent(point -> addMarker(list, added, point));
			}
		}
	}

	private static boolean isOwnedBy(HGBarrierEntity barrier, HierophantGreenEntity hierophant) {
		Entity owner = barrier.getOwner();
		return owner != null && owner.is(hierophant);
	}

	private static void addMarker(List<MarkerInstance> list, Set<Vec3> added, Vec3 point) {
		if (added.add(point)) {
			list.add(new MarkerInstance(point, false));
		}
	}
}
