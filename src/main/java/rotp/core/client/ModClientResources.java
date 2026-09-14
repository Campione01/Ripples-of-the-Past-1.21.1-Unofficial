package rotp.core.client;

import java.util.HashSet;
import java.util.Set;

import rotp.core.client.entityanim.AnimationLoader;
import rotp.core.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import rotp.core.client.shader.ModShaders;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.clothes.client.layer.ClothesModelLoader;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ModClientResources {

	@SubscribeEvent
	public static void registerResourceLoaders(/*AddClientReloadListenersEvent*/RegisterClientReloadListenersEvent event) {
//		event.addListener(JojoMod.resLoc("resource_check"), new ResourcePathChecker.ResourceReloadNotifier());
		event.registerReloadListener(new ResourcePathChecker.ResourceReloadNotifier());
		RotpGeckoModelLoader.init(event);
		StandSkinsLoader.init(event);
		AnimationLoader.init(event);
		ClothesModelLoader.init(event);
		ModShaders.init(event);
	}
	
	public static Set<AutoCloseable> closeables = new HashSet<>();
}
