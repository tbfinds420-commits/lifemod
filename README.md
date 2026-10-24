# Life Mod (Fabric 1.20.1)

Bauen:  JDK 17 installieren, dann im Ordner `gradle build` (oder in IntelliJ den Task `build` starten).
Die fertige Mod liegt danach in `build/libs/lifemod-0.1.0.jar`.
Benötigt: Fabric Loader + Fabric API (läuft nur auf dem Server).

Geburt:   /life create <name> <geschlecht> <familie> <talent>
Leben:    /life status | school | jobs | job <name> | work | quit
Freunde:  /life friend <spieler> | friends | unfriend <name>
Liebe:    /life love <spieler> | breakup   (ab 18)
Kinder:   /life child <name> | children    (Paar, beide ab 18)
Anfragen: /life accept | decline

Zeit: Bis 18 dauert ein Lebensjahr 10 Minuten, ab 18 1 Stunde (LifeMod.java).

Ohne JDK bauen: Projekt in ein GitHub-Repository hochladen. Der Workflow
.github/workflows/build.yml baut die Mod automatisch (Reiter "Actions" -> Artifact "lifemod").
