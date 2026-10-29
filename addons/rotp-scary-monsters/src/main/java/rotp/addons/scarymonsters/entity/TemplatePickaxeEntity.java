package rotp.addons.scarymonsters.entity;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class TemplatePickaxeEntity extends ModdedProjectileEntity
        implements ItemSupplier {
    public TemplatePickaxeEntity(
            EntityType<? extends TemplatePickaxeEntity> type,
            Level level) {
        super(type, level);
    }

    public TemplatePickaxeEntity(
            EntityType<? extends TemplatePickaxeEntity> type,
            LivingEntity owner,
            Level level) {
        super(type, owner, level);
    }

    @Override protected float getBaseDamage() { return 6.0F; }
    @Override protected float getMaxHardnessBreakable() { return 100.0F; }
    @Override public boolean standDamage() { return true; }
    @Override public int ticksLifespan() { return Integer.MAX_VALUE; }
    @Override public ItemStack getItem() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }
}
