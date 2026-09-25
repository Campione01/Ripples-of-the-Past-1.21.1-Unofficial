package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowItem applied the Stand virus at level IV (StandVirusEffect.getEffectLevelToApply: 3 minus Virus
 * Inhibition). Every 10 ticks it dealt 1.5 + 2 * level (a tenth for a player with XP), and a mob given a Stand by the
 * virus survived with chance 1 - 0.15 * level (MobStandGiver). The bannedStands config kept Stands out of arrows,
 * random rolls and discs (JojoModConfig, StandDiscItem.use, StandType pools).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowVirusGameTests {
	private static final float EPS = 1.0E-3F;
	private static final int TURTLES = 24;

	private StandArrowVirusGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void arrowVirusIsLevelFourAndPlayerDamageScales(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			boolean pierced = StandArrowItem.onPiercedByArrow(player, new ItemStack(ModItems.STAND_ARROW.get()),
					helper.getLevel(), Optional.empty());
			MobEffectInstance virus = player.getEffect(ModStatusEffects.STAND_VIRUS);
			helper.assertTrue(pierced && virus != null && virus.getAmplifier() == 3,
					"1.16 arrows apply Stand virus IV (amplifier 3), got "
							+ (virus != null ? virus.getAmplifier() : "no effect"));

			player.experienceLevel = 0;
			player.setHealth(20.0F);
			tick(player, 10);
			helper.assertTrue(Math.abs(player.getHealth() - 12.5F) < EPS,
					"Without XP 1.16 dealt 1.5 + 2 * 3 = 7.5 per 10 ticks; health " + player.getHealth());

			player.experienceLevel = 5;
			player.setHealth(20.0F);
			tick(player, 10);
			helper.assertTrue(Math.abs(player.getHealth() - 19.25F) < EPS && player.experienceLevel == 4,
					"With XP 1.16 dealt a tenth (0.75) and took one level; health " + player.getHealth()
							+ ", level " + player.experienceLevel);
			helper.assertTrue(!PowerClass.STAND.attachGet(player).hasPower(),
					"Two virus ticks must not grant the Stand yet");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void mobVirusRollsTheSurviveChance(GameTestHelper helper) {
		List<LivingEntity> turtles = new ArrayList<>();
		int granted = 0;
		int killed = 0;
		try {
			for (int i = 0; i < TURTLES; i++) {
				LivingEntity turtle = (LivingEntity) ModEntityTypes.COCO_JUMBO_TURTLE.get().create(helper.getLevel());
				helper.assertTrue(turtle != null, "Could not create a Coco Jumbo turtle");
				turtle.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
				turtles.add(turtle);
				boolean pierced = StandArrowItem.onPiercedByArrow(turtle, new ItemStack(ModItems.STAND_ARROW.get()),
						helper.getLevel(), Optional.empty());
				MobEffectInstance virus = turtle.getEffect(ModStatusEffects.STAND_VIRUS);
				helper.assertTrue(pierced && virus != null && virus.getAmplifier() == 3,
						"1.16 arrows apply Stand virus IV to the turtle, got "
								+ (virus != null ? virus.getAmplifier() : "no effect"));
				// the first virus tick resolves the grant
				turtle.setHealth(1.0F);
				tick(turtle, 10);
				StandPower power = StandPower.get(turtle);
				if (power != null && power.hasPower()) {
					granted++;
				}
				else if (turtle.isDeadOrDying()) {
					killed++;
				}
				helper.assertTrue(!turtle.hasEffect(ModStatusEffects.STAND_VIRUS),
						"The virus did not end after its grant resolution");
			}
			// survive chance 1 - 0.15 * 3 = 0.55; all-or-nothing over 24 rolls is below 1e-6
			helper.assertTrue(granted > 0 && killed > 0 && granted + killed == TURTLES,
					"1.16 rolled 1 - 0.15 * level before a mob got its Stand and a failed roll dealt the damage;"
							+ " granted " + granted + ", killed " + killed + " of " + TURTLES);
		}
		finally {
			turtles.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void bannedStandsLeaveArrowsRandomRollsAndDiscs(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<List<? extends String>> config = bannedStandsConfig();
		List<? extends String> previous = config.get();
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BannedStands"));
		try {
			List<StandType> pool = StandUtil.standsForPlayerArrow().toList();
			StandType kept = pool.stream()
					.filter(stand -> stand.getStandStats().getRandomWeight() > 0)
					.findFirst().orElse(null);
			StandType banned = pool.stream()
					.filter(stand -> stand != kept)
					.min((a, b) -> Boolean.compare(!JojoMod.MOD_ID.equals(a.getId().getNamespace()),
							!JojoMod.MOD_ID.equals(b.getId().getNamespace())))
					.orElse(null);
			helper.assertTrue(kept != null && banned != null, "Fixture needs two arrow Stands");

			List<String> ids = new ArrayList<>();
			for (StandType stand : pool) {
				if (stand == banned && JojoMod.MOD_ID.equals(stand.getId().getNamespace())) {
					// the 1.16 "jojo:" id still names this mod's Stand
					ids.add("jojo:" + stand.getId().getPath());
				}
				else if (stand != kept) {
					ids.add(stand.getId().toString());
				}
			}
			config.set(ids);

			helper.assertTrue(StandUtil.standsForPlayerArrow().toList().equals(List.of(kept)),
					"Banned Stands stayed in the arrow pool: " + StandUtil.standsForPlayerArrow().toList());
			for (int i = 0; i < 16; i++) {
				Either<StandType, Component> roll = StandUtil.randomStandOrError(user, user.getRandom());
				helper.assertTrue(roll.left().orElse(null) == kept, "A random roll gave a banned Stand");
			}

			StandPower power = PowerClass.STAND.attachGet(user);
			ItemStack bannedDisc = StandDiscItem.withStand(new StandInstance(banned));
			user.setItemInHand(InteractionHand.MAIN_HAND, bannedDisc);
			InteractionResult result = bannedDisc.use(helper.getLevel(), user, InteractionHand.MAIN_HAND).getResult();
			helper.assertTrue(result == InteractionResult.FAIL && !power.hasPower() && bannedDisc.getCount() == 1,
					"1.16 refused a banned Stand's disc; result " + result + ", has Stand " + power.hasPower());

			// the client copy drives the arrow tooltip and the Creative tab
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
					helper.getLevel().registryAccess());
			new JojoModConfig.Common.SyncedValues(JojoModConfig.getCommonConfigInstance(false)).writeToBuf(buf);
			JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(buf));
			helper.assertTrue(StandUtil.isStandBanned(banned, true) && !StandUtil.isStandBanned(kept, true),
					"The ban list did not reach the synced client config");

			// control: the same disc works once the Stand is allowed again
			config.set(previous);
			ItemStack allowedDisc = StandDiscItem.withStand(new StandInstance(banned));
			user.setItemInHand(InteractionHand.MAIN_HAND, allowedDisc);
			result = allowedDisc.use(helper.getLevel(), user, InteractionHand.MAIN_HAND).getResult();
			helper.assertTrue(result.consumesAction() && power.getStandInstance()
					.map(StandInstance::getStandId).map(banned.getId()::equals).orElse(false),
					"Control: an allowed disc must give its Stand; result " + result);
		}
		finally {
			config.set(previous);
			JojoModConfig.resetSyncedConfig();
			user.discard();
		}
		helper.succeed();
	}

	private static void tick(LivingEntity entity, int ticks) {
		for (int i = 0; i < ticks; i++) {
			entity.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
		}
	}

	private static ModConfigSpec.ConfigValue<List<? extends String>> bannedStandsConfig() {
		// Spec setters are memory-only; restored in finally, the user's config is never saved.
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", "bannedStands"));
	}
}
