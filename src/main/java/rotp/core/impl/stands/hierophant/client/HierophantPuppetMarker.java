package rotp.core.impl.stands.hierophant.client;

import java.util.List;
import java.util.Optional;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntity.StandFlag;
import rotp.core.impl.stands.hierophant.HierophantPuppetEffect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class HierophantPuppetMarker extends MarkerRenderer {

	public HierophantPuppetMarker(Minecraft mc) {
		super("puppet", mc);
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
		if (standPower == null) return;

		Optional<HierophantPuppetEffect> puppetingOptional = standPower.userStandEffects
				.getEffectOfType(ModStandAbilities.EFFECT_HG_PUPPET.get());
		if (!puppetingOptional.isPresent()) return;

		HierophantPuppetEffect puppeting = puppetingOptional.get();
		Entity puppetTarget = puppeting.getTarget();
		if (puppetTarget == null || !puppetTarget.isAlive()) return;

		StandEntity hierophant = standPower.getSummonedStandEntity();
		if (hierophant != null && hierophant.isAlive() && !hierophant.getStandFlag(StandFlag.MANUAL_CONTROL)) {
			list.add(new MarkerInstance(entityMarkerPos(puppetTarget, partialTick), false, puppetingOptional));
		}
	}
}
