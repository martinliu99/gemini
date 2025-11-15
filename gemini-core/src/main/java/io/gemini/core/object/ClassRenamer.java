/*
 * Copyright © 2023, the original author or authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.core.object;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.SimpleRemapper;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ClassRenamer {

    Map<String, String> getNameMapping();

    byte[] map(String className, byte[] originByteCode) throws IllegalClassFormatException;


    class Default implements ClassRenamer {

        private static final Logger LOGGER = LoggerFactory.getLogger(ClassRenamer.class);

        private final Map<String, String> nameMapping;

        private final Set<String> removedInnerClasses;
        private final Set<String> removedAnnotationDescriptors;

        private final boolean dumpByteCode;
        private final String byteCodeDumpPath;


        public Default(Map<String, String> nameMapping, boolean dumpByteCode, String byteCodeDumpPath) {
            this(nameMapping, null, dumpByteCode, byteCodeDumpPath);
        }

        public Default(Map<String, String> nameMapping, Collection<String> removedAnnotations,
                boolean dumpByteCode, String byteCodeDumpPath) {
            nameMapping = nameMapping == null ? Collections.emptyMap() : nameMapping;
            this.nameMapping = new LinkedHashMap<>(nameMapping.size());
            for (Entry<String, String> entry : nameMapping.entrySet()) {
                String oldName = entry.getKey();
                String newName = entry.getValue();
                if (StringUtils.hasText(oldName) == false || StringUtils.hasText(newName) == false)
                    continue;

                this.nameMapping.put(
                        oldName.replace(ClassUtils.PACKAGE_SEPARATOR, ClassUtils.RESOURCE_SPERATOR),
                        newName.replace(ClassUtils.PACKAGE_SEPARATOR, ClassUtils.RESOURCE_SPERATOR)
                );
            }

            removedAnnotations = removedAnnotations == null ? Collections.emptySet() : removedAnnotations;
            this.removedInnerClasses = new HashSet<String>(removedAnnotations.size());
            this.removedAnnotationDescriptors = new HashSet<String>(removedAnnotations.size());
            for (String annotationName : removedAnnotations) {
                if (StringUtils.hasText(annotationName) == false)
                    continue;

                String descriptor = annotationName.replace(ClassUtils.PACKAGE_SEPARATOR, ClassUtils.RESOURCE_SPERATOR);
                this.removedInnerClasses.add(descriptor);
                this.removedAnnotationDescriptors.add("L" + descriptor + ";" );
            }

            this.dumpByteCode = dumpByteCode;
            this.byteCodeDumpPath = byteCodeDumpPath + File.separator + "class-renamer" + File.separator;

            if (dumpByteCode) {
                File path = new File(this.byteCodeDumpPath);
                path.mkdirs();
            }
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Map<String, String> getNameMapping() {
            return Collections.unmodifiableMap(nameMapping);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public byte[] map(String className, byte[] originByteCode) throws IllegalClassFormatException {
            ClassReader reader = new ClassReader(originByteCode);

            ClassWriter writer = new ClassWriter(null, 0);
            ClassVisitor visitor = new ClassRemapper(writer, new SimpleRemapper(nameMapping));
            if (CollectionUtils.isEmpty(removedAnnotationDescriptors) == false) {
                visitor = new AnnotationRemover(visitor, removedAnnotationDescriptors);
            }

            reader.accept(visitor, 0);
            byte[] mappedByteCode = writer.toByteArray();

            try {
                dumpByteCode(className, originByteCode, mappedByteCode);
            } catch (IOException e) {
                LOGGER.warn("Could not dump renamed class '{}'.", className, e);
            }

            return mappedByteCode;
        }

        private void dumpByteCode(String className, byte[] originByteCode, byte[] mappedByteCode) throws IOException {
            if (dumpByteCode == false)
                return;

            long timestamp = System.currentTimeMillis();

            String originClassName = byteCodeDumpPath + className + "-original." + timestamp + ClassUtils.CLASS_FILE_EXTENSION;
            IOUtils.saveToFile(originByteCode, originClassName);

            String mappedClassName = byteCodeDumpPath + className + "." + timestamp + ClassUtils.CLASS_FILE_EXTENSION;
            IOUtils.saveToFile(mappedByteCode, mappedClassName);
        }


        class AnnotationRemover extends ClassVisitor {

            private final Set<String> removedAnnotationDescriptors;

            public AnnotationRemover(ClassVisitor classVisitor, Set<String> removedAnnotationDescriptors) {
                super(Opcodes.ASM9, classVisitor);

                this.removedAnnotationDescriptors = removedAnnotationDescriptors;
            }

            public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
                if (removedInnerClasses.contains(name) == false)
                    super.visitInnerClass(name, outerName, innerName, access);
            }

            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                return removedAnnotationDescriptors.contains(descriptor) ? null : super.visitAnnotation(descriptor, visible);
            }
        }
    }
}