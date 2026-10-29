package rotp.addons.ultimate_lifeform.power;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import com.illusivesoulworks.caelus.api.CaelusApi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

public final class UltimateLifeformPowerType
        extends PlayerPowerType<UltimateLifeformData> {
    public static final int COLOR = 0x756B43;
    public static final ResourceLocation FLIGHT_MODIFIER_ID =
            UltimateLifeformAddon.id("ultimate_flight");
    private static final AttributeModifier FLIGHT_MODIFIER =
            new AttributeModifier(
                    FLIGHT_MODIFIER_ID,
                    1.0D,
                    AttributeModifier.Operation.ADD_VALUE);

    public UltimateLifeformPowerType(
            ResourceLocation id, MovesetBuilder moveset) {
        super(id, moveset);
    }

    @Override
    public UltimateLifeformData newDataInstance() {
        return new UltimateLifeformData();
    }

    // 1.16 keepOnDeath compared two registry names by reference (never
    // equal), so the dimension never mattered
    @Override
    public boolean keepOnDeath(PlayerPower power) {
        return !power.getUser().isInLava()
                && power.getUser().getY() <= 256.0D;
    }

    @Override
    public boolean isLeapUnlocked(PlayerPower power) {
        return true;
    }

    @Override
    public float getLeapStrength(PlayerPower power) {
        return 2.0F;
    }

    @Override
    public int getLeapCooldownPeriod(PlayerPower power) {
        return 20;
    }

    @Override
    public float getLeapEnergyCost(PlayerPower power) {
        return 1200.0F;
    }

    // 1.16 NonStandPower.canLeap / onLeap went through hasEnergy and
    // consumeEnergy, which passed and took nothing for a creative user.
    @Override
    public boolean hasLeapEnergy(
            PlayerPower power, float energyCost) {
        return power.getCurTypeData(
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .map(data -> isCreative(power)
                        || data.hasEnergy(energyCost))
                .orElse(false);
    }

    @Override
    public boolean consumeLeapEnergy(
            PlayerPower power, float energyCost) {
        return power.getCurTypeData(
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .map(data -> isCreative(power)
                        || data.consumeEnergy(
                                power.getUser(), energyCost))
                .orElse(false);
    }

    private static boolean isCreative(PlayerPower power) {
        return power.getUser() instanceof Player player
                && player.getAbilities().instabuild;
    }

    @Override
    public void onLeap(PlayerPower power) {
        if (!(power.getUser() instanceof Player player)) {
            return;
        }
        UltimateLifeformData data = power.getCurTypeData(
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        // 1.16 onLeap asked for the armor effects, which milk or fire could
        // take off while the armor type stayed
        if (data == null
                || player.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                || player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)) {
            return;
        }
        enableFlight(player);
        player.startFallFlying();
        data.setFlying(player, true);
    }

    @Override
    public float getStandMaxStaminaFactor(
            PlayerPower power, StandPower standPower) {
        return 1.0F;
    }

    @Override
    public float getStandStaminaRegenFactor(
            PlayerPower power, StandPower standPower) {
        return 1.0F;
    }

    public static void enableFlight(Player player) {
        AttributeInstance instance = player.getAttribute(
                CaelusApi.getInstance()
                        .getFallFlyingAttribute());
        if (instance != null
                && !instance.hasModifier(FLIGHT_MODIFIER_ID)) {
            instance.addTransientModifier(FLIGHT_MODIFIER);
        }
    }

    public static void disableFlight(Player player) {
        AttributeInstance instance = player.getAttribute(
                CaelusApi.getInstance()
                        .getFallFlyingAttribute());
        if (instance != null) {
            instance.removeModifier(FLIGHT_MODIFIER_ID);
        }
    }
}
