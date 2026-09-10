package com.github.standobyte.jojo.client.rendertype;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.renderer.block.model.BakedQuad;

public final class BarrageVertexConsumer implements VertexConsumer {
	private final VertexConsumer body;
	private final Supplier<VertexConsumer> barrageSupplier;
	private VertexConsumer barrage;

	public BarrageVertexConsumer(VertexConsumer body, Supplier<VertexConsumer> barrageSupplier) {
		this.body = Objects.requireNonNull(body, "body");
		this.barrageSupplier = Objects.requireNonNull(barrageSupplier, "barrageSupplier");
	}

	public static VertexConsumer forBarrage(VertexConsumer consumer) {
		if (consumer instanceof BarrageVertexConsumer tagged) {
			if (tagged.barrage == null) {
				tagged.barrage = Objects.requireNonNull(tagged.barrageSupplier.get(), "barrage");
			}
			return tagged.barrage;
		}
		return consumer;
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		body.addVertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer addVertex(Vector3f position) {
		body.addVertex(position);
		return this;
	}

	@Override
	public VertexConsumer addVertex(PoseStack.Pose pose, Vector3f position) {
		body.addVertex(pose, position);
		return this;
	}

	@Override
	public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
		body.addVertex(pose, x, y, z);
		return this;
	}

	@Override
	public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
		body.addVertex(pose, x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setColor(int red, int green, int blue, int alpha) {
		body.setColor(red, green, blue, alpha);
		return this;
	}

	@Override
	public VertexConsumer setColor(float red, float green, float blue, float alpha) {
		body.setColor(red, green, blue, alpha);
		return this;
	}

	@Override
	public VertexConsumer setColor(int color) {
		body.setColor(color);
		return this;
	}

	@Override
	public VertexConsumer setWhiteAlpha(int alpha) {
		body.setWhiteAlpha(alpha);
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		body.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		body.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		body.setUv2(u, v);
		return this;
	}

	@Override
	public VertexConsumer setLight(int packedLight) {
		body.setLight(packedLight);
		return this;
	}

	@Override
	public VertexConsumer setOverlay(int packedOverlay) {
		body.setOverlay(packedOverlay);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
		body.setNormal(normalX, normalY, normalZ);
		return this;
	}

	@Override
	public VertexConsumer setNormal(PoseStack.Pose pose, float normalX, float normalY, float normalZ) {
		body.setNormal(pose, normalX, normalY, normalZ);
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v,
			int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
		body.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
	}

	@Override
	public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green,
			float blue, float alpha, int packedLight, int packedOverlay) {
		body.putBulkData(pose, quad, red, green, blue, alpha, packedLight, packedOverlay);
	}

	@Override
	public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
			float red, float green, float blue, float alpha, int[] lightmap,
			int packedOverlay, boolean readAlpha) {
		body.putBulkData(pose, quad, brightness, red, green, blue, alpha, lightmap,
				packedOverlay, readAlpha);
	}

	@Override
	public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green,
			float blue, float alpha, int packedLight, int packedOverlay, boolean readExistingColor) {
		body.putBulkData(pose, quad, red, green, blue, alpha, packedLight, packedOverlay, readExistingColor);
	}

	@Override
	public VertexConsumer misc(VertexFormatElement element, int... rawData) {
		body.misc(element, rawData);
		return this;
	}

	@Override
	public int applyBakedLighting(int packedLight, ByteBuffer data) {
		return body.applyBakedLighting(packedLight, data);
	}

	@Override
	public void applyBakedNormals(Vector3f generated, ByteBuffer data, Matrix3f normalTransform) {
		body.applyBakedNormals(generated, data, normalTransform);
	}
}
