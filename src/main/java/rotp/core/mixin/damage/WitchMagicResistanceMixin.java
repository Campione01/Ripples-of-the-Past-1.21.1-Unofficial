package rotp.core.mixin.damage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import rotp.core.util.functions.DamageUtil;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;

@Mixin(Witch.class)
public abstract class WitchMagicResistanceMixin {
	@ModifyExpressionValue(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/raid/Raider;getDamageAfterMagicAbsorb("
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)F"))
	private float jojo_ripples$recordMagicResistance(float damage, @Local(argsOnly = true) DamageSource source) {
		DamageUtil.recordWitchMagicResistance((LivingEntity) (Object) this, source, damage);
		return damage;
	}
}
