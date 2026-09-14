package rotp.core.client.shader;

import rotp.core.core.JojoMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.RegisterStageEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class CustomLevelRenderStages {
	public static RenderLevelStageEvent.Stage BEFORE_SPECTATOR_SHADER;

	@SubscribeEvent
	public static void registerStages(RegisterStageEvent event) {
		BEFORE_SPECTATOR_SHADER = event.register(JojoMod.resLoc("before_spectator_shader"), null);
	}
}
