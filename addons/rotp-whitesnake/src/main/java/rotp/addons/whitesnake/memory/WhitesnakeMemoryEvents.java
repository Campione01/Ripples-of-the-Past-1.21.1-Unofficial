package rotp.addons.whitesnake.memory;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class WhitesnakeMemoryEvents {
    private WhitesnakeMemoryEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity attacker = resolveAttacker(event.getSource());
        if (attacker != null
                && WhitesnakeAddon.memoryState(attacker).isExtracted()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player replacement = event.getEntity();
        if (replacement.level().isClientSide()) {
            return;
        }
        WhitesnakeAddon.memoryState(replacement).copyFrom(
                WhitesnakeAddon.memoryState(original));
    }

    @Nullable
    private static LivingEntity resolveAttacker(DamageSource source) {
        LivingEntity attacker = normalizeSourceEntity(
                source.getEntity());
        return attacker != null
                ? attacker
                : normalizeSourceEntity(source.getDirectEntity());
    }

    @Nullable
    private static LivingEntity normalizeSourceEntity(Entity entity) {
        if (entity instanceof Projectile projectile) {
            entity = projectile.getOwner();
        }
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        return StandUtil.getStandUser(living);
    }
}
