package rotp.core.mixin.hamon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.init.ModDamageTypes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;

// 1.16 DamageUtil.dealHamonDamage built an IndirectEntityDamageSource whenever it got a direct and an indirect
// entity (a Hamon projectile and its owner, a charged projectile, the body an S.Y.O. punch knocked into the
// target), and a 1.16 Enderman teleports away unharmed from every indirect source. 1.21 asks the damage type
// instead, and the Hamon type is not projectile damage.
@Mixin(EnderMan.class)
public abstract class EnderManIndirectHamonMixin {
	@WrapOperation(
			method = "hurt",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean jojo$dodgeIndirectHamon(
			DamageSource source,
			TagKey<DamageType> tag,
			Operation<Boolean> original) {
		return original.call(source, tag)
				|| tag == DamageTypeTags.IS_PROJECTILE && source.is(ModDamageTypes.HAMON) && jojo$isIndirect(source);
	}

	@Unique
	private static boolean jojo$isIndirect(DamageSource source) {
		Entity direct = source.getDirectEntity();
		Entity owner = source.getEntity();
		return direct != null && owner != null && direct != owner;
	}
}
