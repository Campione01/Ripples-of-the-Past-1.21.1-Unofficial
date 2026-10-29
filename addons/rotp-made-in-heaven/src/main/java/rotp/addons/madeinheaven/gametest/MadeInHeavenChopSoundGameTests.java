package rotp.addons.madeinheaven.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.addons.madeinheaven.entity.MadeInHeavenStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.network.s2c.StandSkinSoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopSoundGameTests {
    private static final short ATTACK=206,PRIME=207;
    private static final PowerSkillUnlocks.Owner UNLOCKS=PowerSkillUnlocks.register(MadeInHeavenAddon.id("chop_sound_tests"));
    private static Suite active;
    private enum Kind { HIT,CANCELED,REFUSED,IMMUNE,ZERO_M,KILL,BOAT,BOAT_REFUSED,BLOCK,MISS,ORDINARY,REFUSED_ENTRY,SOUND_EVENT_CANCEL,NULL_REVIVER }
    private record Cue(int order,Holder<SoundEvent> sound,SoundSource category,Vec3 position,float volume,float pitch,boolean canceled) {}
    private record Sent(int order,StandSkinSoundPacket payload,String sender) {}
    private static final class Hurt {final int before;final float amount,health;boolean result;int after;Hurt(Scene scene,float amount){before=++scene.order;this.amount=amount;health=((LivingEntity)scene.target).getHealth();}}
    private MadeInHeavenChopSoundGameTests() {}
    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=1800,batch="mihChopSound15")
    public static void chopLivingImpactFollowsPrimaryHurtWithoutChangingOtherSoundRoles(GameTestHelper helper){Suite suite=new Suite(helper);try{identity(helper,MadeInHeavenAddon.class,MadeInHeavenChopAbility.class,MadeInHeavenChopAbility.MadeInHeavenChop.class,MadeInHeavenStandEntity.class,StandEntityHeavyPunchAbility.class,StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class,StandEntity.class,StandUtil.class,AbilityInput.class,StandSkinSoundPacket.class,PlayVoiceLinePacket.class,ServerPlayer.class,PlayerList.class,ServerGamePacketListenerImpl.class,PlayLevelSoundEvent.AtPosition.class,MadeInHeavenChopSoundGameTests.class,ObservedCow.class,ObservedBoat.class,CapturingListener.class);suite.listen();helper.onEachTick(suite::tick);}catch(RuntimeException|Error failure){throwCleanup(suite.closeInto(failure));}}
    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=800,batch="mihChopSound15T1")
    public static void soundFixtureExceptionalCleanupPreservesCauseAndOwnedResources(GameTestHelper helper){identity(helper,MadeInHeavenChopSoundGameTests.class,Suite.class,Scene.class,CleanupProbe.class,UnregisterProbe.class,RemovalProbePlayer.class,RemovalProbeCow.class);CleanupProbe probe=new CleanupProbe(helper);helper.testInfo.addListener(probe);helper.onEachTick(probe::tick);}
    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=600,batch="mihChopSound15T1R1")
    public static void soundFixturePartialFallbackRetainsOwnedRetryAndInitialCause(GameTestHelper helper){identity(helper,CleanupProbe.class,FallbackProbe.class,FallbackListener.class);FallbackProbe probe=new FallbackProbe(helper);helper.testInfo.addListener(probe);helper.onEachTick(probe::tick);}
    @AfterBatch(batch="mihChopSound15")
    public static void release(ServerLevel level){if(active!=null)active.close();log("BATCH-CLEANUP active="+(active!=null));}
    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;final List<Object> listeners=new ArrayList<>();final List<String> failures=new ArrayList<>();
        final ModConfigSpec.DoubleValue multiplier=(ModConfigSpec.DoubleValue)JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings","standPowerMultiplier"));final double originalMultiplier;
        Scene scene;RuntimeException observerFailure;int index,checks;boolean closed,multiplierRestored;
        Suite(GameTestHelper helper){helper.assertTrue(active==null,"Overlapping sound suite");this.helper=helper;originalMultiplier=multiplier.get();active=this;helper.testInfo.addListener(this);}
        void listen(){Consumer<EntityTickEvent.Pre> pre=e->{if(scene!=null&&!closed)try{scene.pre(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};Consumer<EntityTickEvent.Post> post=e->{if(scene!=null&&!closed)try{scene.post(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};Consumer<LivingIncomingDamageEvent> damage=e->{if(scene!=null&&!closed)scene.incoming(e);};Consumer<PlayLevelSoundEvent.AtPosition> sound=e->{if(scene!=null&&!closed)scene.sound(e);};listeners.addAll(List.of(pre,post,damage,sound));NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Post.class,post);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,damage);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,true,PlayLevelSoundEvent.AtPosition.class,sound);}
        void tick(){if(closed)return;try{if(observerFailure!=null)throw observerFailure;if(scene!=null&&scene.done){scene.close();scene=null;}if(scene==null){if(index==Kind.values().length){log("SUMMARY fix=15 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"MIH living Chop impact sound gate failed; see SUMMARY");helper.succeed();return;}scene=new Scene(this,Kind.values()[index++]);scene.prepare();}scene.tick();helper.assertTrue(++scene.ticks<100,"Natural sound scene timeout");}catch(RuntimeException|Error failure){throwCleanup(closeInto(failure));}}
        void check(boolean ok,String detail){checks++;log((ok?"PASS ":"FAIL ")+scene.kind+" "+detail);if(!ok)failures.add(scene.kind+": "+detail);}
        void close(){throwCleanup(closeInto(null));}
        Throwable closeInto(Throwable failure){
            if(closed&&listeners.isEmpty()&&(scene==null||scene.cleanupComplete)&&multiplierRestored)return failure;
            closed=true;
            for(Object listener:new ArrayList<>(listeners))failure=attemptCleanup(failure,()->{NeoForge.EVENT_BUS.unregister(listener);listeners.remove(listener);});
            if(scene!=null)failure=scene.closeInto(failure);
            failure=attemptCleanup(failure,()->{multiplier.set(originalMultiplier);multiplierRestored=Double.compare(multiplier.get(),originalMultiplier)==0;});
            if(listeners.isEmpty()&&(scene==null||scene.cleanupComplete)&&multiplierRestored&&active==this)active=null;
            return attemptCleanup(failure,()->log("CLEANUP actors="+(scene==null?0:scene.remainingActors())+" listeners="+listeners.size()+" M="+multiplier.get()+" sceneComplete="+(scene==null||scene.cleanupComplete)+" multiplierRestored="+multiplierRestored));
        }
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }
    private static final class Scene implements AutoCloseable {
        final Suite suite;final Kind kind;final ServerLevel level;final List<Player> ownedUsers=new ArrayList<>();final List<Actor> actors=new ArrayList<>();final List<Cue> cues=new ArrayList<>();final List<Sent> sends=new ArrayList<>();final List<PlayVoiceLinePacket> voices=new ArrayList<>();
        Actor caster,primer;Entity target;Vec3 targetOrigin,impactPosition;ServerPlayer recipient;CapturingListener listener;EmbeddedChannel channel;List<ServerPlayer> ownedMembership;int originalPlayers;
        EntityActionInstance action,primeAction;Hurt hurt;BlockPos block;BlockState originalBlock;float beforeStamina,healthBefore;int ticks,order,debits,incoming,nonlivingBefore,nonlivingAfter;boolean nonlivingResult,observing,done,closed,cleanupComplete;
        Scene(Suite suite,Kind kind){this.suite=suite;this.kind=kind;level=suite.helper.getLevel();originalPlayers=level.getServer().getPlayerList().getPlayers().size();}
        boolean living(){return !List.of(Kind.BOAT,Kind.BOAT_REFUSED,Kind.BLOCK,Kind.MISS).contains(kind);}
        boolean acceptedHurt(){return List.of(Kind.HIT,Kind.ZERO_M,Kind.KILL,Kind.ORDINARY,Kind.SOUND_EVENT_CANCEL).contains(kind);}
        void prepare(){suite.multiplier.set(kind==Kind.ZERO_M?0D:1D);ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.37,suite.helper.absolutePos(BlockPos.ZERO).getY()+8.2,chunk.getMiddleBlockZ()+0.43);caster=actor(origin,28);targetOrigin=origin.add(0,0,1.5);
            if(kind==Kind.BLOCK){block=BlockPos.containing(targetOrigin.add(0,1,0));originalBlock=level.getBlockState(block);level.setBlock(block,Blocks.STONE.defaultBlockState(),3);impactPosition=Vec3.atCenterOf(block);premise(level.getBlockState(block).getDestroySpeed(level,block)>0,"real nonzero-hardness stone control");}
            else if(kind!=Kind.MISS){if(kind==Kind.BOAT||kind==Kind.BOAT_REFUSED)target=new ObservedBoat(this);else{ObservedCow cow=new ObservedCow(this);cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(kind==Kind.KILL?1:200);target=cow;}target.setNoGravity(true);target.noPhysics=true;target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,0,0);premise(level.addFreshEntity(target),"actual observed target added");if(kind==Kind.REFUSED||kind==Kind.BOAT_REFUSED)target.setInvulnerable(true);impactPosition=target.getBoundingBox().getCenter();}
            GameProfile profile=new GameProfile(UUID.randomUUID(),"mih-sound-recipient");recipient=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());Connection connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);listener=new CapturingListener(level.getServer(),connection,recipient,CommonListenerCookie.createInitial(profile,false),this);recipient.moveTo(origin.x+2,origin.y,origin.z-1,0,0);level.addNewPlayer(recipient);recipient.setGameMode(GameType.SURVIVAL);recipient.setNoGravity(true);recipient.noPhysics=true;
            StandPower recipientPower=PowerClass.STAND.attachGet(recipient);premise(StandPowerTransitions.insert(recipientPower,new StandInstance(MadeInHeavenAddon.MADE_IN_HEAVEN.get())).status()==StandPowerTransitions.Status.APPLIED,"actual recipient Stand-user hearing eligibility");joinOwnedPlayerList();premise(level.getEntity(recipient.getUUID())==recipient&&level.getServer().getPlayerList().getPlayers().contains(recipient)&&StandUtil.entityCanHearStands(recipient),"actual native recipient world/list/hearing membership before admission");
        }
        @SuppressWarnings("unchecked") void joinOwnedPlayerList(){PlayerList playerList=level.getServer().getPlayerList();originalPlayers=playerList.getPlayers().size();try{ownedMembership=playerList.getPlayers();ownedMembership.add(recipient);log("RECIPIENT-MEMBERSHIP kind="+kind+" source=publicMutableGetter");}catch(UnsupportedOperationException immutable){try{Field field=PlayerList.class.getDeclaredField("players");field.setAccessible(true);ownedMembership=(List<ServerPlayer>)field.get(playerList);ownedMembership.add(recipient);log("RECIPIENT-MEMBERSHIP kind="+kind+" publicGetterImmutable=true field=net.minecraft.server.players.PlayerList.players reason=fixtureOwnedNativeSoundRecipient");}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}premise(playerList.getPlayers().size()==originalPlayers+1,"only fresh owned player added to actual native list");}
        void tick(){if(action!=null||done||ticks<3)return;if(kind==Kind.IMMUNE){if(primer==null){primer=actor(caster.origin.add(-0.5,0,0),32);primer.stand.setFinisherMeter(0.25F);LivingComponentAction.getComponent(primer.stand).entityAim.setTarget(new ActionTarget(target));primeAction=press(primer,PRIME,true);return;}return;}begin();}
        void begin(){if(kind!=Kind.ORDINARY){var unlocked=UNLOCKS.forceUnlock(caster.power,"chop");premise(unlocked==PowerSkillUnlocks.Result.UNLOCKED||unlocked==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"actual public Chop unlock");}caster.stand.setFinisherMeter(kind==Kind.ORDINARY?0.25F:0.75F);caster.power.setStamina(kind==Kind.REFUSED_ENTRY?49:caster.power.getMaxStamina());caster.stand.updateStrengthMultipliers();ActionTarget aim=kind==Kind.BLOCK?new ActionTarget(block,Direction.NORTH):kind==Kind.MISS?ActionTarget.EMPTY:new ActionTarget(target);LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(aim);
            // the crosshair a client sends: 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget),
            // and the strike aimed again with the precision ray from the user's eyes, so the user looks at the boat
            // (pre, with the actors' pin on every tick)
            LivingComponentAction.getComponent(caster.user).entityAim.setTarget(aim);
            premise(caster.power.getStamina()==(kind==Kind.REFUSED_ENTRY?49:caster.power.getMaxStamina())&&!caster.power.isStaminaInfinite(),"actual finite/effective stamina before registered entry");if(target!=null){premise(caster.stand.canAttackEntity(target)&&StandEntityPunchAbility.validatePunchTarget(caster.stand,aim).getMainEntity()==target,"real target/melee admission before attack");if(target instanceof LivingEntity living)premise(StandEntity.isStandAttackInvulnerable(living)==(kind==Kind.IMMUNE),"actual canonical RAW immunity before attack");}
            observing=true;order=0;healthBefore=target instanceof LivingEntity living?living.getHealth():0;action=press(caster,ATTACK,kind!=Kind.REFUSED_ENTRY);if(kind==Kind.REFUSED_ENTRY){suite.check(action==null&&cues.isEmpty()&&sends.isEmpty()&&voices.isEmpty(),"refused finite49 input emits no impact or voice and starts no action");suite.check(caster.stand.getCurStandAction()==null,"refused input does not install a native action");done=true;return;}premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop)==(kind!=Kind.ORDINARY),"actual registered Chop/ordinary factory");log("PRESS kind="+kind+" world="+level.getGameTime()+" actor="+caster.user.getUUID()+" stand="+caster.stand.getUUID()+" recipient="+recipient.getUUID()+" target="+(target==null?kind:target.getUUID())+" selected="+((Ability)action.ability).name()+" stamina="+caster.power.getStamina()+" soundRadius="+MadeInHeavenAddon.MIH_CHOP.value().getRange(1));}
        void pre(Entity entity){if(caster==null||entity!=caster.stand||done||closed)return;for(Actor actor:actors){actor.user.moveTo(actor.origin.x,actor.origin.y,actor.origin.z,0,0);actor.stand.moveTo(actor.origin.x,actor.origin.y,actor.origin.z,0,0);actor.stand.setDeltaMovement(Vec3.ZERO);actor.power.setStamina(actor.power.getMaxStamina());actor.stand.updateStrengthMultipliers();}if(target!=null&&!target.isRemoved()){target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,0,0);target.setDeltaMovement(Vec3.ZERO);if(kind==Kind.BOAT||kind==Kind.BOAT_REFUSED)caster.user.lookAt(EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());}if(action!=null){beforeStamina=caster.power.getStamina();premise(caster.stand.getAttackSpeed()==28&&caster.stand.getAttackDamage()==15&&beforeStamina==caster.power.getMaxStamina(),"controlled finite max stats before real natural action tick");if(target instanceof LivingEntity living)premise(StandEntity.isStandAttackInvulnerable(living)==(kind==Kind.IMMUNE),"actual canonical target RAW premise before natural tick");}}
        void incoming(LivingIncomingDamageEvent event){if(!observing||target==null||event.getEntity()!=target||event.getSource().getDirectEntity()!=caster.stand)return;incoming++;if(kind==Kind.CANCELED||kind==Kind.NULL_REVIVER)event.setCanceled(true);log("INCOMING kind="+kind+" amount="+event.getAmount()+" canceled="+event.isCanceled()+" order="+(++order));}
        void sound(PlayLevelSoundEvent.AtPosition event){if(!observing||event.getLevel()!=level||impactPosition==null||!near(event.getPosition(),impactPosition))return;ResourceLocation key=key(event.getSound());if(key!=null&&!key.equals(MadeInHeavenAddon.MIH_CHOP.getId())&&!key.equals(JojoMod.resLoc("stand_punch_heavy")))return;if(kind==Kind.NULL_REVIVER&&event.getSound()==null)event.setSound(MadeInHeavenAddon.MIH_CHOP);if(kind==Kind.SOUND_EVENT_CANCEL)event.setCanceled(true);cues.add(new Cue(++order,event.getSound(),event.getSource(),event.getPosition(),event.getNewVolume(),event.getNewPitch(),event.isCanceled()));log("EMISSION kind="+kind+" order="+order+" holder="+key(event.getSound())+" pos="+event.getPosition()+" category="+event.getSource()+" v="+event.getNewVolume()+" p="+event.getNewPitch()+" canceled="+event.isCanceled()+" primaryHurt="+(hurt==null?"notReturned":hurt.result+"@"+hurt.after)+" hp="+(target instanceof LivingEntity living?living.getHealth():"nonliving"));}
        void post(Entity entity){if(closed||done)return;if(primer!=null&&action==null&&entity==primer.stand&&primeAction.punchedTarget!=null){premise(target instanceof LivingEntity living&&StandEntity.isStandAttackInvulnerable(living),"real registered primer established canonical immunity");begin();}if(entity!=caster.stand||action==null||action.punchedTarget==null)return;observing=false;float debit=beforeStamina-caster.power.getStamina();if(debit>0)debits++;premise(debit==50&&debits==1,"one real natural attack debit50");premise(action.punchedTarget.getType()==(kind==Kind.BLOCK?ActionTarget.TargetType.BLOCK:kind==Kind.MISS?ActionTarget.TargetType.EMPTY:ActionTarget.TargetType.ENTITY),"actual selected primary target role");
            if(target instanceof LivingEntity living){if(kind==Kind.IMMUNE)premise(hurt==null&&incoming==0,"canonical RAW veto has no delegated hurt");else premise(hurt!=null&&hurt.result==acceptedHurt(),"qualified actual primary native returned hurt, including M0 true/noHP loss and lethal true");if(kind==Kind.ZERO_M)premise(hurt.amount==0&&living.getHealth()==healthBefore&&hurt.result,"actual positive-RAW postscale0 returns true with no health loss");if(kind==Kind.KILL)premise(living.getHealth()==0&&!living.isAlive()&&hurt.result,"actual successful lethal primary without post-hit alive admission");}
            if(kind==Kind.BOAT||kind==Kind.BOAT_REFUSED)premise(nonlivingAfter>nonlivingBefore&&nonlivingResult==(kind==Kind.BOAT),"actual nonliving native hurt success/refusal control");
            int expected=kind==Kind.IMMUNE||kind==Kind.MISS||kind==Kind.CANCELED||kind==Kind.REFUSED||kind==Kind.NULL_REVIVER||kind==Kind.BLOCK?0:1;
            suite.check(cues.size()==expected,"actual outcome-gated living request or unchanged nonliving/block/miss count expected="+expected+" actual="+cues.size());suite.check(sends.size()==(kind==Kind.SOUND_EVENT_CANCEL?0:expected),"actual native StandSkinSoundPacket recipient send count");
            // 1.16 StandEntity.attackTarget: the ordinary heavy punch also sounds after the hit it landed (core heavy punch)
             boolean delayed=target instanceof LivingEntity&&acceptedHurt();suite.check(cues.stream().allMatch(cue->(!delayed||hurt!=null&&cue.order>hurt.after)&&(!(kind==Kind.BOAT||kind==Kind.BOAT_REFUSED)||cue.order>nonlivingAfter)),"successful Chop and ordinary heavy cue follows actual primary hurt return; 1.16 attackTarget sounded a nonliving target after doHit, hurt or not");
            ResourceLocation expectedHolder=JojoMod.resLoc("stand_punch_heavy");suite.check(cues.stream().allMatch(cue->key(cue.sound).equals(expectedHolder)&&near(cue.position,impactPosition)&&cue.volume==1&&cue.pitch==1&&cue.category==caster.stand.getSoundSource()),"retained exact holder/prestrike primary position/category/volume/pitch");
            suite.check(sends.stream().allMatch(sent->key(sent.payload.sound()).equals(expectedHolder)&&sent.payload.soundCategory()==caster.stand.getSoundSource()&&sent.payload.volume()==1&&sent.payload.pitch()==1&&sent.payload.position().distanceToSqr(impactPosition)<1E-8&&sent.payload.standType().equals(caster.type.getId())&&sent.payload.standSkin().equals(caster.power.getStandInstance().orElseThrow().getSelectedSkin())&&sent.sender.contains("rotp.core.powersystem.standpower.StandUtil.broadcastSound")),"actual producer/send path and unchanged native holder/position/category/type/skin payload");
            suite.check(kind==Kind.ORDINARY?voices.stream().noneMatch(voice->key(voice.sound()).equals(MadeInHeavenAddon.MIH_CHOP_USER.getId())):voices.size()==1&&key(voices.getFirst().sound()).equals(MadeInHeavenAddon.MIH_CHOP_USER.getId())&&voices.getFirst().source()==SoundSource.VOICE&&voices.getFirst().entityId()==caster.user.getId()&&voices.getFirst().volume()==1&&voices.getFirst().pitch()==1&&!voices.getFirst().interrupt(),"existing actual Chop user voice route retained, no manual voice/RNG call");
            suite.check(debit==50&&action.lastDamageDealtToLiving==(target instanceof LivingEntity living?Math.max(healthBefore-living.getHealth(),0):0),"current natural payment/lastDamage retained");if(kind==Kind.BLOCK)suite.check(level.getBlockState(block).is(Blocks.STONE),"unchanged Chop block refusal/no break");suite.check(performSound(action)==null,"1.16 Chop plays MIH_CHOP once when its task is set, not again from the Stand at perform start: "+performSound(action));if(kind!=Kind.ORDINARY)suite.check(cry(action)==MadeInHeavenAddon.MIH_CHOP.value(),"1.16 Chop.onTaskSet MIH_CHOP is the windup cry, not the generic heavy cry "+cry(action));
            log("RESULT kind="+kind+" hurt="+(hurt==null?"none":hurt.amount+"/"+hurt.result+"@"+hurt.before+".."+hurt.after)+" events="+cues+" sends="+sends+" voices="+voices+" debit="+debit+" health="+(target instanceof LivingEntity living?living.getHealth():"nonliving"));done=true;
        }
        Actor actor(Vec3 origin,double speed){StandType type=MadeInHeavenAddon.MADE_IN_HEAVEN.get();Player user=new Player(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-sound-actor")){@Override public boolean isCreative(){return false;}@Override public boolean isSpectator(){return false;}};ownedUsers.add(user);user.setNoGravity(true);user.noPhysics=true;user.getAbilities().invulnerable=false;user.moveTo(origin.x,origin.y,origin.z,0,0);premise(level.addFreshEntity(user),"owned actual user added");StandPower power=PowerClass.STAND.attachGet(user);premise(StandPowerTransitions.insert(power,new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"public actual MIH grant");power.setResolveLevel(power.getMaxResolveLevel());premise(type.summon(user,power),"actual registered final MIH factory");StandEntity stand=power.getSummonedStandEntity();stand.summonLockTicks=0;stand.gradualSummonWeaknessTicks=0;stand.setCanFollowUser(false);stand.setNoGravity(true);stand.noPhysics=true;stand.moveTo(origin.x,origin.y,origin.z,0,0);user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);StandEntity.updateStandStatAttributes(stand,user);power.setStamina(power.getMaxStamina());stand.updateStrengthMultipliers();user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock=true;premise(stand.getClass()==MadeInHeavenStandEntity.class&&stand.getUser()==user&&stand.getUserPower()==power&&stand.getAttackSpeed()==speed&&!power.isStaminaInfinite(),"actual factory/user/effective stat binding");Actor actor=new Actor(user,power,type,stand,origin);actors.add(actor);return actor;}
        EntityActionInstance press(Actor actor,short key,boolean required){Ability base=actor.power.getAbility("heavy_punch");var checked=actor.power.updateAvailableMoves().getContextVariationContainer(base);boolean gate=checked!=null&&AbilityInput.withConditionCheck(checked,actor.user,InputMethod.CLICK);if(!gate){if(required)premise(false,"registered checked heavy input refused");return null;}BufferingState buffer=BufferingState.clickOnly();var entry=AbilityInput.keyPress(key,checked.ability,actor.user,null,InputMethod.CLICK,0,buffer,base.getAbilityId());if(required)premise(buffer.isActionSuccess,"real native keyPress succeeded");return buffer.isActionSuccess?(entry!=null&&entry.action instanceof EntityActionInstance nativeAction?nativeAction:actor.stand.getCurStandAction()):null;}
        Hurt beforeHurt(Entity entity,DamageSource source,float amount){if(!observing||target!=entity||source.getDirectEntity()!=caster.stand)return null;Hurt actual=new Hurt(this,amount);hurt=actual;return actual;}
        void afterHurt(Hurt actual,boolean result){if(actual!=null){actual.result=result;actual.after=++order;log("HURT kind="+kind+" amount="+actual.amount+" nativeResult="+result+" hp="+actual.health+"->"+((LivingEntity)target).getHealth()+" order="+actual.before+".."+actual.after);}}
        void premise(boolean ok,String detail){suite.helper.assertTrue(ok,kind+" premise: "+detail);}
        @Override public void close(){throwCleanup(closeInto(null));}
        Throwable closeInto(Throwable failure){
            if(closed&&cleanupComplete)return failure;
            closed=true;observing=false;boolean[] failed={false};
            for(Player user:ownedUsers){
                failure=attemptCleanup(failure,failed,()->AbilityInput.keyRelease(ATTACK,user));
                failure=attemptCleanup(failure,failed,()->AbilityInput.keyRelease(PRIME,user));
                failure=attemptCleanup(failure,failed,()->{if(user.hasData(ModDataAttachmentTypes.CONTROLLER))user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();});
                failure=attemptCleanup(failure,failed,()->{StandPower power=PowerClass.STAND.get(user);if(power!=null&&power.isSummoned())MadeInHeavenAddon.MADE_IN_HEAVEN.get().forceUnsummon(user,power);});
                failure=attemptCleanup(failure,failed,()->{if(!user.isRemoved())user.discard();});
            }
            for(Actor actor:actors)failure=attemptCleanup(failure,failed,()->{if(!actor.stand.isRemoved())actor.stand.discard();});
            if(target!=null)failure=attemptCleanup(failure,failed,()->{if(!target.isRemoved())target.discard();});
            if(block!=null&&originalBlock!=null)failure=attemptCleanup(failure,failed,()->level.setBlock(block,originalBlock,3));
            if(ownedMembership!=null&&recipient!=null)failure=attemptCleanup(failure,failed,()->ownedMembership.remove(recipient));
            if(recipient!=null)failure=attemptCleanup(failure,failed,()->{if(!recipient.isRemoved())recipient.discard();});
            if(channel!=null)failure=attemptCleanup(failure,failed,()->channel.finishAndReleaseAll());
            failure=attemptCleanup(failure,failed,()->premise(level.getServer().getPlayerList().getPlayers().size()==originalPlayers,"exact owned player membership restored, no foreign list clear"));
            failure=attemptCleanup(failure,failed,()->{cleanupComplete=!failed[0]&&remainingActors()==0&&(target==null||target.isRemoved())&&(block==null||originalBlock==null||level.getBlockState(block).equals(originalBlock))&&(recipient==null||recipient.isRemoved()&&!level.getServer().getPlayerList().getPlayers().contains(recipient))&&(channel==null||!channel.isOpen());log("SCENE-CLEANUP kind="+kind+" ownedWorldUsersRemoved="+ownedUsers.stream().allMatch(Entity::isRemoved)+" actorsRemaining="+remainingActors()+" recipientRemoved="+(recipient==null||recipient.isRemoved())+" actualPlayers="+level.getServer().getPlayerList().getPlayers().size()+" channelOpen="+(channel!=null&&channel.isOpen())+" complete="+cleanupComplete);});if(failed[0])cleanupComplete=false;return failure;
        }
        long remainingActors(){return ownedUsers.stream().filter(user->{StandPower power=PowerClass.STAND.get(user);return !user.isRemoved()||power!=null&&power.isSummoned()||actors.stream().anyMatch(actor->actor.user==user&&!actor.stand.isRemoved());}).count();}
    }
    private record Actor(Player user,StandPower power,StandType type,StandEntity stand,Vec3 origin) {}
    private static final class FallbackProbe implements GameTestListener {
        final GameTestHelper helper;final List<String> failures=new ArrayList<>();
        CleanupProbe probe;Actor actor;Cow sentinel;FallbackListener first;Consumer<EntityTickEvent.Pre> later;
        CleanupFailure primary,cleanup;Throwable result;boolean withUnregisterFailure,signaled,complete;int index,checks,phase,ticks,firstCalls,laterCalls;
        FallbackProbe(GameTestHelper helper){this.helper=helper;}
        void setup(){
            withUnregisterFailure=index++==1;primary=new CleanupFailure("R1 partial native setup deliberately aborted");cleanup=new CleanupFailure("R1 actual owned unregister failure");
            probe=new CleanupProbe(helper);probe.kind=CleanupKind.UNREGISTER;probe.suite=new Suite(helper);probe.scene=new Scene(probe.suite,Kind.IMMUNE);probe.suite.scene=probe.scene;
            ChunkPos chunk=new ChunkPos(helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.37,helper.absolutePos(BlockPos.ZERO).getY()+8.2,chunk.getMiddleBlockZ()+0.43);
            actor=probe.scene.actor(origin,28);probe.scene.caster=actor;
            sentinel=new Cow(EntityType.COW,probe.scene.level);sentinel.setNoAi(true);sentinel.setNoGravity(true);sentinel.moveTo(origin.x+4,origin.y,origin.z,0,0);helper.assertTrue(probe.scene.level.addFreshEntity(sentinel),"Actual fallback sentinel admitted");
            first=new FallbackListener(this);later=e->{if(e.getEntity()==sentinel&&phase>0)laterCalls++;};
            probe.suite.listeners.add(first);NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,EntityTickEvent.Pre.class,first);probe.suite.listeners.add(later);NeoForge.EVENT_BUS.addListener(EventPriority.LOW,EntityTickEvent.Pre.class,later);
            helper.assertTrue(actor.power.isSummoned()&&probe.scene.level.getEntity(actor.user.getUUID())==actor.user&&probe.scene.level.getEntity(actor.stand.getUUID())==actor.stand,"Real factory-owned partial actor/Stand before deliberate TEST setup abort");
            helper.assertTrue(probe.scene.target==null&&probe.scene.recipient==null&&probe.scene.channel==null&&probe.inbound==null&&probe.outbound==null,"Actual partial initialization before nullable resources created");
            phase=0;ticks=0;firstCalls=0;laterCalls=0;signaled=false;result=null;
            fallbackLog("BEFORE case="+index+" unregisterFailure="+withUnregisterFailure+" user="+actor.user.getUUID()+" stand="+actor.stand.getUUID()+" sentinel="+sentinel.getUUID()+" actualListeners="+probe.suite.listeners.size()+" nullableResourcesNotCreated=true");
        }
        void tick(){if(complete)return;try{
            if(probe==null){if(index==2){complete=true;fallbackLog("SUMMARY fix=15-T1-R1 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);helper.assertTrue(failures.isEmpty(),"Sound fallback retry/partial diagnostic failed; see R1 SUMMARY");helper.succeed();return;}setup();return;}
            helper.assertTrue(++ticks<70,"Natural fallback probe timeout");
            if(phase==0&&signaled){try{result=probe.rollback(primary);}catch(RuntimeException|Error failure){result=failure;}
                check(result==primary,"actual initial caught native setup-abort cause survives fallback");
                check(primary.getSuppressed().length==(withUnregisterFailure?1:0)&&(!withUnregisterFailure||primary.getSuppressed()[0]==cleanup),"only actual later unregister cause suppressed, no diagnostic NPE substitution");
                check(actor.user.isRemoved()&&actor.stand.isRemoved()&&!actor.power.isSummoned()&&probe.scene.level.getEntity(actor.user.getUUID())==null&&probe.scene.level.getEntity(actor.stand.getUUID())==null,"actual partial-owned user/Stand removed and unlinked");
                check(probe.suite.listeners.size()==(withUnregisterFailure?1:0),"failed unregister retains only actual owned retry key");
                check((active==probe.suite)==withUnregisterFailure,"active ownership retained only for failed primitive/pending native listener");
                String diagnostic=null;Throwable diagnosticFailure=null;try{diagnostic=probe.state();}catch(RuntimeException|Error failure){diagnosticFailure=failure;}
                check(diagnosticFailure==null&&diagnostic!=null&&diagnostic.contains("targetRemoved=notCreated")&&diagnostic.contains("recipientRemoved=notCreated")&&diagnostic.contains("channelOpen=notCreated")&&diagnostic.contains("bufferRefs=notCreated/notCreated"),"actual uncreated target/recipient/channel/buffer diagnostics are null-safe");
                check(probe.scene.level.getServer().getPlayerList().getPlayers().size()==probe.scene.originalPlayers,"partial fallback preserves actual foreign PlayerList");
                fallbackLog("FIRST case="+index+" result="+describe(result)+" pending="+probe.suite.listeners.size()+" activeOwn="+(active==probe.suite)+" diagnostic="+diagnostic+" diagnosticFailure="+describe(diagnosticFailure));phase=1;ticks=0;return;
            }
            if(phase==1&&ticks>=2){check(laterCalls==0,"later owned native listener removed despite earlier cleanup failure");check(withUnregisterFailure?firstCalls>0:firstCalls==0,"natural ticks qualify actual failed-listener presence or successful absence before retry");
                Throwable retry=null;try{retry=probe.rollback(null);}catch(RuntimeException|Error failure){retry=failure;}
                check(retry==null,"successful fallback retry does not manufacture diagnostic or repeated failure");check(probe.suite.listeners.isEmpty()&&active!=probe.suite,"successful native retry settles key and releases only own active pointer");
                fallbackLog("RETRY case="+index+" result="+describe(retry)+" pending="+probe.suite.listeners.size()+" activeOwn="+(active==probe.suite));phase=2;ticks=0;firstCalls=0;laterCalls=0;return;
            }
            if(phase==2&&ticks>=2){check(firstCalls==0&&laterCalls==0,"further real native ticks see neither owned listener after retry");Throwable disposal=dispose(null);helper.assertTrue(disposal==null,"Actual OLD orphan disposal failed: "+describe(disposal));probe=null;return;}
        }catch(RuntimeException|Error failure){complete=true;throwCleanup(dispose(failure));}}
        void check(boolean ok,String detail){checks++;fallbackLog((ok?"PASS ":"FAIL ")+"case="+index+" "+detail);if(!ok)failures.add("case="+index+": "+detail);}
        Throwable dispose(Throwable failure){if(probe==null)return failure;boolean[] failed={false};
            if(first!=null)failure=attemptCleanup(failure,failed,()->{NeoForge.EVENT_BUS.unregister(first);if(probe.suite!=null)probe.suite.listeners.remove(first);});if(later!=null)failure=attemptCleanup(failure,failed,()->{NeoForge.EVENT_BUS.unregister(later);if(probe.suite!=null)probe.suite.listeners.remove(later);});
            if(probe.scene!=null){for(Player user:probe.scene.ownedUsers){failure=attemptCleanup(failure,failed,()->AbilityInput.keyRelease(ATTACK,user));failure=attemptCleanup(failure,failed,()->AbilityInput.keyRelease(PRIME,user));failure=attemptCleanup(failure,failed,()->{StandPower power=PowerClass.STAND.get(user);if(power!=null&&power.isSummoned())MadeInHeavenAddon.MADE_IN_HEAVEN.get().forceUnsummon(user,power);});failure=attemptCleanup(failure,failed,user::discard);}for(Actor owned:probe.scene.actors)failure=attemptCleanup(failure,failed,owned.stand::discard);}
            if(sentinel!=null)failure=attemptCleanup(failure,failed,sentinel::discard);
            if(probe.suite!=null)failure=attemptCleanup(failure,failed,()->{probe.suite.multiplier.set(probe.suite.originalMultiplier);if(!failed[0]&&probe.suite.listeners.isEmpty()&&(probe.scene==null||probe.scene.remainingActors()==0)&&(sentinel==null||sentinel.isRemoved())&&active==probe.suite)active=null;});
            Throwable result=failure;return attemptCleanup(failure,failed,()->fallbackLog("DISPOSAL case="+index+" userRemoved="+(actor==null||actor.user.isRemoved())+" standRemoved="+(actor==null||actor.stand.isRemoved())+" failure="+describe(result)));
        }
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){throwCleanup(dispose(null));}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){throwCleanup(dispose(null));}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){throwCleanup(dispose(null));}
    }
    private static final class FallbackListener implements Consumer<EntityTickEvent.Pre> {
        final FallbackProbe owner;boolean throwNextHash;
        FallbackListener(FallbackProbe owner){this.owner=owner;}
        @Override public void accept(EntityTickEvent.Pre event){if(event.getEntity()!=owner.sentinel)return;if(owner.phase==0&&!owner.signaled){try{throw owner.primary;}catch(RuntimeException failure){owner.primary=(CleanupFailure)failure;owner.signaled=true;}throwNextHash=owner.withUnregisterFailure;fallbackLog("NATIVE-CALLBACK case="+owner.index+" entity="+event.getEntity().getUUID()+" world="+owner.probe.scene.level.getGameTime()+" abortCaught=true hashArmed="+throwNextHash);}else if(owner.phase>0)owner.firstCalls++;}
        @Override public int hashCode(){if(throwNextHash){throwNextHash=false;fallbackLog("NATIVE-UNREGISTER case="+owner.index+" actualHashThrow=true");throw owner.cleanup;}return System.identityHashCode(this);}
    }
    private static void fallbackLog(String message){JojoMod.getLogger().info("D-MIH-CHOP-SOUND-FALLBACK "+message);}
    private enum CleanupKind { UNREGISTER,USER_REMOVE,TARGET_REMOVE }
    private static final class CleanupFailure extends RuntimeException {CleanupFailure(String message){super(message);}}
    private static final class CleanupProbe implements GameTestListener {
        final GameTestHelper helper;final List<String> failures=new ArrayList<>();
        Suite suite;Scene scene;Actor second;Cow eventEntity;UnregisterProbe first;Consumer<EntityTickEvent.Pre> later;
        CleanupKind kind;CleanupFailure primary,cleanup;RuntimeException caught;ByteBuf inbound,outbound;
        int index,checks,phase,waitTicks,firstCalls,laterCalls;long nativeSignalTime=-1;boolean complete;
        CleanupProbe(GameTestHelper helper){this.helper=helper;}
        void setup(){
            kind=CleanupKind.values()[index++];primary=new CleanupFailure(kind+" initial caught native-event setup failure");cleanup=new CleanupFailure(kind+" owned cleanup failure");
            suite=new Suite(helper);scene=new Scene(suite,Kind.IMMUNE);suite.scene=scene;scene.prepare();
            second=scene.actor(scene.caster.origin.add(-0.5,0,0),32);
            if(kind==CleanupKind.USER_REMOVE){Player user=new RemovalProbePlayer(scene.level,cleanup);scene.ownedUsers.addFirst(user);user.moveTo(scene.caster.origin.x-1,scene.caster.origin.y,scene.caster.origin.z,0,0);helper.assertTrue(scene.level.addFreshEntity(user),"Actual removal-probe user admitted to native world");}
            if(kind==CleanupKind.TARGET_REMOVE){Entity original=scene.target;original.discard();helper.assertTrue(original.isRemoved()&&scene.level.getEntity(original.getUUID())==null,"Only previous owned target discarded before cleanup probe replacement");scene.target=new RemovalProbeCow(scene.level,cleanup);scene.target.moveTo(scene.targetOrigin.x,scene.targetOrigin.y,scene.targetOrigin.z,0,0);helper.assertTrue(scene.level.addFreshEntity(scene.target),"Actual removal-probe target admitted to native world");}
            scene.block=BlockPos.containing(scene.caster.origin.add(3,0,0));scene.originalBlock=scene.level.getBlockState(scene.block);scene.level.setBlock(scene.block,Blocks.STONE.defaultBlockState(),3);
            inbound=Unpooled.buffer().writeByte(1);outbound=Unpooled.buffer().writeByte(2);scene.channel.writeInbound(inbound);scene.channel.writeOutbound(outbound);
            eventEntity=new Cow(EntityType.COW,scene.level);eventEntity.setNoAi(true);eventEntity.setNoGravity(true);eventEntity.moveTo(scene.caster.origin.x+4,scene.caster.origin.y,scene.caster.origin.z,0,0);helper.assertTrue(scene.level.addFreshEntity(eventEntity),"Actual native-tick sentinel added");
            first=new UnregisterProbe(this);later=e->{if(e.getEntity()==eventEntity&&phase>=2)laterCalls++;};
            scene.suite.listeners.add(first);NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,EntityTickEvent.Pre.class,first);
            scene.suite.listeners.add(later);NeoForge.EVENT_BUS.addListener(EventPriority.LOW,EntityTickEvent.Pre.class,later);
            helper.assertTrue(scene.actors.size()==2&&scene.caster.power.isSummoned()&&second.power.isSummoned()&&!scene.caster.stand.isRemoved()&&!second.stand.isRemoved(),"Actual public factory caster and second primer-role actor linked before cleanup; no cleanup-probe combat input");
            helper.assertTrue(scene.level.getServer().getPlayerList().getPlayers().contains(scene.recipient)&&scene.channel.isOpen()&&inbound.refCnt()==1&&outbound.refCnt()==1&&!scene.level.getBlockState(scene.block).equals(scene.originalBlock),"Actual own recipient/channel/buffer/block resources before cleanup");
            phase=0;waitTicks=0;firstCalls=0;laterCalls=0;nativeSignalTime=-1;caught=null;
            cleanupLog("BEFORE "+state()+" users="+scene.ownedUsers.stream().map(user->user.getUUID()+":"+user.isRemoved()).toList()+" stands="+scene.actors.stream().map(actor->actor.stand.getUUID()+":"+actor.power.isSummoned()).toList()+" target="+scene.target.getUUID()+" recipient="+scene.recipient.getUUID()+" primary="+primary.getMessage()+" cleanup="+cleanup.getMessage());
        }
        void tick(){if(complete)return;try{
            if(scene==null){if(index==CleanupKind.values().length){complete=true;cleanupLog("SUMMARY fix=15-T1 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);helper.assertTrue(failures.isEmpty(),"Sound fixture exceptional cleanup failed; see15-T1 SUMMARY");helper.succeed();return;}setup();return;}
            helper.assertTrue(++waitTicks<80,"Native cleanup probe timeout");
            if(phase==0&&nativeSignalTime>=0){try{suite.tick();}catch(RuntimeException failure){caught=failure;}phase=2;waitTicks=0;
                check(caught==primary,"original caught native-event cause remains primary");
                check(caught!=null&&caught.getSuppressed().length==1&&caught.getSuppressed()[0]==cleanup,"distinct real owned cleanup failure is suppressed on original cause");
                check(actorsGone(),"every owned caster/second actor user and native Stand removed/unlinked despite earlier cleanup throw");
                check(scene.target.isRemoved()&&scene.level.getEntity(scene.target.getUUID())==null,"actual target removed independently");
                check(scene.level.getBlockState(scene.block).equals(scene.originalBlock),"original owned block restored independently");
                check(scene.recipient.isRemoved()&&scene.level.getEntity(scene.recipient.getUUID())==null,"actual owned recipient removed independently");
                check(!scene.level.getServer().getPlayerList().getPlayers().contains(scene.recipient)&&scene.level.getServer().getPlayerList().getPlayers().size()==scene.originalPlayers,"only own native PlayerList membership removed; foreign list preserved");
                check(!scene.channel.isOpen()&&inbound.refCnt()==0&&outbound.refCnt()==0,"actual owned channel closed and both native queued buffers released independently");
                check(suite.closed&&scene.closed,"both cleanup owners stop normal callbacks after failed close");
                check(suite.listeners.size()==(kind==CleanupKind.UNREGISTER?1:0),"unresolved owned listener inventory retained truthfully for retry");
                cleanupLog("FIRST "+state()+" nativeSignalTime="+nativeSignalTime+" caught="+describe(caught));return;
            }
            if(phase==2&&waitTicks>=2){check(laterCalls==0,"later actual native listener unregistered after earlier failure, qualified by natural sentinel ticks");
                if(kind==CleanupKind.UNREGISTER)check(firstCalls>0,"first failed native unregister remains live until explicit owned retry");else check(firstCalls==0,"successful native listener removals stay inactive");
                RuntimeException retry=null;try{suite.close();}catch(RuntimeException failure){retry=failure;}
                check(retry==null,"idempotent retry has no repeated initial cleanup exception");
                check(actorsGone()&&scene.target.isRemoved()&&!scene.channel.isOpen()&&scene.level.getBlockState(scene.block).equals(scene.originalBlock)&&!scene.level.getServer().getPlayerList().getPlayers().contains(scene.recipient)&&suite.listeners.isEmpty()&&active!=suite,"retry settles all owned residue and releases only own suite");
                phase=3;waitTicks=0;firstCalls=0;laterCalls=0;cleanupLog("RETRY "+state()+" retryFailure="+describe(retry));return;
            }
            if(phase==3&&waitTicks>=2){check(firstCalls==0&&laterCalls==0,"both actual native listeners remain absent after retry and further real ticks");
                Throwable rollback=rollback(null);helper.assertTrue(rollback==null,"Measured OLD orphan disposal/GREEN idempotent safety rollback failed: "+describe(rollback));scene=null;suite=null;return;}
        }catch(RuntimeException|Error failure){Throwable all=rollback(failure);complete=true;throwCleanup(all);}}
        boolean actorsGone(){return scene.ownedUsers.stream().allMatch(user->user.isRemoved()&&scene.level.getEntity(user.getUUID())==null)&&scene.actors.stream().allMatch(actor->actor.stand.isRemoved()&&scene.level.getEntity(actor.stand.getUUID())==null&&!actor.power.isSummoned());}
        String state(){if(scene==null)return "kind="+kind+" scene=notCreated listenersPending="+(suite==null?"notCreated":suite.listeners.size());return "kind="+kind+" world="+scene.level.getGameTime()+" usersRemoved="+scene.ownedUsers.stream().filter(Entity::isRemoved).count()+"/"+scene.ownedUsers.size()+" standsRemoved="+scene.actors.stream().filter(actor->actor.stand.isRemoved()&&!actor.power.isSummoned()).count()+"/"+scene.actors.size()+" listenersPending="+(suite==null?"notCreated":suite.listeners.size())+" targetRemoved="+(scene.target==null?"notCreated":scene.target.isRemoved())+" blockRestored="+(scene.block==null?"notCreated":scene.originalBlock==null?"unknownOriginal":scene.level.getBlockState(scene.block).equals(scene.originalBlock))+" recipientRemoved="+(scene.recipient==null?"notCreated":scene.recipient.isRemoved())+" ownMembership="+(scene.recipient!=null&&scene.level.getServer().getPlayerList().getPlayers().contains(scene.recipient))+" actualPlayers="+scene.level.getServer().getPlayerList().getPlayers().size()+" channelOpen="+(scene.channel==null?"notCreated":scene.channel.isOpen())+" bufferRefs="+(inbound==null?"notCreated":inbound.refCnt())+"/"+(outbound==null?"notCreated":outbound.refCnt());}
        void check(boolean ok,String detail){checks++;cleanupLog((ok?"PASS ":"FAIL ")+kind+" "+detail);if(!ok)failures.add(kind+": "+detail);}
        Throwable rollback(Throwable failure){boolean[] failed={false};
            if(suite!=null)for(Object owned:new ArrayList<>(suite.listeners))failure=attemptCleanup(failure,failed,()->{NeoForge.EVENT_BUS.unregister(owned);suite.listeners.remove(owned);});
            if(scene!=null){
                for(Player user:scene.ownedUsers){failure=attemptCleanup(failure,failed,()->AbilityInput.keyRelease(ATTACK,user));failure=attemptCleanup(failure,failed,()->AbilityInput.keyRelease(PRIME,user));failure=attemptCleanup(failure,failed,()->{StandPower power=PowerClass.STAND.get(user);if(power!=null&&power.isSummoned())MadeInHeavenAddon.MADE_IN_HEAVEN.get().forceUnsummon(user,power);});failure=attemptCleanup(failure,failed,user::discard);}
                for(Actor actor:scene.actors)failure=attemptCleanup(failure,failed,actor.stand::discard);
                if(scene.target!=null)failure=attemptCleanup(failure,failed,scene.target::discard);
                if(scene.block!=null&&scene.originalBlock!=null)failure=attemptCleanup(failure,failed,()->scene.level.setBlock(scene.block,scene.originalBlock,3));
                if(scene.ownedMembership!=null&&scene.recipient!=null)failure=attemptCleanup(failure,failed,()->scene.ownedMembership.remove(scene.recipient));
                if(scene.recipient!=null)failure=attemptCleanup(failure,failed,scene.recipient::discard);
                if(scene.channel!=null)failure=attemptCleanup(failure,failed,()->scene.channel.finishAndReleaseAll());
            }
            if(eventEntity!=null)failure=attemptCleanup(failure,failed,eventEntity::discard);
            if(suite!=null)failure=attemptCleanup(failure,failed,()->suite.multiplier.set(suite.originalMultiplier));
            Throwable result=failure;failure=attemptCleanup(failure,failed,()->cleanupLog("HARNESS-ROLLBACK "+state()+" failure="+describe(result)));
            return attemptCleanup(failure,failed,()->{boolean settled=!failed[0]&&(suite==null||suite.listeners.isEmpty()&&Double.compare(suite.multiplier.get(),suite.originalMultiplier)==0)&&(scene==null||scene.remainingActors()==0&&(scene.target==null||scene.target.isRemoved())&&(scene.block==null||scene.originalBlock!=null&&scene.level.getBlockState(scene.block).equals(scene.originalBlock))&&(scene.recipient==null||scene.recipient.isRemoved()&&!scene.level.getServer().getPlayerList().getPlayers().contains(scene.recipient))&&(scene.channel==null||!scene.channel.isOpen()))&&(eventEntity==null||eventEntity.isRemoved())&&(inbound==null||inbound.refCnt()==0)&&(outbound==null||outbound.refCnt()==0);if(settled&&suite!=null&&active==suite)active=null;});
        }
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){throwCleanup(rollback(null));}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){throwCleanup(rollback(null));}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){throwCleanup(rollback(null));}
    }
    private static final class UnregisterProbe implements Consumer<EntityTickEvent.Pre> {
        final CleanupProbe owner;boolean throwNextHash;
        UnregisterProbe(CleanupProbe owner){this.owner=owner;}
        @Override public void accept(EntityTickEvent.Pre event){if(event.getEntity()!=owner.eventEntity)return;if(owner.phase==0&&owner.nativeSignalTime<0){owner.nativeSignalTime=owner.scene.level.getGameTime();try{throw owner.primary;}catch(RuntimeException primary){owner.suite.observerFailure=primary;}throwNextHash=owner.kind==CleanupKind.UNREGISTER;cleanupLog("NATIVE-CALLBACK kind="+owner.kind+" entity="+event.getEntity().getUUID()+" world="+owner.nativeSignalTime+" primaryCaught=true unregisterHashArmed="+throwNextHash);}else if(owner.phase>=2)owner.firstCalls++;}
        @Override public int hashCode(){if(throwNextHash){throwNextHash=false;cleanupLog("NATIVE-UNREGISTER-HASH kind="+owner.kind+" exception="+owner.cleanup.getMessage());throw owner.cleanup;}return System.identityHashCode(this);}
    }
    private static final class RemovalProbePlayer extends Player {
        final RuntimeException cleanup;boolean throwOnce=true;
        RemovalProbePlayer(ServerLevel level,RuntimeException cleanup){super(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-cleanup-resource"));this.cleanup=cleanup;setNoGravity(true);noPhysics=true;}
        @Override public boolean isCreative(){return false;}
        @Override public boolean isSpectator(){return false;}
        @Override public void remove(RemovalReason reason){super.remove(reason);if(throwOnce&&reason==RemovalReason.DISCARDED){throwOnce=false;cleanupLog("NATIVE-REMOVE user="+getUUID()+" delegatedRemoved="+isRemoved()+" exception="+cleanup.getMessage());throw cleanup;}}
    }
    private static final class RemovalProbeCow extends Cow {
        final RuntimeException cleanup;boolean throwOnce=true;
        RemovalProbeCow(ServerLevel level,RuntimeException cleanup){super(EntityType.COW,level);this.cleanup=cleanup;setNoAi(true);setNoGravity(true);noPhysics=true;}
        @Override public void remove(RemovalReason reason){super.remove(reason);if(throwOnce&&reason==RemovalReason.DISCARDED){throwOnce=false;cleanupLog("NATIVE-REMOVE target="+getUUID()+" delegatedRemoved="+isRemoved()+" exception="+cleanup.getMessage());throw cleanup;}}
    }
    private static Throwable attemptCleanup(Throwable first,Runnable action){try{action.run();}catch(RuntimeException|Error later){if(first==null)return later;if(first!=later&&java.util.Arrays.stream(first.getSuppressed()).noneMatch(error->error==later))first.addSuppressed(later);}return first;}
    private static void throwCleanup(Throwable failure){if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;}
    private static String describe(Throwable failure){return failure==null?"none":failure.getClass().getName()+":"+failure.getMessage()+" suppressed="+java.util.Arrays.stream(failure.getSuppressed()).map(error->error.getClass().getName()+":"+error.getMessage()).toList();}
    private static void cleanupLog(String message){JojoMod.getLogger().info("D-MIH-CHOP-SOUND-CLEANUP "+message);}
    private static final class ObservedCow extends Cow {final Scene scene;ObservedCow(Scene scene){super(EntityType.COW,scene.level);this.scene=scene;}@Override public boolean hurt(DamageSource source,float amount){Hurt record=scene.beforeHurt(this,source,amount);boolean result=super.hurt(source,amount);scene.afterHurt(record,result);return result;}}
    private static final class ObservedBoat extends Boat {final Scene scene;ObservedBoat(Scene scene){super(EntityType.BOAT,scene.level);this.scene=scene;}@Override public boolean hurt(DamageSource source,float amount){boolean observe=scene.observing&&source.getDirectEntity()==scene.caster.stand;if(observe)scene.nonlivingBefore=++scene.order;boolean result=super.hurt(source,amount);if(observe){scene.nonlivingResult=result;scene.nonlivingAfter=++scene.order;log("NONLIVING-HURT kind="+scene.kind+" actualReturned="+result+" order="+scene.nonlivingBefore+".."+scene.nonlivingAfter);}return result;}}
    private static final class CapturingListener extends ServerGamePacketListenerImpl {final Scene scene;CapturingListener(MinecraftServer server,Connection connection,ServerPlayer player,CommonListenerCookie cookie,Scene scene){super(server,connection,player,cookie);this.scene=scene;}@Override public void send(Packet<?> packet){capture(packet);}@Override public void send(Packet<?> packet,PacketSendListener listener){capture(packet);}void capture(Packet<?> packet){if(scene==null||!scene.observing||!(packet instanceof ClientboundCustomPayloadPacket custom))return;if(custom.payload() instanceof StandSkinSoundPacket sound){ResourceLocation key=key(sound.sound());if((key.equals(MadeInHeavenAddon.MIH_CHOP.getId())||key.equals(JojoMod.resLoc("stand_punch_heavy")))&&scene.impactPosition!=null&&near(sound.position(),scene.impactPosition)){String sender=StackWalker.getInstance().walk(frames->frames.filter(frame->frame.getClassName().equals(StandUtil.class.getName())).map(frame->frame.getClassName()+"."+frame.getMethodName()).findFirst().orElse("unqualified"));scene.sends.add(new Sent(++scene.order,sound,sender));log("PACKET kind="+scene.kind+" id="+custom.payload().type().id()+" sender="+sender+" recipient="+scene.recipient.getUUID()+" actualMember="+scene.level.getServer().getPlayerList().getPlayers().contains(scene.recipient)+" actualCanHear="+StandUtil.entityCanHearStands(scene.recipient)+" order="+scene.order+" payload="+sound);}}if(custom.payload() instanceof PlayVoiceLinePacket voice&&voice.entityId()==scene.caster.user.getId()&&voice.sound()!=null){scene.voices.add(voice);log("VOICE kind="+scene.kind+" id="+voice.type().id()+" entity="+voice.entityId()+" holder="+key(voice.sound())+" category="+voice.source()+" volume="+voice.volume()+" pitch="+voice.pitch());}}}
    private static SoundEvent cry(EntityActionInstance action){try{var method=StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class.getDeclaredMethod("getHeavyPunchCrySound");method.setAccessible(true);return (SoundEvent)method.invoke(action);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    // What StandEntityHeavyPunch.playHeavyPunchPerformSound sends from the Stand when the perform phase starts.
    private static Object performSound(EntityActionInstance action){try{var field=StandEntityHeavyPunchAbility.class.getDeclaredField("heavyPunchPerformSound");field.setAccessible(true);return field.get(action.ability);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static ResourceLocation key(Holder<SoundEvent> holder){return holder==null?null:holder.unwrapKey().orElseThrow().location();}
    private static boolean near(Vec3 left,Vec3 right){return left.distanceToSqr(right)<1E-8;}
    private static void identity(GameTestHelper helper,Class<?>... types){for(Class<?> type:types){try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())),expected=System.getProperty("mih.sound.expected."+type.getName());helper.assertTrue(expected==null||expected.equalsIgnoreCase(hash),"Actual sound class mismatch "+type.getName());log("IDENTITY "+type.getName()+"="+hash+" codeSource="+type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString());}catch(Exception failure){throw new IllegalStateException(failure);}}}
    private static void log(String message){JojoMod.getLogger().info("D-MIH-CHOP-SOUND "+message);}
    private static Throwable attemptCleanup(Throwable first,boolean[] failed,Runnable action){return attemptCleanup(first,()->{try{action.run();}catch(RuntimeException|Error failure){failed[0]=true;throw failure;}});}
}
