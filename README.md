# BridgeFalls

BridgeFalls is a Minecraft plugin that adds structural stability rules to placed and nearby blocks.
If a block has no valid support, it can be denied on placement or marked unstable and made to fall later.

It is designed for **Paper/Folia-style servers** and includes configurable support rules, visual instability feedback, and in-game admin commands.

## Features

- **Support simulation** for placed/broken blocks (vertical, horizontal, top support, and anchor checks)
- **Anchor validation** with 3D connectivity (including diagonals)
- **Leaf support restriction**: leaves support only blocks with `LOG` in their material name
- **Unstable block lifecycle**: mark, warn, persist, and optionally convert to `FallingBlock`
- **Siege-aware falling mode** (optional): temporarily disables `falling-block` inside active Towny/SiegeWar war zones
- **Visual feedback** with configurable particle colors and warning sounds
- **Large material rule lists** for:
  - non-supporting vertical blocks
  - non-supporting horizontal blocks
  - always-stable blocks
  - always-stable-but-no-support blocks
  - floating-on-water support blocks
- **Gamemode exclusions** (for example Creative/Spectator)
- **Runtime configuration commands** (`/bf config ...`) with validation and reload
- **Folia-compatible scheduling** (`GlobalRegionScheduler` + per-region execution)
- **Persistence** of unstable blocks to `unstable-blocks.yml`

## Compatibility

- **Java**: 21
- **API target**: Paper API `1.21.11-R0.1-SNAPSHOT`
- **Declared supported Minecraft versions**: `1.20 - 1.21.11`
- `folia-supported: true` in `plugin.yml`

## Installation

1. Build or download the plugin jar.
2. Put the jar in your server `plugins/` folder.
3. Start the server once to generate default files.
4. Edit:
    - `plugins/BridgeFalls/config.yml`
    - `plugins/BridgeFalls/messages.yml`
5. Use `/bf reload` or restart the server.

## Commands

Main aliases:

- `/bf`
- `/bridgeFalls`
- `/bfCommand`
- `/bridgeFallsCommand`

Permission:

- `bridgefalls.admin` (default: op)

Core:

- `/bf help`
- `/bf enable`
- `/bf disable`
- `/bf reload`

Config commands:

- Radius
  - `/bf config support-radius <value>`
  - `/bf config top-support-radius <value>`
  - `/bf config anchor-support-radius <value>`
  - `/bf config anchor-support-radius-check-when-breaking <value>`
  - `/bf config anchor-max-time-ms <value>`
- Behavior
  - `/bf config fall-delay-minutes <minutes>`
  - `/bf config time-to-check <ticks>`
  - `/bf config time-to-check-anchor <ticks>`
  - `/bf config debug [true|false]`
  - `/bf config allow-placing-unstable-blocks [true|false]`
  - `/bf config falling-block [enable|disable]`
  - `/bf config falling-block disable-during-siege [true|false]`
  - `/bf config falling-block drop-item [true|false]`
  - `/bf config falling-block hurt-entities [true|false]`
- Lists (`list`, `add`, `remove`)
  - `no-rest-vertical`
  - `no-rest-horizontal`
  - `always-stable`
  - `always-stable-no-support`
  - `floating-support`
  - `instability-colors`
  - `disabled-gamemodes`

## How support works (high level)

BridgeFalls evaluates support from several angles:

1. **Direct vertical support**
    - checks the 3x3 area below a block
    - excludes air and materials listed in `no-rest-blocks-vertical`
  - leaves (`*_LEAVES`) are valid support only for blocks with `LOG` in their material name
2. **Horizontal support**
    - BFS-style search up to `support-radius`
    - only traverses blocks allowed as horizontal support providers
  - leaves (`*_LEAVES`) are traversable/valid only for blocks with `LOG` in their material name
3. **Top support**
    - optional upward check up to `top-support-radius`
4. **Anchor check**
    - connectivity test within `anchor-support-radius`
    - uses 3D neighboring blocks including diagonals

If support is missing:

- placement can be denied, or
- block is accepted but marked unstable (depending on config)

Marked unstable blocks are periodically re-evaluated. If still unstable after `fall-delay-minutes`, they can fall as `FallingBlock` entities.

## Configuration notes

Important keys in `config.yml`:

- `support-radius`
- `top-support-radius`
- `anchor-support-radius`
- `anchor-support-radius-check-when-breaking`
- `anchor-max-time-ms` (maximum `hasAnchor` evaluation time before assuming anchored)
- `fall-delay-minutes` (`0` means immediate fall)
- `time-to-check` (task interval in ticks)
- `time-to-check-anchor` (interval in ticks between `hasAnchor` checks for unstable blocks)
- `allow-placing-unstable-blocks`
- `falling-block`
- `falling-block-disable-during-siege` (if `true`, `falling-block` is temporarily treated as `false` in active Towny/SiegeWar war zones)
- `falling-block-drop-item`
- `falling-block-hurt-entities`
- `disabled-gamemodes`
- `instability-colors`
- `instable-color` (default outline when falling mode is disabled)

Messages are customizable in `messages.yml` with placeholders (for example `{block}`, `{radius}`, `{count}`, `{minutes}`).

Tip: keep `time-to-check-anchor` greater than or equal to `time-to-check` when you want less frequent anchor checks and lower CPU usage.

If you enable `falling-block-disable-during-siege`, BridgeFalls will auto-check Towny state by location and keep unstable blocks in warning mode (no FallingBlock conversion) while the related town/nation is in active war.

## Folia behavior

BridgeFalls uses a **global repeating task only as a dispatcher**.
All block/world reads are executed per location on the **region scheduler**, which is the safe model for Folia region threading.

## Build from source

```bash
# Unix/macOS
./gradlew clean shadowJar

# Windows
./gradlew.bat clean shadowJar
```

Output jar:

- `build/libs/BridgeFalls-<version>.jar`

Local Folia run task (project configured with run-paper):

```bash
# Unix/macOS
./gradlew runFolia

# Windows
./gradlew.bat runFolia
```

## Development quality tools

The project is configured with:

- Checkstyle
- PMD
- SonarQube plugin

## License

See `LICENSE.md`.
