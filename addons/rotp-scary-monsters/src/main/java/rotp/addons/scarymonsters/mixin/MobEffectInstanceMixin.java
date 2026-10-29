package rotp.addons.scarymonsters.mixin;

import java.util.UUID;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.scarymonsters.state.ScaryMonstersEffectIdentityAccess;
import rotp.addons.scarymonsters.state.ScaryMonstersOwnedEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin implements ScaryMonstersEffectIdentityAccess {
    @Unique
    private UUID rotp_sm$effectIdentity;

    @Override
    public UUID rotp_sm$getEffectIdentity() {
        return rotp_sm$effectIdentity;
    }

    @Override
    public void rotp_sm$setEffectIdentity(UUID identity) {
        rotp_sm$effectIdentity = identity;
    }

    @Inject(method = "setDetailsFrom", at = @At("TAIL"))
    private void rotp_sm$copyIdentity(MobEffectInstance other, CallbackInfo ci) {
        rotp_sm$effectIdentity = ScaryMonstersOwnedEffects.identity(other);
    }

    @Inject(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;duration:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void rotp_sm$copyIncomingIdentity(MobEffectInstance other,
            CallbackInfoReturnable<Boolean> cir) {
        rotp_sm$effectIdentity = ScaryMonstersOwnedEffects.identity(other);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void rotp_sm$saveIdentities(CallbackInfoReturnable<Tag> cir) {
        if (cir.getReturnValue() instanceof CompoundTag tag) {
            ScaryMonstersOwnedEffects.writeIdentities((MobEffectInstance) (Object) this, tag);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void rotp_sm$loadIdentities(CompoundTag tag,
            CallbackInfoReturnable<MobEffectInstance> cir) {
        if (cir.getReturnValue() != null) {
            ScaryMonstersOwnedEffects.readIdentities(cir.getReturnValue(), tag);
        }
    }
}
