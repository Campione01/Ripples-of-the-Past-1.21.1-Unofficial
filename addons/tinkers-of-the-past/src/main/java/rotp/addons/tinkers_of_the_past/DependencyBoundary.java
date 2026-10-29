package rotp.addons.tinkers_of_the_past;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

public final class DependencyBoundary {
    public static final ResourceLocation STAND_ARROW_MATERIAL =
            TinkersOfThePast.id("stand_arrow_alloy");
    public static final ResourceLocation METEOR_MATERIAL =
            TinkersOfThePast.id("meteor");
    public static final ResourceLocation STAND_ARROW_MODIFIER =
            TinkersOfThePast.id("stando_arrow");
    public static final ResourceLocation METEOR_VIRUS_MODIFIER =
            TinkersOfThePast.id("meteor_virus");

    private DependencyBoundary() {}

    public static TConstructRuntimeState runtimeState() {
        ModList modList = ModList.get();
        return TConstructRuntimeState.fromPresence(
                modList.isLoaded("mantle"),
                modList.isLoaded("tconstruct"));
    }

    public static boolean hasCompleteRuntime() {
        return runtimeState().completeRuntimePresent();
    }

    public static boolean canActivateModifierIntegration() {
        return runtimeState().modifierIntegrationAvailable();
    }

    public static void report(Logger logger) {
        TConstructRuntimeState state = runtimeState();
        if (state == TConstructRuntimeState.ABSENT) {
            logger.info(
                    "Mantle and TConstruct are absent. Independent content "
                            + "remains active; guarded TConstruct data and "
                            + "donor modifier IDs {}, {} are preserved.",
                    STAND_ARROW_MODIFIER,
                    METEOR_VIRUS_MODIFIER);
            return;
        }

        if (state
                != TConstructRuntimeState
                        .COMPLETE_BLOCKED_API_UNAVAILABLE) {
            logger.warn(
                    "Incomplete TConstruct runtime detected ({}). Donor "
                            + "materials {}, {} and modifiers {}, {} remain "
                            + "preserved, but integration cannot activate.",
                    state,
                    STAND_ARROW_MATERIAL,
                    METEOR_MATERIAL,
                    STAND_ARROW_MODIFIER,
                    METEOR_VIRUS_MODIFIER);
            return;
        }

        logger.warn(
                "Mantle and TConstruct are present, but no compatible "
                        + "NeoForge 1.21.1 API artifacts are locked. Donor "
                        + "modifier IDs {}, {} and module-local callback "
                        + "semantics are preserved; modifier registration "
                        + "and single-use dispatch remain blocked. The donor "
                        + "RecipeCacheInvalidator reload callback and "
                        + "BlockSideHitListener bootstrap are likewise "
                        + "retained for the future verified adapter. See the "
                        + "packaged dependency-boundary receipt.",
                STAND_ARROW_MODIFIER,
                METEOR_VIRUS_MODIFIER);
    }
}
