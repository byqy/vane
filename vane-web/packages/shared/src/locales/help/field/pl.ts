// Teksty pomocy na poziomie pola w panelu administracyjnym (pl). Zestaw kluczy musi być identyczny jak w innych językach (wymusza to field-help.test.ts).
// Prowadzone w osobnym pliku niż pomoc stron w locales/help/<locale>.ts; scalanie opisuje locales/help/index.ts.

const message = {
  "fieldHelp.open": "Pomoc pola",
  "field.navs.labelKey.help": "Klucz tekstu nazwy menu, np. nav.about. Klucz musi już istnieć w zasobach językowych, w przeciwnym razie portal pokaże surowy klucz.",
  "field.navs.target.help": "Ścieżka względna otwierana po kliknięciu, np. /, /about lub /articles. Frontend sam dodaje prefiks języka — nie podawaj pełnej domeny.",
  "field.navs.sortNo.help": "Mniejsze wartości są wyżej; menu tego samego poziomu są sortowane według tej wartości. Można pozostawić puste (domyślnie 0).",
  "field.pages.slug.help": "Część adresu URL strony (/{locale}/{slug}). Po publikacji lepiej go nie zmieniać, bo stare linki zwrócą 404.",
  "field.articles.slug.help": "Część adresu URL artykułu (/{locale}/articles/{slug}); wpływa też na istniejące linki i indeksowanie w wyszukiwarkach.",
  "field.articles.category.help": "Decyduje, na której stronie kategorii pojawi się artykuł (/{locale}/categories/{code}). Kategorie mają jeden poziom; pole może pozostać puste.",
  "field.articles.tags.help": "Tagi artykułu, można wybrać kilka; portal może filtrować po tagach. Jeśli brak odpowiedniego tagu, utwórz go tutaj.",
  "field.articles.newTag.help": "Wpisz kod tagu i kliknij „Nowy tag”: tag zostanie utworzony i od razu wybrany dla tego artykułu.",
  "field.categories.code.help": "Kod kategorii trafia do adresu URL /categories/{code}; nie zmieniaj go po utworzeniu.",
  "field.tags.code.help": "Unikalny kod tagu, używany do filtrowania i deduplikacji; tagi nie trafiają do adresów URL, więc zmiana nazwy nie psuje linków.",
  "field.users.username.help": "Nazwa do logowania, niezmienialna po utworzeniu. Lepiej użyć prefiksu e-mail lub numeru pracownika i unikać danych, które mogą się zmienić.",
  "field.users.password.help": "Hasło początkowe tylko przy tworzeniu konta; przy pierwszym logowaniu użytkownik musi je zmienić. Pozostaw puste, aby system wygenerował je zgodnie z polityką haseł.",
  "field.users.dept.help": "Organizacja, do której należy konto. Określa zakresy danych „ta organizacja” i „ta organizacja i podrzędne”; błędny wybór ukrywa dane lub pokazuje dane, których nie powinno być widać.",
  "field.users.roles.help": "Role określają uprawnienia do menu i przycisków; można wybrać kilka, a uprawnienia się sumują. Wymagana jest co najmniej jedna rola.",
  "field.users.status.help": "Wyłączone konto nie może się zalogować; dane i dzienniki pozostają zachowane.",
  "field.roles.code.help": "Kod roli służy do autoryzacji API i rozpoznawania ról wbudowanych; nie można go zmienić po utworzeniu. Zalecane wielkie litery i podkreślenia.",
  "field.roles.dataScope.help": "Określa, czyje dane organizacyjne widzi rola. Przy wyborze „Niestandardowy” trzeba też zaznaczyć organizacje w „Zakres organizacyjny”.",
  "field.configs.type.help": "Typ parametru tylko do odczytu; decyduje o interpretacji wartości (tekst, liczba, logiczna, JSON). Niezgodność typu i wartości powoduje błąd odczytu.",
  "field.configs.value.help": "Wpisz wartość zgodnie z typem; JSON musi być poprawny. Sekrety podawaj wyłącznie jako placeholder lub odwołanie do zmiennej środowiskowej, nigdy jawnym tekstem.",
  "field.dicts.typeCode.help": "Programy czytają słownik po tym kodzie; zalecane małe litery i podkreślenia, bez zmian po utworzeniu.",
  "field.dicts.nameKey.help": "Klucz tekstu wyświetlanej nazwy (np. dict.someName); musi już istnieć w zasobach językowych.",
  "field.dicts.itemValue.help": "Wartość pozycji słownika; jest zapisywana i zwracana przez API. Lepiej jej później nie zmieniać, aby dane historyczne się zgadzały.",
  "field.dicts.labelZh.help": "Etykieta wyświetlana w chińskim interfejsie; etykieta angielska jest prowadzona osobno w kolejnym polu.",
  "field.jobs.cron.help": "Wyrażenie 6-polowe: sekunda minuta godzina dzień miesiąc dzień tygodnia. Na przykład 0 0 3 * * * uruchamia zadanie codziennie o 03:00; sprawdź przyciskiem „Uruchom raz”.",
  "field.jobs.params.help": "Obiekt JSON przekazywany do logiki zadania; pozostaw puste, jeśli zadanie nie ma parametrów.",
  "field.jobs.concurrency.help": "Przy zabronionej współbieżności to uruchomienie jest pomijane, dopóki poprzednie się nie zakończy; przy dozwolonej uruchomienia mogą się nakładać.",
  "field.banners.image.help": "Najpierw prześlij plik w „Zarządzanie plikami”, potem wybierz go tutaj. Zalecany obraz poziomy 16:9 o rozmiarze do 500 KB.",
  "field.banners.link.help": "Cel po kliknięciu banera: ścieżka względna lub pełny URL. Pozostaw puste, aby baner nie był klikalny.",
  "field.banners.startAt.help": "Baner pojawi się w portalu o tej porze; pozostaw puste, aby opublikować od razu.",
  "field.banners.endAt.help": "Baner zostanie zdjęty o tej porze; pozostaw puste, aby działał bezterminowo.",
  "field.mail.variables.help": "Zmienne szablonu jako obiekt JSON; klucze muszą odpowiadać placeholderom {name} w treści. Używane tylko do wysyłek testowych.",
  "field.notices.publishAt.help": "Komunikat pojawi się w portalu o tej porze; pozostaw puste, aby opublikować od razu.",
  "field.notices.expireAt.help": "Komunikat zostanie zdjęty o tej porze; pozostaw puste, aby działał bezterminowo."
}

export default message
