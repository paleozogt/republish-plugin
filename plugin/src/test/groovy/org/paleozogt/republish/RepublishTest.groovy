package org.paleozogt.republish

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.BuildLauncher;

import org.junit.Test
import static org.junit.Assert.*

import org.slf4j.Logger
import org.gradle.api.logging.Logging

class RepublishPluginTest {
    private Logger logger= Logging.getLogger(getClass());

    @Test
    public void applyTest() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'org.paleozogt.republish'
    }
}
