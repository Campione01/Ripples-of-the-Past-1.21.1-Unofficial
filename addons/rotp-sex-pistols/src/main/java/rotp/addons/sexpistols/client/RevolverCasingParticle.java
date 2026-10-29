package rotp.addons.sexpistols.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import rotp.core.client.ClientTimeStopHandler;
import rotp.core.subsystems.timestop.TimeStopState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.casing.CasingEjectionSpec;
import rotp.addons.sexpistols.content.revolver.casing.CasingFlightState;
import rotp.addons.sexpistols.content.revolver.casing.CasingWorldPhysics;
import rotp.addons.sexpistols.registry.SexPistolsParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT)
public final class RevolverCasingParticle
        extends TextureSheetParticle {
    private static final ThreadLocal<CasingEjectionSpec> NEXT_SPEC =
            new ThreadLocal<>();
    private static final Set<RevolverCasingParticle> ACTIVE_CASINGS =
            new LinkedHashSet<>();
    private static boolean manualRenderPass;

    private final CasingFlightState state;
    private final CasingFlightState.CollisionResolver collisionResolver;
    private long lastAdvancedGameTime;
    private boolean awaitingEngineRegistration;
    private boolean manuallyTicking;
    private boolean renderReceiptRecorded;
    private boolean removalReceiptRecorded;

    private RevolverCasingParticle(
            ClientLevel level,
            CasingEjectionSpec spec,
            SpriteSet sprites) {
        super(level, spec.x(), spec.y(), spec.z());
        this.state = new CasingFlightState(spec);
        this.collisionResolver = CasingWorldPhysics.resolver(level);
        this.lastAdvancedGameTime = Math.min(
                spec.spawnGameTime(), level.getGameTime());
        this.awaitingEngineRegistration =
                ClientTimeStopHandler.shouldFreezeVisualTick();
        pickSprite(sprites);
        gravity = 0.58F;
        hasPhysics = true;
        quadSize = spec.size();
        lifetime = spec.lifetime();
        advanceToClientTime(level);
        synchronizeFromState();
        ACTIVE_CASINGS.add(this);
        SexPistolsVisualDiagnostics.recordCasingSpawn(lifetime);
        if (state.removed()) {
            remove();
        }
    }

    public static void spawn(
            ClientLevel level, CasingEjectionSpec spec) {
        NEXT_SPEC.set(spec);
        try {
            level.addParticle(
                    SexPistolsParticles.REVOLVER_CASING.get(),
                    spec.x(),
                    spec.y(),
                    spec.z(),
                    spec.xVelocity(),
                    spec.yVelocity(),
                    spec.zVelocity());
        }
        finally {
            NEXT_SPEC.remove();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        boolean frozen = ClientTimeStopHandler.shouldFreezeVisualTick();
        for (RevolverCasingParticle particle
                : new ArrayList<>(ACTIVE_CASINGS)) {
            if (particle.removed) {
                ACTIVE_CASINGS.remove(particle);
                continue;
            }
            // Frozen ParticleEngine ticks cannot drain their pending queue.
            boolean queueHandoff = !frozen
                    && particle.awaitingEngineRegistration;
            if (!frozen && !queueHandoff) {
                continue;
            }
            particle.manuallyTicking = true;
            try {
                particle.tick();
            }
            finally {
                particle.manuallyTicking = false;
            }
            if (queueHandoff) {
                particle.awaitingEngineRegistration = false;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage()
                        != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !ClientTimeStopHandler.shouldFreezeVisualTick()
                || ACTIVE_CASINGS.isEmpty()) {
            return;
        }
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ArrayList<RevolverCasingParticle> particles =
                new ArrayList<>();
        for (RevolverCasingParticle particle : ACTIVE_CASINGS) {
            if (!particle.removed
                    && particle.level == minecraft.level
                    && event.getFrustum().isVisible(
                            particle.getRenderBoundingBox(1.0F))) {
                particles.add(particle);
            }
        }
        if (particles.isEmpty()) {
            return;
        }

        // This includes casings still waiting in ParticleEngine's add queue.
        LightTexture lightTexture =
                minecraft.gameRenderer.lightTexture();
        lightTexture.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        RenderSystem.activeTexture(33986);
        RenderSystem.activeTexture(33984);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        BufferBuilder vertices = ParticleRenderType
                .PARTICLE_SHEET_TRANSLUCENT
                .begin(Tesselator.getInstance(),
                        minecraft.getTextureManager());
        if (vertices == null) {
            lightTexture.turnOffLightLayer();
            return;
        }
        float partialTick = ClientTimeStopHandler
                .getConstantWorldPartialTick(
                        event.getPartialTick()
                                .getGameTimeDeltaPartialTick(true));
        manualRenderPass = true;
        try {
            for (RevolverCasingParticle particle : particles) {
                particle.render(
                        vertices, event.getCamera(), partialTick);
            }
            MeshData mesh = vertices.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }
        finally {
            manualRenderPass = false;
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        for (RevolverCasingParticle particle
                : new ArrayList<>(ACTIVE_CASINGS)) {
            if (particle.level == event.getLevel()) {
                particle.remove();
            }
        }
    }

    @Override
    public void tick() {
        if (!manuallyTicking
                && ClientTimeStopHandler.shouldFreezeVisualTick()) {
            return;
        }
        CasingFlightState.TickResult result = advanceStateTo(
                level.getGameTime());
        synchronizeFromState();
        if (result.removed()) {
            remove();
        }
    }

    private void synchronizeFromState() {
        xo = state.previousX();
        yo = state.previousY();
        zo = state.previousZ();
        setPos(state.x(), state.y(), state.z());
        xd = state.xVelocity();
        yd = state.yVelocity();
        zd = state.zVelocity();
        age = state.age();
        onGround = state.onGround();
    }

    private void advanceToClientTime(ClientLevel level) {
        advanceStateTo(level.getGameTime());
    }

    private CasingFlightState.TickResult advanceStateTo(
            long targetGameTime) {
        long elapsed = Math.max(
                0L, targetGameTime - lastAdvancedGameTime);
        int ticks = (int) Math.min(
                elapsed, CasingEjectionSpec.MAX_LIFETIME_TICKS + 1L);
        boolean landed = false;
        for (int i = 0; i < ticks && !state.removed(); i++) {
            landed |= state.tick(
                    isTimeStopped(), collisionResolver).landed();
        }
        lastAdvancedGameTime = Math.max(
                lastAdvancedGameTime, targetGameTime);
        return new CasingFlightState.TickResult(
                landed, state.removed());
    }

    private boolean isTimeStopped() {
        return TimeStopState.getClientDisplayInstance(
                new ChunkPos(BlockPos.containing(
                        state.x(), state.y(), state.z())))
                .isPresent();
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        if (removed
                || (ClientTimeStopHandler.shouldFreezeVisualTick()
                        && !manualRenderPass)) {
            return;
        }
        if (!renderReceiptRecorded) {
            renderReceiptRecorded = true;
            SexPistolsVisualDiagnostics.recordCasingRender();
        }
        super.render(vertices, camera, partialTick);
    }

    @Override
    public void remove() {
        ACTIVE_CASINGS.remove(this);
        if (!removalReceiptRecorded) {
            removalReceiptRecorded = true;
            SexPistolsVisualDiagnostics.recordCasingRemoved();
        }
        super.remove();
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp(
                ((float) age + partialTick) / lifetime,
                0.0F,
                1.0F);
        return quadSize * (1.0F - progress * progress);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Factory
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            CasingEjectionSpec spec = NEXT_SPEC.get();
            if (spec == null) {
                long fallbackId = Double.doubleToLongBits(x)
                        ^ Long.rotateLeft(Double.doubleToLongBits(y), 17)
                        ^ Long.rotateLeft(Double.doubleToLongBits(z), 31);
                spec = new CasingEjectionSpec(
                        fallbackId,
                        level.getGameTime(),
                        x,
                        y,
                        z,
                        xSpeed,
                        ySpeed,
                        zSpeed,
                        42,
                        0.13F,
                        Math.floorMod((int) fallbackId, 4),
                        1.0F);
            }
            return new RevolverCasingParticle(level, spec, sprites);
        }
    }
}
