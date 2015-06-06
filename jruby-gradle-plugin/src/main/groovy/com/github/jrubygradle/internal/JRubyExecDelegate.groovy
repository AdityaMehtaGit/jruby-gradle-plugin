package com.github.jrubygradle.internal

import com.github.jrubygradle.GemUtils
import com.github.jrubygradle.JRubyExec
import groovy.transform.PackageScope
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.internal.FileUtils
import org.gradle.util.CollectionUtils

/**
 * @author Schalk W. Cronjé
 */
class JRubyExecDelegate implements JRubyExecTraits   {

    static final String JRUBYEXEC_CONFIG = JRubyExec.JRUBYEXEC_CONFIG

    def methodMissing(String name, args) {
        if( name == 'args' || name == 'setArgs' ) {
            throw new UnsupportedOperationException("Use jrubyArgs/scriptArgs instead")
        }
        if( name == 'main' ) {
            throw new UnsupportedOperationException("Setting main class for jruby is not a valid operation")
        }

        if(args.size() == 1) {
            passthrough.add( [ "${name}" : args[0] ] )
        } else {
            passthrough.add( [ "${name}" : args ] )
        }
    }

    /** Gets the script to use.
     *
     * @return Get the script to use. Can be null.
     */
    File getScript() { _convertScript() }

    /** Directory to use for unpacking GEMs.
     * This is optional. If not set, then an internal generated folder will be used. In general the latter behaviour
     * is preferred as it allows for isolating different {@code JRubyExec} tasks. However, this functionality is made
     * available for script authors for would like to control this behaviour and potentially share GEMs between
     * various {@code JRubyExec} tasks.
     *
     * @since 0.1.9
     */
    File getGemWorkDir() {
        _convertGemWorkDir(project)
    }

    /** buildArgs creates a list of arguments to pass to the JVM
     */
    List<String> buildArgs() {
        JRubyExecUtils.buildArgs(_convertJrubyArgs(),script,_convertScriptArgs())
    }

    @PackageScope
    def keyAt(Integer index) {
        passthrough[index].keySet()[0]
    }

    @PackageScope
    def valuesAt(Integer index) {
        passthrough[index].values()[0]
    }

    @PackageScope
    void validate() {
        if( this.script == null ) {
            throw new NullPointerException("'script' is not set")
        }
    }

    private def passthrough = []

    static def jrubyexecDelegatingClosure = { Project project, Closure cl ->
        def proxy =  new JRubyExecDelegate()
        Closure cl2 = cl.clone()
        cl2.delegate = proxy
        cl2.call()

        File gemDir= proxy._convertGemWorkDir(project) ?: project.file(project.jruby.gemInstallDir)

        Configuration config = project.configurations.getByName(JRUBYEXEC_CONFIG)
        GemUtils.OverwriteAction overwrite = project.gradle.startParameter.refreshDependencies ?  GemUtils.OverwriteAction.OVERWRITE : GemUtils.OverwriteAction.SKIP
        project.mkdir gemDir
        GemUtils.extractGems(project,config,config,gemDir,overwrite)
        String pathVar = JRubyExecUtils.pathVar()

        project.javaexec {
            classpath JRubyExecUtils.classpathFromConfiguration(config)
            proxy.passthrough.each { item ->
                def k = item.keySet()[0]
                def v = item.values()[0]
                "${k}" v
            }
            main 'org.jruby.Main'
            proxy.buildArgs().each { item ->
               args item.toString()
            }

            setEnvironment JRubyExecUtils.preparedEnvironment(getEnvironment(),proxy.inheritRubyEnv)
            environment 'PATH' : JRubyExecUtils.prepareWorkingPath(gemDir,System.env."${pathVar}")
            environment 'GEM_HOME' : gemDir.absolutePath
        }
    }

    static void addToProject(Project project) {
        project.ext {
            jrubyexec = JRubyExecDelegate.jrubyexecDelegatingClosure.curry(project)
        }
    }
}