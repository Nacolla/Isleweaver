# Isleweaver

[The Beyond](https://github.com/FloofHips/The-Beyond) reshapes the End into floating islands and sprawling, macro-scale biomes — but almost every other End mod was built for the *old* flat End, so their biomes, structures, and tooling no longer line up. **Isleweaver** weaves them back in: it settles foreign biomes onto Beyond's islands and smooths the rough edges, so nothing floats in the void or vanishes out of sight. Best of all, most of it is automatic — no config to touch.

> **Requires [The Beyond](https://github.com/FloofHips/The-Beyond).** It only acts while Beyond owns the End, and each mod-specific fix activates only when that mod is installed.

## Automatic compatibility

Any End mod whose biomes use the standard tags (`minecraft:is_end` or `c:is_end_biome`, shared by NeoForge and Fabric) is auto-discovered and merged into Beyond's End — no per-mod code, no hardcoded IDs. Fabric-port mods that inject through `TheEndBiomeSource` are caught too, and foreign surface rules are preserved so biomes keep their own look. Mods can extend the scan via `BeyondBiomeTagScanRegistry` (extra tags) or `BeyondBiomeDiscoveryEvent` (hand over biomes the scan can't see).

## Specifically-hooked mods

Some mods need hand-written fixes on top of the automatic layer. Each loads only when that mod is installed.

| Mod | What Isleweaver does |
|---|---|
| **Astrological** | Stops it overriding Beyond's sky/dimension and raises the End's height limits (-256 to 384) so both fit. |
| **Enderscape** | Lets its decorations fill the deep End below y=0 (where they used to vanish) and widens the End's range to -64–320. |
| **BetterEnd** | Makes its structures, plants, and spires follow Beyond's real terrain instead of floating — and yields the central island to Stellarity when both are present. |
| **Wover** | Blocks its terrain-generator takeover while keeping its per-biome surface styling and biomes. |
| **Stellarity** | Steps aside so its exit portal and altar build correctly — clearing the vanilla/BetterEnd pieces and default pillars first. |
| **BCLib** | Extends its structure search down to the bottom of the End so it reaches Beyond's deep islands. |
| **Quark** | Points its spire generator at the real island surface instead of the empty top of the world. |
| **Phantasm** | Adds a safety check so its dragon-mint plant skips foreign blocks instead of crashing. |
| **Dimensional Tears** | Turns off its End liquid layer so Beyond's auroracite islands stay exposed. |
| **Unusual End** | Gives the warped reef large coherent regions and places its floating features at island height. |
| **Oak's End / Oak Frontier** | Reads their Fabric-style biome list (invisible to Beyond) and feeds it into the End. |

## For developers

Isleweaver builds only on Beyond's public `com.thebeyond.api.*` — never internal classes — across **events**, **worldgen**, **compat**, and **registry** (biome discovery, macro regions, surface rules, terrain samplers, coordinate translation). Each integration is a standalone module (`compat.<mod>` / `mixin.<mod>`) gated by `ModList.isLoaded(...)`, so one can never affect another.

The Beyond's API is resolved from CurseForge through CurseMaven, so building is just:

```bash
./gradlew build      # build the jar
./gradlew runClient  # launch with both mods
```

MC 1.21.1 · NeoForge 21.1.200 · mod id `isleweaver` · MIT. `the_beyond_version` in `gradle.properties` pins the API artifact (`0.0.4-InDev`).

## License

MIT — see [`LICENSE`](LICENSE).
