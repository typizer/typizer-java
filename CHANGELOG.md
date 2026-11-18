# Changelog

Все заметные изменения этого пакета. Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версии — [SemVer](https://semver.org/lang/ru/).

## [0.3.0] — 2026-11-18

Совместимо с 0.2.0: прежние вызовы работают без изменений.

### Добавлено

- Проверки полей: `client.validators()` — свои проверки `my/<slug>`: `list()` (с `autoPager()`),
  `get(id или "my/slug")`, `create`, `update` (`ValidatorUpdateResult` с `bump()`), `archive`,
  `versions`, `test(values, ref)` / `test(ValidatorTestParams)` и `suggest(ValidatorSuggestParams)`;
  параметры `ValidatorParams`, `ValidatorTestParams`, `ValidatorSuggestParams`; модели `Validator`,
  `ValidatorDetail`, `ValidatorVersion`, `ValidatorTest`, `ValidatorUsedBy`, `ValidatorUpdateResult`,
  `ValidatorTestResult`, `ValidatorSuggestion`, открытые перечисления `ValidatorBump` и `CheckStatus`.
  `spec` — свободный `Map` (принимается также JSON-текстом или деревом Jackson/Gson).
- `FieldResult.message()` и `FieldResult.checks()` (`FieldCheck`), `Run.warnings()` (`FieldWarning`:
  путь, сообщение, причина), `Run.checksUsed()` (`RunCheckUsed`), `Typizer.checks()` и
  `TypizerVersion.checks()` (`TypizerCheck`), `Me.Features.validatorsSuggest()`. Прогоны и вебхуки
  без этих полей разбираются как раньше.
- Каталог проверок: `client.catalog().validators()` — `list`, `get`, `versions`, `favorite`,
  `unfavorite`, `report` (причина `false_positive`) и `fork` (своя копия `my/<slug>`, ответ —
  `Validator`). У записей каталога — `type()` (`CatalogEntryBrief.TYPE_SCHEMA`/`TYPE_VALIDATOR`),
  `rulePreview()` и `validator()` (`CatalogValidatorBrief`), у версии — `validator()`
  (`CatalogValidatorVersion`: правило, опубликованные примеры, объяснение).
- `FieldCheck.corrected()` — значение прочитано повторно после проваленной проверки («Перечитать при
  ошибке», режим «Точный»).

## [0.2.0] — 2026-11-16

Совместимо с 0.1.0: прежние вызовы работают без изменений.

### Добавлено

- Причина `code_mismatch` у `FieldResult.reason()`: значение поля расходится со штрихкодом или QR-кодом
  на документе. Сервис сам читает коды (платёжный QR по ГОСТ Р 56042-2014, QR чека, «Честный знак»), чтобы точнее
  заполнять поля вашей схемы; ответ — по-прежнему только поля схемы, отдельного списка кодов в прогоне
  нет.

## [0.1.0] — 2026-11-13

Первый выпуск.

### Добавлено

- `TypizerClient` (builder, `fromEnv()`) со всеми 49 операциями публичного API: `runs()`, `files()`,
  `typizers()` (версии, обновления из каталога), `catalog()`, `batches()`, `webhookEndpoints()`
  (доставки), `me()`, `balance()`, `usage()`, `organization()`, `status()`.
- `client.extract(документ, схема)`: файл в том же запросе, ожидание на сервере и опросом,
  `RunFailedException` и `WaitTimeoutException`.
- Модели с открытыми перечислениями и доступом к новым полям API (`extra()`, `get(…)`);
  `run.needsReview()`, `run.fieldsToReview()`, `run.outputAs(Class)` через Jackson или Gson приложения.
- Повторы (сеть, 408, 429, 5xx; `Retry-After`, экспонента с джиттером), `Idempotency-Key` на каждом
  `POST`, таймаут на попытку, `client.async()` с отменой через `CompletableFuture.cancel`.
- `Webhooks.verify` / `isValid` (Standard Webhooks).
- Транспорт по умолчанию на `HttpURLConnection` без зависимостей; прокси (в том числе с логином и
  SOCKS), свой `SSLContext` (корпоративный CA, ГОСТ-TLS), свой `HttpTransport`.
- Логи через `java.util.logging` (`ru.typizer`) или свой `TypizerLogger`; ключ и данные документов
  в логи не попадают.
