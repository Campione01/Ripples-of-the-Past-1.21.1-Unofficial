package rotp.core.api.control;

@FunctionalInterface
public interface PlayerOperationPolicy {
	PlayerOperationDecision decide(PlayerOperationQuery query);
}
