package rotp.core.api.client.render;

@FunctionalInterface
public interface ClientSkyRenderer {
	void render(ClientSkyRenderContext context);

	default boolean suppressVanillaClouds() {
		return false;
	}
}
