package rotp.core.client;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import rotp.core.client.entityanim.AnimationLoader;
import rotp.core.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import rotp.core.client.resources.ModSplashes;
import rotp.core.client.shader.ModShaders;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.clothes.client.layer.ClothesModelLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ModClientResources {
	private static final ModSplashes MOD_SPLASHES = new ModSplashes();
	private static Field titleScreenSplash;

	@SubscribeEvent
	public static void registerResourceLoaders(/*AddClientReloadListenersEvent*/RegisterClientReloadListenersEvent event) {
//		event.addListener(JojoMod.resLoc("resource_check"), new ResourcePathChecker.ResourceReloadNotifier());
		event.registerReloadListener(new ResourcePathChecker.ResourceReloadNotifier());
		RotpGeckoModelLoader.init(event);
		StandSkinsLoader.init(event);
		AnimationLoader.init(event);
		ClothesModelLoader.init(event);
		ModShaders.init(event);
		event.registerReloadListener(MOD_SPLASHES);
	}

	// 1.16 onScreenOpened: runs before TitleScreen.init, which keeps a non-null splash
	@SubscribeEvent
	public static void onScreenOpening(ScreenEvent.Opening event) {
		if (event.getNewScreen() instanceof TitleScreen titleScreen) {
			String splash = MOD_SPLASHES.overrideSplash(Minecraft.getInstance().getUser().getName());
			if (splash != null) {
				setTitleSplash(titleScreen, splash);
			}
		}
	}

	private static void setTitleSplash(TitleScreen screen, String splash) {
		try {
			if (titleScreenSplash == null) {
				titleScreenSplash = ObfuscationReflectionHelper.findField(TitleScreen.class, "splash");
			}
			titleScreenSplash.set(screen, new SplashRenderer(splash));
		} catch (RuntimeException | IllegalAccessException e) {
			JojoMod.getLogger().warn("Could not set the title screen splash", e);
		}
	}

	public static Set<AutoCloseable> closeables = new HashSet<>();
}
