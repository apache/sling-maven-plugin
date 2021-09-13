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

import org.apache.commons.httpclient.HttpClient;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.sling.maven.bundlesupport.deploy.BundleDeploymentMethod;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.deploy.DeployMethod;
import org.apache.sling.maven.bundlesupport.deploy.method.FelixPostDeployMethod;
import org.apache.sling.maven.bundlesupport.deploy.method.SlingPostDeployMethod;
import org.apache.sling.maven.bundlesupport.deploy.method.WebDavPutDeployMethod;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringEndsWith;
import org.hamcrest.core.StringStartsWith;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JcrInstallMojoTest {
    private Path outputBasePath = Paths.get("target/test-out/JcrInstallMojoTest");

    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery();

    @Test
    public void testIsBundleFile() throws Exception {
        JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.project = new MavenProject();
        assertFalse("a license file is not a bundle",
                mojo.isBundleFile(Paths.get("src/main/resources/LICENSE").toFile()));
    }

    @Test
    public void testDoExecuteMethod() {
        JcrInstallMojo mojo = new JcrInstallMojo();
        assertTrue("expect felix for WebConsole",
                mojo.doExecuteMethod(BundleDeploymentMethod.WebConsole) instanceof FelixPostDeployMethod);
        assertTrue("expect webdav for webdav",
                mojo.doExecuteMethod(BundleDeploymentMethod.WebDAV) instanceof WebDavPutDeployMethod);
        assertTrue("expect slingpost for slingpost",
                mojo.doExecuteMethod(BundleDeploymentMethod.SlingPostServlet) instanceof SlingPostDeployMethod);
    }

    @Test
    public void testExecuteWithSkip() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        context.checking(new Expectations() {{
            oneOf(mojoLog).debug(with.<String>is(StringStartsWith.startsWith("Skipping")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.skip = true;
        mojo.execute();
    }

    @Test
    public void testExecuteWithNullProject() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        context.checking(new Expectations() {{
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith("null does not exist")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.execute();
    }

    @Test
    public void testExecuteWithNullArtifact() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        context.checking(new Expectations() {{
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith("null does not exist")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.project = new MavenProject();
        mojo.execute();
    }

    @Test
    public void testExecuteWithNullArtifactFile() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        context.checking(new Expectations() {{
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith("null does not exist")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.project = new MavenProject();
        mojo.project.setArtifact(new DefaultArtifact("group", "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar")));
        mojo.execute();
    }

    @Test
    public void testExecuteWithNonExistentFile() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        final String filePath = "src/main/resources/nonexisting-file.jar";
        context.checking(new Expectations() {{
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith(filePath + " does not exist")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.project = new MavenProject();
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(Paths.get(filePath).toFile());
        mojo.project.setArtifact(artifact);
        mojo.execute();
    }

    @Test
    public void testExecuteWithDirectory() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        final String filePath = "src/main/resources";
        context.checking(new Expectations() {{
            oneOf(mojoLog).debug(with.<String>is(StringStartsWith.startsWith(filePath + " is directory")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.project = new MavenProject();
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(Paths.get(filePath).toFile());
        mojo.project.setArtifact(artifact);
        mojo.execute();
    }

    @Test
    public void testExecuteWithBundleFile() throws Exception {
        final Path testOutPath = this.outputBasePath.resolve("testExecuteWithBundleFile");
        Files.createDirectories(testOutPath);
        final File bundleFile = testOutPath.resolve("bundleFile.jar").toFile();
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Bundle-SymbolicName", "bundle-file");
        try (JarOutputStream os = new JarOutputStream(new FileOutputStream(bundleFile), manifest)) {
            os.flush();
        }
        final Log mojoLog = context.mock(Log.class);
        final String filePath = bundleFile.getPath();
        context.checking(new Expectations() {{
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith(filePath + " is an OSGi Bundle")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.project = new MavenProject();
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(Paths.get(filePath).toFile());
        mojo.project.setArtifact(artifact);
        mojo.execute();
    }

    @Test
    public void testExecuteWithDefaultWebConsoleMethod() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        final String filePath = "src/main/resources/META-INF/LICENSE";
        context.checking(new Expectations() {{
            oneOf(mojoLog).debug(with.<String>is(StringEndsWith.endsWith("because deploymentMethod=WebConsole")));
        }});
        final JcrInstallMojo mojo = new JcrInstallMojo();
        mojo.setLog(mojoLog);
        mojo.project = new MavenProject();
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(Paths.get(filePath).toFile());
        mojo.project.setArtifact(artifact);
        mojo.execute();
    }

    public static class DeployParams {
        private final String targetURL;
        private final File file;
        private final String bundleSymbolicName;
        private final DeployContext context;

        public DeployParams(final String targetURL, final File file,
                            final String bundleSymbolicName, final DeployContext context) {
            this.targetURL = targetURL;
            this.file = file;
            this.bundleSymbolicName = bundleSymbolicName;
            this.context = context;
        }

        public String getTargetURL() {
            return targetURL;
        }

        public File getFile() {
            return file;
        }

        public String getBundleSymbolicName() {
            return bundleSymbolicName;
        }

        public DeployContext getContext() {
            return context;
        }
    }

    @FunctionalInterface
    public interface DeployCallback {
        void onDeploy(BundleDeploymentMethod method, DeployParams params) throws MojoExecutionException;
    }

    public JcrInstallMojo createMojoWithDeployHandler(final DeployCallback handler) {
        return new JcrInstallMojo() {
            @Override
            protected HttpClient getHttpClient() {
                return null;
            }

            @Override
            protected DeployMethod doExecuteMethod(final BundleDeploymentMethod bundleDeploymentMethod) {
                return new DeployMethod() {
                    @Override
                    public void deploy(final String targetURL, final File file, final String bundleSymbolicName,
                                       final DeployContext context) throws MojoExecutionException {
                        final DeployParams params = new DeployParams(targetURL, file, bundleSymbolicName, context);
                        handler.onDeploy(bundleDeploymentMethod, params);
                    }

                    @Override
                    public void undeploy(final String targetURL, final File file, final String bundleSymbolicName,
                                         final DeployContext context) throws MojoExecutionException {
                        /* do nothing */
                    }
                };
            }
        };
    }

    static abstract class DeployParamsMatcher extends BaseMatcher<DeployParams> {
        protected Optional<DeployParams> operandAsDeployParams(final Object operand) {
            return Optional.ofNullable(operand)
                    .filter(DeployParams.class::isInstance)
                    .map(DeployParams.class::cast);
        }

        protected Optional<DeployContext> operandAsDeployContext(final Object operand) {
            return operandAsDeployParams(operand).map(DeployParams::getContext);
        }

        @Override
        public void describeTo(final Description description) {

        }
    }

    static class MimeTypeMatcher extends DeployParamsMatcher {
        private final String expectedMimeType;

        public MimeTypeMatcher(final String expectedMimeType) {
            this.expectedMimeType = expectedMimeType;
        }

        @Override
        public boolean matches(final Object o) {
            return operandAsDeployContext(o)
                    .map(DeployContext::getMimeType)
                    .filter(expectedMimeType::equals)
                    .isPresent();
        }
    }

    static class FailOnErrorMatcher extends DeployParamsMatcher {
        private final Boolean expectValue;

        public FailOnErrorMatcher(final Boolean expectValue) {
            this.expectValue = expectValue;
        }

        @Override
        public boolean matches(final Object o) {
            return operandAsDeployContext(o).map(DeployContext::isFailOnError)
                    .filter(expectValue::equals).isPresent();
        }
    }

    static class DeployFileMatcher extends DeployParamsMatcher {
        private final File file;

        public DeployFileMatcher(final File file) {
            this.file = file;
        }

        @Override
        public boolean matches(final Object o) {
            return operandAsDeployParams(o).map(DeployParams::getFile).filter(file::equals).isPresent();
        }
    }

    static class BsnParamMatcher extends DeployParamsMatcher {
        private final String expectValue;

        public BsnParamMatcher(final String expectValue) {
            this.expectValue = expectValue;
        }

        @Override
        public boolean matches(final Object o) {
            return operandAsDeployParams(o).map(DeployParams::getBundleSymbolicName)
                    .filter(expectValue::equals).isPresent();
        }
    }

    static class TargetURLParamMatcher extends DeployParamsMatcher {
        private final String expectValue;

        public TargetURLParamMatcher(final String expectValue) {
            this.expectValue = expectValue;
        }

        @Override
        public boolean matches(final Object o) {
            return operandAsDeployParams(o).map(DeployParams::getTargetURL).filter(expectValue::equals).isPresent();
        }
    }


    @Deprecated
    @Test
    public void testExecuteWithPut() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        final DeployCallback deployCallback = context.mock(DeployCallback.class);
        final String filePath = "src/main/resources/META-INF/LICENSE";
        final String slingUrl = "http://localhost.localdomain:8888/sling";
        final String slingUrlSuffix = "/apps/myapp-packages/application/install";
        final String expectTargetUrl = slingUrl + slingUrlSuffix;
        final boolean expectFailOnError = false;
        final String expectBsnParam = "testExecuteWithPut";
        final File expectDeployFile = Paths.get(filePath).toFile();
        final String expectMimeType = "application/zip";
        context.checking(new Expectations() {{
            oneOf(mojoLog).warn(with.<String>is(StringStartsWith.startsWith("Using deprecated configuration")));
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith("Installing Artifact file ")));
            oneOf(deployCallback).onDeploy(with.is(Matchers.is(BundleDeploymentMethod.WebDAV)),
                    with.is(Matchers.allOf(
                            new TargetURLParamMatcher(expectTargetUrl),
                            new FailOnErrorMatcher(expectFailOnError),
                            new BsnParamMatcher(expectBsnParam),
                            new DeployFileMatcher(expectDeployFile),
                            new MimeTypeMatcher(expectMimeType))));
        }});
        final JcrInstallMojo mojo = createMojoWithDeployHandler(deployCallback);
        mojo.setLog(mojoLog);
        mojo.slingUrl = slingUrl;
        mojo.slingUrlSuffix = slingUrlSuffix;
        mojo.project = new MavenProject();
        mojo.project.setArtifactId(expectBsnParam);
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(expectDeployFile);
        mojo.project.setArtifact(artifact);
        mojo.mimeType = expectMimeType;
        mojo.usePut = true;
        mojo.failOnError = expectFailOnError;
        mojo.execute();
    }

    @Test
    public void testExecuteWithWebDAV() throws Exception {
        final Log mojoLog = context.mock(Log.class);
        final DeployCallback deployCallback = context.mock(DeployCallback.class);
        final String filePath = "src/main/resources/META-INF/LICENSE";
        final String slingUrl = "http://localhost.localdomain:8888/sling";
        final String slingUrlSuffix = "/apps/myapp-packages/content/install";
        final String expectTargetUrl = slingUrl + slingUrlSuffix;
        final boolean expectFailOnError = true;
        final String expectBsnParam = "testExecuteWithWebDAV";
        final File expectDeployFile = Paths.get(filePath).toFile();
        final String expectMimeType = "application/json";
        context.checking(new Expectations() {{
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith("Installing Artifact file ")));
            oneOf(deployCallback).onDeploy(with.is(Matchers.is(BundleDeploymentMethod.WebDAV)),
                    with.is(Matchers.allOf(
                            new TargetURLParamMatcher(expectTargetUrl),
                            new FailOnErrorMatcher(expectFailOnError),
                            new BsnParamMatcher(expectBsnParam),
                            new DeployFileMatcher(expectDeployFile),
                            new MimeTypeMatcher(expectMimeType))));
        }});
        final JcrInstallMojo mojo = createMojoWithDeployHandler(deployCallback);
        mojo.setLog(mojoLog);
        mojo.slingUrl = slingUrl;
        mojo.slingUrlSuffix = slingUrlSuffix;
        mojo.project = new MavenProject();
        mojo.project.setArtifactId(expectBsnParam);
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(expectDeployFile);
        mojo.project.setArtifact(artifact);
        mojo.mimeType = expectMimeType;
        mojo.deploymentMethod = BundleDeploymentMethod.WebDAV;
        mojo.failOnError = expectFailOnError;
        mojo.execute();
    }

    @Test
    public void testExecuteWithSlingPostServlet() throws Exception {
        final Path testOutPath = this.outputBasePath.resolve("testExecuteWithSlingPostServlet");
        Files.createDirectories(testOutPath);
        final File expectDeployFile = testOutPath.resolve("jarFile.jar").toFile();
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream os = new JarOutputStream(new FileOutputStream(expectDeployFile), manifest)) {
            os.flush();
        }
        final Log mojoLog = context.mock(Log.class);
        final DeployCallback deployCallback = context.mock(DeployCallback.class);
        final String slingUrl = "http://localhost.localdomain:8888/sling";
        final String slingUrlSuffix = "/apps/myapp-packages/content/install";
        final String expectTargetUrl = slingUrl + slingUrlSuffix;
        final boolean expectFailOnError = true;
        final String expectBsnParam = "testExecuteWithSlingPostServlet";
        final String expectMimeType = "application/java-archive";
        context.checking(new Expectations() {{
            oneOf(mojoLog).debug(with.<String>is(StringStartsWith.startsWith("getBundleSymbolicName: No Bundle-SymbolicName in")));
            oneOf(mojoLog).info(with.<String>is(StringStartsWith.startsWith("Installing Artifact file ")));
            oneOf(deployCallback).onDeploy(with.is(Matchers.is(BundleDeploymentMethod.SlingPostServlet)),
                    with.is(Matchers.allOf(
                            new TargetURLParamMatcher(expectTargetUrl),
                            new FailOnErrorMatcher(expectFailOnError),
                            new BsnParamMatcher(expectBsnParam),
                            new DeployFileMatcher(expectDeployFile),
                            new MimeTypeMatcher(expectMimeType))));
        }});
        final JcrInstallMojo mojo = createMojoWithDeployHandler(deployCallback);
        mojo.setLog(mojoLog);
        mojo.slingUrl = slingUrl;
        mojo.slingUrlSuffix = slingUrlSuffix;
        mojo.project = new MavenProject();
        mojo.project.setArtifactId(expectBsnParam);
        final DefaultArtifact artifact = new DefaultArtifact("group",
                "artifact", "1", "compile",
                "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(expectDeployFile);
        mojo.project.setArtifact(artifact);
        mojo.mimeType = expectMimeType;
        mojo.deploymentMethod = BundleDeploymentMethod.SlingPostServlet;
        mojo.failOnError = expectFailOnError;
        mojo.execute();
    }



}