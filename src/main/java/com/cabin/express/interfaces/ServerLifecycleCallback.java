package com.cabin.express.interfaces;

import com.cabin.express.server.CabinServer;

/**
 * Callback interface for server lifecycle events
 */
public interface ServerLifecycleCallback {
    /**
     * Called when server has successfully initialized but before starting the event loop
     */
    void onServerInitialized(int port);

    /**
     * Called if the server encounters a fatal error during startup or execution
     */
    void onServerFailed(Exception e);

    /**
     * Called when the server has cleanly shut down
     */
    void onServerStopped();

}