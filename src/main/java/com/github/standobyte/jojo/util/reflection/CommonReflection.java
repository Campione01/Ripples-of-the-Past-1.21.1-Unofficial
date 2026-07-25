package com.github.standobyte.jojo.util.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

public final class CommonReflection {
	
	private static final Field NEO_DataPackRegistriesHooks_NETWORKABLE_REGISTRIES = ObfuscationReflectionHelper.findField(DataPackRegistriesHooks.class, "NETWORKABLE_REGISTRIES");
	private static final Field CREEPER_SWELL = ObfuscationReflectionHelper.findField(Creeper.class, "swell");
	private static final Field CREEPER_DATA_IS_POWERED = ObfuscationReflectionHelper.findField(Creeper.class, "DATA_IS_POWERED");
	private static final Field TNT_MINECART_FUSE = ObfuscationReflectionHelper.findField(MinecartTNT.class, "fuse");
	private static final Method ZOMBIE_VILLAGER_START_CONVERTING = ObfuscationReflectionHelper.findMethod(ZombieVillager.class, "startConverting", UUID.class, int.class);
	private static final Method MOB_GET_AMBIENT_SOUND = ObfuscationReflectionHelper.findMethod(Mob.class, "getAmbientSound");
	private static final Method LIVING_PLAY_HURT_SOUND = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "playHurtSound", DamageSource.class);
	private static final Method LIVING_DROP_ALL_DEATH_LOOT = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "dropAllDeathLoot", ServerLevel.class, DamageSource.class);
	private static final Field PLAYER_SLEEP_COUNTER = ObfuscationReflectionHelper.findField(Player.class, "sleepCounter");

	public static List<RegistryDataLoader.RegistryData<?>> getDataPackNetworkableRegistries() {
		return ReflectionUtil.getFieldValue(NEO_DataPackRegistriesHooks_NETWORKABLE_REGISTRIES, null);
	}

	public static void setCreeperSwell(Creeper creeper, int swell) {
		ReflectionUtil.setIntFieldValue(CREEPER_SWELL, creeper, swell);
	}

	public static void setCreeperPowered(Creeper creeper, boolean powered) {
		EntityDataAccessor<Boolean> poweredData = ReflectionUtil.getFieldValue(CREEPER_DATA_IS_POWERED, null);
		creeper.getEntityData().set(poweredData, powered);
	}

	public static void setTntMinecartFuse(MinecartTNT minecart, int fuse) {
		ReflectionUtil.setIntFieldValue(TNT_MINECART_FUSE, minecart, fuse);
	}

	public static void startConverting(ZombieVillager entity, @Nullable UUID conversionStarter, int villagerConversionTime) {
		ReflectionUtil.invokeMethod(ZOMBIE_VILLAGER_START_CONVERTING, entity, conversionStarter, villagerConversionTime);
	}

	@Nullable
	public static SoundEvent getAmbientSound(Mob mob) {
		return ReflectionUtil.invokeMethod(MOB_GET_AMBIENT_SOUND, mob);
	}

	public static void playHurtSound(LivingEntity entity, DamageSource damageSource) {
		ReflectionUtil.invokeMethod(LIVING_PLAY_HURT_SOUND, entity, damageSource);
	}

	public static void dropAllDeathLoot(LivingEntity entity, DamageSource damageSource) {
		if (entity.level() instanceof ServerLevel serverLevel) {
			ReflectionUtil.invokeMethod(LIVING_DROP_ALL_DEATH_LOOT, entity, serverLevel, damageSource);
		}
	}

	public static void setSleepCounter(Player player, int sleepCounter) {
		ReflectionUtil.setIntFieldValue(PLAYER_SLEEP_COUNTER, player, sleepCounter);
	}
}
