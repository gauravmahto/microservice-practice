package com.example;

import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.annotation.PreDestroy;

/**
 * Resource responsible for managing the Kubernetes client used by the
 * run-check functionality.
 */
public class RunCheckResource {
    /**
     * Reference to the Kubernetes client so that it can be reused across
     * requests and cleaned up when the application shuts down.
     */
    private final AtomicReference<KubernetesClient> clientRef = new AtomicReference<>();

    /**
     * Closes the Kubernetes client if it has been created and clears the
     * reference to avoid leaks.
     */
    @PreDestroy
    void destroy() {
        KubernetesClient client = clientRef.getAndSet(null);
        if (client != null) {
            client.close();
        }
    }
}

