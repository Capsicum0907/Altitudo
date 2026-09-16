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
