# republish-plugin
Gradle plugin for "republishing" dependencies to another repo

This plugin automates a task that comes up a lot for me (and maybe you, too): copying artifacts from one repo to another.

It re-uses the 'maven-publish' gradle plugin, so your artifacts are published via the normal publishing mechanism.
republish-plugin helps with getting your artifacts into the 'maven-publish' system.

One scenario is when you want to gather up all the dependencies of your project and republish them somewhere else.

```
buildscript {
    repositories {
        mavenLocal()
        jcenter()
    }
    dependencies {
        classpath 'org.paleozogt:republish-plugin:1.0.0'
    }
}
apply plugin: 'java'
apply plugin: 'org.paleozogt.republish'

repositories {
    jcenter()
}

dependencies {
    compile "commons-io:commons-io:2.4"
    compile 'org.apache.commons:commons-lang3:3.3.2'
}

apply plugin: 'maven-publish'
publishing {
    repositories {
        maven {
            // credentials, etc
        }
    }

    publications {
        // publish my stuff
        //
        exampleJar(MavenPublication) {
            groupId "org.paleozogt"
            artifactId project.name
            version project.version
            from components.java
        }
    }
}

republish {
    configs = [ configurations.compile ]        // pull from compile dependencies (default)
    groupIncludes = [ 'org.apache.commons' ]    // only republish one set of dependencies
}
```


Another scenario is when you have a on-disk folder of artifacts and POMs and want to republish those somewhere else.


```
buildscript {
    repositories {
        mavenLocal()
        jcenter()
    }
    dependencies {
        classpath 'org.paleozogt:republish-plugin:1.0.0'
    }
}
apply plugin: 'java'
apply plugin: 'org.paleozogt.republish'

republish {
    paths = [ file('libs') ]                    // pull from the libs folder, which has artifacts and POMs
    groupIncludes = [ 'org.apache.commons' ]    // only republish one set of dependencies
}
```
