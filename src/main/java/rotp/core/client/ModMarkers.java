package rotp.core.client;

import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.client.ui.marker.StandAimMarker;
import rotp.core.impl.stands.crazydiamond.client.CrazyDBloodHomingMarker;
import rotp.core.impl.stands.crazydiamond.client.CrazyDOriginPosAnchorMarker;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeformMarker;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeformRevertMarker;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceMarkedItemMarker;
import rotp.core.impl.stands.hierophant.client.HierophantGreenBarrierDetectionMarker;
import rotp.core.impl.stands.hierophant.client.HierophantPuppetMarker;

import net.minecraft.client.Minecraft;

public class ModMarkers {

	public static void registerMarkers(Minecraft mc) {
		MarkerRenderer.registerMarkerRenderer(new StandAimMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new CrazyDOriginPosAnchorMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new CrazyDBloodHomingMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new GoldExperienceLifeformMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new GoldExperienceLifeformRevertMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new GoldExperienceMarkedItemMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new HierophantGreenBarrierDetectionMarker(mc));
		MarkerRenderer.registerMarkerRenderer(new HierophantPuppetMarker(mc));
	}
}
