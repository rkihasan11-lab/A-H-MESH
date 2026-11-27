package com.ahmesh.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ahmesh.app.domain.repository.NetworkTelemetryRepository
import com.ahmesh.app.presentation.auth.AppLockSessionCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AHMeshApplication : Application() {

    @Inject
    lateinit var appLockCoordinator: AppLockSessionCoordinator

    @Inject
    lateinit var networkTelemetryRepository: NetworkTelemetryRepository

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0) {
                    appLockCoordinator.onAppReturnedToForeground()
                }
                startedActivityCount++
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                    appLockCoordinator.onAppEnteredBackground()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
