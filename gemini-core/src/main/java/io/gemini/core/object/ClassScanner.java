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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.util.Assert;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraph.ClasspathElementFilter;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassInfoList.ClassInfoFilter;
import io.github.classgraph.ScanResult;
import nonapi.io.github.classgraph.scanspec.AcceptReject;
import nonapi.io.github.classgraph.utils.JarUtils;

/**
 * This class scans ClassLoaders to collect type metadata.
 * 
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ClassScanner {

    static int NO_WORK_THREAD = 0;

    List<String> getClassNamesImplementing(String typeName);

    ClassInfoList getClassesImplementing(String typeName);

    List<String> getClassNamesWithAnnotation(String annotationName);

    ClassInfoList getClassesWithAnnotation(String annotationName);


    /**
     * Mark annotation to ignore type, and type elements, such as constructor, method and field.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target( {ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD} )
    @interface Ignored {  }


    class Default implements ClassScanner {

        private static final Logger LOGGER = LoggerFactory.getLogger(ClassScanner.class);

        private static final String STAR = "*";
        private static final Set<Pattern> ACCEPT_ALL_JARS = new HashSet<>();
        private static final String[] ACCEPT_ALL_PACKAGES = new String[] { STAR };

        private static final IgnoredClassInfoFilter IGNORED_CLASS_FILTER = new IgnoredClassInfoFilter();


        // refer to ClassLoaderHandlerRegistry#AUTOMATIC_PACKAGE_ROOT_PREFIXES
        private static final Set<String> AUTOMATIC_PACKAGE_ROOT_SUFFIXES;

        static {
            AUTOMATIC_PACKAGE_ROOT_SUFFIXES = new HashSet<>();

            // POJO classes folder
            AUTOMATIC_PACKAGE_ROOT_SUFFIXES.add("target/classes");
            // POJO classes folder
            AUTOMATIC_PACKAGE_ROOT_SUFFIXES.add("target/test-classes");

            // Spring-Boot classes folder
            AUTOMATIC_PACKAGE_ROOT_SUFFIXES.add("BOOT-INF/classes");

            // J2EE & Jakarta EE classes folder
            AUTOMATIC_PACKAGE_ROOT_SUFFIXES.add("WEB-INF/classes");
        }


        private final List<URL> classpathElementUrls;
        private final DiagnosticLevel diagnosticLevel;

        private final ClassInfoList classInfoList;   // cache found class info globally.


        protected Default(List<ClassLoader> scannedClassLoaders, 
                boolean enableVerbose, Set<String> acceptJarPatterns, Set<String> acceptPackages, int workThreads,
                List<URL> classpathElementUrls, 
                DiagnosticLevel diagnosticLevel) {
            // 1.check input arguments
            Assert.notEmpty(scannedClassLoaders, "'scannedClassLoaders' must not be empty.");

            Set<Pattern> formattedJarPatterns = this.formatAcceptJarPatterns(acceptJarPatterns);
            String[] formmatedPackages = this.formatAcceptPackages(acceptPackages);
            workThreads = workThreads > NO_WORK_THREAD ? workThreads : NO_WORK_THREAD;

            Assert.notEmpty(classpathElementUrls, "'classpathElementUrls' must not be empty.");
            this.classpathElementUrls = classpathElementUrls;

            Assert.notNull(diagnosticLevel, "'diagnosticLevel' must not be null.");
            this.diagnosticLevel = diagnosticLevel;

            long startedAt = System.nanoTime();
            if(diagnosticLevel.isSimpleEnabled()) {
                LOGGER.info("^Creating ClassScanner with settings, \n"
                        + "  scannedClassLoaders: {} \n  enableVerbose: {} \n  acceptJarPatterns: {} \n"
                        + "  acceptPackages: {} \n  workThreads: {} \n  classpathElementUrls: {} \n"
                        + "  diagnosticLevel: {} \n",
                        scannedClassLoaders, enableVerbose, acceptJarPatterns,
                        acceptPackages, workThreads, classpathElementUrls,
                        diagnosticLevel
                );
            }

            // 2.scan ClassLoader at startup
            ScanResult result = null;
            try {
                ClassGraph classGraph = new ClassGraph()
                        .verbose(enableVerbose)
                        .enableMemoryMapping()
                        .enableClassInfo()
                        .enableAnnotationInfo()
//                        .enableExternalClasses()
//                        .enableInterClassDependencies()   // disable it for performance
                        .ignoreClassVisibility()
                        .disableNestedJarScanning()
                        .acceptPackages(formmatedPackages)
                        .filterClasspathElements(
                                ACCEPT_ALL_JARS == formattedJarPatterns 
                                    ? new AllClasspathElementFilter() : new DefaultClasspathElementFilter(formattedJarPatterns) )
                        ;

                for(ClassLoader classLoader : scannedClassLoaders) {
                    classGraph.addClassLoader(classLoader);
                }

                if(workThreads > NO_WORK_THREAD)
                    result = classGraph.scan(workThreads);
                else 
                    result = classGraph.scan();

                // 3.cache found class info
                this.classInfoList = result.getAllClasses()
                        // remove @Ignored types
                        .filter(IGNORED_CLASS_FILTER)
                        ;
            } finally {
                if(result != null) {
                    try {
                        result.close();
                    } catch(Exception ignored) {
                        /* ignore */
                    }
                }

                if(diagnosticLevel.isSimpleEnabled())
                    LOGGER.info("$Took '{}' seconds to create ClassScanner.", (System.nanoTime() - startedAt) / 1e9 );
            }
        }

        private Set<Pattern> formatAcceptJarPatterns(Set<String> acceptJars) {
            Assert.notEmpty(acceptJars, "'acceptJars' must not be empty.");

            Set<Pattern> patterns = new HashSet<>(acceptJars.size());
            for(String jarPattern : acceptJars) {
                // if contain '*' element, accept all jars
                if(STAR.equals(jarPattern))
                    return ACCEPT_ALL_JARS;
                else
                    patterns.add( AcceptReject.globToPattern(jarPattern, true) );
            }
            return patterns;
        }

        private String[] formatAcceptPackages(Set<String> acceptPackages) {
            Assert.notEmpty(acceptPackages, "'acceptPackages' must not be empty.");

            String[] packages = new String[acceptPackages.size()];
            int index = 0;
            for(String classPackage : acceptPackages) {
                // if contain '*' element, accept all class
                if(STAR.equals(classPackage))
                    return ACCEPT_ALL_PACKAGES;
                else
                    packages[index++] = classPackage;
            }
            return packages;
        }

        protected Default(ClassScanner classScanner, List<URL> classpathElementUrls) {
            Assert.notNull(classScanner, "'classScanner' must not be null.");
            if(classScanner instanceof Default == false)
                throw new IllegalArgumentException("classScanner must be instanceof ClassScanner.Default");

            Assert.notNull(classpathElementUrls, "'classpathElementUrls' must not be empty.");
            this.classpathElementUrls = classpathElementUrls;

            Default defaultSanner = (Default) classScanner;
            this.classInfoList = defaultSanner.classInfoList;
            this.diagnosticLevel = defaultSanner.diagnosticLevel;
        }


        /*
         * @see io.gemini.core.object.ClassScanner#getClasseNamesImplementing(java.lang.String)
         */
        public List<String> getClassNamesImplementing(String typeName) {
            return this.getClassesImplementing(typeName, this.classpathElementUrls).getNames();
        }

        /* @see io.gemini.core.object.ClassScanner#getClassesImplementing(java.lang.String) 
         */
        @Override
        public ClassInfoList getClassesImplementing(String typeName) {
            return this.getClassesImplementing(typeName, this.classpathElementUrls);
        }

        private ClassInfoList getClassesImplementing(String typeName, List<URL> resourceUrls) {
            Assert.hasText(typeName, "'typeName' must not be empty");

            ClassInfo classInfo = this.classInfoList.get(typeName);
            if(classInfo == null)
                return null;

            return filterClasses(classInfo.getClassesImplementing(), resourceUrls);
        }

        /*
         * @see io.gemini.core.object.ClassScanner#getClasseNamesWithAnnotation(java.lang.String)
         */
        public List<String> getClassNamesWithAnnotation(String annotationName) {
            return this.getClassesWithAnnotation(annotationName, this.classpathElementUrls).getNames();
        }

        /* @see io.gemini.core.object.ClassScanner#getClasseWithAnnotation(java.lang.String) 
         */
        @Override
        public ClassInfoList getClassesWithAnnotation(String annotationName) {
            return getClassesWithAnnotation(annotationName, classpathElementUrls);
        }

        private ClassInfoList getClassesWithAnnotation(String annotationName, List<URL> resourceUrls) {
            Assert.hasText(annotationName, "'annotationName' must not be empty");

            ClassInfo classInfo = this.classInfoList.get(annotationName);
            if(classInfo == null)
                return null;

            return filterClasses(classInfo.getClassesWithAnnotation(), resourceUrls);
        }

        private ClassInfoList filterClasses(ClassInfoList classInfoList, List<URL> resourceUrls) {
            return classInfoList
                    // filter @Ignored class
                    .filter(IGNORED_CLASS_FILTER)
                    // filter class via ClassPathElement
                    .filter(new DefaultClassInfoFilter(resourceUrls));
        }


        static class AllClasspathElementFilter implements ClasspathElementFilter {

            @Override
            public boolean includeClasspathElement(String classpathElementPathStr) {
                return true;
            }
        }

        static class DefaultClasspathElementFilter implements ClasspathElementFilter {

            private final Set<Pattern> acceptJarPatterns;

            private DefaultClasspathElementFilter(Set<Pattern> acceptJarPatterns) {
                this.acceptJarPatterns = acceptJarPatterns;
            }

            @Override
            public boolean includeClasspathElement(String classpathElementPathStr) {
                // 1) scan Automatic classfile 
                for(String suffix : AUTOMATIC_PACKAGE_ROOT_SUFFIXES) {
                    if(classpathElementPathStr.endsWith(suffix))
                        return true;
                }

                for(Pattern pattern : acceptJarPatterns) {
                    if(pattern.matcher(JarUtils.leafName(classpathElementPathStr)).matches() == true)
                        return true;
                }

                return false;
            }
        }


        static class IgnoredClassInfoFilter implements ClassInfoFilter {

            /**
             * 
             */
            private static final String IGNORED_TYPE = Ignored.class.getName();

            @Override
            public boolean accept(ClassInfo classInfo) {
                // filter annotation
                for(AnnotationInfo annotationInfo : classInfo.getAnnotationInfo()) {
                    if(IGNORED_TYPE.equals(annotationInfo.getClassInfo().getName()) == true)
                        return false;
                }

                return true;
            }
        }

        static class DefaultClassInfoFilter implements ClassInfoFilter {

            private List<URL> classpathElementURLs;

            protected DefaultClassInfoFilter(List<URL> classpathElementURLs) {
                this.classpathElementURLs = classpathElementURLs == null ? Collections.emptyList() : classpathElementURLs;;
            }

            @Override
            public boolean accept(ClassInfo classInfo) {
                try {
                    // filter classpath
                    URL classUrl = classInfo.getClasspathElementURL();
                    for(URL url : this.classpathElementURLs) {
                        if(classUrl.equals(url))
                            return true;
                    }
                    return false;
                } catch(Throwable t) {
                    LOGGER.warn("Failed to compare ClasspathElement of class '{}'", classInfo.getName(), t);
                    return true;
                }
            }
        }
    }


    public static class AccessibleClassInfoFilter implements ClassInfoFilter {

        /* @see io.github.classgraph.ClassInfoList.ClassInfoFilter#accept(io.github.classgraph.ClassInfo) 
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            return classInfo.isStandardClass()
                    && !classInfo.isAbstract()
                    && !classInfo.isEnum()
                    && classInfo.isPublic()
                    && !classInfo.isAnonymousInnerClass()
                    && (!classInfo.isInnerClass() || classInfo.isStatic())
                    ;
        }
    }


    public class Builder {

        private List<ClassLoader> classLoaders;

        private boolean enableVerbose = false;
        private Set<String> acceptJarPatterns;
        private Set<String> acceptPackages;
        private int workThreads = 0;

        private List<URL> classpathElementUrls;
        private DiagnosticLevel diagnosticLevel = DiagnosticLevel.DISABLED;

        private ClassScanner classScanner;


        public Builder scannedClassLoaders(ClassLoader... classLoaders) {
            if(classLoaders != null)
                this.classLoaders = Arrays.asList(classLoaders);

            return this;
        }

        public Builder enableVerbose(boolean enableVerbose) {
            this.enableVerbose = enableVerbose;

            return this;
        }

        public Builder acceptJarPatterns(String... acceptJarPatterns) {
            if(acceptJarPatterns != null) {
                LinkedHashSet<String> _acceptJarPatterns = new LinkedHashSet<String>(acceptJarPatterns.length);
                for(String acceptJarPattern : acceptJarPatterns) 
                    _acceptJarPatterns.add(acceptJarPattern);

                this.acceptJarPatterns = _acceptJarPatterns;
            }

            return this;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Builder acceptJarPatterns(Collection<String> acceptJarPatterns) {
            if(acceptJarPatterns != null) {
                this.acceptJarPatterns = acceptJarPatterns instanceof Set 
                        ? (Set) acceptJarPatterns : new LinkedHashSet<>(acceptJarPatterns);
            }

            return this;
        }

        public Builder acceptPackages(String... acceptPackages) {
            if(acceptPackages != null) {
                LinkedHashSet<String> _acceptPackages = new LinkedHashSet<String>(acceptPackages.length);
                for(String acceptPackage : acceptPackages) 
                    _acceptPackages.add(acceptPackage);

                this.acceptPackages = _acceptPackages;
            }

            return this;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Builder acceptPackages(Set<String> acceptPackages) {
            if(acceptPackages != null) {
                this.acceptPackages = acceptPackages instanceof Set
                        ? (Set) acceptPackages : new LinkedHashSet<>(acceptPackages);
            }

            return this;
        }

        public Builder workThreads(int workThreads) {
            this.workThreads = workThreads;

            return this;
        }

        public Builder classScanner(ClassScanner classScanner) {
            this.classScanner = classScanner;

            return this;
        }

        public Builder classpathElementUrls(URL... classpathElementUrls) {
            if(classpathElementUrls != null) {
                this.classpathElementUrls = Arrays.asList(classpathElementUrls);
            }

            return this;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Builder classpathElementUrls(Collection<URL> classpathElementUrls) {
            if(classpathElementUrls != null) {
                this.classpathElementUrls = classpathElementUrls instanceof List
                        ? (List) classpathElementUrls : new ArrayList<>(classpathElementUrls);
            }

            return this;
        }

        public Builder diagnosticLevel(DiagnosticLevel diagnosticLevel) {
                this.diagnosticLevel = diagnosticLevel;

            return this;
        }

        public ClassScanner build() {
            return this.classScanner == null
                    ? new Default(classLoaders,
                            enableVerbose, acceptJarPatterns, acceptPackages, workThreads, classpathElementUrls, diagnosticLevel)
                    : new Default(classScanner, classpathElementUrls);
        }
    }
}
