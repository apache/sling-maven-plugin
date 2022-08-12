/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.maven.bundlesupport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonWriter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.sling.adapter.annotations.Adaptable;
import org.apache.sling.adapter.annotations.Adaptables;
import org.codehaus.plexus.util.StringUtils;

import io.github.classgraph.AnnotationClassRef;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationInfoList.AnnotationInfoFilter;
import io.github.classgraph.AnnotationParameterValue;
import io.github.classgraph.AnnotationParameterValueList;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/**
 * Build <a href="http://sling.apache.org/documentation/the-sling-engine/adapters.html#implementing-adaptable">adapter metadata (JSON)</a> for the Web Console Plugin at {@code /system/console/status-adapters} and
 * {@code /system/console/adapters} from classes annotated with
 * <a href="https://github.com/apache/sling-adapter-annotations">adapter annotations</a>.
 */
@Mojo(name="generate-adapter-metadata", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateAdapterMetadataMojo extends AbstractMojo {

    private static final String DEFAULT_CONDITION = "If the adaptable is a %s.";


    /** The directory which to check for classes with adapter metadata annotations. */
    @Parameter(defaultValue = "${project.build.outputDirectory}")
    File buildOutputDirectory;

    /**
     * Name of the generated descriptor file.
     */
    @Parameter(property = "adapter.descriptor.name", defaultValue = "SLING-INF/adapters.json")
    String fileName;

    /**
     * The output directory in which to emit the descriptor file with name {@link GenerateAdapterMetadataMojo#fileName}.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    File outputDirectory;

    /**
     * The Maven project.
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        final Map<String,Object> descriptor = new HashMap<>();
        ClassGraph classGraph = new ClassGraph()
                                    .enableAnnotationInfo()                  // only consider annotation info
                                    .overrideClasspath(buildOutputDirectory) // just the classpath of the output directory needed (annotation classes themselves not relevant)
                                    .enableExternalClasses();
        if (getLog().isDebugEnabled()) {
            classGraph.verbose();
        }
        try (ScanResult result = classGraph.scan()) {
            ClassInfoList classInfoList = result.getClassesWithAnnotation(Adaptable.class);
            classInfoList = classInfoList.union(result.getClassesWithAnnotation(Adaptables.class));

            for (ClassInfo annotationClassInfo : classInfoList) {
                getLog().info(String.format("found adaptable annotation on %s", annotationClassInfo.getSimpleName()));
                for (AnnotationInfo annotationInfo : annotationClassInfo.getAnnotationInfo().filter(new AdaptableAnnotationInfoFilter())) {
                    AnnotationParameterValueList annotationParameterValues = annotationInfo.getParameterValues();
                    if (annotationInfo.getName().equals(Adaptables.class.getName())) {
                        parseAdaptablesAnnotation(annotationParameterValues, annotationClassInfo.getSimpleName(), descriptor);
                    } else if (annotationInfo.getName().equals(Adaptable.class.getName())) {
                        parseAdaptableAnnotation(annotationParameterValues, annotationClassInfo.getSimpleName(), descriptor);
                    } else {
                        throw new IllegalStateException("Unexpected annotation class found: " + annotationInfo);
                    }
                }
            }

            final File outputFile = new File(outputDirectory, fileName);
            outputFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(outputFile);
                    JsonWriter jsonWriter = Json.createWriter(writer)) {
                jsonWriter.writeObject(JsonSupport.toJson(descriptor));
            }

        } catch (IOException|JsonException e) {
            throw new MojoExecutionException("Unable to generate metadata", e);
        }

    }

    private static final class AdaptableAnnotationInfoFilter implements AnnotationInfoFilter {
        @Override
        public boolean accept(AnnotationInfo annotationInfo) {
            return (annotationInfo.getName().equals(Adaptables.class.getName()) || annotationInfo.getName().equals(Adaptable.class.getName()));
        }
    }

    private void parseAdaptablesAnnotation(AnnotationParameterValueList annotationParameterValues, String annotatedClassName, final Map<String,Object> descriptor) throws JsonException {
        // only one mandatory parameter "value" of type Adaptable[]
        Object[] annotationInfos = (Object[])annotationParameterValues.get(0).getValue();
        for (Object annotationInfo : annotationInfos) {
            parseAdaptableAnnotation(((AnnotationInfo)annotationInfo).getParameterValues(), annotatedClassName, descriptor);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAdaptableAnnotation(AnnotationParameterValueList annotationParameterValues, String annotatedClassName, final Map<String,Object> descriptor) throws JsonException {
        // two parameters: adaptableClass and Adapter[] adapters
        String adaptableClassName = ((AnnotationClassRef) annotationParameterValues.get("adaptableClass").getValue()).getName();
        Object[] adapters = (Object[]) annotationParameterValues.get("adapters").getValue();
        
        Map<String,Object> adaptableDescription;
        if (descriptor.containsKey(adaptableClassName)) {
            adaptableDescription = (Map<String,Object>)descriptor.get(adaptableClassName);
        } else {
            adaptableDescription = new HashMap<>();
            descriptor.put(adaptableClassName, adaptableDescription);
        }

        for (final Object adapter : adapters) {
            parseAdapterAnnotation(((AnnotationInfo)adapter).getParameterValues(), annotatedClassName, adaptableDescription);
        }
    }

    private void parseAdapterAnnotation(AnnotationParameterValueList annotationParameterValues, String annotatedClassName, final Map<String,Object> adaptableDescription) throws JsonException {
        AnnotationParameterValue conditionParameterValue = annotationParameterValues.get("condition");
        String condition = null;
        if (conditionParameterValue != null) {
            condition = (String) conditionParameterValue.getValue();
        }
        if (StringUtils.isEmpty(condition)) {
            condition = String.format(DEFAULT_CONDITION, annotatedClassName);
        }
        Object[] adapterClasses = (Object[]) annotationParameterValues.get("value").getValue();

        if (adapterClasses == null) {
            throw new IllegalArgumentException("Adapter annotation is malformed. Expecting a list of adapter classes");
        }

        for (final Object adapterClass : adapterClasses) {
            String adapterClassName = ((AnnotationClassRef)adapterClass).getName();
            JsonSupport.accumulate(adaptableDescription, condition, adapterClassName);
        }
    }

}
