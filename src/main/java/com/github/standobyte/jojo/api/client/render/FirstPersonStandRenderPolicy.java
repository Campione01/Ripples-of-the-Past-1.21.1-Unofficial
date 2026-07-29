package com.github.standobyte.jojo.api.client.render;

@FunctionalInterface
public interface FirstPersonStandRenderPolicy {
	boolean shouldSuppress(FirstPersonStandRenderQuery query);
}
