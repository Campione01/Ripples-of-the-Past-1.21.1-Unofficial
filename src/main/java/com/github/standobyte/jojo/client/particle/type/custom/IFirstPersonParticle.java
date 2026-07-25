package com.github.standobyte.jojo.client.particle.type.custom;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

public interface IFirstPersonParticle {
	void renderSprite(Matrix4f matrixEntry, VertexConsumer buffer, int light, float partialTick, Vector3f[] avector3f);
}
