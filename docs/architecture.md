# Архитектура

## Цель

Заменить иерархию стандартного binding:

```
Endpoint (TCP/Serial)
└── Poller
    └── Data Thing
        └── стандартные каналы
```

на:

```
Endpoint (TCP/Serial)
└── Poller
    ├── Channel A
    ├── Channel B
    └── Channel N
```

При этом один результат чтения Poller используется всеми его каналами без дополнительных Modbus-запросов.

## Что переиспользуем из openHAB Core

Транспорт и protocol primitives должны оставаться штатными:

- ModbusCommunicationInterface
- ModbusReadRequestBlueprint
- ModbusWriteRequestBlueprint
- ModbusRegisterArray
- BitArray
- ModbusBitUtilities
- ModbusConstants.ValueType
- PollTask

Собственный Modbus TCP/RTU transport проект не реализует.

## Компоненты

### Endpoint

TCP и Serial endpoint отвечают только за соединение, slave/unit id и получение ModbusCommunicationInterface.

### Poller

Poller задаёт type, start, length, refresh, maxTries и cacheMillis. Он регистрирует один regular poll и передаёт полученный AsyncModbusReadResult всем настроенным каналам.

### Channel runtime

Каждый канал содержит собственную конфигурацию Data-point:

- item/channel type;
- readStart;
- readValueType;
- readTransform;
- writeStart;
- writeType;
- writeValueType;
- writeTransform;
- writeMultipleEvenWithSingleRegisterOrCoil;
- writeMaxTries;
- updateUnchangedValuesEveryMillis.

Runtime канала не выполняет отдельное чтение. Он извлекает своё значение из результата Poller.

## Запись отдельного бита

`writeStart=X.Y` для holding register требует read-modify-write. Это особый случай, который нельзя упрощать.

Алгоритм должен сохранить поведение стандартного binding:

1. использовать последний кэшированный ModbusRegisterArray Poller;
2. изменить только требуемый бит;
3. сформировать запись регистра;
4. инвалидировать/сверить кэш так, чтобы последующий refresh не вернул устаревшее значение.

Если кэш ещё не заполнен, команда записи бита не выполняется.

## REFRESH

REFRESH канала должен вызывать refresh Poller, а не отдельный Modbus request. Необходимо сохранить асинхронный вызов, чтобы не создать deadlock между обработчиком команды и callback чтения.

## Ошибки конфигурации

Ошибочная конфигурация одного канала не должна останавливать корректные каналы Poller. Это намеренное отличие от Data Thing: ошибка локализуется на уровне Channel runtime и должна быть видна в логах/диагностике.

## Этапы

1. Зафиксировать compatibility contract.
2. Реализовать parser/validator адресов и ValueType.
3. Реализовать read codec и unit tests.
4. Реализовать динамические каналы Poller.
5. Реализовать transformations.
6. Реализовать write path.
7. Реализовать bit read-modify-write.
8. TCP integration tests.
9. Serial integration tests.
10. Тест на реальном openHAB.
