package org.example.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Keeps at most one active model stream for each conversation.
 *
 * <p>A newer prompt cancels the previous stream before it is allowed to start.
 * The termination signal is also used to serialize ChatMemory cleanup and the
 * next prompt, so a cancelled stream cannot leave a partial assistant message
 * in runtime memory for the replacement request.</p>
 */
@Service
public class ConversationGenerationCoordinator {

    private final ConcurrentMap<String, Generation> activeGenerations = new ConcurrentHashMap<>();

    public Generation begin(String conversationId) {
        synchronized (activeGenerations) {
            Generation previous = activeGenerations.get(conversationId);
            Generation next = new Generation(conversationId, UUID.randomUUID().toString());
            activeGenerations.put(conversationId, next);
            if (previous != null) {
                previous.cancel();
                next.waitForPrevious(previous.terminated());
            }
            return next;
        }
    }

    public void finish(Generation generation) {
        if (generation.markFinished()) {
            activeGenerations.remove(generation.conversationId(), generation);
            generation.terminate();
        }
    }

    /** Cancels the active stream and waits for it and any predecessor it is waiting on. */
    public Mono<Void> cancelAndAwait(String conversationId) {
        synchronized (activeGenerations) {
            Generation generation = activeGenerations.get(conversationId);
            if (generation == null) {
                return Mono.empty();
            }
            generation.cancel();
            return generation.awaitPreviousTermination().then(generation.terminated());
        }
    }

    public static final class Generation {
        private final String conversationId;
        private final String requestId;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final Sinks.One<Void> cancellation = Sinks.one();
        private final Sinks.One<Void> termination = Sinks.one();
        private Mono<Void> previousTermination = Mono.empty();

        private Generation(String conversationId, String requestId) {
            this.conversationId = conversationId;
            this.requestId = requestId;
        }

        public String requestId() {
            return requestId;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public Mono<Void> cancellation() {
            return cancellation.asMono();
        }

        public Mono<Void> awaitPreviousTermination() {
            return previousTermination;
        }

        private String conversationId() {
            return conversationId;
        }

        private Mono<Void> terminated() {
            return termination.asMono();
        }

        private void waitForPrevious(Mono<Void> previousTermination) {
            this.previousTermination = previousTermination;
        }

        private void cancel() {
            cancelled.set(true);
            cancellation.tryEmitEmpty();
        }

        private void terminate() {
            termination.tryEmitEmpty();
        }

        private boolean markFinished() {
            return finished.compareAndSet(false, true);
        }
    }
}
