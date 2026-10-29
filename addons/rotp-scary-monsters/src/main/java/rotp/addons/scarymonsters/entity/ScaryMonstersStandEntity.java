package rotp.addons.scarymonsters.entity;

import rotp.core.api.RotpAddonApi;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ScaryMonstersStandEntity extends StandEntity {
    public ScaryMonstersStandEntity(
            EntityType<? extends ScaryMonstersStandEntity> type,
            Level level) {
        super(type, level);
        // 1.16 GeoExampleStandEntity.offsetDefault (0, 0, 0); its y was also
        // the y of every task offset that set none, as the block's.
        Y_OFFSET = 0.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
        // 1.16 GeoExampleStandEntity :60 doOffsetLerp false: the Stand stands at
        // a new offset at once instead of gliding there over four ticks.
        if (RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_OFFSET_NO_LERP_V1)) {
            offsetFromUser.noLerp();
        }
    }

    // 1.16 GeoExampleStandEntity.actuallyHurt (func_70665_d) replaced the
    // core's without its BLOCK_STAND_ENTITY line, so the idle Stand never
    // auto-guarded; the held example_stand_block still blocks.
    @Override
    protected boolean autoGuardsOnHit() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            addEffect(new MobEffectInstance(
                    ModStatusEffects.FULL_INVISIBILITY,
                    4,
                    2,
                    false,
                    false,
                    false));
        }
    }
}
