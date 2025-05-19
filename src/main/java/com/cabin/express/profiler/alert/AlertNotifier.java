package com.cabin.express.profiler.alert;

/**
 * Interface for implementing alert notification mechanisms
 */
public interface AlertNotifier {
    /**
     * Notify about an alert with the current value that triggered it
     */
    void notify(AlertManager.Alert<?> alert, Object value);
}