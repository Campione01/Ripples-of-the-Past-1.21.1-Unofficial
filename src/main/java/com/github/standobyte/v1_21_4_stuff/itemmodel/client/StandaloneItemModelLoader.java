package com.github.standobyte.v1_21_4_stuff.itemmodel.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.util.reflection.ClientReflection;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class StandaloneItemModelLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	
	public static final Map<ResourceLocation, ModelResourceLocation> TO_BAKED_MODEL_ID = new HashMap<>();
	
	public static ModelResourceLocation getModelLocation(ResourceLocation itemComponentValue) {
		return TO_BAKED_MODEL_ID.get(itemComponentValue);
	}

	@SubscribeEvent
	public static void registerItemDefinitionLoader(RegisterClientReloadListenersEvent event) {
		// this would be simpler if the AddClientReloadListenersEvent, which sorts the listeners, was backported from Neo 1.21.4, but it is what it is
		Minecraft mc = Minecraft.getInstance();
		ReloadableResourceManager resourceManager = (ReloadableResourceManager) mc.getResourceManager();
		List<PreparableReloadListener> listeners = ClientReflection.getListeners(resourceManager);
		ModelManager vanillaModelManager = mc.getModelManager();
		int index = listeners.indexOf(vanillaModelManager);
		if (index >= 0) {
			listeners.add(index, new StandaloneItemModelLoader.ReloadListener());
		}
	}
	
	private static class ReloadListener implements PreparableReloadListener {
	    public static final FileToIdConverter ITEM_DEFINITION_LISTER = FileToIdConverter.json("items");
		@Override
		public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
				ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor,
				Executor gameExecutor) {
			CompletableFuture<Map<ResourceLocation, ResourceLocation>> definitions = CompletableFuture.supplyAsync(
					() -> ITEM_DEFINITION_LISTER.listMatchingResources(resourceManager), backgroundExecutor)
					.thenCompose(resources -> {
						List<CompletableFuture<Pair<ResourceLocation, ResourceLocation>>> loaded = new ArrayList<>(resources.size());

						for (Entry<ResourceLocation, Resource> resEntry : resources.entrySet()) {
							loaded.add(CompletableFuture.supplyAsync(() -> {
								Resource resource = resEntry.getValue();

								try (Reader reader = resource.openAsReader()) {
									JsonObject json = GsonHelper.parse(reader);
									ResourceLocation modelPath = fromItemDefinition(json);
									if (modelPath == null) {
										return null;
									}
									ResourceLocation filePath = resEntry.getKey();
									return Pair.of(ITEM_DEFINITION_LISTER.fileToId(filePath), modelPath);
								} catch (Exception exception) {
									LOGGER.error("Failed to load item definition {} from pack {}", resEntry.getKey(), resource.sourcePackId(), exception);
								}
								return null;

							}, backgroundExecutor));
						}

						return Util.sequence(loaded)
								.thenApply(list -> list.stream()
										.filter(Objects::nonNull)
										.collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
					});
			
			return definitions
					.thenAcceptAsync(map -> {
						for (var itemEntry : map.entrySet()) {
							TO_BAKED_MODEL_ID.put(itemEntry.getKey(), 
									ModelResourceLocation.standalone(itemEntry.getValue()));
						}
						LOGGER.debug("Found {} standalone item model definitions", TO_BAKED_MODEL_ID.size());
					})
					.thenAccept(preparationBarrier::wait);
		}
		
		public static ResourceLocation fromItemDefinition(JsonObject json) {
			JsonObject model = json.getAsJsonObject("model");
			if (model == null) {
				return null;
			}
			JsonElement modelPath = model.get("model");
			if (modelPath == null || !modelPath.isJsonPrimitive() || !modelPath.getAsJsonPrimitive().isString()) {
				return null;
			}
			return ResourceLocation.parse(modelPath.getAsString());
		}
	}

	@SubscribeEvent
	public static void registerBaked(ModelEvent.RegisterAdditional event) {
		TO_BAKED_MODEL_ID.values().forEach(event::register);
		LOGGER.debug("Registered {} standalone item models for baking", TO_BAKED_MODEL_ID.size());
	}
}
