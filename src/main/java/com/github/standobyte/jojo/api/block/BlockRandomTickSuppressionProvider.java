package com.github.standobyte.jojo.api.block;

@FunctionalInterface
public interface BlockRandomTickSuppressionProvider {
	boolean shouldSuppress(BlockRandomTickSuppressionQuery query);
}
