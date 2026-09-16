package io.github.capsicum0907.altitudo.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.capsicum0907.altitudo.Bands;

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
 * Two separate things happen here.
 * <p>
 * Every band is sampled against vanilla's extent rather than this world's, so one
 * written as {@code above_bottom} stays where vanilla drew it instead of sliding
 * to the new bedrock. That is not an ore question: geodes, dungeons and lichen are
 * anchored that way too, and measured at zero in -64..0 without it, spread thin
 * over two thousand blocks instead. It holds whether or not the mod is following
 * ores, because it is the box that moved, not the generation.
 * <p>
 * Then copies are added, for whatever the dimension's plan allows - ores only in
 * the overworld, where the rest belongs to a surface that did not move; everything
 * in the Nether, which has no surface and where vanilla's own
 * {@code count_on_every_layer} features already scale with the height.
 */
@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementMixin {
    @Shadow
    @Final
    private HeightProvider height;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void altitudo$carryDown(PlacementContext context, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Stream<BlockPos>> callback) {
        Bands.Plan plan = Bands.planFor(context);
        if (plan == null) {
            return;
        }
        int sampled = this.height.sample(random, Bands.vanillaExtent(context, plan));
        if (!Bands.enabled() || (plan.oresOnly() && !Bands.isOre(context))) {
            callback.setReturnValue(Stream.of(pos.atY(sampled)));
            return;
        }
        callback.setReturnValue(Bands.positions(plan, pos, sampled, random).stream());
    }
}
