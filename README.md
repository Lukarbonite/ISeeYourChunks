# I See Your Chunks

![Fabric](https://img.shields.io/badge/modloaders-fabric-blue?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/minecraft-26.2-green?style=for-the-badge)
![Clients need Voxy](https://img.shields.io/badge/clients_need-voxy-red?style=for-the-badge)
![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**I See Your Chunks** renders distant players standing on **real terrain**, far past your render distance. Instead of a player model floating in empty space at the edge of the world, the server streams the chunks that player is standing in, and your client draws them as actual geometry — with real occlusion, live block updates, and correct depth against your LOD terrain.

> ⚠️ **Clients need [Voxy](https://modrinth.com/mod/voxy).** Voxy is what renders the streamed terrain. Without it a client still shows distant players and mobs, but floating with no ground under them. Dedicated servers do **not** need Voxy (it's a client-only mod). See [How It Works](#️-how-it-works).

---

## ✨ Features

### Core Functionality
- **🌍 Real Terrain, Not Ghosts:** Distant players are rendered on the actual chunks they occupy, not floating in the void
- **🚫 Zero Generation Cost:** Only chunks **already loaded** on the server are streamed — nothing new is generated, loaded, or ticked
- **🔄 Live Block Updates:** Changes inside streamed chunks are forwarded incrementally, so far terrain never goes stale
- **🎯 Correct Occlusion:** Because the terrain is genuinely present client-side, a player behind a hill is actually hidden — no partial-occlusion guesswork
- **📡 Opt-In Handshake:** Clients announce themselves; the server sends nothing to anyone who didn't ask

### Advanced Features
- **👁️ Mobs on Shared Ground:** Mobs sitting in the terrain streamed around a viewed player are shown too, standing on that same real ground — never floating in isolation
- **🐴 Mounts Ride Along:** Whatever a viewed player is riding is always shown with them — the boat, the horse, the mount's mount — even at zero terrain, so nobody sits on thin air
- **🎚️ Adjustable Terrain Footprint:** A slider picks how many chunks surround each viewed player, growing nearest-first from just their chunk out to a radius, capped at the server's render distance
- **🛡️ Vanish-Aware:** Spectators, invisible, and vanished players (melius-vanish) are filtered server-side before any bandwidth is spent
- **📊 Bandwidth Bounded:** Hard caps on streamed chunks per viewer, a configurable footprint, and a configurable update interval
- **🤝 Degrades Gracefully:** Vanilla client on a modded server, or modded client on a vanilla server — both fall back to plain vanilla behaviour

---

## ⚙️ How It Works

The mod is split cleanly across the network boundary:

1. **Client → Server (handshake):** On join, the client sends how far out it wants distant players revealed and how many chunks of terrain it wants around each one. The server replies with an ack — its own render distance and whether streaming is on — so the client can keep its slider and chunk storage in step with reality. A vanilla client never sends the handshake and receives nothing.
2. **Server (streaming):** Each tick interval, the server finds revealable players within that distance and sends the chunks nearest each one — grown nearest-first from the chunk they stand in (default 3×3), capped at the server's render distance — plus a one-chunk **neighbour halo** around that patch. Only already-resident chunks are sent, and only those vanilla hasn't already sent to that viewer. Mobs standing in the visible patch (not the halo) are revealed alongside the terrain.
3. **Client (acceptance):** The client's chunk-cache storage radius is widened so those out-of-range chunks are accepted instead of dropped.
4. **Client (Voxy hand-off):** Each streamed far chunk is handed straight to Voxy's ingest as it arrives, and Voxy draws it through its own far renderer, correctly depth-sorted against its LODs.

> **Why Voxy (on clients):** streamed chunks lie beyond Sodium's render distance, so Sodium never compiles them into meshes. Voxy is what actually puts that terrain on screen — without it, the terrain half of this mod does nothing and distant players float in the void. It is required on clients for that reason, but only there: a dedicated server does the streaming and never renders anything, so it neither needs nor can load Voxy (a client-only mod). The client logs a loud warning if Voxy is missing.

> **Why the neighbour halo:** Voxy's LOD mesher reads each chunk's neighbours to build it, so a chunk with un-streamed neighbours can't mesh and stays invisible — which would leave only the inner part of any patch rendering. Streaming one extra ring around the requested patch gives every visible chunk its neighbours; the halo itself is never drawn and never reveals mobs.

---

## 📥 Installation

Install the JAR on **both the server and the clients.** The server does the tracking and streaming; the client removes the rendering limits.

### Required (both sides)
- **Fabric Loader** 0.16.12+
- **Fabric API**
- **Java 25**

### Required on clients
- **Voxy** — draws the streamed far terrain (client-only mod; dedicated servers don't need or want it)

### Optional
- **Mod Menu** — in-game access to the config screen

---

## 🖥️ Configuration

Settings live in `config/i-see-your-chunks.json` and can be edited in-game through **Mod Menu**.

| Option | Description | Default |
|:-------|:------------|:--------|
| `enabled` | Master toggle — disables the mod entirely if false | `true` |
| `renderRemotePlayers` | Render distant players (and stream their chunks) | `true` |
| `renderRemoteEntities` | Render distant mobs that sit in streamed terrain | `true` |
| `visibilityDistanceBlocks` | How far out distant players are revealed | Infinite |
| `chunkRenderCount` | Chunks of terrain around each viewed player (0 = none, nearest-first) | `9` (3×3) |
| `streamFarChunks` | Server-side: stream terrain around revealed players | `true` |
| `sendSpectators` | Server-side: reveal players in spectator mode | `false` |
| `updateIntervalTicks` | Server-side: ticks between streaming passes (1–40) | `5` |

> **Note:** `streamFarChunks`, `sendSpectators`, and `updateIntervalTicks` only take effect on the machine running the server logic. The rest are client-side. At `chunkRenderCount = 0` no terrain is sent, so only the viewed players — and whatever they are riding — appear; loose mobs are shown only where there is streamed ground to stand on.

Changing the config re-sends the handshake immediately, so streaming adjusts without a reconnect.

---

## ✅ Compatibility

| Mod | Status | Notes |
|:----|:------:|:------|
| **Voxy** | **Client-required** | Renders the streamed far terrain; required on clients, not needed on dedicated servers |
| **Sodium** | Full | No coupling to Sodium internals — nothing to break on update |
| **Iris / shaders** | Full | Streamed chunks are ordinary terrain, so shaders treat them normally |
| **melius-vanish** | Full | Vanished players are never revealed (reflective bridge, fails open) |
| **C2ME** | Compatible | Faster generation doesn't change what's streamed — only already-loaded chunks are sent |

### 📋 Technical Details

The mod injects at these points:

- `ChunkMap$TrackedEntity.updatePlayer()` — lifts the tracking-distance cap and chunk requirement per viewer: unconditionally for players and whatever they ride (mount chains included), and for a loose mob only while it sits in a chunk that actually renders for that viewer (the visible patch, never the halo)
- `ChunkMap.tick()` — periodically re-evaluates managed entities against every player, since a mob's visibility flips as the streamed region around a distant player slides over or off it
- `ChunkHolder.broadcast()` — forwards per-chunk block/light/block-entity updates to viewers streaming that chunk far away
- `ClientChunkCache.<init>` **and** `updateViewRadius()` — widens the client storage radius so streamed chunks aren't rejected as out-of-range (both are required: the radius normally arrives in the login packet, before `updateViewRadius` would ever fire)
- `ClientChunkCache.replaceWithPacketData()` — hands each arriving far chunk to Voxy's `VoxelIngestService`, because Voxy otherwise only ingests chunks when they *unload*, and streamed chunks never unload
- `LevelRenderer.isSectionCompiledAndVisible()` — lets an entity render in a section Sodium never compiled, because Voxy is drawing that ground instead
- `EntityRenderDispatcher.shouldRender()` / `Entity.shouldRenderAtSqrDistance()` — distance-limit overrides that still respect the frustum

The client↔server handshake (`ClientHelloPayload` in, `ServerAckPayload` back) carries preferences and the server's actual limits; nothing here relies on chunk generation, so it stays compatible with generation-side mods.

> **Terrain range is memory-bound.** Streamed chunks pass through the vanilla client chunk cache, whose storage radius is capped (~48 chunks / 768 blocks from the viewer) to bound memory. A viewed player farther than that still renders as a player, but their surrounding terrain cannot — real chunks can't be held arbitrarily far from you the way a distant player marker can.

---

## 🗂️ Project Layout

```
26.2/src/       version-common code (config, networking, streaming, mixins, client logic)
26.2/fabric/    Fabric entry points and loader-specific compat
```

## 🔨 Building

```
./gradlew :mc26_2-fabric:build
```

The JAR is written to `26.2/fabric/build/libs/`.

---

## 📜 License

This project is licensed under the **GNU Affero General Public License v3.0** (AGPL-3.0).

You are free to:
- ✅ Use in modpacks
- ✅ Modify for personal use
- ✅ Distribute modified versions (must also be AGPL-3.0)

See the [LICENSE](LICENSE) file for full details.
