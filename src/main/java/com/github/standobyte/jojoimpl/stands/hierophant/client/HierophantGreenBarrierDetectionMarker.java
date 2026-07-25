package com.github.standobyte.jojoimpl.stands.hierophant.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.standobyte.jojo.client.ClientPowerCache;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojoimpl.stands.hierophant.HGBarrierEntity;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantGreenEntity;

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
