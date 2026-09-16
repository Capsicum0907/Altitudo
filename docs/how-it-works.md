# How it works

## A world's height lives in two places

```java
// WorldGenerationContext
this.minY   = Math.max(level.getMinBuildHeight(), generator.getMinY());
this.height = Math.min(level.getHeight(), generator.getGenDepth());
```

The box you can build in comes from `dimension_type`. The range terrain is generated
into comes from `noise_settings`. Only their intersection acts, and vanilla keeps
them deliberately apart in the Nether: a box of 256 over 128 of terrain, and the 128
left over is the space above the bedrock roof.

Reading both dimensions side by side, they are one formula:

| | `min_y` | generated | box | roof gap | `logical_height` |
|---|---:|---:|---:|---:|---:|
| overworld | -64 | 384 | 384 | 0 | 384 |
| nether | 0 | 128 | 256 | 128 | 128 |

`box = generated + roof gap`, and `logical_height = generated`. The Nether is not a
different shape; it is the same shape with a gap above the roof.

That second identity matters. `logical_height` caps where a portal may be placed, and
`PortalForcer` reads it as `min(maxBuild, minY + logicalHeight) - 1`. Vanilla's 128 is
not the number 128 - it is "the roof". Keeping the literal while lowering the floor
would put the cap at y = -1, inside the lava sea, and the fallback branch would then
refuse to build a portal at all.

## The pack is computed, not shipped

`PackResources#getResource` only has to return an `InputStream`; it never has to be a
file. So the pack is built in memory from the config, which is why any depth is a
value rather than one of a handful of prebuilt data packs.

It reads vanilla's own `dimension_type` and `noise_settings` and changes the numbers
inside them, rather than carrying copies. Copies inherit every future change to those
files as a silent divergence, and overwrite whatever another data pack did to them.

The pack registers itself as required, at the top, and fixed there, so a new world
needs no clicking. Every rewrite is checked: a transform that matches nothing still
produces valid JSON and a world that generates, so "found no match" has to be an
error rather than a quiet pass-through.

## The terrain has to be told too

`noise.min_y` and `noise.height` say where terrain may generate. They do not say
where it stops being solid - that is two `y_clamped_gradient` slides, one at each
end, which vanilla builds from the range with fixed offsets and writes into the JSON
as literals.

Miss them and the failure is specific: past the ceiling slide the density is pinned
to a positive constant, so the added space fills with solid stone. A high roof over a
solid block of rock is exactly what happens if you raise the height and leave the
slides where they were.

The offsets differ per dimension (`slideOverworld` and `slideNetherLike`), so they
are held as offsets rather than as results, and one rewrite serves both.

## Caves

Two bounds decide where caves may exist, and both live in density functions vanilla
builds in code and never writes to disk - there is no file to rewrite. They are
caught as the functions are constructed, keyed on the whole tuple of values rather
than on the pair of heights, because the heights alone are not unique across the
shipped presets.

Below the old floor vanilla also hands the aquifer a fallback that says "lava"
outright, and the lava-or-water step is guarded by `fluidType != LAVA`, so it never
gets to choose. Ten blocks of world below -54 made that invisible; two thousand do
not. Altitudo changes only what that fallback calls itself - the boundary, the levels
and the three-way decision stay vanilla's.

## Bands

Vanilla writes where something may appear as a height, and those heights are written
against the world vanilla has. A taller world does not move them: an absolute band
stays where it was and leaves the rest empty, a relative one stretches across the
whole new range and thins by the same factor.

Both are fixed at the one point every `height_range` placement passes through:

1. **Resolve against vanilla's extent.** A band written as `above_bottom` then sits
   where vanilla drew it rather than where the floor moved to.
2. **Emit that placement unchanged, then add copies** one period apart through the
   range to be filled.

Because the original is emitted untouched, the density vanilla had cannot fall. That
is the whole invariant; the rest is which copies to make.

| | resolve against | slice repeated | range filled | copied | bonus |
|---|---|---|---|---|---|
| overworld | -64..320 | -64..0 | `minY`..0 | ores only | `deepOreBonus` |
| nether | 0..128 | 0..128 | `minY`..top | everything | none |

Having a surface or not sets all five. The overworld's surface did not move and has
nowhere new to go, so only the underground slice repeats and only downwards. The
Nether has no surface, so the whole of vanilla's range repeats in both directions.

The Nether gets no bonus because making the deep richer is adding rather than not
thinning, and this mod does not add.

### Why copies at all

Vanilla already scales some of its own content with height: everything placed by
`count_on_every_layer` finds more layers in a taller world and places more. In the
Nether that is the vegetation, the fungi and the basalt columns - eight times as much,
without anyone asking. Copying the `height_range` features makes the other
twenty-seven behave the way those ten already do. The rule is vanilla's, not this
mod's.

### Why `HeightRangePlacement`

Not `VerticalAnchor$Absolute#resolveY`, which looks like the better place - one
method, every absolute anchor in the game. Surface rules resolve through it too, and
their absolute coordinates describe where the ground is, not how deep a vein sits. A
function that has to know its caller is in the wrong place.

Copies are given a fresh x and z drawn from the chunk origin, which is the same
guarantee `in_square` gives, so no copy can reach a chunk that does not exist yet.

## What is left alone

Structures reach their heights by their own route: a nether fortress is placed at a
hardcoded y=64 and a bastion at `start_height: {"absolute": 33}`. In an extended
Nether they stay just above the lava sea. That is a consequence of the boundary
above - Altitudo keeps the world coherent, and where structures belong is a design
question for the game, not a defect in the height.
