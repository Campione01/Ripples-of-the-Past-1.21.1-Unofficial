package com.github.standobyte.jojo.client.rendertype;

import java.nio.ByteBuffer;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class AlphaVertexConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final float alphaMultiplier;

	public AlphaVertexConsumer(VertexConsumer delegate, float alphaMultiplier) {
		this.delegate = delegate;
		this.alphaMultiplier = Mth.clamp(alphaMultiplier, 0.0F, 1.0F);
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		delegate.addVertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setColor(int red, int green, int blue, int alpha) {
		delegate.setColor(red, green, blue, scaleAlpha(alpha));
		return this;
	}

	@Override
	public VertexConsumer setColor(float red, float green, float blue, float alpha) {
		delegate.setColor(red, green, blue, Mth.clamp(alpha * alphaMultiplier, 0.0F, 1.0F));
		return this;
	}

	@Override
	public VertexConsumer setColor(int color) {
		int scaledColor = FastColor.ARGB32.color(scaleAlpha(FastColor.ARGB32.alpha(color)), color);
		delegate.setColor(scaledColor);
		return this;
	}

	@Override
	public VertexConsumer setWhiteAlpha(int alpha) {
		delegate.setWhiteAlpha(scaleAlpha(alpha));
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		delegate.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		delegate.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		delegate.setUv2(u, v);
		return this;
	}

	@Override
	public VertexConsumer setLight(int packedLight) {
		delegate.setLight(packedLight);
		return this;
	}

	@Override
	public VertexConsumer setOverlay(int packedOverlay) {
		delegate.setOverlay(packedOverlay);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
		delegate.setNormal(normalX, normalY, normalZ);
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v,
			int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
		int scaledColor = FastColor.ARGB32.color(scaleAlpha(FastColor.ARGB32.alpha(color)), color);
		delegate.addVertex(x, y, z, scaledColor, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
	}

	@Override
	public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green,
			float blue, float alpha, int packedLight, int packedOverlay) {
		delegate.putBulkData(pose, quad, red, green, blue, alpha * alphaMultiplier, packedLight, packedOverlay);
	}

	@Override
	public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
			float red, float green, float blue, float alpha, int[] lightmap,
			int packedOverlay, boolean readAlpha) {
		delegate.putBulkData(pose, quad, brightness, red, green, blue, alpha * alphaMultiplier,
				lightmap, packedOverlay, readAlpha);
	}

	@Override
	public void putBulkData(PoseStack.Pose pose, BakedQuad bakedQuad, float red, float green,
			float blue, float alpha, int packedLight, int packedOverlay, boolean readExistingColor) {
		delegate.putBulkData(pose, bakedQuad, red, green, blue, alpha * alphaMultiplier,
				packedLight, packedOverlay, readExistingColor);
	}

	@Override
	public VertexConsumer misc(VertexFormatElement element, int... rawData) {
		delegate.misc(element, rawData);
		return this;
	}

	@Override
	public int applyBakedLighting(int packedLight, ByteBuffer data) {
		return delegate.applyBakedLighting(packedLight, data);
	}

	@Override
	public void applyBakedNormals(Vector3f generated, ByteBuffer data, Matrix3f normalTransform) {
		delegate.applyBakedNormals(generated, data, normalTransform);
	}

	private int scaleAlpha(int alpha) {
		return Mth.clamp(Math.round(alpha * alphaMultiplier), 0, 255);
	}
}
