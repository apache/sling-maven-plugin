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

import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonWriter;

import org.apache.maven.model.Resource;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.scannotation.AnnotationDB;

/**
 * Build  <a href="http://sling.apache.org/documentation/the-sling-engine/adapters.html">adapter metadata (JSON)</a> for the Web Console Plugin at {@code /system/console/status-adapters} and
 * {@code /system/console/adapters} from classes annotated with 
 * <a href="https://github.com/apache/sling-adapter-annotations">adapter annotations</a>.
 */
@Mojo(name="generate-adapter-metadata", defaultPhase = LifecyclePhase.PROCESS_CLASSES, 
    threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateAdapterMetadataMojo extends AbstractMojo {

    private static final String ADAPTABLE_DESC = "L" + Adaptable.class.getName().replace('.', '/') + ";";

    private static final String ADAPTABLES_DESC = "L" + Adaptables.class.getName().replace('.', '/') + ";";

    private static final String DEFAULT_CONDITION = "If the adaptable is a %s.";

    private static String getSimpleName(final ClassNode clazz) {
        final String internalName = clazz.name;
        final int idx = internalName.lastIndexOf('/');
        if (idx == -1) {
            return internalName;
        } else {
            return internalName.substring(idx + 1);
        }
    }

    /** The directory which to check for classes with adapter metadata annotations. */
    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File buildOutputDirectory;

    /**
     * Name of the generated descriptor file.
     */
    @Parameter(property = "adapter.descriptor.name", defaultValue = "SLING-INF/adapters.json")
    private String fileName;

    /**
     * The output directory in which to emit the descriptor file with name {@link GenerateAdapterMetadataMojo#fileName}.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File outputDirectory;

    /**
     * The Maven project.
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final Map<String,Object> descriptor = new HashMap<>();

            final AnnotationDB annotationDb = new AnnotationDB();
            annotationDb.scanArchives(buildOutputDirectory.toURI().toURL());

            final Set<String> annotatedClassNames = new HashSet<>();
            addAnnotatedClasses(annotationDb, annotatedClassNames, Adaptable.class);
            addAnnotatedClasses(annotationDb, annotatedClassNames, Adaptables.class);

            for (final String annotatedClassName : annotatedClassNames) {
                getLog().info(String.format("found adaptable annotation on %s", annotatedClassName));
                final String pathToClassFile = annotatedClassName.replace('.', '/') + ".class";
                final File classFile = new File(buildOutputDirectory, pathToClassFile);
                final FileInputStream input = new FileInputStream(classFile);
                final ClassReader classReader;
                try {
                    classReader = new ClassReader(input);
                } finally {
                    input.close();
                }
                final ClassNode classNode = new ClassNode();
                classReader.accept(classNode, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);

                @SuppressWarnings("unchecked")
                final List<AnnotationNode> annotations = classNode.invisibleAnnotations;
                for (final AnnotationNode annotation : annotations) {
                    if (ADAPTABLE_DESC.equals(annotation.desc)) {
                        parseAdaptableAnnotation(annotation, classNode, descriptor);
                    } else if (ADAPTABLES_DESC.equals(annotation.desc)) {
                        parseAdaptablesAnnotation(annotation, classNode, descriptor);
                    }
                }

            }

            final File outputFile = new File(outputDirectory, fileName);
            outputFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(outputFile);
                    JsonWriter jsonWriter = Json.createWriter(writer)) {
                jsonWriter.writeObject(JsonSupport.toJson(descriptor));
            }
            addResource();

        } catch (IOException e) {
            throw new MojoExecutionException("Unable to generate metadata", e);
        } catch (JsonException e) {
            throw new MojoExecutionException("Unable to generate metadata", e);
        }

    }
    
    private void addAnnotatedClasses(final AnnotationDB annotationDb, final Set<String> annotatedClassNames, final Class<? extends Annotation> clazz) {
        Set<String> classNames = annotationDb.getAnnotationIndex().get(clazz.getName());
        if (classNames == null || classNames.isEmpty()) {
            getLog().debug("No classes found with adaptable annotations.");
        } else {
            annotatedClassNames.addAll(classNames);
        }
    }

    private void addResource() {
        final String ourRsrcPath = this.outputDirectory.getAbsolutePath();
        boolean found = false;
        final Iterator<Resource> rsrcIterator = this.project.getResources().iterator();
        while (!found && rsrcIterator.hasNext()) {
            final Resource rsrc = rsrcIterator.next();
            found = rsrc.getDirectory().equals(ourRsrcPath);
        }
        if (!found) {
            final Resource resource = new Resource();
            resource.setDirectory(this.outputDirectory.getAbsolutePath());
            this.project.addResource(resource);
        }

    }

    private void parseAdaptablesAnnotation(final AnnotationNode annotation, final ClassNode classNode,
            final Map<String,Object> descriptor) throws JsonException {
        final Iterator<?> it = annotation.values.iterator();
        while (it.hasNext()) {
            Object name = it.next();
            Object value = it.next();
            if ("value".equals(name)) {
                @SuppressWarnings("unchecked")
                final List<AnnotationNode> annotations = (List<AnnotationNode>) value;
                for (final AnnotationNode innerAnnotation : annotations) {
                    if (ADAPTABLE_DESC.equals(innerAnnotation.desc)) {
                        parseAdaptableAnnotation(innerAnnotation, classNode, descriptor);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAdaptableAnnotation(final AnnotationNode annotation, final ClassNode annotatedClass,
            final Map<String,Object> descriptor) throws JsonException {
        String adaptableClassName = null;
        List<AnnotationNode> adapters = null;

        final List<?> values = annotation.values;

        final Iterator<?> it = values.iterator();
        while (it.hasNext()) {
            Object name = it.next();
            Object value = it.next();

            if ("adaptableClass".equals(name)) {
                adaptableClassName = ((Type) value).getClassName();
            } else if ("adapters".equals(name)) {
                adapters = (List<AnnotationNode>) value;
            }
        }

        if (adaptableClassName == null || adapters == null) {
            throw new IllegalArgumentException(
                    "Adaptable annotation is malformed. Expecting a classname and a list of adapter annotation.");
        }

        Map<String,Object> adaptableDescription;
        if (descriptor.containsKey(adaptableClassName)) {
            adaptableDescription = (Map<String,Object>)descriptor.get(adaptableClassName);
        } else {
            adaptableDescription = new HashMap<>();
            descriptor.put(adaptableClassName, adaptableDescription);
        }

        for (final AnnotationNode adapter : adapters) {
            parseAdapterAnnotation(adapter, annotatedClass, adaptableDescription);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAdapterAnnotation(final AnnotationNode annotation, final ClassNode annotatedClass,
            final Map<String,Object> adaptableDescription) throws JsonException {
        String condition = null;
        List<Type> adapterClasses = null;

        final List<?> values = annotation.values;

        final Iterator<?> it = values.iterator();
        while (it.hasNext()) {
            final Object name = it.next();
            final Object value = it.next();

            if (StringUtils.isEmpty(condition)) {
                condition = String.format(DEFAULT_CONDITION, getSimpleName(annotatedClass));
            }

            if ("condition".equals(name)) {
                condition = (String) value;
            } else if ("value".equals(name)) {
                adapterClasses = (List<Type>) value;
            }
        }

        if (adapterClasses == null) {
            throw new IllegalArgumentException("Adapter annotation is malformed. Expecting a list of adapter classes");
        }
        
        for (final Type adapterClass : adapterClasses) {
            JsonSupport.accumulate(adaptableDescription, condition, adapterClass.getClassName());
        }
    }
    
}
