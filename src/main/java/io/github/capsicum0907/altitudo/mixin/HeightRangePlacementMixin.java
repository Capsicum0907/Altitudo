package io.github.capsicum0907.altitudo.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.capsicum0907.altitudo.OreBands;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

/**
 * The one point every {@code height_range} placement passes through.
 * <p>
 * Chosen over {@code VerticalAnchor$Absolute#resolveY}, which looks like it
 * catches more: surface rules resolve through that one too, and their absolute
 * heights say where the ground is, not how deep a vein sits. A function that has
 * to know its caller is in the wrong place. Carvers and structures reach heights
 * by their own route and are left alone here.
 * <p>
 * ⚠ The same test applies to this point, and it is why nothing is duplicated
 * here. Geodes, dungeons and lichen use {@code height_range} as much as ores do,
 * and from here they are indistinguishable - an earlier version emitted a copy per
 * band length and one of those features reached into a chunk that did not exist
 * yet. Moving a position is safe for all of them; making more of them is not.
 */
@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementMixin {
    @Shadow
    @Final
    private HeightProvider height;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void altitudo$carryDown(PlacementContext context, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Stream<BlockPos>> callback) {
        if (!OreBands.enabled()) {
            return;
        }
        int sampled = this.height.sample(random, context);
        callback.setReturnValue(Stream.of(pos.atY(OreBands.place(sampled, random))));
    }
}
