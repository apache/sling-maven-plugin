/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.maven.bundlesupport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import org.apache.commons.io.file.PathUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.sling.maven.bundlesupport.annotationtest.Adapter1;
import org.apache.sling.maven.bundlesupport.annotationtest.Adapter2;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerateAdapterMetadataMojoTest {

    @Rule
    public TemporaryFolder tmpDirectory = new TemporaryFolder();

    static final Path RELATIVE_ANNOTATIONTEST_PACKAGE_PATH =
            Paths.get("org", "apache", "sling", "maven", "bundlesupport", "annotationtest");

    @Test
    public void testExecute() throws MojoExecutionException, MojoFailureException, IOException, URISyntaxException {
        GenerateAdapterMetadataMojo mojo = new GenerateAdapterMetadataMojo();
        // copy classes in package "annotationtest" to classpath?
        File classpathFolder = tmpDirectory.newFolder("test-classpath");
        Path testClasspath = Paths.get(GenerateAdapterMetadataMojoTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        // only support directory right now
        if (!Files.isDirectory(testClasspath)) {
            throw new IllegalStateException("Only supposed to be called from a directory, not a jar file");
        }
        Path annotationTestSource = testClasspath.resolve(RELATIVE_ANNOTATIONTEST_PACKAGE_PATH);
        Path annotationTestTarget = classpathFolder.toPath().resolve(RELATIVE_ANNOTATIONTEST_PACKAGE_PATH);
        Files.createDirectories(annotationTestTarget);
        PathUtils.copyDirectory(annotationTestSource, annotationTestTarget);
        mojo.buildOutputDirectory = classpathFolder;
        mojo.outputDirectory = tmpDirectory.getRoot();
        mojo.fileName = "output.json";
        mojo.execute();

        // check output file
        Path outputFile = new File(tmpDirectory.getRoot(), "output.json").toPath();
        assertTrue(Files.exists(outputFile));
        JsonObjectBuilder expectedJsonObjectBuilder = Json.createObjectBuilder();
        expectedJsonObjectBuilder.add(
                "java.lang.Long", Json.createObjectBuilder().add("first condition", Adapter2.class.getName()));
        expectedJsonObjectBuilder.add(
                "java.lang.String",
                Json.createObjectBuilder()
                        .add(
                                "If the adaptable is a Adapter1.",
                                Json.createArrayBuilder()
                                        .add(Adapter1.class.getName())
                                        .add(Adapter2.class.getName())));
        expectedJsonObjectBuilder.add(
                "java.lang.Integer",
                Json.createObjectBuilder().add("If the adaptable is a Adapter1.", Adapter1.class.getName()));

        try (InputStream input = Files.newInputStream(outputFile);
                JsonReader jsonReader = Json.createReader(input)) {
            assertEquals(expectedJsonObjectBuilder.build(), jsonReader.readObject());
        }
    }
}
