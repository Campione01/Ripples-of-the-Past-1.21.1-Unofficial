package rotp.addons.nextalbum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import rotp.addons.nextalbum.NextAlbumGameEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

@Mixin({LivingEntity.class, Player.class})
public abstract class MistaAcceptedDamageMixin {
    // Match LivingHurtEvent: accepted damage, before armor wear/reduction.
    @WrapOperation(method = "actuallyHurt", at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F",
            ordinal = 0,
            remap = false))
    private float nextalbum$protectAcceptedDamage(
            DamageContainer container, Operation<Float> original) {
        return NextAlbumGameEvents.reduceProjectileDamage(
                (LivingEntity) (Object) this, container, original.call(container));
    }
}
