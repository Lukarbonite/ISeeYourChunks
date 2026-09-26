package com.lukarbonite.iseeyourchunks.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches into {@code ChunkMap$TrackedEntity}, which is package-private with no public surface.
 * {@link ChunkMapMixin} needs both members to re-run tracking decisions on its own schedule.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
interface ChunkMapTrackedEntityAccessor {
	@Accessor("entity")
	Entity iSeeYourChunks$entity();

	@Invoker("updatePlayer")
	void iSeeYourChunks$updatePlayer(ServerPlayer player);
}
