package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandDiscItem.use ran putOutStand() (clear() in Creative), so ResolveCounter.onClearStandType zeroed the
 * Resolve value, records and boosts, then giveStandFromDisc -> onNewPowerGiven set the leap cooldown to 0 (no Stand
 * manifested) and the stamina to half. A disc used by someone who already has a Stand starts the new Stand the same
 * way; the port's disc goes through the context replace, which otherwise keeps all of it (Tusk's act change).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandDiscSwapGameTests {
	private static final ResourceLocation STAR_PLATINUM = JojoMod.resLoc("star_platinum");
	private static final ResourceLocation THE_WORLD = JojoMod.resLoc("the_world");
	private static final int LEFTOVER_LEAP = 60;
	private static final float KEPT_STAMINA = 100.0F;
	private static final float EPS = 1.0E-3F;

	private StandDiscSwapGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void discSwapStartsTheNewStandLikeA116Give(GameTestHelper helper) {
		String name = "DiscSwapReset";
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		player.moveTo(at.x, at.y, at.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + name);
		StandPower power = PowerClass.STAND.attachGet(player);
		// the swapped-out Star Platinum disc stays counted as taken (1.16 putOutStand); taken back in finally
		var holders = rotp.core.subsystems.ServerDuplicateCounter.StandHolders.get(helper.getLevel().getServer());
		int onDiscBefore = holders.getOnDisc(STAR_PLATINUM);
		try {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type(helper, STAR_PLATINUM)))
					.applied(), "Could not grant Star Platinum");
			helper.assertTrue(!player.getAbilities().instabuild && !power.isStaminaInfinite(),
					"The disc user must be in Survival with finite stamina, the test would prove nothing");

			// what a fight leaves: a Resolve value in its no-decay ticks, a boost, a record, a leap cooldown
			CompoundTag nbt = power.resolveCounter.writeNBT();
			nbt.putFloat("Resolve", 400.0F);
			nbt.putInt("ResolveTicks", 300);
			nbt.putFloat("BoostAttack", 2.0F);
			ListTag records = new ListTag();
			records.add(FloatTag.valueOf(900.0F));
			nbt.put("ResolveRecord", records);
			nbt.putFloat("MaxAchieved", 900.0F);
			power.resolveCounter.readNBT(nbt);
			power.setStamina(KEPT_STAMINA);
			power.setLeapCooldown(LEFTOVER_LEAP);
			ResolveCounter counter = power.resolveCounter;
			helper.assertTrue(Math.abs(counter.getResolveValue() - 400.0F) < EPS && counter.boostAttack == 2.0F
					&& counter.maxAchievedValue == 900.0F, "Could not fill the Resolve");
			helper.assertTrue(Math.abs(power.getStamina() - KEPT_STAMINA) < EPS
					&& power.getLeapCooldown() == LEFTOVER_LEAP, "Could not set the stamina and leap cooldown");

			ItemStack disc = StandDiscItem.withStand(new StandInstance(type(helper, THE_WORLD)));
			player.setItemInHand(InteractionHand.MAIN_HAND, disc);
			InteractionResult result = disc.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
			helper.assertTrue(result.consumesAction() && power.getPowerType() == type(helper, THE_WORLD),
					"The disc did not swap Star Platinum for The World; result " + result);

			helper.assertTrue(counter.getResolveValue() == 0.0F && counter.noResolveDecayTicks == 0,
					"The disc swap kept the old Stand's Resolve value: " + counter.getResolveValue());
			helper.assertTrue(counter.boostAttack == 1.0F,
					"The disc swap kept the old Stand's attack boost: " + counter.boostAttack);
			helper.assertTrue(counter.maxAchievedValue == 0.0F
					&& counter.writeNBT().getList("ResolveRecord", Tag.TAG_FLOAT).isEmpty(),
					"The disc swap kept the old Stand's Resolve records (max " + counter.maxAchievedValue + ")");
			float max = power.getMaxStamina();
			helper.assertTrue(max > 0 && Math.abs(power.getStamina() - max * 0.5F) < EPS,
					"The disc swap did not start The World at half stamina: " + power.getStamina() + " / " + max);
			helper.assertTrue(power.getLeapCooldown() == 0,
					"The disc swap kept the old Stand's leap cooldown: " + power.getLeapCooldown());
		}
		finally {
			if (power.isSummoned() && power.getPowerType() != null) {
				power.getPowerType().forceUnsummon(player, power);
			}
			player.discard();
			while (holders.getOnDisc(STAR_PLATINUM) > onDiscBefore && holders.takeFromDisc(STAR_PLATINUM)) {
			}
		}
		helper.succeed();
	}

	private static StandType type(GameTestHelper helper, ResourceLocation standId) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
		helper.assertTrue(type != null, "Missing registered Stand " + standId);
		return type;
	}
}
