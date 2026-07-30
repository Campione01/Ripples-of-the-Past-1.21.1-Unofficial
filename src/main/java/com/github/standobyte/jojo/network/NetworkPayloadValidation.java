package com.github.standobyte.jojo.network;

import java.util.Objects;

import io.netty.handler.codec.DecoderException;
import net.minecraft.world.phys.Vec3;

public final class NetworkPayloadValidation {
	private NetworkPayloadValidation() {}

	public static int requireCollectionSize(int size, int maxSize, String description) {
		if (size < 0 || size > maxSize) {
			throw new DecoderException("Invalid " + description + " count " + size
					+ " (max " + maxSize + ")");
		}
		return size;
	}

	public static int requireOutboundCollectionSize(
			int size, int maxSize, String description) {
		if (size < 0 || size > maxSize) {
			throw new IllegalArgumentException("Invalid outbound " + description
					+ " count " + size + " (max " + maxSize + ")");
		}
		return size;
	}

	public static String requireUtfLength(
			String value, int maxLength, String description) {
		Objects.requireNonNull(value, description);
		if (value.length() > maxLength) {
			throw new IllegalArgumentException("Outbound " + description
					+ " exceeds " + maxLength + " characters");
		}
		return value;
	}

	public static int requireCollectionSize(
			int size, int maxSize, int readableBytes, String description) {
		requireCollectionSize(size, maxSize, description);
		if (size > readableBytes) {
			throw new DecoderException("Truncated " + description + ": expected "
					+ size + " entries, only " + readableBytes + " bytes remain");
		}
		return size;
	}

	public static Vec3 requireFinite(Vec3 position, String description) {
		if (!Double.isFinite(position.x) || !Double.isFinite(position.y)
				|| !Double.isFinite(position.z)) {
			throw new DecoderException("Non-finite " + description);
		}
		return position;
	}
}
