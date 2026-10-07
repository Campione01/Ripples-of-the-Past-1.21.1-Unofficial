package rotp.core.mixin.clackers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.init.ModDamageTypes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.monster.EnderMan;

// The Hamon hit of thrown 1.16 Clackers was an indirect entity source, which an Enderman dodges like the hit of
// the Clackers themselves. 1.21 asks the damage type instead, and the Hamon type is not projectile damage.
@Mixin(EnderMan.class)
public abstract class EnderManClackersMixin {
	@WrapOperation(
			method = "hurt",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean jojo$dodgeClackersHamonLikeTheirHit(
			DamageSource source,
			TagKey<DamageType> tag,
			Operation<Boolean> original) {
		return original.call(source, tag)
				|| tag == DamageTypeTags.IS_PROJECTILE && source.is(ModDamageTypes.HAMON)
						&& source.getDirectEntity() instanceof ClackersEntity && source.getEntity() != null;
	}
}
