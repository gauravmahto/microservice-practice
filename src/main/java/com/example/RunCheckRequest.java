package com.example;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Map;

/**
 * JSON body accepted by POST /run-check.
 * All fields are optional; sensible defaults are applied in the resource if
 * omitted.
 */
public class RunCheckRequest {
  public String namespace; // target namespace; defaults to k8s.namespace.default
  public String jobName; // if omitted, server generates a unique name with "run-check-"
  public String image; // container image; defaults to "busybox:1.36"
  public List<String> command; // e.g. ["sh", "-c"]
  public List<String> args; // e.g. ["echo hello && sleep 5"]
  public Map<String, String> env; // simple env map
  public Map<String, String> labels; // optional labels to attach to Job and Pod

  public Integer backoffLimit; // default 0 (do not retry) or 1 if you prefer
  public Integer ttlSecondsAfterFinished; // default 60
  public Integer activeDeadlineSeconds; // optional kill time for the pod
  public String restartPolicy; // default "Never"
  public String serviceAccountName; // optional SA if your cluster requires one

  @JsonbProperty("parallelism")
  public Integer parallelism; // default 1

  @JsonbProperty("completions")
  public Integer completions; // default 1
}
