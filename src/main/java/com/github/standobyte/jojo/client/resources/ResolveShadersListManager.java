package com.github.standobyte.jojo.client.resources;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class ResolveShadersListManager {
	private static final ResourceLocation DEFAULT_LOCATION = JojoMod.resLoc("default");
	private static final ResourceLocation NO_STAND_LOCATION = JojoMod.resLoc("no_stand");
	private static final String POST_SHADER_PREFIX = "shaders/post/";
	private static final String JSON_SUFFIX = ".json";

	private List<ResourceLocation> shadersDefault = Collections.emptyList();
	private List<ResourceLocation> shadersNoStand = Collections.emptyList();
	private final Map<ResourceLocation, List<ResourceLocation>> shadersOverrides = new HashMap<>();

	public void reload(ResourceManager resourceManager) {
		Map<ResourceLocation, JsonArray> arrays = new HashMap<>();
		arrays.put(DEFAULT_LOCATION, readArray(resourceManager, DEFAULT_LOCATION));
		arrays.put(NO_STAND_LOCATION, readArray(resourceManager, NO_STAND_LOCATION));

		JojoRegistries.DEFAULT_STANDS_REG.entrySet().forEach(entry -> {
			ResourceLocation standId = entry.getKey().location();
			JsonArray jsonArray = readArray(resourceManager, standId);
			if (jsonArray != null) {
				arrays.put(standId, jsonArray);
			}
		});

		shadersDefault = toResourceLocationList(arrays.get(DEFAULT_LOCATION));
		shadersNoStand = toResourceLocationList(arrays.get(NO_STAND_LOCATION));

		shadersOverrides.clear();
		arrays.forEach((stand, jsonArray) -> {
			if (!stand.equals(DEFAULT_LOCATION) && !stand.equals(NO_STAND_LOCATION)) {
				shadersOverrides.put(stand, toResourceLocationList(jsonArray));
			}
		});
	}

	@Nullable
	private JsonArray readArray(ResourceManager resourceManager, ResourceLocation location) {
		ResourceLocation arrayPath = ResourceLocation.fromNamespaceAndPath(
				location.getNamespace(), "shaders/resolve/" + location.getPath() + JSON_SUFFIX);
		return resourceManager.getResource(arrayPath)
				.map(resource -> readArray(arrayPath, resource))
				.orElse(null);
	}

	@Nullable
	private JsonArray readArray(ResourceLocation arrayPath, Resource resource) {
		try (Reader reader = resource.openAsReader()) {
			JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
			JsonElement shaders = object.get("shaders");
			return shaders != null && shaders.isJsonArray() ? shaders.getAsJsonArray() : null;
		}
		catch (IOException | IllegalStateException | JsonParseException e) {
			JojoMod.getLogger().warn("Failed to load Resolve shader list {}", arrayPath, e);
			return null;
		}
	}

	private List<ResourceLocation> toResourceLocationList(@Nullable JsonArray jsonArray) {
		if (jsonArray == null) {
			return Collections.emptyList();
		}
		return jsonArray.asList().stream()
				.map(JsonElement::getAsString)
				.map(this::normalizeShaderPath)
				.toList();
	}

	private ResourceLocation normalizeShaderPath(String rawPath) {
		ResourceLocation id = ResourceLocation.parse(rawPath);
		String namespace = id.getNamespace().equals("jojo") ? JojoMod.MOD_ID : id.getNamespace();
		String path = id.getPath();
		if (path.startsWith(POST_SHADER_PREFIX)) {
			path = path.substring(POST_SHADER_PREFIX.length());
		}
		if (path.endsWith(JSON_SUFFIX)) {
			path = path.substring(0, path.length() - JSON_SUFFIX.length());
		}
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	public List<ResourceLocation> getShadersImmutableList(ResourceLocation standName) {
		return shadersOverrides.getOrDefault(standName, shadersDefault);
	}

	@Nullable
	public ResourceLocation getRandomShader(@Nullable StandPower standPower, Random random) {
		List<ResourceLocation> shaders = standPower != null && standPower.hasPower()
				? getShadersImmutableList(standPower.getPowerType().getId())
				: shadersNoStand;
		if (shaders.isEmpty()) {
			return null;
		}
		return shaders.get(random.nextInt(shaders.size()));
	}
}
