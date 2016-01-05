package org.paleozogt.republish

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.commons.io.FilenameUtils

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class RepublishPlugin implements Plugin<Project> {
    Logger logger = LoggerFactory.getLogger(getClass())
    def republishedTargets= []

    void apply(Project project) {
        project.extensions.create("republish", RepublishExtension)
        project.afterEvaluate {
            project.configure(project) {
                if (republish.configs.length == 0) republish.configs= configurations.compile
                logger.lifecycle("republishing configurations:{} paths:{} groupIncludes:{} groupExcludes:{}", 
                                 republish.configs, republish.paths, republish.groupIncludes, republish.groupExcludes)

                apply plugin: 'maven-publish'
                publishing {
                    repositories {
                        maven {
                            name "buildDir"
                            url "$buildDir/m2repository"
                        }
                    }

                    publications {
                        republish.configs.each { configuration ->
                            configuration.resolvedConfiguration.resolvedArtifacts.each { art ->
                                def artId= art.moduleVersion.id
                                if (!republish.accept(artId.group)) return;
                                logger.lifecycle("republishing {}", artId)

                                def pomFile= getPomFromArtifact(project, art)
                                def pomXml= new XmlParser().parse(pomFile)

                                def targetName= makeTargetName(artId.name)
                                republishedTargets.push(targetName)

                                "$targetName"(MavenPublication) {
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

                        republish.paths.each { path ->
                            fileTree(dir:path, include:'**/*.pom').each { pomFile ->
                                def pomXml= new XmlParser().parse(pomFile)
                                def gid= pomXml.groupId[0].value()[0]
                                def aid= pomXml.artifactId[0].value()[0]
                                def ver= pomXml.version[0].value()[0]

                                if (!republish.accept(gid)) return;
                                logger.lifecycle("republishing {}:{}:{}", gid, aid, ver)

                                fileTree(dir:path, include:"**/${aid}*", excludes:['**/*.pom', '**/*.md5', '**/*.sha1']).each { art ->
                                    def ext= FilenameUtils.getExtension(art.name)

                                    // the classifier (if any) is whatever's after the version in the filename
                                    def cls= FilenameUtils.getBaseName(art.name).replace("$aid-$ver", '').replace('-', '')
                                    if (cls.length() == 0) cls= null

                                    logger.lifecycle("republishing {}:{}:{}", gid, aid, ver)
                                    def targetName= makeTargetName(aid)
                                    republishedTargets.push(targetName)

                                    "$targetName"(MavenPublication) {
                                        groupId gid
                                        artifactId aid
                                        version ver
                                        artifact(art) {
                                            classifier cls
                                            extension ext
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

                    // convenience targets for each repo
                    repositories.each { repo ->
                        def repoSuffix= repo.name.capitalize() + 'Repository'
                        def task= project.task("republishTo$repoSuffix")

                        republishedTargets.each { targetName ->
                            task.dependsOn("publish${targetName}PublicationTo${repoSuffix}")
                        }
                    }
                }

            }
        }
    }

    String makeTargetName(name) {
        return name.tokenize('-_').collect { it.toLowerCase().capitalize() }.join('')
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
    File[] paths= []
    String[] groupIncludes= []
    String[] groupExcludes= []

    boolean accept(group) {
        boolean accept= true;
        accept= accept && (groupIncludes.length == 0 ? true : groupIncludes.contains(group))
        accept= accept && (groupExcludes.length == 0 ? true : !groupExcludes.contains(group))
        return accept
    }
}
