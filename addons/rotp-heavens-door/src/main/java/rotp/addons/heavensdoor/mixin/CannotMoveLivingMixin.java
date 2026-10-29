package rotp.addons.heavensdoor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import rotp.addons.heavensdoor.state.CannotMoveRules;

@Mixin(LivingEntity.class)
public abstract class CannotMoveLivingMixin {
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z", opcode = Opcodes.GETFIELD))
    private boolean heavensDoor$noJumpInput(boolean jumping) {
        return jumping && !CannotMoveRules.blocksSelf((LivingEntity) (Object) this);
    }

    @ModifyVariable(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
    private Vec3 heavensDoor$noVoluntaryTravel(Vec3 input) {
        return CannotMoveRules.blocksTravel((LivingEntity) (Object) this) ? Vec3.ZERO : input;
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void heavensDoor$noJump(CallbackInfo ci) {
        if (CannotMoveRules.blocksSelf((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    // Only the side that moves the mount skips its ridden tick (steering, the rider's jump); the other side
    // keeps the vanilla branch with its block contacts.
    @Inject(method = "travelRidden", at = @At("HEAD"), cancellable = true)
    private void heavensDoor$noMountSteering(Player rider, Vec3 input, CallbackInfo ci) {
        LivingEntity mount = (LivingEntity) (Object) this;
        if (mount.isControlledByLocalInstance()
                && (CannotMoveRules.blocksSelf(rider) || CannotMoveRules.blocksSelf(mount))) {
            mount.travel(Vec3.ZERO);
            ci.cancel();
        }
    }
}
