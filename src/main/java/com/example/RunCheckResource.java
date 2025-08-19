package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * POST /run-check
 * Creates a Kubernetes Job with a single container.
 */
@ApplicationScoped
@Path("/run-check")
public class RunCheckResource {

  @Inject
  KubernetesClient k8s;

  @Inject
  KubernetesClientProducer k8sProducer; // for default namespace access

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response runJob(RunCheckRequest req) {
    // 1) Resolve inputs & defaults
    final String ns = (req != null && req.namespace != null && !req.namespace.isBlank())
        ? req.namespace
        : k8sProducer.defaultNamespace();

    final String jobName = (req != null && req.jobName != null && !req.jobName.isBlank())
        ? sanitizeName(req.jobName)
        : "run-check-" + Instant.now().toEpochMilli();

    final String image = (req != null && req.image != null && !req.image.isBlank())
        ? req.image
        : "busybox:1.36";

    final List<String> command = (req != null && req.command != null && !req.command.isEmpty())
        ? req.command
        : List.of("sh", "-c");

    final List<String> args = (req != null && req.args != null && !req.args.isEmpty())
        ? req.args
        : List.of("echo 'RunCheck OK' && sleep 3");

    final Map<String, String> labels = (req != null && req.labels != null) ? req.labels
        : Map.of(
            "app", "run-check",
            "managed-by", "helidon-mp");

    final int backoffLimit = (req != null && req.backoffLimit != null) ? req.backoffLimit : 0;
    final int ttlSec = (req != null && req.ttlSecondsAfterFinished != null) ? req.ttlSecondsAfterFinished : 60;
    final Integer activeDeadline = (req != null) ? req.activeDeadlineSeconds : null;
    final String saName = (req != null && req.serviceAccountName != null && !req.serviceAccountName.isBlank())
        ? req.serviceAccountName
        : null;
    final String restartPolicy = (req != null && req.restartPolicy != null && !req.restartPolicy.isBlank())
        ? req.restartPolicy
        : "Never";
    final int parallelism = (req != null && req.parallelism != null) ? req.parallelism : 1;
    final int completions = (req != null && req.completions != null) ? req.completions : 1;

    // 2) Build container
    ContainerBuilder container = new ContainerBuilder()
        .withName("job")
        .withImage(image)
        .withCommand(command)
        .withArgs(args);

    // Env vars
    if (req != null && req.env != null && !req.env.isEmpty()) {
      List<EnvVar> envVars = new ArrayList<>();
      req.env.forEach((k, v) -> envVars.add(new EnvVar(k, v, null)));
      container = container.withEnv(envVars);
    }

    // 3) Build pod spec
    PodSpecBuilder podSpec = new PodSpecBuilder()
        .withRestartPolicy(restartPolicy)
        .withContainers(container.build());

    if (saName != null) {
      podSpec = podSpec.withServiceAccountName(saName);
    }
    if (activeDeadline != null && activeDeadline > 0) {
      podSpec = podSpec.withActiveDeadlineSeconds(Long.valueOf(activeDeadline));
    }

    // 4) Assemble Job
    Job job = new JobBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(jobName)
                .withNamespace(ns)
                .withLabels(labels)
                .build())
        .withNewSpec()
        .withParallelism(parallelism)
        .withCompletions(completions)
        .withBackoffLimit(backoffLimit)
        .withTtlSecondsAfterFinished(ttlSec)
        .withTemplate(
            new PodTemplateSpecBuilder()
                .withMetadata(new ObjectMetaBuilder().withLabels(labels).build())
                .withSpec(podSpec.build())
                .build())
        .endSpec()
        .build();

    // 5) Create (idempotent behavior: if name exists, append a suffix and create)
    try {
      Job created;
      if (k8s.batch().v1().jobs().inNamespace(ns).withName(jobName).get() != null) {
        String alt = jobName + "-" + UUID.randomUUID().toString().substring(0, 5);
        job.getMetadata().setName(alt);
        created = k8s.batch().v1().jobs().inNamespace(ns).resource(job).create();
        return Response.ok(RunCheckResponse.ok("CREATED", ns, created.getMetadata().getName(),
            created.getMetadata().getUid(), "Job name existed; created with a unique suffix")).build();
      } else {
        created = k8s.batch().v1().jobs().inNamespace(ns).resource(job).create();
        return Response.ok(RunCheckResponse.ok("CREATED", ns, created.getMetadata().getName(),
            created.getMetadata().getUid(), "Job created")).build();
      }
    } catch (Exception e) {
      return Response.serverError()
          .entity(RunCheckResponse.error(ns, jobName, "Failed to create Job: " + e.getMessage()))
          .build();
    }
  }

  /** K8s resource names must be DNS-1123 compatible. This keeps it simple. */
  private static String sanitizeName(String name) {
    String n = name.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
    // trim invalid leading/trailing chars
    n = n.replaceAll("^[^a-z0-9]+", "").replaceAll("[^a-z0-9]+$", "");
    return (n.isBlank() ? "job" : n);
  }
}
