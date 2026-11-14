# ru.typizer:typizer-java — Java SDK для Typizer API

Официальный Java SDK для [Typizer](https://typizer.ru) — сервиса, который извлекает данные из
документов (счета, УПД, накладные, договоры, сканы и фото) в JSON по вашей схеме.

Один вызов `client.extract(файл, схема)` — и у вас прогон с результатом, проверками полей и
источниками значений. Ресурсы называются как в REST API и документации (`runs`, `files`, `typizers`,
`catalog`, `batches`, `webhookEndpoints`), коды ошибок — те же, что в справочнике. Полная
документация — [docs.typizer.ru](https://docs.typizer.ru).
- Java 8 и новее (байткод Java 8; проверяется на 8, 17 и 21), Kotlin и Scala — тем же артефактом.
- **Ни одной обязательной зависимости**: HTTP — `HttpURLConnection` из JDK, JSON — встроенный
  парсер. Никаких конфликтов версий Jackson, OkHttp или Netty с вашим приложением.
- Транспорт заменяемый: OkHttp, Apache HttpClient, корпоративный прокси, ГОСТ-TLS (КриптоПро JCP).
- Повторы, `Idempotency-Key`, таймауты, проверка подписи вебхуков — включены по умолчанию.

## Установка

Maven:

```xml
<dependency>
  <groupId>ru.typizer</groupId>
  <artifactId>typizer-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("ru.typizer:typizer-java:0.1.0")
```

Gradle (Groovy):

```groovy
implementation 'ru.typizer:typizer-java:0.1.0'
```

Артефакт лежит в Maven Central; если сборка ходит через корпоративный Nexus или Artifactory, он
придёт через их прокси-репозиторий Central.

## Ключ API

1. Зарегистрируйтесь в кабинете [my.typizer.ru](https://my.typizer.ru).
2. Откройте раздел «Ключи API» и создайте ключ со скоупом `runs:write` (для запуска прогонов) —
   подробнее о скоупах в [«Ключах API»](https://docs.typizer.ru/how-to/api-keys/).
3. Ключ (`tz_live_…`) показывается один раз. Сохраните его, например в переменную окружения:

```sh
export TYPIZER_API_KEY="tz_live_…"
```

`TypizerClient.fromEnv()` (или `builder().build()` без `apiKey`) берёт ключ из `TYPIZER_API_KEY`;
без ключа клиент не создастся — `ConfigurationException` сразу, а не при первом запросе. Ключ —
секрет сервера: не храните его в репозитории. SDK не пишет ключ ни в логи, ни в тексты исключений,
а `toString()` клиента показывает только маску `tz_live_…a1b2`.

## Быстрый старт

Схему удобно держать в файле рядом с кодом — например, `invoice.schema.json`:

```json
{
  "type": "object",
  "properties": {
    "invoice_number": { "type": "string" },
    "invoice_date": { "type": "string", "format": "date" },
    "total": { "type": "number" },
    "seller": {
      "type": "object",
      "properties": {
        "name": { "type": "string" },
        "inn": { "type": "string", "x-typizer-check": "inn" }
      }
    }
  },
  "required": ["invoice_number", "total"]
}
```

Распознать документ и получить данные:

```java
TypizerClient client = TypizerClient.fromEnv();
String schema = new String(Files.readAllBytes(Paths.get("invoice.schema.json")), StandardCharsets.UTF_8);

Run run = client.extract(Paths.get("invoice.pdf"), schema);

Map<String, Object> data = run.output();          // {invoice_number=А-1024, total=3660, seller={…}}
System.out.println(data.get("invoice_number"));

if (run.needsReview()) {                           // какие-то проверки не прошли — покажите человеку
    for (FieldResult field : run.fieldsToReview()) {
        System.out.println(field.path() + ": " + field.statusValue() + " " + field.reason());
    }
}
```

`extract` отправляет файл в том же запросе, ждёт результата (до 300 секунд: сначала на стороне
сервера, потом опросом) и возвращает завершённый прогон. Схема — JSON-текст, `Map` (возьмите
`LinkedHashMap`: порядок свойств — это порядок столбцов в CSV/XLSX и элементов в XML) или дерево
Jackson/Gson (`JsonNode`, `JsonObject`). Документ — `Path`, `java.io.File`, `byte[]`,
`InputStream`, `URL` или `TypizerFile`:

```java
TypizerClient client = TypizerClient.fromEnv();
String schema = new String(Files.readAllBytes(Paths.get("invoice.schema.json")), StandardCharsets.UTF_8);

byte[] bytes = Files.readAllBytes(Paths.get("invoice.pdf"));
Run fromBytes = client.extract(TypizerFile.fromBytes(bytes, "invoice.pdf"), schema);  // имя — для типа файла
Run fromUrl = client.extract(new URL("https://typizer.ru/samples/invoice.pdf"), schema);

File uploaded = client.files().upload(Paths.get("invoice.pdf"));                       // загрузить один раз…
Run fromId = client.extract(TypizerFile.fromId(uploaded.id()), schema);                // …и запускать по id
```

Поток (`InputStream`) SDK читает в память один раз (не больше 50 МиБ — это лимит API), чтобы
повтор после сетевой ошибки мог отправить его снова; сам поток не закрывает. Файл больше 50 МиБ
отклоняется до отправки той же `FileTooLargeException`, что вернул бы сервер.

Результат — модель `Run`: `status()`, `output()` (ключи и порядок — как в вашей схеме, числа —
`Long` или `BigDecimal`, без потери копеек), `fields()` — проверка каждого поля (`ok`, `warn` с
причиной вроде `checksum_mismatch`, `missing`) и где в документе найдено значение (`sources()`:
страница, рамка, текст). Числовой «уверенности» API не отдаёт — вместо неё эти проверки.

Свой класс вместо `Map` — через Jackson или Gson, если они есть в приложении (SDK их не тянет):

```java
public static class Invoice {
    public String invoice_number;
    public java.math.BigDecimal total;
}
```

```java
TypizerClient client = TypizerClient.fromEnv();
Run run = client.extract(Paths.get("invoice.pdf"), new String(Files.readAllBytes(Paths.get("invoice.schema.json")), StandardCharsets.UTF_8));
Invoice invoice = run.outputAs(Invoice.class);   // или run.outputJson() — в любую свою библиотеку
System.out.println(invoice.total);
```

## Схема из каталога

Для типовых документов не нужно писать схему: возьмите готовую из
[каталога](https://typizer.ru/catalog/) — `schet-faktura`, `upd`, схемы сообщества
`издатель/схема`. Версия после `@` фиксирует схему, чтобы обновления каталога не меняли ваш
результат:

```java
TypizerClient client = TypizerClient.fromEnv();

Run run = client.extract(ExtractParams.builder()
        .file(Paths.get("invoice.pdf"))
        .catalog("schet-faktura@1")
        .metadata("order_id", "12345")      // вернётся с прогоном и в вебхуке
        .build());

CatalogEntry entry = client.catalog().get("schet-faktura");
System.out.println(entry.title() + ": " + entry.fieldsPreview());
for (CatalogEntryBrief e : client.catalog().list().autoPager()) {
    System.out.println(e.ref());
}
```

## Типизатор

Схему, которую вы используете постоянно, сохраните как типизатор и запускайте по `id`. Результат
можно сразу получить в XML для 1С, CSV или XLSX:

```java
TypizerClient client = TypizerClient.fromEnv();
String schema = new String(Files.readAllBytes(Paths.get("invoice.schema.json")), StandardCharsets.UTF_8);

Typizer invoices = client.typizers().create("Счета поставщиков", schema);

Run run = client.extract(ExtractParams.builder()
        .file(Paths.get("invoice.pdf"))
        .typizer(invoices)
        .build());

try (Download xml = client.runs().output(run.id(), Format.XML)) {
    xml.saveTo(Paths.get("invoice.xml"));
}

for (Run r : client.runs().list(RunListParams.builder().typizerId(invoices.id()).build()).autoPager()) {
    System.out.println(r.id() + " " + r.status());
}
```

`list(…)` возвращает страницу (`data()`, `hasMore()`, `nextCursor()`, `nextPage()`), а
`autoPager()` (или `stream()`) лениво проходит все страницы.

Создать прогон, не дожидаясь результата, — `client.runs().create(params)` (вернётся `queued`), а
дождаться позже — `client.runs().waitFor(runId)`: он возвращает прогон в любом конечном статусе и
не бросает исключение для `failed`.

## Spring Boot

Один клиент на приложение — бин; ключ и адрес — из настроек:

```java spring
@Configuration
public class TypizerConfig {
    @Bean(destroyMethod = "close")
    public TypizerClient typizerClient(@Value("${typizer.api-key}") String apiKey) {
        return TypizerClient.builder()
                .apiKey(apiKey)
                .appInfo("acme-erp", "2.1.0")         // попадёт в User-Agent — так проще разбираться в поддержке
                .timeout(Duration.ofSeconds(75))
                .build();
    }
}

@Service
public class InvoiceRecognizer {
    private final TypizerClient typizer;

    public InvoiceRecognizer(TypizerClient typizer) {
        this.typizer = typizer;
    }

    public Map<String, Object> recognize(MultipartFile upload) throws IOException {
        Run run = typizer.extract(ExtractParams.builder()
                .file(upload.getBytes(), upload.getOriginalFilename())
                .catalog("schet-faktura@1")
                .build());
        return run.output();
    }
}
```

```properties
typizer.api-key=${TYPIZER_API_KEY}
```

Клиент потокобезопасен и переиспользует соединения. Асинхронно — `client.async().extract(params)`
возвращает `CompletableFuture<Run>`; `future.cancel(true)` прерывает и HTTP-запрос, и ожидание
прогона (сам прогон на сервере продолжится).

## Ошибки

Всё, что бросает SDK, — непроверяемые исключения от `TypizerException`. Ошибка API —
`ApiException` (или подкласс) с полями из ответа `application/problem+json`:
- `code()` — машиночитаемый код (`invalid_schema`, `insufficient_balance`, `rate_limited`, …);
- `statusCode()`, `detail()`, `param()` — поле запроса, если виновато оно;
- `requestId()` — приложите его к обращению в поддержку (support@typizer.ru);
- `docUrl()` — страница кода: `https://docs.typizer.ru/errors/<code>`;
- `isRetryable()`, `retryAfter()`, `rateLimit()`.

Текст исключения: `[insufficient_balance] Not enough pages on the balance: 1 needed, 0 available
(HTTP 402, request_id=req_…, https://docs.typizer.ru/errors/insufficient_balance)`.

| Исключение                                               | Когда                                                                     |
| -------------------------------------------------------- | ------------------------------------------------------------------------- |
| `BadRequestException`                                    | 400: неверный запрос, id или схема (`invalid_schema`, `too_many_pages`…)  |
| `AuthenticationException`                                | 401: ключа нет, он неверен или отозван                                    |
| `InsufficientBalanceException`                           | 402: не хватает страниц на балансе                                        |
| `PermissionDeniedException`                              | 403: у ключа нет скоупа (`insufficient_scope`)                            |
| `NotFoundException`                                      | 404                                                                       |
| `ConflictException`, `IdempotencyConflictException`      | 409; второе — тот же `Idempotency-Key` с другим запросом                  |
| `FileTooLargeException`                                  | 413: файл больше 50 МиБ                                                   |
| `UnsupportedFileTypeException`                           | 415                                                                       |
| `UnprocessableDocumentException`                         | 422: документ не читается                                                 |
| `RateLimitException`                                     | 429 после всех повторов                                                   |
| `InternalServerException`, `ServiceUnavailableException` | 5xx после всех повторов                                                   |
| `ConnectionException`, `TimeoutException`                | ответа нет: DNS, соединение, TLS, таймаут попытки                         |
| `RunFailedException`                                     | `extract()`: прогон завершился `failed` (`code()`, `run()`)               |
| `WaitTimeoutException`                                   | `extract()`/`waitFor()` не дождались; прогон продолжается и будет оплачен |
| `WebhookVerificationException`                           | `Webhooks.verify()`: подпись или время не прошли (`reason()`)             |

Ошибки в аргументах — нет схемы, два источника схемы сразу — `IllegalArgumentException` до
отправки запроса: это ошибка в коде, а не сбой сервиса.

```java
TypizerClient client = TypizerClient.fromEnv();
try {
    Run run = client.extract(ExtractParams.builder()
            .url("https://typizer.ru/samples/invoice.pdf")
            .catalog("schet-faktura@1")
            .build());
    System.out.println(run.output());
} catch (InsufficientBalanceException e) {
    System.out.println("Пополните баланс в кабинете");
} catch (RunFailedException e) {
    System.out.println("Документ не распознан: " + e.code());   // document_unreadable, too_many_pages…
} catch (WaitTimeoutException e) {
    Run run = client.runs().waitFor(e.runId());                  // прогон идёт — дождаться ещё
    System.out.println(run.status());
} catch (ApiException e) {
    System.out.println(e.code() + ": " + e.detail() + " (" + e.docUrl() + ", request_id " + e.requestId() + ")");
} catch (ConnectionException e) {
    System.out.println("Сеть: " + e.getMessage());
}
```

## Повторы и идемпотентность

SDK сам повторяет запрос (по умолчанию до 2 раз, `builder().maxRetries(…)`) при сетевой ошибке,
таймауте попытки, `408`, `429` и `5xx`: выжидает `Retry-After` (если тот не длиннее минуты), иначе
экспоненциальную паузу 0,5 → 1 → 2 → 4 → 8 с с джиттером. Остальные коды не исчезнут от повтора:
сначала исправьте причину.

Каждый `POST` уходит с заголовком `Idempotency-Key` — случайным UUID на вызов, одним и тем же во
всех повторах этого вызова, поэтому повтор после обрыва связи не создаст второй прогон и не спишет
страницы дважды. Свой ключ — для повторов между процессами: тот же счёт из той же очереди,
доставленный дважды, даст один прогон:

```java
TypizerClient client = TypizerClient.fromEnv();
String invoiceId = "INV-2026-000123";

Run run = client.extract(
        ExtractParams.builder().url("https://typizer.ru/samples/invoice.pdf").catalog("schet-faktura@1").build(),
        RequestOptions.builder().idempotencyKey("invoice-" + invoiceId).build());

TypizerResponse<Run> raw = client.withRawResponse(c -> c.runs().get(run.id()));
System.out.println(raw.requestId() + " " + raw.idempotentReplayed());
```

Тот же ключ с другим запросом — `IdempotencyConflictException`. Повтор создания конечной точки
вебхука с тем же ключом вернёт её с `secret: null` — секрет показывается только в первом ответе.

Таймаут — на одну HTTP-попытку: 75 с по умолчанию (`builder().timeout(…)`), 10 с на соединение
(`connectTimeout`). Для создания прогона с ожиданием на сервере SDK сам поднимает его до
`wait + 15 с`. Настройки отдельного вызова — `RequestOptions` (`timeout`, `maxRetries`,
`idempotencyKey`, `header`); копия клиента с другими настройками — `client.withTimeout(…)`,
`client.withMaxRetries(…)` (соединения общие).

## Вебхуки

Вместо ожидания результат можно получать вебхуком: укажите `webhookUrl(…)` в параметрах прогона
или создайте конечную точку:

```java
TypizerClient client = TypizerClient.fromEnv();
WebhookEndpoint endpoint = client.webhookEndpoints().create(
        "https://erp.example.ru/typizer/webhook", WebhookEventType.RUN_SUCCEEDED, WebhookEventType.RUN_FAILED);
System.out.println(endpoint.secret());   // whsec_… — ключ проверки подписи; храните как секрет
client.webhookEndpoints().test(endpoint.id());
```

Доставки подписаны по [Standard Webhooks](https://www.standardwebhooks.com) (HMAC-SHA256).
`Webhooks.verify(тело, заголовки, секрет)` проверяет подпись и время (±5 минут) и возвращает
событие; при неверной подписи — `WebhookVerificationException`. Ключ API для этого не нужен.
Тело передавайте **ровно в тех байтах, в которых оно пришло** — в Spring это `@RequestBody String`
или `byte[]`, а не объект, который Jackson уже разобрал и собрал заново:

```java spring
@RestController
public class TypizerWebhookController {
    @Value("${typizer.webhook-secret}")
    private String secret;

    @PostMapping("/typizer/webhook")
    public ResponseEntity<Void> receive(@RequestBody String body, @RequestHeader HttpHeaders headers) {
        WebhookEvent event;
        try {
            event = Webhooks.verify(body, headers::getFirst, secret);
        } catch (WebhookVerificationException e) {
            return ResponseEntity.badRequest().build();
        }
        if (event.type() == WebhookEventType.RUN_SUCCEEDED) {
            Run run = event.run();
            // run.metadata().get("order_id"), run.output(), run.needsReview() …
        }
        return ResponseEntity.ok().build();
    }
}
```

Заголовки можно передать функцией (`request::getHeader` сервлета, `headers::getFirst` Spring) или
`Map` (имена без учёта регистра). Вебхук может прийти повторно с тем же `event.id()` (он же
`webhook-id`) — используйте его как ключ дедупликации. Подробнее — в
[«Приёме вебхуков»](https://docs.typizer.ru/how-to/webhooks/).

## Прокси и свой HTTP-клиент

По умолчанию запросы идут через `HttpURLConnection` из JDK и используют прокси JVM
(`-Dhttps.proxyHost=… -Dhttps.proxyPort=…` или `-Djava.net.useSystemProxies=true`); переменную
окружения `HTTPS_PROXY` JDK сам не читает. Явный прокси, в том числе с логином:

```java
TypizerClient client = TypizerClient.builder()
        .apiKey(System.getenv("TYPIZER_API_KEY"))
        .proxy("http://user:password@proxy.corp.local:3128")
        .build();
```

> Логин прокси уходит только самому прокси и никогда — серверу API. Для HTTPS SDK передаёт его
> через аутентификатор соединения, а он есть в JDK с Java 9. На Java 8 логин прокси для HTTPS
> SDK не отправит и сообщит об этом ошибкой: задайте `Authenticator.setDefault(…)` и запустите
> JVM с `-Djdk.http.auth.tunneling.disabledSchemes=""` (без логина в `.proxy(…)`) или подключите
> свой транспорт (ниже).

Корпоративный корневой сертификат (прокси с инспекцией TLS), клиентский сертификат или **ГОСТ-TLS**
— через `SSLContext`. Проверку сертификатов SDK отключить не даёт — и не нужно: добавьте свой
корневой сертификат в trust store. С КриптоПро JCP (провайдеры JCP и JTLS установлены в JVM):

```java compile
SSLContext gost = SSLContext.getInstance("GostTLS");   // протокол провайдера КриптоПро JTLS
gost.init(null, null, null);                           // ключи и доверенные сертификаты — из хранилищ JCP
TypizerClient client = TypizerClient.builder()
        .apiKey(System.getenv("TYPIZER_API_KEY"))
        .baseUrl("https://api.typizer.ru")
        .sslContext(gost)
        .build();
```

Свой HTTP-клиент (OkHttp, Apache HttpClient, JAX-RS, транспорт вашей шины) — реализуйте
`ru.typizer.http.HttpTransport` (один метод `execute`) и передайте в `builder().httpClient(…)`.
Повторы, ключи идемпотентности, таймауты попыток и классы ошибок остаются за SDK; прокси и TLS —
за вашим клиентом. Клиент, переданный вами, SDK не закрывает.

## Логи

SDK пишет в `java.util.logging`, логгер `ru.typizer`, и по умолчанию молчит (только `WARNING` —
объявленные технические работы, один раз на клиент). На уровне `FINE` — строка на каждую
HTTP-попытку: метод, путь, статус, `request_id`, номер попытки и длительность; на `INFO` — каждый
повтор с причиной и паузой. Ни ключа, ни тел запросов и ответов, ни имён файлов, ни `metadata` в
логах нет — в документах персональные данные (152-ФЗ).

Включить: `Logger.getLogger("ru.typizer").setLevel(Level.FINE)` (или через ваш мост JUL → SLF4J),
переменная окружения `TYPIZER_LOG=debug|info|warn` (печать в stderr) или свой приёмник:

```java
TypizerClient client = TypizerClient.builder()
        .apiKey(System.getenv("TYPIZER_API_KEY"))
        .logger((level, message) -> System.err.println(level + " " + message))  // или SLF4J: log.info(message)
        .build();
client.balance();
```

## Kotlin

```kotlin
val client = TypizerClient.fromEnv()
val run = client.extract(Paths.get("invoice.pdf"), File("invoice.schema.json").readText())
println(run.output()?.get("invoice_number"))
```

## Документация

- [Документация Typizer](https://docs.typizer.ru)
- [Справочник API](https://docs.typizer.ru/reference/api/) — REST-операция за каждым методом SDK
- [Коды ошибок](https://docs.typizer.ru/reference/errors/)
- [Первый вызов API](https://docs.typizer.ru/tutorials/first-api-call/)

Вопросы и проблемы — support@typizer.ru.

## Лицензия

MIT — см. файл [LICENSE](LICENSE).
