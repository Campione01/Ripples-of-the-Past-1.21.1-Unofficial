package rotp.core.network.s2c;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class SkippedStandProgressionPacketSmokeTest {
	private SkippedStandProgressionPacketSmokeTest() {}

	public static void run() {
		for (boolean skipped : new boolean[] {false, true}) {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			try {
				SkippedStandProgressionPacket.Handler.STREAM_CODEC.encode(buf, new SkippedStandProgressionPacket(skipped));
				check(buf.readableBytes() == 1, "owner progression snapshot must carry exactly one boolean");
				check(SkippedStandProgressionPacket.Handler.STREAM_CODEC.decode(buf).skipped() == skipped,
						"both progression grants and clears must round-trip");
				check(buf.readableBytes() == 0, "progression snapshot must be fully consumed");
			}
			finally {
				buf.release();
			}
		}
		FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
		try {
			try {
				SkippedStandProgressionPacket.Handler.STREAM_CODEC.decode(truncated);
				throw new AssertionError("a missing progression flag must not decode as false");
			}
			catch (IndexOutOfBoundsException expected) {
			}
		}
		finally {
			truncated.release();
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
