package rotp.addons.nextalbum.item;

import java.util.List;
import java.util.function.Consumer;

import rotp.addons.nextalbum.client.render.MistaSuitArmorRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class MistaSuitArmorItem
        extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache =
            GeckoLibUtil.createInstanceCache(this);
    private final String modelPath;
    private final String texturePath;
    @Nullable
    private final String slimModelPath;
    @Nullable
    private final String slimTexturePath;
    private final boolean hideBodyOverlay;
    private final boolean hideArmOverlays;
    private final boolean hideLegOverlays;
    private boolean renderingSlimVariant;

    public MistaSuitArmorItem(
            ArmorItem.Type type,
            String modelPath,
            String texturePath,
            @Nullable String slimModelPath,
            @Nullable String slimTexturePath,
            boolean hideBodyOverlay,
            boolean hideArmOverlays,
            boolean hideLegOverlays) {
        super(
                MistaSuitArmorMaterial.MISTA_SUIT,
                type,
                new Item.Properties()
                        .stacksTo(1)
                        .durability(type.getDurability(
                                MistaSuitArmorMaterial
                                        .DURABILITY_MULTIPLIER)));
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.slimModelPath = slimModelPath;
        this.slimTexturePath = slimTexturePath;
        this.hideBodyOverlay = hideBodyOverlay;
        this.hideArmOverlays = hideArmOverlays;
        this.hideLegOverlays = hideLegOverlays;
    }

    public String modelPath() {
        return renderingSlimVariant && slimModelPath != null
                ? slimModelPath
                : modelPath;
    }

    public String texturePath() {
        return renderingSlimVariant && slimTexturePath != null
                ? slimTexturePath
                : texturePath;
    }

    public void setRenderingSlimVariant(boolean slim) {
        renderingSlimVariant = slim && slimModelPath != null;
    }

    public boolean hidesBodyOverlay() {
        return hideBodyOverlay;
    }

    public boolean hidesArmOverlays() {
        return hideArmOverlays;
    }

    public boolean hidesLegOverlays() {
        return hideLegOverlays;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.nextalbum.mista_suit.tooltips")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        stack.getDescriptionId()
                                + ".reference_quote")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void registerControllers(
            AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(
            Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<MistaSuitArmorItem> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?>
                    getGeoArmorRenderer(
                            @Nullable T livingEntity,
                            ItemStack itemStack,
                            @Nullable EquipmentSlot equipmentSlot,
                            @Nullable HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new MistaSuitArmorRenderer();
                }
                return renderer;
            }
        });
    }
}
