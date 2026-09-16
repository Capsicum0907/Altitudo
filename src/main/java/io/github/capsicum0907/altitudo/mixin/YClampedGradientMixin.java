package io.github.capsicum0907.altitudo.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.capsicum0907.altitudo.Anchors;

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
