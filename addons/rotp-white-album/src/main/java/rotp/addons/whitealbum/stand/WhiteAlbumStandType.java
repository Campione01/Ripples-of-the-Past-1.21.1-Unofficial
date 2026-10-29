package rotp.addons.whitealbum.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.WhiteAlbumSpec;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class WhiteAlbumStandType extends EntityStandType {
    public WhiteAlbumStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> controllerType,
            ResourceLocation id) {
        super(stats, moveset, controllerType, id);
        pinControllerTopology();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        entityType.reset();
        pinControllerTopology();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        pinControllerTopology();
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    // 1.16's guard was a plain held StandAction, never a task of the Stand
    // entity, so stamina kept regenerating while it was held. The port's
    // guard is the core entity action, and entity actions stop regeneration.
    @Override
    protected float getBaseStaminaRegen(StandPower standPower) {
        if (standPower.getSummonedStandEntity()
                        instanceof WhiteAlbumControllerEntity controller) {
            EntityActionInstance action =
                    controller.getCurStandAction();
            if (action != null
                    && action.ability.getAbilityId() != null
                    && WhiteAlbumAddon.GUARD_NAME.equals(
                            action.ability.getAbilityId()
                                    .nameInMoveset())) {
                return WhiteAlbumSpec.GUARD_STAMINA_REGEN;
            }
        }
        return super.getBaseStaminaRegen(standPower);
    }

    private void pinControllerTopology() {
        standControlType(StandControlType.PHENOMENON);
        manualControl(false);
        distanceStrengthDecay(false);
        standLeapEnabled = false;
    }
}
