package rotp.addons.ultimate_lifeform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 1.16 PlayerEntityMixin: the wall climb replaces vanilla travel for the climbing player.
@Mixin(Player.class)
public abstract class PlayerWallClimbTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void rotp_ul$wallClimbTravel(
            Vec3 travelVector, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        UltimateLifeformData data = PlayerPower.getPowerData(
                        player, UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        if (data != null
                && data.travelWallClimb(player, travelVector, false)) {
            ci.cancel();
        }
    }
}
