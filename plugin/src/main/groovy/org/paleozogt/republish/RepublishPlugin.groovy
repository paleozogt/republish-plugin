package org.paleozogt.republish

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.commons.io.FilenameUtils

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class RepublishPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("republish", RepublishExtension, project)
    }
}

class RepublishExtension {
    Project project

    Configuration[] configs= []
    File[] paths= []
    String[] groupIncludes= []
    String[] groupExcludes= []

    def republishedTargets= []

    RepublishExtension(Project project) {
        this.project= project
    
        project.configure(project) {
            apply plugin: 'maven-publish'
            publishing {
                repositories {
                    maven {
                        name "buildDir"
                        url "$buildDir/m2repository"
                    }
                }

                // convenience targets for each repo
                repositories.each { repo ->
                    makeRepublishTask(repo)
                }
            }
        }

        project.afterEvaluate {
            project.configure(project) {
                if (configs.length == 0) configs= configurations.compile
                logger.lifecycle("republishing configurations:{} paths:{} groupIncludes:{} groupExcludes:{}", 
                                 configs, paths, groupIncludes, groupExcludes)

                publishing {
                    publications {
                        configs.each { configuration ->
                            configuration.resolvedConfiguration.resolvedArtifacts.each { art ->
                                def artId= art.moduleVersion.id
                                if (!accept(artId.group)) return;
                                logger.lifecycle("republishing {}", artId)

                                def pomFile= getPomFromArtifact(art)
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

                        paths.each { path ->
                            fileTree(dir:path, include:'**/*.pom').each { pomFile ->
                                def pomXml= new XmlParser().parse(pomFile)
                                def gid= pomXml.groupId[0].value()[0]
                                def aid= pomXml.artifactId[0].value()[0]
                                def ver= pomXml.version[0].value()[0]

                                if (!accept(gid)) return;
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
                        def repoSuffix= makeRepoSuffix(repo)
                        def task= makeRepublishTask(repo)

                        republishedTargets.each { targetName ->
                            task.dependsOn("publish${targetName}PublicationTo${repoSuffix}")
                        }
                    }
                }

            }
        }   
    }

    boolean accept(group) {
        boolean accept= true;
        accept= accept && (groupIncludes.length == 0 ? true : groupIncludes.contains(group))
        accept= accept && (groupExcludes.length == 0 ? true : !groupExcludes.contains(group))
        return accept
    }
    
    String makeTargetName(name) {
        return name.tokenize('-_').collect { it.toLowerCase().capitalize() }.join('')
    }
    
    String makeRepoSuffix(repo) {
        return repo.name.capitalize() + 'Repository'
    }

    Task makeRepublishTask(repo) {
        def taskName= "republishTo${makeRepoSuffix(repo)}"
        def task= project.tasks.findByPath(taskName)
        if (task == null) {
            task= project.task(taskName)
        }
        return task
    }

    File getPomFromArtifact(ResolvedArtifact artifact) {
        def component = project.dependencies.createArtifactResolutionQuery()
                                .forComponents(artifact.id.componentIdentifier)
                                .withArtifacts(MavenModule, MavenPomArtifact)
                                .execute()
                                .resolvedComponents[0]
        def pomFile= component.getArtifacts(MavenPomArtifact)[0].file
        return pomFile
    }    
}
