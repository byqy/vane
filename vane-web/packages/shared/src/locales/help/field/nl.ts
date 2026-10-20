// Veldhulpteksten voor het beheerpaneel (nl). De set sleutels moet gelijk zijn aan die van de andere talen (afgedwongen door field-help.test.ts).
// Staat in een apart bestand van de paginahulp in locales/help/<locale>.ts; samenvoegen gebeurt in locales/help/index.ts.

const message = {
  "fieldHelp.open": "Veldhulp",
  "field.navs.labelKey.help": "Berichtensleutel van de menunaam, bijvoorbeeld nav.about. De sleutel moet al in de taalbestanden bestaan, anders toont het portaal de ruwe sleutel.",
  "field.navs.target.help": "Relatief pad dat bij klik wordt geopend, bijvoorbeeld /, /about of /articles. De front-end voegt zelf de taalprefix toe; vul geen volledig domein in.",
  "field.navs.sortNo.help": "Lagere waarden staan vooraan; menu's op hetzelfde niveau worden hierop gesorteerd. Mag leeg blijven (standaard 0).",
  "field.pages.slug.help": "Deel van de pagina-URL (/{locale}/{slug}). Wijzig dit na publicatie liever niet, anders geven oude links een 404.",
  "field.articles.slug.help": "Deel van de artikel-URL (/{locale}/articles/{slug}); beïnvloedt ook bestaande links en indexering door zoekmachines.",
  "field.articles.category.help": "Bepaalt op welke categoriepagina het artikel verschijnt (/{locale}/categories/{code}). Categorieën hebben één niveau; het veld mag leeg blijven.",
  "field.articles.tags.help": "Tags van het artikel, meerdere toegestaan; het portaal kan op tag filteren. Ontbreekt een passende tag, dan maak je die hier direct aan.",
  "field.articles.newTag.help": "Voer de tagcode in en klik op \"Nieuwe tag\": de tag wordt aangemaakt en meteen voor dit artikel geselecteerd.",
  "field.categories.code.help": "De categoriecode komt in de URL /categories/{code}; wijzig deze niet na aanmaken.",
  "field.tags.code.help": "Unieke tagcode voor filteren en ontdubbelen; tags komen niet in de URL, dus hernoemen breekt geen links.",
  "field.users.username.help": "Inlognaam, niet te wijzigen na aanmaken. Gebruik bij voorkeur de e-mailprefix of het personeelsnummer en vermijd waarden die kunnen veranderen.",
  "field.users.password.help": "Alleen bij het aanmaken een initieel wachtwoord; bij de eerste login moet de gebruiker het wijzigen. Leeg laten betekent dat het systeem er een genereert volgens het wachtwoordbeleid.",
  "field.users.dept.help": "Organisatie waartoe het account behoort. Bepaalt de datascopes \"deze organisatie\" en \"deze organisatie en onderliggende\"; een verkeerde keuze verbergt data of toont data die niet zichtbaar hoort te zijn.",
  "field.users.roles.help": "Rollen bepalen menu- en knoprechten; meerdere rollen zijn mogelijk en de rechten worden samengevoegd. Minstens één rol is verplicht.",
  "field.users.status.help": "Een uitgeschakeld account kan niet inloggen; gegevens en logboeken blijven bewaard.",
  "field.roles.code.help": "De rolocode wordt gebruikt voor API-autorisatie en herkenning van ingebouwde rollen; niet te wijzigen na aanmaken. Gebruik hoofdletters met underscores.",
  "field.roles.dataScope.help": "Bepaalt welke organisatiedata de rol kan zien. Kies je \"Aangepast\", dan moet je de organisaties ook aanvinken onder \"Organisatiebereik\".",
  "field.configs.type.help": "Parametertype is alleen-lezen; het bepaalt hoe de waarde wordt geïnterpreteerd (tekst, getal, boolean, JSON). Een verkeerd type veroorzaakt leesfouten.",
  "field.configs.value.help": "Vul de waarde passend bij het type in; JSON moet geldige JSON zijn. Zet geheimen niet in platte tekst, maar gebruik een plaatsaanduiding of verwijzing naar een omgevingsvariabele.",
  "field.dicts.typeCode.help": "Programma's lezen het woordenboek op deze code; gebruik bij voorkeur kleine letters met underscores en wijzig de code niet na aanmaken.",
  "field.dicts.nameKey.help": "Berichtensleutel van de weergegeven naam (bijvoorbeeld dict.someName); moet al in de taalbestanden bestaan.",
  "field.dicts.itemValue.help": "Waarde van het woordenboekitem; wordt opgeslagen en via de API teruggegeven. Wijzig deze later liever niet, zodat historische data blijft kloppen.",
  "field.dicts.labelZh.help": "Label in de Chinese interface; het Engelse label wordt apart in het volgende veld onderhouden.",
  "field.jobs.cron.help": "Expressie met 6 velden: seconde minuut uur dag maand weekdag. Bijvoorbeeld 0 0 3 * * * draait dagelijks om 03:00; test met \"Eenmalig uitvoeren\".",
  "field.jobs.params.help": "JSON-object dat aan de taaklogica wordt doorgegeven; laat leeg als de taak geen parameters heeft.",
  "field.jobs.concurrency.help": "Bij verboden gelijktijdigheid wordt deze uitvoering overgeslagen zolang de vorige nog loopt; is gelijktijdigheid toegestaan, dan kunnen uitvoeringen overlappen.",
  "field.banners.image.help": "Upload het bestand eerst bij \"Bestandsbeheer\" en kies het hier; gebruik bij voorkeur een liggende afbeelding van 16:9 en maximaal 500 KB.",
  "field.banners.link.help": "Doel bij klikken op de banner: een relatief pad of een volledige URL. Leeg laten betekent niet klikbaar.",
  "field.banners.startAt.help": "De banner verschijnt op dit tijdstip in het portaal; leeg laten betekent direct publiceren.",
  "field.banners.endAt.help": "De banner gaat op dit tijdstip offline; leeg laten betekent permanent.",
  "field.mail.variables.help": "Sjabloonvariabelen als JSON-object; de sleutels moeten overeenkomen met de {name}-plaatsaanduidingen in de tekst. Alleen voor testverzendingen.",
  "field.notices.publishAt.help": "De mededeling verschijnt op dit tijdstip in het portaal; leeg laten betekent direct publiceren.",
  "field.notices.expireAt.help": "De mededeling gaat op dit tijdstip offline; leeg laten betekent permanent."
}

export default message
