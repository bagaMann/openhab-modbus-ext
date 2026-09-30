# Решение по динамическим каналам

## Результат проверки openHAB 5.2

Для нашей модели не требуется изобретать собственный механизм добавления каналов.

openHAB Thing description поддерживает атрибут `extensible`. Официальный HTTP binding использует его для пользовательских каналов:

```xml
<thing-type id="url"
    extensible="color,contact,datetime,dimmer,image,location,number,player,rollershutter,string,switch">
```

Каждый extensible channel имеет собственный `channel-type` и собственный `config-description`. Runtime получает конфигурацию непосредственно через:

```java
channel.getConfiguration().as(ChannelConfig.class)
```

Это именно та модель, которая нужна Modbus Ext.

## Принятое решение

Poller будет extensible Thing/Bridge с типами каналов:

- number
- switch
- contact
- string
- dimmer
- rollershutter
- datetime

На первом этапе сохраняем набор item representations стандартного Modbus Data Thing. Позже можно добавить Number с dimension/UoM без изменения Modbus transport layer.

Каждый channel type будет ссылаться на общую Modbus channel configuration:

```
readStart
readValueType
readTransform
writeStart
writeValueType
writeType
writeTransform
updateUnchangedValuesEveryMillis
```

Поля, относящиеся только к конкретному item type, при необходимости получат специализированную config-description.

## Почему это лучше программного ChannelBuilder

Пользователь сам добавляет/удаляет канал через MainUI. Канал становится частью Thing configuration и переживает restart. Handler не должен заново угадывать или генерировать пользовательские точки при каждом initialize.

Это также делает Code view естественным: Poller и его channels могут быть представлены в YAML стандартным способом openHAB.

## Runtime

При initialize Poller:

1. Валидирует собственный read range.
2. Перебирает `thing.getChannels()`.
3. Преобразует `Channel.getConfiguration()` в `ModbusChannelConfig`.
4. Создаёт лёгкий `ModbusChannelRuntime` для каждого channel.
5. Регистрирует только один regular poll task.

После read callback:

```
AsyncModbusReadResult
        |
        +--> ChannelRuntime A
        +--> ChannelRuntime B
        +--> ChannelRuntime C
```

Все runtimes получают один и тот же read result/cache.

## Отдельный binding

Новый bundle не должен зависеть от Java-классов `org.openhab.binding.modbus.internal.*`.

Он будет зависеть от публичного Core Modbus transport API:

```
org.openhab.core.io.transport.modbus
```

и иметь собственные endpoint/poller handlers.

Причина: endpoint и Data handlers официального binding находятся в `.internal`; связывать отдельный OSGi bundle с внутренней реализацией другого binding ненадёжно. Кроме того, нам всё равно требуется изменить жизненный цикл Poller с child Data Things на channel runtimes.

## Идентификаторы

Binding ID:

```
modbusext
```

Bundle:

```
org.openhab.binding.modbusext
```

Thing types:

```
modbusext:tcp
modbusext:serial
modbusext:poller
```

Отдельного `data` Thing намеренно нет.

## Следующий этап

Создать собираемый Maven/OSGi skeleton и metadata:

- binding.xml
- thing-types.xml
- config descriptions
- ModbusExtHandlerFactory
- TCP/Serial endpoint configuration
- Poller configuration
- ModbusChannelConfig

После этого переносить runtime поведение по тестам стандартного Modbus binding.
