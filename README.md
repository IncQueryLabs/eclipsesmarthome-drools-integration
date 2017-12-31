# eclipsesmarthome-drools-integration
This project integrates the [Drools Business Rules Engine](https://www.drools.org/) to [Eclipse SmartHome](https://www.eclipse.org/smarthome/). With the help of this integration, Drools rules can be used to create smart home automation rules. This integration contains an API to make it easier to create automation rules in Drools. The goal is to modify this project to match the [Eclipse SmartHome coding guide lines](https://www.eclipse.org/smarthome/documentation/development/guidelines.html).

This project is actually a sub project of a Smart home Complex Event Processing demonstrator, which creates smart home automation rules in a smart home simulator, HomeIO. Find more information about it in the [Smart home CEP demonstrator repository](https://github.com/IncQueryLabs/smarthome-cep-demonstrator).

## Drools rules
Drools rules are similar to OpenHAB rules. The following rule turns on the light when the motion detector detects motion, but only if the brightness sensor detects darkness.

```
rule "Switch on light for motion in dark"
    when
        $light : Item( name == "Example_Light", state == OFF )
        Item( name == "Example_Brightness_Sensor", state == DARKNESS )

        ItemStateChangedEvent(name == "Example_Motion_Detector", newState == MOTION)
    then
        openhab.postCommand($light, ON);
end
```

In the when section, you can specify the triggers and filters. Triggers are events, such as ItemStateChangedEvent or ItemCommandEvent. Filters can be a specified state for an item. Full documentation about the Drools rule langue can be found in the [Drools documentation, chapter 8](https://docs.jboss.org/drools/release/7.2.0.Final/drools-docs/html_single/index.html#_droolslanguagereferencechapter).

## The integration to Eclipse SmartHome


### Extended event bus
Drools rule engine operates on objects. However, the Eclipse SmartHome event bus uses only the string name of the item to communicate on the event bus. To solve this, an extended event bus is created, with the same events as in Eclipse SmartHome, but they use the item object instead of the name of the item. More can be read about it in the [wiki](https://github.com/IncQueryLabs/eclipsesmarthome-drools-integration/wiki/Extended-event-bus).