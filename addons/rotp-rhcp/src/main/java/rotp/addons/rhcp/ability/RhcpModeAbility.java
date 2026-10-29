package rotp.addons.rhcp.ability;

/**
 * An RHCP ability with its {@link RhcpAbility.Mode}: RhcpAbility and the
 * core-based RhcpPunchAbility, RhcpBarrageAbility and RhcpHeavyAbility.
 */
public interface RhcpModeAbility {
    RhcpAbility.Mode rhcpMode();
}
