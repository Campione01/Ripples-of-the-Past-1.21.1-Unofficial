package rotp.addons.sexpistols.content.accessory;

import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

public final class MistaHatGameEvents {
    public static final float PROJECTILE_DAMAGE_MULTIPLIER = 0.85F;

    private MistaHatGameEvents() {}

    public static float reduceProjectileDamage(
            LivingEntity entity, DamageContainer container, float amount) {
        if (amount <= 0.0F
                || entity.level().isClientSide()) {
            return amount;
        }
        DamageSource source = container.getSource();
        boolean revolverBullet =
                source.getDirectEntity() instanceof RevolverBulletEntity
                        || "revolver_bullet".equals(source.getMsgId());
        if (!source.is(DamageTypeTags.IS_PROJECTILE)
                && !revolverBullet) {
            return amount;
        }
        ItemStack head = entity.getItemBySlot(
                EquipmentSlot.HEAD);
        if (!head.is(SexPistolsItems.MISTA_SUIT_HAT.get())) {
            return amount;
        }
        float reduced = amount * PROJECTILE_DAMAGE_MULTIPLIER;
        container.setNewDamage(reduced);
        return reduced;
    }
}
