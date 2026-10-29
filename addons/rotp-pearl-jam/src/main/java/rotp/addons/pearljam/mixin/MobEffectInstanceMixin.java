package rotp.addons.pearljam.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.pearljam.food.PearlJamHealthBoostCompat;
import rotp.addons.pearljam.food.PearlJamWeakHealthBoostAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin implements PearlJamWeakHealthBoostAccess {
    @Shadow
    private int amplifier;

    @Override
    public int rotp_pj$getRawAmplifier() {
        return amplifier;
    }

    @Unique
    private boolean rotp_pj$weakHealthBoost;

    @Override
    public boolean rotp_pj$isWeakHealthBoost() {
        return rotp_pj$weakHealthBoost;
    }

    @Override
    public void rotp_pj$setWeakHealthBoost(boolean weak) {
        rotp_pj$weakHealthBoost = weak;
    }

    // Only comparisons use -2; the assignment between them keeps the wire value 0.
    @Redirect(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;amplifier:I",
            opcode = Opcodes.GETFIELD, ordinal = 0))
    private int rotp_pj$incomingAmplifier(MobEffectInstance instance) {
        return PearlJamHealthBoostCompat.comparisonAmplifier(instance);
    }

    @Redirect(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;amplifier:I",
            opcode = Opcodes.GETFIELD, ordinal = 1))
    private int rotp_pj$currentAmplifier(MobEffectInstance instance) {
        return PearlJamHealthBoostCompat.comparisonAmplifier(instance);
    }

    @Redirect(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;amplifier:I",
            opcode = Opcodes.GETFIELD, ordinal = 3))
    private int rotp_pj$incomingEqualAmplifier(MobEffectInstance instance) {
        return PearlJamHealthBoostCompat.comparisonAmplifier(instance);
    }

    @Redirect(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;amplifier:I",
            opcode = Opcodes.GETFIELD, ordinal = 4))
    private int rotp_pj$currentEqualAmplifier(MobEffectInstance instance) {
        return PearlJamHealthBoostCompat.comparisonAmplifier(instance);
    }

    @Inject(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;amplifier:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void rotp_pj$copyReplacementMarker(MobEffectInstance other,
            CallbackInfoReturnable<Boolean> cir) {
        rotp_pj$weakHealthBoost = PearlJamHealthBoostCompat.isWeak(other);
    }

    @Inject(method = "setDetailsFrom", at = @At("TAIL"))
    private void rotp_pj$copyDetailsMarker(MobEffectInstance other, CallbackInfo ci) {
        rotp_pj$weakHealthBoost = PearlJamHealthBoostCompat.isWeak(other);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void rotp_pj$applyPromotedModifiers(LivingEntity entity,
            Runnable onExpiration, CallbackInfoReturnable<Boolean> cir) {
        if (rotp_pj$weakHealthBoost && cir.getReturnValue()) {
            PearlJamHealthBoostCompat.applyActiveModifiers(entity);
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void rotp_pj$saveMarkers(CallbackInfoReturnable<Tag> cir) {
        if (cir.getReturnValue() instanceof CompoundTag tag) {
            PearlJamHealthBoostCompat.writeMarkers((MobEffectInstance) (Object) this, tag);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void rotp_pj$loadMarkers(CompoundTag tag,
            CallbackInfoReturnable<MobEffectInstance> cir) {
        if (cir.getReturnValue() != null) {
            PearlJamHealthBoostCompat.readMarkers(cir.getReturnValue(), tag);
        }
    }
}
