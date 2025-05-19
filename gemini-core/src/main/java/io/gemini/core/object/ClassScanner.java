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

import java.net.URL;
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

import io.gemini.api.annotation.NoScanning;
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


    class Default implements ClassScanner {

        private static final Logger LOGGER = LoggerFactory.getLogger(ClassScanner.class);

        private static final String STAR = "*";
        private static final Set<Pattern> ACCEPT_ALL_JARS = new HashSet<>();
        private static final String[] ACCEPT_ALL_PACKAGES = new String[] { STAR };


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


        private final DiagnosticLevel diagnosticLevel;

        private final Set<URL> filteredClasspathElementUrls;

        private final ClassInfoList classInfoList;   // cache found class info globally.


        protected Default(boolean enableVerbose, DiagnosticLevel diagnosticLevel,
                Set<ClassLoader> scannedClassLoaders, Set<URL> overrideClasspaths, 
                Set<String> acceptJarPatterns, Set<String> acceptPackages, 
                int workThreads, Set<URL> filteredClasspathElementUrls) {
            // 1.check input arguments
            Assert.notNull(diagnosticLevel, "'diagnosticLevel' must not be null.");
            this.diagnosticLevel = diagnosticLevel;

            Assert.isTrue( !(scannedClassLoaders.size() == 0 && overrideClasspaths.size() == 0), 
                    "'scannedClassLoaders' and 'overrideClasspaths' must not be both empty.");

            Set<Pattern> formattedJarPatterns = this.formatAcceptJarPatterns(acceptJarPatterns);
            String[] formmatedPackages = this.formatAcceptPackages(acceptPackages);
            workThreads = workThreads > NO_WORK_THREAD ? workThreads : NO_WORK_THREAD;

            Assert.notEmpty(filteredClasspathElementUrls, "'filteredClasspathElementUrls' must not be empty.");
            this.filteredClasspathElementUrls = filteredClasspathElementUrls;

            long startedAt = System.nanoTime();
            if(diagnosticLevel.isSimpleEnabled()) {
                LOGGER.info("^Creating ClassScanner with settings, \n"
                        + "  enableVerbose: {} \n  diagnosticLevel: {} \n"
                        + "  scannedClassLoaders: {} \n  overrideClasspaths: {}\n"
                        + "  acceptPackages: {} \n  acceptJarPatterns: {} \n"
                        + "  workThreads: {} \n  filteredClasspathElementUrls: {} \n",
                        enableVerbose, diagnosticLevel, 
                        scannedClassLoaders, overrideClasspaths,
                        acceptPackages, acceptJarPatterns, 
                        workThreads, filteredClasspathElementUrls
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
                        .enableExternalClasses()
//                        .enableInterClassDependencies()   // disable it for performance
                        .ignoreClassVisibility()
                        .disableNestedJarScanning()
                        .acceptPackages(formmatedPackages)
                        .filterClasspathElements(
                                ACCEPT_ALL_JARS == formattedJarPatterns 
                                    ? new AllClasspathElementFilter() : new DefaultClasspathElementFilter(formattedJarPatterns) )
                        ;

                if(overrideClasspaths.size() > 0) {
                    for(URL overrideClasspath : overrideClasspaths) {
                        classGraph.overrideClasspath(overrideClasspath);
                    }
                } else {
                    for(ClassLoader classLoader : scannedClassLoaders) {
                        classGraph.addClassLoader(classLoader);
                    }
                }

                if(workThreads > NO_WORK_THREAD)
                    result = classGraph.scan(workThreads);
                else 
                    result = classGraph.scan();

                // 3.cache found class info
                this.classInfoList = result.getAllClasses()
                        // remove {@code NoScanning} types
                        .filter(NoScanningClassInfoFilter.INSTANCE)
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

        protected Default(ClassScanner classScanner, Set<URL> filteredClasspathElementUrls) {
            Assert.notNull(classScanner, "'classScanner' must not be null.");
            if(classScanner instanceof Default == false)
                throw new IllegalArgumentException("classScanner must be instanceof ClassScanner.Default");

            Assert.notNull(filteredClasspathElementUrls, "'filteredClasspathElementUrls' must not be empty.");
            this.filteredClasspathElementUrls = filteredClasspathElementUrls;

            Default defaultSanner = (Default) classScanner;
            this.classInfoList = defaultSanner.classInfoList;
            this.diagnosticLevel = defaultSanner.diagnosticLevel;
        }

        /**
         * {@inheritDoc}
         */
        public List<String> getClassNamesImplementing(String typeName) {
            return this.getClassesImplementing(typeName, this.filteredClasspathElementUrls).getNames();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ClassInfoList getClassesImplementing(String typeName) {
            return this.getClassesImplementing(typeName, this.filteredClasspathElementUrls);
        }

        private ClassInfoList getClassesImplementing(String typeName, Collection<URL> filteredClasspathElementUrls) {
            Assert.hasText(typeName, "'typeName' must not be empty");

            ClassInfo classInfo = this.classInfoList.get(typeName);
            if(classInfo == null)
                return new ClassInfoList();

            return filterClasses(classInfo.getClassesImplementing(), filteredClasspathElementUrls);
        }

        /**
         * {@inheritDoc}
         */
        public List<String> getClassNamesWithAnnotation(String annotationName) {
            return this.getClassesWithAnnotation(annotationName, this.filteredClasspathElementUrls).getNames();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ClassInfoList getClassesWithAnnotation(String annotationName) {
            return getClassesWithAnnotation(annotationName, filteredClasspathElementUrls);
        }

        private ClassInfoList getClassesWithAnnotation(String annotationName, Collection<URL> filteredClasspathElementUrls) {
            Assert.hasText(annotationName, "'annotationName' must not be empty");

            ClassInfo classInfo = this.classInfoList.get(annotationName);
            if(classInfo == null)
                return new ClassInfoList();

            return filterClasses(classInfo.getClassesWithAnnotation(), filteredClasspathElementUrls);
        }

        private ClassInfoList filterClasses(ClassInfoList classInfoList, Collection<URL> filteredClasspathElementUrls) {
            return classInfoList
                    // filter out {@code NoScanning} types
                    .filter(NoScanningClassInfoFilter.INSTANCE)
                    // filter out class via ClassPathElement
                    .filter(new DefaultClassInfoFilter(filteredClasspathElementUrls));
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


        static enum NoScanningClassInfoFilter implements ClassInfoFilter {

            INSTANCE
            ;

            private static final String NO_SCANNING_TYPE = NoScanning.class.getName();

            @Override
            public boolean accept(ClassInfo classInfo) {
                // filter annotation
                for(AnnotationInfo annotationInfo : classInfo.getAnnotationInfo()) {
                    if(NO_SCANNING_TYPE.equals(annotationInfo.getClassInfo().getName()) == true)
                        return false;
                }

                return true;
            }
        }

        static class DefaultClassInfoFilter implements ClassInfoFilter {

            private Collection<URL> classpathElementURLs;

            protected DefaultClassInfoFilter(Collection<URL> classpathElementURLs) {
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


    public static class InstantiableClassInfoFilter implements ClassInfoFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            // top level or nested, concrete class
            return (!classInfo.isInterface() && !classInfo.isAbstract()
                        && !classInfo.isAnnotation() && !classInfo.isEnum() )
                    && (!classInfo.isInnerClass() || classInfo.isStatic() )
                    ;
        }
    }


    public class Builder {

        private boolean enableVerbose = false;
        private DiagnosticLevel diagnosticLevel = DiagnosticLevel.DISABLED;

        private final Set<ClassLoader> scannedClassLoaders;
        private final Set<URL> overrideClasspaths;

        private final Set<String> acceptJarPatterns;
        private final Set<String> acceptPackages;

        private int workThreads = 0;

        private ClassScanner classScanner;
        private final Set<URL> filteredClasspathElementUrls;


        public Builder() {
            this.scannedClassLoaders = new LinkedHashSet<>();
            this.overrideClasspaths = new LinkedHashSet<>();

            this.acceptJarPatterns = new LinkedHashSet<>();
            this.acceptPackages = new LinkedHashSet<>();

            this.filteredClasspathElementUrls = new LinkedHashSet<>();
        }


        public Builder enableVerbose(boolean enableVerbose) {
            this.enableVerbose = enableVerbose;

            return this;
        }

        public Builder diagnosticLevel(DiagnosticLevel diagnosticLevel) {
            this.diagnosticLevel = diagnosticLevel;

            return this;
        }

        public Builder scannedClassLoaders(ClassLoader... classLoaders) {
            if(classLoaders != null)
                this.scannedClassLoaders.addAll( Arrays.asList(classLoaders) );

            return this;
        }

        public Builder scannedClassLoaders(Collection<ClassLoader> classLoaders) {
            if(classLoaders != null)
                this.scannedClassLoaders.addAll( classLoaders );

            return this;
        }

        public Builder overrideClasspaths(URL... overrideClasspaths) {
            if(overrideClasspaths != null)
                this.overrideClasspaths.addAll( Arrays.asList(overrideClasspaths) );

            return this;
        }

        public Builder overrideClasspaths(Collection<URL> overrideClasspaths) {
            if(overrideClasspaths != null)
                this.overrideClasspaths.addAll( overrideClasspaths );

            return this;
        }

        public Builder acceptJarPatterns(String... acceptJarPatterns) {
            if(acceptJarPatterns != null) {
                this.acceptJarPatterns.addAll( Arrays.asList(acceptJarPatterns) );
            }

            return this;
        }

        public Builder acceptJarPatterns(Collection<String> acceptJarPatterns) {
            if(acceptJarPatterns != null) {
                this.acceptJarPatterns.addAll( acceptJarPatterns );
            }

            return this;
        }

        public Builder acceptPackages(String... acceptPackages) {
            if(acceptPackages != null) {
                this.acceptPackages.addAll( Arrays.asList(acceptPackages) );
            }

            return this;
        }

        public Builder acceptPackages(Collection<String> acceptPackages) {
            if(acceptPackages != null) {
                this.acceptPackages.addAll(acceptPackages);
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

        public Builder filteredClasspathElementUrls(URL... classpathElementUrls) {
            if(classpathElementUrls != null) {
                this.filteredClasspathElementUrls.addAll( Arrays.asList(classpathElementUrls) );
            }

            return this;
        }

        public Builder filteredClasspathElementUrls(Collection<URL> classpathElementUrls) {
            if(classpathElementUrls != null) {
                this.filteredClasspathElementUrls.addAll( classpathElementUrls );
            }

            return this;
        }

        public ClassScanner build() {
            return this.classScanner == null
                    ? new Default(enableVerbose, diagnosticLevel,
                            scannedClassLoaders, overrideClasspaths,
                            acceptJarPatterns, acceptPackages, 
                            workThreads, filteredClasspathElementUrls)
                    : new Default(classScanner, filteredClasspathElementUrls);
        }
    }
}
