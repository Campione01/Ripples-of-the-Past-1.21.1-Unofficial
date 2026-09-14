package rotp.core.api.stand;

@FunctionalInterface
public interface StandDamageAuthorizer {
	boolean canHurtStand(StandDamageQuery query);
}
