package com.lukarbonite.iseeyourchunks.platform;

import net.minecraft.world.phys.Vec3;

import java.util.ServiceLoader;

/**
 * Client-only companion to {@link VersionHelper} for the version-specific API that touches client-only
 * classes ({@code Minecraft}/{@code Camera}). Kept separate so it is never loaded on a dedicated server:
 * its only caller ({@code ClientEntityVisibility}) is itself client-only, so {@link #INSTANCE} and the
 * implementation class are resolved lazily and exclusively on the client.
 */
public interface ClientVersionHelper {

	ClientVersionHelper INSTANCE = ServiceLoader.load(ClientVersionHelper.class)
		.findFirst()
		.orElseThrow(() -> new IllegalStateException("No ClientVersionHelper implementation found"));

	/** Position of the main render camera, or {@code null} before the camera exists. */
	Vec3 cameraPosition();
}
