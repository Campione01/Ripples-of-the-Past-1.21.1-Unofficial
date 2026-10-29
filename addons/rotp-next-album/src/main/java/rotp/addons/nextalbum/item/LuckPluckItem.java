package rotp.addons.nextalbum.item;

import java.util.List;
import java.util.function.Consumer;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.render.LuckPluckRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class LuckPluckItem extends SwordItem implements GeoItem {
    public static final ResourceLocation LEGACY_REACH_MODIFIER_ID =
            NextAlbumIds.id(
                    "9b14156e-7ba3-446a-b18b-4c81a7d47a8b");
    private static final RawAnimation IDLE =
            RawAnimation.begin()
                    .thenLoop("animation.luck_pluck.idle");

    private final AnimatableInstanceCache cache =
            GeckoLibUtil.createInstanceCache(this);

    public LuckPluckItem(Item.Properties ignoredLegacyRarity) {
        super(
                Tiers.DIAMOND,
                new Item.Properties()
                        .stacksTo(1)
                        .durability(Tiers.DIAMOND.getUses())
                        .attributes(createLegacyAttributes()));
    }

    public static ItemAttributeModifiers createLegacyAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(
                                LEGACY_REACH_MODIFIER_ID,
                                0.5D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.BLOCK_INTERACTION_RANGE,
                        new AttributeModifier(
                                LEGACY_REACH_MODIFIER_ID,
                                0.5D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                7.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                -2.4D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    public boolean openFingers() {
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.nextalbum.luck_pluck.tooltips")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_nextalbum.luck_pluck.reference_quote")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void registerControllers(
            AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "controller",
                0,
                state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(
            Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<LuckPluckItem> renderer;

            @Override
            public GeoItemRenderer<LuckPluckItem>
                    getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new LuckPluckRenderer();
                }
                return renderer;
            }
        });
    }
}
