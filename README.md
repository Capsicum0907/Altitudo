# Altitudo

A taller world, with the generation following it up and down.

*Altitudo* is the Latin for both height and depth. One word for the distance up and
the distance down, which is the pair of numbers this mod exists to move.

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.248 |
| Java | 21 |

## What it does

The overworld runs from **-2032 to 2031** and the Nether from **-128 to 1023**, and
the things that make a world worth digging follow them:

- **Caves** reach the new floor, instead of the space below -64 being shapeless
  lava-filled voids.
- **Ore** keeps vanilla's density at every depth, and gets richer further down.
- **The Nether's roof** rises with the terrain, with the same 128 blocks of building
  space above it that vanilla leaves.
- **The lava sea** is as deep as the floor is low - 160 blocks instead of 32.

Other mods' ores come along, because an ore is recognised by being configured as
one rather than by a list of ids. A mod that wants to be left alone can say so:
[docs/for-mod-authors.md](docs/for-mod-authors.md).

## What it does not do

Altitudo keeps Minecraft coherent in a taller world. It does not add content, and it
does not reshape terrain. If the added space should also be *more interesting* than
vanilla - new structures, new ores, a different landscape - that is another mod's
job, not this one's.

## Settings

`config/altitudo-startup.toml`, read before any world exists. A world's height is
fixed when it is created, so changing these needs a restart and only affects new
worlds.

| | default | vanilla | |
|---|---:|---:|---|
| `minY` | -2032 | -64 | Lowest block of the overworld |
| `height` | 4064 | 384 | Overworld height |
| `seaLevel` | 63 | 63 | Ocean surface. Moves the water, not the land |
| `extendCaves` | true | | Carry the cave bounds down to `minY` |
| `followOres` | true | | Repeat vanilla's bands into the added space |
| `oreAnchor` | 0 | | Nothing is added at or above this height |
| `deepOreBonus` | 4.5 | 1.0 | Ore density at `minY`, as a multiple of vanilla |
| `extendNether` | true | | Extend the Nether as well |
| `netherMinY` | -128 | 0 | Lowest block of the Nether; also the lava sea's depth |
| `netherHeight` | 1024 | 128 | How much of the Nether is generated |
| `netherRoofGap` | 128 | 128 | Empty space kept above the Nether's roof |

`minY`, `height` and `netherRoofGap` are multiples of 16; `minY + height` may not
exceed 2032. Anything the game would reject is refused at startup with a line in the
log, and the world generates at vanilla height rather than crashing halfway through
creation.

## Documentation

- [How it works](docs/how-it-works.md) - the mechanism, and why each piece is where
  it is
- [For mod authors](docs/for-mod-authors.md) - what Altitudo touches, and how to opt
  out of it

## Build

```
run.bat                   # compile and launch a dev client - double-clickable
gradlew build             # produce the jar
gradlew runGameTestServer # run every game test, headless, then exit
```

`JAVA_HOME` must point at a JDK 21, or `java` must be on `PATH`.

## License

MIT, the same as the rest of the set. See [LICENSE](LICENSE).
