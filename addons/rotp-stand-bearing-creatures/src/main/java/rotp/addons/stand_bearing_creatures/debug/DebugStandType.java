package rotp.addons.stand_bearing_creatures.debug;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.type.NoSummonStandType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.Lazy;
import rotp.addons.stand_bearing_creatures.util.RainbowTextUtil;

public final class DebugStandType extends NoSummonStandType {
    public DebugStandType(
            StandStats stats,
            MovesetBuilder moveset,
            ResourceLocation id) {
        super(stats, moveset, id);
        name = Lazy.of(() ->
                RainbowTextUtil.rainbow("Debug Stand", true));
    }

    @Override
    public Component getName(Power<?> power) {
        return RainbowTextUtil.rainbow("Debug Stand", true);
    }
}
