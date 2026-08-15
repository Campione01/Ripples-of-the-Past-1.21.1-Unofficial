package com.github.standobyte.jojo.network;

import java.util.Objects;

import io.netty.handler.codec.DecoderException;
import net.minecraft.world.phys.Vec3;

public final class NetworkPayloadValidation {
	public static final int MAX_ABILITY_EXTRA_BYTES = 8 * 1024;
	public static final int MAX_ENTITY_ACTION_BYTES = 64 * 1024;
	public static final int MAX_ACTION_SYNC_VALUES = 255;

	private NetworkPayloadValidation() {}

	public static int requireByteLength(
			int length, int maxLength, String description) {
		if (length < 0 || length > maxLength) {
			throw new DecoderException("Invalid " + description + " byte length "
					+ length + " (max " + maxLength + ")");
		}
		return length;
	}

	public static int requireOutboundByteLength(
			int length, int maxLength, String description) {
		if (length < 0 || length > maxLength) {
			throw new IllegalArgumentException(
					"Invalid outbound " + description + " byte length "
							+ length + " (max " + maxLength + ")");
		}
		return length;
	}

	public static long requireGeneration(
			long generation, String description) {
		if (generation <= 0L) {
			throw new DecoderException(
					"Invalid " + description + " generation " + generation);
		}
		return generation;
	}

	public static long requireOutboundGeneration(
			long generation, String description) {
		if (generation <= 0L) {
			throw new IllegalArgumentException(
					"Invalid outbound " + description + " generation "
							+ generation);
		}
		return generation;
	}

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
