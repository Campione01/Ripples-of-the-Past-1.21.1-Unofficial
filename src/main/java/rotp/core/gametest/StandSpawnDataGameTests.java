package rotp.core.gametest;

import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A client makes a Stand from the entity type the server sends and then reads its spawn data, so that data must bring
 * the server's dimensions: a humanoid Stand has its eyes at 1.62, not at the 0.85 of its height that
 * EntityDimensions.scalable gives, and a Stand placed by its feet looks and aims from its eyes.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSpawnDataGameTests {
	private static final List<String> CORE_ENTITY_STANDS = List.of("star_platinum", "the_world", "hierophant_green",
			"silver_chariot", "magicians_red", "crazy_diamond", "gold_experience");

	private StandSpawnDataGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void everyCoreStandReachesClientsWithTheServersDimensions(GameTestHelper helper) {
		for (String id : CORE_ENTITY_STANDS) {
			checkSpawnDimensions(helper, id);
		}
		helper.succeed();
	}

	private static void checkSpawnDimensions(GameTestHelper helper, String id) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
		StandPower power = null;
		Entity created = null;
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			helper.assertTrue(type instanceof EntityStandType, "Missing the core entity Stand " + id);
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user of " + id);
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + id);
			helper.assertTrue(type.summon(user, power), "Could not summon " + id);
			StandEntity server = power.getSummonedStandEntity();
			helper.assertTrue(server != null, "setup: no summoned " + id);
			helper.assertTrue(Math.abs(server.getEyeHeight() - server.getBbHeight() * 0.85F) > 1.0E-3F,
					"setup: the server's " + id + " has its eyes at 0.85 of its height " + server.getBbHeight()
							+ ", where width and height alone would do");

			created = server.getType().create(helper.getLevel());
			helper.assertTrue(created instanceof StandEntity, "setup: the entity type of " + id + " made " + created);
			StandEntity client = (StandEntity) created;
			server.writeSpawnData(buf);
			client.readSpawnData(buf);
			helper.assertTrue(buf.readableBytes() == 0,
					"The spawn data of " + id + " left " + buf.readableBytes() + " bytes unread");
			EntityDimensions sent = server.getDimensions(server.getPose());
			EntityDimensions got = client.getDimensions(client.getPose());
			helper.assertTrue(sent.width() == got.width() && sent.height() == got.height()
					&& sent.eyeHeight() == got.eyeHeight() && server.getEyeHeight() == client.getEyeHeight(),
					"A client makes " + id + " " + got.width() + " wide and " + got.height() + " high with its eyes at "
							+ client.getEyeHeight() + "; the server's is " + sent.width() + " wide and " + sent.height()
							+ " high with its eyes at " + server.getEyeHeight());
		}
		finally {
			buf.release();
			if (created != null) {
				created.discard();
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
