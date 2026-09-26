package com.lukarbonite.iseeyourchunks.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** MC 1.21.1 (Mojang-mapped) implementation of {@link ClientVersionHelper}. Client only. */
public final class ClientVersionHelperImpl implements ClientVersionHelper {

	@Override
	public Vec3 cameraPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) {
			return null;
		}
		return client.gameRenderer.getMainCamera().getPosition();
	}
}
