package io.github.capsicum0907.altitudo.mixin;

import java.util.stream.IntStream;
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
 * Every mod's ores come through here as well, which is the point - there is no
 * list of them to hold.
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
        int moved = OreBands.stretch(sampled);
        int copies = OreBands.copies(moved, random);
        OreBands.note(sampled, moved, copies);

        if (copies == 1 && moved == sampled) {
            callback.setReturnValue(Stream.of(pos.atY(sampled)));
            return;
        }
        // Each copy samples again, so they spread through the stretched band instead
        // of stacking into one column - the band is what got longer, not the vein.
        callback.setReturnValue(IntStream.range(0, copies)
                .mapToObj(i -> pos.atY(i == 0 ? moved
                        : OreBands.stretch(this.height.sample(random, context)))));
    }
}
