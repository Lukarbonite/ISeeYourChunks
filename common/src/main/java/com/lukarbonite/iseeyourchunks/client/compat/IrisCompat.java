package com.lukarbonite.iseeyourchunks.client.compat;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.platform.PlatformHelper;

import java.lang.reflect.Method;

/**
 * Whether an Iris shader pack is active. A pack replaces vanilla fog entirely, so the far-entity fog passes stay out of
 * its way. Iris is optional, so its API is bound reflectively once.
 */
public final class IrisCompat {
	private static Method isShaderPackInUse;
	private static Object irisApi;
	private static boolean lookupDone;

	private IrisCompat() {
	}

	public static boolean shaderPackInUse() {
		if (!lookupDone) {
			lookupDone = true;
			if (PlatformHelper.get().isModLoaded("iris")) {
				try {
					Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
					irisApi = api.getMethod("getInstance").invoke(null);
					isShaderPackInUse = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException exception) {
					ISeeYourChunks.LOGGER.warn("Iris is installed but its API could not be bound; far-entity fog may double up.", exception);
				}
			}
		}
		if (isShaderPackInUse == null) {
			return false;
		}
		try {
			return (boolean) isShaderPackInUse.invoke(irisApi);
		} catch (ReflectiveOperationException exception) {
			return false;
		}
	}
}
