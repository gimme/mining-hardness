# Mining Hardness

**The deeper and more buried a block is, the harder it is to mine.** Caves stay easy — tunneling through solid rock gets expensive.

![Logo](images/logo.png)

In vanilla, the optimal way to mine is also the most boring one: dig down to the right Y level and hold left-click. Mining Hardness changes that math. Every block's break time scales with two things: **how deep it is** and **how enclosed it is** by surrounding solid blocks. Blocks near the surface or exposed to open air mine exactly like vanilla. Blocks deep underground, sealed in rock on every side, become dramatically harder — slower to break, more food-draining, and rougher on your tools.

The result: caves become the smart way down. Natural tunnels and caverns are pre-carved paths through otherwise punishing rock, so exploring beats strip-mining — without banning anything.

Some numbers with default settings (above Y 62, everything is vanilla):

| Situation | Enclosure | At Y 0 | At Y −59 (diamond level) |
|---|---|---|---|
| Block exposed in an open cavern | low | ~1× | ~1× |
| Mining into a flat wall | ~65% | ~1.4× | ~1.7× |
| Tunneling a straight 1×1 hole | ~96% | ~6.5× | ~12× |

## How it works

Two factors combine into a hardness multiplier:

- **Depth** (0–1): scales linearly from sea level (Y 62) down to bedrock (Y −64). The Nether has no natural surface, so depth is maxed everywhere there by default.
- **Enclosure** (0–1): how surrounded the block is by solid blocks, scanned in a 5×5×5 area with closer blocks weighted more heavily. The value is raised to an exponent (default 7), so open-ish spaces barely register while tightly sealed rock ramps up sharply.

With default settings, the bonus hardness added on top of vanilla is:

```
15 × enclosure^7 × depth
```

Depth alone adds nothing — it *gates* the enclosure effect. But an optional depth-only bonus can be enabled in the config.

Harder blocks don't just break slower:

- **Hunger** — mining boosted blocks costs proportionally more exhaustion.
- **Tool durability** — tools take proportionally more damage on boosted blocks.

A soft cap dampens hardness above obsidian's level (50), so already-hard block types never become absurdly slow. The mod affects the Overworld and the Nether; other dimensions are untouched.

## Configuration

All settings live in `config/mininghardness-server.toml` on the server, generated on first run, and sync to clients automatically (the mod must be installed on both sides). Every option is documented in detail in the file itself, with concrete examples of what the defaults do. The highlights:

- `enclosure.maxBonus` / `enclosure.exponent` — overall strength of the effect and how sharply it ramps with enclosure.
- `depth.startY` / `depth.endY` — the Y range over which depth scales, per dimension.
- `depth.maxBonus` — extra hardness from depth alone, independent of enclosure (off by default).
- `effects.*` — how strongly exhaustion and tool damage scale with hardness.
- `scope.blockWhitelist` / `scope.blockBlacklist` — regexes to limit which blocks are affected. Example: blacklist `.*_ore` keeps all ores at vanilla hardness. Blacklisted blocks are also ignored by the enclosure scan, so they don't make their neighbors harder.
