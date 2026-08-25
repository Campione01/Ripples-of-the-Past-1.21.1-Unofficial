package com.github.standobyte.jojo.client.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.api.client.render.AddonPostEffect;
import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.client.ModClientResources;
import com.github.standobyte.jojo.client.entityrender.stand.aura.StandAuraShaders;
import com.github.standobyte.jojo.client.shader.core.PrivateTargetShaderInstance;
import com.github.standobyte.jojo.client.shader.core.RotpShader;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.util.reflection.ClientReflection;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModShaders implements ResourceManagerReloadListener, AutoCloseable {
	private static ModShaders instance;
	private ModShaders() {}

	public static ModShaders getInstance() {
		return instance;
	}

	public StandTranslucencyFramebuffer standTranslucencyFramebuffer;
	public TimeStopPostShader timeStopPostShader;
	public TimeStopShaderManager timeStopShaderManager;
	public ResolvePostShader resolvePostShader;
	public ResolveShaderManager resolveShaderManager;
	@ApiStatus.Internal public List<RotpShader> _allShaders = new ArrayList<>();
	public ShaderInstance coreStandTranslucent;
	private ShaderInstance privateTargetBlit;
	private ShaderInstance privateTargetEntityMask;

	private void init() {
		Minecraft mc = Minecraft.getInstance();
		SequencedMap<RenderType, ByteBufferBuilder> fixedRenderBuffers = ClientReflection.getFixedBuffers(mc.renderBuffers().bufferSource());

		_allShaders.add(standTranslucencyFramebuffer = new StandTranslucencyFramebuffer(mc));
		timeStopShaderManager = new TimeStopShaderManager();
		resolveShaderManager = new ResolveShaderManager();
		_allShaders.add(timeStopPostShader = new TimeStopPostShader(mc, fixedRenderBuffers, timeStopShaderManager));
		_allShaders.add(resolvePostShader = new ResolvePostShader(mc, fixedRenderBuffers, resolveShaderManager));
	}

	public static void init(RegisterClientReloadListenersEvent event) {
		if (instance == null) {
			instance = new ModShaders();
			instance.init();
			ModClientResources.closeables.add(instance);
			event.registerReloadListener(instance);
			
			NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent renderEvent) -> instance.frameRenderCallback(renderEvent));
		}
	}
	
	// (this was written on 1.21.4 at first)
	// static GraphicsResourceAllocator resourcePoolCache;
	// @SubscribeEvent
	// public static void mcInit2(RenderLevelStageEvent.RegisterStageEvent event) {
	// 	resourcePoolCache = ClientReflection.getResourcePool(Minecraft.getInstance().gameRenderer);
	// }



	@Override
	public void close() {
		EntityMaskPostEffect.closeLoadedTargets();
		AddonPostEffect.closeLoadedChains();
		for (RotpShader shader : _allShaders) {
			shader.close();
		}
		_allShaders.clear();
	}

	@Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
		EntityMaskPostEffect.onResourceManagerReload(resourceManager);
		AddonPostEffect.onResourceManagerReload(resourceManager);
		resolveShaderManager.listManager().reload(resourceManager);
		resolveShaderManager.onResourceReload();
		for (RotpShader shader : _allShaders) {
			shader.loadPostShader(resourceManager);
		}
	}
	
	@SubscribeEvent
	public static void loadCoreShaders(RegisterShadersEvent event) {
		for (RotpShader shader : instance._allShaders) {
			shader.loadCoreShaders(event);
		}
		loadPrivateTargetCoreShader(event, JojoMod.resLoc("stand_translucent"), DefaultVertexFormat.NEW_ENTITY,
				shader -> instance.coreStandTranslucent = shader);
		loadPrivateTargetCoreShader(event, ResourceLocation.withDefaultNamespace("position_tex"),
				DefaultVertexFormat.POSITION_TEX, shader -> instance.privateTargetBlit = shader);
		loadPrivateTargetCoreShader(event, ResourceLocation.withDefaultNamespace("rendertype_entity_solid"),
				DefaultVertexFormat.NEW_ENTITY, shader -> instance.privateTargetEntityMask = shader);
		StandAuraShaders.loadCoreShader(event);
	}

	public static ShaderInstance privateTargetBlit() {
		return instance != null ? instance.privateTargetBlit : null;
	}

	public static ShaderInstance privateTargetEntityMask() {
		return instance != null
				? instance.privateTargetEntityMask
				: null;
	}

	public void resize(int width, int height) {
		EntityMaskPostEffect.resize(width, height);
		AddonPostEffect.resize(width, height);
		for (RotpShader shader : _allShaders) {
			shader.resize(width, height);
		}
	}
	
	private void frameRenderCallback(RenderLevelStageEvent event) {
		for (RotpShader shader : _allShaders) {
			shader.frameRenderCallback(event);
		}
		EntityMaskPostEffect.onFrame(event);
		AddonPostEffect.onFrame(event);
	}

	public void frameRenderCallback(RenderLevelStageEvent.Stage stage) {
		for (RotpShader shader : _allShaders) {
			shader.frameRenderCallback(stage);
		}
	}


	
	public static PostChain loadPostShaderChain(ResourceLocation path, RenderTarget targetBuffer) {
		ResourceLocation actualPath = Minecraft.getInstance().getResourceManager().getResource(path).isPresent()
				? path
				: path.withPath(p -> "shaders/post/" + p + ".json");
		Minecraft mc = Minecraft.getInstance();
		PostChain effect;
		try {
			effect = new PostChain(mc.getTextureManager(), mc.getResourceManager(), targetBuffer, actualPath);
			effect.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
		} catch (IOException e) {
			JojoMod.getLogger().error("Failed to load shader: {}", actualPath, e);
			effect = null;
		} catch (JsonSyntaxException e) {
			JojoMod.getLogger().error("Failed to parse shader: {}", actualPath, e);
			effect = null;
		}
		return effect;
	}

	public static void prepareMainTargetPostChain(PostChain postChain) {
		if (postChain != null) {
			postChain.screenTarget = Minecraft.getInstance().getMainRenderTarget();
		}
	}
	
	public static void loadCoreShader(RegisterShadersEvent event, 
			ResourceLocation path, VertexFormat vertexFormat, 
			Consumer<ShaderInstance> init) {
		ResourceProvider resourceProvider = event.getResourceProvider();
		ShaderInstance shader;
		try {
			shader = new ShaderInstance(resourceProvider, path, vertexFormat);
			event.registerShader(shader, init);
		}
		catch (IOException e) {
			JojoMod.getLogger().error("Failed loading a core shader from the mod", e);
			init.accept(null);
		}
	}

	public static void loadPrivateTargetCoreShader(
			RegisterShadersEvent event,
			ResourceLocation path,
			VertexFormat vertexFormat,
			Consumer<ShaderInstance> init) {
		ResourceProvider resourceProvider = event.getResourceProvider();
		ShaderInstance shader;
		try {
			shader = new PrivateTargetShaderInstance(
					resourceProvider, path, vertexFormat);
			event.registerShader(shader, init);
		}
		catch (IOException e) {
			JojoMod.getLogger().error(
					"Failed loading a private-target core shader {}",
					path,
					e);
			init.accept(null);
		}
	}

}
