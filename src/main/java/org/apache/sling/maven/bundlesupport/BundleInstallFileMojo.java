/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sling.maven.bundlesupport;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Install an OSGi bundle from a given file path or Maven coordinates (resolved from the repository) to a running Sling instance.
 * One of the following parameter sets are used to determine the bundle to install (evaluated in the given order):
 * <ul>
 * <li>{@link #groupId}, {@link #artifactId}, {@link #version}, and optionally {@link #classifier} and {@link #packaging}</li>
 * <li>{@link #artifact}</li>
 * <li>{@link #bundleFileName}</li>
 * </ul>
 * 
 * To install a bundle which has been built from the current Maven project rather use goal <a href="install-mojo.html">install</a>.
 * For details refer to <a href="bundle-installation.html">Bundle Installation</a>.
 */
@Mojo(name = "install-file", requiresProject = false)
public class BundleInstallFileMojo extends AbstractBundleInstallMojo {

    /**
     * The path of the bundle file to install.
     * Is only effective if artifact is not determined via some other way (Maven coordinates).
     */
    @Parameter(property="sling.file", defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File bundleFileName;

    /**
     * The groupId of the artifact to install. Takes precedence over {@link #artifact} and {@link #bundleFileName}.
     */
    @Parameter(property="sling.groupId")
    private String groupId;

    /**
     * The artifactId of the artifact to install. Takes precedence over {@link #artifact} and {@link #bundleFileName}.
     */
    @Parameter(property="sling.artifactId")
    private String artifactId;

    /**
     * The version of the artifact to install. Takes precedence over {@link #artifact} and {@link #bundleFileName}.
     */
    @Parameter(property="sling.version")
    private String version;

    /**
     * The packaging of the artifact to install
     */
    @Parameter(property="sling.packaging", defaultValue="jar")
    private String packaging = "jar";

    /**
     * The classifier of the artifact to install
     */
    @Parameter(property="sling.classifier")
    private String classifier;

    /**
     * A string of the form {@code groupId:artifactId:version[:packaging[:classifier]]}.
     * Takes precedence over {@link #bundleFileName}.
     */
    @Parameter(property="sling.artifact")
    private String artifact;

    @Override
    protected File getBundleFileName() throws MojoExecutionException {
        File fileName = resolveBundleFileFromArtifact();
        if (fileName == null) {
            fileName = bundleFileName;
        } else {
            if (mountByFS) {
                getLog().warn("The parameter 'mountByFS' is only supported with files outside the Maven repository and therefore ignored in this context!");
                mountByFS = false;
            }
        }
        if (fileName == null) {
            throw new MojoExecutionException("Must provide either sling.file, sling.artifact or sling.groupId/sling.artifactId/sling.version parameters");
        }

        return fileName;
    }

    private File resolveBundleFileFromArtifact() throws MojoExecutionException {
        if (artifactId == null && artifact == null) {
            return null;
        }
        if (artifactId == null) {
            String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length != 3 && tokens.length != 4 && tokens.length != 5) {
                throw new MojoExecutionException("Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            groupId = tokens[0];
            artifactId = tokens[1];
            version = tokens[2];
            if (tokens.length >= 4)
                packaging = tokens[3];
            if (tokens.length == 5)
                classifier = tokens[4];
        }

        File resolvedArtifactFile = resolveArtifact(new DefaultArtifact(groupId, artifactId, classifier, packaging, version));
        getLog().info("Resolved artifact to " + resolvedArtifactFile.getAbsolutePath());
        return resolvedArtifactFile;
    }
}