package rotp.addons.achtungbaby.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class AchtungBabyStandType extends EntityStandType {
    public AchtungBabyStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        standLeapEnabled = false;
    }

    // No summon() override: the void sound is the Stand's own summon and unsummon sound (AchtungBabyAddon.createStand),
    // as in 1.16; summon plays it as rotp_ab:void_summon, since a summon sound equal to the unsummon sound is cut
    // by the core client after one tick. Played from here through user.playSound, it reached everyone but the user.

    // No forceUnsummon() override: the core calls it at the end of every ordinary retraction and on each tick of a
    // spectator (which Ghost mode makes of the player), not only when the Stand is lost. Neither mode ends there, as
    // in 1.16; AchtungBabyService.tick ends both for a player who no longer has Achtung Baby.

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        standLeapEnabled = false;
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        standLeapEnabled = false;
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }
}
