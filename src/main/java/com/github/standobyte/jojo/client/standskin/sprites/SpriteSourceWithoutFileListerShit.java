package com.github.standobyte.jojo.client.standskin.sprites;

import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class SpriteSourceWithoutFileListerShit implements SpriteSource {
	protected static final Logger LOGGER = LogUtils.getLogger();
	public final ResourceLocation resourceId;
	public final ResourceLocation filePath;

	public SpriteSourceWithoutFileListerShit(ResourceLocation resourceId, ResourceLocation filePath) {
		this.resourceId = resourceId;
		this.filePath = filePath;
	}

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		Optional<Resource> resource = resourceManager.getResource(filePath);
		if (resource.isPresent()) {
			output.add(resourceId, resource.get());
		} else {
			LOGGER.warn("Missing sprite: {}", filePath);
		}
	}

	@Override
	public SpriteSourceType type() {
		return SpriteSources.SINGLE_FILE;
	}
}
