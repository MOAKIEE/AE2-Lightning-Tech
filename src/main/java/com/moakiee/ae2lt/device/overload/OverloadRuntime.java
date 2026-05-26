package com.moakiee.ae2lt.device.overload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-local overload runtime for one physical device stack.
 */
public final class OverloadRuntime {
    private static final Map<UUID, OverloadRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private final LoadBucket bucket = new LoadBucket();
    private final OverloadDynamics dynamics = new OverloadDynamics();

    private OverloadRuntime() {
    }

    public static OverloadRuntime get(UUID deviceId) {
        return RUNTIMES.computeIfAbsent(deviceId, ignored -> new OverloadRuntime());
    }

    public static void reset(UUID deviceId) {
        if (deviceId != null) {
            RUNTIMES.remove(deviceId);
        }
    }

    public LoadBucket bucket() {
        return bucket;
    }

    public OverloadDynamics dynamics() {
        return dynamics;
    }

    public LockState tick(int cap) {
        return dynamics.tick(bucket.tick(), cap);
    }

    public int currentLoad() {
        return bucket.current();
    }
}
