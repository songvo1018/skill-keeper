# Code Review: skills-keeper

- **Дата:** 2026-09-13
- **Ревизия:** `af1f40a` (branch `master`)
- **Скоуп:** 23 класса в `src/main`, 13 тестовых классов. Spring Boot 4.1.1 / Java 25. Фичи: `auth`, `filestorage`.
- **Роль ревьюера:** senior/lead Java

> **Ограничение ревью.** Собрать проект и прогнать тесты локально не удалось: в системе доступен только
> JDK 21 (`C:\Program Files\OpenIDE\...\jbr`), а `pom.xml` требует `java.version=25`. Ревью статическое —
> выводы по тестам сделаны из чтения кода, а не из зелёного прогона. Любой агент, реализующий
> исправления, обязан добиться реального `./mvnw test` на JDK 25.

## Вердикт

Код аккуратный, читаемый, с последовательным стилем и неплохим покрытием happy path. Но есть
**2 блокера** (падение старта приложения и 500 на скачивании валидно загруженного файла) и системный
пробел — **в проекте нет ни одной строчки логирования**. Прод-готовность: нет.

Часть «страшного» в `auth` (отсутствие экспирации токенов, stub-верификатор, незащищённый actuator) —
**осознанный tracked-долг**, зафиксированный в Non-Goals в
`openspec/changes/archive/2026-09-12-add-token-authentication/{proposal,design}.md`. Ниже претензии
разделены на **дефекты** (код работает не так, как должен) и **принятый долг**, который пора закрывать.

---

## Классификация

| Severity | Findings |
|---|---|
| 🔴 Blocker | 1, 2 |
| 🟠 Major | 3, 4, 5, 6, 7, 8 |
| 🟡 Minor | 9–19 |
| 🧪 Tests | 20–25 |
| 🏛 Architecture / process | 26–29 |

---

## 🔴 Блокеры

### Finding 1 — Сайдкар `{}` валит запуск приложения

`src/main/java/com/skillskeeper/skillskeeper/filestorage/FileStorageService.java:88`

```java
FileMetadata metadata = objectMapper.readValue(metaPath.toFile(), FileMetadata.class);
index.put(metadata.id(), metadata);
```

Jackson спокойно десериализует `{}` в record с `null`-полями — это **валидный JSON**, `JacksonException`
не летит. Далее `ConcurrentHashMap.put(null, …)` → `NullPointerException`, которую `catch (JacksonException e)`
на строке 89 не ловит. Результат: приложение не стартует, пока файл не удалят руками.

Сценарий реален ровно тот, под который писался этот `catch`: обрыв процесса на полузаписанном сайдкаре.
Комментарий на строке 90 обещает «not fatal» — обещание не выполняется.

**Фикс:** валидировать `metadata.id()` на `null`/blank и на совпадение с именем файла; ловить `RuntimeException`;
логировать пропуск.

### Finding 2 — `GET /api/files/{id}` падает в 500 на клиентском `Content-Type`

`filestorage/FileStorageController.java:41-43`

```java
MediaType contentType = metadata.contentType() != null
        ? MediaType.parseMediaType(metadata.contentType())
        : MediaType.APPLICATION_OCTET_STREAM;
```

`contentType` приходит из multipart-заголовка клиента, пишется на диск **как есть, без валидации**, и
парсится на скачивании. Любой мусор в `Content-Type` части (`foo`, `text/`, `a/b; charset=«`) →
`InvalidMediaTypeException` → 500 при каждой попытке скачать. Файл становится непригодным навсегда,
загрузившись с 201.

**Фикс:** валидировать/парсить на `store()` (fail fast, 400), а не на выдаче; на выдаче — fallback на
`application/octet-stream` при любой ошибке парсинга.

---

## 🟠 Major

### Finding 3 — Content-Disposition собирается сырым `String.format`

`filestorage/FileStorageController.java:47-48`

`sanitizeFilename()` вырезает только `/` и `\`. Кавычки, CR/LF и не-ASCII остаются:

- `my"file.txt` ломает кавычки заголовка;
- `отчёт.pdf` уедет в заголовок UTF-8 байтами без RFC 5987-кодирования (клиенты покажут мусор);
- CR/LF Tomcat отобьёт исключением → 500.

**Фикс** — штатный билдер Spring:

```java
.header(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
                .filename(metadata.originalFilename(), StandardCharsets.UTF_8)
                .build().toString())
```

Уйдёт и константа-шаблон, и риск инъекции в заголовок. Попутно добавить `X-Content-Type-Options: nosniff` —
сейчас `text/html` от клиента отдаётся обратно с его же Content-Type.

> **Примечание по проверке (добавлено при реализации).** Проверять это через `curl` из Git Bash на Windows нельзя: консоль перекодирует не-ASCII аргумент до того, как его получит `curl.exe`, и результат выглядит как порча имени на сервере, хотя сервер тут не при чём. MockMvc тоже не годится — он передаёт имя Java-строкой, минуя разбор заголовка части. Нужен реальный сервер и вручную собранное multipart-тело; см. `FileStorageMultipartEncodingTest`.

### Finding 4 — `store()` неатомарен: орфаны на диске навсегда

`filestorage/FileStorageService.java:44-56`

Сначала `transferTo(binPath)`, потом `writeValue(metaPath)`. Если второе падает (или процесс умирает между) —
`.bin` остаётся на диске, в индексе его нет, `load()` отдаёт 404 (требует оба файла). Файл невидим, не
скачиваем, не удаляем, но занимает место. Ни компенсации, ни сборки мусора, ни `DELETE`-эндпоинта нет.

**Фикс:** писать `.bin` в `*.tmp`, затем мету, затем `Files.move(..., ATOMIC_MOVE)`; в `catch` на мете —
удалять осиротевший `.bin`.

### Finding 5 — Два источника правды: индекс в памяти vs диск

`filestorage/FileStorageService.java:59-80`

`listFiles()` читает `index`, `load()` — заново парсит JSON с диска, **хотя те же данные уже лежат в памяти**.
Лишний I/O и десериализация на каждое скачивание, плюс два пути, которые расходятся:

| Состояние на диске | `GET /api/files` | `GET /api/files/{id}` |
|---|---|---|
| `.meta.json` без `.bin` | файл в списке ✅ | 404 ❌ |
| `.bin` без `.meta.json` | нет в списке | 404 |
| id внутри JSON ≠ имя файла | показывает один id | 404 по нему |

**Фикс:** `load()` берёт метаданные из индекса, с диска читает только байты.

### Finding 6 — Ноль логирования во всём приложении

`grep` по `Logger`/`log.` не находит ничего. Последствия:

- пустой `catch` в `FileStorageService.java:89-91` молча глотает битые сайдкары — потерю файла нечем диагностировать;
- `FileStorageException` вообще не имеет хендлера в `FileStorageExceptionHandler` → 500 без единой записи о причине;
- неудачные логины и невалидные токены нигде не фиксируются → аудит и алертинг по brute-force невозможны в принципе.

Для сервиса, который пишет файлы на диск, это самый дорогой пробел в списке.

### Finding 7 — Незаэкспайренные токены + верификатор-заглушка = неаутентифицированное исчерпание памяти

`auth/TokenService.java:12`, `auth/AlwaysApprovingCredentialsVerifier.java:9`

По отдельности каждый пункт — заявленный Non-Goal. В комбинации получается то, чего в Non-Goals нет:
`Set` только растёт, логин одобряет **любого**, rate limiting отсутствует. Скрипт на `POST /api/auth/login`
в цикле гарантированно съедает heap. Если стенд окажется доступен извне — это не долг, а уязвимость.

**Фикс (дёшево сейчас):** TTL на токены и жёсткий cap на размер стора; `AlwaysApprovingCredentialsVerifier`
не должен подниматься вне dev-профиля (`@Profile("!prod")` + fail-fast проверка на старте). Имя класса
честное, но имя не остановит деплой.

### Finding 8 — Аутентифицированная личность нигде не сохраняется

`auth/AuthTokenInterceptor.java:18-30`

Интерцептор отвечает только «да/нет» и ничего не кладёт ни в `request.setAttribute`, ни в какой контекст.
Токен не связан с username (`Set<String>`, не `Map<String, …>`). Следствие: `FileMetadata` не имеет владельца,
и **любой залогиненный видит и скачивает файлы всех остальных**. С учётом Finding 7 (логинится кто угодно) —
это публичное файлохранилище с декоративным замком.

Смена `Set<String>` → `Map<String, TokenPrincipal>` сейчас стоит ~20 строк. После пары фич поверх — это
рефакторинг всего `/api`.

---

## 🟡 Minor

### Finding 9 — `Bearer` сравнивается case-sensitive

`auth/AuthTokenInterceptor.java:20` — `header.startsWith("Bearer ")`. По RFC 7235 схема аутентификации
**регистронезависима**; `bearer <token>` от валидного клиента получит 401. Нужен `regionMatches(true, …)`.

### Finding 10 — 401 без заголовка `WWW-Authenticate`

`auth/AuthExceptionHandler.java:12-14`. RFC 7235 требует его на каждом 401. Сейчас клиент не может
определить схему программно.

### Finding 11 — I/O в конструкторе record-а `@ConfigurationProperties`

`filestorage/FileStorageProperties.java:13-20`. `Files.createDirectories()` внутри canonical-конструктора
value-объекта — побочный эффект там, где его не ждут. Следствия: (а) если `app.file-storage.base-dir` не
задан в каком-то профиле, `baseDir.toAbsolutePath()` даёт NPE вместо внятного «property required»;
(б) объект нельзя создать в тесте, не потрогав ФС. Создание каталога — работа `FileStorageService`
(`@PostConstruct`) или отдельного инициализатора.

### Finding 12 — `resolveWithinBaseDir` возвращает `null`

`filestorage/FileStorageService.java:98-101` — прямое нарушение собственного правила проекта
`.claude/skills/correct-function-skill`: метод с возвращаемым значением при ошибке должен бросать
исключение, а не возвращать `null`. Вызывающий код на строке 63 вынужден делать четырёхчастную проверку.

Попутно: нормализация лексическая, симлинки не разрешаются. Риск низкий (имена — серверные UUID), но
`toRealPath()` был бы честнее как defense-in-depth.

### Finding 13 — Общий `ObjectMapper` для HTTP и для дискового формата

`filestorage/FileStorageService.java:27` — инжектится веб-маппер Spring. Любая будущая настройка JSON для API
(naming strategy, `FAIL_ON_UNKNOWN`, формат дат) молча поменяет формат хранения и сломает чтение старых
сайдкаров. Формат персистентности нужно зафиксировать отдельным `ObjectMapper`-ом.

### Finding 14 — `GET /api/files` без сортировки и пагинации

`List.copyOf(index.values())` — порядок `ConcurrentHashMap`, т.е. произвольный и нестабильный между
запросами. Плюс отдаётся весь индекс целиком: на 100k файлов это 100k элементов в одном ответе.
В `FileMetadata` нет даже `createdAt`, так что сортировать пока не по чему.

### Finding 15 — Константы сообщений централизованы наполовину

`FileStorageMessages` существует, но рядом живут инлайновые литералы: `"Uploaded file must not be empty"`
(`FileStorageService.java:35`), `"Failed to store uploaded file"` (`:47`),
`"Uploaded file exceeds the maximum allowed size"` (`FileStorageExceptionHandler.java:20`),
`"Invalid username or password"` (`AuthController.java:24`). Формально правило
`no-inline-concatenated-strings` не нарушено (нет конкатенации), но читателю непонятно, где искать текст ошибки.

Заодно: `AuthMessages.BEARER_PREFIX` — это не message, а протокольная константа; в классе с таким именем
ей не место.

### Finding 16 — `LoginRequest` без ограничений длины и с утечкой пароля в `toString()`

`@NotBlank` есть, `@Size` нет — 10-мегабайтный username пройдёт валидацию. Сгенерированный `toString()`
record-а печатает пароль в открытом виде; при DEBUG-логировании `BindingResult` он окажется в логах.

### Finding 17 — `base-dir=./data/files` — относительный путь

`src/main/resources/application.properties`. Зависит от CWD процесса: в IDE, в `java -jar` и в контейнере
это три разных каталога. Для стейтфул-пути нужен абсолютный, задаваемый через env.

### Finding 18 — Мусор от скелета в `pom.xml`

Пустые `<description/>`, `<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`,
`<scm>` с пустыми детьми. Часть release-плагинов на таком спотыкается.

Чего в сборке нет и пора бы: jacoco, Checkstyle/Spotless, spotbugs, `maven-enforcer`. CI-конфига
(`.github/`) нет ни в каком виде.

### Finding 19 — Мелочи

- `HelloController` — остаток скелета в корневом пакете, возвращает строку. Удалить либо увести в `/actuator/info`.
- `com.skillskeeper.skillskeeper` (дублирование) при артефакте `skills-keeper` и groupId `com.skillskeeper` —
  три разных написания одного имени. Переименовать сейчас дёшево, потом нет.
- Нет OpenAPI/springdoc и нет README — для API, который кто-то будет вызывать, это пробел.
- `loadIndexFromDisk()` вызывается из конструктора; `@PostConstruct` чище.

---

## 🧪 Тесты

Покрытие happy path приличное, интеграционные тесты на 401/413 — хорошо. Но есть проблемы, которые
обесценивают часть прогона.

### Finding 20 — Тавтологическая проверка path traversal

`src/test/java/com/skillskeeper/skillskeeper/filestorage/FileStorageControllerTest.java:99-101`

```java
try (var paths = Files.list(tempDir)) {
    assertThat(paths).allMatch(path -> path.getParent().equals(tempDir));
}
```

`Files.list()` по контракту возвращает **только непосредственных детей** каталога — их `getParent()` равен
`tempDir` всегда, при любом поведении кода. Assert не может упасть. Проверять нужно обратное: что рядом с
`tempDir` не появилось новых файлов, и что число файлов в `tempDir` равно 2×(число загрузок).

### Finding 21 — Тесты заглушки цементируют заглушку

`auth/AlwaysApprovingCredentialsVerifierTest.java` — четыре ассерта на `return true`, включая
`verify("", "")` → `true`. Ценность ноль, а вред есть: когда придёт реальная проверка, этот тест будет
*требовать* одобрять пустые креды.

### Finding 22 — `Validator` используется после закрытия фабрики

`auth/LoginRequestTest.java:18-22`

```java
static {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
        VALIDATOR = factory.getValidator();
    }   // factory закрыта, VALIDATOR из неё живёт дальше
}
```

На Hibernate Validator сегодня работает, но это use-after-close: контракт `AutoCloseable` у фабрики такого
не гарантирует.

### Finding 23 — Тесты пишут в рабочий каталог репозитория

`AuthEnforcementTest`, `AuthLoginEndpointTest`, `SkillsKeeperApplicationTests` не переопределяют
`app.file-storage.base-dir` → при подъёме контекста создаётся `./data/files` прямо в репозитории.
В `.gitignore` он есть, так что не коммитится, но тесты не должны оставлять следов на ФС.

### Finding 24 — `FileStorageUploadLimitsTest` фиксирует неожиданный порядок: 413 раньше 401

`filestorage/FileStorageUploadLimitsTest.java:44-63` отправляет 11 МБ **без токена** и ждёт 413. Тест
проходит — и тем самым документирует, что неаутентифицированный клиент заставляет сервер распарсить
multipart до проверки токена. Это расходится со спекой authentication («reject the request **before** it
reaches that endpoint's normal handling») и даёт дешёвый вектор нагрузки.

### Finding 25 — Дубли и гранулярность

`AuthEnforcementTest.protectedEndpointWithoutTokenReturnsUnauthorizedProblemDetail` и
`ExistingEndpointsRequireTokenTest.helloWithoutTokenIsUnauthorized` проверяют одно и то же. Почти все тесты —
`@SpringBootTest` с полным контекстом там, где хватило бы `@WebMvcTest`/юнита; на 13 классах это уже
ощутимо по времени сборки.

**Чего не хватает:** кейсов на Findings 1–5 (битый сайдкар, мусорный content-type, сбой записи меты,
`.meta` без `.bin`), на юникод/кавычки в имени файла, на `bearer` в нижнем регистре, на конкурентную загрузку.

---

## 🏛 Архитектура и процесс

### Finding 26 — PostgreSQL заявлен, но не используется

В `openspec/config.yaml` БД — PostgreSQL, фактически метаданные лежат в самописных JSON-сайдкарах с индексом
в памяти. Это вручную написанная БД: без транзакций (Finding 4), без консистентности (Finding 5), без запросов
и пагинации (Finding 14). Пока файлов мало — работает. Решение «почему не Postgres» нигде не зафиксировано —
его стоит либо записать как осознанный выбор с триггером пересмотра, либо закрыть долг, пока схема тривиальна.

### Finding 27 — Горизонтальное масштабирование сейчас невозможно

И это нигде не сказано вслух: in-memory токены (вторая реплика не знает чужих токенов), in-memory индекс
файлов (реплики видят разные списки), локальный диск. Любой деплой в >1 инстанс даст плавающие 401 и
неполные листинги.

### Finding 28 — Расхождение спеки и кода по actuator

`openspec/specs/authentication/spec.md` требует токен на «every request to an endpoint other than login»,
интерцептор висит только на `/api/**` (`auth/AuthWebConfig.java:19`), `/actuator/**` открыт. В Non-Goals
изменения это явно исключено — значит, расходится не код, а **текст главной спеки**, и поправить нужно его.
Реальный риск низкий: по умолчанию наружу торчит только `/actuator/health` без деталей.

### Finding 29 — Spring Security vs HandlerInterceptor: нужен триггер пересмотра

Решение в `design.md` аргументировано честно, и для «есть ли строка в сете» интерцептор действительно
достаточен. Но цена этого выбора уже видна: Findings 7, 8, 9, 10 — ровно то, что Spring Security даёт из
коробки (TTL, principal в контексте, корректная обработка схемы, `WWW-Authenticate`).

Предлагаемый явный триггер: **первое же требование вида «этот эндпоинт только для роли X» или «файлы видит
только владелец» = время заводить Spring Security**, а не дописывать интерцептор.

---

## Что сделано хорошо

Это не формальность — вещи ниже заметно выше среднего для проекта такого размера:

- **Path traversal продуман на двух уровнях** — и санитизация имени при записи, и проверка
  `startsWith(baseDir)` при чтении, с тестами на оба. Большинство самописных файлохранилищ не делают ни одного.
- **`ProblemDetail` (RFC 7807) как единый формат ошибок** во всём API, с per-package `@RestControllerAdvice`.
- **Конструкторная инъекция везде, ни одного `@Autowired` на поле** в проде, всё `final`.
- **Records для DTO/value-объектов** — `LoginRequest`, `FileMetadata`, `StoredFile`, `FileStorageProperties`.
- **`CredentialsVerifier` как интерфейс с ровно одной реализацией** — и `design.md` отдельно объясняет, почему
  это единственное оправданное исключение из «одного конкретного класса». Именно так и надо вводить seam:
  под известное будущее требование, а не спекулятивно.
- **Комментарий в `FileStorageUploadLimitsTest`** объясняет *почему* здесь реальный сервер вместо MockMvc.
- **Дисциплина OpenSpec.** Каждая фича имеет proposal → design → spec → tasks, с зафиксированными
  альтернативами и Risks/Trade-offs. Долг не «забыт», а записан — именно это позволило отделить дефекты
  от осознанных решений.

---

## Приоритетный план

| # | Что | Findings | Почему сначала |
|---|---|---|---|
| 1 | NPE на битом сайдкаре | 1 | приложение не стартует |
| 2 | Валидация content-type при записи | 2 | файл грузится, но не скачивается |
| 3 | Логирование (SLF4J) + хендлер `FileStorageException` | 6 | без этого 1–5 не диагностируются в проде |
| 4 | `ContentDisposition` builder + nosniff | 3 | некорректные заголовки / инъекция |
| 5 | Атомарная запись + удаление `.bin` при сбое | 4 | утечка диска без возможности вычистить |
| 6 | `load()` берёт мету из индекса | 5 | убирает расхождение list/load и лишний I/O |
| 7 | TTL + cap на токены; `@Profile("!prod")` на stub | 7 | исчерпание памяти без аутентификации |
| 8 | `Map<String, principal>` + owner в `FileMetadata` | 8 | потом это стоит в разы дороже |
| 9 | Починить тавтологии, удалить тесты заглушки | 20–25 | сейчас часть прогона не проверяет ничего |
| 10 | CI + jacoco + Spotless; абсолютный `base-dir` | 17, 18 | инфраструктурная гигиена |

---

## Связанный change

Исправления Findings 1–13, 16, 20–25 спланированы в
[`openspec/changes/archive/2026-09-13-fix-code-review-defects`](../../openspec/changes/archive/2026-09-13-fix-code-review-defects/proposal.md).
Findings 14, 15, 17–19, 26–29 в него сознательно не вошли — см. раздел Non-Goals того change.
