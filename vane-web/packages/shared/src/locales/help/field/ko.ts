// 관리자 화면의 「필드 단위」 도움말 문구(ko). 키 집합은 다른 언어와 완전히 같아야 합니다(field-help.test.ts에서 강제).
// locales/help/<locale>.ts의 페이지 단위 도움말과 별도 파일로 관리하며, 병합 규칙은 locales/help/index.ts를 참고하세요.

const message = {
  "fieldHelp.open": "필드 설명",
  "field.navs.labelKey.help": "메뉴 이름의 문구 key입니다(예: nav.about). 이 key는 언어 리소스에 이미 있어야 하며, 없으면 포털에 원본 key가 그대로 표시됩니다.",
  "field.navs.target.help": "클릭 시 이동할 상대 경로입니다(예: /, /about, /articles). 언어 접두사는 프런트에서 자동으로 붙으므로 전체 도메인을 쓰지 마세요.",
  "field.navs.sortNo.help": "값이 작을수록 앞에 표시됩니다. 같은 수준의 메뉴는 이 값으로 정렬되며, 비워 두면 0입니다.",
  "field.pages.slug.help": "페이지 URL의 일부입니다(/{locale}/{slug}). 게시 후에는 되도록 변경하지 마세요. 기존 링크가 404가 됩니다.",
  "field.articles.slug.help": "글 URL의 일부입니다(/{locale}/articles/{slug}). 기존 링크와 검색 엔진 수집에도 영향을 줍니다.",
  "field.articles.category.help": "글이 어느 분류 페이지에 표시될지 정합니다(/{locale}/categories/{code}). 분류는 1단계 구조이며 비워 둘 수 있습니다.",
  "field.articles.tags.help": "글 태그로 여러 개를 선택할 수 있고, 포털에서는 태그로 필터링할 수 있습니다. 맞는 태그가 없으면 여기서 바로 만들 수 있습니다.",
  "field.articles.newTag.help": "태그 코드를 입력하고 「새 태그」를 누르면 태그가 만들어지고 자동으로 선택됩니다.",
  "field.categories.code.help": "분류 코드는 URL /categories/{code}에 들어갑니다. 만든 뒤에는 변경하지 마세요.",
  "field.tags.code.help": "필터링과 중복 제거에 쓰는 태그의 고유 코드입니다. 태그는 URL에 들어가지 않으므로 이름을 바꿔도 링크는 깨지지 않습니다.",
  "field.users.username.help": "로그인 이름이며 만든 뒤에는 변경할 수 없습니다. 전화번호처럼 바뀔 수 있는 정보 대신 이메일 앞부분이나 사번을 권장합니다.",
  "field.users.password.help": "새로 만들 때만 초기 비밀번호를 입력합니다. 첫 로그인 시 변경을 요구합니다. 비워 두면 비밀번호 정책에 따라 시스템이 생성합니다.",
  "field.users.dept.help": "계정이 속한 조직으로 「본 조직」「본 조직 및 하위」데이터 범위의 기준이 됩니다. 잘못 선택하면 데이터가 보이지 않거나 보면 안 되는 데이터가 보입니다.",
  "field.users.roles.help": "역할은 메뉴와 버튼 권한을 결정합니다. 여러 개를 선택할 수 있고 권한은 합집합으로 적용됩니다. 최소 하나는 선택해야 합니다.",
  "field.users.status.help": "비활성화하면 이 계정은 로그인할 수 없으며 데이터와 로그는 유지됩니다.",
  "field.roles.code.help": "역할 코드는 API 인가와 내장 역할 판별에 쓰입니다. 만든 뒤에는 변경할 수 없으며 대문자와 밑줄을 권장합니다.",
  "field.roles.dataScope.help": "이 역할이 볼 수 있는 조직 데이터를 정합니다. 「사용자 지정」을 선택하면 「조직 권한」에서 대상 조직을 선택해야 합니다.",
  "field.configs.type.help": "매개변수 유형은 읽기 전용이며 값의 해석 방식(문자열, 숫자, 불리언, JSON)을 정합니다. 유형과 값이 맞지 않으면 읽기에 실패합니다.",
  "field.configs.value.help": "유형에 맞게 값을 입력하세요. JSON은 올바른 JSON이어야 합니다. 비밀 정보는 자리표시자나 환경 변수 참조만 넣고 평문으로 저장하지 마세요.",
  "field.dicts.typeCode.help": "프로그램이 이 코드로 사전을 읽습니다. 소문자와 밑줄을 권장하며 만든 뒤에는 변경하지 마세요.",
  "field.dicts.nameKey.help": "화면에 표시할 이름의 문구 key입니다(예: dict.someName). 언어 리소스에 이미 있어야 합니다.",
  "field.dicts.itemValue.help": "사전 항목의 값으로 저장되어 API로 반환됩니다. 기존 데이터와 어긋나지 않도록 만든 뒤에는 변경하지 않는 것이 좋습니다.",
  "field.dicts.labelZh.help": "중국어 화면에 표시할 라벨입니다. 영어 라벨은 다음 항목에서 따로 관리합니다.",
  "field.jobs.cron.help": "6자리 식: 초 분 시 일 월 요일. 예를 들어 0 0 3 * * *는 매일 03:00에 실행한다는 뜻이며, 「한 번 실행」으로 확인할 수 있습니다.",
  "field.jobs.params.help": "작업 처리 로직에 전달할 JSON 객체입니다. 매개변수가 없으면 비워 두세요.",
  "field.jobs.concurrency.help": "동시 실행을 금지하면 이전 실행이 끝나지 않았을 때 이번 실행을 건너뜁니다. 허용하면 동시에 여러 번 실행됩니다.",
  "field.banners.image.help": "먼저 「파일 관리」에 업로드한 뒤 여기서 선택하세요. 16:9 가로 이미지, 장당 500 KB 이하를 권장합니다.",
  "field.banners.link.help": "클릭 시 이동할 주소로 상대 경로나 전체 URL을 쓸 수 있습니다. 비워 두면 클릭할 수 없습니다.",
  "field.banners.startAt.help": "지정한 시각이 되면 포털에 자동으로 표시됩니다. 비워 두면 즉시 적용됩니다.",
  "field.banners.endAt.help": "지정한 시각이 되면 자동으로 내려갑니다. 비워 두면 계속 유효합니다.",
  "field.mail.variables.help": "템플릿 변수를 JSON 객체로 입력합니다. 키는 본문의 {name} 자리표시자와 대응해야 하며 테스트 발송에만 사용합니다.",
  "field.notices.publishAt.help": "지정한 시각이 되면 포털에 자동으로 표시됩니다. 비워 두면 즉시 적용됩니다.",
  "field.notices.expireAt.help": "지정한 시각이 되면 자동으로 내려갑니다. 비워 두면 계속 유효합니다."
}

export default message
