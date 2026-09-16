package io.github.capsicum0907.altitudo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.capsicum0907.altitudo.Anchors;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Stops the deep from being declared lava before anything has looked at it.
 * <p>
 * Vanilla already decides, per aquifer, whether a cavity is dry, a pool, or flooded,
 * and then whether that pool is water or lava. All three run below the old floor
 * too - but the fallback handed to them says "lava" outright, and the lava-or-water
 * step is guarded by {@code fluidType != LAVA}, so it never gets to choose. Ten
 * blocks of world below -54 made that invisible; two thousand do not.
 * <p>
 * The only thing changed here is what that fallback calls itself. The boundary, the
 * levels and the three-way decision are vanilla's.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class FluidPickerMixin {
    @Inject(method = "createFluidPicker", at = @At("RETURN"), cancellable = true)
    private static void altitudo$letTheAquiferDecide(NoiseGeneratorSettings settings,
            CallbackInfoReturnable<Aquifer.FluidPicker> callback) {
        // Water as the default fluid is what an overworld-shaped dimension looks like
        // from here; the Nether's is lava and its deep really is a lava sea, which is
        // the one case this must not touch.
        if (!Anchors.extendingCaves() || !settings.defaultFluid().is(Blocks.WATER)) {
            return;
        }
        Aquifer.FluidPicker vanilla = callback.getReturnValue();
        Aquifer.FluidStatus undecided =
                new Aquifer.FluidStatus(Anchors.DEEP_FALLBACK_LEVEL, settings.defaultFluid());
        // The same boundary vanilla tests, written out because FluidStatus keeps its
        // fields to itself and the answer cannot be read back off one.
        int deep = Math.min(Anchors.DEEP_FALLBACK_LEVEL, settings.seaLevel());
        callback.setReturnValue((x, y, z) -> y < deep ? undecided : vanilla.computeFluid(x, y, z));
        Anchors.noteFluidPicker();
    }
}
