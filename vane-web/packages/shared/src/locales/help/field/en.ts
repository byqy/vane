// Field-level help copy for admin forms (en). Key set must match every other locale (enforced by field-help.test.ts).
// Kept in a separate file from the page-level help in locales/help/<locale>.ts; merged in locales/help/index.ts.

const message = {
  "fieldHelp.open": "Field help",
  "field.navs.labelKey.help": "Message key for the menu label, for example nav.about. The key must already exist in the language resources, otherwise the portal shows the raw key.",
  "field.navs.target.help": "Relative path to open on click, for example /, /about or /articles. The front end adds the locale prefix itself, so do not enter a full domain.",
  "field.navs.sortNo.help": "Lower values come first; sibling menus are ordered by it. Leave empty for the default 0.",
  "field.pages.slug.help": "Part of the page URL (/{locale}/{slug}). Avoid changing it after publishing or old links will 404.",
  "field.articles.slug.help": "Part of the article URL (/{locale}/articles/{slug}); it also affects existing links and search indexing.",
  "field.articles.category.help": "Decides which category page lists the article (/{locale}/categories/{code}). Categories are single-level and the field may stay empty.",
  "field.articles.tags.help": "Article tags, multiple allowed; the portal can filter by tag. Create a missing tag right here.",
  "field.articles.newTag.help": "Enter a tag code and press \"New tag\": the tag is created and selected for this article.",
  "field.categories.code.help": "The category code appears in the URL /categories/{code}; do not change it after creation.",
  "field.tags.code.help": "Unique tag code used for filtering and de-duplication; tags never appear in URLs, so renaming cannot break links.",
  "field.users.username.help": "Sign-in name, cannot be changed after creation. Prefer an email prefix or employee number over values that may change.",
  "field.users.password.help": "Initial password for a new account only; the user must change it on first sign-in. Leave empty to let the system generate one.",
  "field.users.dept.help": "Organization the account belongs to. It decides the \"this organization\" and \"this organization and below\" data scopes; a wrong choice hides data or exposes data it should not.",
  "field.users.roles.help": "Roles grant menus and button permissions; multiple roles are combined. At least one role is required.",
  "field.users.status.help": "A disabled account cannot sign in; its data and logs are kept.",
  "field.roles.code.help": "Role code is used for API authorization and built-in role checks. It cannot be changed after creation; use uppercase with underscores.",
  "field.roles.dataScope.help": "Decides which organizations' data the role can see. With \"Custom\" you must also tick the organizations under Organization scope.",
  "field.configs.type.help": "Read-only parameter type; it decides how the value is parsed (string, number, boolean, JSON). A type mismatch breaks reads.",
  "field.configs.value.help": "Fill the value for its type; JSON must be valid JSON. Keep secrets out and reference a placeholder or environment variable instead.",
  "field.dicts.typeCode.help": "Programs read the dictionary by this code. Prefer lowercase with underscores and do not change it after creation.",
  "field.dicts.nameKey.help": "Message key of the display name (for example dict.someName); it must already exist in the language resources.",
  "field.dicts.itemValue.help": "Stored and returned by the API as the dictionary item value; avoid changing it later so historical data keeps matching.",
  "field.dicts.labelZh.help": "Label shown in the Chinese UI; the English label is maintained separately in the next field.",
  "field.jobs.cron.help": "6-field expression: second minute hour day month weekday. For example 0 0 3 * * * runs daily at 03:00; verify with \"Run once\".",
  "field.jobs.params.help": "JSON object passed to the job handler; leave empty when the job takes no parameters.",
  "field.jobs.concurrency.help": "With concurrency forbidden, a run is skipped while the previous one is still going; allowed runs may overlap.",
  "field.banners.image.help": "Upload the file under File management first, then pick it here. A 16:9 landscape image under 500 KB works best.",
  "field.banners.link.help": "Target on click: a relative path or a full URL. Leave empty to make the banner non-clickable.",
  "field.banners.startAt.help": "The banner appears on the portal at this time; leave empty to publish immediately.",
  "field.banners.endAt.help": "The banner is taken offline at this time; leave empty to keep it forever.",
  "field.mail.variables.help": "Template variables as a JSON object; keys must match the {name} placeholders in the body. Used for test sends only.",
  "field.notices.publishAt.help": "The notice appears on the portal at this time; leave empty to publish immediately.",
  "field.notices.expireAt.help": "The notice is taken offline at this time; leave empty to keep it forever."
}

export default message
