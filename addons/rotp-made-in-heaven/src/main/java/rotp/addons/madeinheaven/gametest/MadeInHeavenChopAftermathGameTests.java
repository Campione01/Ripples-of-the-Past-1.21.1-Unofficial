package rotp.addons.madeinheaven.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.addons.madeinheaven.entity.MadeInHeavenStandEntity;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopAftermathGameTests {
    private static final short ATTACK=156,BARRAGE=157,CONTROL=158;
    private static final PowerSkillUnlocks.Owner UNLOCKS=PowerSkillUnlocks.register(MadeInHeavenAddon.id("chop_aftermath_tests"));
    private static Suite active;
    private enum Kind { CHOP_BARRAGE,HEAVY_BARRAGE,CHOP_IDLE,HEAVY_IDLE,CHOP_EXISTING,HEAVY_EXISTING,CHOP_CANCELED,HEAVY_CANCELED }
    private record Actor(Player user,StandPower power,StandType type,StandEntity stand,Vec3 origin) {}
    private record ProvisionalUser(Player user,StandType type) {}
    private MadeInHeavenChopAftermathGameTests() {}

    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=1600,batch="mihChopAftermath10")
    public static void chopOmitsHeavyBarrageAndCollisionAftermath(GameTestHelper helper){
        Suite suite=new Suite(helper);
        try{
            for(Class<?> type:new Class<?>[]{MadeInHeavenAddon.class,MadeInHeavenChopAbility.class,MadeInHeavenChopAbility.MadeInHeavenChop.class,MadeInHeavenStandEntity.class,StandEntityHeavyPunchAbility.class,StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class,StandEntityBarrageAbility.class,StandEntity.class,AbilityInput.class,EntityActionAbility.class,KnockbackCollisionImpact.class,MadeInHeavenChopAftermathGameTests.class}){
                String actual=sha(type),expected=System.getProperty("mih.aftermath.expected."+type.getName());
                helper.assertTrue(expected==null||expected.equalsIgnoreCase(actual),"Native class mismatch "+type.getName());
                log("IDENTITY "+type.getName()+"="+actual+" codeSource="+uri(type));
            }
            suite.listen();helper.onEachTick(suite::tick);
        }catch(RuntimeException|Error failure){suite.close();throw failure;}
    }
    @AfterBatch(batch="mihChopAftermath10")
    public static void releaseBatch(ServerLevel level){if(active!=null)active.close();log("BATCH-CLEANUP active="+(active!=null));}

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;final List<Object> listeners=new ArrayList<>();final List<String> failures=new ArrayList<>();
        Scene scene;RuntimeException observerFailure;int index,checks;boolean closed;
        Suite(GameTestHelper helper){helper.assertTrue(active==null,"Overlapping aftermath suites");this.helper=helper;active=this;helper.testInfo.addListener(this);}
        void listen(){
            Consumer<EntityTickEvent.Pre> pre=event->{if(scene!=null&&!closed)try{scene.pre(event.getEntity());}catch(RuntimeException error){observerFailure=error;}};
            Consumer<EntityTickEvent.Post> post=event->{if(scene!=null&&!closed)try{scene.post(event.getEntity());}catch(RuntimeException error){observerFailure=error;}};
            Consumer<LivingIncomingDamageEvent> damage=event->{if(scene!=null&&!closed)scene.incoming(event);};
            listeners.addAll(List.of(pre,post,damage));
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Post.class,post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,damage);
        }
        void tick(){if(closed)return;try{
            if(observerFailure!=null)throw observerFailure;
            if(scene!=null&&scene.done){scene.close();scene=null;}
            if(scene==null){if(index==Kind.values().length){log("SUMMARY fix=10 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"Chop aftermath failed; see SUMMARY");helper.succeed();return;}scene=new Scene(this,Kind.values()[index++]);scene.prepare();}
            scene.tick();helper.assertTrue(++scene.ticks<100,"Aftermath natural scene timeout "+scene.kind);
        }catch(RuntimeException|Error failure){close();throw failure;}}
        void check(boolean passed,String message){checks++;log((passed?"PASS ":"FAIL ")+scene.kind+" "+message);if(!passed)failures.add(scene.kind+": "+message);}
        void close(){if(closed)return;closed=true;for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);listeners.clear();if(scene!=null)scene.close();active=null;log("CLEANUP actors=0 listeners=0");}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }
    private static final class Scene implements AutoCloseable {
        final Suite suite;final Kind kind;final boolean chop,barraging,existing,canceled;final ServerLevel level;
        final List<Actor> actors=new ArrayList<>();final List<ProvisionalUser> provisionalUsers=new ArrayList<>();Actor caster,target;EntityActionInstance action,barrage;
        KnockbackCollisionImpact impact;CompoundTag before;float health;int ticks,incoming;boolean sampling,done,closed;
        Scene(Suite suite,Kind kind){this.suite=suite;this.kind=kind;chop=kind.name().startsWith("CHOP");existing=kind.name().endsWith("EXISTING");canceled=kind.name().endsWith("CANCELED");barraging=kind.name().endsWith("BARRAGE")||canceled;level=suite.helper.getLevel();}
        void prepare(){
            ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.5,suite.helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);
            caster=actor(MadeInHeavenAddon.MADE_IN_HEAVEN.get(),origin,0,28,true);
            StandType sp=JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));target=actor(sp,origin.add(0,0,1.5),0,14,false);
            press(target,"manual_control",InputMethod.CLICK,CONTROL);premise(target.stand.isManuallyControlled(),"real manual control excludes passive auto-guard");
            if(barraging){LivingComponentAction.getComponent(target.stand).entityAim.setTarget(ActionTarget.EMPTY);barrage=press(target,"barrage",InputMethod.HOLD,BARRAGE);premise(barrage instanceof StandEntityBarrageAbility.StandEntityBarrage&&barrage.ability instanceof EntityActionAbility ability&&ability.stopsOnHeavyAttack(barrage),"genuine registered target barrage with its ordinary-heavy interruption policy");}
            impact=KnockbackCollisionImpact.getHandler(target.user);
            if(existing){Vec3 prior=new Vec3(0.12,0,0.6);target.user.setDeltaMovement(prior);impact.onPunchSetKnockbackImpact(prior,caster.stand).withImpactExplosion(3,null,0);premise(impact.isActive()&&impact.serializeNBT(level.registryAccess()).getFloat("ExplosionRadius")==3,"public handler extension setup is actually armed; no unowned state reset");}
            else premise(!impact.isActive()&&impact.serializeNBT(level.registryAccess()).isEmpty(),"fresh correct linked-user collision handler is unarmed");
            log("START "+kind+" casterClass="+caster.stand.getClass().getName()+" targetClass="+target.stand.getClass().getName()+" impactOwner="+target.user.getUUID()+" existing="+existing);
        }
        void tick(){if(action==null&&ticks>=3){
            if(barraging)premise(LivingComponentAction.getCurEntityAction(target.stand)==barrage&&!barrage.isOver()&&barrage.getPhase()==ActionPhase.PERFORM,"genuine target barrage is running before attacker input");
            if(chop){var result=UNLOCKS.forceUnlock(caster.power,"chop");premise(result==PowerSkillUnlocks.Result.UNLOCKED||result==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"public Chop unlock");}
            caster.stand.setFinisherMeter(chop?0.75F:0.25F);caster.power.setStamina(caster.power.getMaxStamina());
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(new ActionTarget(target.stand));
            premise(caster.stand.canAttackEntity(target.stand)&&!StandEntity.isStandAttackInvulnerable(target.stand)&&!caster.power.isStaminaInfinite(),"actual friendly-fire/RAW/finite admission before registered heavy input");
            action=press(caster,"heavy_punch",InputMethod.CLICK,ATTACK);premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop)==chop,"registered finisher Chop versus ordinary-heavy action factory");
        }}
        void pre(Entity entity){if(entity!=caster.stand||action==null||done||closed)return;sampling=false;incoming=0;
            for(Actor actor:actors){actor.stand.moveTo(actor.origin.x,actor.origin.y,actor.origin.z,0,0);actor.user.moveTo(actor.origin.x,actor.origin.y,actor.origin.z,0,0);actor.stand.setDeltaMovement(Vec3.ZERO);}
            caster.user.setDeltaMovement(Vec3.ZERO);target.user.setDeltaMovement(existing?new Vec3(0.12,0,0.6):Vec3.ZERO);
            health=target.user.getHealth();before=impact.serializeNBT(level.registryAccess()).copy();
            if(existing)premise(impact.isActive()&&before.contains("Vec"),"existing impact is actually active at pre-attack sampling");else premise(!impact.isActive()&&before.isEmpty(),"fresh impact remains unarmed at pre-attack sampling");
            if(barraging)premise(barrage.getPhase()==ActionPhase.PERFORM&&!barrage.isOver(),"running native target barrage before the actual attack tick");
            sampling=true;
        }
        void incoming(LivingIncomingDamageEvent event){if(!sampling||event.getEntity()!=target.stand||event.getSource().getDirectEntity()!=caster.stand)return;incoming++;log("INCOMING "+kind+" nativeAmount="+event.getAmount()+" barragePhase="+(barrage==null?"none":barrage.getPhase()));if(canceled)event.setCanceled(true);}
        void post(Entity entity){if(entity!=caster.stand||action==null||done||closed)return;sampling=false;if(action.punchedTarget==null)return;
            premise(action.punchedTarget.getMainEntity()==target.stand&&incoming==1,"one natural primary target/native Incoming call");
            float loss=health-target.user.getHealth();premise(canceled?loss==0:loss>0,"qualified native canceled/successful health outcome, never a substituted hurt");
            CompoundTag after=impact.serializeNBT(level.registryAccess());boolean ordinaryArms=!chop&&!canceled;
            suite.check(canceled?loss==0:loss>0,"actual native health outcome retained");
            suite.check(Math.abs(action.lastDamageDealtToLiving-loss)<0.0001,"lastDamage equals actual linked-user health loss or0 on failed hurt");
            suite.check(impact.isActive()==(existing||ordinaryArms),"only ordinary successful heavy arms a fresh collision handler");
            boolean nbt=existing?(chop?after.equals(before):!after.equals(before)&&after.contains("Vec")&&after.getFloat("ExplosionRadius")>0):ordinaryArms?after.contains("Vec")&&after.getDouble("Power")>0&&after.getFloat("ExplosionRadius")==2.125F:after.isEmpty();
            suite.check(nbt,"collision Vec/ExplosionRadius preserves existing state for Chop or registers ordinary aftermath");
            premise(target.stand.getDeltaMovement().lengthSqr()>1E-8&&target.user.getDeltaMovement().lengthSqr()>1E-8,"actual nonzero stand/linked-user pulse velocity excludes zero-vector false oracle");
            suite.check(target.stand.getDeltaMovement().lengthSqr()>1E-8&&target.user.getDeltaMovement().lengthSqr()>1E-8,"retained admitted pulse/tail reaches real velocity");
            if(barraging){boolean intact=LivingComponentAction.getCurEntityAction(target.stand)==barrage&&barrage.getPhase()==ActionPhase.PERFORM&&!barrage.isOver();suite.check(intact==(chop||canceled),"Chop/canceled hit preserves genuine barrage; ordinary hurt interrupts it");}
            else suite.check(LivingComponentAction.getCurEntityAction(target.stand)==null,"idle target remains idle");
            log("IMPACT "+kind+" loss="+loss+" lastDamage="+action.lastDamageDealtToLiving+" targetVelocity="+target.stand.getDeltaMovement()+" userVelocity="+target.user.getDeltaMovement()+" active="+impact.isActive()+" before="+before+" after="+after+" barrage="+(barrage==null?"none":barrage.getPhase()));done=true;
        }
        Actor actor(StandType type,Vec3 origin,float yaw,double speed,boolean mih){
            Player user=new Player(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-aftermath")){@Override public boolean isCreative(){return false;}@Override public boolean isSpectator(){return false;}};
            provisionalUsers.add(new ProvisionalUser(user,type));
            user.noPhysics=true;user.setNoGravity(true);user.getAbilities().invulnerable=false;user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);user.setHealth(200);user.moveTo(origin.x,origin.y,origin.z,yaw,0);user.setYHeadRot(yaw);premise(level.addFreshEntity(user),"native player added");
            StandPower power=PowerClass.STAND.attachGet(user);premise(StandPowerTransitions.insert(power,new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"public registered Stand grant");power.setResolveLevel(6);premise(type.summon(user,power),"actual registered native summon factory");StandEntity stand=power.getSummonedStandEntity();
            Actor actor=new Actor(user,power,type,stand,origin);actors.add(actor);stand.summonLockTicks=0;stand.gradualSummonWeaknessTicks=0;stand.setCanFollowUser(false);stand.setNoGravity(true);stand.noPhysics=true;stand.moveTo(origin.x,origin.y,origin.z,yaw,0);stand.setYHeadRot(yaw);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);StandEntity.updateStandStatAttributes(stand,user);power.setStamina(power.getMaxStamina());stand.updateStrengthMultipliers();
            user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock=true;
            premise((mih?stand.getClass()==MadeInHeavenStandEntity.class:stand.getClass()==StandEntity.class)&&stand.getUser()==user&&stand.getUserPower()==power&&stand.getStandType().equals(type.getId()),"actual final factory class/registry/user/power binding");
            log("SETUP class="+stand.getClass().getName()+" type="+type.getId()+" strength="+stand.getAttackDamage()+" speed="+stand.getAttackSpeed()+" durability="+stand.getDurability()+" reach="+stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)+" resistance="+stand.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)+" stored="+power.serializeNBT(level.registryAccess()).getFloat("Stamina"));return actor;
        }
        EntityActionInstance press(Actor actor,String name,InputMethod method,short key){Ability base=actor.power.getAbility(name);premise(base!=null,"registered "+name);var checked=actor.power.updateAvailableMoves().getContextVariationContainer(base);premise(checked!=null&&AbilityInput.withConditionCheck(checked,actor.user,method),"production condition-checked "+name);BufferingState buffer=BufferingState.clickOnly();var entry=AbilityInput.keyPress(key,checked.ability,actor.user,null,method,0,buffer,base.getAbilityId());premise(buffer.isActionSuccess,"native keyPress succeeded");return entry!=null&&entry.action instanceof EntityActionInstance action?action:actor.stand.getCurStandAction();}
        void premise(boolean passed,String message){suite.helper.assertTrue(passed,kind+" premise: "+message);}
        @Override public void close(){if(closed)return;closed=true;sampling=false;for(Actor actor:actors){AbilityInput.keyRelease(ATTACK,actor.user);AbilityInput.keyRelease(BARRAGE,actor.user);AbilityInput.keyRelease(CONTROL,actor.user);if(actor.user.hasData(ModDataAttachmentTypes.CONTROLLER))actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();if(actor.power.isSummoned())actor.type.forceUnsummon(actor.user,actor.power);actor.user.discard();}for(ProvisionalUser pending:provisionalUsers){Player user=pending.user;if(user.isRemoved())continue;if(user.hasData(ModDataAttachmentTypes.CONTROLLER))user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();StandPower power=PowerClass.STAND.get(user);if(power!=null&&power.isSummoned())pending.type.forceUnsummon(user,power);user.discard();}}
    }
    private static String sha(Class<?> type){try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));}catch(Exception error){throw new IllegalStateException(error);}}
    private static String uri(Class<?> type){try{return type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString();}catch(Exception error){throw new IllegalStateException(error);}}
    private static void log(String message){JojoMod.getLogger().info("D-MIH-CHOP-AFTERMATH "+message);}
}
