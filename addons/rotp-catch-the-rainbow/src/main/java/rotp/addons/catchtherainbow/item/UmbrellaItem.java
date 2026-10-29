package rotp.addons.catchtherainbow.item;

import java.util.List;

import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class UmbrellaItem extends Item {
    public static final int USE_DURATION = 72000;
    public static final double GRAVITY_MULTIPLIER = -0.875D;
    public static final ResourceLocation GRAVITY_MODIFIER =
            CatchTheRainbowAddon.id("umbrella_gravity");

    private static final ItemAttributeModifiers ATTRIBUTES =
            ItemAttributeModifiers.builder()
                    .add(
                            Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(
                                    BASE_ATTACK_DAMAGE_ID,
                                    0.0D,
                                    AttributeModifier.Operation
                                            .ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .add(
                            Attributes.ATTACK_SPEED,
                            new AttributeModifier(
                                    BASE_ATTACK_SPEED_ID,
                                    -3.2D,
                                    AttributeModifier.Operation
                                            .ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .build();

    public UmbrellaItem(Properties properties) {
        super(properties);
    }

    public static void tickPlayer(ServerPlayer player) {
        boolean upright = isHoldingUpright(player);
        AttributeInstance gravity =
                player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            boolean floating = upright
                    && !player.onGround()
                    && !player.isInWaterOrBubble()
                    && !player.isFallFlying()
                    && player.getDeltaMovement().y < 0.0D
                    && !player.hasEffect(
                            net.minecraft.world.effect.MobEffects
                                    .SLOW_FALLING);
            if (floating) {
                gravity.addOrUpdateTransientModifier(
                        new AttributeModifier(
                                GRAVITY_MODIFIER,
                                GRAVITY_MULTIPLIER,
                                AttributeModifier.Operation
                                        .ADD_MULTIPLIED_TOTAL));
                player.resetFallDistance();
            }
            else {
                gravity.removeModifier(GRAVITY_MODIFIER);
            }
        }
        if (!upright) {
            return;
        }
        if (RainExposure.isExposedToRain(player)
                && player.tickCount % 10 == 0) {
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    CatchTheRainbowAddon
                            .UMBRELLA_RAIN_SOUND.get(),
                    player.getSoundSource(),
                    1.0F,
                    1.0F);
        }
        protectVampire(player);
    }

    private static void protectVampire(ServerPlayer player) {
        if (!PlayerPower.getPowerData(
                        player, ModPlayerPowers.VAMPIRISM)
                .isPresent()
                || !player.level().isDay()
                || !player.level().canSeeSky(
                        player.blockPosition())
                || player.level().isRaining()
                || player.level().isThundering()) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                ModStatusEffects.SUN_RESISTANCE,
                10,
                9,
                false,
                false,
                false));
        damageHeldUmbrella(
                player,
                InteractionHand.MAIN_HAND,
                EquipmentSlot.MAINHAND);
        damageHeldUmbrella(
                player,
                InteractionHand.OFF_HAND,
                EquipmentSlot.OFFHAND);
    }

    private static void damageHeldUmbrella(
            ServerPlayer player,
            InteractionHand hand,
            EquipmentSlot slot) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof UmbrellaItem
                && isHoldingUpright(player, hand)) {
            stack.hurtAndBreak(111, player, slot);
        }
    }

    public static boolean isHoldingUpright(
            LivingEntity entity, InteractionHand hand) {
        return entity.getItemInHand(hand).getItem()
                        instanceof UmbrellaItem
                && (!entity.isUsingItem()
                        || entity.getUsedItemHand() != hand);
    }

    public static boolean isHoldingUpright(
            LivingEntity entity) {
        return isHoldingUpright(
                        entity, InteractionHand.MAIN_HAND)
                || isHoldingUpright(
                        entity, InteractionHand.OFF_HAND);
    }

    @Override
    public boolean hurtEnemy(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker) {
        stack.hurtAndBreak(
                1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public int getUseDuration(
            ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(
            ItemStack stack) {
        return ATTRIBUTES;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<net.minecraft.network.chat.Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component
                .translatable(
                        "item.rotp_ctr.umbrella.reference_quote")
                .withStyle(
                        ChatFormatting.GRAY,
                        ChatFormatting.ITALIC));
    }
}
