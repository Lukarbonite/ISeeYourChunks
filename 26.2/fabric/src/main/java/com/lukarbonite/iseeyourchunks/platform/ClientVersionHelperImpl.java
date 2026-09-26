package com.lukarbonite.iseeyourchunks.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** MC 26.2 (unobfuscated) implementation of {@link ClientVersionHelper}. Client only. */
public final class ClientVersionHelperImpl implements ClientVersionHelper {

	@Override
	public Vec3 cameraPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.mainCamera() == null) {
			return null;
		}
		return client.gameRenderer.mainCamera().position();
	}
}
