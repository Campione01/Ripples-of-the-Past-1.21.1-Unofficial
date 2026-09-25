package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.item.StandRemoverItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ModItems: reusable stand_remover, stand_eject and stand_full_clear (stack of 1,
 * never used up) next to their one-time versions, all in MAIN_TAB.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandRemoverReusableGameTests {
	private static final List<String> IDS = List.of("stand_remover", "stand_eject", "stand_full_clear");

	private StandRemoverReusableGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void reusableRemoversAreRegisteredWithAssetsInTheMainTab(GameTestHelper helper) {
		List<Item> reusable = List.of(ModItems.STAND_REMOVER.get(),
				ModItems.STAND_EJECT.get(), ModItems.STAND_FULL_CLEAR.get());
		List<Item> oneTime = List.of(ModItems.STAND_REMOVER_ONE_TIME.get(),
				ModItems.STAND_EJECT_ONE_TIME.get(), ModItems.STAND_FULL_CLEAR_ONE_TIME.get());
		CreativeModeTab tab = ModItems.MAIN_TAB.get();
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
				helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
		List<Item> tabItems = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
		for (int i = 0; i < IDS.size(); i++) {
			String id = IDS.get(i);
			Item item = BuiltInRegistries.ITEM.get(JojoMod.resLoc(id));
			helper.assertTrue(item == reusable.get(i) && item instanceof StandRemoverItem,
					"jojo_ripples:" + id + " is not a registered Stand remover: " + item);
			helper.assertTrue(new ItemStack(item).getMaxStackSize() == 1,
					id + " stacks to " + new ItemStack(item).getMaxStackSize() + ", 1.16 stacked to 1");
			helper.assertTrue(new ItemStack(oneTime.get(i)).getMaxStackSize() == 64,
					id + "_one_time no longer stacks to 64");
			int at = tabItems.indexOf(item);
			int oneTimeAt = tabItems.indexOf(oneTime.get(i));
			helper.assertTrue(at >= 0 && oneTimeAt > at,
					id + " must be in the main tab before its one-time version: " + at + " / " + oneTimeAt);
			String model = readAsset("models/item/" + id + ".json");
			helper.assertTrue(model != null && model.contains("\"jojo_ripples:item/" + id + "\""),
					id + " item model does not use its own texture: " + model);
			helper.assertTrue(readAsset("textures/item/" + id + ".png") != null, id + " texture is missing");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void reusableRemoverClearsTheStandAndIsNotUsedUp(GameTestHelper helper) {
		User u = new User(helper, "ReusableStandRemover");
		try {
			helper.assertTrue(u.use(ModItems.STAND_REMOVER.get()), "Remove Stand did not remove the Stand");
			helper.assertTrue(!u.power.hasPower(), "Remove Stand left the Stand");
			u.assertStillHeld(ModItems.STAND_REMOVER.get(), "Remove Stand");
			// the one-time version is still used up by the same player
			u.grant();
			helper.assertTrue(u.use(ModItems.STAND_REMOVER_ONE_TIME.get()),
					"Remove Stand (one-time) did not remove the Stand");
			helper.assertTrue(u.player.getMainHandItem().isEmpty(), "Remove Stand (one-time) was not used up");
		}
		finally {
			u.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void reusableEjectGivesTheDiscAndIsNotUsedUp(GameTestHelper helper) {
		User u = new User(helper, "ReusableStandEject");
		try {
			helper.assertTrue(u.use(ModItems.STAND_EJECT.get()), "Eject Stand did not eject the Stand");
			helper.assertTrue(!u.power.hasPower(), "Eject Stand left the Stand");
			u.assertStillHeld(ModItems.STAND_EJECT.get(), "Eject Stand");
			Inventory inventory = u.player.getInventory();
			boolean disc = false;
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				disc |= inventory.getItem(slot).is(ModItems.STAND_DISC.get());
			}
			helper.assertTrue(disc, "Eject Stand gave no Stand Disc");
		}
		finally {
			u.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void reusableFullClearResetsProgressionAndIsNotUsedUp(GameTestHelper helper) {
		User u = new User(helper, "ReusableStandFullClear");
		try {
			u.power.setResolveLevel(User.LEVEL);
			helper.assertTrue(u.power.getResolveLevel() == User.LEVEL, "Could not set the Resolve level");
			helper.assertTrue(u.use(ModItems.STAND_FULL_CLEAR.get()), "Full Stand Clear did not clear the Stand");
			helper.assertTrue(!u.power.hasPower(), "Full Stand Clear left the Stand");
			u.assertStillHeld(ModItems.STAND_FULL_CLEAR.get(), "Full Stand Clear");
			// unlike Remove Stand, the Stand's saved progression is gone too
			u.grant();
			helper.assertTrue(u.power.getResolveLevel() == u.levelOfANewStand(),
					"Full Stand Clear kept the Resolve level: " + u.power.getResolveLevel());
		}
		finally {
			u.close();
		}
		helper.succeed();
	}

	private static String readAsset(String path) {
		String full = "assets/" + JojoMod.MOD_ID + "/" + path;
		InputStream in = StandRemoverReusableGameTests.class.getResourceAsStream("/" + full);
		if (in == null) {
			in = StandRemoverReusableGameTests.class.getClassLoader().getResourceAsStream(full);
		}
		if (in == null) {
			return null;
		}
		try (InputStream stream = in) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			return null;
		}
	}

	private static final class User {
		static final int LEVEL = 2;
		final GameTestHelper helper;
		final ServerPlayer player;
		final StandPower power;

		User(GameTestHelper helper, String name) {
			this.helper = helper;
			player = FakePlayerFactory.get(helper.getLevel(),
					new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			player.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the test player");
			// survival use: one-time items shrink, reusable ones must not
			player.getAbilities().instabuild = false;
			power = PowerClass.STAND.attachGet(player);
			onDiscBefore = holders().getOnDisc(JojoMod.resLoc("star_platinum"));
			grant();
		}

		// Stand Eject keeps the ejected Stand counted (1.16 putOutStand); close() takes that count back
		final int onDiscBefore;

		rotp.core.subsystems.ServerDuplicateCounter.StandHolders holders() {
			return rotp.core.subsystems.ServerDuplicateCounter.StandHolders.get(helper.getLevel().getServer());
		}

		void grant() {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
		}

		boolean use(Item item) {
			player.getInventory().clearContent();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			return item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction();
		}

		void assertStillHeld(Item item, String what) {
			ItemStack held = player.getMainHandItem();
			helper.assertTrue(held.is(item) && held.getCount() == 1,
					what + " was used up (held " + held + "); the reusable 1.16 item stays");
		}

		// skipStandProgression gives a new Stand its highest level
		int levelOfANewStand() {
			return JojoModConfig.getCommonConfigInstance(false).skipStandProgression.get() ? power.getMaxResolveLevel() : 0;
		}

		void close() {
			if (power.isSummoned() && power.getPowerType() != null) {
				power.getPowerType().forceUnsummon(player, power);
			}
			player.getInventory().clearContent();
			player.discard();
			// the ejected disc is thrown away with the inventory
			var starPlatinum = JojoMod.resLoc("star_platinum");
			while (holders().getOnDisc(starPlatinum) > onDiscBefore && holders().takeFromDisc(starPlatinum)) {
			}
		}
	}
}
