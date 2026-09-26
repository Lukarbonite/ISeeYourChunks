package com.lukarbonite.iseeyourchunks.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * SPI abstraction for loader-specific (currently only Fabric) services.
 *
 * <p>Each loader module provides exactly one implementation and registers it via
 * {@code META-INF/services/com.lukarbonite.iseeyourchunks.platform.PlatformHelper}. {@link #get()}
 * resolves it on first call through {@link ServiceLoader}, which is safe to call from
 * {@code IMixinConfigPlugin.shouldApplyMixin} - before the mod entry point has run.
 */
public interface PlatformHelper {

	/** The loader's config directory (e.g. {@code .minecraft/config}). */
	Path getConfigDir();

	/** Whether a mod with the given id is loaded. Safe to call during the mixin stage. */
	boolean isModLoaded(String modId);

	// --- Singleton accessor (lazy-loaded via ServiceLoader) ---

	PlatformHelper[] INSTANCE = {null};

	static PlatformHelper get() {
		if (INSTANCE[0] == null) {
			INSTANCE[0] = ServiceLoader.load(PlatformHelper.class)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
					"No PlatformHelper implementation found on the classpath. Ensure the loader JAR provides "
						+ "META-INF/services/com.lukarbonite.iseeyourchunks.platform.PlatformHelper"));
		}
		return INSTANCE[0];
	}
}
