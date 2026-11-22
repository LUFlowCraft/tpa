# TPA (Fabric, Minecraft 26.3)

Port des Paper-Plugins `tpa-1_0_0-paper-26_3.jar`.

## Bauen
Voraussetzungen: JDK 25, Internet (Loom laedt Minecraft + Fabric API).

    gradle wrapper --gradle-version 9.5.1   # einmalig, falls kein gradlew vorhanden
    ./gradlew build

Ergebnis: `build/libs/tpa-1.0.0+26.3.jar`

## Installieren (Server)
`tpa-1.0.0+26.3.jar` und **Fabric API (0.161.0+26.3)** in den Ordner `mods/`.
Server-only: Spieler brauchen keine Mod.

## Befehle
/tpa <Spieler> | accept | refuse | enable | disable
/tpahere <Spieler> | accept | refuse | enable | disable
