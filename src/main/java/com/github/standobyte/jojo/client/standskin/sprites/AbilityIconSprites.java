package com.github.standobyte.jojo.client.standskin.sprites;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;

import net.minecraft.Util;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class AbilityIconSprites implements AutoCloseable {
	public static final String DIR_NAME = "ability";
	private static final Map<String, List<String>> SPRITE_NAME_ALIASES = Map.ofEntries(
			Map.entry("light_attack", List.of("punch")),
			Map.entry("no_rapier_light_attack", List.of("punch")),
			Map.entry("melee_barrage", List.of("barrage")),
			Map.entry("grab", List.of("heavy_punch")),
			Map.entry("grab_punch", List.of("punch")),
			Map.entry("grab_barrage", List.of("barrage")),
			Map.entry("grab_heavy_punch", List.of("heavy_punch")),
			Map.entry("grab_uppercut", List.of("finisher_uppercut", "uppercut", "heavy_punch")),
			Map.entry("grab_finisher", List.of("finisher", "heavy_punch")),
			Map.entry("grab_throw", List.of("grab", "heavy_punch")),
			Map.entry("heavy_charged", List.of("heavy_punch")),
			Map.entry("bearing_shot", List.of("heavy_punch")),
			Map.entry("uppercut", List.of("finisher_uppercut")),
			Map.entry("finisher_uppercut", List.of("uppercut")),
			Map.entry("enhanced_eyesight", List.of("zoom")),
			Map.entry("time_stop_blink", List.of("time_stop", "ts_blink")),
			Map.entry("time_resume", List.of("time_stop")),
			Map.entry("repair_item", List.of("repair")),
			Map.entry("uncraft", List.of("repair_item", "repair")),
			Map.entry("revert_state", List.of("previous_state")),
			Map.entry("fuse_with_rock", List.of("angelo_rock")),
			Map.entry("block_anchor", List.of("anchor_move")),
			Map.entry("disfiguring_punch", List.of("mishealing_punch")),
			Map.entry("mishealing_punch", List.of("disfiguring_punch")),
			Map.entry("misshape_arms", List.of("misheal_arms")),
			Map.entry("misshape_face", List.of("misheal_face")),
			Map.entry("misshape_legs", List.of("misheal_legs")),
			Map.entry("misheal_arms", List.of("misshape_arms")),
			Map.entry("misheal_face", List.of("misshape_face")),
			Map.entry("misheal_legs", List.of("misshape_legs")),
			Map.entry("crossfire_hurricane_special", List.of("crossfire_hurricane", "ch_special")),
			Map.entry("mr_detector", List.of("detector")),
			Map.entry("emerald_splash_concentrated", List.of("emerald_splash", "es_concentrated")),
			Map.entry("string_attack", List.of("attack")),
			Map.entry("string_bind", List.of("attack_binding")),
			Map.entry("grapple_entity", List.of("grapple")),
			Map.entry("hamon_beat", List.of("hamon_overdrive_beat")),
			Map.entry("sunlight_yellow_overdrive", List.of("hamon_sunlight_yellow_overdrive")),
			Map.entry("rebuff_overdrive", List.of("hamon_rebuff_overdrive")),
			Map.entry("bubble_barrier", List.of("hamon_bubble_barrier")),
			Map.entry("bubble_cutter", List.of("hamon_bubble_cutter")),
			Map.entry("bubble_cutter_gliding", List.of("hamon_bubble_cutter_gliding")),
			Map.entry("bubble_launcher", List.of("hamon_bubble_launcher")),
			Map.entry("hamon_cutter", List.of("hamon_hamon_cutter")),
			Map.entry("hypnosis", List.of("hamon_hypnosis")),
			Map.entry("life_magnetism", List.of("hamon_life_magnetism")),
			Map.entry("metal_silver_overdrive", List.of("hamon_metal_silver_overdrive")),
			Map.entry("metal_silver_overdrive_weapon", List.of("hamon_metal_silver_overdrive_weapon")),
			Map.entry("organism_infusion", List.of("hamon_organism_infusion")),
			Map.entry("overdrive_barrage", List.of("hamon_overdrive_barrage")),
			Map.entry("plant_infusion", List.of("hamon_plant_infusion")),
			Map.entry("projectile_shield", List.of("hamon_projectile_shield")),
			Map.entry("scarlet_overdrive", List.of("hamon_scarlet_overdrive")),
			Map.entry("sendo_overdrive", List.of("hamon_sendo_overdrive")),
			Map.entry("sendo_wave_kick", List.of("hamon_sendo_wave_kick")),
			Map.entry("sunlight_yellow_overdrive_barrage", List.of("hamon_syo_barrage")),
			Map.entry("tornado_overdrive", List.of("hamon_tornado_overdrive")),
			Map.entry("turquoise_blue_overdrive", List.of("hamon_turquoise_blue_overdrive")),
			Map.entry("wall_climbing", List.of("hamon_wall_climbing")),
			Map.entry("zoom_punch", List.of("hamon_zoom_punch")));
	
	protected final TextureAtlas textureAtlas;
	protected final Set<MetadataSectionSerializer<?>> metadataSections;

	public AbilityIconSprites(TextureManager textureManager) {
		this(textureManager, JojoMod.resLoc("abilities"), SpriteLoader.DEFAULT_METADATA_SECTIONS);
	}

	public AbilityIconSprites(TextureManager textureManager, ResourceLocation atlasId) {
		this(textureManager, atlasId, SpriteLoader.DEFAULT_METADATA_SECTIONS);
	}

	public AbilityIconSprites(TextureManager textureManager, ResourceLocation atlasId, Set<MetadataSectionSerializer<?>> metadataSections) {
		this.textureAtlas = new TextureAtlas(atlasId);
		textureManager.register(this.textureAtlas.location(), this.textureAtlas);
		this.metadataSections = metadataSections;
	}


	public TextureAtlasSprite getAbilityIcon(Ability ability, Power<?> context, @Nullable StandSkin curStandSkin) {
		return getAbilityIcon(ability.getSpriteName(context), curStandSkin);
	}

	public TextureAtlasSprite getAbilityIcon(String abilitySpriteName, @Nullable StandSkin curStandSkin) {
		TextureAtlasSprite exactSprite = getAbilityIconExact(abilitySpriteName, curStandSkin);
		if (exactSprite != textureAtlas.missingSprite) {
			return exactSprite;
		}
		
		for (String alias : SPRITE_NAME_ALIASES.getOrDefault(abilitySpriteName, List.of())) {
			TextureAtlasSprite aliasSprite = getAbilityIconExact(alias, curStandSkin);
			if (aliasSprite != textureAtlas.missingSprite) {
				return aliasSprite;
			}
		}
		
		return textureAtlas.missingSprite;
	}

	private TextureAtlasSprite getAbilityIconExact(String abilitySpriteName, @Nullable StandSkin curStandSkin) {
		Map<ResourceLocation, TextureAtlasSprite> texturesByName = textureAtlas.texturesByName;
		ResourceLocation spritePath;

		if (curStandSkin != null) {
			spritePath = skinSpritePath(curStandSkin.skinId, abilitySpriteName);
			if (texturesByName.containsKey(spritePath)) return texturesByName.get(spritePath);
			
			if (!curStandSkin.isDefault) {
				spritePath = skinSpritePath(curStandSkin.standTypeId, abilitySpriteName);
				if (texturesByName.containsKey(spritePath)) return texturesByName.get(spritePath);
			}
		}

		spritePath = defaultSpritePath(abilitySpriteName);
		if (texturesByName.containsKey(spritePath)) return texturesByName.get(spritePath);
		
		return textureAtlas.missingSprite;
	}

	protected static ResourceLocation skinSpritePath(ResourceLocation skinId, String abilityName) {
		return memoize1.apply(skinId, abilityName);
	}
	protected static final BiFunction<ResourceLocation, String, ResourceLocation> memoize1 = Util.memoize(
			(ResourceLocation skinId, String abilityName) -> {
				return ResourceLocation.fromNamespaceAndPath(
						skinId.getNamespace(), 
						skinId.getPath() + "/" + abilityName);
			});

	protected static ResourceLocation defaultSpritePath(String abilityName) { 
		return memoize2.apply(abilityName);
	}
	protected static final Function<String, ResourceLocation> memoize2 = Util.memoize(
			(String abilityName) -> {
				return JojoMod.resLoc(DIR_NAME + "/" + abilityName);
			});


	@ApiStatus.Internal
	public static class Preps {
		protected AbilityIconSprites atlasHolder;
		protected SpriteLoader.Preparations prepsDone;
		protected Map<ResourceLocation, ResourceLocation> skinSprites = new HashMap<>();

		public Preps(AbilityIconSprites atlasHolder) {
			this.atlasHolder = atlasHolder;
		}

		public void addSkinSprite(ResourceLocation skinId, String spriteName, ResourceLocation filePath) {
			skinSprites.put(skinSpritePath(skinId, spriteName), filePath);
		}

		public CompletableFuture<SpriteLoader.Preparations> stitch(ResourceManager resourceManager, Executor executor) {
			SpriteLoader loader = SpriteLoader.create(atlasHolder.textureAtlas);
			SpriteResourceLoader resourceLoader = SpriteResourceLoader.create(atlasHolder.metadataSections);

			int mipLevel = 0;

			return CompletableFuture.supplyAsync(() -> {
				List<SpriteSource> sources = new ArrayList<>();
				// sprites from all the stand skins
				for (var spriteEntry : skinSprites.entrySet()) {
					ResourceLocation id = spriteEntry.getKey();
					ResourceLocation filePath = spriteEntry.getValue();
					sources.add(new SpriteSourceWithoutFileListerShit(id, filePath));
				}
				// and the default sprites that are not from the stand skins
				sources.add(new DirectoryLister(DIR_NAME, DIR_NAME + "/"));
				return new SpriteSourceList(sources).list(resourceManager);
			}).thenCompose((List<Function<SpriteResourceLoader, SpriteContents>> factories) -> {
				return SpriteLoader.runSpriteSuppliers(resourceLoader, factories, executor);
			}).thenApply((List<SpriteContents> contents) -> {
				return loader.stitch(contents, mipLevel, executor);
			}).thenCompose(preps -> {
				this.prepsDone = preps;
				return preps.waitForUpload();
			});
		}

		public void apply(ResourceManager resourceManager, ProfilerFiller profiler) {
			atlasHolder.apply(prepsDone, resourceManager, profiler);
		}

	}


	@ApiStatus.Internal
	public void apply(SpriteLoader.Preparations preparations, ResourceManager resourceManager, ProfilerFiller profiler) {
		profiler.startTick();
		profiler.push("upload");
		this.textureAtlas.upload(preparations);
		profiler.pop();
		profiler.endTick();
	}


	@Override
	public void close() {
		this.textureAtlas.clearTextureData();
	}
}
