package com.github.standobyte.jojoimpl.npc.rps;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.network.s2c.RPSGameStatePacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rock Paper Scissors Kid encounter mob.
 */
public class RockPaperScissorsKidEntity extends PathfinderMob {

	public RockPaperScissorsKidEntity(EntityType<? extends RockPaperScissorsKidEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	private StandPower ensureBoyIIManPower() {
		StandPower standPower = PowerClass.STAND.attachGet(this);
		if (standPower.getPowerType() != ModStands.BOY_II_MAN.get()) {
			standPower.setStand(ModStands.BOY_II_MAN.get());
		}
		return standPower;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
			@Nullable SpawnGroupData spawnGroupData) {
		ensureBoyIIManPower();
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			ensureBoyIIManPower();
			ServerSavedData data = ServerSavedData.get(serverPlayer.getServer());
			RockPaperScissorsGame game = data.rpsPvpGames.get(serverPlayer.getUUID());
			if (game == null || !game.opponentIsNpc() || !getUUID().equals(game.opponent()) || game.isMatchOver()) {
				data.rpsPvpGames.put(serverPlayer, getUUID(), true);
				game = data.rpsPvpGames.get(serverPlayer.getUUID());
				if (game != null) {
					game.setOpponentThoughts(RockPaperScissorsGame.Pick.SCISSORS);
				}
			}
			if (game != null) {
				PacketDistributor.sendToPlayer(serverPlayer, RPSGameStatePacket.enteredGame(getId(),
						game.playerPreviousPicks(), game.opponentPreviousPicks(), game.round()));
			}
			data.setDirty();
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}
}
