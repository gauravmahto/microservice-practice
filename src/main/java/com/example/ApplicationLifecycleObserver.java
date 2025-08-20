package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.BeforeDestroyed;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application lifecycle observer that sets readiness state based on application events.
 */
@ApplicationScoped
public class ApplicationLifecycleObserver {

    private static final Logger LOGGER = Logger.getLogger(ApplicationLifecycleObserver.class.getName());

    /**
     * Observes application startup completion and sets application as ready.
     * 
     * @param event application context initialized event
     */
    public void onApplicationStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        LOGGER.log(Level.INFO, "Application started, setting application as ready");
        ReadinessHealthCheck.setReady(true);
    }

    /**
     * Observes application stop and sets application as not ready.
     * 
     * @param event application context being destroyed event
     */
    public void onApplicationStop(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        LOGGER.log(Level.INFO, "Application stopping, setting application as not ready");
        ReadinessHealthCheck.setReady(false);
    }
}