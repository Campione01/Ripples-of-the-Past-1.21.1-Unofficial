package com.github.standobyte.jojo;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.explosion.CustomExplosionPacket;
import com.github.standobyte.jojo.entityattachment.custom_effect.TrEntityCustomEffectsPacket;
import com.github.standobyte.jojo.entityattachment.custom_effect.sync.TrStandEffectSynchedDataPacket;
import com.github.standobyte.jojo.mechanics.clothes.TrClothesItemsPacket;
import com.github.standobyte.jojo.mechanics.clothes.sewing.ClSetSewingMachineItemPacket;
import com.github.standobyte.jojo.mechanics.resolve.ResolveBoostsPacket;
import com.github.standobyte.jojo.mechanics.resolve.TrResolvePacket;
import com.github.standobyte.jojo.network.c2s.ClAbilityInputPacket;
import com.github.standobyte.jojo.network.c2s.ClAimTargetPacket;
import com.github.standobyte.jojo.network.c2s.ClAngeloRockButtonPacket;
import com.github.standobyte.jojo.network.c2s.ClBroadcastedModSettingsPacket;
import com.github.standobyte.jojo.network.c2s.ClDebugCommandPacket;
import com.github.standobyte.jojo.network.c2s.ClGELifeformButtonPacket;
import com.github.standobyte.jojo.network.c2s.ClGEMetLifeformPacket;
import com.github.standobyte.jojo.network.c2s.ClGELifeformUiPacket;
import com.github.standobyte.jojo.network.c2s.ClNoParamsPacket;
import com.github.standobyte.jojo.network.c2s.ClPhotoAssignIdPacket;
import com.github.standobyte.jojo.network.c2s.ClPhotoRequestPacket;
import com.github.standobyte.jojo.network.c2s.ClPhotoSaveDataPacket;
import com.github.standobyte.jojo.network.c2s.ClRPSGameInputPacket;
import com.github.standobyte.jojo.network.c2s.ClRPSPickThoughtsPacket;
import com.github.standobyte.jojo.network.c2s.ClRemovePlayerSoulEntityPacket;
import com.github.standobyte.jojo.network.c2s.ClSetStandSkinPacket;
import com.github.standobyte.jojo.network.c2s.ClSoulRotationPacket;
import com.github.standobyte.jojo.network.c2s.ClWalkmanControlsPacket;
import com.github.standobyte.jojo.network.s2c.BloodParticlesPacket;
import com.github.standobyte.jojo.network.s2c.BrokenBlocksParticlesAndSoundsPacket;
import com.github.standobyte.jojo.network.s2c.CommonConfigPacket;
import com.github.standobyte.jojo.network.s2c.DatapackStandsPacket;
import com.github.standobyte.jojo.network.s2c.DeflectedBulletPacket;
import com.github.standobyte.jojo.network.s2c.EntitySyncMotionBypassingPacket;
import com.github.standobyte.jojo.network.s2c.ItemBreakVisualsPacket;
import com.github.standobyte.jojo.network.s2c.PhotoDataPacket;
import com.github.standobyte.jojo.network.s2c.PhotoForOtherPlayerPacket;
import com.github.standobyte.jojo.network.s2c.PhotoIdAssignedPacket;
import com.github.standobyte.jojo.network.s2c.PlayVoiceLinePacket;
import com.github.standobyte.jojo.network.s2c.RPSGameStatePacket;
import com.github.standobyte.jojo.network.s2c.RPSOpponentPickThoughtsPacket;
import com.github.standobyte.jojo.network.s2c.ResetSyncedCommonConfigPacket;
import com.github.standobyte.jojo.network.s2c.SoulSpawnPacket;
import com.github.standobyte.jojo.network.s2c.StandEntitySoundPacket;
import com.github.standobyte.jojo.network.s2c.StandFullClearPacket;
import com.github.standobyte.jojo.network.s2c.StandSkinSoundPacket;
import com.github.standobyte.jojo.network.s2c.TrAbilityUsePacket;
import com.github.standobyte.jojo.network.s2c.TrAimTargetPacket;
import com.github.standobyte.jojo.network.s2c.TrBarrageHitSoundPacket;
import com.github.standobyte.jojo.network.s2c.TrDirectEntityDataPacket;
import com.github.standobyte.jojo.network.s2c.TrDirectEntityPosPacket;
import com.github.standobyte.jojo.network.s2c.TrDyingBodyTimerPacket;
import com.github.standobyte.jojo.network.s2c.TrGELifeformStatePacket;
import com.github.standobyte.jojo.network.s2c.TrGESplitConsciousnessPacket;
import com.github.standobyte.jojo.network.s2c.TrGEStuckObjectsPacket;
import com.github.standobyte.jojo.network.s2c.TrNonEntityStandSummonPacket;
import com.github.standobyte.jojo.network.s2c.TrPlayerCoffinSleepPacket;
import com.github.standobyte.jojo.network.s2c.TrPlayerModSettingsPacket;
import com.github.standobyte.jojo.network.s2c.TrPowerDataPacket;
import com.github.standobyte.jojo.network.s2c.TrTimeStopInstancePacket;
import com.github.standobyte.jojo.network.s2c.TrTimeStopPlayerStatePacket;
import com.github.standobyte.jojo.network.s2c.TrPowerStandInstancePacket;
import com.github.standobyte.jojo.network.s2c.TrPowerTypePacket;
import com.github.standobyte.jojo.network.s2c.TrRefreshMovementInTimeStopPacket;
import com.github.standobyte.jojo.network.s2c.TrResetDeathTimePacket;
import com.github.standobyte.jojo.network.s2c.TrSetStandEntityPacket;
import com.github.standobyte.jojo.network.s2c.TrStandSkinPacket;
import com.github.standobyte.jojo.network.s2c.TrSyncStandOffsetPacket;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.TrEntityActionWithOBBSyncPacket;
import com.github.standobyte.jojo.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;
import com.github.standobyte.jojo.powersystem.playerpower.packet.TrPlayerPowerLeapCooldownPacket;
import com.github.standobyte.jojo.powersystem.standpower.StandAwakeningDataPacket;
import com.github.standobyte.jojo.powersystem.standpower.packet.StandExpPacket;
import com.github.standobyte.jojo.powersystem.standpower.packet.TrStandAbilityCooldownPacket;
import com.github.standobyte.jojo.powersystem.standpower.packet.TrStandLeapCooldownPacket;
import com.github.standobyte.jojo.powersystem.standpower.packet.TrStaminaPacket;
import com.github.standobyte.jojo.powersystem.unlockableskill.ClLearnSkillPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer._stand.input.ClStandItemInputPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ClExtendedContainerClickPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerClosePacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerOpenPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetContentPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetDataPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetSlotPacket;
import com.github.standobyte.jojo.subsystems.entity_grab.TrSetGrabbedEntityPacket;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.TrPossessEntityPacket;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.SetClientControllerPacket;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.mob.ClControlledMobCommandPacket;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.mob.ClMobControlMovementPacket;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.stand.ClStandManualMovementPacket;
import com.github.standobyte.jojo.subsystems.movement_input_sync.ClPlayerMovementInputPacket;
import com.github.standobyte.jojo.subsystems.movement_input_sync.TrPlayerMovementInputPacket;
import com.github.standobyte.jojo.subsystems.entity_useitem.ClStandClickPacket;
import com.github.standobyte.jojo.subsystems.itemtracking.TrackedItemPacket;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.BrokenChunkBlocksPacket;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.CDBlocksRestoredPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonAbandonButtonPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonDoubleShiftPressPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonInteractAskTeacherPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonInteractTeachPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonMeditationPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonPickTechniquePacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonResetSkillsButtonPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonStopWallClimbPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonWallClimbMovementPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonWindowOpenedPacket;
import com.github.standobyte.jojoimpl.powers.hamon.HamonExercisesPacket;
import com.github.standobyte.jojoimpl.powers.hamon.HamonStatFeedbackPacket;
import com.github.standobyte.jojoimpl.powers.hamon.HamonTeachersSkillsPacket;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUiEffectPacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonLiquidWalkingPacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonMeditationPacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonEntityChargePacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonSyncPlayerLearnerPacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonTeacherScreenPacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonWallClimbingPacket;
import com.github.standobyte.jojoimpl.powers.hamon.TrHamonWallClimbMovementPacket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PacketsRegister {
	public static final String NETWORK_PROTOCOL_VERSION = "4";

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
