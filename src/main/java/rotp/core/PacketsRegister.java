package rotp.core;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.explosion.CustomExplosionPacket;
import rotp.core.entityattachment.custom_effect.TrEntityCustomEffectsPacket;
import rotp.core.entityattachment.custom_effect.sync.TrStandEffectSynchedDataPacket;
import rotp.core.mechanics.clothes.TrClothesItemsPacket;
import rotp.core.mechanics.clothes.sewing.ClSetSewingMachineItemPacket;
import rotp.core.mechanics.resolve.ResolveBoostsPacket;
import rotp.core.mechanics.resolve.TrResolvePacket;
import rotp.core.network.c2s.ClAbilityInputPacket;
import rotp.core.network.c2s.ClAimTargetPacket;
import rotp.core.network.c2s.ClAngeloRockButtonPacket;
import rotp.core.network.c2s.ClBroadcastedModSettingsPacket;
import rotp.core.network.c2s.ClDebugCommandPacket;
import rotp.core.network.c2s.ClGELifeformButtonPacket;
import rotp.core.network.c2s.ClGEMetLifeformPacket;
import rotp.core.network.c2s.ClGELifeformUiPacket;
import rotp.core.network.c2s.ClNoParamsPacket;
import rotp.core.network.c2s.ClPhotoAssignIdPacket;
import rotp.core.network.c2s.ClPhotoRequestPacket;
import rotp.core.network.c2s.ClPhotoSaveDataPacket;
import rotp.core.network.c2s.ClRPSGameInputPacket;
import rotp.core.network.c2s.ClRPSPickThoughtsPacket;
import rotp.core.network.c2s.ClRemovePlayerSoulEntityPacket;
import rotp.core.network.c2s.ClSetStandSkinPacket;
import rotp.core.network.c2s.ClSoulRotationPacket;
import rotp.core.network.c2s.ClWalkmanControlsPacket;
import rotp.core.network.s2c.BloodParticlesPacket;
import rotp.core.network.s2c.BrokenBlocksParticlesAndSoundsPacket;
import rotp.core.network.s2c.CommonConfigPacket;
import rotp.core.network.s2c.DatapackStandsPacket;
import rotp.core.network.s2c.DeflectedBulletPacket;
import rotp.core.network.s2c.EntitySyncMotionBypassingPacket;
import rotp.core.network.s2c.ItemBreakVisualsPacket;
import rotp.core.network.s2c.PhotoDataPacket;
import rotp.core.network.s2c.PhotoForOtherPlayerPacket;
import rotp.core.network.s2c.PhotoIdAssignedPacket;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.network.s2c.RPSGameStatePacket;
import rotp.core.network.s2c.RPSOpponentPickThoughtsPacket;
import rotp.core.network.s2c.ResetSyncedCommonConfigPacket;
import rotp.core.network.s2c.SoulSpawnPacket;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.network.s2c.StandFullClearPacket;
import rotp.core.network.s2c.StandSkinSoundPacket;
import rotp.core.network.s2c.TrAbilityUsePacket;
import rotp.core.network.s2c.TrAimTargetPacket;
import rotp.core.network.s2c.TrBarrageHitSoundPacket;
import rotp.core.network.s2c.TrDirectEntityDataPacket;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.network.s2c.TrDyingBodyTimerPacket;
import rotp.core.network.s2c.TrGELifeformStatePacket;
import rotp.core.network.s2c.TrGESplitConsciousnessPacket;
import rotp.core.network.s2c.TrGEStuckObjectsPacket;
import rotp.core.network.s2c.TrNonEntityStandSummonPacket;
import rotp.core.network.s2c.TrPlayerCoffinSleepPacket;
import rotp.core.network.s2c.TrPlayerModSettingsPacket;
import rotp.core.network.s2c.TrPowerDataPacket;
import rotp.core.network.s2c.TrTimeStopInstancePacket;
import rotp.core.network.s2c.TrTimeStopPlayerStatePacket;
import rotp.core.network.s2c.TrPowerStandInstancePacket;
import rotp.core.network.s2c.TrPowerTypePacket;
import rotp.core.network.s2c.TrRefreshMovementInTimeStopPacket;
import rotp.core.network.s2c.TrResetDeathTimePacket;
import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.network.s2c.TrStandSkinPacket;
import rotp.core.network.s2c.TrSyncStandOffsetPacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionWithOBBSyncPacket;
import rotp.core.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;
import rotp.core.powersystem.playerpower.packet.TrPlayerPowerLeapCooldownPacket;
import rotp.core.powersystem.standpower.StandAwakeningDataPacket;
import rotp.core.powersystem.standpower.packet.StandExpPacket;
import rotp.core.powersystem.standpower.packet.TrStandAbilityCooldownPacket;
import rotp.core.powersystem.standpower.packet.TrStandLeapCooldownPacket;
import rotp.core.powersystem.standpower.packet.TrStaminaPacket;
import rotp.core.powersystem.unlockableskill.ClLearnSkillPacket;
import rotp.core.subsystems.entity_externalcontainer._stand.input.ClStandItemInputPacket;
import rotp.core.subsystems.entity_externalcontainer.packet.ClExtendedContainerClickPacket;
import rotp.core.subsystems.entity_externalcontainer.packet.ExternalContainerClosePacket;
import rotp.core.subsystems.entity_externalcontainer.packet.ExternalContainerOpenPacket;
import rotp.core.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetContentPacket;
import rotp.core.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetDataPacket;
import rotp.core.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetSlotPacket;
import rotp.core.subsystems.entity_grab.TrSetGrabbedEntityPacket;
import rotp.core.subsystems.entity_possessionv2.TrPossessEntityPacket;
import rotp.core.subsystems.entity_puppetcontrol.SetClientControllerPacket;
import rotp.core.subsystems.entity_puppetcontrol.client.mob.ClControlledMobCommandPacket;
import rotp.core.subsystems.entity_puppetcontrol.client.mob.ClMobControlMovementPacket;
import rotp.core.subsystems.entity_puppetcontrol.client.stand.ClStandManualMovementPacket;
import rotp.core.subsystems.movement_input_sync.ClPlayerMovementInputPacket;
import rotp.core.subsystems.movement_input_sync.TrPlayerMovementInputPacket;
import rotp.core.subsystems.entity_useitem.ClStandClickPacket;
import rotp.core.subsystems.itemtracking.TrackedItemPacket;
import rotp.core.impl.stands.crazydiamond.brokenblocks.BrokenChunkBlocksPacket;
import rotp.core.impl.stands.crazydiamond.brokenblocks.CDBlocksRestoredPacket;
import rotp.core.impl.powers.hamon.ClHamonAbandonButtonPacket;
import rotp.core.impl.powers.hamon.ClHamonDoubleShiftPressPacket;
import rotp.core.impl.powers.hamon.ClHamonInteractAskTeacherPacket;
import rotp.core.impl.powers.hamon.ClHamonInteractTeachPacket;
import rotp.core.impl.powers.hamon.ClHamonMeditationPacket;
import rotp.core.impl.powers.hamon.ClHamonPickTechniquePacket;
import rotp.core.impl.powers.hamon.ClHamonResetSkillsButtonPacket;
import rotp.core.impl.powers.hamon.ClHamonStopWallClimbPacket;
import rotp.core.impl.powers.hamon.ClHamonWallClimbMovementPacket;
import rotp.core.impl.powers.hamon.ClHamonWindowOpenedPacket;
import rotp.core.impl.powers.hamon.HamonExercisesPacket;
import rotp.core.impl.powers.hamon.HamonStatFeedbackPacket;
import rotp.core.impl.powers.hamon.HamonTeachersSkillsPacket;
import rotp.core.impl.powers.hamon.HamonUiEffectPacket;
import rotp.core.impl.powers.hamon.TrHamonLiquidWalkingPacket;
import rotp.core.impl.powers.hamon.TrHamonMeditationPacket;
import rotp.core.impl.powers.hamon.TrHamonEntityChargePacket;
import rotp.core.impl.powers.hamon.TrHamonSyncPlayerLearnerPacket;
import rotp.core.impl.powers.hamon.TrHamonTeacherScreenPacket;
import rotp.core.impl.powers.hamon.TrHamonWallClimbingPacket;
import rotp.core.impl.powers.hamon.TrHamonWallClimbMovementPacket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PacketsRegister {
	public static final String NETWORK_PROTOCOL_VERSION = "5";

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(NETWORK_PROTOCOL_VERSION);
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClAbilityInputPacket.Handler(JojoMod.resLoc("clkey")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClNoParamsPacket.Handler(JojoMod.resLoc("clsignal")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClGELifeformButtonPacket.Handler(JojoMod.resLoc("cl_ge_lifeform")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClGEMetLifeformPacket.Handler(JojoMod.resLoc("cl_ge_met_lifeform")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClGELifeformUiPacket.Handler(JojoMod.resLoc("cl_ge_lifeform_ui")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClAngeloRockButtonPacket.Handler(JojoMod.resLoc("cl_angelo_rock_button")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClRPSGameInputPacket.Handler(JojoMod.resLoc("cl_rps_game_input")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClRPSPickThoughtsPacket.Handler(JojoMod.resLoc("cl_rps_pick_thoughts")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClAimTargetPacket.Handler(JojoMod.resLoc("clientaim")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClLearnSkillPacket.Handler(JojoMod.resLoc("cllearnskill")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonPickTechniquePacket.Handler(JojoMod.resLoc("cl_hamon_pick_technique")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonResetSkillsButtonPacket.Handler(JojoMod.resLoc("cl_hamon_reset_skills")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonWindowOpenedPacket.Handler(JojoMod.resLoc("cl_hamon_window_opened")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonMeditationPacket.Handler(JojoMod.resLoc("cl_hamon_meditation")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonAbandonButtonPacket.Handler(JojoMod.resLoc("cl_hamon_abandon")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonDoubleShiftPressPacket.Handler(JojoMod.resLoc("cl_hamon_double_shift")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonInteractAskTeacherPacket.Handler(JojoMod.resLoc("cl_hamon_interact_ask_teacher")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonInteractTeachPacket.Handler(JojoMod.resLoc("cl_hamon_interact_teach")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonStopWallClimbPacket.Handler(JojoMod.resLoc("cl_hamon_stop_wall_climb")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClHamonWallClimbMovementPacket.Handler(JojoMod.resLoc("cl_hamon_wall_climb_moving")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClSetStandSkinPacket.Handler(JojoMod.resLoc("clskin")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClBroadcastedModSettingsPacket.Handler(JojoMod.resLoc("clbroadcastedsettings")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClStandManualMovementPacket.Handler(JojoMod.resLoc("clstandmove")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClMobControlMovementPacket.Handler(JojoMod.resLoc("clmobctrlmove")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClControlledMobCommandPacket.Handler(JojoMod.resLoc("clmobitemslot")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClStandClickPacket.Handler(JojoMod.resLoc("clstandclick")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClDebugCommandPacket.Handler(JojoMod.resLoc("cldebug")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClStandItemInputPacket.Handler(JojoMod.resLoc("clstanditem")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClSetSewingMachineItemPacket.Handler(JojoMod.resLoc("clsewingitem")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClExtendedContainerClickPacket.Handler(JojoMod.resLoc("clslotclick")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClSoulRotationPacket.Handler(JojoMod.resLoc("clsoulrot")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClRemovePlayerSoulEntityPacket.Handler(JojoMod.resLoc("clsoulskip")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClWalkmanControlsPacket.Handler(JojoMod.resLoc("clwalkman")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClPhotoAssignIdPacket.Handler(JojoMod.resLoc("clphotoid")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClPhotoSaveDataPacket.Handler(JojoMod.resLoc("clphotosave")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClPhotoRequestPacket.Handler(JojoMod.resLoc("clphotorequest")));
		registerPacket(registrar, PayloadRegistrar::playToServer, new ClPlayerMovementInputPacket.Handler(JojoMod.resLoc("clmovinput")));

		registerPacket(registrar, PayloadRegistrar::playToClient, new DatapackStandsPacket.Handler(JojoMod.resLoc("datastands")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new CommonConfigPacket.Handler(JojoMod.resLoc("commonconfig")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ResetSyncedCommonConfigPacket.Handler(JojoMod.resLoc("resetcommonconfig")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrAbilityUsePacket.Handler(JojoMod.resLoc("abilityuse")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrEntityActionInstancePacket.Handler(JojoMod.resLoc("action")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrActionSynchedDataPacket.Handler(JojoMod.resLoc("actiondata")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrEntityActionPhaseTimePacket.Handler(JojoMod.resLoc("actionphase")));
        registerPacket(registrar, PayloadRegistrar::playToClient, new TrEntityActionWithOBBSyncPacket.Handler(JojoMod.resLoc("obbsync")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new StandExpPacket.Handler(JojoMod.resLoc("standxp")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPowerStandInstancePacket.Handler(JojoMod.resLoc("standinst")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new StandFullClearPacket.Handler(JojoMod.resLoc("standfullclear")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPowerTypePacket.Handler(JojoMod.resLoc("plpowertype")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPowerDataPacket.Handler(JojoMod.resLoc("powerdata")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPlayerPowerLeapCooldownPacket.Handler(JojoMod.resLoc("playerleapcd")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPlayerCoffinSleepPacket.Handler(JojoMod.resLoc("coffinsleep")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonLiquidWalkingPacket.Handler(JojoMod.resLoc("tr_hamon_liquid_walking")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonEntityChargePacket.Handler(JojoMod.resLoc("tr_hamon_entity_charge")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonMeditationPacket.Handler(JojoMod.resLoc("tr_hamon_meditation")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonWallClimbingPacket.Handler(JojoMod.resLoc("tr_hamon_wall_climbing")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonWallClimbMovementPacket.Handler(JojoMod.resLoc("tr_hamon_wall_climb_moving")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonSyncPlayerLearnerPacket.Handler(JojoMod.resLoc("tr_hamon_sync_player_learner")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrHamonTeacherScreenPacket.Handler(JojoMod.resLoc("tr_hamon_teacher_screen")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new HamonTeachersSkillsPacket.Handler(JojoMod.resLoc("hamon_teachers_skills")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new HamonExercisesPacket.Handler(JojoMod.resLoc("hamon_exercises")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new HamonStatFeedbackPacket.Handler(JojoMod.resLoc("hamon_stat_feedback")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new HamonUiEffectPacket.Handler(JojoMod.resLoc("hamon_ui_effect")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrSetStandEntityPacket.Handler(JojoMod.resLoc("standentity")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrNonEntityStandSummonPacket.Handler(JojoMod.resLoc("nestandsummon")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrEntityCustomEffectsPacket.Handler(JojoMod.resLoc("standeffect")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrStandEffectSynchedDataPacket.Handler(JojoMod.resLoc("steffdata")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrStaminaPacket.Handler(JojoMod.resLoc("stamina")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrStandLeapCooldownPacket.Handler(JojoMod.resLoc("standleapcd")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrStandAbilityCooldownPacket.Handler(JojoMod.resLoc("standabilitycd")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new SoulSpawnPacket.Handler(JojoMod.resLoc("soulspawn")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrResolvePacket.Handler(JojoMod.resLoc("resolve")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ResolveBoostsPacket.Handler(JojoMod.resLoc("resolveboost")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new StandAwakeningDataPacket.Handler(JojoMod.resLoc("standawake")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrAimTargetPacket.Handler(JojoMod.resLoc("aim")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPlayerModSettingsPacket.Handler(JojoMod.resLoc("trplayersettings")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new PlayVoiceLinePacket.Handler(JojoMod.resLoc("voiceline")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new PhotoIdAssignedPacket.Handler(JojoMod.resLoc("photoid")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new PhotoDataPacket.Handler(JojoMod.resLoc("photodata")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new PhotoForOtherPlayerPacket.Handler(JojoMod.resLoc("photoother")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrTimeStopInstancePacket.Handler(JojoMod.resLoc("trtimestopinstance")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrTimeStopPlayerStatePacket.Handler(JojoMod.resLoc("trtimestopplayerstate")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrRefreshMovementInTimeStopPacket.Handler(JojoMod.resLoc("trrefreshmovementintimestop")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrGELifeformStatePacket.Handler(JojoMod.resLoc("gelifeformstate")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrGESplitConsciousnessPacket.Handler(JojoMod.resLoc("ge_split_consciousness")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrGEStuckObjectsPacket.Handler(JojoMod.resLoc("gestuck")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrDyingBodyTimerPacket.Handler(JojoMod.resLoc("dyingbodytimer")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrStandSkinPacket.Handler(JojoMod.resLoc("standskin")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new StandSkinSoundPacket.Handler(JojoMod.resLoc("standsound")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new StandEntitySoundPacket.Handler(JojoMod.resLoc("standsound2")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrBarrageHitSoundPacket.Handler(JojoMod.resLoc("barragehitsound")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new RPSGameStatePacket.Handler(JojoMod.resLoc("rps_game_state")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new RPSOpponentPickThoughtsPacket.Handler(JojoMod.resLoc("rps_opponent_pick_thoughts")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrSyncStandOffsetPacket.Handler(JojoMod.resLoc("standoffset")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrSetGrabbedEntityPacket.Handler(JojoMod.resLoc("grab")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPlayerMovementInputPacket.Handler(JojoMod.resLoc("movinput")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrDirectEntityDataPacket.Handler(JojoMod.resLoc("directdata")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrDirectEntityPosPacket.Handler(JojoMod.resLoc("directpos")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new SetClientControllerPacket.Handler(JojoMod.resLoc("ctrltarget")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrPossessEntityPacket.Handler(JojoMod.resLoc("possess")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new CustomExplosionPacket.Handler(JojoMod.resLoc("expl")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new BrokenBlocksParticlesAndSoundsPacket.Handler(JojoMod.resLoc("blbreak")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrackedItemPacket.Handler(JojoMod.resLoc("itemtrack")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrClothesItemsPacket.Handler(JojoMod.resLoc("clothes")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ExternalContainerOpenPacket.Handler(JojoMod.resLoc("extcopen")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ExternalContainerClosePacket.Handler(JojoMod.resLoc("extcclose")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ExternalContainerSyncSetSlotPacket.Handler(JojoMod.resLoc("extcslot")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ExternalContainerSyncSetContentPacket.Handler(JojoMod.resLoc("extccont")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ExternalContainerSyncSetDataPacket.Handler(JojoMod.resLoc("extcdata")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new EntitySyncMotionBypassingPacket.Handler(JojoMod.resLoc("motfix")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new TrResetDeathTimePacket.Handler(JojoMod.resLoc("undeath")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new DeflectedBulletPacket.Handler(JojoMod.resLoc("projdefl")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new BloodParticlesPacket.Handler(JojoMod.resLoc("blood")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new BrokenChunkBlocksPacket.Handler(JojoMod.resLoc("brokenblocks")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new CDBlocksRestoredPacket.Handler(JojoMod.resLoc("restoreblocks")));
		registerPacket(registrar, PayloadRegistrar::playToClient, new ItemBreakVisualsPacket.Handler(JojoMod.resLoc("itemparticle")));
	}

	
	public static interface PacketHandler<T extends CustomPacketPayload> {
		CustomPacketPayload.Type<T> type();
		void handle(T payload, IPayloadContext context);
	}
	
	public static interface PacketOGHandler<T extends CustomPacketPayload> extends PacketHandler<T> {
		void encode(T packet, RegistryFriendlyByteBuf buf);
		T decode(RegistryFriendlyByteBuf buf);
	}
	
	public static interface PacketCodecHandler<T extends CustomPacketPayload> extends PacketHandler<T> {
		StreamCodec<? super RegistryFriendlyByteBuf, T> reader();
	}
	
	public static <T extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar, PacketType packetType, PacketOGHandler<T> handler) {
		packetType.register(registrar, handler.type(), StreamCodec.ofMember(handler::encode, handler::decode), handler::handle);
	}
	
	public static <T extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar, PacketType packetType, PacketCodecHandler<T> handler) {
		packetType.register(registrar, handler.type(), handler.reader(), handler::handle);
	}
	
	@FunctionalInterface
	public static interface PacketType {
		<T extends CustomPacketPayload> void register(PayloadRegistrar registrar, 
				CustomPacketPayload.Type<T> type, 
				StreamCodec<? super RegistryFriendlyByteBuf, T> reader, 
				IPayloadHandler<T> handler);
	}
}
