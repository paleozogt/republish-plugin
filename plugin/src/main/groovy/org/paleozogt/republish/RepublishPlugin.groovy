package org.paleozogt.republish

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.tasks.wrapper.Wrapper
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

class ArtifactCoords {
    ArtifactCoords(group, name, version, classifier, extension) {
        this.group= group
        this.name= name
        this.version= version
        this.classifier= StringUtils.isEmpty(classifier) ? null : classifier
        this.extension = StringUtils.isEmpty(extension)  ? null : extension
    }

    String toString() {
        [group,
         name,
         version,
         StringUtils.isEmpty(classifier)?"":classifier,
         StringUtils.isEmpty(extension)?"":extension
        ].join(':')
    }

    String group, name, version, classifier, extension
}

class RepublishExtension {
    private Project project

    Configuration[] configs= []
    File[] paths= []
    String[] groupIncludes= []
    String[] groupExcludes= []
    File scriptPath

    def republishedArtifacts = [:]

    RepublishExtension(Project project) {
        this.project= project
        def scriptText= this.class.getResourceAsStream("/republish.gradle").text

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
                if (configs.length == 0) configs= configurations.findByName('compile')
                logger.lifecycle("republishing configurations:{} paths:{} groupIncludes:{} groupExcludes:{}", 
                                 configs, paths, groupIncludes, groupExcludes)

                if (scriptPath != null) {
                    project.task(type:Wrapper, 'generateRepublishScript') {
                        jarFile new File(scriptPath, jarFile.getAbsolutePath().replace(project.projectDir.getAbsolutePath(), ''))
                        scriptFile new File(scriptPath, scriptFile.getAbsolutePath().replace(project.projectDir.getAbsolutePath(), ''))

                        doFirst {
                            scriptPath.mkdirs()
                        }
                        doLast {
                            new File("$scriptPath/build.gradle").text= scriptText
                        }
                    }
                }

                publishing {
                    publications {
                        configs.each { configuration ->
                            configuration.resolvedConfiguration.resolvedArtifacts.each { art ->
                                def coords= new ArtifactCoords(art.moduleVersion.id.group, art.moduleVersion.id.name, art.moduleVersion.id.version,
                                                               art.classifier, art.extension)
                                if (!accept(coords.group)) return;
                                logger.debug("republishing config={} coordinates={}", configuration.name, coords)

                                def pomFile= getPomFromArtifact(art)
                                def pomXml= new XmlParser().parse(pomFile)

                                def targetName= markAsPublished(coords)
                                if (targetName == null) {
                                    logger.debug("ignoring dupe {}", coords)
                                    return
                                }

                                "$targetName"(MavenPublication) {
                                    groupId coords.group
                                    artifactId coords.name
                                    version coords.version
                                    artifact(art.file) {
                                        classifier coords.classifier
                                        extension  coords.extension
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
                                logger.debug("found pom {}", pomFile)
                                def pomXml= new XmlParser().parse(pomFile)
                                def group= pomXml.groupId[0].value()[0]
                                def name= pomXml.artifactId[0].value()[0]
                                def ver= pomXml.version[0].value()[0]
                                if (!accept(group)) return;

                                fileTree(dir:pomFile.parentFile, include:"**/${name}*", excludes:['**/*.pom', '**/*.md5', '**/*.sha1']).each { art ->
                                    def coords= new ArtifactCoords(group, name, ver,
                                                                   getClassifier(name, ver, art.name), FilenameUtils.getExtension(art.name))
                                    logger.lifecycle("republishing file={}: {}", art, coords)

                                    def targetName= markAsPublished(coords)
                                    if (targetName == null) {
                                        logger.debug("ignoring dupe {}", coords)
                                        return
                                    }

                                    "$targetName"(MavenPublication) {
                                        groupId coords.group
                                        artifactId coords.name
                                        version coords.version
                                        artifact(art) {
                                            classifier coords.classifier
                                            extension coords.extension
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

                        republishedArtifacts.each { targetName, artifacts ->
                            def taskName= "publish${targetName}PublicationTo${repoSuffix}"
                            logger.debug("making convenience task {}", taskName)
                            task.dependsOn(taskName)
                        }
                    }
                }

            }
        }   
    }

    protected boolean accept(group) {
        boolean accept= true;
        accept= accept && (groupIncludes.length == 0 ? true : groupIncludes.contains(group))
        accept= accept && (groupExcludes.length == 0 ? true : !groupExcludes.contains(group))
        return accept
    }

    protected String markAsPublished(ArtifactCoords coords) {
        String targetName= makePublishingTargetName(coords)
        if (!republishedArtifacts.containsKey(targetName)) republishedArtifacts[targetName]= []

        if (republishedArtifacts[targetName].contains(coords.toString())) return null
        republishedArtifacts[targetName].add(coords.toString())
        return targetName
    }

    protected static String makePublishingTargetName(ArtifactCoords coords) {
        return convertToCamelCase(coords.group) +
                convertToCamelCase(coords.name) +
                convertToCamelCase(coords.version)
    }

    static String convertToCamelCase(str) {
        return str.tokenize('.+-_').collect { it.toLowerCase().capitalize() }.join('')
    }

    static String makeRepoSuffix(repo) {
        return repo.name.capitalize() + 'Repository'
    }

    protected Task makeRepublishTask(repo) {
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

    protected static String getClassifier(aid, ver, name) {
        def baseName= FilenameUtils.getBaseName(name)
        def plainName= "$aid-$ver"
        if (baseName == plainName) {
            return null
        } else {
            // the classifier (if any) is whatever's after the version in the baseName
            def cls= baseName.replace("$plainName-", '')
            return cls
        }
    }
}
