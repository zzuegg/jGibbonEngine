plugins {
    `java-library`
}

// No dependency on :core — the processor uses string-based annotation lookup
// to avoid a circular dependency (core -> core-processor -> core).
