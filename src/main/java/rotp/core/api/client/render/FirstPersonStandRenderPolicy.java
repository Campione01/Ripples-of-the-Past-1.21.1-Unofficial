package rotp.core.api.client.render;

@FunctionalInterface
public interface FirstPersonStandRenderPolicy {
	boolean shouldSuppress(FirstPersonStandRenderQuery query);
}
