/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package org.arquillian.container.osgi.remote.processor;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.arquillian.container.osgi.remote.activator.ArquillianBundleActivator;
import org.arquillian.container.osgi.remote.processor.service.BundleActivatorsManager;
import org.arquillian.container.osgi.remote.processor.service.ImportPackageManager;
import org.arquillian.container.osgi.remote.processor.service.ManifestManager;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filters;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.container.ClassContainer;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Cristina González
 */
public class OSGiAllInProcessor implements ApplicationArchiveProcessor {

	@Override
	public void process(Archive<?> archive, TestClass testClass) {
		try {
			JavaArchive javaArchive = (JavaArchive)archive;

			validateBundleArchive(javaArchive);

			addTestClass(javaArchive, testClass);

			addOSGiImports(javaArchive);

			addArquillianDependencies(javaArchive);

			List<Archive<?>> auxiliaryArchives = loadAuxiliaryArchives();

			handleAuxiliaryArchives(javaArchive, auxiliaryArchives);

			cleanRepeatedImports(javaArchive, auxiliaryArchives);

			ManifestManager manifestManager = _manifestManagerInstance.get();

			Manifest manifest = manifestManager.getManifest(javaArchive);

			Attributes mainAttributes = manifest.getMainAttributes();

			Attributes.Name bundleActivatorName = new Attributes.Name(
				"Bundle-Activator");

			String bundleActivator = mainAttributes.getValue(
				bundleActivatorName);

			mainAttributes.put(
				bundleActivatorName,
				ArquillianBundleActivator.class.getCanonicalName());

			manifestManager.replaceManifest(javaArchive, manifest);

			javaArchive.addClass(ArquillianBundleActivator.class);

			if (bundleActivator != null) {
				addBundleActivator(javaArchive, bundleActivator);
			}
		}
		catch (RuntimeException rte) {
			throw rte;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(
				"Not a valid OSGi bundle: " + archive, ex);
		}
	}

	private void addArquillianDependencies(JavaArchive javaArchive)
		throws Exception {

		javaArchive.addPackage(JMXTestRunner.class.getPackage());
	}

	private void addBundleActivator(
			JavaArchive javaArchive, String bundleActivatorValue)
		throws IOException {

		BundleActivatorsManager bundleActivatorsManager =
			_bundleActivatorsManagerInstance.get();

		List<String> bundleActivators =
			bundleActivatorsManager.getBundleActivators(
				javaArchive, _ACTIVATORS_FILE);

		bundleActivators.add(bundleActivatorValue);

		bundleActivatorsManager.replaceBundleActivatorsFile(
			javaArchive, _ACTIVATORS_FILE, bundleActivators);
	}

	private void addOSGiImports(JavaArchive javaArchive) throws IOException {
		String[] extensionsImports =
			new String[] {"org.osgi.framework", "javax.management",
				"javax.management.*", "javax.naming", "javax.naming.*",
				"org.osgi.service.packageadmin", "org.osgi.service.startlevel",
				"org.osgi.util.tracker"
			};

		ManifestManager manifestManager = _manifestManagerInstance.get();

		Manifest manifest = manifestManager.putAttributeValue(
			manifestManager.getManifest(javaArchive), "Import-Package",
			extensionsImports);

		manifestManager.replaceManifest(javaArchive, manifest);
	}

	private void addTestClass(JavaArchive javaArchive, TestClass testClass)
		throws IOException {

		Class<ClassContainer> classContainerClass = ClassContainer.class;

		if (!classContainerClass.isAssignableFrom(javaArchive.getClass())) {
			throw new IllegalArgumentException(
				"ClassContainer expected: " + javaArchive);
		}

		// Get the test class and its super classes

		Class<?> javaClass = testClass.getJavaClass();

		Set<Class<?>> classes = new HashSet<>();

		classes.add(javaClass);

		Class<?> superclass = javaClass.getSuperclass();

		while (superclass != Object.class) {
			classes.add(superclass);

			superclass = superclass.getSuperclass();
		}

		// Check if the application javaArchive already contains
		// the test classes

		String javaArchiveName = javaArchive.getName();

		if (!javaArchiveName.endsWith(".war")) {
			for (Class<?> clazz : classes) {
				boolean testClassFound = false;

				String className = clazz.getName();

				String path = className.replace('.', '/') + ".class";

				Map<ArchivePath, Node> javaArchiveContentMap =
					javaArchive.getContent();

				for (ArchivePath auxpath : javaArchiveContentMap.keySet()) {
					if (auxpath.toString().endsWith(path)) {
						testClassFound = true;

						break;
					}
				}

				if (testClassFound == false) {
					((ClassContainer<?>)javaArchive).addClass(clazz);
				}
			}
		}

		ManifestManager manifestManager = _manifestManagerInstance.get();

		Manifest manifest = manifestManager.putAttributeValue(
			manifestManager.getManifest(javaArchive), "Export-Package",
			javaClass.getPackage().getName());

		manifestManager.replaceManifest(javaArchive, manifest);
	}

	private void cleanRepeatedImports(
			JavaArchive javaArchive, Collection<Archive<?>> auxiliaryArchives)
		throws IOException {

		try {
			ImportPackageManager importPackageManager =
				_importPackageManagerInstance.get();

			ManifestManager manifestManager = _manifestManagerInstance.get();

			Manifest manifest = manifestManager.getManifest(javaArchive);

			manifest = importPackageManager.cleanRepeatedImports(
				manifest, auxiliaryArchives);

			manifestManager.replaceManifest(javaArchive, manifest);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void handleAuxiliaryArchives(
			JavaArchive javaArchive, Collection<Archive<?>> auxiliaryArchives)
		throws Exception {

		for (Archive auxiliaryArchive : auxiliaryArchives) {
			Map<ArchivePath, Node> remoteLoadableExtensionMap =
				auxiliaryArchive.getContent(
					Filters.include(_REMOTE_LOADABLE_EXTENSION_FILE));

			Collection<Node> remoteLoadableExtensions =
				remoteLoadableExtensionMap.values();

			if (remoteLoadableExtensions.size() > 1) {
				throw new RuntimeException(
					"The archive " + auxiliaryArchive.getName() +
						" contains more than one RemoteLoadableExtension file");
			}

			if (remoteLoadableExtensions.size() == 1) {
				Iterator<Node> remoteLoadableExtensionsIterator =
					remoteLoadableExtensions.iterator();

				Node remoteLoadableExtensionsNext =
					remoteLoadableExtensionsIterator.next();

				javaArchive.add(
					remoteLoadableExtensionsNext.getAsset(),
					_REMOTE_LOADABLE_EXTENSION_FILE);
			}

			ZipExporter auxiliaryArchiveZipExporter = auxiliaryArchive.as(
				ZipExporter.class);

			InputStream auxiliaryArchiveInputStream =
				auxiliaryArchiveZipExporter.exportAsInputStream();

			ByteArrayAsset byteArrayAsset = new ByteArrayAsset(
				auxiliaryArchiveInputStream);

			String path = "extension/" + auxiliaryArchive.getName();

			javaArchive.addAsResource(byteArrayAsset, path);

			ManifestManager manifestManager = _manifestManagerInstance.get();

			Manifest manifest = manifestManager.putAttributeValue(
				manifestManager.getManifest(javaArchive), "Bundle-ClassPath",
				".", path);

			manifestManager.replaceManifest(javaArchive, manifest);

			try {
				validateBundleArchive(auxiliaryArchive);

				Manifest auxiliaryArchiveManifest = manifestManager.getManifest(
					(JavaArchive)auxiliaryArchive);

				Attributes mainAttributes =
					auxiliaryArchiveManifest.getMainAttributes();

				String value = mainAttributes.getValue("Import-package");

				if (value != null) {
					String[] importValues = value.split(",");

					manifest = manifestManager.putAttributeValue(
						manifest, "Import-Package", importValues);

					manifestManager.replaceManifest(javaArchive, manifest);
				}

				String bundleActivatorValue = mainAttributes.getValue(
					"Bundle-Activator");

				if ((bundleActivatorValue != null) &&
					!bundleActivatorValue.isEmpty()) {

					addBundleActivator(javaArchive, bundleActivatorValue);
				}
			}
			catch (BundleException e) {
				if (_logger.isInfoEnabled()) {
					_logger.info(
						"Not processing manifest from " + auxiliaryArchive +
							": " + e.getMessage());
				}
			}
		}
	}

	private List<Archive<?>> loadAuxiliaryArchives() {
		List<Archive<?>> archives = new ArrayList<>();

		// load based on the Containers ClassLoader

		ServiceLoader serviceLoader = _serviceLoaderInstance.get();

		Collection<AuxiliaryArchiveAppender> archiveAppenders =
			serviceLoader.all(AuxiliaryArchiveAppender.class);

		for (AuxiliaryArchiveAppender archiveAppender : archiveAppenders) {
			Archive<?> auxiliaryArchive =
				archiveAppender.createAuxiliaryArchive();

			if (auxiliaryArchive != null) {
				archives.add(auxiliaryArchive);
			}
		}

		return archives;
	}

	private void validateBundleArchive(Archive<?> archive) throws Exception {
		Manifest manifest = null;

		Node node = archive.get(JarFile.MANIFEST_NAME);

		if (node != null) {
			manifest = new Manifest(node.getAsset().openStream());
		}

		if (manifest != null) {
			OSGiManifestBuilder.validateBundleManifest(manifest);
		}
		else {
			throw new BundleException("can't obtain Manifest");
		}
	}

	private static final String _ACTIVATORS_FILE =
		"/META-INF/services/" + BundleActivator.class.getCanonicalName();

	private static final String _REMOTE_LOADABLE_EXTENSION_FILE =
		"/META-INF/services/" +
			RemoteLoadableExtension.class.getCanonicalName();

	private static final Logger _logger = LoggerFactory.getLogger(
		ApplicationArchiveProcessor.class);

	@Inject
	private Instance<BundleActivatorsManager> _bundleActivatorsManagerInstance;

	@Inject
	private Instance<ImportPackageManager> _importPackageManagerInstance;

	@Inject
	private Instance<ManifestManager> _manifestManagerInstance;

	@Inject
	private Instance<ServiceLoader> _serviceLoaderInstance;

}