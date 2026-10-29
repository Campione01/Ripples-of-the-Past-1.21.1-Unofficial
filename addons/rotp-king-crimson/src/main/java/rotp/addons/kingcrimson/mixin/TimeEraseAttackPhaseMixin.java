package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

@Mixin(value = EntityActionInstance.class, remap = false)
public abstract class TimeEraseAttackPhaseMixin {
    @Inject(method = "_onTick", at = @At("HEAD"))
    private void kingCrimson$endEraseBeforeStrike(CallbackInfo ci) {
        EntityActionInstance action = (EntityActionInstance) (Object) this;
        if (action.getPhase() == ActionPhase.PERFORM
                && (action.ability instanceof StandEntityPunchAbility
                        || action.ability instanceof StandEntityBarrageAbility)
                && action.getPowerUser() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService.terminateOnInteraction(player);
        }
    }
}
