package com.github.standobyte.jojoimpl.powers.hamon.client.particle.custom;

import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/*
 * Particle batch setup derived from PneumaticCraft: Repressurized's
 * AirParticle by Team Pneumatic, including desht:
 * https://github.com/TeamPneumatic/pnc-repressurized/blob/9f722043d49d248222e0117a7eea6afef75af069/src/main/java/me/desht/pneumaticcraft/client/particle/AirParticle.java#L107
 * Upstream license: GPL-3.0-or-later.
 * Modification notice (2026-07-26): adapted the additive particle rendering
 * state for Hamon aura batches and optional texture blurring.
 */
public class HamonAuraParticleRenderType implements ParticleRenderType {
	public static final HamonAuraParticleRenderType HAMON_AURA = new HamonAuraParticleRenderType();
	private static boolean auraBatchOpen;
	private static boolean auraBlurApplied;
	private static TextureManager auraTextureManager;

	protected HamonAuraParticleRenderType() {}

	@Override
	public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
		endAuraBatchIfOpen();
		auraBatchOpen = true;
		auraTextureManager = textureManager;
		RenderSystem.depthMask(false);
		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		auraBlurApplied = ClientModSettings.getSettingsReadOnly().hamonAuraBlur;
		if (auraBlurApplied) {
			textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES).setBlurMipmap(true, false);
		}
		return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
	}

	public static void endAuraBatchIfOpen() {
		if (!auraBatchOpen) {
			return;
		}
		if (auraBlurApplied && auraTextureManager != null) {
			auraTextureManager.getTexture(TextureAtlas.LOCATION_PARTICLES).restoreLastBlurMipmap();
		}
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		auraBatchOpen = false;
		auraBlurApplied = false;
		auraTextureManager = null;
	}

	@Override
	public String toString() {
		return "jojo_ripples:hamon_aura";
	}

}
