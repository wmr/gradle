/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.util

import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.MutationGuard
import org.gradle.api.internal.MutationGuards
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.instantiation.InjectAnnotationHandler
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.testfixtures.internal.ProjectBuilderImpl

class TestUtil {
    public static final Closure TEST_CLOSURE = {}
    private static InstantiatorFactory instantiatorFactory
    private static ManagedFactoryRegistry managedFactoryRegistry
    private static ServiceRegistry services

    private final File rootDir

    private TestUtil(File rootDir) {
        NativeServicesTestFixture.initialize()
        this.rootDir = rootDir
    }

    static InstantiatorFactory instantiatorFactory() {
        if (instantiatorFactory == null) {
            NativeServicesTestFixture.initialize()
            def annotationHandlers = ProjectBuilderImpl.getGlobalServices().getAll(InjectAnnotationHandler.class)
            instantiatorFactory = new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory(), annotationHandlers)
        }
        return instantiatorFactory
    }

    static ManagedFactoryRegistry managedFactoryRegistry() {
        if (managedFactoryRegistry == null) {
            NativeServicesTestFixture.initialize()
            managedFactoryRegistry = ProjectBuilderImpl.getGlobalServices().get(ManagedFactoryRegistry.class)
        }
        return managedFactoryRegistry
    }

    static DomainObjectCollectionFactory domainObjectCollectionFactory() {
        return services().get(DomainObjectCollectionFactory)
    }

    static ProviderFactory providerFactory() {
        return services().get(ProviderFactory)
    }

    static ObjectFactory objectFactory() {
        return services().get(ObjectFactory)
    }

    static ObjectFactory objectFactory(TestFile baseDir) {
        def fileResolver = TestFiles.resolver(baseDir)
        def fileCollectionFactory = TestFiles.fileCollectionFactory(baseDir)
        return createServices(fileResolver, fileCollectionFactory).get(ObjectFactory)
    }

    private static ServiceRegistry createServices(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
        def services = new DefaultServiceRegistry()
        services.register {
            it.add(ProviderFactory, new DefaultProviderFactory())
            it.add(TestCrossBuildInMemoryCacheFactory)
            it.add(NamedObjectInstantiator)
            it.add(CollectionCallbackActionDecorator, CollectionCallbackActionDecorator.NOOP)
            it.add(MutationGuard, MutationGuards.identity())
            it.add(DefaultDomainObjectCollectionFactory)
            it.addProvider(new Object() {
                InstantiatorFactory createInstantiatorFactory() {
                    instantiatorFactory()
                }

                ObjectFactory createObjectFactory(InstantiatorFactory instantiatorFactory, NamedObjectInstantiator namedObjectInstantiator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
                    def filePropertyFactory = new DefaultFilePropertyFactory(PropertyHost.NO_OP, fileResolver, fileCollectionFactory)
                    return new DefaultObjectFactory(instantiatorFactory.decorate(services), namedObjectInstantiator, fileResolver, TestFiles.directoryFileTreeFactory(), PropertyHost.NO_OP, filePropertyFactory, fileCollectionFactory, domainObjectCollectionFactory)
                }

                ProjectLayout createProjectLayout() {
                    def filePropertyFactory = new DefaultFilePropertyFactory(PropertyHost.NO_OP, fileResolver, fileCollectionFactory)
                    return new DefaultProjectLayout(fileResolver.resolve("."), fileResolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), PropertyHost.NO_OP, fileCollectionFactory, filePropertyFactory, filePropertyFactory)
                }

                ChecksumService createChecksumService() {
                    new ChecksumService() {
                        @Override
                        HashCode md5(File file) {
                            Hashing.md5().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode sha1(File file) {
                            Hashing.sha1().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode sha256(File file) {
                            Hashing.sha256().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode sha512(File file) {
                            Hashing.sha512().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode hash(File src, String algorithm) {
                            def algo = algorithm.toLowerCase().replaceAll('-', '')
                            Hashing."$algo"().hashBytes(src.bytes)
                        }
                    }
                }
            })
        }
        return services
    }

    static ServiceRegistry services() {
        if (services == null) {
            services = createServices(TestFiles.resolver().newResolver(new File(".").absoluteFile), TestFiles.fileCollectionFactory())
        }
        return services
    }

    static NamedObjectInstantiator objectInstantiator() {
        return services().get(NamedObjectInstantiator)
    }

    static FeaturePreviews featurePreviews() {
        return new FeaturePreviews()
    }

    static TestUtil create(File rootDir) {
        return new TestUtil(rootDir)
    }

    static TestUtil create(TestDirectoryProvider testDirectoryProvider) {
        return new TestUtil(testDirectoryProvider.testDirectory)
    }

    public <T extends Task> T task(Class<T> type) {
        return createTask(type, createRootProject(this.rootDir))
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project) {
        return createTask(type, project, 'name')
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project, String name) {
        return project.services.get(TaskInstantiator).create(name, type)
    }

    static ProjectBuilder builder(File rootDir) {
        return ProjectBuilder.builder().withProjectDir(rootDir)
    }

    static ProjectBuilder builder(TestDirectoryProvider temporaryFolder) {
        return builder(temporaryFolder.testDirectory)
    }

    ProjectInternal rootProject() {
        createRootProject(rootDir)
    }

    static ProjectInternal createRootProject(File rootDir) {
        return ProjectBuilder
            .builder()
            .withProjectDir(rootDir)
            .build()
    }

    static ProjectInternal createChildProject(ProjectInternal parent, String name, File projectDir = null) {
        return ProjectBuilder
            .builder()
            .withName(name)
            .withParent(parent)
            .withProjectDir(projectDir)
            .build()
    }

    static groovy.lang.Script createScript(String code) {
        new GroovyShell().parse(code)
    }

    static Object call(String text, Object... params) {
        toClosure(text).call(*params)
    }

    static Closure toClosure(String text) {
        return new GroovyShell().evaluate("return " + text)
    }

    static Closure toClosure(TestClosure closure) {
        return { param -> closure.call(param) }
    }

    static Closure returns(Object value) {
        return { value }
    }

    static ChecksumService getChecksumService() {
        services().get(ChecksumService)
    }
}

interface TestClosure {
    Object call(Object param);
}
