package rotp.addons.nextalbum;

import rotp.addons.nextalbum.effect.CollapsePoseState;
import rotp.addons.nextalbum.item.MistaSuitArmorItem;
import rotp.addons.nextalbum.state.NecklaceBeamService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(
        modid = NextAlbumIds.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class NextAlbumGameEvents {
    // Sex Pistols' hat, matched by id only (no class link).
    static final ResourceLocation MISTA_HAT_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_sex_pistols", "mista_suit_hat");
    // MistaHatGameEvents.PROJECTILE_DAMAGE_MULTIPLIER in rotp_sex_pistols.
    static final float MISTA_HAT_MULTIPLIER = 0.85F;

    private NextAlbumGameEvents() {}

    public static float reduceProjectileDamage(
            LivingEntity entity, DamageContainer container, float amount) {
        DamageSource source = container.getSource();
        if (amount <= 0.0F
                || entity.level().isClientSide()
                || (!source.is(DamageTypeTags.IS_PROJECTILE)
                        && !"revolver_bullet".equals(
                                source.getMsgId()))) {
            return amount;
        }
        int pieces = 0;
        for (ItemStack stack : entity.getArmorSlots()) {
            if (stack.getItem()
                    instanceof MistaSuitArmorItem) {
                pieces++;
            }
        }
        // 1.16 counted the hat as a fourth suit piece (1 - 0.15n, 0.40
        // for all four). The hat now lives in rotp_sex_pistols, whose own
        // handler multiplies by 0.85, so divide that out. Hat alone: 0.85.
        if (pieces > 0) {
            boolean hat = MISTA_HAT_ID.equals(
                    BuiltInRegistries.ITEM.getKey(
                            entity.getItemBySlot(
                                    EquipmentSlot.HEAD).getItem()));
            if (hat) {
                pieces++;
            }
            float multiplier = 1.0F - pieces * 0.15F;
            if (hat) {
                multiplier /= MISTA_HAT_MULTIPLIER;
            }
            float reduced = amount * multiplier;
            container.setNewDamage(reduced);
            return reduced;
        }
        return amount;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NecklaceBeamService.tick(player);
        }
    }

    // 1.16 AjaStoneNecklaceItem.handleRightClick on the server's use
    // events: a Sneak use starts the charge (or the Keeper's beam), a use
    // while charging keeps it, and either one is taken from vanilla.
    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
                && NecklaceBeamService.keepAlive(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && NecklaceBeamService.keepAlive(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCollapseRemoved(
            MobEffectEvent.Remove event) {
        if (event.getEffect().is(NextAlbumAddon.COLLAPSE)) {
            CollapsePoseState.releaseIfOwned(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCollapseExpired(
            MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null
                && instance.getEffect()
                        .is(NextAlbumAddon.COLLAPSE)) {
            CollapsePoseState.releaseIfOwned(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NecklaceBeamService.cancel(player);
            CollapsePoseState.releaseIfOwned(player);
        }
    }
}
