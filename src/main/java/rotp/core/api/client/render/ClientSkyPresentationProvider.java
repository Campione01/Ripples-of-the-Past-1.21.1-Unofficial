package rotp.core.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface ClientSkyPresentationProvider {
	@Nullable
	ClientSkyPresentation presentation(
			ClientSkyPresentationQuery query);
}
