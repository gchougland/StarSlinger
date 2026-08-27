# Changelog

## [1.1.0] - 8/27/2026

### Changed

- **Hytale Update 6** — Targets server `>=0.6.0-pre.0 <0.7.0` and rebuilds against the new protocol.
- **Chunk and block APIs** — Astral Tether detection and Galaxy Bottle placement use section refs and `BlockOperations` instead of deprecated `World`/`WorldChunk` helpers.
- **Math types** — Positions, velocities, and debug shapes use JOML `Vector3d` / `Matrix4d` and `Rotation3f`.
- **Galaxy in a Bottle** — Drops the removed `IsUsable` block flag; throw usability comes from the item interaction.
- **Astral Tether rope** — Replaces the white debug line and sphere with a physics rope that sags on swing and stays taut on launch.
