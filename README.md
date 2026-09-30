# openHAB Modbus Ext

Экспериментальный Modbus binding для openHAB с моделью **Endpoint → Poller → Channels**.

Главная идея проекта — сохранить привычную семантику стандартного Modbus binding, но убрать необходимость создавать отдельный Data Thing для каждой Modbus-точки. Один Poller читает диапазон данных, а пользовательские Channels внутри него описывают отдельные значения.

## Состояние проекта

Версия **0.2.0** предназначена для эксплуатационного тестирования. Основная архитектура, чтение/запись, dynamic channels и импульсное управление уже работают и проверяются на реальной установке openHAB.

Для стабильной импульсной схемы зафиксирован baseline tag:

`v0.2.0-pulse-feedback-baseline`

## Архитектура

```text
Endpoint (TCP / Serial)
└── Poller
    ├── Channel
    ├── Channel
    └── Channel
```

Endpoint отвечает за Modbus transport и соединение. Poller выполняет один циклический запрос диапазона. Channels извлекают из результата отдельные значения и при необходимости выполняют запись.

## Реализовано

- Modbus TCP и Serial endpoint;
- Poller для Discrete Inputs, Coils, Input Registers и Holding Registers;
- dynamic channels без отдельного Data Thing для каждой точки;
- типы Channel: Number, Switch, Contact, String, Dimmer, Rollershutter и DateTime;
- чтение и запись holding registers и coils;
- `readTransform` и `writeTransform`;
- адресация `BIT` и 8-bit значений через `X.Y`;
- безопасный read-modify-write для отдельного бита/байта holding register;
- `updateUnchangedValuesEveryMillis`;
- режимы записи `direct` и `pulse` для бинарных holding-register Channels;
- настраиваемая длительность импульса;
- feedback-driven pulse logic: импульс формируется только когда требуемое логическое состояние отличается от фактического feedback;
- очередь следующей команды во время активного импульса;
- Pulse Guard: физическая проверка возврата импульсного выхода в OFF и повторный принудительный OFF при необходимости;
- generation guard, исключающий влияние устаревших асинхронных проверок предыдущего импульса;
- автоматический `AutoUpdatePolicy.VETO` для pulse Channels: состояние Item определяется реальным feedback, а не optimistic autoupdate openHAB.

## ValueType

Для register Channels поддерживаются используемые openHAB Modbus transport типы:

`bit`, `int8`, `uint8`, `int16`, `uint16`, `int32`, `uint32`, `float32`, `int32_swap`, `uint32_swap`, `float32_swap`, `int64`, `uint64`, `int64_swap`, `uint64_swap`.

## Pulse mode

Для бинарного Holding Register Channel доступны два режима записи:

- **Direct** — обычная запись требуемого значения;
- **Pulse** — кратковременная активация физического выхода, если requested state отличается от feedback.

В Pulse mode `Pulse Duration` задаёт длительность активного импульса. После завершения binding контролирует физическое состояние выходного бита. Состояние связанного Item при этом продолжает обновляться Poller-ом по `Read Start`, поэтому внешнее управление (например, обычным настенным выключателем) остаётся видимым в openHAB.

## Конфигурация Channel

Набор параметров намеренно оставлен компактным. В зависимости от типа Channel используются:

- `Read Start`;
- `Read Value Type`;
- `Read Transform`;
- `Write Start`;
- `Write Value Type`;
- `Write Transform`;
- `Write Mode` (`Direct` / `Pulse`);
- `Pulse Duration`;
- `Update Unchanged Values`.

Редко изменяемые параметры находятся в Advanced.

## Сборка

```bash
mvn clean test
mvn package
```

Результирующий bundle создаётся в `target/`.

## Документация

Дополнительные материалы находятся в каталоге `docs/`:

- `architecture.md` — архитектура;
- `compatibility.md` — контракт совместимости со стандартным Modbus binding;
- `dynamic-channels.md` — модель dynamic channels;
- `source-analysis.md` — анализ исходной реализации openHAB Modbus.

## Статус 0.2.0

На этапе 0.2.0 функциональность намеренно не расширяется новыми защитными режимами и дополнительными параметрами Channel. Текущая реализация Pulse Guard, feedback и AutoUpdate зафиксирована и оставляется на длительное тестирование перед дальнейшим развитием проекта.
