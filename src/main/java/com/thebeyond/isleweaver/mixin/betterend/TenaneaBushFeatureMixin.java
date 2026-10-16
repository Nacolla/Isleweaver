package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// also accept nylium ground: SpireFeatureMixin skins spire tops to nylium, which otherwise fails the c:end_stones check
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.bushes.TenaneaBushFeature", remap = false)
public abstract class TenaneaBushFeatureMixin {
    @WrapOperation(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z")
    )
    private boolean isleweaver$acceptNyliumGround(
            BlockState state, TagKey<Block> tag, Operation<Boolean> op) {
        boolean original = op.call(state, tag);
        if (!original && BeyondTerrainState.isActive() && state.is(BlockTags.NYLIUM)) {
            return true;
        }
        return original;
    }
}
