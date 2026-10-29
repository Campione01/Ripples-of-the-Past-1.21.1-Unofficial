package rotp.addons.diverdown.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import rotp.addons.diverdown.DiverDownGameEvents;

@Mixin({LivingEntity.class, Player.class})
public abstract class AcceptedBoneDamageMixin {
    // Both vanilla implementations read the accepted amount before armor here.
    @WrapOperation(method = "actuallyHurt", at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F",
            ordinal = 0,
            remap = false))
    private float diverDown$acceptedBoneDamage(
            DamageContainer container, Operation<Float> original) {
        return DiverDownGameEvents.applyAcceptedBoneDamage(
                (LivingEntity) (Object) this, container, original.call(container));
    }
}
