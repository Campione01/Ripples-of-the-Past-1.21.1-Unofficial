package rotp.addons.heavensdoor.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * 1.16 KYSEffect: every 40 ticks the target strikes itself with its main
 * hand. Any living entity can carry it, but only players, mobs (see
 * HeavensDoorEvents.addMobAttackDamage) and Stands have ATTACK_DAMAGE, so
 * the damage adds that attribute only when the entity has one (1.16
 * calculateDamage null-checked it the same way).
 */
public final class KysEffect extends StatusEffectModified {
    /**
     * 1.16 {@code new EntityDamageSource("self.harm", entity)
     * .bypassArmor().setMagic()}; the tags live in the data pack.
     */
    public static final ResourceKey<DamageType> SELF_HARM =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    HeavensDoorAddon.id("self_harm"));

    public KysEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFF0000);
        setUncurable();
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        Level level = entity.level();
        if (level.isClientSide() || !entity.isAlive()) {
            return true;
        }
        boolean hasLighter = hasLighterInHand(entity);
        if (entity instanceof Creeper creeper) {
            creeper.ignite();
            return true;
        }
        entity.swing(InteractionHand.MAIN_HAND, true);
        if (entity instanceof Player player) {
            player.resetAttackStrengthTicker();
        }
        else {
            level.broadcastEntityEvent(entity, (byte) 4);
        }
        entity.playSound(
                SoundEvents.PLAYER_ATTACK_STRONG,
                0.8F,
                0.8F + entity.getRandom().nextFloat() * 0.4F);
        DamageSource selfHarm =
                DamageUtil.make(level, SELF_HARM, entity);
        entity.hurt(
                selfHarm,
                calculateDamage(entity, selfHarm, amplifier));
        if (hasLighter) {
            level.playSound(
                    null,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE,
                    SoundSource.PLAYERS,
                    0.8F,
                    0.9F + level.getRandom().nextFloat() * 0.2F);
        }
        return true;
    }

    private static boolean hasLighterInHand(LivingEntity entity) {
        return entity.getMainHandItem().is(Items.FLINT_AND_STEEL)
                || entity.getOffhandItem().is(Items.FLINT_AND_STEEL);
    }

    /**
     * 1.16 calculateDamage: 1, plus the main-hand item's attack damage
     * modifiers, a tool's own attack damage, the enchantment bonus against
     * the wielder itself, plus ATTACK_DAMAGE only if the entity has it;
     * the sum is multiplied by (amplifier + 1).
     */
    private static float calculateDamage(
            LivingEntity entity,
            DamageSource selfHarm,
            int amplifier) {
        float damage = 1.0F;
        ItemStack mainHandItem = entity.getMainHandItem();
        if (!mainHandItem.isEmpty()) {
            for (ItemAttributeModifiers.Entry entry
                    : mainHandItem.getAttributeModifiers().modifiers()) {
                if (entry.slot().test(EquipmentSlot.MAINHAND)
                        && Attributes.ATTACK_DAMAGE.equals(
                                entry.attribute())) {
                    damage = (float) (damage
                            + entry.modifier().amount());
                }
            }
            if (mainHandItem.getItem() instanceof DiggerItem tool) {
                damage += toolAttackDamage(tool);
            }
            if (entity.level() instanceof ServerLevel serverLevel) {
                damage += EnchantmentHelper.modifyDamage(
                        serverLevel,
                        mainHandItem,
                        entity,
                        selfHarm,
                        0.0F);
            }
        }
        AttributeInstance attackDamage =
                entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            damage = (float) (damage + attackDamage.getValue());
        }
        return damage * (float) (amplifier + 1);
    }

    /**
     * 1.16 ToolItem.getAttackDamage(), which 1.21 keeps only as the item's
     * own base attack damage modifier.
     */
    private static float toolAttackDamage(DiggerItem tool) {
        float damage = 0.0F;
        for (ItemAttributeModifiers.Entry entry : tool.components()
                .getOrDefault(
                        DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.EMPTY)
                .modifiers()) {
            if (entry.matches(
                    Attributes.ATTACK_DAMAGE,
                    Item.BASE_ATTACK_DAMAGE_ID)) {
                damage = (float) (damage + entry.modifier().amount());
            }
        }
        return damage;
    }
}
