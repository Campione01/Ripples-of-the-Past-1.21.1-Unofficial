package rotp.addons.kingcrimson.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.core.subsystems.target.HitResultUtil;

@Mixin(value = HitResultUtil.class, remap = false)
public abstract class TimeEraseTargetMixin {
    @ModifyVariable(method = "clip", at = @At("HEAD"), argsOnly = true)
    private static Predicate<Entity> kingCrimson$ignoreOwnProjection(
            Predicate<Entity> filter, Vec3 start, Vec3 direction, double blockRange, double entityRange,
            Level level, Predicate<Entity> original, Entity aiming, double precision) {
        return target -> (filter == null || filter.test(target))
                && !KingCrimsonTimeEraseDecoyEntity.isOwnProjection(target, aiming);
    }
}
