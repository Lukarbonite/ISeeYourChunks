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
- **🔄 Live Block Updates:** When a block changes in a streamed chunk the server re-sends that whole chunk and the client re-ingests it, so distant terrain updates in place — torches, lava, and builds all appear live
- **♾️ Far Past the Render Sphere:** Terrain renders well beyond Voxy's own render sphere by injecting only the streamed columns as extra render roots — no cost for the empty disc in between. Voxy's projection far plane is pushed out from its hardcoded 48,000, and distant players are held to the same reach, so a viewed player and the ground under them always clip together. That reach tracks the visibility-distance slider live — up to 1,024,000 blocks at the slider's maximum — so raising the slider extends both terrain and players immediately, with no reconnect. The 1,024,000 ceiling is a deliberate "more than enough" cap, not a technical limit (reverse-Z depth means it could go further at no real cost)
- **💡 Real Lighting:** Far terrain is lit from the chunk packet's actual block + sky light, so torches and lava glow and shadows fall correctly, instead of a flat approximation
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
4. **Client (Voxy hand-off):** Each streamed far chunk is converted straight into Voxy's LOD store as it arrives — using the real block + sky light from the chunk packet — and Voxy draws it through its own far renderer, correctly depth-sorted against its LODs. Columns beyond Voxy's render sphere are additionally injected as extra render roots so they draw at any distance without inflating the sphere.
5. **Live updates:** When a block changes in a streamed chunk, the server marks that chunk (and only chunks it is actively streaming) dirty and re-sends the whole chunk on the next interval; the client re-ingests it and Voxy re-meshes that column, so distant edits appear without a reconnect.

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
| `visibilityDistanceBlocks` | How far out distant players are revealed (rendering reaches at most 1,024,000 blocks) | Maximum |
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
- `ChunkHolder.broadcast()` — marks a streamed far chunk dirty when it changes, so the streamer re-sends the whole chunk on its next interval (only chunks it is actively streaming are ever tracked; a full re-send is used because the client can't apply incremental deltas to an out-of-range chunk it never cached)
- `ClientChunkCache.<init>` **and** `updateViewRadius()` — widens the client storage radius so streamed chunks aren't rejected as out-of-range (both are required: the radius normally arrives in the login packet, before `updateViewRadius` would ever fire)
- `ClientChunkCache.replaceWithPacketData()` — converts each arriving far chunk into Voxy's LOD store directly, supplying our own light. Voxy's normal ingest reads the *client* light engine, which never lights a chunk past render distance (so it would silently write nothing); instead it is run through Voxy's own conversion pipeline with the real light captured from the chunk packet. Chunks past the client's storage radius (a viewed player thousands of blocks away) are rejected by vanilla before decoding; those are re-decoded from the intact packet buffer into a throwaway chunk and ingested directly, so far terrain renders at any distance without being held in the cache
- `ClientPacketListener.handleLevelChunkWithLight()` — captures the chunk packet's real block + sky light so the ingest above can feed it to Voxy (vanilla otherwise discards that light for out-of-range chunks)
- `LevelRenderer.isSectionCompiledAndVisible()` — lets an entity render in a section Sodium never compiled, because Voxy is drawing that ground instead
- `EntityRenderDispatcher.shouldRender()` / `Entity.shouldRenderAtSqrDistance()` — distance-limit overrides that still respect the frustum
- `VoxyRenderSystem.computeProjectionMat()` — raises Voxy's hardcoded 48,000-block projection far plane to the shared far-render bound so injected far columns aren't clipped there (applied only when Voxy is present, gated by a mixin config plugin). The bound is computed live from the visibility-distance slider (48,000 floor, 1,024,000 ceiling) and this method runs every frame, so the reach follows the slider immediately; the managed-entity far plane uses the same bound, keeping terrain and the players on it in lockstep

The client↔server handshake (`ClientHelloPayload` in, `ServerAckPayload` back) carries preferences and the server's actual limits; nothing here relies on chunk generation, so it stays compatible with generation-side mods.

> **Terrain range.** Chunks within the client's storage radius (~48 chunks) pass through the vanilla cache normally. Beyond that, they're decoded straight into Voxy's LOD and never cached, so terrain around an arbitrarily distant player still renders — bounded by Voxy's own compact LOD store, not by held chunks. Columns past Voxy's render sphere are injected as extra render roots, and Voxy's distance cull is lifted for them, so they draw out to Voxy's projection far plane — which this mod raises from Voxy's hardcoded 48,000. That far plane is computed live from the visibility-distance slider (clamped to a 48,000 floor and a 1,024,000 ceiling at the slider's maximum) and shared with the managed-entity far plane, so terrain and the players on it reach the same distance and both follow the slider without a reconnect. The 1,024,000 ceiling is chosen simply as more than enough for any practical use, not because anything technical stops it there — pushing it out is nearly free on precision because the depth buffer is reverse-Z. Lighting uses the real block + sky light from each chunk packet; if a chunk ever arrives without light data, it falls back to a heightmap-based sky approximation.

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
