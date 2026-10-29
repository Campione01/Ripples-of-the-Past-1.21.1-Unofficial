package rotp.addons.diegosworld.client;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.client.render.EntityPostRenderExtensions;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.shader.CustomLevelRenderStages;
import rotp.core.client.shader.ModShaders;
import rotp.core.client.shader.TimeStopPostShader;
import rotp.core.client.shader.core.RotpShader;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

@EventBusSubscriber(
        modid = DiegosTheWorldAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class DiegoClientEvents {
    private static final ModelResourceLocation REVOLVER_MODEL =
            ModelResourceLocation.inventory(
                    DiegosTheWorldAddon.id("revolver"));
    private static final double CENTRAL_SCREEN_LIMIT = 0.82D;
    private static final RotpShader TIME_STOP_POST_PROCESS_PROBE =
            new TimeStopPostProcessProbe();
    private static final VarHandle TIME_STOP_ACTIVE_THIS_FRAME =
            timeStopActiveThisFrameHandle();

    private static boolean diagnosticsArmed;
    private static long pendingFirstPersonFrame = Long.MIN_VALUE;
    private static long pendingTimeStopFrame = Long.MIN_VALUE;
    private static long pendingWireStageFrame = Long.MIN_VALUE;
    private static long lastFirstPersonFrame = Long.MIN_VALUE;
    private static long lastObserverFrame = Long.MIN_VALUE;
    private static long lastTimeStopFrame = Long.MIN_VALUE;
    private static long lastWireStageFrame = Long.MIN_VALUE;
    private static int firstPersonGunFrames;
    private static int observerGunFrames;
    private static int timeStopStageFrames;
    private static int wireModelQuadRequests;
    private static int wireModelQuadCount;
    private static int wireCutoutStageFrames;
    private static boolean revolverModelPresent;
    private static int revolverModelQuads;
    private static String revolverModelClass = "not_observed";
    private static ResourceLocation timeStopShader;
    private static ResourceLocation timeStopShaderResource;
    private static boolean timeStopShaderResourcePresent;
    private static boolean timeStopActiveThisFrameObserved;
    private static BlockPos lastVisibleWirePos;
    private static Projection lastWireProjection =
            Projection.notObserved();

    private DiegoClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                DiegosTheWorldAddon.THE_WORLD_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                DiegosTheWorldAddon.REVOLVER_BULLET.get(),
                DiegoRevolverBulletRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(
                    DiegosTheWorldAddon.BARBED_WIRE.get(),
                    RenderType.cutout());
            installTimeStopPostProcessProbe();
        });
    }

    @SubscribeEvent
    public static void modifyBakingResult(
            ModelEvent.ModifyBakingResult event) {
        for (BlockState state : DiegosTheWorldAddon.BARBED_WIRE.get()
                .getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location =
                    BlockModelShaper.stateToModelLocation(state);
            BakedModel model = event.getModels().get(location);
            if (model != null
                    && !(model instanceof BarbedWireDiagnosticModel)) {
                event.getModels().put(
                        location,
                        new BarbedWireDiagnosticModel(model));
            }
        }
    }

    public static synchronized void resetDiagnostics() {
        installTimeStopPostProcessProbe();
        diagnosticsArmed = true;
        pendingFirstPersonFrame = Long.MIN_VALUE;
        pendingTimeStopFrame = Long.MIN_VALUE;
        pendingWireStageFrame = Long.MIN_VALUE;
        lastFirstPersonFrame = Long.MIN_VALUE;
        lastObserverFrame = Long.MIN_VALUE;
        lastTimeStopFrame = Long.MIN_VALUE;
        lastWireStageFrame = Long.MIN_VALUE;
        firstPersonGunFrames = 0;
        observerGunFrames = 0;
        timeStopStageFrames = 0;
        wireModelQuadRequests = 0;
        wireModelQuadCount = 0;
        wireCutoutStageFrames = 0;
        revolverModelPresent = false;
        revolverModelQuads = 0;
        revolverModelClass = "not_observed";
        timeStopShader = null;
        timeStopShaderResource = null;
        timeStopShaderResourcePresent = false;
        timeStopActiveThisFrameObserved = false;
        lastVisibleWirePos = null;
        lastWireProjection = Projection.notObserved();
        DiegoRevolverBulletRenderer.resetDiagnostics();
    }

    public static synchronized DiagnosticSnapshot diagnosticSnapshot() {
        return new DiagnosticSnapshot(
                firstPersonGunFrames,
                observerGunFrames,
                lastFirstPersonFrame,
                lastObserverFrame,
                revolverModelPresent,
                revolverModelQuads,
                revolverModelClass,
                timeStopStageFrames,
                lastTimeStopFrame,
                timeStopShader,
                timeStopShaderResource,
                timeStopShaderResourcePresent,
                timeStopActiveThisFrameObserved,
                wireModelQuadRequests,
                wireModelQuadCount,
                wireCutoutStageFrames,
                lastWireStageFrame,
                lastVisibleWirePos,
                lastWireProjection);
    }

    private static synchronized void recordWireQuads(
            int quadCount) {
        if (!diagnosticsArmed) {
            return;
        }
        wireModelQuadRequests++;
        wireModelQuadCount += quadCount;
    }

    private static void installTimeStopPostProcessProbe() {
        ModShaders shaders = ModShaders.getInstance();
        if (shaders != null
                && !shaders._allShaders.contains(
                        TIME_STOP_POST_PROCESS_PROBE)) {
            shaders._allShaders.add(TIME_STOP_POST_PROCESS_PROBE);
        }
    }

    private static synchronized void recordTimeStopPostProcess() {
        if (!diagnosticsArmed) {
            return;
        }
        ModShaders shaders = ModShaders.getInstance();
        if (shaders == null
                || shaders.timeStopPostShader == null
                || shaders.timeStopShaderManager == null
                || !shaders.timeStopShaderManager.active()
                || shaders.timeStopShaderManager.selectedShader() == null) {
            return;
        }
        if (TIME_STOP_ACTIVE_THIS_FRAME == null
                || !(boolean) TIME_STOP_ACTIVE_THIS_FRAME.get(
                        shaders.timeStopPostShader)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        pendingTimeStopFrame =
                EntityPostRenderExtensions.currentFrameId();
        timeStopActiveThisFrameObserved = true;
        timeStopShader =
                shaders.timeStopShaderManager.selectedShader();
        timeStopShaderResource = minecraft.getResourceManager()
                .getResource(timeStopShader).isPresent()
                ? timeStopShader
                : timeStopShader.withPath(path ->
                        "shaders/post/" + path + ".json");
        timeStopShaderResourcePresent = minecraft
                .getResourceManager()
                .getResource(timeStopShaderResource).isPresent();
    }

    @Nullable
    private static VarHandle timeStopActiveThisFrameHandle() {
        try {
            return MethodHandles.privateLookupIn(
                            TimeStopPostShader.class,
                            MethodHandles.lookup())
                    .findVarHandle(
                            TimeStopPostShader.class,
                            "activeThisFrame",
                            boolean.class);
        }
        catch (IllegalAccessException | NoSuchFieldException failure) {
            return null;
        }
    }

    private static void recordRevolverModel(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel missing = minecraft.getModelManager()
                .getMissingModel();
        BakedModel registered = minecraft.getModelManager()
                .getModel(REVOLVER_MODEL);
        BakedModel resolved = minecraft.getItemRenderer().getModel(
                stack,
                minecraft.level,
                minecraft.player,
                0);
        revolverModelPresent = registered != null
                && registered != missing
                && resolved != null
                && resolved != missing
                && !resolved.isCustomRenderer();
        revolverModelQuads = registered != null
                ? quadCount(registered) : -1;
        revolverModelClass = resolved != null
                ? resolved.getClass().getName()
                : "missing";
    }

    private static int quadCount(BakedModel model) {
        try {
            RandomSource random = RandomSource.create(0L);
            int count = model.getQuads(
                    null, null, random, ModelData.EMPTY, null).size();
            for (Direction direction : Direction.values()) {
                random.setSeed(0L);
                count += model.getQuads(
                        null,
                        direction,
                        random,
                        ModelData.EMPTY,
                        null).size();
            }
            return count;
        }
        catch (RuntimeException exception) {
            return -1;
        }
    }

    private static Projection projection(Vec3 point) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 offset = point.subtract(camera.getPosition());
        Vector3f lookVector = camera.getLookVector();
        Vector3f leftVector = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        double forward = offset.dot(new Vec3(
                lookVector.x(), lookVector.y(), lookVector.z()));
        double aspect = Math.max(1.0D,
                (double) minecraft.getWindow().getWidth()
                        / Math.max(1,
                                minecraft.getWindow().getHeight()));
        double scale = Math.max(
                1.0E-6D,
                forward * Math.tan(Math.toRadians(
                        Math.max(1, minecraft.options.fov().get()))
                        / 2.0D));
        double x = offset.dot(new Vec3(
                leftVector.x(), leftVector.y(), leftVector.z()))
                / (scale * aspect);
        double y = offset.dot(new Vec3(
                upVector.x(), upVector.y(), upVector.z())) / scale;
        double projectedHeightPixels = Math.abs(
                minecraft.getWindow().getHeight() / (2.0D * scale));
        return new Projection(
                forward,
                x,
                y,
                projectedHeightPixels,
                forward > 0.05D
                        && Math.abs(x) <= CENTRAL_SCREEN_LIMIT
                        && Math.abs(y) <= CENTRAL_SCREEN_LIMIT);
    }

    @EventBusSubscriber(
            modid = DiegosTheWorldAddon.MOD_ID,
            value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.GAME)
    public static final class GameBusEvents {
        private GameBusEvents() {}

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static synchronized void onRenderHand(
                RenderHandEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!diagnosticsArmed
                    || event.isCanceled()
                    || event.getHand() != InteractionHand.MAIN_HAND
                    || minecraft.player == null
                    || minecraft.options.getCameraType()
                            != CameraType.FIRST_PERSON
                    || !event.getItemStack().is(
                            DiegosTheWorldAddon.REVOLVER.get())) {
                return;
            }
            recordRevolverModel(event.getItemStack());
            pendingFirstPersonFrame =
                    EntityPostRenderExtensions.currentFrameId();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static synchronized void onRenderPlayer(
                RenderPlayerEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!diagnosticsArmed
                    || minecraft.player == null
                    || event.getEntity() != minecraft.player
                    || minecraft.options.getCameraType()
                            == CameraType.FIRST_PERSON
                    || !event.getEntity().getMainHandItem().is(
                            DiegosTheWorldAddon.REVOLVER.get())) {
                return;
            }
            long frame = EntityPostRenderExtensions.currentFrameId();
            if (lastObserverFrame != frame) {
                recordRevolverModel(
                        event.getEntity().getMainHandItem());
                lastObserverFrame = frame;
                observerGunFrames++;
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static synchronized void onRenderLevelStage(
                RenderLevelStageEvent event) {
            if (!diagnosticsArmed) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            long frame = EntityPostRenderExtensions.currentFrameId();
            if (event.getStage()
                    == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                    && minecraft.level != null
                    && minecraft.player != null
                    && wireModelQuadRequests > 0
                    && wireModelQuadCount > 0) {
                for (BlockPos pos : BlockPos.withinManhattan(
                        minecraft.player.blockPosition(), 12, 6, 12)) {
                    if (!minecraft.level.getBlockState(pos).is(
                            DiegosTheWorldAddon.BARBED_WIRE.get())) {
                        continue;
                    }
                    Projection visible = projection(
                            Vec3.atCenterOf(pos));
                    if (visible.centralScreenRegion()) {
                        lastVisibleWirePos = pos.immutable();
                        lastWireProjection = visible;
                        pendingWireStageFrame = frame;
                        break;
                    }
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static synchronized void onRenderFramePost(
                RenderFrameEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!diagnosticsArmed || minecraft.player == null) {
                return;
            }
            if (pendingFirstPersonFrame != Long.MIN_VALUE
                    && pendingFirstPersonFrame
                            != lastFirstPersonFrame
                    && minecraft.options.getCameraType()
                            == CameraType.FIRST_PERSON
                    && minecraft.player.getMainHandItem().is(
                            DiegosTheWorldAddon.REVOLVER.get())
                    && revolverModelPresent
                    && revolverModelQuads > 0) {
                lastFirstPersonFrame = pendingFirstPersonFrame;
                firstPersonGunFrames++;
            }
            if (pendingTimeStopFrame != Long.MIN_VALUE
                    && pendingTimeStopFrame != lastTimeStopFrame
                    && timeStopShaderResourcePresent) {
                lastTimeStopFrame = pendingTimeStopFrame;
                timeStopStageFrames++;
            }
            if (pendingWireStageFrame != Long.MIN_VALUE
                    && pendingWireStageFrame != lastWireStageFrame
                    && lastWireProjection.centralScreenRegion()) {
                lastWireStageFrame = pendingWireStageFrame;
                wireCutoutStageFrames++;
            }
        }
    }

    private static final class BarbedWireDiagnosticModel
            extends BakedModelWrapper<BakedModel> {
        private BarbedWireDiagnosticModel(BakedModel originalModel) {
            super(originalModel);
        }

        @Override
        public List<BakedQuad> getQuads(
                @Nullable BlockState state,
                @Nullable Direction side,
                RandomSource random,
                ModelData modelData,
                @Nullable RenderType renderType) {
            List<BakedQuad> quads = originalModel.getQuads(
                    state, side, random, modelData, renderType);
            if (state != null && state.is(
                    DiegosTheWorldAddon.BARBED_WIRE.get())) {
                recordWireQuads(quads.size());
            }
            return quads;
        }
    }

    private static final class TimeStopPostProcessProbe
            extends RotpShader {
        @Override
        public void loadPostShader(ResourceManager resourceManager) {}

        @Override
        public void resize(int width, int height) {}

        @Override
        public void close() {}

        @Override
        public void frameRenderCallback(
                RenderLevelStageEvent.Stage stage) {
            if (stage
                    == CustomLevelRenderStages.BEFORE_SPECTATOR_SHADER) {
                recordTimeStopPostProcess();
            }
        }
    }

    public record Projection(
            double forwardDepth,
            double normalizedX,
            double normalizedY,
            double projectedHeightPixels,
            boolean centralScreenRegion) {
        private static Projection notObserved() {
            return new Projection(
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    false);
        }
    }

    public record DiagnosticSnapshot(
            int firstPersonGunFrames,
            int observerGunFrames,
            long lastFirstPersonFrame,
            long lastObserverFrame,
            boolean revolverModelPresent,
            int revolverModelQuads,
            String revolverModelClass,
            int timeStopStageFrames,
            long lastTimeStopFrame,
            @Nullable ResourceLocation timeStopShader,
            @Nullable ResourceLocation timeStopShaderResource,
            boolean timeStopShaderResourcePresent,
            boolean timeStopActiveThisFrameObserved,
            int wireModelQuadRequests,
            int wireModelQuadCount,
            int wireCutoutStageFrames,
            long lastWireStageFrame,
            @Nullable BlockPos lastVisibleWirePos,
            Projection lastWireProjection) {}
}
