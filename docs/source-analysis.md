# Разбор стандартного openHAB Modbus binding

Анализ выполнен по актуальной ветке `main` openHAB/openhab-addons.

## ModbusPollerThingHandler

Poller уже является правильной точкой агрегации.

Он:

- получает ModbusCommunicationInterface от endpoint;
- строит один ModbusReadRequestBlueprint;
- регистрирует PollTask через registerRegularPoll;
- принимает AsyncModbusReadResult / AsyncModbusFailure;
- кэширует последний PollResult согласно cacheMillis;
- хранит lastPolledDataCache;
- передаёт результат всем дочерним ModbusDataThingHandler;
- обслуживает REFRESH с возможностью повторного использования свежего cache.

Для новой архитектуры fan-out на child Data Things заменяется fan-out на channel runtimes внутри Poller.

## ModbusDataThingHandler

Класс совмещает несколько разных обязанностей:

1. parsing/validation конфигурации;
2. вычисление смещения относительно Poller;
3. extraction значения из ModbusRegisterArray/BitArray;
4. transformation;
5. конвертацию в State подходящего Item type;
6. suppression неизменившихся updates;
7. command handling;
8. transformation команды;
9. построение write request;
10. read-modify-write отдельного бита holding register;
11. read/write diagnostics;
12. lifecycle/status.

Это главный кандидат на декомпозицию.

## Критичная формула extraction

Для ValueType >=16 bit индекс extraction равен:

`readIndex - pollStart`

Для типов <16 bit:

`(readIndex - pollStart) * (16 / valueType.bits) + subIndex`

После этого стандартный binding вызывает `ModbusBitUtilities.extractStateFromRegisters(...)`. Эту функцию нужно переиспользовать, а не дублировать endian/swap логику.

## Validation

Границы проверяются в битах. Это позволяет одной логикой проверять BIT, INT8 и multi-register типы.

Для register poller dataElementBits=16, для coils/discretes dataElementBits=1.

Особые ограничения:

- subIndex для BIT регистра: 0..15;
- subIndex для INT8/UINT8: 0..1;
- >=16-bit ValueType не допускает subIndex;
- coils/discretes допускают только BIT;
- всё значение должно помещаться в poll range.

## Запись BIT X.Y

Стандартная реализация использует AtomicReference<ModbusRegisterArray> из Poller и атомарно заменяет кэш результатом combineCommandWithRegisters. Это обеспечивает read-modify-write одного бита без уничтожения остальных 15 бит регистра.

После мутации Poller при refresh сравнивает cached registers с последним successful PollResult и сбрасывает result cache, если массив был изменён записью.

Это поведение является обязательным для parity.

## Тесты

Integration tests стандартного binding уже покрывают значительную часть нужного контракта: границы BIT/INT8/INT16/INT32, read transformations, write handling, function codes и ошибки.

Стратегия проекта: переносить сценарии тестов как black-box compatibility tests, меняя только модель Data Thing на Channel runtime.
