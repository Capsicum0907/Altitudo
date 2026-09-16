package io.github.capsicum0907.altitudo.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.capsicum0907.altitudo.Anchors;

/**
 * Carries the cave band down to the new floor, once, as it is built.
 * <p>
 * The decision needs all four values at once - the pair of heights alone is not
 * unique across the shipped presets - so it is taken after the record is built
 * rather than on any single argument. The shape is constructed a handful of times
 * at load, so the cost is paid once; changing the answer in {@code compute} would
 * pay it per cell of every chunk instead.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$YClampedGradient")
public abstract class YClampedGradientMixin {
    @Shadow
    @Final
    @Mutable
    private int fromY;

    @Shadow
    @Final
    @Mutable
    private double fromValue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void altitudo$carryDown(int fromY, int toY, double fromValue, double toValue,
            CallbackInfo callback) {
        this.fromY = Anchors.gradientFromY(fromY, toY, fromValue, toValue);
        this.fromValue = Anchors.gradientFromValue(fromY, toY, fromValue, toValue);
    }
}
