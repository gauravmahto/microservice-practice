package com.example;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * CDI producer for a shared KubernetesClient.
 * By default this uses in-cluster config if present, otherwise falls back to
 * ~/.kube/config.
 */
@ApplicationScoped
public class KubernetesClientProducer {

  private KubernetesClient client;

  // Optional overrides via MP Config; leave empty to use auto-detection.
  @ConfigProperty(name = "k8s.masterUrl", defaultValue = "")
  String masterUrl;

  @ConfigProperty(name = "k8s.namespace.default", defaultValue = "default")
  String defaultNamespace;

  @Produces
  @ApplicationScoped
  public KubernetesClient client() {
    if (client == null) {
      Config cfg = (masterUrl == null || masterUrl.isBlank())
          ? Config.autoConfigure(null)
          : new ConfigBuilderFrom(Config.autoConfigure(null)).withMasterUrl(masterUrl).build();

      client = new DefaultKubernetesClient(cfg);
    }
    return client;
  }

  /**
   * Convenience method to get a default namespace for callers.
   */
  public String defaultNamespace() {
    return (defaultNamespace == null || defaultNamespace.isBlank()) ? "default" : defaultNamespace;
  }

  @PreDestroy
  void shutdown() {
    if (client != null) {
      client.close();
    }
  }

  /**
   * Small helper to tweak a base Config (Fabric8’s builder is usually via
   * ConfigBuilder).
   */
  static class ConfigBuilderFrom extends io.fabric8.kubernetes.client.ConfigBuilder {
    ConfigBuilderFrom(Config base) {
      super(base);
    }
  }
}
