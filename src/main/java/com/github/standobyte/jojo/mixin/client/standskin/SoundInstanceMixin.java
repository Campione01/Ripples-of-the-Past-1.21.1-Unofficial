package com.github.standobyte.jojo.mixin.client.standskin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.client.standskin.sound.SoundInstanceWithStandSkin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;

@Mixin(AbstractSoundInstance.class)
public class SoundInstanceMixin implements SoundInstanceWithStandSkin {
	@Shadow protected Sound sound;
	@Shadow @Final protected ResourceLocation location;
	private ResourceLocation jojo_ripples$standId;
	private Optional<ResourceLocation> jojo_ripples$selectedStandSkin;
	private StandSkin jojo_ripples$standSkin;
	
	@Override
	public void jojo_ripples$setStandSkin(ResourceLocation standId, Optional<ResourceLocation> standSkin) {
		this.jojo_ripples$standId = standId;
		this.jojo_ripples$selectedStandSkin = standSkin;
	}

	@ModifyVariable(method = "resolve", at = @At("STORE"), ordinal = 0)
	public WeighedSoundEvents jojo_ripples$overrideJsonSound(WeighedSoundEvents soundEvent) {
		if (jojo_ripples$standId != null) {
			StandSkinsLoader standSkinsLoader = StandSkinsLoader.getInstance();
			jojo_ripples$standSkin = standSkinsLoader.getSkinFromId(jojo_ripples$standId, jojo_ripples$selectedStandSkin);
			if (jojo_ripples$standSkin == null) {
				jojo_ripples$standSkin = standSkinsLoader.getDefaultSkin(jojo_ripples$standId);
			}
			if (jojo_ripples$standSkin != null) {
				WeighedSoundEvents standSkinSoundEvent = jojo_ripples$standSkin.getSoundEvent(location);
				if (standSkinSoundEvent != null) {
					return standSkinSoundEvent;
				}
			}
		}
		
		return soundEvent;
	}
	
	@Inject(method = "resolve", at = @At("TAIL"))
	public void jojo_ripples$overrideSoundFile(SoundManager handler, CallbackInfoReturnable<WeighedSoundEvents> ci) {
		if (jojo_ripples$standSkin != null && this.sound != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
			Sound standSkinSound = jojo_ripples$standSkin.overrideSound(sound);
			if (standSkinSound != null) {
				this.sound = standSkinSound;
			}
		}
	}
}
