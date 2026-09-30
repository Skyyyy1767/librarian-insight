# Librarian Insight — Minecraft 26.3

Librarian Insight displays nearby librarian enchanted-book trades on lecterns and provides detailed librarian information without changing trades, RNG, items, villagers, or lecterns.

## Client commands

The full command name and short alias expose the same settings:

- `/librarianinsight price [on|off]`
- `/li price [on|off]`
- `/librarianinsight color <black|white|red|green|blue|yellow|orange|purple|pink|cyan|gray|reset>`
- `/li color <black|white|red|green|blue|yellow|orange|purple|pink|cyan|gray|reset>`
- `/librarianinsight theme [light|dark]`
- `/li theme [light|dark]`

Client settings are stored in `config/librarian-insight.properties`. Fresh configurations default to the dark menu theme. If that file is absent, settings from the previous `config/visible-librarian-trades.properties` filename are imported automatically; explicit saved light or dark preferences are preserved. The legacy file is retained.

## Librarian interface

Right-click an empty lectern to open the Librarian Insight reference screen. The
26.3 interface presents known unlocked offers, the complete possible-trade catalog,
and live librarian status in three immediately accessible tabs. The dedicated
enchanted-book browser uses a two-column catalog on suitable windows, a focused
results/detail flow on compact windows, prominent search, smooth mouse-wheel
scrolling, and draggable scrollbar thumbs. Recognizable trade inputs and outputs
use Minecraft's normal item rendering and tooltips.

The screen uses a Minecraft-native dark-oak, parchment, lapis, and gold visual
language with light and dark themes. Fast time-based entrance, hover, selection,
tab, and scroll transitions keep input responsive while clarifying state changes.
The layout derives every panel from the current scaled window size, shortens labels
where needed, changes the status view to one column on narrow screens, clips all
scroll regions, and keeps tooltips inside Minecraft's normal bounds. The interface
remains read-only; it only explains existing trade and villager state.

## Loaders and builds

Install the JAR for your loader on the client. The mod ID remains `librarian_insight`
and the mod version is `1.1+mc26.3`. No server installation is required.

| Component | Version |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.160.7+26.3 or newer compatible release (Fabric and Quilt) |
| Quilt Loader | 0.31.0-beta.4 or newer compatible release |
| NeoForge | 26.3.0.6-beta or newer compatible 26.3 release |
| Forge | 66.0.2 or newer compatible 26.3 release |
| Fabric Loom | 1.17.21 |
| NeoForge ModDevGradle | 2.0.147 |
| ForgeGradle | 7.0.40 |
| Gradle wrapper | 9.6.0 |
| Java toolchains / bytecode | JDK 25, JDK 21 for NeoForge test assets, and auxiliary JDK 8 for ForgeGradle's launcher / Java 25 |
| JUnit | 5.12.2 (tests only) |

Minecraft 26.3 supplies unobfuscated names, so none of the loaders need Yarn,
Parchment, remapping, or an Architectury runtime dependency.

With JDK 25 configured in `JAVA_HOME` and JDK 21 installed where Gradle can detect
it, run these commands from the repository root (use `gradlew.bat` instead of
`./gradlew` on Windows):

```sh
./gradlew build                 # all four loaders, tests, and JAR verification
./gradlew :fabric:build         # independently build Fabric
./gradlew :quilt:build          # independently build Quilt
./gradlew :neoforge:build       # independently build NeoForge
./gradlew :forge:build          # independently build Forge
./gradlew test                  # shared test suite in all loader classpaths
./gradlew :fabric:runClient
./gradlew :quilt:runClient
./gradlew :neoforge:runClient
./gradlew :forge:runClient
```

The optional `-Pjava_version=26` selects JDK 26 for compilation, tests, and client
runs while retaining Java 25 bytecode. Keep JDK 25 installed as well: NeoForge's
Minecraft artifact tools independently require the game's Java 25 toolchain.
ModDevGradle's unit-test asset setup also requests JDK 21, so both JDK 21 and JDK 25
must remain discoverable for a complete `build` or `test`. CI installs both versions;
the `org.gradle.java.installations.fromEnv` setting lets Gradle use their versioned
`JAVA_HOME_*` variables. ForgeGradle's Slime Launcher also requests an auxiliary Java 8
toolchain; the Foojay resolver downloads it automatically. Mod compilation and every
Minecraft client still use Java 25.
Reimport the root Gradle project in IntelliJ after changing modules. Client runs
use separate `fabric/run`, `quilt/run`, `neoforge/run`, and `forge/run` directories; the old
root `run` folder is retained and is not migrated automatically.

ModDevGradle generates the NeoForge IntelliJ launch argument files during Gradle
project synchronization. A standalone `clean` removes them with the rest of
`neoforge/build`; reload the Gradle project before using `neoforge - Client`, or run
`./gradlew :neoforge:neoForgeIdeSync` to regenerate the complete IDE run state.

Distributable JARs:

- `fabric/build/libs/librarian-insight-fabric-1.1+mc26.3.jar`
- `quilt/build/libs/librarian-insight-quilt-1.1+mc26.3.jar`
- `neoforge/build/libs/librarian-insight-neoforge-1.1+mc26.3.jar`
- `forge/build/libs/librarian-insight-forge-1.1+mc26.3.jar`

The corresponding `-sources.jar` files are for development. `verifyDistribution`
runs with each loader's `check`/`build` task and checks metadata, Java 25 bytecode,
shared resources/classes, and absence of foreign loader references or embedded JARs.
CI builds all four loaders and uploads all four sets of artifacts.

## Project architecture

- `common`: source-only module containing UI, responsive layout and motion state,
  rendering, trade queries and snapshots,
  calculations, commands, config persistence, textures, all ten original mixins,
  and the shared tests. It has no loader imports and produces no runtime JAR.
- `fabric`: Fabric entrypoint, callbacks, config-directory lookup, command feedback,
  and `fabric.mod.json`.
- `fabric-quilt`: source-only Fabric API event adapter shared by the Fabric and Quilt
  entrypoints.
- `quilt`: Quilt entrypoint, Quilt config-directory lookup, `quilt.mod.json`, and a
  Quilt Loader client run using Fabric API for current Minecraft versions.
- `neoforge`: client-scoped NeoForge entrypoint, event adapters, config-directory
  lookup, command feedback, NeoForge metadata, and two lifecycle/interaction mixins.
- `forge`: client-scoped Forge entrypoint, event adapters, config-directory lookup,
  command feedback, Forge metadata, and two lifecycle/interaction mixins.
- `gradle/loader.gradle`: shared compilation, tests, packaging, and publication rules.

Each loader compiles the same common sources against its own Minecraft environment.
This catches loader-specific API patches during compilation without bundling one loader
into another loader's JAR. The loader adapters invoke shared methods directly; no runtime
bridge, reflection-based platform lookup, or additional runtime library is needed.

NeoForge and Forge block-use events occur inside packet prediction, so canceling them
alone would still send a use packet. Their interaction mixins invoke the shared empty-lectern
menu handler before prediction, preserving Fabric's spectator/world-border checks
and packet suppression. Their lifecycle mixins supply block-entity membership callbacks
for load, replacement, removal, and chunk unload. Both loader-specific mixin sets are
client-scoped and required. The ten original mixins and their required injection counts
are retained.
MixinExtras used by the NeoForge lifecycle hook is already provided by NeoForge;
the Forge implementation uses standard Mixin redirects and adds no runtime dependency.

To add another loader, add a loader module using the shared Gradle script and
implement the entrypoint, command feedback, config path, tick, entity/block-entity,
interaction, and screen callbacks. Review that loader's patched Minecraft methods
and event timing, particularly pre-packet cancellation and foreground drawing.
Shared vanilla mixins still need compatibility checks for each new loader/version.

Tooling references: [Fabric 26.3 guidance](https://fabricmc.net/2026/09/15/263.html),
[NeoForge 26.3 MDK](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle),
[ModDevGradle documentation](https://github.com/neoforged/ModDevGradle),
[Forge 26.3 downloads](https://files.minecraftforge.net/net/minecraftforge/forge/),
and [ForgeGradle 7 MDK examples](https://github.com/MinecraftForge/MDKExamples).

ForgeGradle 7 is a recently rewritten toolchain. Its first Forge build performs a
large Mavenizer/decompile setup, and its first client run downloads the Minecraft
asset index; later runs use the generated caches. ForgeGradle 7 currently requires
Gradle 9.3 or newer and uses `net.minecraftforge.gradle.merge-source-sets=true` to
present development classes and resources as one mod.

## Manual in-world validation

Title-screen startup does not exercise every gameplay path. On each loader, verify
lectern placement/removal/replacement and chunk reloads; nearby trade discovery and
refresh; empty-lectern browsing and book placement; all three menu tabs, themes,
scrolling, and tooltips; merchant current/minimum overlays; lectern text and item
icons in all orientations; villager icons; profession changes; standard/rebalanced
trades and custom-trade fallbacks. Test integrated-server status and remote-server
limitations separately. Exercise compact, normal, and large window sizes at several
GUI scales, including the one-column status layout and the searchable book browser.
Commands, config migration, and responsive layout bounds are covered by automated
tests.

## Original project and license

Librarian Insight is inspired by Saphjyr's original [Visible Librarian Trades](https://github.com/Saphjyr/visible-librarian-trades) mod.

The original source and assets are Copyright © 2022 Saphjyr and are used under the MIT License. See [LICENSE](LICENSE).

All original code and modifications made for Librarian Insight are Copyright © 2026 Skyyyy and are licensed under the MIT License.
