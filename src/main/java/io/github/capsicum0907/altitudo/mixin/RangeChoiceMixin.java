package io.github.capsicum0907.altitudo.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.capsicum0907.altitudo.Anchors;

import net.minecraft.world.level.levelgen.DensityFunction;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$RangeChoice")
public abstract class RangeChoiceMixin {
    @Shadow
    @Final
    @Mutable
    private double minInclusive;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void altitudo$openDownwards(DensityFunction input, double minInclusive,
            double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange,
            CallbackInfo callback) {
        this.minInclusive = Anchors.rangeMin(minInclusive, maxExclusive);
    }
}
