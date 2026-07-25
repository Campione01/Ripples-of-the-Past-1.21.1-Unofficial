package com.github.standobyte.jojo.client;

import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.client.ui.marker.StandAimMarker;
import com.github.standobyte.jojoimpl.stands.crazydiamond.client.CrazyDBloodHomingMarker;
import com.github.standobyte.jojoimpl.stands.crazydiamond.client.CrazyDOriginPosAnchorMarker;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.GoldExperienceLifeformMarker;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.GoldExperienceLifeformRevertMarker;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.GoldExperienceMarkedItemMarker;
import com.github.standobyte.jojoimpl.stands.hierophant.client.HierophantGreenBarrierDetectionMarker;
import com.github.standobyte.jojoimpl.stands.hierophant.client.HierophantPuppetMarker;

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
