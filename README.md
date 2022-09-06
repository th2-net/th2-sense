# th2-sense

The component that computes statistic for messages and events using user-defined rules

## Usage

The th2-sense allows partitioning events and messages according to custom rule created by users.

To use th2-sense you need:

1. Create Kotlin project and add th2-sense-dsl into dependencies.
    <details>
      <summary>Here is a template for gradle.build file</summary>

    ```groovy
    plugins {
        id 'org.jetbrains.kotlin.jvm' version '1.6.21'
        id 'application'
        id 'com.palantir.docker' version '0.25.0'
        id 'org.jetbrains.kotlin.kapt' version '1.6.21'
    }
   
    ext {
       sense_version = '0.0.1'
    }
    
    dependencies {
        implementation "com.exactpro.th2:sense-dsl:${sense_version}"
        runtimeOnly "com.exactpro.th2:sense-app:${sense_version}"
    
        compileOnly 'com.google.auto.service:auto-service-annotations:1.0.1'
        kapt 'com.google.auto.service:auto-service:1.0.1'
    }
    
    applicationName = 'service'
    
    distTar {
        archiveFileName.set("${applicationName}.tar")
    }
    
    dockerPrepare {
        dependsOn distTar
    }
    
    docker {
        copySpec.from(tarTree("$buildDir/distributions/${applicationName}.tar"))
    }
    
    application {
        mainClass.set('com.exactpro.th2.sense.app.bootstrap.Main')
    }
    ```
   </details>
2. Create your rule implementations
   1. If you need a rule without configuration you should extend `com.exactpro.th2.sense.event.rule.SimpleEventRuleFactory`
   2. If you need a rule with configuration you should extent `com.exactpro.th2.sense.event.rule.ConfigurableEventRuleFactory`
      and create configuration class that extends `com.exactpro.th2.sense.api.ProcessorSettings`
   3. Specify your rule name (must be unique) and setup rule in the `setup` method.
   <details>
     <summary>Simple rule example</summary>
    
    ```kotlin
    @AutoService(EventProcessorFactory::class)
    class YourRuleFactory : SimpleEventRuleFactory("rule name") {
       override fun EventRuleBuilder.setup() {
         // rule setup
       }
    }
    ```
   </details>

   <details>
     <summary>Configurable rule example</summary>

    ```kotlin
    class YourConfiguration(val param: Int) : ProcessorSettings()

    @AutoService(EventProcessorFactory::class)
    class YourRuleFactory : ConfigurableEventRuleFactory<YourConfiguration>(
      "rule name",
      YourConfiguration::class.java,
    ) {
       override fun EventRuleBuilder.setup(settings: YourConfiguration) {
         // rule setup. The configuration is available
       }
    }
    ```
   </details>

## Rule definition DSL

Rules allows you to associate certain events with custom type and collect statistic for those types.
You can define them using DSL supplied along with `sense-app`.

### Events

There is a `EventRuleBuilder.sutup` method inside your rule factory.
Here you define the rules to associate event with certain type.
**_Each rule might contain matching for more than one event type._**

Each event has the following fields available:
+ name - event name
+ type - event type
+ startTimestamp - the timestamp of event start
+ endTimestamp - the timestamp of event end
+ parentEvent - the parent event of the current event (only if it has one)
+ rootEvent - the root event for the current event (only if it is not root itself)

The base structure is:
```
override fun EventRuleBuilder.setup() {
  eventType("<user type>") whenever <allOf|anyOf|noneOf> {
    <event field> <operation> <value>
    or
    <event field> <allOf|anyOf|noneOf> {
      <operation>(<value>)
      [<operation>(<value>)]
    }
    or
    <parentEvent|rootEvent> <allOf|anyOf|noneOf> {
       <event field> <operation> <value>
       or
       <event field> <allOf|anyOf|noneOf> {
         <operation>(<value>)
         [<operation>(<value>)]
       }
    }
  }
}
```

**NOTES:**

1. `allOf` methods accepts the value only if **all** conditions are passed
2. `anyOf` methods accepts the value if **any** condition is passed
3. `noneOf` methods accepts the value if **none** of the conditions is passed

#### Examples:

Event has name that starts with "Test"

```kotlin
override fun EventRuleBuilder.setup() {
  eventType("your type") whenever allOf {
    name startsWith "Test"
  }
}
```

Event has name that starts with "Test" and contains "Execution"
```kotlin
override fun EventRuleBuilder.setup() {
  eventType("your type") whenever allOf {
    name allOf {
      startsWith("Test")
      contains("Execution")
    }
  }
}
```

Event has a parent with type "ParentType"
```kotlin
override fun EventRuleBuilder.setup() {
  eventType("your type") whenever allOf {
    parentEvent allOf {
      type equal "ParentType"
    }
  }
}
```