package com.github.standobyte.jojo.api.block;

@FunctionalInterface
public interface BlockSignalSuppressor {
	boolean suppress(BlockSignalQuery query);
}
