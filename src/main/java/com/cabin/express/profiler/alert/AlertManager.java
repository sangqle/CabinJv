package com.cabin.express.profiler.alert;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.profiler.ServerProfiler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Manages profiler metric alerts based on thresholds
 */
public class AlertManager {
    private final List<Alert<?>> alerts = new ArrayList<>();
    private final List<AlertNotifier> notifiers = new ArrayList<>();

    /**
     * Constructor with default logger notifier
     */
    public AlertManager() {
        addNotifier(new AlertNotifier() {
            @Override
            public void notify(Alert<?> alert, Object value) {
                CabinLogger.warn("ALERT: " + alert.getName() + " - " +
                        "Current value: " + value + " " +
                        "Threshold: " + alert.getThreshold());
            }
        });
    }

    /**
     * Add a notifier to handle alerts
     */
    public AlertManager addNotifier(AlertNotifier notifier) {
        notifiers.add(notifier);
        return this;
    }

    /**
     * Add a memory usage alert
     */
    public AlertManager addMemoryAlert(double heapUtilizationThreshold) {
        alerts.add(new Alert<>(
                "High Memory Usage",
                heapUtilizationThreshold,
                snapshot -> snapshot.getMemoryMetrics().getHeapUtilization(),
                value -> value > heapUtilizationThreshold
        ));
        return this;
    }

    /**
     * Add a CPU usage alert
     */
    public AlertManager addCpuAlert(double cpuUtilizationThreshold) {
        alerts.add(new Alert<>(
                "High CPU Usage",
                cpuUtilizationThreshold,
                snapshot -> snapshot.getCpuMetrics().getProcessCpuLoad(),
                value -> value > cpuUtilizationThreshold
        ));
        return this;
    }

    /**
     * Add a high thread count alert
     */
    public AlertManager addThreadAlert(int threadCountThreshold) {
        alerts.add(new Alert<>(
                "High Thread Count",
                threadCountThreshold,
                snapshot -> snapshot.getThreadMetrics().getThreadCount(),
                value -> value > threadCountThreshold
        ));
        return this;
    }

    /**
     * Add a request latency alert
     */
    public AlertManager addLatencyAlert(double avgResponseMsThreshold) {
        alerts.add(new Alert<>(
                "High Response Latency",
                avgResponseMsThreshold,
                snapshot -> snapshot.getRequestMetrics().getAverageResponseTimeMs(),
                value -> value > avgResponseMsThreshold
        ));
        return this;
    }

    /**
     * Add a custom alert with specified criteria
     */
    public <T> AlertManager addCustomAlert(
            String name,
            T threshold,
            Function<ServerProfiler.ProfilerSnapshot, T> valueExtractor,
            Predicate<T> alertCondition) {
        alerts.add(new Alert<>(name, threshold, valueExtractor, alertCondition));
        return this;
    }

    /**
     * Check all alerts against the provided snapshot
     */
    public void checkAlerts(ServerProfiler.ProfilerSnapshot snapshot) {
        for (Alert<?> alert : alerts) {
            if (alert.check(snapshot)) {
                Object value = alert.getValue(snapshot);
                for (AlertNotifier notifier : notifiers) {
                    notifier.notify(alert, value);
                }
            }
        }
    }

    /**
     * Alert definition with criteria and thresholds
     */
    public static class Alert<T> {
        private final String name;
        private final T threshold;
        private final Function<ServerProfiler.ProfilerSnapshot, T> valueExtractor;
        private final Predicate<T> alertCondition;

        public Alert(
                String name,
                T threshold,
                Function<ServerProfiler.ProfilerSnapshot, T> valueExtractor,
                Predicate<T> alertCondition) {
            this.name = name;
            this.threshold = threshold;
            this.valueExtractor = valueExtractor;
            this.alertCondition = alertCondition;
        }

        /**
         * Extract the value from the snapshot
         */
        public T getValue(ServerProfiler.ProfilerSnapshot snapshot) {
            return valueExtractor.apply(snapshot);
        }

        /**
         * Check if this alert should be triggered
         */
        public boolean check(ServerProfiler.ProfilerSnapshot snapshot) {
            T value = getValue(snapshot);
            return alertCondition.test(value);
        }

        // Getters
        public String getName() { return name; }
        public T getThreshold() { return threshold; }
    }
}