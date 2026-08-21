package com.github.standobyte.jojo.powersystem;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.config.JsonConfigurable;
import com.github.standobyte.jojo.api.stand.StandMovesetExtensions;
import com.github.standobyte.jojo.powersystem.ability.controls.ControlSchemeTemplate;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;
import com.github.standobyte.jojo.util.objects_java.DefaultedValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class PowerType implements JsonConfigurable {
	protected final DefaultedValue<MovesetBuilder> movesetConfigured;
	protected Moveset baseMoveset;
	private ResourceLocation baseMovesetExtensionTarget;
	private long baseMovesetExtensionRevision;
	
	public PowerType(MovesetBuilder defaultMoveset) {
		this.movesetConfigured = new DefaultedValue<>(defaultMoveset);
		initBaseMoveset();
	}
	
	@Nonnull public abstract PowerData newDataInstance();
	
	public abstract ResourceLocation getId();
	
	public abstract PowerClass<?> getPowerClass();
	
	public boolean isEnabled() {
		return true;
	}
	
	public abstract Component getName(Power<?> power);
	
	
	@Override
	public JsonObject makeConfigTemplate() {
		JsonObject json = new JsonObject();
		
		Moveset defaultMoveset = makeExtendedMovesetBuilder(
				movesetConfigured.defaultValue).build(getPowerClass(), getId());
		JsonElement movesetJson = Moveset.toJson(defaultMoveset);
		json.add("moveset", movesetJson);
		
		return json;
	}
	
	// XXX (data-driven stands) control scheme in data-driven stands
	@Override
	public void applyConfig(JsonElement json) {
		JsonObject config = json.getAsJsonObject();
		
		JsonObject movesetEditsJson = config.getAsJsonObject("moveset");
		if (movesetEditsJson != null) {
			MovesetBuilder configured = makeExtendedMovesetBuilder(
					movesetConfigured.defaultValue);
			Moveset.applyJsonConfig(configured, movesetEditsJson);
			movesetConfigured.value = configured;
		}
		else {
			movesetConfigured.reset();
		}
		initBaseMoveset();
	}
	
	@Override
	public void restoreDefaults() {
		movesetConfigured.reset();
		initBaseMoveset();
	}
	
	
	@ApiStatus.Internal
	public MovesetBuilder getDefaultMoveset() {
		return this.movesetConfigured.defaultValue;
	}
	
	protected void initBaseMoveset() {
		ResourceLocation extensionTarget = getMovesetExtensionTargetId();
		this.baseMoveset = makeExtendedMovesetBuilder(
				this.movesetConfigured.value).build(getPowerClass(), getId());
		this.baseMovesetExtensionTarget = extensionTarget;
		this.baseMovesetExtensionRevision =
				getMovesetExtensionRevision(extensionTarget);
	}

	public Moveset getBaseMoveset() {
		ResourceLocation extensionTarget = getMovesetExtensionTargetId();
		if (!Objects.equals(
					baseMovesetExtensionTarget, extensionTarget)
				|| baseMovesetExtensionRevision
						!= getMovesetExtensionRevision(
								extensionTarget)) {
			initBaseMoveset();
		}
		return baseMoveset;
	}
	
	public Moveset makeMoveset(Power<?> userPower) {
		Moveset moveset = makeExtendedMovesetBuilder(
				this.movesetConfigured.value).build(getPowerClass(), getId());
		return moveset;
	}

	@ApiStatus.Internal
	public ControlSchemeTemplate makeDefaultControlSchemeTemplate() {
		MovesetBuilder moveset = makeExtendedMovesetBuilder(
				movesetConfigured.defaultValue);
		moveset.prepareControlSchemes();
		var schemeIter = moveset.controlSchemes.values().iterator();
		ControlSchemeTemplate merged = schemeIter.hasNext()
				? schemeIter.next().deepCopy()
				: new ControlSchemeTemplate();
		while (schemeIter.hasNext()) {
			ControlSchemeTemplate copy = schemeIter.next().deepCopy();
			for (var groupEntry : copy.groups.entrySet()) {
				ControlSchemeTemplate.GroupTemplate sourceGroup =
						groupEntry.getValue();
				ControlSchemeTemplate.GroupTemplate targetGroup =
						merged.groups.computeIfAbsent(
								groupEntry.getKey(),
								name -> new ControlSchemeTemplate.GroupTemplate(
										name, sourceGroup.toggleHudKey));
				for (var bindEntry :
						sourceGroup.separateBinds.entrySet()) {
					if (!targetGroup.separateBinds.containsKey(
							bindEntry.getKey())) {
						targetGroup.separateBinds.put(
								bindEntry.getKey(), bindEntry.getValue());
						sourceGroup.additionalSeparateBinds.stream()
								.filter(bind -> bind.ability().equals(
										bindEntry.getKey()))
								.forEach(targetGroup.additionalSeparateBinds::add);
					}
				}
				targetGroup.hotbars.addAll(sourceGroup.hotbars);
			}
		}
		return merged;
	}

	protected ResourceLocation getMovesetExtensionTargetId() {
		return null;
	}

	protected long getMovesetExtensionRevision(
			ResourceLocation extensionTarget) {
		return StandMovesetExtensions.targetRevision(
				extensionTarget);
	}

	protected void applyMovesetExtensions(
			ResourceLocation extensionTarget,
			MovesetBuilder moveset) {
		StandMovesetExtensions.applyRegisteredExtensions(
				extensionTarget, moveset);
	}

	@ApiStatus.Internal
	public long getCurrentMovesetExtensionRevision() {
		return getMovesetExtensionRevision(
				getMovesetExtensionTargetId());
	}

	private MovesetBuilder makeExtendedMovesetBuilder(
			MovesetBuilder source) {
		MovesetBuilder copy = source.deepCopy();
		ResourceLocation extensionTarget = getMovesetExtensionTargetId();
		if (extensionTarget != null) {
			applyMovesetExtensions(extensionTarget, copy);
		}
		return copy;
	}
	
	
	public Map<String, ? extends UnlockableSkill> getUnlockableSkills() {
		return makeExtendedMovesetBuilder(
				this.movesetConfigured.value).unlockableSkills;
	}
	
}
