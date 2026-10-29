package rotp.addons.ultimate_lifeform.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.network.UltimateWallClimbInputPayload;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 1.16 PlayerEntityMixin on the local player: the client moves itself along the wall.
@Mixin(Player.class)
public abstract class LocalPlayerWallClimbTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void rotp_ul$localWallClimbTravel(
            Vec3 travelVector, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer player)) {
            return;
        }
        UltimateLifeformData data = PlayerPower.getPowerData(
                        player, UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        if (data == null || !data.isWallClimbing()) {
            return;
        }
        boolean jump = player.input.jumping;
        if (jump) {
            // The climb ends locally on this tick, so the end-of-tick input
            // packet would no longer be sent.
            PacketDistributor.sendToServer(
                    new UltimateWallClimbInputPayload(
                            (float) travelVector.x,
                            (float) travelVector.z,
                            true));
        }
        if (data.travelWallClimb(player, travelVector, jump)) {
            ci.cancel();
        }
    }
}
