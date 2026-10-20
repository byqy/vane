// Textes d'aide au niveau du champ pour les formulaires d'administration (fr). Le jeu de clés doit être identique à celui des autres langues (vérifié par field-help.test.ts).
// Fichier distinct de l'aide au niveau de la page dans locales/help/<locale>.ts ; règles de fusion dans locales/help/index.ts.

const message = {
  "fieldHelp.open": "Aide du champ",
  "field.navs.labelKey.help": "Clé de libellé du menu, par exemple nav.about. La clé doit déjà exister dans les ressources linguistiques, sinon le portail affiche la clé brute.",
  "field.navs.target.help": "Chemin relatif à ouvrir au clic, par exemple /, /about ou /articles. Le préfixe de langue est ajouté automatiquement, n'indiquez donc pas de domaine complet.",
  "field.navs.sortNo.help": "Les valeurs plus petites passent en premier ; les menus de même niveau sont triés selon cette valeur. Laissez vide pour la valeur 0.",
  "field.pages.slug.help": "Partie de l'URL de la page (/{locale}/{slug}). Évitez de la modifier après publication, sinon les anciens liens renvoient 404.",
  "field.articles.slug.help": "Partie de l'URL de l'article (/{locale}/articles/{slug}) ; influence aussi les liens existants et l'indexation par les moteurs de recherche.",
  "field.articles.category.help": "Détermine la page de catégorie où figure l'article (/{locale}/categories/{code}). Les catégories sont à un seul niveau ; le champ peut rester vide.",
  "field.articles.tags.help": "Étiquettes de l'article, plusieurs choix possibles ; le portail peut filtrer par étiquette. Une étiquette manquante peut être créée ici même.",
  "field.articles.newTag.help": "Saisissez un code d'étiquette puis cliquez sur « Nouvelle étiquette » : l'étiquette est créée et sélectionnée automatiquement.",
  "field.categories.code.help": "Le code de catégorie apparaît dans l'URL /categories/{code} ; ne le modifiez pas après création.",
  "field.tags.code.help": "Code unique de l'étiquette, utilisé pour le filtrage et la déduplication ; les étiquettes n'apparaissent pas dans les URL, un renommage ne casse donc aucun lien.",
  "field.users.username.help": "Identifiant de connexion, non modifiable après création. Préférez un préfixe d'e-mail ou un matricule à une information susceptible de changer.",
  "field.users.password.help": "Mot de passe initial uniquement à la création ; il devra être changé à la première connexion. Laissez vide pour que le système en génère un selon la politique.",
  "field.users.dept.help": "Organisation du compte. Elle détermine les périmètres « mon organisation » et « mon organisation et les suivantes » ; un mauvais choix masque des données ou en expose trop.",
  "field.users.roles.help": "Les rôles déterminent les droits sur les menus et les boutons ; plusieurs choix possibles, les droits sont cumulés. Au moins un rôle est requis.",
  "field.users.status.help": "Un compte désactivé ne peut plus se connecter ; les données et les journaux sont conservés.",
  "field.roles.code.help": "Le code de rôle sert à l'autorisation des API et à la détection des rôles intégrés. Non modifiable après création ; majuscules et tirets bas recommandés.",
  "field.roles.dataScope.help": "Détermine les données des organisations visibles par ce rôle. Avec « Personnalisé », cochez les organisations dans « Autorisation d'organisation ».",
  "field.configs.type.help": "Type du paramètre en lecture seule ; il détermine l'interprétation de la valeur (chaîne, nombre, booléen, JSON). Un type incohérent fait échouer la lecture.",
  "field.configs.value.help": "Saisissez la valeur selon son type ; le JSON doit être valide. Pour les secrets, n'indiquez qu'un espace réservé ou une variable d'environnement, jamais en clair.",
  "field.dicts.typeCode.help": "Les programmes lisent le dictionnaire via ce code. Minuscules et tirets bas recommandés ; ne le modifiez pas après création.",
  "field.dicts.nameKey.help": "Clé de libellé du nom affiché (par exemple dict.someName) ; elle doit déjà exister dans les ressources linguistiques.",
  "field.dicts.itemValue.help": "Valeur de l'élément de dictionnaire, stockée et renvoyée par l'API ; évitez de la modifier ensuite pour ne pas désaligner les données existantes.",
  "field.dicts.labelZh.help": "Libellé affiché dans l'interface chinoise ; le libellé anglais est géré séparément dans le champ suivant.",
  "field.jobs.cron.help": "Expression à 6 champs : seconde minute heure jour mois jour de la semaine. Par exemple 0 0 3 * * * s'exécute chaque jour à 03:00 ; vérifiez avec « Exécuter une fois ».",
  "field.jobs.params.help": "Objet JSON transmis au traitement de la tâche ; laissez vide si la tâche n'a pas de paramètres.",
  "field.jobs.concurrency.help": "Si la concurrence est interdite, l'exécution est ignorée tant que la précédente n'est pas terminée ; si elle est autorisée, plusieurs exécutions se chevauchent.",
  "field.banners.image.help": "Téléversez d'abord le fichier dans « Gestion des fichiers », puis sélectionnez-le ici. Image paysage 16:9, 500 KB maximum par fichier.",
  "field.banners.link.help": "Cible au clic : chemin relatif ou URL complète ; vide signifie non cliquable.",
  "field.banners.startAt.help": "La bannière apparaît automatiquement sur le portail à cette heure ; vide signifie immédiatement.",
  "field.banners.endAt.help": "La bannière est retirée automatiquement à cette heure ; vide signifie sans limite.",
  "field.mail.variables.help": "Variables du modèle au format objet JSON ; les clés doivent correspondre aux espaces réservés {name} du corps. Utilisé uniquement pour l'envoi de test.",
  "field.notices.publishAt.help": "L'annonce apparaît automatiquement sur le portail à cette heure ; vide signifie immédiatement.",
  "field.notices.expireAt.help": "L'annonce est retirée automatiquement à cette heure ; vide signifie sans limite."
}

export default message
