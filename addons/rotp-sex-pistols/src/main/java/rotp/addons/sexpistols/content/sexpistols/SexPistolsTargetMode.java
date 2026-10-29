package rotp.addons.sexpistols.content.sexpistols;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

public enum SexPistolsTargetMode {
    PLAYERS,
    HOSTILE,
    FRIENDLY,
    ALL;

    public static SexPistolsTargetMode byId(int id) {
        SexPistolsTargetMode[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public SexPistolsTargetMode cycle(boolean backwards) {
        return byId(ordinal() + (backwards ? -1 : 1));
    }

    public boolean matches(
            LivingEntity owner, LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == owner
                || target instanceof StandEntity
                || target instanceof SexPistolsEntity
                || target instanceof Player player
                        && player.isSpectator()) {
            return false;
        }
        return switch (this) {
            case PLAYERS -> target instanceof Player player
                    && !owner.isAlliedTo(player)
                    && (!(owner instanceof Player ownerPlayer)
                            || ownerPlayer.canHarmPlayer(player));
            case HOSTILE -> target instanceof Monster;
            case FRIENDLY -> !(target instanceof Player)
                    && !(target instanceof Monster);
            case ALL -> !(target instanceof Player player)
                    || !(owner instanceof Player ownerPlayer)
                    || ownerPlayer.canHarmPlayer(player);
        };
    }
}
