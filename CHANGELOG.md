# Changelog

Все заметные изменения проекта фиксируются в этом файле.

## [0.2.0] — release candidate / field testing

### Добавлено

- архитектура Endpoint → Poller → Channels;
- dynamic channels внутри Poller;
- чтение Discrete Inputs, Coils, Input Registers и Holding Registers;
- запись Coils и Holding Registers;
- поддержка register ValueType и адресации `X.Y`;
- `readTransform` и `writeTransform`;
- read-modify-write для отдельных битов и 8-bit значений holding register;
- режимы записи `direct` и `pulse` для бинарных holding Channels;
- настраиваемый `pulseDurationMillis`;
- feedback-driven импульсное управление;
- обработка pending-команды во время активного импульса;
- Pulse Guard с проверкой физического возврата выхода в OFF;
- повторные попытки принудительного OFF при ошибке/неподтверждённом сбросе;
- per-channel generation guard для защиты от устаревших асинхронных Pulse Guard операций;
- автоматический `AutoUpdatePolicy.VETO` для pulse Channels;
- тесты PulseWriteController, PulseOutputGuard, PulseGuardGeneration, ChannelUpdateTracker, HoldingRegisterRmw, transformations и конфигурации Channel.

### Проверено

- последовательные ON/OFF команды;
- быстрые переключения одного Item;
- быстрые переключения между разными Items;
- pending pulse при новой команде до завершения предыдущего импульса;
- независимость generation между Channels;
- возврат физического pulse output в OFF;
- обновление Item по реальному feedback при `AutoUpdate=false/VETO`;
- изменение feedback извне, включая управление обычным выключателем.

### Зафиксированный baseline

Стабильная реализация Pulse Guard + feedback + AutoUpdate до релизной документации отмечена тегом:

`v0.2.0-pulse-feedback-baseline`

Baseline указывает на commit `00edeac3465b840e7959a7796d9181dd45beba9d`.

### Решения для 0.2.0

- Safety Scan не добавляется;
- дополнительные параметры Pulse Guard в UI не добавляются;
- текущие значения внутренних guard/retry задержек остаются реализационными деталями;
- рабочая Java-логика после полевых тестов не изменяется перед релизом без обнаруженной ошибки;
- версия остаётся на эксплуатационном тестировании перед дальнейшим расширением функциональности.
