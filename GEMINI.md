# Gemini Code Assistant Workspace Context

## Project Overview

This project is a high-performance, custom-built RPC (Remote Procedure Call) framework named "My RPC Framework". It is written in Java and built with Maven. The framework is designed to provide a complete solution for distributed service communication, drawing inspiration from established frameworks like Dubbo.

It includes a rich set of features for robust service-to-service interaction, such as service registration and discovery, load balancing, various retry strategies, and resilience patterns like circuit breakers and rate limiting.

A significant point to note is the presence of a typo in a key package name: `org.cade.rpc.comsumer` should be `org.cade.rpc.consumer`. This is a known issue mentioned in the documentation.

## Key Technologies

- **Language:** Java 17
- **Build Tool:** Maven
- **Networking:** Netty (for asynchronous, non-blocking I/O)
- **Service Discovery:** ZooKeeper (via Apache Curator)
- **Serialization:** FastJSON2 (default), Hessian
- **Compression:** Gzip, Zstd
- **Testing:** JUnit 5

## Core Concepts & Architecture

The framework is divided into several key modules that work together to enable remote procedure calls:

- **Provider:** The server-side component that exposes services. Services are registered and started using `ProviderServer`.
- **Consumer:** The client-side component that invokes remote services. It uses a `ConsumerProxyFactory` to create dynamic proxies for service interfaces.
- **Registry:** Manages service registration and discovery. The primary implementation uses ZooKeeper to store service metadata.
- **Protocol:** A custom binary protocol is used for communication, with a defined message structure (Magic, Version, Type, Payload, etc.).
- **SPI (Service Provider Interface):** The framework is highly extensible. Key components like `Serializer`, `Compression`, `LoadBalancer`, and `RetryPolicy` are designed as extension points using Java's `ServiceLoader` mechanism.
- **Resilience:** Includes implementations for:
    - **Load Balancing:** Random, Round-Robin.
    - **Retry Policies:** Retry on same node, Failover, Forking calls.
    - **Rate Limiting:** Concurrency and token-bucket limiters.
    - **Circuit Breaker:** A response-time-based circuit breaker to prevent cascading failures.
    - **Fallback:** Supports returning cached or mock data when calls fail.

## Building and Running

The project is built and managed using Maven.

### Build the Project

To compile the source code and install dependencies:

```bash
mvn clean install
```

### Run the Demo Applications

The `demo` package contains example provider and consumer applications.

**1. Start the Provider:**

```bash
mvn exec:java -Dexec.mainClass="demo.ProviderApp"
```

**2. Start the Consumer:**

```bash
mvn exec:java -Dexec.mainClass="demo.ConsuerApp"
```

*(Note: These commands require a running ZooKeeper instance if the default configuration is used.)*

## Development Conventions & Known Issues

- **Package Naming:** There is a consistent typo in the consumer package name. It is `org.cade.rpc.comsumer` instead of `org.cade.rpc.consumer`. Be mindful of this when working with consumer-related classes.
- **Testing:** The project has a low unit test coverage. New features or bug fixes should ideally include corresponding tests.
- **Logging:** The project uses SLF4J with a Logback implementation. Logs are separated by topic (e.g., `provider`, `consumer_proxy_factory`).
- **Extensibility:** When adding new functionality for serialization, load balancing, etc., it should be done via the SPI mechanism by creating a new implementation and registering it in the `src/main/resources/META-INF/services/` directory.

## Key Files

- `pom.xml`: Defines project structure, dependencies, and build process.
- `README.md`: The primary source of documentation, providing a detailed overview of features, architecture, and usage.
- `src/main/java/org/cade/rpc/provider/ProviderServer.java`: Core class for the service provider, responsible for starting the Netty server and managing service registration.
- `src/main/java/org/cade/rpc/comsumer/ConsumerProxyFactory.java`: Core class for the service consumer, responsible for creating proxy objects that handle the RPC logic.
- `src/main/java/org/cade/rpc/register/ServiceRegister.java`: Interface for the service registry. `DefaultServiceRegister` provides the ZooKeeper implementation.
- `src/main/resources/META-INF/services/`: Directory containing SPI configuration files for making the framework extensible.
- `src/main/java/demo/`: Contains simple applications (`ProviderApp.java`, `ConsuerApp.java`) demonstrating how to use the framework.
