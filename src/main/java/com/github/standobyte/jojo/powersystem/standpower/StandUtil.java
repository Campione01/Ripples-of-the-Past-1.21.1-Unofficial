package com.github.standobyte.jojo.powersystem.standpower;

import javax.annotation.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.init.ModEntityAttributes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.modcompat.JojoModsInteraction;
import com.github.standobyte.jojo.network.s2c.StandEntitySoundPacket;
import com.github.standobyte.jojo.network.s2c.StandSkinSoundPacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.util.functions.AttributeUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.sound.MultiSoundEventResolver;
import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandUtil {
	
	public static Stream<StandType> standsForPlayerArrow() {
		return StandType.getAllEnabledStands().filter(ModStands.PLAYER_CAN_GET_FROM_ARROW::contains);
	}
	
	public static Either<StandType, Component> randomStandOrError(Player player, RandomSource random) {
		if (player.level().isClientSide()) {
			throw new IllegalStateException("Can only use this function to get a random Stand on server side");
		}
		List<StandType> stands = standsForPlayerArrow().toList();
		if (stands.isEmpty()) {
			return Either.right(Component.translatable("jojo.arrow.no_stands"));
		}
		return Either.left(stands.get(random.nextInt(stands.size())));
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
