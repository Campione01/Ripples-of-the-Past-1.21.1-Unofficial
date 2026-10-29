package rotp.addons.shadowtheworld.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

abstract class AbstractDioNoteItem extends Item {
    private final boolean complete;

    AbstractDioNoteItem(
            Properties properties, boolean complete) {
        super(properties);
        this.complete = complete;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int itemSlot,
            boolean isSelected) {
        super.inventoryTick(
                stack, level, entity, itemSlot, isSelected);
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!level.isClientSide()) {
            addHiddenEffect(
                    player,
                    MobEffects.DIG_SPEED,
                    complete ? 1 : 0,
                    40);
            addHiddenEffect(
                    player,
                    MobEffects.MOVEMENT_SPEED,
                    complete ? 2 : 1,
                    40);
            addHiddenEffect(
                    player,
                    MobEffects.DAMAGE_BOOST,
                    complete ? 1 : 0,
                    40);
            addHiddenEffect(
                    player,
                    MobEffects.DAMAGE_RESISTANCE,
                    complete ? 2 : 0,
                    40);
            if (complete) {
                addHiddenEffect(
                        player,
                        MobEffects.NIGHT_VISION,
                        0,
                        250);
                addHiddenEffect(
                        player,
                        MobEffects.REGENERATION,
                        0,
                        40);
                addHiddenEffect(
                        player,
                        MobEffects.FIRE_RESISTANCE,
                        0,
                        40);
            }
            return;
        }
        if ((player.getMainHandItem() == stack
                || player.getOffhandItem() == stack)
                && player.getRandom().nextInt(4) == 0) {
            Vec3 center = player.position().add(
                    0.0D, player.getBbHeight() * 0.5D, 0.0D);
            for (int i = 0; i < 5; i++) {
                Vec3 start = center.add(
                        randomSigned(player, 5.0D),
                        randomSigned(player, 5.0D),
                        randomSigned(player, 5.0D));
                Vec3 velocity =
                        center.subtract(start).scale(0.8D);
                level.addParticle(
                        ParticleTypes.PORTAL,
                        start.x,
                        start.y,
                        start.z,
                        velocity.x,
                        velocity.y,
                        velocity.z);
            }
        }
    }

    private static double randomSigned(
            Player player, double radius) {
        return (player.getRandom().nextDouble() * 2.0D - 1.0D)
                * radius;
    }

    private static void addHiddenEffect(
            Player player,
            net.minecraft.core.Holder<MobEffect> effect,
            int amplifier,
            int duration) {
        player.addEffect(new MobEffectInstance(
                effect,
                duration,
                amplifier,
                false,
                false,
                false));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        complete
                                ? "item.rotp_shadowtheworld"
                                        + ".dio_note.desc"
                                : "item.rotp_shadowtheworld"
                                        + ".dio_note_page.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
