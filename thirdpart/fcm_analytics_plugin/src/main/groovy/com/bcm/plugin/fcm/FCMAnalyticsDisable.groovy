package com.bcm.plugin.fcm

import org.gradle.api.Plugin
import org.gradle.api.Project

class FCMAnalyticsDisable implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.android.registerTransform(new MessagingAnalyticsDisable(project))
    }
}