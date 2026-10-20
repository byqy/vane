// Feldbezogene Hilfetexte für Admin-Formulare (de). Der Schlüsselsatz muss mit allen anderen Sprachen übereinstimmen (erzwungen durch field-help.test.ts).
// Getrennt von der seitenbezogenen Hilfe in locales/help/<locale>.ts; Zusammenführung siehe locales/help/index.ts.

const message = {
  "fieldHelp.open": "Feldhilfe",
  "field.navs.labelKey.help": "Textschlüssel des Menünamens, zum Beispiel nav.about. Der Schlüssel muss in den Sprachressourcen vorhanden sein, sonst zeigt das Portal den rohen Schlüssel.",
  "field.navs.target.help": "Relativer Pfad für den Klick, zum Beispiel /, /about oder /articles. Das Sprachpräfix ergänzt das Frontend automatisch, daher keine vollständige Domain eintragen.",
  "field.navs.sortNo.help": "Kleinere Werte stehen weiter oben; Menüs gleicher Ebene werden danach sortiert. Leer lassen für den Standardwert 0.",
  "field.pages.slug.help": "Teil der Seiten-URL (/{locale}/{slug}). Nach dem Veröffentlichen möglichst nicht ändern, sonst laufen alte Links ins 404.",
  "field.articles.slug.help": "Teil der Artikel-URL (/{locale}/articles/{slug}); beeinflusst auch bestehende Links und die Indexierung durch Suchmaschinen.",
  "field.articles.category.help": "Legt fest, auf welcher Kategorienseite der Artikel erscheint (/{locale}/categories/{code}). Kategorien sind einstufig, das Feld darf leer bleiben.",
  "field.articles.tags.help": "Artikel-Tags, Mehrfachauswahl möglich; das Portal kann nach Tags filtern. Fehlende Tags lassen sich direkt hier anlegen.",
  "field.articles.newTag.help": "Tag-Code eingeben und „Neuer Tag“ drücken: Der Tag wird angelegt und automatisch ausgewählt.",
  "field.categories.code.help": "Der Kategoriencode steht in der URL /categories/{code}; nach dem Anlegen nicht mehr ändern.",
  "field.tags.code.help": "Eindeutiger Tag-Code für Filterung und Duplikatprüfung; Tags erscheinen nicht in URLs, eine Umbenennung bricht daher keine Links.",
  "field.users.username.help": "Anmeldename, nach dem Anlegen unveränderlich. Besser E-Mail-Präfix oder Personalnummer statt veränderlicher Angaben wie der Telefonnummer.",
  "field.users.password.help": "Startpasswort nur beim Anlegen; bei der ersten Anmeldung muss es geändert werden. Leer lassen, dann erzeugt das System eines nach der Passwortrichtlinie.",
  "field.users.dept.help": "Organisation des Kontos. Sie bestimmt die Datenbereiche „eigene Organisation“ und „eigene und untergeordnete“; eine falsche Wahl verbirgt Daten oder zeigt fremde.",
  "field.users.roles.help": "Rollen steuern Menü- und Buttonrechte, Mehrfachauswahl möglich, Rechte werden vereinigt. Mindestens eine Rolle ist nötig.",
  "field.users.status.help": "Ein deaktiviertes Konto kann sich nicht anmelden; Daten und Protokolle bleiben erhalten.",
  "field.roles.code.help": "Der Rollencode dient der API-Autorisierung und der Prüfung integrierter Rollen. Nach dem Anlegen unveränderlich; Großbuchstaben mit Unterstrichen empfohlen.",
  "field.roles.dataScope.help": "Legt fest, deren Organisationen Daten die Rolle sieht. Bei „Benutzerdefiniert“ müssen die Organisationen zusätzlich unter „Organisationsfreigabe“ angehakt werden.",
  "field.configs.type.help": "Parametertyp nur lesbar; er bestimmt die Auswertung des Werts (Zeichenkette, Zahl, Wahrheitswert, JSON). Passt der Typ nicht zum Wert, schlägt das Lesen fehl.",
  "field.configs.value.help": "Wert passend zum Typ eintragen; JSON muss gültiges JSON sein. Für Geheimnisse nur Platzhalter oder Umgebungsvariablen angeben, nie im Klartext speichern.",
  "field.dicts.typeCode.help": "Programme lesen das Wörterbuch über diesen Code. Kleinbuchstaben mit Unterstrichen empfohlen, nach dem Anlegen nicht ändern.",
  "field.dicts.nameKey.help": "Textschlüssel des Anzeigenamens (zum Beispiel dict.someName); muss in den Sprachressourcen vorhanden sein.",
  "field.dicts.itemValue.help": "Wert des Wörterbucheintrags, wird gespeichert und über die API zurückgegeben; nach dem Anlegen besser nicht ändern, damit alte Daten passen.",
  "field.dicts.labelZh.help": "Beschriftung für die chinesische Oberfläche; die englische Beschriftung wird im nächsten Feld separat gepflegt.",
  "field.jobs.cron.help": "Ausdruck mit 6 Feldern: Sekunde Minute Stunde Tag Monat Wochentag. Zum Beispiel 0 0 3 * * * bedeutet täglich um 03:00; prüfbar über „Einmal ausführen“.",
  "field.jobs.params.help": "JSON-Objekt, das an die Aufgabenlogik übergeben wird; ohne Parameter leer lassen.",
  "field.jobs.concurrency.help": "Ist Parallelität verboten, wird der Lauf übersprungen, solange der vorige noch läuft; ist sie erlaubt, laufen mehrere gleichzeitig.",
  "field.banners.image.help": "Zuerst in die Dateiverwaltung hochladen, dann hier auswählen. Empfohlen: Querformat 16:9, maximal 500 KB pro Bild.",
  "field.banners.link.help": "Ziel beim Klick, relativer Pfad oder vollständige URL; leer bedeutet nicht klickbar.",
  "field.banners.startAt.help": "Zum Zeitpunkt erscheint der Banner automatisch im Portal; leer bedeutet sofort.",
  "field.banners.endAt.help": "Zum Zeitpunkt wird der Banner automatisch offline genommen; leer bedeutet unbegrenzt gültig.",
  "field.mail.variables.help": "Vorlagenvariablen als JSON-Objekt; die Schlüssel müssen den {name}-Platzhaltern im Text entsprechen. Nur für Testversand.",
  "field.notices.publishAt.help": "Zum Zeitpunkt erscheint die Mitteilung automatisch im Portal; leer bedeutet sofort.",
  "field.notices.expireAt.help": "Zum Zeitpunkt wird die Mitteilung automatisch offline genommen; leer bedeutet unbegrenzt gültig."
}

export default message
