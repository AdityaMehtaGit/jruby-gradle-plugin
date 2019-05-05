package com.github.jrubygradle.testhelper

import java.util.regex.Pattern

/**
 * @author Schalk W. Cronjé.
 */
class VersionFinder {
    @SuppressWarnings(['NoDef'])
    static String find(final File repoDir, final String artifact, final String extension) {
        Pattern pat = ~/^${artifact}-(.+)\.${extension}/
        def files = repoDir.list([ accept: { File dir, String name ->
            name ==~ pat
        } ] as FilenameFilter)

        if (files.size()) {
            def matcher = files[0] =~ pat
            matcher[0][1]
        } else {
            null
        }
    }

    static String findDependency(final File repoDir, final String organisation, final String artifact, final String extension) {
        "${organisation}:${artifact}:${find(repoDir, artifact, extension)}@${extension}"
    }
}
