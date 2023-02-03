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
        mainClass.set('com.exactpro.th2.processor.MainKt')
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
    class YourRuleFactory : ConfigurableEventRuleFactory<YourConfiguration>("rule name") {
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

## Running in th2

### Configuration

#### source

The source type. Currently supported:
+ mq - from MQ
+ crawler - from Crawler

if source type is crawler then [Processor setting](#processorSettings) and [crawler-processor settings](https://github.com/th2-net/th2-processor-core-j/tree/TH2-4262-reduce-load-book-and-page#configuration-example) are required

#### processorSettings
* name - name of crawler-processor

#### processors

The list of processor with their configurations

Example:

```yaml
processors:
  - id: test
    param1: a
  - id: test2
  - id: test3
    param2: B
    param3: 1
```

#### statistic

The buckets in which events and messages should be collected.

**eventBuckets** - the buckets for events. Default value: [PT1S, PT1M, PT1H]

**messagesBuckets** - the buckets for messages. Default value: [PT1S, PT1M, PT1H]

#### httpConfiguration (optional)

If specified the HTTP server will be started to allow notification submission. 

**host** - the address to start listening

**port** - the port to start listening (if _0_ the random free port will be used)

# CR example:

## Crawler strategy

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: sense
spec:
  image-name: <image-name>
  image-version: 1.0.0
  type: th2-act
  custom-config:
    processors:
      - id: "processor id"
        param: 1
    stateSessionAlias: my-processor-state
    enableStoreState: false

    crawler:
       from: 2021-06-16T12:00:00.00Z
       to: 2021-06-17T14:00:00.00Z

       intervalLength: PT10M
       syncInterval: PT10M
       awaitTimeout: 10
       awaitUnit: SECONDS

       messages:
          messageKinds:
             - MESSAGE
             - RAW_MESSAGES
          bookToGroups:
             book1:
                - group1
                - group2
             book2:
                - group1
                - group2
       events:
          bookToScope:
             book3:
                - scope1
                - scope2
             book4:
                - scope1
                - scope2
  pins:
    gprc:
      client:
        - name: to_data_provider
          service-class: com.exactpro.th2.dataprovider.lw.grpc.DataProviderService
          linkTo:
             - box: lw-data-provider
               pin: server
        - name: to_data_provider_stream
          service-class: com.exactpro.th2.dataprovider.lw.grpc.QueueDataProviderService
          linkTo:
            - box: lw-data-provider
              pin: server
      server:
        - name: sense-server
          connection-type: grpc-server
          service-classes:
            - com.exactpro.th2.sense.grpc.SenseService
            - th2.sense.SenseService
    mq:  
      publishers:
        - name: state
          attributes:
            - store
      
  extended-settings:
    service:
      enabled: true
      type: NodePort
      endpoints:
        - name: 'grpc'
          targetPort: 8080
          nodePort: 30178
    envVariables:
      JAVA_TOOL_OPTIONS: "-XX:+ExitOnOutOfMemoryError"
    resources:
      limits:
        memory: 200Mi
        cpu: 200m
      requests:
        memory: 110Mi
        cpu: 50m
```

## Realtime strategy

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
   name: sense
spec:
  image-name: <image-name>
  image-version: 1.0.0
  type: th2-act
  custom-config:
    processors:
      - id: "processor id"
        param: 1
    stateSessionAlias: my-processor-state
    enableStoreState: false

    realtime:
      enableMessageSubscribtion: true
      enableEventSubscribtion: false
  pins:
    grpc:
      client:
        - name: to_data_provider
          service-class: com.exactpro.th2.dataprovider.lw.grpc.DataProviderService
          linkTo:
            - box: lw-data-provider
              pin: server
    mq:
      subscribers:
        - name: messages
          attributes:
            - group
            - in
        - name: events
          attributes:
            - event
            - in
      publishers:
        - name: state
          attributes:
            - store
```

# Changelog
## 1.0.0
  + migration to `processor-core-j`
  + migration to `book and pages`