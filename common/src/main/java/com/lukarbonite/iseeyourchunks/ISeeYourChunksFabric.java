package com.lukarbonite.iseeyourchunks;

import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfigManager;
import com.lukarbonite.iseeyourchunks.network.ISeeYourChunksNetworking;
import com.lukarbonite.iseeyourchunks.server.FarChunkStreamer;
import net.fabricmc.api.ModInitializer;

public final class ISeeYourChunksFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ISeeYourChunksConfigManager.load();
		ISeeYourChunksNetworking.registerCommon();
		FarChunkStreamer.register();

		ISeeYourChunks.LOGGER.info("{} is active.", ISeeYourChunks.MOD_NAME);
	}
}
