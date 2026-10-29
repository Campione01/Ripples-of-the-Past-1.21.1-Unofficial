package rotp.addons.speedwagon.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;

public class HenchmanEntity
        extends AbstractHenchmanEntity {
    public HenchmanEntity(
            EntityType<? extends HenchmanEntity> type,
            Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ATTACK_SPEED, 0.15D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(NeoForgeMod.SWIM_SPEED, 2.0D);
    }
}
