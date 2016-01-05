package org.paleozogt.republish

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class RepublishPlugin implements Plugin<Project> {
    Logger logger = LoggerFactory.getLogger(getClass())

    void apply(Project project) {
        project.extensions.create("republish", RepublishExtension)
        project.afterEvaluate {
            project.configure(project) {
                if (republish.configs.length == 0) republish.configs= configurations.compile
                logger.lifecycle("republishing configurations:{} groupIncludes:{}", republish.configs, republish.groupIncludes)

                apply plugin: 'maven-publish'
                publishing {
                    repositories {
                        maven {
                            name "buildDir"
                            url "$buildDir/repo"
                        }
                    }

                    publications {
                        republish.configs.each { configuration ->
                            configuration.resolvedConfiguration.resolvedArtifacts.each { art ->
                                def artId= art.moduleVersion.id
                                if (!republish.groupIncludes.contains(artId.group)) return;
                                def pomFile= getPomFromArtifact(project, art)
                                def pomXml= new XmlParser().parse(pomFile)

                                logger.lifecycle("republishing {}", artId)

                                "$artId.name"(MavenPublication) {
                                    groupId artId.group
                                    artifactId artId.name
                                    version artId.version
                                    artifact(art.file) {
                                        classifier art.classifier
                                        extension art.extension
                                    }

                                    // copy the pom
                                    pom.withXml {
                                        asNode().setValue(pomXml.value())
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    File getPomFromArtifact(Project project, ResolvedArtifact artifact) {
        def component = project.dependencies.createArtifactResolutionQuery()
                                .forComponents(artifact.id.componentIdentifier)
                                .withArtifacts(MavenModule, MavenPomArtifact)
                                .execute()
                                .resolvedComponents[0]
        def pomFile= component.getArtifacts(MavenPomArtifact)[0].file
        return pomFile
    }
}

class RepublishExtension {
    Configuration[] configs= []
    String[] groupIncludes= []
    String[] groupExcludes= []
}
