/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.examples.helloworld;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link HelloWorldClient}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 *
 * directExecutor() makes it easier to have deterministic tests.
 *
 * <p>For more unit test examples see {@link io.grpc.examples.routeguide.RouteGuideClientTest} and
 * {@link io.grpc.examples.routeguide.RouteGuideServerTest}.
 */
@RunWith(JUnit4.class)
public class HelloWorldClientTest {
  /**
   * This rule manages automatic graceful shutdown for the registered servers and channels at the
   * end of test.
   */
  @Rule
  public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private final GreeterGrpc.GreeterImplBase serviceImpl =
      mock(GreeterGrpc.GreeterImplBase.class, delegatesTo(
          new GreeterGrpc.GreeterImplBase() {
          // By default the client will receive Status.UNIMPLEMENTED for all RPCs.
          // You might need to implement necessary behaviors for your test here, like this:
          //
          // @Override
          // public void sayHello(HelloRequest request, StreamObserver<HelloReply> respObserver) {
          //   respObserver.onNext(HelloReply.getDefaultInstance());
          //   respObserver.onCompleted();
          // }
          }));

  private HelloWorldClient client;

  @Before
  public void setUp() throws Exception {
    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(InProcessServerBuilder
        .forName(serverName).directExecutor().addService(serviceImpl).build().start());

    // Create a client channel and register for automatic graceful shutdown.
    ManagedChannel channel = grpcCleanup.register(
        InProcessChannelBuilder.forName(serverName).directExecutor().build());

    // Create a HelloWorldClient using the in-process channel;
    client = new HelloWorldClient(channel);
  }

  /**
   * To test the client, call from the client against the fake server, and verify behaviors or state
   * changes from the server side.
   */
  @Test
  public void greet_messageDeliveredToServer() {
    ArgumentCaptor<HelloRequest> requestCaptor = ArgumentCaptor.forClass(HelloRequest.class);

    client.greet("test name");

    verify(serviceImpl)
        .sayHello(requestCaptor.capture(), ArgumentMatchers.<StreamObserver<HelloReply>>any());
    assertEquals("test name", requestCaptor.getValue().getName());
  }
}