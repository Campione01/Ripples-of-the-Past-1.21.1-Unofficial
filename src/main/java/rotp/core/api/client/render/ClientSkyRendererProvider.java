package rotp.core.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface ClientSkyRendererProvider {
	@Nullable
	ClientSkyRenderer renderer(ClientSkyRendererQuery query);
}
