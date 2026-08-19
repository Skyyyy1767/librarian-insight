# Librarian Insight — Minecraft 26.2 Fabric mod

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

Release builds are named `librarian-insight-<version>+mc26.2.jar`.

## Original project and license

Librarian Insight is inspired by Saphjyr's original [Visible Librarian Trades](https://github.com/Saphjyr/visible-librarian-trades) mod.

The original source and assets are Copyright © 2022 Saphjyr and are used under the MIT License. See [LICENSE](LICENSE).
