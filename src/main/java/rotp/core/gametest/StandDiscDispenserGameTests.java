package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandDiscItem registered a dispenser behaviour: the disc went into the first entity in front of the
 * dispenser that could take its Stand (MCUtil.dispenseOnNearbyEntity + giveStandFromDisc), otherwise it was
 * dropped like any item.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandDiscDispenserGameTests {
	private static final BlockPos DISPENSER = new BlockPos(1, 2, 2);
	private static final BlockPos TARGET = new BlockPos(2, 2, 2);

	private StandDiscDispenserGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void dispenserInsertsDiscIntoPlayerWithoutStand(GameTestHelper helper) {
		ServerPlayer user = addPlayer(helper, "StandDiscDispenserInsert");
		try {
			DispenserBlockEntity dispenser = placeDispenser(helper, disc(helper, "star_platinum"));
			fire(helper);
			StandPower power = PowerClass.STAND.get(user);
			helper.assertTrue(power != null && power.getStandInstance()
					.map(stand -> JojoMod.resLoc("star_platinum").equals(stand.getStandId())).orElse(false),
					"The dispensed disc did not give the player in front its Stand");
			helper.assertTrue(dispenser.getItem(0).isEmpty(),
					"The dispensed disc was not used up: " + dispenser.getItem(0));
			helper.assertTrue(discItems(helper).isEmpty(), "The disc was inserted and also dropped");
		}
		finally {
			cleanup(helper, user);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void dispenserDropsDiscWhenPlayerHasStand(GameTestHelper helper) {
		ServerPlayer user = addPlayer(helper, "StandDiscDispenserOccupied");
		try {
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType(helper, "star_platinum")))
					.applied(), "Could not grant Star Platinum");
			DispenserBlockEntity dispenser = placeDispenser(helper, disc(helper, "magicians_red"));
			fire(helper);
			ResourceLocation current = power.getStandInstance().map(StandInstance::getStandId).orElse(null);
			helper.assertTrue(JojoMod.resLoc("star_platinum").equals(current),
					"1.16 giveStandFromInstance never replaced a Stand, now: " + current);
			helper.assertTrue(dispenser.getItem(0).isEmpty(), "The refused disc stayed in the dispenser");
			helper.assertTrue(discItems(helper).size() == 1, "The refused disc was not dropped in front of the dispenser");
		}
		finally {
			cleanup(helper, user);
		}
		helper.succeed();
	}

	private static StandType standType(GameTestHelper helper, String id) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
		helper.assertTrue(type != null, "Missing registered Stand " + id);
		return type;
	}

	private static net.minecraft.world.item.ItemStack disc(GameTestHelper helper, String id) {
		return StandDiscItem.withStand(new StandInstance(standType(helper, id)));
	}

	private static ServerPlayer addPlayer(GameTestHelper helper, String name) {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(TARGET));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the player");
		return user;
	}

	private static DispenserBlockEntity placeDispenser(GameTestHelper helper, net.minecraft.world.item.ItemStack disc) {
		helper.setBlock(DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		helper.setBlock(TARGET, Blocks.AIR.defaultBlockState());
		DispenserBlockEntity dispenser = helper.getBlockEntity(DISPENSER);
		helper.assertTrue(dispenser != null, "No dispenser block entity");
		dispenser.setItem(0, disc);
		return dispenser;
	}

	// the scheduled tick a redstone pulse leads to
	private static void fire(GameTestHelper helper) {
		BlockState state = helper.getBlockState(DISPENSER);
		state.tick(helper.getLevel(), helper.absolutePos(DISPENSER), helper.getLevel().getRandom());
	}

	private static List<ItemEntity> discItems(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(TARGET)).inflate(2),
				item -> item.getItem().is(ModItems.STAND_DISC.get()));
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer user) {
		discItems(helper).forEach(ItemEntity::discard);
		// the fake player is cached, so a rerun must start without a Stand
		StandPower power = PowerClass.STAND.get(user);
		if (power != null) {
			power.getStandInstance().ifPresent(stand -> StandPowerTransitions.extract(power, stand.getStandId()));
		}
		user.discard();
	}
}
