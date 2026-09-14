package com.woodpeckerbros.watchreminder.entitlement;

/** Small clock seam so trial boundaries can be tested without device time. */
public interface EntitlementClock {
    long wallTimeMillis();
}
