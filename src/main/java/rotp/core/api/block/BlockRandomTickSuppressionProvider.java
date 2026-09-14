package rotp.core.api.block;

@FunctionalInterface
public interface BlockRandomTickSuppressionProvider {
	boolean shouldSuppress(BlockRandomTickSuppressionQuery query);
}
