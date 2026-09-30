# Совместимость со стандартным Modbus Data Thing

Этот документ является контрактом проекта. Функция не считается реализованной, пока для неё нет эквивалентного теста.

## Чтение

Для register poller должны поддерживаться:

| ValueType | Адрес | Размер |
|---|---|---:|
| bit | X.Y, Y=0..15 | 1 bit |
| int8 / uint8 | X.Y, Y=0..1 | 8 bit |
| int16 / uint16 | X | 16 bit |
| int32 / uint32 | X | 32 bit |
| int32_swap / uint32_swap | X | 32 bit |
| float32 / float32_swap | X | 32 bit |
| int64 / uint64 | X | 64 bit |
| int64_swap / uint64_swap | X | 64 bit |

Для coil/discrete poller допустим только BIT и целочисленный адрес элемента.

Значение целиком должно находиться внутри диапазона Poller. Например, 32-bit значение требует двух последовательных 16-bit регистров, 64-bit — четырёх.

## Семантика X.Y

Для BIT в register poller `X.Y` означает бит Y регистра X, Y=0..15.

Для INT8/UINT8 `X.0` и `X.1` выбирают соответствующий 8-bit элемент регистра согласно семантике ModbusBitUtilities стандартного openHAB binding. Мы не вводим собственную трактовку порядка байтов.

## Transformations

Должны поддерживаться `readTransform` и `writeTransform` с той же цепочкой transformation services, что использует стандартный binding.

Identity/default transformation должна сохранять специальное преобразование boolean-подобных каналов (ON/OFF, OPEN/CLOSED).

## Типы openHAB Channel

Минимальный parity-набор:

- Number
- Switch
- Contact
- String
- Dimmer
- Rollershutter
- DateTime

В новой модели один Modbus point соответствует одному пользовательскому Channel выбранного типа вместо семи фиксированных представлений Data Thing.

## Запись

Сохраняются:

- writeType=coil;
- writeType=holding;
- writeValueType;
- writeStart;
- writeTransform;
- writeMaxTries;
- writeMultipleEvenWithSingleRegisterOrCoil;
- single/multiple write function behavior штатного transport;
- запись `BIT X.Y` через read-modify-write кэшированного holding register.

## Обновление состояния

Сохраняется логика `updateUnchangedValuesEveryMillis`: неизменившееся значение не должно без необходимости обновлять Item, но периодическое обновление должно оставаться возможным.

## Диагностика

Стандартный Data Thing имеет lastReadSuccess, lastReadError, lastWriteSuccess, lastWriteError. В новой архитектуре нужно сохранить эквивалентную диагностическую информацию, но окончательное место её представления (служебные каналы Poller или per-channel metadata/status) будет определено после прототипа dynamic channels.

## Источник истины

При расхождении документации и поведения стандартного binding ориентируемся на актуальные integration tests `org.openhab.binding.modbus.tests` и реализацию `ModbusDataThingHandler`.
