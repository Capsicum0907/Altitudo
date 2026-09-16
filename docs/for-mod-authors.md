# For mod authors

Altitudo makes the world taller and then moves vanilla's own height-anchored
generation so the added space is not empty. Anything another mod registers goes
through the same code, which is usually what you want and sometimes is not.

## What Altitudo touches

Exactly one thing: **`minecraft:height_range` placement modifiers**, in dimensions
Altitudo resized. For each placement that passes through one:

1. The height is resolved against **vanilla's** extent rather than the world's, so a
   band written as `above_bottom` stays where vanilla drew it instead of sliding down
   to the new bedrock.
2. That placement is emitted unchanged, and copies are added one period apart through
   the rest of the world.

In the overworld only ore features are copied - anything configured with
`minecraft:ore`'s `OreConfiguration`, whoever registered it. In the Nether everything
is, because the Nether has no surface and vanilla's own `count_on_every_layer`
features already scale with the height there.

Nothing else is touched. Carvers, structures, surface rules and heightmap-based
placement reach their heights by their own route and are left alone.

## Opting out

Add your placed feature to the `#altitudo:keep` tag. Altitudo then leaves it exactly
as you wrote it - no resolve, no copies.

Ship this file in your own jar. Tags are keyed by the tag's namespace, not the
contributing mod's, so the path is Altitudo's either way and every mod's copy is
merged:

```
data/altitudo/tags/worldgen/placed_feature/keep.json
```

```json
{
  "replace": false,
  "values": [
    "yourmod:ore_example",
    "yourmod:strange_thing_that_belongs_at_y_11"
  ]
}
```

This is a plain data pack tag. It needs no dependency on Altitudo, no code, and no
soft-dependency declaration: if Altitudo is absent the tag is simply never read.
Players can use the same tag from a data pack to exclude anything they like.

Two things to get right, because Minecraft's tag loader is strict:

**These are placed features, not configured ones.** `minecraft:ore_diamond_small` is
a configured feature and has no placed feature of that name; the placed ones are
`ore_diamond`, `ore_diamond_medium`, `ore_diamond_large` and `ore_diamond_buried`.

**One missing id discards the whole tag,** with a single line in the log and no other
sign - every other entry stops working too. If an id might be absent, mark it
optional:

```json
{
  "replace": false,
  "values": [
    "yourmod:ore_example",
    { "id": "someothermod:ore_maybe", "required": false }
  ]
}
```

Opting out means vanilla's behaviour in a taller world, which is not the same as
vanilla's behaviour. An absolute band stays where you put it; a band written as
`above_bottom` follows the new floor all the way down, because that is what the game
does with it when nothing intervenes. Measured with diamond opted out: the absolute
`ore_diamond_medium` stayed at -64..-4, and the three relative ones ended up in the
bottom sixty blocks of the world.

Add to the tag when your feature's height means something absolute that should not
repeat - a band tied to a specific structure, a one-off vein at a fixed depth, or
anything you have already balanced against the whole world rather than against a
slice of it.

## Adding a dimension

Altitudo identifies dimensions by their extent, not their name, so a modded dimension
whose `min_y` and generation height happen to match Altitudo's configured overworld
or Nether is treated as that dimension. Any other dimension is left entirely alone.

There is no API to register a third dimension with Altitudo yet.
