package app.kpakam.inference

import javax.inject.Qualifier

/** Marks the on-device [InferenceClient]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnDevice

/** Marks the safety-net [InferenceClient] (`PREFER_ON_DEVICE`, reason `ROUTER_FALLBACK`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SafetyNet

/** Marks the cloud [InferenceClient]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InCloud
