package rotp.addons.waytoheaven.evolution;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.waytoheaven.WayToHeavenAddon;
import rotp.addons.waytoheaven.item.SinnerBoneItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class HeavenEvolutionService {
    private HeavenEvolutionService() {}

    public static boolean evolveWhitesnake(
            LivingEntity user, ItemStack bone) {
        if (user == null
                || user.level().isClientSide()
                || !isStandUser(
                        user,
                        WayToHeavenAddon.WHITESNAKE_ID)
                || user.getOffhandItem() != bone
                || !SinnerBoneItem.isFull(bone)) {
            return false;
        }

        StandPower power = StandPower.get(user);
        StandInstance replacement =
                StandInstance.fromExistingStandId(
                        WayToHeavenAddon.CMOON_ID);
        StandPowerTransitions.Result result =
                StandPowerTransitions.replace(
                        power,
                        WayToHeavenAddon.WHITESNAKE_ID,
                        replacement);
        if (result.applied()) {
            bone.shrink(1);
        }
        return result.applied();
    }

    public static boolean evolveCMoon(
            LivingEntity user) {
        if (user == null
                || user.level().isClientSide()
                || !isStandUser(
                        user,
                        WayToHeavenAddon.CMOON_ID)) {
            return false;
        }

        StandPower power = StandPower.get(user);
        StandInstance replacement =
                StandInstance.fromExistingStandId(
                        WayToHeavenAddon.MADE_IN_HEAVEN_ID);

        // Core replacement unsummons C-Moon first, releasing its gravity binding.
        StandPowerTransitions.Result result =
                StandPowerTransitions.replace(
                        power,
                        WayToHeavenAddon.CMOON_ID,
                        replacement);
        if (result.applied()) {
            DirectionalGravityApi.reconcileEffectiveDirection(user);
            if (user instanceof Player player) {
                player.getData(
                                WayToHeavenAddon.EVOLUTION_STATE)
                        .grantFallImmunity();
            }
        }
        return result.applied();
    }

    public static boolean isStandUser(
            LivingEntity user,
            net.minecraft.resources.ResourceLocation standId) {
        StandPower power =
                user != null ? StandPower.get(user) : null;
        return power != null
                && power.getStandInstance()
                        .filter(StandInstance::standExists)
                        .map(StandInstance::getStandId)
                        .filter(standId::equals)
                        .isPresent();
    }

    public static boolean hasFallImmunity(Player player) {
        return player != null
                && player.hasData(
                        WayToHeavenAddon.EVOLUTION_STATE)
                && player.getData(
                                WayToHeavenAddon.EVOLUTION_STATE)
                        .hasFallImmunity();
    }

    public static boolean consumeFallImmunity(Player player) {
        return player != null
                && player.hasData(
                        WayToHeavenAddon.EVOLUTION_STATE)
                && player.getData(
                                WayToHeavenAddon.EVOLUTION_STATE)
                        .consumeFallImmunity();
    }

    public static void clearFallImmunity(Player player) {
        if (player != null
                && player.hasData(
                        WayToHeavenAddon.EVOLUTION_STATE)) {
            player.getData(
                            WayToHeavenAddon.EVOLUTION_STATE)
                    .clear();
        }
    }
}
