package com.github.standobyte.jojo.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface ClientSkyPresentationProvider {
	@Nullable
	ClientSkyPresentation presentation(
			ClientSkyPresentationQuery query);
}
