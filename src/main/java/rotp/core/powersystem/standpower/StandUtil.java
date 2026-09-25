package rotp.core.powersystem.standpower;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandArrowPoolOverrides;
import rotp.core.command.configpack.PlayerStandAssignmentConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStands;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.modcompat.JojoModsInteraction;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.network.s2c.StandSkinSoundPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerDuplicateCounter;
import rotp.core.subsystems.ServerDuplicateCounter.UniquenessPriority;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.util.functions.AttributeUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.sound.MultiSoundEventResolver;
import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandUtil {
	
	public static Stream<StandType> standsForPlayerArrow() {
		return standsForPlayerArrow(false);
	}

	public static Stream<StandType> standsForPlayerArrow(boolean clientSide) {
		return StandType.getAllEnabledStands()
				.filter(ModStands.PLAYER_CAN_GET_FROM_ARROW::contains)
				.filter(stand -> !StandArrowPoolOverrides
						.isExcluded(stand.getId()))
				.filter(stand -> !isStandBanned(stand, clientSide));
	}

	// 1.16 bannedStands config: out of arrows, random rolls, discs and the creative tab
	public static boolean isStandBanned(@Nullable StandType standType, boolean clientSide) {
		return standType != null
				&& JojoModConfig.getCommonConfigInstance(clientSide).isStandBanned(standType.getId());
	}
	
	public static Either<StandType, Component> randomStandOrError(Player player, RandomSource random) {
		if (player.level().isClientSide()) {
			throw new IllegalStateException("Can only use this function to get a random Stand on server side");
		}
		List<StandType> stands = standsForPlayerArrow().toList();
		if (stands.isEmpty()) {
			return Either.right(Component.translatable("jojo.arrow.no_stands"));
		}
		// 1.16: /jojoconfig assign_stand limits the pool per player
		stands = PlayerStandAssignmentConfig.limitToAssignedStands(player, stands);
		if (stands.isEmpty()) {
			return Either.right(Component.translatable("jojo.arrow.assigned_banned", player.getName()));
		}
		// 1.16 standArrowMode: LEAST_TAKEN / NOT_TAKEN narrow the pool server-wide
		stands = limitStandPool(JojoModConfig.getCommonConfigInstance(false).standArrowMode.get(), player, stands);
		if (stands.isEmpty()) {
			return Either.right(Component.translatable("jojo.arrow.all_stands_taken"));
		}
		Optional<StandType> selected = randomWeightedStand(stands, random);
		return selected.<Either<StandType, Component>>map(Either::left)
				.orElseGet(() -> Either.right(
						Component.translatable("jojo.arrow.no_stand_weights")));
	}

	// 1.16 StandUtil.StandRandomPoolFilter (config key standArrowMode)
	public enum StandRandomPoolFilter {
		NONE,
		// only the Stands the fewest players on the server hold
		LEAST_TAKEN,
		// only the Stands no player on the server holds
		NOT_TAKEN
	}

	public static List<StandType> limitStandPool(@Nullable StandRandomPoolFilter filter, Player player, List<StandType> stands) {
		MinecraftServer server = player.getServer();
		if (filter == null || filter == StandRandomPoolFilter.NONE || stands.isEmpty() || server == null) {
			return stands;
		}
		UniquenessPriority mode = filter == StandRandomPoolFilter.NOT_TAKEN
				? UniquenessPriority.ON_SERVER_UNIQUE
				: UniquenessPriority.ON_SERVER;
		return ServerDuplicateCounter.StandHolders.get(server).counter
				.getMostUnique(mode, player.getUUID(), stands.stream(), StandType::getId)
				.toList();
	}

	// 1.16 StandPower give/clear kept the server-wide taken count
	public static void trackTakenStand(ServerPlayer player, @Nullable StandType stand) {
		MinecraftServer server = player.getServer();
		if (server != null) {
			ServerDuplicateCounter.StandHolders.get(server).setHeld(player.getUUID(), stand != null ? stand.getId() : null);
		}
	}

	// Stands given before the count existed are counted on login
	@EventBusSubscriber(modid = JojoMod.MOD_ID)
	public static class TakenStandEvents {
		@SubscribeEvent
		public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
			if (event.getEntity() instanceof ServerPlayer player) {
				trackTakenStand(player, PowerClass.STAND.attachGet(player).getPowerType());
			}
		}
	}

	static Optional<StandType> randomWeightedStand(
			List<StandType> stands,
			RandomSource random) {
		double[] weights = new double[stands.size()];
		for (int i = 0; i < stands.size(); i++) {
			weights[i] = safeRandomWeight(stands.get(i));
		}
		int selectedIndex = randomWeightedIndex(weights, random);
		return selectedIndex >= 0
				? Optional.of(stands.get(selectedIndex))
				: Optional.empty();
	}

	static int randomWeightedIndex(
			double[] weights,
			RandomSource random) {
		double weightSum = 0.0D;
		for (double weight : weights) {
			weightSum += safeRandomWeight(weight);
		}
		if (!(weightSum > 0.0D) || !Double.isFinite(weightSum)) {
			return -1;
		}

		double randomWeight = random.nextDouble() * weightSum;
		int lastWeightedIndex = -1;
		for (int i = 0; i < weights.length; i++) {
			double weight = safeRandomWeight(weights[i]);
			if (weight <= 0.0D) {
				continue;
			}
			lastWeightedIndex = i;
			randomWeight -= weight;
			if (randomWeight < 0.0D) {
				return i;
			}
		}
		return lastWeightedIndex;
	}

	private static double safeRandomWeight(StandType stand) {
		return safeRandomWeight(stand.getStandStats().getRandomWeight());
	}

	private static double safeRandomWeight(double weight) {
		return Double.isFinite(weight) && weight > 0.0D ? weight : 0.0D;
	}

    public static LivingEntity getStandUser(LivingEntity entityMaybeStand) {
        if (entityMaybeStand instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            if (user != null) return user;
        }
        return entityMaybeStand;
    }
    
    @Nullable
    public static StandEntity getSummonedStand(LivingEntity standUser) {
    	StandPower standPower = StandPower.get(standUser);
    	return standPower != null ? standPower.getSummonedStandEntity() : null;
    }

    public static StandEntity getSummonedStand(Power<?> standPower) {
    	StandPower _standPower = PowerClass.STAND.cast(standPower);
    	return _standPower != null ? _standPower.getSummonedStandEntity() : null;
    }

    @Nullable
    public static LivingEntity getStandGrabTarget(Power<?> power) {
    	StandEntity stand = getSummonedStand(power);
    	return stand != null ? LivingComponentGrab.getEntityGrabbedBy(stand) : null;
    }
    
    public static class StandAndUserEntity {
    	protected static StandAndUserEntity instance = new StandAndUserEntity();
    	
    	@Nullable public LivingEntity standUser;
    	@Nullable public LivingEntity standEntity;
    }
    
    public static StandAndUserEntity getStandAndUser(LivingEntity someEntity) {
    	LivingEntity targetStandEntity = StandUtil.getSummonedStand(someEntity);
    	LivingEntity targetStandUser = someEntity == targetStandEntity ? StandUtil.getStandUser(targetStandEntity) : someEntity;
    	StandAndUserEntity obj = StandAndUserEntity.instance;
    	obj.standUser = targetStandUser;
    	obj.standEntity = targetStandEntity;
    	return obj;
    }
    
    public static boolean isEntityStandUser(LivingEntity entity) {
    	StandPower standData = StandPower.get(entity);
    	return standData != null && standData.hasPower() || JojoModsInteraction.entityHasStandFromAnotherMod(entity);
    }

    public static boolean entityCanSeeStands(LivingEntity entity) {
    	return entity instanceof Player player && JojoModUtil.seesInvisibleAsSpectator(player)
    			|| isEntityStandUser(entity)
    			|| entity.hasEffect(ModStatusEffects.SPIRIT_VISION);
    }

    public static boolean entityCanHearStands(Player player) {
    	return entityCanSeeStands(player);
    }

	public static double staminaCondition(StandPower standPower) {
		return standIgnoresStaminaDebuff(standPower) ? 1
				: 0.25 + Math.min((double) (standPower.getStamina() / standPower.getMaxStamina()) * 1.5, 0.75);
	}

	public static boolean standIgnoresStaminaDebuff(StandPower standPower) {
		if (standPower == null) {
			return true;
		}
		LivingEntity user = standPower.getUser();
		return user == null || ResolveModeEffect.getResolveEffectLvl(user) >= 0 || standPower.isUserCreative();
	}

	/** 1.16 StandPower client tick: below half stamina, where the debuff applies, the stamina bar flashes red. */
	public static boolean showsStaminaDebuff(StandPower standPower) {
		return standPower != null && standPower.getStamina() < standPower.getMaxStamina() * 0.5F
				&& !standIgnoresStaminaDebuff(standPower);
	}

	/** 1.16 HUD: the Resolve icon was filled by level / max level. */
	public static float resolveLevelFill(StandPower standPower) {
		int maxLevel = standPower != null ? standPower.getMaxResolveLevel() : 0;
		return maxLevel > 0 ? Math.min(Math.max((float) standPower.getResolveLevel() / (float) maxLevel, 0.0F), 1.0F) : 0.0F;
	}

	
	public static double getPhysicalStatValue(StandPower standPower, StandStat stat) {
		StandEntity standEntity = standPower.getSummonedStandEntity();
		LivingEntity user = standPower.getUser();
		if (standEntity != null) {
			return switch (stat) {
				case STRENGTH -> standEntity.getAttackDamage();
				case ATTACK_SPEED -> standEntity.getAttackSpeed();
				case DURABILITY -> standEntity.getDurability();
				case PRECISION -> standEntity.getPrecision();
			};
		}
		else if (user != null) {
			Holder<Attribute> attribute = switch (stat) {
				case STRENGTH -> ModEntityAttributes.STAND_STRENGTH;
				case ATTACK_SPEED -> ModEntityAttributes.STAND_SPEED;
				case DURABILITY -> ModEntityAttributes.STAND_DURABILITY;
				case PRECISION -> ModEntityAttributes.STAND_PRECISION;
			};
			return AttributeUtil.getValueOrDefault(user, attribute, 0) * staminaCondition(standPower);
		}
		
		else return 0;
	}
	
	public enum StandStat {
		STRENGTH,
		ATTACK_SPEED,
		DURABILITY,
		PRECISION
	}

	public static void leap(Entity entity, float leapStrength) {
		entity.setOnGround(false);
		entity.hasImpulse = true;
		if (entity instanceof LivingEntity livingEntity) {
			livingEntity.setJumping(true);
		}
		Vec3 leap = Vec3.directionFromRotation(Math.min(entity.getXRot(), -30F), entity.getYRot()).scale(leapStrength);
		entity.setDeltaMovement(leap.x, leap.y * 0.5, leap.z);
	}

	/** 1.16 leap icon fill: 1 - cooldown / period, full without a period. */
	public static float leapIconFill(int cooldown, int period) {
		return period != 0 ? Math.min(Math.max(1.0F - (float) cooldown / (float) period, 0.0F), 1.0F) : 1.0F;
	}

	/** 1.16 leap icon x: beside the hotbar on the main arm's side, 20 further out past a hotbar attack indicator. */
	public static int leapIconX(int guiWidth, boolean rightArm, boolean hotbarAttackIndicator) {
		int shift = hotbarAttackIndicator ? 20 : 0;
		return rightArm ? guiWidth / 2 + 91 + 6 + shift : guiWidth / 2 - 91 - 22 - shift;
	}

	
	public static void broadcastSound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, 
			boolean onlyForStandUsers, StandPower userPower, 
			SoundSource category, float volume, float pitch) {
		PlayLevelSoundEvent.AtPosition event = EventHooks.onPlaySoundAtPosition(level, pos.x, pos.y, pos.z, sound, category, volume, pitch);
		if (event.isCanceled() || event.getSound() == null) return;
		
		sound = event.getSound();
		category = event.getSource();
		volume = event.getNewVolume();
		pitch = event.getNewPitch();
		sound = MultiSoundEventResolver.resolve(sound);
		
		StandSkinSoundPacket packet = StandSkinSoundPacket.play(pos, sound, userPower, category, volume, pitch);
		double radius = sound.value().getRange(volume);
        Packet<?> vanillaPacket = new ClientboundCustomPayloadPacket(packet);
        PlayerList playerList = level.getServer().getPlayerList();
        ResourceKey<Level> dimension = level.dimension();
        for (ServerPlayer player : playerList.getPlayers()) {
        	if (player.level().dimension() == dimension && (!onlyForStandUsers || StandUtil.entityCanHearStands(player))) {
        		double diffX = pos.x - player.getX();
        		double diffY = pos.y - player.getY();
        		double diffZ = pos.z - player.getZ();
        		if (diffX * diffX + diffY * diffY + diffZ * diffZ < radius * radius) {
        			player.connection.send(vanillaPacket);
        		}
        	}
        }
	}

	public static void broadcastSoundWithCondition(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound,
			boolean onlyForStandUsers, StandPower userPower,
			SoundSource category, float volume, float pitch, Predicate<ServerPlayer> playerFilter) {
		PlayLevelSoundEvent.AtPosition event = EventHooks.onPlaySoundAtPosition(level, pos.x, pos.y, pos.z, sound, category, volume, pitch);
		if (event.isCanceled() || event.getSound() == null) return;
		
		sound = event.getSound();
		category = event.getSource();
		volume = event.getNewVolume();
		pitch = event.getNewPitch();
		
		StandSkinSoundPacket packet = StandSkinSoundPacket.play(pos, sound, userPower, category, volume, pitch);
        Packet<?> vanillaPacket = new ClientboundCustomPayloadPacket(packet);
        PlayerList playerList = level.getServer().getPlayerList();
        ResourceKey<Level> dimension = level.dimension();
        for (ServerPlayer player : playerList.getPlayers()) {
        	if (player.level().dimension() == dimension
        			&& (!onlyForStandUsers || StandUtil.entityCanHearStands(player))
        			&& playerFilter.test(player)) {
        		player.connection.send(vanillaPacket);
        	}
        }
	}

	public static void playStandEntitySound(StandEntity standEntity, SoundEvent sound, float volume, float pitch) {
		playStandEntitySound(standEntity, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
	}

	public static void playStandEntitySound(StandEntity standEntity, Holder<SoundEvent> sound, float volume, float pitch) {
		if (standEntity.isSilent() || standEntity.level().isClientSide()) {
			return;
		}
		sound = MultiSoundEventResolver.resolve(sound);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(standEntity,
				new StandEntitySoundPacket(standEntity, sound, volume, pitch));
	}

}
