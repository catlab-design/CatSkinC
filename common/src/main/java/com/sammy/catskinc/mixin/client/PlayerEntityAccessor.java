package com.sammy.catskinc.mixin.client;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Avatar.class)
public interface PlayerEntityAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> Catskinc$getPlayerModelParts() {
        throw new AssertionError();
    }
}



