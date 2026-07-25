package com.github.standobyte.jojo.client.sound.sounds;

import com.github.standobyte.jojo.item.TommyGunItem;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class TommyGunLoopSound extends EntityStoppableSoundInstance {
	public TommyGunLoopSound(SoundEvent soundEvent, SoundSource source, float volume, LivingEntity entity, ItemStack tommyGunItem) {
		super(soundEvent, source, volume, 1.0F, entity, entity.getRandom().nextLong(), () -> shouldStop(entity, tommyGunItem));
		this.looping = true;
	}

	private static boolean shouldStop(LivingEntity entity, ItemStack tommyGunItem) {
		ItemStack usedItem = entity.getUseItem();
		return Minecraft.getInstance().level != entity.level()
				|| !entity.isAlive()
				|| usedItem.isEmpty()
				|| usedItem.getItem() != tommyGunItem.getItem()
				|| TommyGunItem.getAmmo(usedItem) <= 0;
	}
}
