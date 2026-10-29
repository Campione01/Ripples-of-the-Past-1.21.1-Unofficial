package rotp.addons.speedwagon.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class Henchman2Entity
        extends AbstractHenchmanEntity {
    public Henchman2Entity(
            EntityType<? extends Henchman2Entity> type,
            Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ATTACK_SPEED, 0.3D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(NeoForgeMod.SWIM_SPEED, 2.0D);
    }
}
