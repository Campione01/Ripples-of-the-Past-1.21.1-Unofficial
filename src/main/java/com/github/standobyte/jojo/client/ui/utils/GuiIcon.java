package com.github.standobyte.jojo.client.ui.utils;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public class GuiIcon {
	public final ResourceLocation file;
	public final float texWidth;
	public final float texHeight;
	
	public final float width;
	public final float height;
	public final float minU;
	public final float widthU;
	public final float minV;
	public final float heightV;

	public GuiIcon(ResourceLocation file, 
			float texWidth, float texHeight) {
		this(file, 0, 0, texWidth, texHeight, texWidth, texHeight);
	}

	public GuiIcon(ResourceLocation file, 
			float offsetU, float offsetV, 
			float widthU, float heightV, 
			float texWidth, float texHeight) {
		this.file = file;
		this.texWidth = texWidth;
		this.texHeight = texHeight;
		this.width = widthU;
		this.height = heightV;
		this.minU = offsetU / texWidth;
		this.widthU = widthU / texWidth;
		this.minV = offsetV / texHeight;
		this.heightV = heightV / texHeight;
	}

	public void render(PoseStack poseStack, float x, float y) {
		render(poseStack, x, y, BlitFloat.NO_TINT);
	}

	public void render(PoseStack poseStack, float x, float y, int color) {
		render(poseStack, x, y, this.width, this.height, color);
	}

	public void render(PoseStack poseStack, float x, float y, float width, float height, int color) {
		BlitFloat.blit(poseStack, Minecraft.getInstance(), file, 
				x, y, width, height, 0, 
				minU, minV, widthU, heightV, 1, 1, 
				color);
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof GuiIcon other && 
				this.file.equals(other.file) && 
				this.width == other.width && this.height == other.height && 
				this.minU == other.minU && this.widthU == other.widthU && 
				this.minV == other.minV && this.heightV == other.heightV;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(file, width, height, minU, widthU, minV, heightV);
	}
}
