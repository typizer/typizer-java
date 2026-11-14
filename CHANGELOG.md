# Changelog

Все заметные изменения этого пакета. Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версии — [SemVer](https://semver.org/lang/ru/).

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
