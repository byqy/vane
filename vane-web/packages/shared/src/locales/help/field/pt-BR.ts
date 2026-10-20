// Textos de ajuda por campo do painel administrativo (pt-BR). O conjunto de chaves deve ser idêntico ao dos demais idiomas (validado por field-help.test.ts).
// Mantido em arquivo separado da ajuda de página em locales/help/<locale>.ts; a mesclagem está em locales/help/index.ts.

const message = {
  "fieldHelp.open": "Ajuda do campo",
  "field.navs.labelKey.help": "Chave de texto do nome do menu, como nav.about. A chave precisa existir nos recursos de idioma; caso contrário, o portal exibe a chave bruta.",
  "field.navs.target.help": "Caminho relativo aberto ao clicar, por exemplo /, /about ou /articles. O front-end adiciona o prefixo de idioma automaticamente; não informe o domínio completo.",
  "field.navs.sortNo.help": "Valores menores aparecem primeiro; os menus do mesmo nível são ordenados por esse valor. Pode ficar vazio (0 por padrão).",
  "field.pages.slug.help": "Parte da URL da página (/{locale}/{slug}). Evite alterá-lo após publicar, ou os links antigos retornarão 404.",
  "field.articles.slug.help": "Parte da URL do artigo (/{locale}/articles/{slug}); também afeta links existentes e a indexação nos buscadores.",
  "field.articles.category.help": "Define em qual página de categoria o artigo aparece (/{locale}/categories/{code}). As categorias têm um único nível e o campo pode ficar vazio.",
  "field.articles.tags.help": "Tags do artigo, várias permitidas; o portal pode filtrar por tag. Se não houver uma adequada, crie aqui mesmo.",
  "field.articles.newTag.help": "Informe o código da tag e clique em «Nova tag»: ela é criada e já selecionada para este artigo.",
  "field.categories.code.help": "O código da categoria entra na URL /categories/{code}; não o altere após a criação.",
  "field.tags.code.help": "Código único da tag, usado para filtro e deduplicação; as tags não entram na URL, então renomear não quebra links.",
  "field.users.username.help": "Nome de login; não pode ser alterado após a criação. Prefira o prefixo do e-mail ou a matrícula, evitando dados que possam mudar.",
  "field.users.password.help": "Senha inicial apenas na criação da conta; o usuário deverá alterá-la no primeiro acesso. Se ficar vazio, o sistema gera uma conforme a política de senhas.",
  "field.users.dept.help": "Organização à qual a conta pertence. Define os escopos de dados «esta organização» e «esta organização e as inferiores»; uma escolha errada oculta dados ou expõe dados indevidos.",
  "field.users.roles.help": "As funções definem permissões de menus e botões; várias podem ser escolhidas e as permissões se somam. É obrigatório selecionar ao menos uma.",
  "field.users.status.help": "Uma conta desativada não consegue entrar; os dados e registros são mantidos.",
  "field.roles.code.help": "O código da função é usado na autorização da API e na identificação de funções internas; não pode ser alterado após a criação. Prefira maiúsculas com sublinhados.",
  "field.roles.dataScope.help": "Define quais dados de organização a função pode ver. Ao escolher «Personalizado», marque as organizações em «Escopo de organização».",
  "field.configs.type.help": "Tipo de parâmetro somente leitura; define como o valor é interpretado (texto, número, booleano, JSON). Tipo e valor incompatíveis causam falha na leitura.",
  "field.configs.value.help": "Preencha o valor conforme o tipo; JSON precisa ser válido. Para segredos, use apenas um marcador ou referência a variável de ambiente, nunca texto puro.",
  "field.dicts.typeCode.help": "Os programas leem o dicionário por este código; prefira minúsculas com sublinhados e não o altere após a criação.",
  "field.dicts.nameKey.help": "Chave de texto do nome exibido (por exemplo, dict.someName); precisa existir nos recursos de idioma.",
  "field.dicts.itemValue.help": "Valor do item de dicionário; é armazenado e retornado pela API. Evite alterá-lo depois para que os dados históricos continuem compatíveis.",
  "field.dicts.labelZh.help": "Rótulo exibido na interface em chinês; o rótulo em inglês é mantido à parte no campo seguinte.",
  "field.jobs.cron.help": "Expressão de 6 campos: segundo minuto hora dia mês dia da semana. Por exemplo, 0 0 3 * * * executa diariamente às 03:00; use «Executar uma vez» para validar.",
  "field.jobs.params.help": "Objeto JSON passado ao processador da tarefa; deixe vazio se a tarefa não tiver parâmetros.",
  "field.jobs.concurrency.help": "Com concorrência proibida, esta execução é ignorada enquanto a anterior não terminar; se permitida, as execuções podem se sobrepor.",
  "field.banners.image.help": "Envie o arquivo em «Gerenciamento de arquivos» primeiro e selecione-o aqui. Recomenda-se uma imagem horizontal 16:9 de até 500 KB.",
  "field.banners.link.help": "Destino ao clicar no banner: caminho relativo ou URL completa. Deixe vazio para tornar o banner não clicável.",
  "field.banners.startAt.help": "O banner aparece no portal nesse momento; deixe vazio para publicar imediatamente.",
  "field.banners.endAt.help": "O banner sai do ar nesse momento; deixe vazio para mantê-lo por tempo indeterminado.",
  "field.mail.variables.help": "Variáveis de modelo em objeto JSON; as chaves devem corresponder aos marcadores {name} do corpo. Usado apenas em envios de teste.",
  "field.notices.publishAt.help": "O aviso aparece no portal nesse momento; deixe vazio para publicar imediatamente.",
  "field.notices.expireAt.help": "O aviso sai do ar nesse momento; deixe vazio para mantê-lo por tempo indeterminado."
}

export default message
