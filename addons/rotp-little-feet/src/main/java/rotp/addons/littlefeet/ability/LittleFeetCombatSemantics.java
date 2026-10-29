package rotp.addons.littlefeet.ability;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.MathUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;

public final class LittleFeetCombatSemantics {
    public static final float ARMOR_PIERCING_FACTOR = 0.15F;
    public static final float BLOCKING_CHANCE_OFFSET = 0.25F;
    public static final int SHIELD_COOLDOWN_TICKS = 100;

    private LittleFeetCombatSemantics() {}

    public static float legacyDamage(
            StandEntity stand, float damageFactor) {
        return (float) stand.getAttackDamage() * damageFactor;
    }

    public static float armorPiercing(StandEntity stand) {
        return Mth.clamp(
                (float) stand.getAttackDamage()
                        * ARMOR_PIERCING_FACTOR,
                0.0F,
                1.0F);
    }

    public static float blockingDisableChance(
            StandEntity stand, LivingEntity target) {
        double attackRange = stand.getAttributeValue(
                Attributes.ENTITY_INTERACTION_RANGE);
        double proximity = attackRange > 0.0D
                ? 1.0D - MathUtil.getAABBDistance(
                        stand.getBoundingBox(),
                        target.getBoundingBox()) / attackRange
                : 0.0D;
        return Mth.clamp(
                (float) proximity - BLOCKING_CHANCE_OFFSET,
                0.0F,
                1.0F);
    }

    public static int blockingBreakTicks(double durability) {
        return Math.max(240 - (int) (durability * 10.0D), 1);
    }

    public static void tryBreakStandGuard(
            StandEntity attacker, LivingEntity target) {
        if (target instanceof StandEntity targetStand
                && attacker.getRandom().nextFloat()
                        < blockingDisableChance(attacker, target)) {
            targetStand.breakStandBlocking(
                    blockingBreakTicks(targetStand.getDurability()));
        }
    }

    public static void tryDisableShield(
            StandEntity attacker, LivingEntity target) {
        if (!(target instanceof Player player)
                || target.getUseItem().getUseAnimation()
                        != UseAnim.BLOCK
                || attacker.getRandom().nextFloat()
                        >= blockingDisableChance(attacker, target)) {
            return;
        }
        player.getCooldowns().addCooldown(
                target.getUseItem().getItem(),
                SHIELD_COOLDOWN_TICKS);
        target.stopUsingItem();
        target.level().broadcastEntityEvent(target, (byte) 30);
    }
}
