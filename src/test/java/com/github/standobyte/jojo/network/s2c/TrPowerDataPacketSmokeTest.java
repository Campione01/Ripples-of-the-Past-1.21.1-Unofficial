package com.github.standobyte.jojo.network.s2c;

import java.lang.reflect.Field;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TrPowerDataPacketSmokeTest {
	private TrPowerDataPacketSmokeTest() {}

	public static void run() {
		testServerPayloadIsSnapshottedBeforeNettyEncode();

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "retained_power");
		ResourceLocation otherId = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "other");

		check(TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, id, true),
				"matching retained Stand data must be accepted");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, id, false),
				"power-class mismatch must be rejected");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, otherId, true),
				"power-type ID mismatch must be rejected");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, null, true),
				"unknown power-type ID must be rejected");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				null, id, true),
				"missing packet power-type ID must be rejected");
	}

	private static void testServerPayloadIsSnapshottedBeforeNettyEncode() {
		TestPowerType type = new TestPowerType();
		MutablePowerData data = new MutablePowerData(type, 17);
		byte[] snapshot = TrPowerDataPacket.snapshotPowerTypeData(
				data, false);

		data.value = 99;
		FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
		try {
			TrPowerDataPacket.Handler.writePowerTypeDataSnapshot(
					snapshot, encoded);
			check(encoded.readInt() == 17,
					"queued power-data packet must retain its construction-time state");
			check(data.encodeCalls == 1,
					"Netty encoding must not revisit mutable power data");
		}
		finally {
			encoded.release();
		}

		for (Field field : TrPowerDataPacket.class.getDeclaredFields()) {
			check(!PowerData.class.isAssignableFrom(field.getType()),
					"packet must not retain live mutable PowerData fields");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class MutablePowerData extends PowerData {
		private int value;
		private int encodeCalls;

		private MutablePowerData(PowerType powerType, int value) {
			super(powerType);
			this.value = value;
		}

		@Override
		public PowerClass<?> getPowerClass() {
			return null;
		}

		@Override
		public void toBuf(
				FriendlyByteBuf buf, boolean isSentToTracking) {
			encodeCalls++;
			buf.writeInt(value);
		}
	}

	private static final class TestPowerType extends PowerType {
		private static final ResourceLocation ID =
				ResourceLocation.fromNamespaceAndPath(
						"rotp_test", "snapshot_power");

		private TestPowerType() {
			super(new MovesetBuilder());
		}

		@Override
		public PowerData newDataInstance() {
			return new MutablePowerData(this, 0);
		}

		@Override
		public ResourceLocation getId() {
			return ID;
		}

		@Override
		public PowerClass<?> getPowerClass() {
			return null;
		}

		@Override
		public Component getName(Power<?> power) {
			return Component.literal("Snapshot test power");
		}
	}

}
