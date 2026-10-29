package rotp.addons.kingcrimson.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.ability.KingCrimsonHeavyPunchAbility;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.config.ConfigAbilityFactory;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

final class KingCrimsonBasicMeleeFixture implements GameTestListener, AutoCloseable {
    static final double EPS = 0.0003;
    static final class Pulse {
        final boolean insideHurt;
        final double strength, x, z, resistance;
        final Vec3 before;
        final boolean grounded;
        Vec3 after;
        float eventStrength;
        boolean eventSeen, eventCanceled;
        Pulse(LivingEntity target, boolean insideHurt, double strength, double x, double z) {
            this.insideHurt=insideHurt;this.strength=strength;this.x=x;this.z=z;
            resistance=target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);before=target.getDeltaMovement();grounded=target.onGround();
        }
    }
    final GameTestHelper helper;
    final boolean heavy;
    final String fix;
    final List<String> failures = new ArrayList<>();
    final List<Pulse> pulses = new ArrayList<>();
    final List<String> order = new ArrayList<>();
    KingCrimsonGroundAdmissionFixture scene;
    KingCrimsonGroundAdmissionFixture.Actor caster, primer;
    Entity target;
    EntityActionInstance action, primeAction;
    Pulse pendingPulse;
    boolean closed, done, recording, insideHurt, hurtResult, primeHit, primeGuard, rawImmune;
    int index, ticks, checks, kind, hurtCalls, incoming, primeIncoming, startAge, primeStage;
    float sourceAdded, counterAtImpact, primeAmount;
    RuntimeException eventFailure;
    KingCrimsonBasicMeleeFixture(GameTestHelper helper, boolean heavy) {
        this.helper=helper;this.heavy=heavy;fix=heavy?"D-KC-BASIC-01":"D-KC-BASIC-02";
    }
    void start(){helper.testInfo.addListener(this);helper.onEachTick(this::tick);}
    void tick(){
        if(closed)return;
        try{
            if(eventFailure!=null)throw eventFailure;
            if(done){closeScene();scene=null;done=false;}
            if(scene==null){
                if(index==(heavy?9:10)){close();log(fix+" SUMMARY scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);
                    helper.assertTrue(failures.isEmpty(),fix+" behavioral failures="+failures.size());helper.succeed();return;}
                prepare(index++);ticks=0;
            }
            helper.assertTrue(++ticks<220,"Native basic melee scene deadline "+scene.id);
        }catch(RuntimeException|Error error){close();throw error;}
    }
    void prepare(int mode){
        kind=mode;recording=insideHurt=hurtResult=primeHit=primeGuard=rawImmune=false;
        hurtCalls=incoming=primeIncoming=primeStage=0;pulses.clear();order.clear();action=primeAction=null;
        scene=new KingCrimsonGroundAdmissionFixture(helper,(heavy?"heavy/":"counter/")+kind,1);
        caster=scene.actor(scene.origin,scene.origin,0,kind==5?0:4,40);
        caster.stand().setFinisherMeter(0);
        Vec3 position=scene.origin.add(0,0,1.5);
        if(kind==4){
            ObservedPlayer player=player(position);player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));
            player.startUsingItem(InteractionHand.OFF_HAND);target=player;
        }else if(kind==7){
            ObservedBoat boat=scene.add(new ObservedBoat(this),position);boat.setInvulnerable(true);target=boat;
        }else{
            ObservedCow cow=new ObservedCow(this);cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(kind==1?0.5:kind==2?1:0);
            target=scene.add(cow,position);
        }
        scene.listen(LivingKnockBackEvent.class,event->{
            if(event.getEntity()==target&&pendingPulse!=null){pendingPulse.eventSeen=true;pendingPulse.eventStrength=event.getStrength();pendingPulse.eventCanceled=event.isCanceled();}
        });
        scene.listen(LivingIncomingDamageEvent.class,event->{
            if(primer!=null&&event.getEntity()==caster.stand()&&event.getSource().getDirectEntity()==primer.stand()){
                scene.premise(event.getSource().getEntity()==primer.user()||event.getSource().getEntity()==primer.stand(),"Real incoming primer source ownership");
                primeIncoming++;primeGuard=caster.stand().isStandBlocking();primeAmount=event.getAmount();
            }
            if(recording&&event.getEntity()==target&&event.getSource().getDirectEntity()==caster.stand()){
                incoming++;if(kind==3)event.setCanceled(true);
            }
        });
        scene.listen(EntityTickEvent.Pre.class,event->{
            if(event.getEntity()!=caster.stand()||action==null||done)return;
            if(action.getPhase()==ActionPhase.PERFORM&&action.getPhaseTick()<1){
                recording=true;counterAtImpact=caster.stand().guardCounter();
                if(!heavy&&kind==5)scene.premise(StandStatFormulas.getLightAttackDamage(caster.stand().getAttackDamage())==0,"Actual light RAW0 before native hurt helper");
                rawImmune=target instanceof LivingEntity living&&StandEntity.isStandAttackInvulnerable(living);
                target.setDeltaMovement(new Vec3(0.8,0.1,-0.4));
            }
        });
        scene.listen(EntityTickEvent.Post.class,event->{
            if(done||eventFailure!=null)return;
            try{post(event.getEntity());}catch(RuntimeException error){eventFailure=error;}
        });
        if(!heavy&&kind!=9){
            caster.user().getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(0);
            StandEntity.updateStandStatAttributes(caster.stand(),caster.user());caster.power().setStamina(caster.power().getMaxStamina());
            scene.press(caster,null,"guard","guard",InputMethod.HOLD,KingCrimsonGroundAdmissionFixture.GUARD);
            scene.premise(caster.stand().isStandBlocking(),"Real registered held guard before incoming counter prime");
        }
        if(kind!=4&&(!heavy&&kind!=9))return;
        if(kind==4)return;
        if(kind==6)primeImmunity();else beginAttack();
    }
    ObservedPlayer player(Vec3 position){
        ObservedPlayer player=new ObservedPlayer(this);player.setNoGravity(true);player.noPhysics=true;
        player.moveTo(position.x,position.y,position.z,180,0);player.setYHeadRot(180);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);player.setHealth(200);
        scene.force(position);scene.owned.add(player);scene.premise(scene.level.addFreshEntity(player),"Native ticking Player target added");return player;
    }
    void primeCounter(){
        primeStage=1;
        primer=scene.actor(scene.origin.add(0,0,1.5),scene.origin.add(0,0,1.5),180,160,40);
        primer.stand().setFinisherMeter(0);
        scene.premise(caster.stand().isStandBlocking()&&caster.stand().canBlockFromAngle(primer.stand().position()),"Actual held front guard");
        primeAction=scene.press(primer,caster.stand(),"punch","punch",InputMethod.CLICK,KingCrimsonGroundAdmissionFixture.ATTACK);
    }
    void primeImmunity(){
        primeStage=2;
        primer=scene.actor(scene.origin.add(-1,0,0),scene.origin.add(-1,0,0),0,4,40);
        primer.stand().setFinisherMeter(0);
        primeAction=scene.press(primer,target,"heavy_punch","heavy_punch",InputMethod.CLICK,KingCrimsonGroundAdmissionFixture.ATTACK);
    }
    void beginAttack(){
        if(heavy&&kind==5)scene.premise(caster.stand().getAttackDamage()==0&&StandStatFormulas.getHeavyAttackDamage(0)==1,"Inherited heavy minimum1 remains an admitted zero-strength control");
        if(!heavy&&kind!=9){
            log("COUNTER "+scene.id+" incoming="+primeIncoming+" actualLightDamage="+primeAmount+" heldGuard="+primeGuard+" counter="+caster.stand().guardCounter());
            scene.premise(primeIncoming==1&&primeGuard&&caster.stand().guardCounter()>0,"Admitted real incoming hit established positive guard counter");
            scene.premise(caster.stand().guardCounter()==1,"Real counter reaches1 without private-field injection");
            caster.power().setStamina(caster.power().getMaxStamina());caster.stand().updateStrengthMultipliers();
            // the strike at a boat aims again with 1.16's precision ray, which takes a player or Stand first: the primer,
            // standing where the boat is, steps aside once its hit has charged the counter
            if(!(target instanceof LivingEntity)){Vec3 aside=scene.origin.add(4,0,1.5);scene.force(aside);
                primer.user().moveTo(aside.x,aside.y,aside.z,180,0);primer.stand().moveTo(aside.x,aside.y,aside.z,180,0);}
        }
        if(kind==4)scene.premise(((LivingEntity)target).isBlocking(),"Actual naturally raised shield at requested attack");
        if(kind==6)scene.premise(target instanceof LivingEntity living&&StandEntity.isStandAttackInvulnerable(living),"Prior actual heavy established raw immunity");
        caster.stand().setFinisherMeter(0);
        String base=heavy?"heavy_punch":"punch";
        String expected=kind==8?(heavy?"grab_heavy_punch":"grab_punch"):base;
        if(kind==8){
            caster.stand().getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget((LivingEntity)target);
            scene.premise(LivingComponentGrab.getEntityGrabbedBy(caster.stand())==target,"Native grab relationship for named-factory control");
        }
        action=scene.press(caster,target,base,expected,InputMethod.CLICK,KingCrimsonGroundAdmissionFixture.ATTACK);
        if(!heavy&&kind!=9){
            scene.premise(!caster.stand().isStandBlocking()&&caster.stand().guardCounter()==1,"Actual guard-to-punch transition rolls real blocked damage into counter");
            AbilityInput.keyRelease(KingCrimsonGroundAdmissionFixture.GUARD,caster.user());
        }
        scene.premise(action!=null&&action.ability==caster.power().getAbility(expected),"Selected exact ordinary/grab registration, not Ground finisher");
        if(kind==8)scene.premise(action.ability!=caster.power().getAbility(base),"Grab factory owns a distinct configured instance");
        startAge=caster.stand().tickCount;
    }
    void post(Entity entity){
        if(action==null&&primeAction==null){
            if(!heavy&&kind!=9&&entity==caster.user()&&caster.user().tickCount>=65){primeCounter();return;}
            if(heavy&&kind==4&&entity==target&&target.tickCount>=65){scene.premise(((LivingEntity)target).isBlocking(),"Shield naturally raised past warmup");beginAttack();return;}
        }
        if(primeAction!=null&&action==null&&entity==primer.stand()&&primeAction.punchedTarget!=null){
            primeHit=true;
            if(kind==6&&!heavy&&primeStage==1){
                AbilityInput.keyRelease(KingCrimsonGroundAdmissionFixture.ATTACK,primer.user());
                primer=null;primeAction=null;primeImmunity();return;
            }
            if(kind==6){target.moveTo(scene.origin.x,scene.origin.y,scene.origin.z+1.5,180,0);target.setDeltaMovement(Vec3.ZERO);}
            beginAttack();return;
        }
        if(action==null||entity!=caster.stand()||action.punchedTarget==null)return;
        scene.premise(caster.stand().tickCount>startAge&&action.punchedTarget.getMainEntity()==target,"Natural actual primary impact");
        verify();done=true;
    }
    void verify(){
        boolean rawRejected=!heavy&&kind==5||kind==6,grab=kind==8;
        if(kind==6)scene.premise(rawImmune,"Raw immunity still active at immediate native impact");
        check(hurtCalls==(rawRejected?0:1),scene.id+" actual virtual hurt count="+hurtCalls);
        if(!rawRejected){
            boolean expectedHurt=kind!=3&&kind!=4&&kind!=7;
            check(hurtResult==expectedHurt,scene.id+" native hurt outcome="+hurtResult);
            check(incoming==(kind==7?0:1),scene.id+" actual incoming count="+incoming);
            check(grab||Math.abs(sourceAdded)<EPS,scene.id+" detached source carries no combined addition="+sourceAdded);
            if(grab)check(sourceAdded>0||!heavy&&counterAtImpact==0,scene.id+" grab keeps default metadata path="+sourceAdded);
        }
        List<Pulse> tails=pulses.stream().filter(pulse->!pulse.insideHurt).toList();
        int expected=rawRejected||grab||!heavy&&kind==9?0:kind==7?0:hurtResult?2:1;
        check(tails.size()==expected,scene.id+" detached living pulses="+tails.size()+" expected="+expected);
        double half=heavy?StandStatFormulas.getHeavyAttackKnockback(caster.stand().getAttackDamage(),0)*0.5:counterAtImpact*0.5;
        if(!rawRejected&&!grab&&kind!=9)check(Math.abs(half-(heavy&&kind==5?0.25:0.5))<EPS,scene.id+" actual extra half pulse="+half);
        for(Pulse pulse:tails){
            check(Math.abs(pulse.strength-half)<EPS&&pulse.eventSeen&&!pulse.eventCanceled,scene.id+" exact detached native/event strength="+pulse.strength);
            double effective=pulse.eventStrength*(1-pulse.resistance);
            Vec3 ratio=new Vec3(pulse.x,0,pulse.z).normalize().scale(effective);
            Vec3 expectedVelocity=effective>0?new Vec3(pulse.before.x/2-ratio.x,pulse.grounded?Math.min(0.4,pulse.before.y/2+effective):pulse.before.y,pulse.before.z/2-ratio.z):pulse.before;
            check(pulse.after.distanceTo(expectedVelocity)<EPS,scene.id+" native resistance-aware sequential velocity expected="+expectedVelocity+" actual="+pulse.after);
        }
        if(kind==7){
            Vec3 direction=target.position().subtract(caster.stand().position());
            Vec3 expectedVelocity=new Vec3(direction.x,0,direction.z).normalize().scale(half);
            check(target.getDeltaMovement().distanceTo(expectedVelocity)<EPS,scene.id+" admitted nonliving refused-hurt tail="+target.getDeltaMovement());
        }
        if(rawRejected)check(pulses.isEmpty(),scene.id+" raw rejection has no native knockback calls");
        if(!tails.isEmpty())check(order.indexOf("hurt-end")>=0&&order.indexOf("detached")>order.indexOf("hurt-end"),scene.id+" detached pulses occur after virtual hurt");
        log("IMPACT "+scene.id+" hurt="+hurtResult+" counter="+counterAtImpact+" pulses="+pulses.size()+" order="+order+" velocity="+target.getDeltaMovement());
    }
    boolean hurt(Entity entity,DamageSource source,float amount,java.util.function.BooleanSupplier original){
        if(!recording||entity!=target||source.getDirectEntity()!=caster.stand())return original.getAsBoolean();
        scene.premise(source.getEntity()==caster.stand()||source.getEntity()==caster.user(),"Actual native owned Stand/user damage source");
        hurtCalls++;insideHurt=true;sourceAdded=((DamageSourceModified)source).jojo_ripples$addKnockback();order.add("hurt-start");
        try{return hurtResult=original.getAsBoolean();}finally{insideHurt=false;order.add("hurt-end");}
    }
    void knockback(LivingEntity entity,double strength,double x,double z,Runnable original){
        if(!recording||entity!=target){original.run();return;}
        Pulse pulse=new Pulse(entity,insideHurt,strength,x,z);pulses.add(pulse);order.add(insideHurt?"hurt-kb":"detached");pendingPulse=pulse;
        try{original.run();pulse.after=entity.getDeltaMovement();}finally{pendingPulse=null;}
    }
    void check(boolean passed,String detail){checks++;log((passed?"PASS ":"FAIL ")+detail);if(!passed)failures.add(detail);}
    void closeScene(){if(scene!=null){if(caster!=null)caster.stand().getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);scene.close();}}
    @Override public void close(){if(closed)return;closed=true;closeScene();}
    @Override public void testStructureLoaded(GameTestInfo test){}
    @Override public void testPassed(GameTestInfo test,GameTestRunner runner){close();}
    @Override public void testFailed(GameTestInfo test,GameTestRunner runner){close();}
    @Override public void testAddedForRerun(GameTestInfo oldTest,GameTestInfo newTest,GameTestRunner runner){close();}
    static final class ObservedCow extends Cow{
        final KingCrimsonBasicMeleeFixture scene;ObservedCow(KingCrimsonBasicMeleeFixture scene){super(EntityType.COW,scene.scene.level);this.scene=scene;}
        @Override public boolean hurt(DamageSource source,float amount){return scene.hurt(this,source,amount,()->super.hurt(source,amount));}
        @Override public void knockback(double strength,double x,double z){scene.knockback(this,strength,x,z,()->super.knockback(strength,x,z));}
    }
    static final class ObservedBoat extends Boat{
        final KingCrimsonBasicMeleeFixture scene;ObservedBoat(KingCrimsonBasicMeleeFixture scene){super(EntityType.BOAT,scene.scene.level);this.scene=scene;}
        @Override public boolean hurt(DamageSource source,float amount){return scene.hurt(this,source,amount,()->super.hurt(source,amount));}
    }
    static final class ObservedPlayer extends Player{
        final KingCrimsonBasicMeleeFixture scene;ObservedPlayer(KingCrimsonBasicMeleeFixture scene){super(scene.scene.level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"kc-basic-target"));this.scene=scene;}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isCreative(){return false;}
        @Override public boolean hurt(DamageSource source,float amount){return scene.hurt(this,source,amount,()->super.hurt(source,amount));}
        @Override public void knockback(double strength,double x,double z){scene.knockback(this,strength,x,z,()->super.knockback(strength,x,z));}
    }
    static void identity(GameTestHelper helper,Class<?> test){
        for(Class<?> type:new Class<?>[]{StandPower.class,StandEntity.class,EntityActionInstance.class,AbilityInput.class,DamageSourceModified.class,
                ConfigAbilityFactory.class,StandEntityHeavyPunchAbility.class,StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class,
                StandEntityPunchAbility.class,StandEntityPunchAbility.StandEntityPunch.class,KingCrimsonAddon.class,KingCrimsonHeavyPunchAbility.class,
                KingCrimsonGroundAdmissionFixture.class,KingCrimsonBasicMeleeFixture.class,test}){
            try(var bytes=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                String expected=System.getProperty("kc.basic.expected."+type.getName());var source=type.getProtectionDomain().getCodeSource();
                helper.assertTrue(source!=null&&(!Boolean.getBoolean("kc.basic.bound")||expected!=null)&&(expected==null||hash.equalsIgnoreCase(expected)),"Native class/archive binding "+type.getName());
                log("LOADED "+type.getName()+" source="+source.getLocation().toURI().toASCIIString()+" sha256="+hash);
            }catch(Exception error){throw new IllegalStateException("KC basic binding",error);}
        }
    }
    static void log(String message){System.out.println("KC-BASIC "+message);}
}
