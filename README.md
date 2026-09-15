# Altitudo

A taller world, with the generation following it up and down.
*Altitudo* is the Latin for both height and depth. It is one word for the distance up and the distance down, which is the pair of numbers this mod exists to move.

> **Status: scaffold only.** The mod loads and does nothing.

## Target

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.248 |
| Java | 21 |

1.21.1 is the version large tech mods stayed on, so it is where this mod is useful.

## Design

A world's height lives in two places, and only their intersection is real:

```java
// WorldGenerationContext
this.minY   = Math.max(level.getMinBuildHeight(), generator.getMinY());
this.height = Math.min(level.getHeight(), generator.getGenDepth());
```

The box you can build in comes from `dimension_type`. The range terrain is generated
into comes from `noise_settings`. The Nether keeps them deliberately apart — a box of
256 over 128 of terrain — and the 128 left over is the space above the bedrock roof.

So changing a world's height is a few numbers. Making the world worth the space is
the whole job, and it splits by what is countable:

**The dimensions and the shape of the terrain** are finitely many known files —
`dimension_type`, `noise_settings`, and the density functions the two of them
reference. Those are generated: one setting per dimension, and the three to five
places that have to agree are derived from it rather than written out. The pack is
built in memory, so any depth is a value rather than one of six builds, and it
rewrites the numbers it needs inside vanilla's files instead of shipping copies of
them that go stale a version later.

**The bands written in absolute coordinates** are unboundedly many unknown ones.
Vanilla has 54 placed features anchored to a fixed Y, and every other mod that adds
an ore has its own. There is no list of them to hold, so Altitudo holds none: it
remaps where a band resolves to, at the one point every `height_range` placement
passes through. Nobody's feature is named, so everybody's travels.

That point is `HeightRangePlacement`, not `VerticalAnchor$Absolute#resolveY`. The
latter looks like the better place — one method, every absolute anchor in the game —
but surface rules resolve through it too, and their absolute coordinates describe
where the ground is, not how deep a vein sits. A function that has to know its caller
is in the wrong place.

Two levers, not one. Widening a band spreads the same number of veins over more
space, which thins it; the count has to scale with the range before any of this is
break-even, and only then can depth pay better than the surface.

Because the failure is silent — no error, no warning, just stone — the y-band census
is a test, not something you go and dig for.

## Build

```
run.bat                   # compile and launch a dev client - double-clickable
gradlew build             # produce the jar
gradlew runGameTestServer # run every game test, headless, then exit
gradlew runData           # regenerate models, recipes and language
```

`JAVA_HOME` must point at a JDK 21, or `java` must be on `PATH`.

## Roadmap

- [x] **0** — scaffold; the mod loads
- [ ] **1** — the feature above, in a form that can be watched
- [ ] **2** — checked by game tests rather than by eye

## License

Not decided yet. Until it is, the metadata says All Rights Reserved.
