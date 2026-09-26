package com.lukarbonite.iseeyourchunks.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The client's entity section manager, which decides which entities tick; see FarChunkEntityTicking. */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
	@Accessor("entityStorage")
	TransientEntitySectionManager<Entity> iSeeYourChunks$entityStorage();
}
