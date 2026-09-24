package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.item.StoneMaskItem;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.impl.powers.vampirism.VampirismData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 BleedingEffect.applyStoneMask gave Vampirism through NonStandPower.givePower, which PowerBaseImpl.canGetPower
 * allowed only for a user with no power or whose type isReplaceableWith(VAMPIRISM): Hamon yes (HamonPowerType:264),
 * Zombie, Vampirism and Pillar Man no. A Zombie wearing the mask kept its power and the mask stayed unused.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoneMaskReplaceGameTests {
	private StoneMaskReplaceGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void zombieKeepsPowerUnderStoneMask(GameTestHelper helper) {
		Player player = maskedPlayer(helper);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(ModPlayerPowers.ZOMBIE.get());
			ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
			helper.assertTrue(!BleedingEffect.applyStoneMask(player, mask),
					"a Zombie's Stone Mask activated: 1.16 ZombiePowerType.isReplaceableWith is false");
			helper.assertTrue(power.getPowerType() == ModPlayerPowers.ZOMBIE.get(),
					"the Stone Mask replaced a Zombie power with " + power.getPowerType());
			helper.assertTrue(mask.getDamageValue() == 0 && StoneMaskItem.getActivatedTicks(mask) == 0,
					"the refused Stone Mask still wore or activated: damage=" + mask.getDamageValue());
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hamonUserBecomesVampireUnderStoneMask(GameTestHelper helper) {
		Player player = maskedPlayer(helper);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
			helper.assertTrue(BleedingEffect.applyStoneMask(player, mask),
					"a Hamon user's Stone Mask did not activate: 1.16 Hamon is replaceable with Vampirism");
			helper.assertTrue(power.getPowerType() == ModPlayerPowers.VAMPIRISM.get(),
					"the Stone Mask did not make the Hamon user a vampire: " + power.getPowerType());
			VampirismData vampirism = power.getCurTypeData(ModPlayerPowers.VAMPIRISM).orElseThrow();
			helper.assertTrue(vampirism.isVampireAtFullPower() && vampirism.isVampireHamonUser(),
					"the masked Hamon user is not a full-power vampire that was a Hamon user");
			helper.assertTrue(StoneMaskItem.getActivatedTicks(mask) > 0, "the Stone Mask did not activate");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void coreReplaceabilityRules(GameTestHelper helper) {
		PlayerPowerType<?> hamon = ModPlayerPowers.HAMON.get();
		PlayerPowerType<?> vampirism = ModPlayerPowers.VAMPIRISM.get();
		PlayerPowerType<?> zombie = ModPlayerPowers.ZOMBIE.get();
		PlayerPowerType<?> pillarman = ModPlayerPowers.PILLAR_MAN.get();
		helper.assertTrue(hamon.isReplaceableWith(vampirism), "1.16 HamonPowerType.isReplaceableWith(VAMPIRISM) is true");
		for (PlayerPowerType<?> other : new PlayerPowerType<?>[] { hamon, zombie, pillarman }) {
			helper.assertTrue(!hamon.isReplaceableWith(other), "Hamon must not be replaceable with " + other.getId());
		}
		for (PlayerPowerType<?> type : new PlayerPowerType<?>[] { vampirism, zombie, pillarman }) {
			for (PlayerPowerType<?> other : new PlayerPowerType<?>[] { hamon, vampirism, zombie, pillarman }) {
				helper.assertTrue(!type.isReplaceableWith(other),
						"1.16 " + type.getId() + ".isReplaceableWith is false, yet it accepts " + other.getId());
			}
		}

		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			placeInTest(helper, player);
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(zombie);
			helper.assertTrue(!power.trySetPowerType(vampirism) && power.getPowerType() == zombie,
					"trySetPowerType replaced a Zombie power with Vampirism");
			power.setPowerType(hamon);
			helper.assertTrue(!power.trySetPowerType(zombie) && power.getPowerType() == hamon,
					"trySetPowerType replaced Hamon with a Zombie power");
			helper.assertTrue(power.trySetPowerType(vampirism) && power.getPowerType() == vampirism,
					"trySetPowerType refused Vampirism for a Hamon user");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static Player maskedPlayer(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		placeInTest(helper, player);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.STONE_MASK.get()));
		return player;
	}

	private static void placeInTest(GameTestHelper helper, Player player) {
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Stone Mask test player");
	}
}
