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
 * Geodes, dungeons and lichen use {@code height_range} as much as ores do, so
 * anything that is not an ore leaves here exactly as vanilla sampled it. Only ores
 * gain positions, and only below the anchor.
 * <p>
 * An ore's band is sampled against vanilla's extent rather than this world's, so a
 * band written as {@code above_bottom} stays where vanilla drew it instead of
 * sliding down to the new bedrock. See {@code OreBands.vanillaExtent}.
 */
@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementMixin {
    @Shadow
    @Final
    private HeightProvider height;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void altitudo$carryDown(PlacementContext context, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Stream<BlockPos>> callback) {
        if (!OreBands.enabled() || !OreBands.isOre(context)) {
            return;
        }
        int sampled = this.height.sample(random, OreBands.vanillaExtent(context));
        callback.setReturnValue(OreBands.positions(pos, sampled, random).stream());
    }
}
