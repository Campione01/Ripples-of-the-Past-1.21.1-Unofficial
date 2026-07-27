package com.github.standobyte.jojo.mechanics.standdisc;

import java.util.Optional;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.TestStandInstances;
import com.github.standobyte.jojo.powersystem.standpower.datapack.DataDrivenStandsLoader;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class StandWrittenOnDiscSmokeTest {
	private static final ResourceLocation SKIN =
			id("jojo_ripples", "test_disc_skin");
	private static final ResourceLocation CHANGED_SKIN =
			id("jojo_ripples", "test_disc_changed_skin");

	private StandWrittenOnDiscSmokeTest() {}

	public static void run() {
		StandInstance source = TestStandInstances.valid(
				id("jojo_ripples", "test_disc_stand"));
		source.removePart(StandPart.ARMS);
		source.setCustomSkin(Optional.of(SKIN));
		StandWrittenOnDisc disc = new StandWrittenOnDisc(source);

		source.addPart(StandPart.ARMS);
		source.setCustomSkin(Optional.of(CHANGED_SKIN));
		assertStoredState(disc, "disc constructor must copy its input");

		StandInstance exposed = disc.getInstance();
		exposed.addPart(StandPart.ARMS);
		exposed.setCustomSkin(Optional.of(CHANGED_SKIN));
		assertStoredState(disc, "disc getter must return a defensive copy");

		StandInstance networkSource = disc.copyStandInstance();
		StandWrittenOnDisc networkDataDisc = new StandWrittenOnDisc(
				StandInstance.NetworkData.wrap(networkSource));
		networkSource.addPart(StandPart.ARMS);
		networkSource.setCustomSkin(Optional.of(CHANGED_SKIN));
		assertStoredState(networkDataDisc, "disc network-data constructor must copy its input");

		StandWrittenOnDisc equalDisc = new StandWrittenOnDisc(disc.copyStandInstance());
		check(disc.equals(equalDisc), "equivalent disc contents must compare equal");
		check(disc.hashCode() == equalDisc.hashCode(),
				"equivalent disc contents must have equal hashes");

		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			StandWrittenOnDisc.STREAM_CODEC.encode(buffer, disc);
			StandWrittenOnDisc streamRoundTrip = StandWrittenOnDisc.STREAM_CODEC.decode(buffer);
			check(disc.equals(streamRoundTrip), "disc stream codec must preserve Stand contents");
		}
		finally {
			buffer.release();
		}

		DataDrivenStandsLoader.getDatapackStandsLoader();
		Tag encoded = StandWrittenOnDisc.CODEC.encodeStart(NbtOps.INSTANCE, disc).getOrThrow();
		StandWrittenOnDisc codecRoundTrip =
				StandWrittenOnDisc.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		check(disc.equals(codecRoundTrip), "disc codec must preserve Stand contents");
	}

	private static void assertStoredState(StandWrittenOnDisc disc, String message) {
		StandInstance stored = disc.getInstance();
		check(!stored.hasPart(StandPart.ARMS), message + ": parts changed");
		check(stored.getSelectedSkin().equals(Optional.of(SKIN)),
				message + ": skin changed");
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
