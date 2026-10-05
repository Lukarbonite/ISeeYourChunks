package com.lukarbonite.iseeyourchunks.client;

import com.lukarbonite.iseeyourchunks.client.compat.CachedChunkEditIngest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** 1.20.1-only client entrypoint: drives {@link CachedChunkEditIngest}, which only this version needs. */
public final class CachedChunkEditsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> CachedChunkEditIngest.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CachedChunkEditIngest.reset());
	}
}
