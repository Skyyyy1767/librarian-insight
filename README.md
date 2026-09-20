# Librarian Insight — Minecraft 1.21.1 Fabric

Librarian Insight displays nearby librarian enchanted-book trades on lecterns and provides detailed librarian information without changing trades, RNG, items, villagers, or lecterns.

## Client commands

The full command name and short alias expose the same settings:

- `/librarianinsight price [on|off]`
- `/li price [on|off]`
- `/librarianinsight color <black|white|red|green|blue|yellow|orange|purple|pink|cyan|gray|reset>`
- `/li color <black|white|red|green|blue|yellow|orange|purple|pink|cyan|gray|reset>`
- `/librarianinsight theme [light|dark]`
- `/li theme [light|dark]`

Client settings are stored in `config/librarian-insight.properties`. If that file is absent, settings from the previous `config/visible-librarian-trades.properties` filename are imported automatically. The legacy file is retained.

## Fabric build

Install the Fabric JAR on the client. The mod ID remains `librarian_insight` and the
mod version is `1.0.1.1+mc1.21.1`. No server installation is required.

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.116.17+1.21.1 or newer compatible 1.21.1 release |
| Fabric Loom | 1.17.21 |
| Gradle wrapper | 9.6.0 |
| Mappings | Official Mojang mappings for 1.21.1 |
| Java toolchain / bytecode | JDK 21 / Java 21 |
| JUnit | 5.12.2 (tests only) |

With JDK 21 configured in `JAVA_HOME`, run these commands from the repository root
(use `gradlew.bat` instead of `./gradlew` on Windows):

```sh
./gradlew build                 # Fabric build, tests, and JAR verification
./gradlew test                  # shared automated tests
./gradlew :fabric:runClient
```

The distributable JAR is:

- `fabric/build/libs/librarian-insight-fabric-1.0.1.1+mc1.21.1.jar`

The corresponding `-sources.jar` is for development. `verifyDistribution` runs with
`check`/`build` and checks Fabric metadata, Java 21 bytecode, shared resources/classes,
and the absence of NeoForge/Forge references or embedded JARs.

## Project architecture

- `common`: source-only module containing UI, rendering, trade queries and snapshots,
  calculations, commands, config persistence, textures, Fabric mixins,
  and the shared tests. It has no loader imports and produces no runtime JAR.
- `fabric`: Fabric entrypoint, callbacks, config-directory lookup, command feedback,
  and `fabric.mod.json`.
- `gradle/loader.gradle`: shared compilation, tests, packaging, and publication rules.

Fabric compiles the common sources against Minecraft 1.21.1 using official Mojang
mappings and remaps the distributable JAR to intermediary names. No runtime bridge,
reflection-based platform lookup, or additional compatibility library is used.
The possible-trades catalog reflects Minecraft 1.21.1 content; later enchantments
such as Lunge are absent because they do not exist in this game version.

## Manual in-world validation

Title-screen startup does not exercise every gameplay path. In a Fabric world, verify
lectern placement/removal/replacement and chunk reloads; nearby trade discovery and
refresh; empty-lectern browsing and book placement; all three menu tabs, themes,
scrolling, and tooltips; merchant current/minimum overlays; lectern text and item
icons in all orientations; villager icons; profession changes; standard/rebalanced
trades and custom-trade fallbacks. Test integrated-server status and remote-server
limitations separately. Commands and config migration are covered by automated tests.

## Original project and license

Librarian Insight is inspired by Saphjyr's original [Visible Librarian Trades](https://github.com/Saphjyr/visible-librarian-trades) mod.

The original source and assets are Copyright © 2022 Saphjyr and are used under the MIT License. See [LICENSE](LICENSE).

All original code and modifications made for Librarian Insight are Copyright © 2026 Skyyyy and are licensed under the MIT License.
