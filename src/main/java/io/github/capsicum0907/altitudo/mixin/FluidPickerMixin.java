package io.github.capsicum0907.altitudo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.capsicum0907.altitudo.Anchors;
import io.github.capsicum0907.altitudo.Dimensions;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class FluidPickerMixin {
    @Inject(method = "createFluidPicker", at = @At("RETURN"), cancellable = true)
    private static void altitudo$letTheAquiferDecide(NoiseGeneratorSettings settings,
            CallbackInfoReturnable<Aquifer.FluidPicker> callback) {
        if (!Anchors.extendingCaves() || !settings.defaultFluid().is(Blocks.WATER)
                || !ours(settings)) {
            return;
        }
        Aquifer.FluidPicker vanilla = callback.getReturnValue();
        Aquifer.FluidStatus undecided =
                new Aquifer.FluidStatus(Anchors.DEEP_FALLBACK_LEVEL, settings.defaultFluid());
        // FluidStatus keeps its fields to itself, so the boundary is recomputed here.
        int deep = Math.min(Anchors.DEEP_FALLBACK_LEVEL, settings.seaLevel());
        callback.setReturnValue((x, y, z) -> y < deep ? undecided : vanilla.computeFluid(x, y, z));
        Anchors.noteFluidPicker();
    }

    private static boolean ours(NoiseGeneratorSettings settings) {
        Dimensions configured;
        try {
            configured = Dimensions.fromConfig();
        } catch (RuntimeException e) {
            return false;
        }
        return settings.noiseSettings().minY() == configured.minY()
                && settings.noiseSettings().height() == configured.height();
    }
}
