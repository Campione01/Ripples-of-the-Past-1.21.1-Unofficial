package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.DataEventListeners;
import rotp.core.impl.stands.boyiiman.BoyIIManStandPartTakenEffect;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Stand effects whose target dies: 1.16 kept the target where the effect still had work to do.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandEffectTargetDeathGameTests {

	private StandEffectTargetDeathGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void boyIIManKeepsTakenArmsWhenLoserDiesWithKeepStand(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = keepOnDeathConfig();
		boolean previous = keep.get();
		ServerLevel level = helper.getLevel();
		GameProfile loserProfile = new GameProfile(UUID.randomUUID(), "BiimLoser");
		FakePlayer winner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BiimWinner"));
		FakePlayer loser = new FakePlayer(level, loserProfile);
		FakePlayer respawned = new FakePlayer(level, loserProfile);
		try {
			keep.set(true);
			StandPower winnerPower = PowerClass.STAND.attachGet(winner);
			BoyIIManStandPartTakenEffect effect = takeArms(helper, winnerPower, loser);
			loser.setHealth(0.0F);
			helper.assertTrue(loser.isDeadOrDying(), "Loser death fixture failed");
			for (int tick = 0; tick < 3; tick++) {
				listeners(winner).onTick();
			}
			helper.assertTrue(winnerPower.userStandEffects.getById(effect.getId()) == effect && !effect.isStopped(),
					"Taken-arms effect ended when the loser died with keepStandOnDeath on");
			helper.assertTrue(loser.getUUID().equals(effect.getTargetUUID()),
					"Taken-arms effect forgot the dead loser's UUID");

			listeners(loser).onClone(respawned, true);
			StandPower respawnedPower = PowerClass.STAND.attachGet(respawned);
			helper.assertTrue(respawnedPower.getStandInstance().map(stand -> !stand.hasPart(StandPart.ARMS)).orElse(false),
					"Respawn fixture must keep the Stand without its arms");
			// The server finds the respawned player by the kept UUID; fake players are not in the level.
			effect.setTargetEntity(respawned);
			winnerPower.userStandEffects.removeEffect(effect);
			helper.assertTrue(respawnedPower.getStandInstance().map(stand -> stand.hasPart(StandPart.ARMS)).orElse(false),
					"Ending the effect did not give the arms back to the respawned loser");
		}
		finally {
			keep.set(previous);
			discardPlayers(winner, loser, respawned);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void boyIIManDropsTakenArmsWhenLoserDiesWithoutKeepStand(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = keepOnDeathConfig();
		boolean previous = keep.get();
		ServerLevel level = helper.getLevel();
		FakePlayer winner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BiimWinner"));
		FakePlayer loser = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BiimLoser"));
		try {
			keep.set(false);
			StandPower winnerPower = PowerClass.STAND.attachGet(winner);
			BoyIIManStandPartTakenEffect effect = takeArms(helper, winnerPower, loser);
			loser.setHealth(0.0F);
			listeners(winner).onTick();
			helper.assertTrue(winnerPower.userStandEffects.getById(effect.getId()) == null
					&& effect.getTargetUUID() == null,
					"Without keepStandOnDeath the loser's death must end the taken-arms effect");
		}
		finally {
			keep.set(previous);
			discardPlayers(winner, loser);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void killedGoldExperienceLifeformTurnsBackIntoItsSource(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GeLifeformUser"));
		Pig lifeform = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
		AABB area = lifeform.getBoundingBox().inflate(3.0);
		try {
			StandPower power = PowerClass.STAND.attachGet(user);
			GECreatedLifeformEffect effect = ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get().create(level);
			effect.setSourceItem(new ItemStack(Items.APPLE));
			effect.withTarget(lifeform);
			power.userStandEffects.addEffect(effect);
			lifeform.kill();
			helper.assertTrue(lifeform.isDeadOrDying(), "Lifeform kill fixture failed");
			listeners(user).onTick();
			helper.assertTrue(power.userStandEffects.getById(effect.getId()) == null,
					"Killed lifeform's effect did not end");
			List<GETransformationEntity> reverses = level.getEntities(ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get(),
					area, transformation -> transformation.getTransformationTarget() == lifeform);
			helper.assertTrue(reverses.size() == 1,
					"Killed lifeform did not start turning back into its source; found " + reverses.size());
			GETransformationEntity reverse = reverses.get(0);
			helper.assertTrue(reverse.isTurningBack() && reverse.getSourceItemView().is(Items.APPLE),
					"Reverse transformation lost the source item");
			helper.assertTrue(lifeform.isRemoved(), "Killed lifeform body was not replaced by the reverse transformation");
		}
		finally {
			level.getEntities(ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get(), area, transformation -> true)
					.forEach(GETransformationEntity::discard);
			lifeform.discard();
			discardPlayers(user);
		}
		helper.succeed();
	}

	private static BoyIIManStandPartTakenEffect takeArms(GameTestHelper helper, StandPower winnerPower, ServerPlayer loser) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "Missing registered Star Platinum");
		StandPower loserPower = PowerClass.STAND.attachGet(loser);
		helper.assertTrue(StandPowerTransitions.insert(loserPower, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant the loser's Stand");
		// Same split as RockPaperScissorsGame after Boy II Man's first win.
		StandInstance taken = new StandInstance(type);
		for (StandPart part : StandPart.values()) {
			if (part != StandPart.ARMS) {
				taken.removePart(part);
			}
		}
		loserPower.getStandInstance().orElseThrow().removePart(StandPart.ARMS);
		BoyIIManStandPartTakenEffect effect = new BoyIIManStandPartTakenEffect(taken);
		effect.withTarget(loser);
		winnerPower.userStandEffects.addEffect(effect);
		return effect;
	}

	private static ModConfigSpec.BooleanValue keepOnDeathConfig() {
		// Spec setters are memory-only; restore in finally and never save the user's config.
		return JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Keep Powers After Death", "keepStandOnDeath"));
	}

	private static DataEventListeners listeners(ServerPlayer player) {
		return player.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get());
	}

	private static void discardPlayers(FakePlayer... players) {
		for (FakePlayer player : players) {
			StandPower power = StandPower.get(player);
			if (power != null) {
				for (StandEffectInstance effect : List.copyOf(power.userStandEffects.getEffects())) {
					power.userStandEffects.removeEffect(effect);
				}
			}
			player.discard();
		}
	}
}
