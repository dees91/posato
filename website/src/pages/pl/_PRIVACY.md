# Polityka prywatności Posato

Obowiązuje od 22 października 2026.

Posato nie wysyła zestawów, listy stron, wyboru aplikacji, sesji,
harmonogramów ani danych obszaru roboczego do swojego twórcy. Nie ma konta
Posato, analityki, reklam ani serwera prowadzonego przez Posato. Ta polityka
obejmuje Posato na Maca, iPhone'a, Linux i Android. Opcjonalna synchronizacja
z iCloud albo przez folder, wstrzymywanie na każdym urządzeniu oraz prośby o
aktualizacje na macOS są opisane poniżej.

## Dane, które Posato przechowuje na urządzeniach

Aby działać, Posato zapisuje na każdym urządzeniu:

- zestawy: ich nazwy, który zestaw jest domyślny, które domeny stron każdy
  zawiera oraz które aplikacje wybrano dla każdego na tym urządzeniu,
  zapisane jako wybory dostarczone przez system, których Posato nie może
  odczytać jako nazw aplikacji na iPhonie; na Macu, Linuxie i Androidzie
  Posato zapisuje, które to aplikacje;
- sesje: kiedy się zaczynają i kończą, którego zestawu każda użyła i czy
  zakończyły się wcześniej;
- harmonogramy: ich nazwy, ich zestawy, dni tygodnia i godziny powtarzania,
  czy każdy jest włączony oraz które zaplanowane pauzy pominięto albo
  zakończono wcześniej;
- ustawienia, takie jak to, czy synchronizacja jest włączona, który folder
  wybrano do synchronizacji przez folder oraz czy pomocnik macOS jest
  włączony;
- gdy synchronizacja jest włączona, kiedy to urządzenie ostatnio zakończyło
  synchronizację.

Te dane zostają w prywatnym magazynie Posato na urządzeniu, dopóki ich nie
zmienisz albo nie usuniesz. Na Macu, iPhonie i Linuxie te dane, z wyjątkiem
wyboru aplikacji na iPhonie, mogą też trafić do kopii zapasowych urządzenia,
takich jak iCloud Backup, Time Machine albo kopia katalogu domowego na
Linuxie, zgodnie z ustawieniami kopii zapasowych. Na Androidzie Posato
wyłącza swoje dane z kopii zapasowych Androida i z przenoszenia urządzenia.
Na Macu przeniesienie Posato do Kosza nie usuwa tych danych; zostają w
folderze danych aplikacji Posato, dopóki ich nie usuniesz. Na Linuxie
usunięcie pakietu też ich nie usuwa; zostają w folderze Posato w danych
aplikacji katalogu domowego, dopóki ich nie usuniesz.

Gdy synchronizacja jest włączona, przez iCloud albo folder, Posato trzyma też
historię zsynchronizowanych zmian w swoim prywatnym magazynie na każdym
połączonym urządzeniu: każdy utworzony, przemianowany albo usunięty zestaw,
każdą stronę dodaną do zestawu albo z niego usuniętą, który zestaw jest
domyślny i którego zestawu użyła każda sesja i każdy harmonogram, oraz kiedy
wprowadzono każdą zmianę, w tym kiedy dawne sesje się zaczęły, na kiedy
zaplanowano ich koniec i czy zakończono je wcześniej. Wpisy sprzed Posato 1.3
zapisują też zmiany wspólnej nazwy grupy aplikacji, której Posato wtedy
używało. Zmiana albo usunięcie strony lub zestawu nie wymazuje z tej historii
wcześniejszych wpisów. Historia zostaje, aż na tym urządzeniu dokończy się
**Usuń obszar roboczy** albo aż dane Posato zostaną z niego usunięte, i może
trafić do kopii zapasowych urządzenia tak jak dane powyżej.

Posato nie zbiera historii przeglądania, odwiedzanych stron, tego, które
strony zostały wstrzymane, tego, jak często otwierasz aplikacje, wyników
użycia ani żadnego innego zapisu aktywności.

## Synchronizacja z iCloud

Synchronizacja z iCloud jest opcjonalna i wyłączona, dopóki nie wybierzesz
**Synchronizuj z iCloud**.

- Zestawy (ich nazwy, ich strony i który jest domyślny), we zmianach sprzed
  Posato 1.3 wspólna nazwa grupy aplikacji, początek i koniec sesji oraz
  harmonogramy, w tym pominięte i wcześniej zakończone zaplanowane pauzy, są
  szyfrowane na urządzeniu, zanim trafią do prywatnej bazy CloudKit Twojego
  własnego konta iCloud. Twórca Posato nie może ich odczytać ani uzyskać do
  nich dostępu.
- Każda zmiana i rejestracja każdego urządzenia są zapisane jako osobny
  zaszyfrowany rekord, a te rekordy gromadzą się w Twojej bazie iCloud, dopóki
  nie wybierzesz **Usuń obszar roboczy**; Posato nie czyści ich automatycznie.
- Klucz szyfrowania jest dzielony między urządzeniami przez pęk kluczy iCloud.
- Wybór aplikacji nigdy nie jest synchronizowany; każde urządzenie trzyma
  własny.
- Apple prowadzi iCloud i może obserwować informacje techniczne potrzebne do
  jego świadczenia, takie jak powiązanie z kontem, czas, rozmiary rekordów i
  ich liczby, zgodnie z własną polityką prywatności Apple.

**Usuń obszar roboczy** usuwa zsynchronizowane rekordy Posato z Twojej bazy
iCloud, historię zsynchronizowanych zmian na urządzeniu, na którym je
usuwasz, oraz klucz obszaru roboczego z pęku kluczy iCloud, więc inne
urządzenia też tracą dostęp do obszaru roboczego; ich własne kopie zestawów,
sesji i historii zmian zostają na tych urządzeniach, dopóki nie usuniesz tam
obszaru roboczego. Zestawy i strony zapisane na urządzeniu, na którym
usuwasz obszar roboczy, zostają na tym urządzeniu. Apple może przechowywać
kopie przez pewien czas zgodnie z własnymi zasadami. Jeśli zgubisz każdą
kopię klucza, zsynchronizowanych danych nie da się odzyskać.

## Synchronizacja przez folder

Synchronizacja przez folder jest opcjonalna i wyłączona, dopóki nie
wybierzesz folderu w Posato. To jedyna synchronizacja na Linuxie i Androidzie,
a na Macu i iPhonie zastępuje synchronizację z iCloud dla tego obszaru
roboczego.

- Posato zapisuje folder `Posato` wewnątrz wybranego folderu. Jest w nim jeden
  plik z losowymi identyfikatorami obszaru roboczego oraz, dla każdej zmiany,
  osobny plik zaszyfrowany na urządzeniu tym samym szyfrowaniem co
  synchronizacja z iCloud: zestawy (ich nazwy, ich strony i który jest
  domyślny), początek i koniec sesji oraz harmonogramy, w tym pominięte i
  wcześniej zakończone zaplanowane pauzy. Wybór aplikacji nigdy nie jest
  synchronizowany; każde urządzenie trzyma własny.
- Te pliki gromadzą się w folderze, dopóki nie wybierzesz **Usuń obszar
  roboczy**, co usuwa z niego folder Posato. Posato nie czyści ich
  automatycznie.
- Posato samo niczego nie wysyła. Usługa, która utrzymuje folder w
  synchronizacji, na przykład Dropbox, OneDrive, iCloud Drive albo Syncthing,
  kopiuje pliki zgodnie z własną polityką prywatności. Nie może odczytać ich
  treści, ale może obserwować informacje techniczne, takie jak nazwy plików,
  które są losowe, rozmiary, liczby i czas zmian plików, i może przez jakiś
  czas trzymać wcześniejsze wersje albo usunięte pliki.
- Przy synchronizacji przez folder każde urządzenie trzyma klucz obszaru
  roboczego w pliku w prywatnym magazynie Posato, który może czytać tylko
  Twoje konto użytkownika, a nie w pęku kluczy iCloud. Na Androidzie plik jest
  też zapieczętowany kluczem, który nigdy nie opuszcza Android Keystore tego
  telefonu. Na Macu, iPhonie i Linuxie plik klucza może trafić do kopii
  zapasowych urządzenia tak jak dane powyżej.
- Aby dodać urządzenie, Posato na urządzeniu w obszarze roboczym pokazuje
  jednorazowy kod parowania, także jako kod QR i jako odnośnik `posato://`,
  który zawiera tylko ten kod. Umieszcza klucz obszaru roboczego w folderze,
  zaszyfrowany kluczem wyprowadzonym z kodu. Usuwa ten plik, gdy nowe
  urządzenie dołączy, gdy zamkniesz kod albo, gdy kod wygaśnie po 10 minutach,
  przy następnym uruchomieniu Posato albo przy pokazaniu nowego kodu. Posato
  pokazuje kod QR, ale nie używa aparatu. Jeśli udostępnisz odnośnik przez
  wiadomość, pocztę albo schowek, przechodzi on przez wybraną aplikację.
  Każdy, kto ma kod albo odnośnik i może otworzyć folder albo kopię tego
  pliku w historii plików, może odzyskać klucz obszaru roboczego, nawet po
  wygaśnięciu kodu, więc udostępniaj je tylko własnym urządzeniom.

**Usuń obszar roboczy** usuwa folder Posato z wybranego folderu, jeśli to
urządzenie nadal może do niego sięgnąć, oraz plik klucza obszaru roboczego i
historię zsynchronizowanych zmian na urządzeniu, na którym go usuwasz. Inne
urządzenia trzymają własne kopie zestawów, sesji, historii zmian i pliku
klucza, dopóki nie usuniesz tam obszaru roboczego. Usługa synchronizacji może
trzymać wcześniejsze wersje plików zgodnie z własnymi zasadami. Jeśli zgubisz
każdą kopię klucza, zsynchronizowanych danych nie da się odzyskać.

## Czas przed ekranem na iPhonie

Na iPhonie Posato korzysta z mechanizmu Czas przed ekranem firmy Apple, aby
wstrzymywać wybrane strony i aplikacje. Wybór aplikacji to nieprzejrzyste
wybory systemowe, które zostają na urządzeniu; Posato nie otrzymuje od Czasu
przed ekranem nazw aplikacji ani danych o użyciu. Aby harmonogram mógł
wystartować przy zamkniętym Posato, Posato trzyma kopię harmonogramów oraz
stron i wyboru aplikacji każdego zestawu w magazynie dzielonym tylko z własnym
rozszerzeniem Czasu przed ekranem na tym samym iPhonie. Nigdy nie opuszcza
urządzenia, jest wyłączone z kopii zapasowych urządzenia i jest usuwane, gdy
usuwane są dane Posato albo obszar roboczy.

## Wstrzymywanie na Macu

Na Macu pomocnik Posato w tle wstrzymuje wybrane strony, kierując ruch
przeglądarki przez lokalne proxy na Macu, gdy sesja jest aktywna. Pomocnik
ogląda każde żądanie tylko tak długo, ile trzeba, aby je przepuścić albo
wstrzymać, i nie zapisuje go ani nie wysyła. Aby pokazać stronę pauzy w
bieżącej karcie Safari albo Chrome, Posato może użyć uprawnienia
Automatyzacja; odczytuje wtedy tylko adres bieżącej karty w tej chwili i nie
prowadzi zapisu. Posato nie odszyfrowuje ruchu HTTPS.

Jeśli zdecydujesz, aby na Macu rozpoczynać sesje bez hasła administratora,
pomocnik trzyma zapis tego uprawnienia w pliku systemowym, który może czytać
tylko system. Zapis zawiera numer użytkownika i identyfikator systemowy konta
na Macu oraz identyfikator sprzętowy Maca. Nigdy nie opuszcza Maca i jest
usuwany, gdy wyłączysz tę opcję albo usuniesz Posato z tego Maca.

## Wstrzymywanie na Linuxie

Na Linuxie Posato instaluje usługę pauzy, która działa z uprawnieniami
systemowymi, raz, Twoim hasłem. Żądania może do niej wysyłać tylko Twoje
konto użytkownika i system. W trakcie pauzy usługa:

- dodaje każdą wstrzymaną stronę i jej nazwę `www` do pliku systemowego
  `/etc/hosts`, który mogą czytać inne konta użytkownika na komputerze, i
  usuwa je, gdy pauza się kończy;
- kończy wybrane wstrzymane programy, które działają na Twoim koncie
  użytkownika, sprawdzając, którym programem jest każdy działający proces,
  bez czytania ani przechowywania tego, co w nim robisz.

Usługa trzyma Twoją nazwę użytkownika i numer użytkownika w pliku konfiguracji
systemowej, a wymuszaną pauzę, wraz ze stronami i położeniem programów, w
pliku systemowym, który może czytać tylko system, aby restart utrzymał pauzę.
Po zakończeniu pauzy ten plik trzyma tylko identyfikator sesji, dopóki Posato
go nie odczyta. Usunięcie pakietu usuwa usługę, przywraca `/etc/hosts` i
usuwa oba pliki. Nic, czym zajmuje się usługa, nie opuszcza komputera.

## Wstrzymywanie na Androidzie

Na Androidzie Posato potrzebuje kilku uprawnień, każdego nadanego w
ustawieniach Androida:

- **VPN.** Gdy trwa pauza ze stronami, Posato uruchamia lokalny VPN, który
  przenosi tylko zapytania o nazwy, czyli pytania telefonu o adres strony. Sam
  odpowiada na wstrzymane nazwy, a każde inne zapytanie przekazuje do serwera
  DNS sieci, w której jesteś, albo do Google Public DNS, gdy ta sieć nie
  oferuje żadnego, z którego Posato może skorzystać. Żaden inny ruch nie
  wchodzi do VPN, a Posato nie zapisuje ani nie przechowuje zapytań.
- **Dostęp do danych o użyciu.** Gdy trwa pauza z aplikacjami, Posato sprawdza
  około dwa razy na sekundę, która aplikacja jest na wierzchu, aby zasłonić
  wstrzymaną aplikację ekranem pauzy. Trzyma w pamięci tylko aplikację na
  wierzchu w danej chwili i nie zapisuje użycia aplikacji.
- **Wyświetlanie nad innymi aplikacjami**, aby pokazać ekran pauzy.
- **Powiadomienia, dokładne alarmy i działanie bez limitów baterii**, aby
  pauzy i harmonogramy zaczynały się i kończyły o czasie, gdy Posato jest
  zamknięte. Ciche powiadomienie widać, gdy Posato utrzymuje je w działaniu.
- **Dostęp do wszystkich plików**, aby Posato mogło czytać i zapisywać folder
  wybrany do synchronizacji przez folder. Posato czyta i zapisuje tylko ten
  folder.

Posato czyta listę aplikacji, które można otworzyć z launchera, aby dało się
wybrać aplikacje do pauzy; lista zostaje na telefonie. Wymuszana pauza, wraz
ze stronami i nazwami pakietów aplikacji, jest trzymana w prywatnym magazynie
Posato do końca pauzy, aby utrzymał ją restart.

## Eksport i import

Możesz wyeksportować zestawy do pliku i zaimportować go na innym urządzeniu.
Plik zawiera nazwy zestawów i strony, który zestaw jest domyślny, oraz
harmonogramy. Nigdy nie zawiera wyboru aplikacji, sesji, historii, nazw
urządzeń ani kluczy synchronizacji. Posato zapisuje plik tylko tam, gdzie go
zapisujesz albo udostępniasz, i samo nigdzie niczego nie wysyła.

- Bez kodu każdy, kto dostanie plik, może odczytać zestawy i strony.
- Przy **Chroń kodem** plik jest szyfrowany kluczem wyprowadzonym z
  dziewięcioznakowego kodu, który Posato pokazuje raz i którego nie
  przechowuje. Kod powstrzymuje przypadkowego czytelnika pliku, który trafi do
  niewłaściwej osoby, ale ktoś, kto ma plik i poświęci dużo mocy obliczeniowej
  na zgadywanie, mógłby go otworzyć, więc wysyłaj plik i kod tylko tam, gdzie
  im ufasz.
- Na iPhonie udostępniony plik to tymczasowa kopia, którą Posato usuwa, gdy
  arkusz udostępniania się zamyka, a importowany plik jest czytany z
  tymczasowej kopii, którą Posato usuwa po odczytaniu.

Import zmienia zestawy i harmonogramy tak jak ręczna edycja; jeśli
synchronizacja jest włączona, zmiany docierają na inne urządzenia.

## Powiadomienia

Na Macu i iPhonie Posato może pokazywać lokalne powiadomienia, gdy pauza
kończy się albo zaczyna na innym urządzeniu. Tworzy je na urządzeniu system
powiadomień tego urządzenia; Posato nie wysyła z ich powodu niczego na serwer.
Możesz je wyłączyć w O Posato albo w ustawieniach urządzenia.

## Aktualizacje na Macu

Posato na iPhone'a, Linux i Android samo nie sprawdza aktualizacji. Na Macu
Posato pyta, zanim będzie sprawdzać aktualizacje automatycznie. Jeśli się
zgodzisz, sprawdza raz dziennie, gdy aplikacja działa. Możesz wyłączyć
automatyczne sprawdzanie albo sam wybrać Sprawdź aktualizacje. Każda
aktualizacja wymaga Twojego działania, aby ją pobrać i zainstalować, a
instalacja czeka, aż żadna sesja nie jest aktywna.

Informacje o aktualizacjach i pliki do pobrania pochodzą z GitHub Releases
przez Sparkle, bibliotekę aktualizacji dołączoną do Posato. Prośby nie
zawierają listy stron, wyboru aplikacji, sesji, danych iCloud, identyfikatora
instalacji ani profilu systemu. Posato porównuje wersje i wymagania systemowe
na Macu.

GitHub i jego dostawcy dostarczania mogą otrzymać adres IP, czas prośby,
żądany zasób i techniczne informacje o połączeniu. Adres pobierania wskazuje
wersję, o którą prosisz. Obchodzą się z tymi informacjami zgodnie z
[oświadczeniem o prywatności GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
GitHub udostępnia zbiorcze liczby pobrań; Posato nie dodaje analityki użycia
ani identyfikatora śledzenia. Otwarcie odnośnika do wydania albo pomocy w
przeglądarce podlega zasadom tej strony i ustawieniom przeglądarki.

Ustawienia aktualizacji są zapisane na tym Macu i nie są synchronizowane przez
iCloud. Sparkle przechowuje stan sprawdzania aktualizacji i tymczasowe pliki
aktualizacji lokalnie.

## Diagnostyka

Posato nie zbiera ani nie wysyła diagnostyki, raportów awarii ani telemetrii.
Poza wybraną synchronizacją, zapytaniami, które przekazuje VPN na Androidzie,
oraz prośbami o aktualizacje opisanymi w Aktualizacjach na Macu, Posato nie
wykonuje własnych żądań sieciowych.
System operacyjny albo sklep z aplikacjami może zbierać diagnostykę zgodnie z
ustawieniami urządzenia i własnymi zasadami; Posato nie ma do niej dostępu i
nic do niej nie dodaje.

Jeśli prosisz o pomoc, podaj tylko to, co sam wybierzesz. W publicznych
zgłoszeniach nie dołączaj nazw stron, nazw aplikacji, szczegółów urządzenia,
zrzutów ekranu ani dzienników.

## Dzieci

Posato nie jest przeznaczone do zarządzania urządzeniem innej osoby i
świadomie nie zbiera danych od nikogo.

## Zmiany

Zmiany tej polityki będą publikowane pod tym samym adresem z nową datą
obowiązywania.

## Kontakt

Pytania o prywatność: privacy@posato.app
