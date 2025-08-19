package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Endpoint that triggers a Kubernetes Job. Disabled when client not available.
 */
@Path("/run-check")
@ApplicationScoped
public class RunCheckResource {
    private static final Logger LOGGER = Logger.getLogger(RunCheckResource.class.getName());
    private final AtomicReference<KubernetesClient> clientRef = new AtomicReference<>();

    private KubernetesClient client() {
        if (Boolean.getBoolean("k8s.disabled")) {
            return null;
        }
        KubernetesClient existing = clientRef.get();
        if (existing == null) {
            try {
                existing = new KubernetesClientBuilder().build();
                clientRef.compareAndSet(null, existing);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Kubernetes client initialization failed; /run-check disabled: {0}", e.getMessage());
                return null;
            }
        }
        return existing;
    }

    @POST
    public Response runCheck(@Context UriInfo uriInfo) {
        KubernetesClient client = client();
        if (client == null) {
            return Response.status(503).entity("Kubernetes client not available; feature disabled or init failed\n").build();
        }
        try {
            String jobName = "simple-check-job-" + System.currentTimeMillis();
            String overrideImage = uriInfo.getQueryParameters().getFirst("image");
            if (overrideImage == null || overrideImage.isBlank()) {
                overrideImage = System.getProperty("check.image", "simple-check-app:latest");
            }
            String targetUrl = uriInfo.getQueryParameters().getFirst("url");
            if (targetUrl == null || targetUrl.isBlank()) {
                targetUrl = System.getProperty("check.target.url", "");
            }
            Job job = new JobBuilder()
                    .withNewMetadata()
                    .withName(jobName)
                    .addToLabels("app", "simple-check")
                    .endMetadata()
                    .withNewSpec()
                    .withNewTemplate()
                    .withNewSpec()
                    .addNewContainer()
                    .withName("check-container")
                    .withImage(overrideImage)
                    .withImagePullPolicy("IfNotPresent")
                    .addNewEnv().withName("TOTAL_TIMEOUT").withValue("30").endEnv()
                    .addAllToEnv(targetUrl == null || targetUrl.isBlank() ? java.util.List.of() :
                            java.util.List.of(new EnvVarBuilder().withName("TARGET_URL").withValue(targetUrl).build()))
                    .endContainer()
                    .withRestartPolicy("Never")
                    .endSpec()
                    .endTemplate()
                    .withBackoffLimit(0)
                    .endSpec()
                    .build();
            String ns = client.getNamespace();
            if (ns == null || ns.isBlank()) {
                ns = "default";
            }
            client.batch().v1().jobs().inNamespace(ns).create(job);
            return Response.ok("Successfully created Job: " + jobName + "\n").build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create Job", e);
            return Response.serverError().entity("Failed to create Job: " + e.getMessage() + "\n").build();
        }
    }
}
