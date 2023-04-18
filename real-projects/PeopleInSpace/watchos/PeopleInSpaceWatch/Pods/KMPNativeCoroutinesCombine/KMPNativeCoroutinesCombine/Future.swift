//
//  Future.swift
//  KMPNativeCoroutinesCombine
//
//  Created by Rick Clephas on 06/06/2021.
//

import Combine
import KMPNativeCoroutinesCore

/// Creates an `AnyPublisher` for the provided `NativeSuspend`.
/// - Parameter nativeSuspend: The native suspend function to await.
/// - Returns: A publisher that either finishes with a single value or fails with an error.
public func createFuture<Result, Failure: Error, Unit>(
    for nativeSuspend: @escaping NativeSuspend<Result, Failure, Unit>
) -> AnyPublisher<Result, Failure> {
    return NativeSuspendFuture(nativeSuspend: nativeSuspend)
        .eraseToAnyPublisher()
}

internal struct NativeSuspendFuture<Result, Failure: Error, Unit>: Publisher {
    
    typealias Output = Result
    typealias Failure = Failure
    
    let nativeSuspend: NativeSuspend<Result, Failure, Unit>
    
    func receive<S>(subscriber: S) where S : Subscriber, Failure == S.Failure, Result == S.Input {
        let subscription = NativeSuspendSubscription(nativeSuspend: nativeSuspend, subscriber: subscriber)
        subscriber.receive(subscription: subscription)
    }
}

internal class NativeSuspendSubscription<Result, Failure, Unit, S: Subscriber>: Subscription where S.Input == Result, S.Failure == Failure {
    
    private var nativeCancellable: NativeCancellable<Unit>? = nil
    private var subscriber: S?
    
    init(nativeSuspend: NativeSuspend<Result, Failure, Unit>, subscriber: S) {
        self.subscriber = subscriber
        nativeCancellable = nativeSuspend({ output, unit in
            if let subscriber = self.subscriber {
                _ = subscriber.receive(output)
                subscriber.receive(completion: .finished)
            }
            return unit
        }, { error, unit in
            self.subscriber?.receive(completion: .failure(error))
            return unit
        })
    }
    
    func request(_ demand: Subscribers.Demand) { }
    
    func cancel() {
        subscriber = nil
        _ = nativeCancellable?()
        nativeCancellable = nil
    }
}
