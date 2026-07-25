package com.github.standobyte.jojo.modcompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class ReflectiveVampirismModIntegration implements IVampirismModIntegration {
	private static final String VAMPIRISM_API_CLASS = "de.teamlapen.vampirism.api.VampirismAPI";
	private static final String VREFERENCE_CLASS = "de.teamlapen.vampirism.api.VReference";
	private static final String HELPER_CLASS = "de.teamlapen.vampirism.util.Helper";

	private boolean apiFailed;
	private boolean helperFailed;
	@Nullable
	private Method factionRegistryMethod;
	@Nullable
	private Field vampireFactionField;
	@Nullable
	private Method getFactionMethod;
	@Nullable
	private Method isVampireMethod;

	@Override
	public boolean isEntityVampire(LivingEntity entity) {
		if (!apiFailed && isEntityVampireByApi(entity)) {
			return true;
		}
		return !helperFailed && isEntityVampireByHelper(entity);
	}

	private boolean isEntityVampireByApi(LivingEntity entity) {
		try {
			if (factionRegistryMethod == null || vampireFactionField == null) {
				Class<?> apiClass = Class.forName(VAMPIRISM_API_CLASS);
				factionRegistryMethod = apiClass.getMethod("factionRegistry");
				Class<?> referenceClass = Class.forName(VREFERENCE_CLASS);
				vampireFactionField = referenceClass.getField("VAMPIRE_FACTION");
			}
			Object registry = factionRegistryMethod.invoke(null);
			Object vampireFaction = vampireFactionField.get(null);
			if (registry == null || vampireFaction == null) {
				return false;
			}
			if (getFactionMethod == null || !getFactionMethod.getDeclaringClass().isInstance(registry)) {
				getFactionMethod = findSingleArgMethod(registry.getClass(), "getFaction", Entity.class);
			}
			Object entityFaction = getFactionMethod.invoke(registry, entity);
			return vampireFaction.equals(entityFaction);
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			apiFailed = true;
			return false;
		}
	}

	private boolean isEntityVampireByHelper(LivingEntity entity) {
		try {
			if (isVampireMethod == null) {
				Class<?> helperClass = Class.forName(HELPER_CLASS);
				isVampireMethod = findSingleArgMethod(helperClass, "isVampire", Entity.class);
			}
			Object result = isVampireMethod.invoke(null, entity);
			return Boolean.TRUE.equals(result);
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			helperFailed = true;
			return false;
		}
	}

	private static Method findSingleArgMethod(Class<?> owner, String name, Class<?> argumentType) throws NoSuchMethodException {
		Method fallback = null;
		for (Method method : owner.getMethods()) {
			if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> parameterType = method.getParameterTypes()[0];
			if (!parameterType.isAssignableFrom(argumentType)) {
				continue;
			}
			if (!Modifier.isPublic(method.getModifiers())) {
				method.setAccessible(true);
			}
			if (parameterType == argumentType) {
				return method;
			}
			fallback = method;
		}
		if (fallback != null) {
			return fallback;
		}
		throw new NoSuchMethodException(owner.getName() + "#" + name + "(" + argumentType.getName() + ")");
	}
}
