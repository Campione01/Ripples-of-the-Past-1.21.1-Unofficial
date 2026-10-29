package rotp.addons.tickettoride.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.diagnostic.TicketToRideDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Read-only client model and render receipts for automated acceptance.
 */
@OnlyIn(Dist.CLIENT)
public final class TicketToRideClientDiagnostics {
    public static final String TEARS_BLADE_MODEL_KEY =
            "rotp_ttr:tearsblade#inventory";
    public static final String TEARS_BLADE_MODEL_PARENT =
            "rotp_ttr:item/tearsblade_native";
    public static final String TEARS_BLADE_RENDER_ROUTE =
            "vanilla_item_renderer/baked_inventory_model";
    public static final String LOVE_TRAIN_WALL_TEXTURE =
            "rotp_ttr:textures/particle/love_train_light_wall.png";
    public static final String OWNER_FIRST_PERSON =
            "owner_first_person";
    public static final String OWNER_THIRD_PERSON =
            "owner_third_person";
    public static final String TRACKED_OBSERVER =
            "tracked_non_owner_observer";

    private static final ModelResourceLocation TEARS_BLADE_MODEL =
            ModelResourceLocation.inventory(
                    TicketToRideAddon.id("tearsblade"));
    private static final Map<UUID, RenderReceipt> RECEIPTS =
            new HashMap<>();

    private static long particleProviderCreationCount;
    private static long particleRemovalCount;
    private static int liveParticleInstances;
    private static String lastParticleId = "";
    private static long particleRenderCount;
    private static long observerParticleRenderCount;
    private static int lastParticleVertices;
    private static String lastParticleViewRoute = "none";
    private static String lastParticleCameraEntityId = "none";
    private static UUID lastParticleCameraEntityUuid;

    private TicketToRideClientDiagnostics() {}

    public static synchronized ClientSnapshot localSnapshot() {
        return snapshot(Minecraft.getInstance().player);
    }

    public static synchronized ClientSnapshot snapshot(
            @Nullable LivingEntity user) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel missing = minecraft.getModelManager()
                .getMissingModel();
        BakedModel registered = minecraft.getModelManager()
                .getModel(TEARS_BLADE_MODEL);
        boolean registeredMissing = registered == null
                || registered == missing;
        int registeredQuads = registered != null
                ? quadCount(registered) : -1;

        ItemStack heldBlade = heldBlade(user);
        BakedModel resolved = !heldBlade.isEmpty()
                ? minecraft.getItemRenderer().getModel(
                        heldBlade,
                        minecraft.level,
                        user,
                        0)
                : registered;
        boolean resolvedMissing = resolved == null
                || resolved == missing;
        RenderReceipt receipt = user != null
                ? RECEIPTS.getOrDefault(
                        user.getUUID(), RenderReceipt.EMPTY)
                : RenderReceipt.EMPTY;
        long gameTime = minecraft.level != null
                ? minecraft.level.getGameTime() : -1L;
        boolean wallSubmittedRecently = gameTime >= 0L
                && receipt.lastWallGameTime >= 0L
                && gameTime - receipt.lastWallGameTime <= 1L;
        boolean bakedRouteReady = !registeredMissing
                && !registered.isCustomRenderer()
                && registeredQuads > 0
                && (heldBlade.isEmpty()
                        || !resolvedMissing
                        && !resolved.isCustomRenderer());
        TicketToRideDiagnostics.Snapshot production =
                TicketToRideDiagnostics.snapshot(user);
        return new ClientSnapshot(
                production,
                TEARS_BLADE_MODEL_KEY,
                TEARS_BLADE_MODEL_PARENT,
                TEARS_BLADE_RENDER_ROUTE,
                !registeredMissing,
                registeredMissing,
                registered != null
                        && registered.isCustomRenderer(),
                registered != null
                        ? registered.getClass().getName() : "",
                registeredQuads,
                !heldBlade.isEmpty(),
                resolvedMissing,
                resolved != null && resolved.isCustomRenderer(),
                resolved != null
                        ? resolved.getClass().getName() : "",
                resolved != null && resolved == registered,
                bakedRouteReady,
                receipt.acceptedHandRenderCount,
                receipt.lastHand,
                receipt.completedThirdPersonPassCount,
                receipt.itemFillViews.freeze(),
                receipt.loveTrainViews.freeze(),
                receipt.wallSubmissionCount,
                receipt.lastWallColumns,
                receipt.lastWallVertices,
                receipt.lastWallRadius,
                receipt.lastWallAlpha,
                receipt.lastWallGameTime,
                wallSubmittedRecently,
                receipt.lastWallTexture,
                receipt.lastWallViewRoute,
                receipt.lastWallCameraEntityId,
                receipt.lastWallCameraEntityUuid,
                particleProviderCreationCount,
                particleRemovalCount,
                liveParticleInstances,
                lastParticleId,
                particleRenderCount,
                observerParticleRenderCount,
                lastParticleVertices,
                lastParticleViewRoute,
                lastParticleCameraEntityId,
                lastParticleCameraEntityUuid,
                production.loveTrain().cleanupComplete()
                        && !wallSubmittedRecently
                        && liveParticleInstances == 0);
    }

    static synchronized void recordHandRender(
            Player player, InteractionHand hand) {
        RenderReceipt receipt = receipt(player);
        receipt.acceptedHandRenderCount++;
        receipt.lastHand = hand.name();
        recordActiveActionView(player, receipt, true);
    }

    static synchronized void recordThirdPersonPass(
            Player player) {
        RenderReceipt receipt = receipt(player);
        receipt.completedThirdPersonPassCount++;
        recordActiveActionView(player, receipt, false);
    }

    static synchronized void recordWallSubmission(
            LivingEntity owner,
            int columns,
            int vertices,
            float radius,
            float alpha,
            ResourceLocation texture) {
        RenderReceipt receipt = receipt(owner);
        receipt.wallSubmissionCount++;
        receipt.lastWallColumns = columns;
        receipt.lastWallVertices = vertices;
        receipt.lastWallRadius = radius;
        receipt.lastWallAlpha = alpha;
        receipt.lastWallGameTime = owner.level().getGameTime();
        receipt.lastWallTexture = texture != null
                ? texture.toString() : "none";
        ViewRoute view = viewRoute(owner, false);
        receipt.lastWallViewRoute = view.route();
        receipt.lastWallCameraEntityId = view.cameraEntityId();
        receipt.lastWallCameraEntityUuid = view.cameraEntityUuid();
    }

    static synchronized void recordParticleCreated(
            String particleId) {
        particleProviderCreationCount++;
        liveParticleInstances++;
        lastParticleId = particleId;
    }

    static synchronized void recordParticleRemoved() {
        particleRemovalCount++;
        liveParticleInstances = Math.max(
                0, liveParticleInstances - 1);
    }

    static synchronized void recordParticleRendered(
            String particleId, @Nullable Entity cameraEntity,
            int submittedVertices) {
        Minecraft minecraft = Minecraft.getInstance();
        particleRenderCount++;
        lastParticleId = particleId;
        lastParticleVertices = submittedVertices;
        lastParticleCameraEntityUuid = cameraEntity != null
                ? cameraEntity.getUUID() : null;
        lastParticleCameraEntityId = entityId(cameraEntity);
        boolean ownerCamera = minecraft.player != null
                && cameraEntity == minecraft.player;
        lastParticleViewRoute = ownerCamera
                ? minecraft.options.getCameraType() == CameraType.FIRST_PERSON
                        ? OWNER_FIRST_PERSON : OWNER_THIRD_PERSON
                : cameraEntity != null ? TRACKED_OBSERVER : "none";
        if (TRACKED_OBSERVER.equals(lastParticleViewRoute)) {
            observerParticleRenderCount++;
        }
    }

    static synchronized void clearReceipts() {
        RECEIPTS.clear();
        particleProviderCreationCount = 0L;
        particleRemovalCount = 0L;
        liveParticleInstances = 0;
        lastParticleId = "";
        particleRenderCount = 0L;
        observerParticleRenderCount = 0L;
        lastParticleVertices = 0;
        lastParticleViewRoute = "none";
        lastParticleCameraEntityId = "none";
        lastParticleCameraEntityUuid = null;
    }

    private static void recordActiveActionView(
            Player player, RenderReceipt receipt, boolean handPass) {
        TicketToRideDiagnostics.Snapshot production =
                TicketToRideDiagnostics.snapshot(player);
        String actionId = production.activeActionId();
        ActionViewReceipt action =
                TicketToRideDiagnostics.ITEM_FILL_ACTION_ID.equals(actionId)
                        ? receipt.itemFillViews
                        : TicketToRideDiagnostics.LOVE_TRAIN_ACTION_ID
                                .equals(actionId)
                                        ? receipt.loveTrainViews
                                        : null;
        if (action == null
                && production.loveTrain().serviceActive()) {
            action = receipt.loveTrainViews;
        }
        if (action == null) {
            return;
        }
        ViewRoute view = viewRoute(player, handPass);
        if ("none".equals(view.route())) {
            return;
        }
        action.record(
                view,
                production.activeActionPhase(),
                production.activeActionPhaseTick());
    }

    private static ViewRoute viewRoute(
            LivingEntity owner, boolean handPass) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = minecraft.getCameraEntity();
        CameraType cameraType = minecraft.options.getCameraType();
        String route;
        if (handPass) {
            route = cameraType == CameraType.FIRST_PERSON
                            && cameraEntity == owner
                    ? OWNER_FIRST_PERSON : "none";
        }
        else if (cameraType == CameraType.FIRST_PERSON) {
            route = "none";
        }
        else if (cameraEntity == owner) {
            route = OWNER_THIRD_PERSON;
        }
        else {
            route = cameraEntity != null
                    ? TRACKED_OBSERVER : "none";
        }
        return new ViewRoute(
                route,
                cameraType.name(),
                entityId(cameraEntity),
                cameraEntity != null ? cameraEntity.getUUID() : null);
    }

    private static String entityId(@Nullable Entity entity) {
        if (entity == null) {
            return "none";
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(
                entity.getType());
        return id != null ? id.toString() : "none";
    }

    private static RenderReceipt receipt(LivingEntity entity) {
        return RECEIPTS.computeIfAbsent(
                entity.getUUID(), ignored -> new RenderReceipt());
    }

    private static ItemStack heldBlade(
            @Nullable LivingEntity user) {
        if (user == null) {
            return ItemStack.EMPTY;
        }
        if (user.getMainHandItem().is(
                TicketToRideAddon.TEARS_BLADE.get())) {
            return user.getMainHandItem();
        }
        if (user.getOffhandItem().is(
                TicketToRideAddon.TEARS_BLADE.get())) {
            return user.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static int quadCount(BakedModel model) {
        try {
            RandomSource random = RandomSource.create(0L);
            int quads = model.getQuads(
                    null,
                    null,
                    random,
                    ModelData.EMPTY,
                    null).size();
            for (Direction direction : Direction.values()) {
                random.setSeed(0L);
                quads += model.getQuads(
                        null,
                        direction,
                        random,
                        ModelData.EMPTY,
                        null).size();
            }
            return quads;
        }
        catch (RuntimeException exception) {
            return -1;
        }
    }

    public record ClientSnapshot(
            TicketToRideDiagnostics.Snapshot production,
            String bladeModelKey,
            String bladeModelParent,
            String bladeRenderRoute,
            boolean registeredBladeModelPresent,
            boolean registeredBladeModelMissing,
            boolean registeredBladeModelCustomRenderer,
            String registeredBladeModelClass,
            int registeredBladeModelQuadCount,
            boolean heldBladePresent,
            boolean heldResolvedModelMissing,
            boolean heldResolvedModelCustomRenderer,
            String heldResolvedModelClass,
            boolean heldResolvedModelSameAsRegistered,
            boolean vanillaBakedRouteReady,
            long acceptedHandRenderCount,
            String lastRenderedHand,
            long completedThirdPersonPassCount,
            ActionViewSnapshot itemFillActionViews,
            ActionViewSnapshot loveTrainActionViews,
            long wallSubmissionCount,
            int lastWallColumns,
            int lastWallVertices,
            float lastWallRadius,
            float lastWallAlpha,
            long lastWallGameTime,
            boolean wallSubmittedRecently,
            String lastWallTexture,
            String lastWallViewRoute,
            String lastWallCameraEntityId,
            @Nullable UUID lastWallCameraEntityUuid,
            long particleProviderCreationCount,
            long particleRemovalCount,
            int liveParticleInstances,
            String lastParticleId,
            long particleRenderCount,
            long observerParticleRenderCount,
            int lastParticleVertices,
            String lastParticleViewRoute,
            String lastParticleCameraEntityId,
            @Nullable UUID lastParticleCameraEntityUuid,
            boolean loveTrainVisualCleanupComplete) {}

    public record ActionViewSnapshot(
            long ownerFirstPersonPasses,
            long ownerThirdPersonPostPasses,
            long observerPostPasses,
            String lastViewRoute,
            String lastCameraType,
            String lastCameraEntityId,
            @Nullable UUID lastCameraEntityUuid,
            String lastActionPhase,
            float lastActionPhaseTick) {}

    private record ViewRoute(
            String route,
            String cameraType,
            String cameraEntityId,
            @Nullable UUID cameraEntityUuid) {}

    private static final class ActionViewReceipt {
        private long ownerFirstPersonPasses;
        private long ownerThirdPersonPostPasses;
        private long observerPostPasses;
        private String lastViewRoute = "none";
        private String lastCameraType = "none";
        private String lastCameraEntityId = "none";
        private UUID lastCameraEntityUuid;
        private String lastActionPhase = "none";
        private float lastActionPhaseTick = -1.0F;

        private void record(
                ViewRoute view, String phase, float phaseTick) {
            switch (view.route()) {
                case OWNER_FIRST_PERSON -> ownerFirstPersonPasses++;
                case OWNER_THIRD_PERSON -> ownerThirdPersonPostPasses++;
                case TRACKED_OBSERVER -> observerPostPasses++;
                default -> {
                    return;
                }
            }
            lastViewRoute = view.route();
            lastCameraType = view.cameraType();
            lastCameraEntityId = view.cameraEntityId();
            lastCameraEntityUuid = view.cameraEntityUuid();
            lastActionPhase = phase;
            lastActionPhaseTick = phaseTick;
        }

        private ActionViewSnapshot freeze() {
            return new ActionViewSnapshot(
                    ownerFirstPersonPasses,
                    ownerThirdPersonPostPasses,
                    observerPostPasses,
                    lastViewRoute,
                    lastCameraType,
                    lastCameraEntityId,
                    lastCameraEntityUuid,
                    lastActionPhase,
                    lastActionPhaseTick);
        }
    }

    private static final class RenderReceipt {
        private static final RenderReceipt EMPTY =
                new RenderReceipt();

        private long acceptedHandRenderCount;
        private String lastHand = "";
        private long completedThirdPersonPassCount;
        private final ActionViewReceipt itemFillViews =
                new ActionViewReceipt();
        private final ActionViewReceipt loveTrainViews =
                new ActionViewReceipt();
        private long wallSubmissionCount;
        private int lastWallColumns;
        private int lastWallVertices;
        private float lastWallRadius;
        private float lastWallAlpha;
        private long lastWallGameTime = -1L;
        private String lastWallTexture = "none";
        private String lastWallViewRoute = "none";
        private String lastWallCameraEntityId = "none";
        private UUID lastWallCameraEntityUuid;
    }
}
