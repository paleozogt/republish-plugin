package org.paleozogt.republish

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration

class RepublishPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("republish", RepublishExtension)

        project.task('republish') << {
            logger.lifecycle("republish {}", project.republish.groupIncludes)
            project.republish.configs.each { config ->
                logger.lifecycle("{}", config)
            }
        }
    }
}

class RepublishExtension {
    Configuration[] configs
    String[] groupIncludes
    String[] groupExcludes
}
