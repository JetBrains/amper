# gRPC Hello World Example

The Java code is sourced from the 
[grpc-java repo](https://github.com/grpc/grpc-java/tree/v1.76.x/examples/src/main/java/io/grpc/examples/helloworld).
Should be kept in sync with that.

## Running the Example

You can launch `HelloWorldServer.main` and `HelloWorldClient.main` using the IDE run functionality. 

To run the example manually:
```bash
./amper run -m grpc-hello-world --main-class io.grpc.examples.helloworld.HelloWorldServer
```
And then in a separate shell:
```bash
./amper run -m grpc-hello-world --main-class io.grpc.examples.helloworld.HelloWorldClient
```

To run tests manually:
```bash
./amper test -m grpc-hello-world
```