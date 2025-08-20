package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// Import Kubernetes client classes
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kubernetes job resource providing job creation functionality.
 */
@Path("/run-check")
@ApplicationScoped
public class KubernetesJobResource {

    private static final Logger LOGGER = Logger.getLogger(KubernetesJobResource.class.getName());
    // Lazily-created Kubernetes client (avoid failing class initialization in test
    // envs without K8s config)
    private static volatile KubernetesClient k8s; // not final to allow lazy init

    private static KubernetesClient k8sClient() {
        // Optional toggle to disable Kubernetes integration entirely (e.g. in tests):
        // -Dk8s.disabled=true
        if (Boolean.getBoolean("k8s.disabled")) {
            return null;
        }
        KubernetesClient ref = k8s;
        if (ref == null) {
            synchronized (KubernetesJobResource.class) {
                ref = k8s;
                if (ref == null) {
                    try {
                        ref = new KubernetesClientBuilder().build();
                        k8s = ref;
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                                "Kubernetes client initialization failed; /run-check disabled: " + e.getMessage());
                        k8s = null; // leave null so later attempts can retry (or remain disabled)
                    }
                }
            }
        }
        return k8s;
    }

    /**
     * Creates a Kubernetes Job (if client available).
     * 
     * @param image optional image override via query parameter
     * @param url optional target URL override via query parameter
     * @return Response with job creation result
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleRunCheck(@QueryParam("image") String image, @QueryParam("url") String url) {
        try {
            KubernetesClient client = k8sClient();
            if (client == null) {
                return Response.status(503)
                        .entity("Kubernetes client not available; feature disabled or init failed\n")
                        .build();
            }
            
            // Generate a unique name for the Job using a timestamp
            String jobName = "simple-check-job-" + System.currentTimeMillis();

            // Allow overriding image via query param or system property for flexibility
            String overrideImage = image;
            if (overrideImage == null || overrideImage.isBlank()) {
                overrideImage = System.getProperty("check.image", "simple-check-app:latest");
            }

            // Target URL (optional) override; falls back to env-based defaults inside
            // script
            String targetUrl = url;
            if (targetUrl == null || targetUrl.isBlank()) {
                targetUrl = System.getProperty("check.target.url", "");
            }

            Job job = new JobBuilder()
                    .withApiVersion("batch/v1")
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
                    // Optional explicit target if provided
                    .addAllToEnv(targetUrl == null || targetUrl.isBlank() ? java.util.List.of()
                            : java.util.List.of(
                                    new io.fabric8.kubernetes.api.model.EnvVarBuilder().withName("TARGET_URL").withValue(targetUrl)
                                            .build()))
                    .endContainer()
                    .withRestartPolicy("Never")
                    .endSpec()
                    .endTemplate()
                    .withBackoffLimit(0)
                    .endSpec()
                    .build();

            // Use the client to create the Job in the same namespace (fallback to 'default'
            // if null)
            String ns = client.getNamespace();
            if (ns == null || ns.isBlank()) {
                ns = "default"; // explicit fallback
            }
            client.batch().v1().jobs().inNamespace(ns).create(job);

            LOGGER.log(Level.INFO, "Successfully created Job: {0} in namespace {1}", new Object[] { jobName, ns });
            return Response.ok("Successfully created Job: " + jobName + "\n").build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create Job: " + e.getMessage(), e);
            return Response.status(500)
                    .entity("Failed to create Job: " + e.getMessage() + "\n")
                    .build();
        }
    }
}