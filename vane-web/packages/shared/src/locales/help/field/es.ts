// Textos de ayuda por campo del panel de administración (es). El conjunto de claves debe coincidir con el de los demás idiomas (lo valida field-help.test.ts).
// Se mantiene en un archivo aparte de la ayuda de página de locales/help/<locale>.ts; la fusión se define en locales/help/index.ts.

const message = {
  "fieldHelp.open": "Ayuda del campo",
  "field.navs.labelKey.help": "Clave de texto del nombre del menú, por ejemplo nav.about. La clave debe existir ya en los recursos de idioma; de lo contrario, el portal mostrará la clave en bruto.",
  "field.navs.target.help": "Ruta relativa a la que se accede al hacer clic, por ejemplo /, /about o /articles. El frontend añade el prefijo de idioma automáticamente; no escriba un dominio completo.",
  "field.navs.sortNo.help": "Los valores más bajos aparecen primero; los menús del mismo nivel se ordenan por este valor. Puede dejarlo vacío (0 por defecto).",
  "field.pages.slug.help": "Parte de la URL de la página (/{locale}/{slug}). Evite cambiarlo tras publicar, o los enlaces antiguos darán 404.",
  "field.articles.slug.help": "Parte de la URL del artículo (/{locale}/articles/{slug}); también afecta a los enlaces existentes y a la indexación en buscadores.",
  "field.articles.category.help": "Determina en qué página de categoría aparece el artículo (/{locale}/categories/{code}). Las categorías son de un solo nivel y el campo puede quedar vacío.",
  "field.articles.tags.help": "Etiquetas del artículo, se permiten varias; el portal puede filtrar por etiqueta. Si no hay una adecuada, puede crearla aquí mismo.",
  "field.articles.newTag.help": "Introduzca el código de la etiqueta y pulse «Nueva etiqueta»: se creará y se seleccionará automáticamente para este artículo.",
  "field.categories.code.help": "El código de categoría forma parte de la URL /categories/{code}; no lo modifique después de crearlo.",
  "field.tags.code.help": "Código único de la etiqueta, usado para filtrar y evitar duplicados; las etiquetas no aparecen en las URL, así que renombrarlas no rompe enlaces.",
  "field.users.username.help": "Nombre de inicio de sesión; no se puede modificar tras crear la cuenta. Se recomienda el prefijo del correo o el número de empleado, y evitar datos que puedan cambiar.",
  "field.users.password.help": "Contraseña inicial solo al crear la cuenta; el usuario deberá cambiarla en el primer inicio de sesión. Si lo deja vacío, el sistema generará una según la política de contraseñas.",
  "field.users.dept.help": "Organización a la que pertenece la cuenta. Determina los ámbitos de datos «esta organización» y «esta organización y las inferiores»; una elección incorrecta oculta datos o muestra datos que no debería.",
  "field.users.roles.help": "Los roles determinan los permisos de menús y botones; se pueden elegir varios y los permisos se acumulan. Debe seleccionar al menos un rol.",
  "field.users.status.help": "Una cuenta deshabilitada no puede iniciar sesión; sus datos y registros se conservan.",
  "field.roles.code.help": "El código de rol se usa para la autorización de la API y la detección de roles integrados; no se puede modificar tras crearlo. Se recomienda mayúsculas con guiones bajos.",
  "field.roles.dataScope.help": "Determina qué datos de organización puede ver el rol. Si elige «Personalizado», deberá marcar las organizaciones concretas en «Ámbito de organización».",
  "field.configs.type.help": "Tipo de parámetro de solo lectura; determina cómo se interpreta el valor (cadena, número, booleano, JSON). Si el tipo y el valor no coinciden, la lectura falla.",
  "field.configs.value.help": "Rellene el valor según su tipo; JSON debe ser JSON válido. Para los secretos, use solo un marcador o una referencia a una variable de entorno, nunca texto plano.",
  "field.dicts.typeCode.help": "Los programas leen el diccionario por este código; se recomienda minúsculas con guiones bajos y no modificarlo tras crearlo.",
  "field.dicts.nameKey.help": "Clave de texto del nombre que se muestra (por ejemplo, dict.someName); debe existir ya en los recursos de idioma.",
  "field.dicts.itemValue.help": "Valor del elemento de diccionario; se almacena y se devuelve por la API. Se recomienda no modificarlo después para que los datos históricos sigan coincidiendo.",
  "field.dicts.labelZh.help": "Etiqueta que se muestra en la interfaz en chino; la etiqueta en inglés se mantiene aparte en el campo siguiente.",
  "field.jobs.cron.help": "Expresión de 6 campos: segundo minuto hora día mes día de la semana. Por ejemplo, 0 0 3 * * * se ejecuta a diario a las 03:00; puede comprobarlo con «Ejecutar una vez».",
  "field.jobs.params.help": "Objeto JSON que se pasa al controlador de la tarea; déjelo vacío si la tarea no tiene parámetros.",
  "field.jobs.concurrency.help": "Si se prohíbe la concurrencia, se omite esta ejecución mientras la anterior siga en curso; si se permite, las ejecuciones pueden solaparse.",
  "field.banners.image.help": "Suba primero el archivo en «Gestión de archivos» y selecciónelo aquí. Se recomienda una imagen horizontal 16:9 de menos de 500 KB.",
  "field.banners.link.help": "Destino al hacer clic en el carrusel: una ruta relativa o una URL completa. Déjelo vacío para que no se pueda hacer clic.",
  "field.banners.startAt.help": "El banner aparece en el portal en ese momento; déjelo vacío para publicarlo de inmediato.",
  "field.banners.endAt.help": "El banner se retira en ese momento; déjelo vacío para que sea permanente.",
  "field.mail.variables.help": "Variables de plantilla como objeto JSON; las claves deben coincidir con los marcadores {name} del cuerpo. Solo se usan en envíos de prueba.",
  "field.notices.publishAt.help": "El aviso aparece en el portal en ese momento; déjelo vacío para publicarlo de inmediato.",
  "field.notices.expireAt.help": "El aviso se retira en ese momento; déjelo vacío para que sea permanente."
}

export default message
