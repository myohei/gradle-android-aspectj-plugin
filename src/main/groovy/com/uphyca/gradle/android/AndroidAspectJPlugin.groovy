// This plugin is based on https://github.com/JakeWharton/hugo
package com.uphyca.gradle.android

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAspectJPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def log = project.logger

        final def variants
        final def plugin
        if (project.plugins.hasPlugin(AppPlugin)) {
            variants = project.android.applicationVariants
            plugin = project.plugins.getPlugin(AppPlugin)
        } else if (project.plugins.hasPlugin(LibraryPlugin)) {
            variants = project.android.libraryVariants
            plugin = project.plugins.getPlugin(LibraryPlugin)
        } else {
            throw new IllegalStateException("The 'android' or 'android-library' plugin is required.")
        }

        project.dependencies {
            compile 'org.aspectj:aspectjrt:1.7.4'
        }

        project.afterEvaluate {
            variants.all { variant ->

                def sourceRoots = []
                project.android.sourceSets.main.java.srcDirs.findAll { it.exists() }.each {
                    sourceRoots << it.absolutePath
                }
                project.android.sourceSets[new File(variant.dirName).name].java.srcDirs.findAll {
                    it.exists()
                }.each { sourceRoots << it.absolutePath }
                variant.productFlavors.each {
                    project.android.sourceSets[it.name].java.srcDirs.findAll { it.exists() }.each {
                        sourceRoots << it.absolutePath
                    }
                }
                sourceRoots << "${project.buildDir}/source/r/${variant.dirName}"
                sourceRoots << "${project.buildDir}/source/buildConfig/${variant.dirName}"

                JavaCompile javaCompile = variant.javaCompile
                javaCompile.doLast {
                    String[] args = [
                            "-showWeaveInfo",
                            "-encoding", "UTF-8",
                            "-1.7",
                            "-inpath", javaCompile.destinationDir.toString(),
                            "-aspectpath", javaCompile.classpath.asPath,
                            "-d", javaCompile.destinationDir.toString(),
                            "-classpath", javaCompile.classpath.asPath,
                            "-bootclasspath", plugin.runtimeJarList.join(File.pathSeparator),
                            "-sourceroots", sourceRoots.join(File.pathSeparator)
                    ]

                    log.debug "ajc args: " + Arrays.toString(args)

                    MessageHandler handler = new MessageHandler(true);
                    new Main().run(args, handler);
                    for (IMessage message : handler.getMessages(null, true)) {
                        switch (message.getKind()) {
                            case IMessage.ABORT:
                            case IMessage.ERROR:
                            case IMessage.FAIL:
                                log.error message.message, message.thrown
                                break;
                            case IMessage.WARNING:
                                log.warn message.message, message.thrown
                                break;
                            case IMessage.INFO:
                                log.info message.message, message.thrown
                                break;
                            case IMessage.DEBUG:
                                log.debug message.message, message.thrown
                                break;
                        }
                    }
                }
            }
        }
    }
}