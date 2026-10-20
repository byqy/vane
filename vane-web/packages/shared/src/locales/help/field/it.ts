// Testi di guida a livello di campo per il back office (it). L'insieme delle chiavi deve coincidere con quello delle altre lingue (verificato da field-help.test.ts).
// Tenuto in un file separato dalla guida di pagina in locales/help/<locale>.ts; l'unione è definita in locales/help/index.ts.

const message = {
  "fieldHelp.open": "Guida del campo",
  "field.navs.labelKey.help": "Chiave del testo per il nome del menu, ad esempio nav.about. La chiave deve già esistere nelle risorse linguistiche, altrimenti il portale mostra la chiave grezza.",
  "field.navs.target.help": "Percorso relativo aperto al clic, ad esempio /, /about o /articles. Il front-end aggiunge da sé il prefisso di lingua: non inserire il dominio completo.",
  "field.navs.sortNo.help": "I valori più bassi compaiono prima; i menu dello stesso livello sono ordinati per questo valore. Può restare vuoto (0 predefinito).",
  "field.pages.slug.help": "Parte dell'URL della pagina (/{locale}/{slug}). Evita di cambiarlo dopo la pubblicazione, altrimenti i vecchi link daranno 404.",
  "field.articles.slug.help": "Parte dell'URL dell'articolo (/{locale}/articles/{slug}); influisce anche sui link esistenti e sull'indicizzazione nei motori di ricerca.",
  "field.articles.category.help": "Stabilisce in quale pagina di categoria compare l'articolo (/{locale}/categories/{code}). Le categorie sono a un solo livello; il campo può restare vuoto.",
  "field.articles.tags.help": "Tag dell'articolo, se ne possono scegliere più di uno; il portale può filtrare per tag. Se non ne esiste uno adatto, puoi crearlo qui.",
  "field.articles.newTag.help": "Inserisci il codice del tag e premi «Nuovo tag»: il tag viene creato e selezionato per questo articolo.",
  "field.categories.code.help": "Il codice di categoria entra nell'URL /categories/{code}; non modificarlo dopo la creazione.",
  "field.tags.code.help": "Codice univoco del tag, usato per filtrare ed evitare duplicati; i tag non compaiono nell'URL, quindi rinominarli non rompe i link.",
  "field.users.username.help": "Nome di accesso, non modificabile dopo la creazione. Meglio usare il prefisso dell'email o la matricola, evitando dati che possono cambiare.",
  "field.users.password.help": "Password iniziale solo alla creazione dell'account; al primo accesso l'utente dovrà cambiarla. Se lasciata vuota, il sistema ne genera una secondo la politica delle password.",
  "field.users.dept.help": "Organizzazione di appartenenza dell'account. Determina gli ambiti dati «questa organizzazione» e «questa organizzazione e quelle inferiori»; una scelta errata nasconde dati o ne mostra di non dovuti.",
  "field.users.roles.help": "I ruoli determinano i permessi di menu e pulsanti; se ne possono scegliere più di uno e i permessi si sommano. È obbligatorio almeno un ruolo.",
  "field.users.status.help": "Un account disattivato non può accedere; dati e log vengono conservati.",
  "field.roles.code.help": "Il codice del ruolo serve per l'autorizzazione delle API e il riconoscimento dei ruoli predefiniti; non è modificabile dopo la creazione. Meglio maiuscole con trattino basso.",
  "field.roles.dataScope.help": "Stabilisce quali dati delle organizzazioni può vedere il ruolo. Scegliendo «Personalizzato» occorre selezionare le organizzazioni in «Ambito organizzazioni».",
  "field.configs.type.help": "Tipo di parametro in sola lettura; stabilisce come viene interpretato il valore (stringa, numero, booleano, JSON). Tipo e valore non coerenti causano errori di lettura.",
  "field.configs.value.help": "Compilare il valore in base al tipo; JSON deve essere valido. Per i segreti usare solo un segnaposto o un riferimento a una variabile d'ambiente, mai il testo in chiaro.",
  "field.dicts.typeCode.help": "I programmi leggono il dizionario tramite questo codice; meglio minuscole con trattino basso e nessuna modifica dopo la creazione.",
  "field.dicts.nameKey.help": "Chiave del testo del nome visualizzato (ad esempio dict.someName); deve già esistere nelle risorse linguistiche.",
  "field.dicts.itemValue.help": "Valore della voce di dizionario; viene salvato e restituito dall'API. Meglio non modificarlo in seguito, per non disallineare i dati storici.",
  "field.dicts.labelZh.help": "Etichetta mostrata nell'interfaccia in cinese; l'etichetta in inglese si gestisce a parte nel campo successivo.",
  "field.jobs.cron.help": "Espressione a 6 campi: secondo minuto ora giorno mese giorno della settimana. Ad esempio 0 0 3 * * * viene eseguita ogni giorno alle 03:00; puoi verificarla con «Esegui una volta».",
  "field.jobs.params.help": "Oggetto JSON passato alla logica del job; lascia vuoto se il job non ha parametri.",
  "field.jobs.concurrency.help": "Con la concorrenza vietata, questa esecuzione viene saltata finché la precedente non è terminata; se è consentita, le esecuzioni possono sovrapporsi.",
  "field.banners.image.help": "Carica prima il file in «Gestione file», poi selezionalo qui. Meglio un'immagine orizzontale 16:9 entro 500 KB.",
  "field.banners.link.help": "Destinazione al clic sul banner: percorso relativo o URL completo. Lascia vuoto per renderlo non cliccabile.",
  "field.banners.startAt.help": "Il banner compare nel portale in questo momento; lascia vuoto per pubblicarlo subito.",
  "field.banners.endAt.help": "Il banner viene tolto dal portale in questo momento; lascia vuoto per mantenerlo a tempo indeterminato.",
  "field.mail.variables.help": "Variabili del modello come oggetto JSON; le chiavi devono corrispondere ai segnaposto {name} nel corpo. Usate solo per invii di prova.",
  "field.notices.publishAt.help": "L'avviso compare nel portale in questo momento; lascia vuoto per pubblicarlo subito.",
  "field.notices.expireAt.help": "L'avviso viene tolto dal portale in questo momento; lascia vuoto per mantenerlo a tempo indeterminato."
}

export default message
