package rotp.addons.tournament.util;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import rotp.addons.tournament.AddonMain;
import rotp.addons.tournament.TournamentConfig;
import rotp.addons.tournament.init.AddonBlocks;
import rotp.addons.tournament.item.AddonItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(
        modid = AddonMain.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class GameplayUtilHandler {
    public static final List<String> CANONICAL_RECORD_PLAYERS =
            List.of("August_dr, Pr1tcha, iIIiBlackiIIi");
    public static final Set<String> CORRECTED_PRIVATE_RECORD_PLAYERS =
            Set.of("August_dr", "Pr1tcha", "iIIiBlackiIIi");

    private GameplayUtilHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingEntitySpikesFall(
            LivingFallEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()
                || !living.level()
                        .getBlockState(
                                living.blockPosition().below())
                        .is(AddonBlocks.SPIKES.get())) {
            return;
        }

        PlayerPower power = PlayerPower.get(living);
        if (power != null && isVampirismOrZombie(power)) {
            PlayerPowerType<?> type = power.getPowerType();
            float threshold =
                    2.5F * type.getLeapStrength(power);
            if (event.getDistance() == 0.0F
                    || event.getDistance() > threshold) {
                float distance = event.getDistance() == 0.0F
                        ? 4.0F
                        : Mth.clamp(
                                event.getDistance(),
                                0.0F,
                                15.0F);
                if (distance > 3.0F) {
                    consumeUndeadEnergy(
                            power,
                            living,
                            distance * 2.5F);
                    BleedingEffect.splashBlood(
                            living.level(),
                            living.getBoundingBox().getCenter(),
                            distance,
                            distance,
                            OptionalInt.of((int) distance / 2),
                            living);
                    living.level().playSound(
                            null,
                            living.blockPosition(),
                            ModSoundEvents
                                    .STONE_MASK_ACTIVATION
                                    .get(),
                            SoundSource.BLOCKS,
                            1.0F,
                            1.0F);
                }
                return;
            }
        }
        event.setDamageMultiplier(
                event.getDamageMultiplier() * 2.0F);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void discDrop(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isRecordPlayer(
                        player.getGameProfile().getName())) {
            return;
        }
        event.getDrops().add(new ItemEntity(
                player.level(),
                player.getX(),
                player.getY(),
                player.getZ(),
                new ItemStack(AddonItems.ASSGORE_RECORD.get())));
    }

    private static boolean isVampirismOrZombie(
            PlayerPower power) {
        return power.getPowerType()
                        == ModPlayerPowers.VAMPIRISM.get()
                || power.getPowerType()
                        == ModPlayerPowers.ZOMBIE.get();
    }

    private static void consumeUndeadEnergy(
            PlayerPower power,
            LivingEntity living,
            float amount) {
        power.getCurTypeData(ModPlayerPowers.VAMPIRISM)
                .ifPresent(vampirism ->
                        vampirism.consumeBlood(living, amount));
        power.getCurTypeData(ModPlayerPowers.ZOMBIE)
                .ifPresent(zombie ->
                        zombie.consumeEnergy(living, amount));
    }

    private static boolean isRecordPlayer(String name) {
        return TournamentConfig
                        .ENABLE_CORRECTED_RECORD_PLAYER_LIST
                        .get()
                ? CORRECTED_PRIVATE_RECORD_PLAYERS.contains(name)
                : CANONICAL_RECORD_PLAYERS.contains(name);
    }
}
