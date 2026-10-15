package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityTypes;
import rotp.core.subsystems.soul.SoulEntity;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 SoulEntity.getDeltaMovement returned a constant (0, 0.04, 0): the soul, and the camera of a player who watches
 * it, rose 0.04 blocks a tick, 8 blocks over its 200 ticks, also in the port's resolve-up phase of its last 60 ticks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoulRiseGameTests {
	private static final int WAIT = 40;

	private SoulRiseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80, batch = "soul_rise")
	public static void soulRisesFourHundredthsOfABlockATick(GameTestHelper helper) {
		Cow origin = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(origin != null, "Fixture: could not create the cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		origin.setNoAi(true);
		origin.setNoGravity(true);
		origin.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(origin), "Fixture: could not add the cow");
		// dying, so that its soul stays; the soul holds its death animation short of removal
		origin.setHealth(0.0F);
		// past 20 ticks the soul is in its last 60, the port's resolve-up phase
		SoulEntity soul = new SoulEntity(helper.getLevel(), origin, 70);
		helper.assertTrue(helper.getLevel().addFreshEntity(soul), "Fixture: could not add the soul");
		double startY = soul.getY();
		helper.runAfterDelay(WAIT, () -> {
			try {
				helper.assertTrue(!soul.isRemoved() && soul.tickCount >= WAIT / 2,
						"Fixture: the soul is gone or did not tick: removed=" + soul.isRemoved() + " ticks=" + soul.tickCount);
				double rose = soul.getY() - startY;
				double expected = 0.04D * soul.tickCount;
				helper.assertTrue(Math.abs(rose - expected) < 1.0E-3D, "The soul rose " + rose + " blocks in " + soul.tickCount
						+ " ticks; 1.16 rose a constant 0.04 a tick, " + expected);
			}
			finally {
				soul.discard();
				origin.discard();
			}
			helper.succeed();
		});
	}

	// 1.16 SoulEntity counted tickCount against lifeSpan on both sides: the soul fire of its last 3 seconds and the soul
	// bar of the player who watches it ran on the client too. The port counts lifeSpan down on the server only; a client
	// has the life the soul had when it began to track it and the ticks it has seen since.
	@GameTest(template = "empty", timeoutTicks = 100, batch = "soul_rise")
	public static void soulLifeSpanOnAClientCountsDownLikeTheServers(GameTestHelper helper) {
		Cow origin = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(origin != null, "Fixture: could not create the cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		origin.setNoAi(true);
		origin.setNoGravity(true);
		origin.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(origin), "Fixture: could not add the cow");
		origin.setHealth(0.0F);
		SoulEntity soul = new SoulEntity(helper.getLevel(), origin, 200);
		helper.assertTrue(helper.getLevel().addFreshEntity(soul), "Fixture: could not add the soul");
		SoulEntity trackedFromTheStart = tracked(helper, soul);
		helper.runAfterDelay(WAIT + 5, () -> {
			try {
				helper.assertTrue(!soul.isRemoved() && soul.tickCount >= WAIT,
						"Fixture: the soul is gone or did not tick: removed=" + soul.isRemoved() + " ticks=" + soul.tickCount);
				int server = soul.lifeSpanLeft(false);
				helper.assertTrue(server < 200 - WAIT / 2, "Fixture: the server's life span did not count down: " + server);
				trackedFromTheStart.tickCount = soul.tickCount;
				helper.assertTrue(trackedFromTheStart.lifeSpanLeft(true) == server,
						"A client that saw the soul from its start has " + trackedFromTheStart.lifeSpanLeft(true) + " ticks of life"
								+ " left after " + soul.tickCount + " ticks; the server counts " + server);
				helper.assertTrue(trackedFromTheStart.getInitialLifeSpan() == 200,
						"A client that saw the soul from its start has an initial life of " + trackedFromTheStart.getInitialLifeSpan());

				// a client that begins to track the soul now has its remaining life for lifeSpan, the full life for the bar
				SoulEntity trackedLate = tracked(helper, soul);
				helper.assertTrue(trackedLate.getInitialLifeSpan() == 200 && trackedLate.lifeSpanLeft(true) == server,
						"A client that began to track the soul with " + server + " ticks of its 200 left has an initial life of "
								+ trackedLate.getInitialLifeSpan() + " and " + trackedLate.lifeSpanLeft(true) + " left");
				trackedLate.tickCount = 25;
				helper.assertTrue(trackedLate.lifeSpanLeft(true) == server - 25,
						"25 ticks later the late client has " + trackedLate.lifeSpanLeft(true) + " ticks of life left, expected "
								+ (server - 25));

				// the soul fire of the last 60 ticks
				trackedFromTheStart.tickCount = 139;
				boolean before = SoulEntity.isResolveUpPhase(trackedFromTheStart.lifeSpanLeft(true));
				trackedFromTheStart.tickCount = 140;
				boolean at = SoulEntity.isResolveUpPhase(trackedFromTheStart.lifeSpanLeft(true));
				trackedFromTheStart.tickCount = 199;
				boolean last = SoulEntity.isResolveUpPhase(trackedFromTheStart.lifeSpanLeft(true));
				trackedFromTheStart.tickCount = 200;
				boolean over = SoulEntity.isResolveUpPhase(trackedFromTheStart.lifeSpanLeft(true));
				helper.assertTrue(!before && at && last && !over,
						"The client's soul fire phase of a 200 tick soul: tick 139 " + before + ", 140 " + at + ", 199 " + last
								+ ", 200 " + over + "; expected false, true, true, false");
			}
			finally {
				soul.discard();
				origin.discard();
			}
			helper.succeed();
		});
	}

	// what a client reads from the spawn data of a soul
	private static SoulEntity tracked(GameTestHelper helper, SoulEntity soul) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		soul.writeSpawnData(buf);
		SoulEntity seen = new SoulEntity(ModEntityTypes.SOUL.get(), helper.getLevel());
		seen.readSpawnData(buf);
		return seen;
	}
}
