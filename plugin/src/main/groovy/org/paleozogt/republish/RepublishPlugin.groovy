package org.paleozogt.republish

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class RepublishPlugin implements Plugin<Project> {
    Logger logger = LoggerFactory.getLogger(getClass())

    Configuration[] configs= []
    String[] groupIncludes= []
    String[] groupExcludes= []

    void apply(Project project) {
        project.extensions.create("republish", RepublishExtension)
        project.configure(project) {
            logger.lifecycle("configure")

            apply plugin: 'maven-publish'
            publishing {
                repositories {
                    maven {
                        name "buildDir"
                        url "$buildDir/repo"
                    }
                }

                publications {
                    [ configurations.compile ].each { configuration ->
                        configuration.resolvedConfiguration.resolvedArtifacts.each { art ->
                            def artId= art.moduleVersion.id
                            if (groupExcludes.contains(artId.group)) return;

                            logger.lifecycle("art {}", artId)

                            // get the pom
                            def component = project.dependencies.createArtifactResolutionQuery()
                                                    .forComponents(art.id.componentIdentifier)
                                                    .withArtifacts(MavenModule, MavenPomArtifact)
                                                    .execute()
                                                    .resolvedComponents[0]
                            def pomFile= component.getArtifacts(MavenPomArtifact)[0].file
                            def pomXml= new XmlParser().parse(pomFile)

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

class RepublishExtension {
    Configuration[] configs
    String[] groupIncludes
    String[] groupExcludes
}
