/*
 * JamiServicesModule.kt — Hilt DI module for libjamiclient services.
 *
 * Phase 3 commit 3a: empty skeleton. Just registers as a singleton
 * module so Hilt's component graph compiles. No actual @Provides
 * methods yet.
 *
 * Phase 3 commit 3b: this file grows to provide all the libjamiclient
 * services — DaemonService, AccountService, ContactService, CallService,
 * HistoryService, VCardService, DeviceRuntimeService, HardwareService,
 * NotificationService, PreferencesService, LogService, PeerServicesService,
 * ConversationFacade, and the named DaemonExecutor ScheduledExecutorService.
 * Mirrors vendor/jami-client-android/jami-android/app/src/main/java/
 *         cx/ring/dependencyinjection/ServiceInjectionModule.kt
 * but uses our own Impl classes copied into com.avatok.comms.services.jami.
 *
 * Copyright (C) AvaTok Comms contributors.
 * Distributed under GPL-3.0-or-later. See ../../../../LICENSE.
 */
package com.avatok.comms.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object JamiServicesModule {
    // Phase 3 commit 3b: @Provides methods land here.
}
