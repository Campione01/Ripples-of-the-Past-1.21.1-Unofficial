package rotp.core.client.entityanim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.core.JojoMod;
import rotp.core.util.functions.JSONUtil;
import rotp.core.util.functions.StringUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import rotp.core.compat.v1_21_4.missingmethods.Zone;
import rotp.core.compat.v1_21_4.missingmethods._ProfilerFiller;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforgespi.language.IModInfo;

public class AnimationLoader extends SimplePreparableReloadListener<Map<ResourceLocation, AnimationSet.Builder>> {
	private static AnimationLoader instance;
	private static final Map<ResourceLocation, ResourceLocation> ANIMATION_SET_ALIASES = Map.ofEntries(
			Map.entry(JojoMod.resLoc("pillarman"), JojoMod.resLoc("pillar_man")),
			Map.entry(JojoMod.resLoc("vampirism"), JojoMod.resLoc("vampire")),
			// 1.16 zombie claw swipe reused the vampire clip
			Map.entry(JojoMod.resLoc("zombie"), JojoMod.resLoc("vampire")));

	/** Animation file an action anim set id reads from (the id itself when not aliased). */
	public static ResourceLocation animSetAlias(ResourceLocation geckoAnimFilePath) {
		return ANIMATION_SET_ALIASES.getOrDefault(geckoAnimFilePath, geckoAnimFilePath);
	}

	@ApiStatus.Internal
	public static void init(/*AddClientReloadListenersEvent*/RegisterClientReloadListenersEvent event) {
		if (instance == null) {
			instance = new AnimationLoader();
		}
//		event.addListener(JojoMod.resLoc("entityanim"), instance);
		event.registerReloadListener(instance);
		KeyframesMolangEngine.init();
	}
	
	public static AnimationLoader getInstance() {
		return instance;
	}
	
	
	private Map<ResourceLocation, AnimationSet> anims = new HashMap<>();
	
	@Nullable
	public AnimationSet getAnimSet(ResourceLocation geckoAnimFilePath) {
		AnimationSet direct = anims.get(geckoAnimFilePath);
		if (direct != null) {
			return direct;
		}
		ResourceLocation aliasedPath = animSetAlias(geckoAnimFilePath);
		return aliasedPath.equals(geckoAnimFilePath) ? null : anims.get(aliasedPath);
	}
	

	private static final String TOP_DIR = "animations";
	private static final String EXTENSION = ".animation.json";
	
	@Override
	protected Map<ResourceLocation, AnimationSet.Builder> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<ResourceLocation, AnimationSet.Builder> anims = new HashMap<>();

		try (Zone zone = _ProfilerFiller.zone(profiler, JojoMod.MOD_ID + "_animations")) {
			Map<ResourceLocation, List<Resource>> resources = resourceManager.listResourceStacks(TOP_DIR, path -> path.getPath().endsWith(EXTENSION));
			Set<String> ownNamespaces = ownNamespaces(resources, coreAndDependentModIds());
			for (var resourceEntry : resources.entrySet()) {
				ResourceLocation resourcePathFull = resourceEntry.getKey();
				ResourceLocation animPath = resourcePathFull.withPath(
						StringUtil.trimEnding(resourceEntry.getKey().getPath(), EXTENSION).substring(TOP_DIR.length() + 1));
				AnimationSet.Builder anim = loadAnimations(resourceEntry.getValue(), resourcePathFull,
						ownNamespaces.contains(resourcePathFull.getNamespace()));
				if (!anim.isEmpty()) {
					anims.put(animPath, anim);
				}
			}
		}
		
		return anims;
	}
	
	/** The core and the mods that declare a dependency on it: their packs hold animations in our format. */
	protected Set<String> coreAndDependentModIds() {
		Set<String> modIds = new HashSet<>();
		modIds.add(JojoMod.MOD_ID);
		ModList modList = ModList.get();
		if (modList != null) {
			for (IModInfo mod : modList.getMods()) {
				if (mod.getDependencies().stream().anyMatch(dependency -> JojoMod.MOD_ID.equals(dependency.getModId()))) {
					modIds.add(mod.getModId());
				}
			}
		}
		return modIds;
	}
	
	private static final String MOD_PACK_PREFIX = "mod/";
	
	/**
	 * 1.16 scanned every namespace too, but only parsed the files of registered Stand models. The port reads
	 * animation sets by any id, so a namespace counts as ours when it is one of those mods' ids or when one of
	 * their packs puts a file into it (add-ons keep 1.16 asset namespaces that differ from their mod id).
	 */
	static Set<String> ownNamespaces(Map<ResourceLocation, List<Resource>> resources, Set<String> ownModIds) {
		Set<String> namespaces = new HashSet<>(ownModIds);
		for (var resourceEntry : resources.entrySet()) {
			String namespace = resourceEntry.getKey().getNamespace();
			if (!namespaces.contains(namespace)
					&& resourceEntry.getValue().stream().anyMatch(file -> isModPackOf(file.sourcePackId(), ownModIds))) {
				namespaces.add(namespace);
			}
		}
		return namespaces;
	}
	
	// NeoForge names a mod file's pack "mod/<id>", with the ids comma-separated when the file holds several mods
	private static boolean isModPackOf(String packId, Set<String> modIds) {
		if (!packId.startsWith(MOD_PACK_PREFIX)) {
			return false;
		}
		for (String modId : packId.substring(MOD_PACK_PREFIX.length()).split(",")) {
			if (modIds.contains(modId)) {
				return true;
			}
		}
		return false;
	}
	
	public static AnimationSet.Builder loadAnimations(List<Resource> resources, ResourceLocation resourcePath) {
		return loadAnimations(resources, resourcePath, true);
	}
	
	/**
	 * @param ownFile false for a file of a namespace that is neither the core's nor an add-on's: other mods keep
	 *                their own formats under the same folder and extension, so what cannot be read there is skipped
	 *                with a debug line instead of an error
	 */
	public static AnimationSet.Builder loadAnimations(List<Resource> resources, ResourceLocation resourcePath, boolean ownFile) {
		AnimationSet.Builder animationSet = new AnimationSet.Builder();
		for (var animFile : resources) {
			try (var reader = animFile.openAsReader()) {
				JsonObject json = JSONUtil.parse(reader);
				addAnimsToAnimSet(json, animationSet, resourcePath, animFile.sourcePackId(), ownFile);
			}
			catch (Exception e) {
				if (ownFile) {
					JojoMod.getLogger().error("Failed to read animations from {} in pack {}", resourcePath, animFile.sourcePackId(), e);
				}
				else {
					JojoMod.getLogger().debug("Skipped {} in pack {}, not a {} animation file: {}",
							resourcePath, animFile.sourcePackId(), JojoMod.MOD_ID, e.toString());
				}
			}
		}
		return animationSet;
	}
	
	public static void addAnimsToAnimSet(JsonObject json, AnimationSet.Builder animSetBuilder, ResourceLocation resPath) {
		addAnimsToAnimSet(json, animSetBuilder, resPath, null);
	}
	
	public static void addAnimsToAnimSet(JsonObject json, AnimationSet.Builder animSetBuilder, ResourceLocation resPath, @Nullable String sourcePackId) {
		addAnimsToAnimSet(json, animSetBuilder, resPath, sourcePackId, true);
	}
	
	public static void addAnimsToAnimSet(JsonObject json, AnimationSet.Builder animSetBuilder, ResourceLocation resPath,
			@Nullable String sourcePackId, boolean ownFile) {
		JsonObject modelAnimsJson = json.getAsJsonObject().getAsJsonObject("animations");
		for (Map.Entry<String, JsonElement> animJsonEntry : modelAnimsJson.entrySet()) {
			String animName = animJsonEntry.getKey();
			if (!animName.startsWith("unused#")) {
				try {
					JsonObject animJson = animJsonEntry.getValue().getAsJsonObject();
					RotpAnimDefinition anim = ParseGeckoAnims.parseAnim(animJson);
					animSetBuilder.putNamedAnim(animName, anim);
				}
				catch (Exception e) {
					if (!ownFile) {
						JojoMod.getLogger().debug("Skipped animation {} from {} in pack {}, not in the {} format: {}",
								animName, resPath, sourcePackId, JojoMod.MOD_ID, e.toString());
					}
					else if (sourcePackId != null) {
						JojoMod.getLogger().error("Failed to load animation {} from {} in pack {}", animName, resPath, sourcePackId, e);
					}
					else {
						JojoMod.getLogger().error("Failed to load animation {} from {}", animName, resPath, e);
					}
					continue;
				}
			}
		}
	}
	
	
	@Override
	protected void apply(Map<ResourceLocation, AnimationSet.Builder> skinsRead, ResourceManager resourceManager, ProfilerFiller profiler) {
		this.anims.clear();
		skinsRead.forEach((key, animBuilder) -> this.anims.put(key, animBuilder.build()));
		JojoMod.getLogger().info("Loaded {} entity animation files", this.anims.size());
	}
	
}
