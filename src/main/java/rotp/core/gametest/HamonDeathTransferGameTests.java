package rotp.core.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonTechnique;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.CrimsonBubbleEntity;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonDeathTransferGameTests {
	private HamonDeathTransferGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void deepPassRejectsInvalidRecipientsAndDeathReplay(GameTestHelper helper) {
		Pig donor = createDonor(helper);
		ServerPlayer deadRecipient = createRecipient(helper, "HamonDead", 0.5D);
		ServerPlayer spectator = createRecipient(helper, "HamonSpectator", 1.0D);
		ServerPlayer recipient = createRecipient(helper, "HamonRecipient", 2.0D);
		ServerPlayer farther = createRecipient(helper, "HamonFarther", 5.0D);
		Pig respawnedDonor = null;
		try {
			HamonData source = grantHamon(helper, donor, 1000, 2000, ModHamonSkills.CHARACTER_ZEPPELI.get());
			HamonData deadData = grantHamon(helper, deadRecipient, 10, 20, null);
			HamonData spectatorData = grantHamon(helper, spectator, 10, 20, null);
			HamonData received = grantHamon(helper, recipient, 100, 200, null);
			HamonData fartherData = grantHamon(helper, farther, 10, 20, null);
			deadRecipient.setHealth(0.0F);
			spectator.setGameMode(GameType.SPECTATOR);
			kill(helper, donor);
			assertPoints(helper, received, 1100, 2200, "Nearest living receiver");
			assertPoints(helper, deadData, 10, 20, "Dead receiver");
			assertPoints(helper, spectatorData, 10, 20, "Spectator receiver");
			assertPoints(helper, fartherData, 10, 20, "Farther receiver");

			CompoundTag saved = source.serializeNBT(helper.getLevel().registryAccess());
			helper.assertTrue(saved.getBoolean("DeathPerksTriggered"), "Death claim was not persisted");
			source.deserializeNBT(helper.getLevel().registryAccess(), saved);
			HamonUtil.hamonPerksOnDeath(donor);
			assertPoints(helper, received, 1100, 2200, "Reloaded death replay");
			CompoundTag recipientSaved = received.serializeNBT(helper.getLevel().registryAccess());
			received.deserializeNBT(helper.getLevel().registryAccess(), recipientSaved);
			assertPoints(helper, received, 1100, 2200, "Recipient save round trip");

			donor.setHealth(donor.getMaxHealth());
			source.tick(PlayerPower.get(donor));
			helper.assertTrue(!source.serializeNBT(helper.getLevel().registryAccess()).getBoolean("DeathPerksTriggered"),
					"A real living Hamon tick did not rearm the death perk");
			donor.setHealth(0.0F);
			respawnedDonor = createDonor(helper);
			PlayerPower newPower = PowerClass.PLAYER_POWER.attachGet(respawnedDonor);
			PlayerPower.get(donor).onPlayerCloneData(newPower, true);
			helper.assertTrue(!newPower.hasPower(), "Death clone unexpectedly retained Hamon");
			grantHamon(helper, respawnedDonor, 1000, 2000, ModHamonSkills.CHARACTER_ZEPPELI.get());
			kill(helper, respawnedDonor);
			assertPoints(helper, received, 2100, 4200, "New life death transfer");
			helper.succeed();
		}
		finally {
			donor.discard();
			deadRecipient.discard();
			spectator.discard();
			recipient.discard();
			farther.discard();
			if (respawnedDonor != null) {
				respawnedDonor.discard();
			}
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void canceledDeathDoesNotSpendOrTransferHamon(GameTestHelper helper) {
		Pig donor = createDonor(helper);
		ServerPlayer recipient = createRecipient(helper, "HamonCanceled", 2.0D);
		Consumer<LivingDeathEvent> cancelDeath = event -> {
			if (event.getEntity() == donor) {
				event.setCanceled(true);
				donor.setHealth(donor.getMaxHealth());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, LivingDeathEvent.class, cancelDeath);
		try {
			HamonData source = grantHamon(helper, donor, 1000, 2000, ModHamonSkills.CHARACTER_ZEPPELI.get());
			HamonData received = grantHamon(helper, recipient, 100, 200, null);
			donor.hurt(donor.damageSources().genericKill(), Float.MAX_VALUE);
			helper.assertTrue(donor.isAlive(), "The canceling death listener did not run");
			assertPoints(helper, received, 100, 200, "Canceled death");
			helper.assertTrue(!source.serializeNBT(helper.getLevel().registryAccess()).getBoolean("DeathPerksTriggered"),
					"Canceled death consumed its future transfer");
			NeoForge.EVENT_BUS.unregister(cancelDeath);
			// Canceling death restores health, but retains the first hit's hurt cooldown.
			donor.invulnerableTime = 0;
			kill(helper, donor);
			assertPoints(helper, received, 1100, 2200, "Uncanceled follow-up death");
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(cancelDeath);
			donor.discard();
			recipient.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void crimsonBubbleHasOneConsumerAndReleasesHeldItem(GameTestHelper helper) {
		Pig donor = createDonor(helper);
		ServerPlayer first = createRecipient(helper, "HamonBubbleOne", 6.0D);
		ServerPlayer second = createRecipient(helper, "HamonBubbleTwo", 7.0D);
		CrimsonBubbleEntity bubble = null;
		ItemEntity heldItem = null;
		try {
			HamonData source = grantHamon(helper, donor, 1000, 2000, ModHamonSkills.CHARACTER_CAESAR.get());
			// Both learned perks must still choose the Caesar branch only.
			helper.assertTrue(source.learnSkill(ModHamonSkills.DEEP_PASS.get()), "Could not grant second death perk");
			HamonData firstData = grantHamon(helper, first, 100, 200, null);
			HamonData secondData = grantHamon(helper, second, 100, 200, null);
			donor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
			kill(helper, donor);
			assertPoints(helper, firstData, 100, 200, "Caesar did not also award Deep Pass");
			List<CrimsonBubbleEntity> bubbles = helper.getLevel().getEntitiesOfClass(
					CrimsonBubbleEntity.class, donor.getBoundingBox().inflate(4.0D));
			helper.assertTrue(bubbles.size() == 1, "Caesar death did not create exactly one bubble");
			bubble = bubbles.getFirst();
			helper.assertTrue(bubble.getPassengers().size() == 1 && bubble.getFirstPassenger() instanceof ItemEntity,
					"Caesar's main-hand item was not carried by the bubble");
			heldItem = (ItemEntity) bubble.getFirstPassenger();
			helper.assertTrue(heldItem.getItem().is(Items.DIAMOND) && heldItem.getAge() == -6000,
					"Carried item lost its identity or extended lifetime");
			HamonUtil.hamonPerksOnDeath(donor);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(CrimsonBubbleEntity.class,
					donor.getBoundingBox().inflate(4.0D)).size() == 1, "Repeated death created another bubble");
			first.setPos(bubble.position());
			second.setPos(bubble.position());
			bubble.tick();
			helper.assertTrue(bubble.isRemoved(), "Claimed bubble survived its consumption tick");
			boolean firstWon = firstData.getHamonStrengthPoints() == 900;
			assertPoints(helper, firstWon ? firstData : secondData, 900, 1800, "Crimson bubble receiver");
			assertPoints(helper, firstWon ? secondData : firstData, 100, 200, "Second overlapping receiver");
			helper.assertTrue(heldItem.isAlive() && !heldItem.isPassenger(), "Consumed bubble did not release its item");
			bubble.tick();
			helper.assertTrue(!bubble.hurt(first.damageSources().playerAttack(first), 1.0F),
					"A spent bubble still accepted attacks");
			assertPoints(helper, firstWon ? firstData : secondData, 900, 1800, "Repeated bubble collision");
			CompoundTag spent = bubble.saveWithoutId(new CompoundTag());
			helper.assertTrue(spent.getInt("StrengthPoints") == 0 && spent.getInt("ControlPoints") == 0,
					"Consumed payload remained serializable for replay");
			helper.succeed();
		}
		finally {
			if (bubble != null) {
				bubble.discard();
			}
			if (heldItem != null) {
				heldItem.discard();
			}
			donor.discard();
			first.discard();
			second.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void crimsonBubbleReloadBoundsAndExpiration(GameTestHelper helper) {
		CrimsonBubbleEntity bubble = new CrimsonBubbleEntity(helper.getLevel());
		ItemEntity item = new ItemEntity(helper.getLevel(), 0.0D, 0.0D, 0.0D, new ItemStack(Items.DIAMOND));
		try {
			bubble.setPos(origin(helper));
			bubble.setHamonPoints(1000, 2000);
			CompoundTag saved = bubble.saveWithoutId(new CompoundTag());
			bubble.load(saved);
			CompoundTag restored = bubble.saveWithoutId(new CompoundTag());
			helper.assertTrue(restored.getInt("StrengthPoints") == 1000 && restored.getInt("ControlPoints") == 2000,
					"Valid bubble payload changed on reload");
			saved.putInt("StrengthPoints", -1);
			saved.putInt("ControlPoints", Integer.MAX_VALUE);
			bubble.load(saved);
			restored = bubble.saveWithoutId(new CompoundTag());
			helper.assertTrue(restored.getInt("StrengthPoints") == 0
					&& restored.getInt("ControlPoints") == HamonData.MAX_HAMON_POINTS,
					"Persisted bubble payload escaped the Hamon stat bounds");
			helper.assertTrue(helper.getLevel().addFreshEntity(bubble), "Could not add persisted bubble");
			item.setPos(bubble.position());
			helper.assertTrue(helper.getLevel().addFreshEntity(item), "Could not add carried item");
			bubble.putItem(item);
			CompoundTag oldItem = item.saveWithoutId(new CompoundTag());
			oldItem.putShort("Age", (short) 5999);
			item.load(oldItem);
			bubble.tick();
			helper.assertTrue(item.getAge() == -6000, "Live bubble did not preserve its carried item");
			bubble.setHamonPoints(0, 0);
			bubble.tick();
			helper.assertTrue(bubble.isRemoved() && item.isAlive() && !item.isPassenger(),
					"Expired bubble did not terminate and release its item");
			item.tick();
			helper.assertTrue(item.getAge() == -5999, "Released item kept an unlimited lifetime");
			helper.succeed();
		}
		finally {
			bubble.discard();
			item.discard();
		}
	}

	private static Vec3 origin(GameTestHelper helper) {
		return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 4, 2)));
	}

	private static Pig createDonor(GameTestHelper helper) {
		Pig donor = new Pig(EntityType.PIG, helper.getLevel());
		donor.setBaby(true);
		donor.setNoAi(true);
		donor.setNoGravity(true);
		donor.setPos(origin(helper));
		helper.assertTrue(helper.getLevel().addFreshEntity(donor), "Could not add Hamon donor");
		return donor;
	}

	private static ServerPlayer createRecipient(GameTestHelper helper, String name, double offset) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(origin(helper).add(offset, 0.0D, 0.0D));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add Hamon recipient");
		return player;
	}

	private static HamonData grantHamon(GameTestHelper helper, LivingEntity user,
			int strength, int control, HamonTechnique technique) {
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
		power.setPowerType(ModPlayerPowers.HAMON.get());
		HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, strength, true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, control, true, true);
		if (technique != null) {
			helper.assertTrue(hamon.pickHamonTechnique(user, technique), "Could not pick donor technique");
		}
		return hamon;
	}

	private static void kill(GameTestHelper helper, LivingEntity donor) {
		donor.hurt(donor.damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(!donor.isAlive(), "Lethal damage did not kill Hamon donor");
	}

	private static void assertPoints(GameTestHelper helper, HamonData hamon,
			int strength, int control, String label) {
		helper.assertTrue(hamon.getHamonStrengthPoints() == strength && hamon.getHamonControlPoints() == control,
				label + ": expected=" + strength + "/" + control + ", actual="
						+ hamon.getHamonStrengthPoints() + "/" + hamon.getHamonControlPoints());
	}
}
