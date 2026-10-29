package rotp.addons.ger.client;

import java.util.HashSet;
import java.util.Set;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.network.GerUndiePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 1.16 UndieClientState: the dying and pulled-back Undie victims this
 * client knows of. The victim's own client keeps the dying camera but never
 * opens the death screen nor sends a respawn for a loop death.
 */
@EventBusSubscriber(
        modid = GoldExperienceRequiemAddon.MOD_ID,
        value = Dist.CLIENT)
public final class GerUndieClientState {
    private static final int PULL_START_DEATH_TIME = 15;

    private static final Set<Integer> DYING = new HashSet<>();
    private static final Set<Integer> PULLING = new HashSet<>();
    private static boolean restoreImmediateRespawn;

    private GerUndieClientState() {}

    public static void setState(int entityId, byte phase) {
        DYING.add(entityId);
        if (phase == GerUndiePayloads.PULLING) {
            PULLING.add(entityId);
        }
        holdDeathScreenBack();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.getId() == entityId
                && !(minecraft.screen instanceof GerUndieScreen)) {
            minecraft.setScreen(new GerUndieScreen());
        }
    }

    public static void revive(int entityId, float health) {
        DYING.remove(entityId);
        PULLING.remove(entityId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getEntity(entityId)
                        instanceof LivingEntity living) {
            reviveLocally(living, health);
            if (living == minecraft.player
                    && (minecraft.screen instanceof DeathScreen
                            || minecraft.screen
                                    instanceof GerUndieScreen)) {
                minecraft.setScreen(null);
            }
        }
        releaseDeathScreen();
    }

    private static void reviveLocally(
            LivingEntity living, float health) {
        living.setHealth(health);
        living.deathTime = 0;
        living.hurtTime = 0;
        living.hurtDuration = 0;
        living.swingTime = 0;
        living.attackAnim = 0.0F;
        living.invulnerableTime = 0;
        living.fallDistance = 0.0F;
        living.setPose(Pose.STANDING);
        living.clearFire();
    }

    // A client told to respawn at once would send the respawn request the
    // moment its player reads 0 health, so it shows (and drops) the death
    // screen instead while the loop death lasts.
    private static void holdDeathScreenBack() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null
                && DYING.contains(player.getId())
                && !player.shouldShowDeathScreen()) {
            player.setShowDeathScreen(true);
            restoreImmediateRespawn = true;
        }
    }

    private static void releaseDeathScreen() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DYING.contains(player.getId())) {
            return;
        }
        if (restoreImmediateRespawn && player != null) {
            player.setShowDeathScreen(false);
        }
        restoreImmediateRespawn = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!DYING.isEmpty()) {
            holdDeathScreenBack();
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide()
                || !(entity instanceof LivingEntity living)
                || !DYING.contains(living.getId())) {
            return;
        }
        if (PULLING.contains(living.getId())) {
            living.deathTime = Math.max(living.deathTime - 2, 0);
            if (living.deathTime <= 0) {
                reviveLocally(living,
                        Math.min(living.getMaxHealth(), 1.0F));
            }
        }
        else if (living.deathTime >= PULL_START_DEATH_TIME) {
            // Wait here for the server's pullback instead of dying out.
            living.deathTime = PULL_START_DEATH_TIME - 1;
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getNewScreen() instanceof DeathScreen
                && player != null
                && DYING.contains(player.getId())) {
            event.setNewScreen(new GerUndieScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    private static void reset() {
        DYING.clear();
        PULLING.clear();
        releaseDeathScreen();
    }
}
