package com.lukarbonite.iseeyourchunks.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric implementation of {@link PlatformHelper}. This mod is Fabric-only, so the single
 * implementation lives in {@code common}; it centralizes every {@link FabricLoader} call behind the SPI.
 */
public final class FabricPlatformHelper implements PlatformHelper {

	@Override
	public Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}
}
