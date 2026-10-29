package rotp.addons.ultimate_lifeform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.ultimate_lifeform.UltimateLifeformGameEvents;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

// 1.16 GameplayEventHandler.cancelDamage ran on LivingAttackEvent, which Forge posted at the head of PlayerEntity.hurt.
@Mixin(Player.class)
public abstract class PlayerUltimateFireArmorMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void rotp_ul$fireArmorHit(
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        boolean fireArmor = UltimateLifeformGameEvents.cancelFireArmorHit(player, source);
        if (UltimateLifeformGameEvents.cancelHeldMoveHit(player, source) || fireArmor) {
            cir.setReturnValue(false);
        }
    }
}
