package rotp.core.api.soul;

@FunctionalInterface
public interface SoulResolveEligibilityProvider {
	SoulResolveDecision decide(SoulResolveQuery query);
}
