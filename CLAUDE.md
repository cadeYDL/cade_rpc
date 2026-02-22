# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
mvn compile

# Package as JAR
mvn package

# Clean and rebuild
mvn clean compile
```

## Running the Application

This is a client-server RPC system. Run the provider (server) first, then the consumer (client).

```bash
# Run provider server (listens on port 10086)
mvn exec:java -Dexec.mainClass="demo.ProviderApp"

# Run consumer client (in a separate terminal)
mvn exec:java -Dexec.mainClass="demo.ConsuerApp"
```

## Architecture

This is a custom RPC framework built with Netty and fastjson2.

### Core Components

**Provider Side (Server)**
- `ProviderServer`: Netty server that accepts connections, decodes requests, and invokes registered services
- `ProviderRegistry`: Service registry using reflection-based invocation. Services are registered by interface class and looked up by fully-qualified interface name

**Consumer Side (Client)**
- `ConsumerProxyFactory`: Creates JDK dynamic proxies for service interfaces. Method calls are automatically serialized as RPC requests
- `ConnectionManager`: Manages TCP connections with connection pooling per host:port
- In-flight request tracking via `ConcurrentHashMap<Integer, CompletableFuture<Response>>`

**Message Protocol**
- Wire format: `[4-byte length][4-byte magic "cade"][8-byte version][1-byte type][JSON payload]`
- `MsgDecoder`: Extends `LengthFieldBasedFrameDecoder`, deserializes to `Request` or `Response` based on message type
- `RequestEncoder`/`ResponseEncoder`: Serialize messages to JSON using fastjson2

**Message Types**
- `Request`: Contains serviceName (interface FQN), methodName, paramsType (Class[]), params (Object[]), requestID
- `Response`: Contains result, code (0=success, -1=error), message, requestId

### Adding a New Service

1. Define interface in `org.cade.rpc.api`
2. Implement in `org.cade.rpc.provider`
3. Register in `ProviderApp`: `server.register(MyInterface.class, new MyImpl())`
4. Use in consumer via: `factory.getConsumerProxy(MyInterface.class)`