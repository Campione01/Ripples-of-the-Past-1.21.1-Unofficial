package rotp.core.mixin.clackers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.decoration.ArmorStand;

// The 1.16 Clackers were arrows, and one arrow breaks an armour stand. 1.21 asks the damage type instead of the
// projectile's class, and the Clackers share theirs with projectiles that never broke one.
@Mixin(ArmorStand.class)
public abstract class ArmorStandClackersMixin {
	@WrapOperation(
			method = "hurt",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean jojo$breakByClackersLikeArrows(
			DamageSource source,
			TagKey<DamageType> tag,
			Operation<Boolean> original) {
		return original.call(source, tag)
				|| tag == DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS && source.getDirectEntity() instanceof ClackersEntity;
	}
}
