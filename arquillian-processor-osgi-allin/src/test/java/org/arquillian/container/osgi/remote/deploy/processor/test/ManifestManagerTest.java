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

package org.arquillian.container.osgi.remote.deploy.processor.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.arquillian.container.osgi.remote.processor.service.ManifestManager;
import org.arquillian.container.osgi.remote.processor.service.ManifestManagerImpl;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Cristina González
 */
public class ManifestManagerTest {

	@Test
	public void testGetManifest() throws IOException {
		//given:

		JavaArchive javaArchive = ShrinkWrap.create(
			JavaArchive.class, "dummy-jar.jar");

		javaArchive.addPackage(ImportPackageManagerTest.class.getPackage());

		Manifest manifest = new Manifest();

		manifest.getMainAttributes().put(
			new Attributes.Name("Manifest-Version"), "1.0");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-Name"), "Test");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-ManifestVersion"), "1");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		manifest.write(baos);

		ByteArrayAsset byteArrayAsset = new ByteArrayAsset(baos.toByteArray());

		javaArchive.add(byteArrayAsset, JarFile.MANIFEST_NAME);

		//when:
		Manifest actualManifest = _manifestManager.getManifest(javaArchive);

		//then:
		Attributes mainAttributes = actualManifest.getMainAttributes();

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Manifest-Version")), "1.0");

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Bundle-Name")), "Test");

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Bundle-ManifestVersion")),
			"1");
	}

	@Test
	public void testPutAttribute() throws IOException {
		//given:
		Manifest manifest = new Manifest();

		manifest.getMainAttributes().put(
			new Attributes.Name("Manifest-Version"), "1.0");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-Name"), "Test");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-ManifestVersion"), "1");

		//when:
		Manifest actualManifest = _manifestManager.putAttributeValue(
			manifest, "Import-Package", "com.import.example");

		//then:
		Attributes mainAttributes = actualManifest.getMainAttributes();

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Import-Package")),
			"com.import.example");
	}

	@Test
	public void testPutAttributeList() throws IOException {
		//given:
		Manifest manifest = new Manifest();

		manifest.getMainAttributes().put(
			new Attributes.Name("Manifest-Version"), "1.0");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-Name"), "Test");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-ManifestVersion"), "1");

		//when:
		Manifest actualManifest = _manifestManager.putAttributeValue(
			manifest, "Import-Package", "com.import.example1",
			"com.import.example2");

		//then:
		Attributes mainAttributes = actualManifest.getMainAttributes();

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Import-Package")),
			"com.import.example1,com.import.example2");
	}

	@Test
	public void testReplaceManifest() throws IOException {
		//given:

		JavaArchive javaArchive = ShrinkWrap.create(
			JavaArchive.class, "dummy-jar.jar");

		javaArchive.addPackage(ImportPackageManagerTest.class.getPackage());

		Manifest manifest = new Manifest();

		manifest.getMainAttributes().put(
			new Attributes.Name("Manifest-Version"), "1.0");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-Name"), "Test");
		manifest.getMainAttributes().put(
			new Attributes.Name("Bundle-ManifestVersion"), "1");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		manifest.write(baos);

		ByteArrayAsset byteArrayAsset = new ByteArrayAsset(baos.toByteArray());

		javaArchive.add(byteArrayAsset, JarFile.MANIFEST_NAME);

		//when:
		Manifest updateManifest = new Manifest();

		updateManifest.getMainAttributes().put(
			new Attributes.Name("Manifest-Version"), "2.0");
		updateManifest.getMainAttributes().put(
			new Attributes.Name("Bundle-Name"), "Test2");
		updateManifest.getMainAttributes().put(
			new Attributes.Name("Bundle-ManifestVersion"), "2");

		_manifestManager.replaceManifest(javaArchive, updateManifest);

		//then:
		Manifest actualManifest = _manifestManager.getManifest(javaArchive);

		Attributes mainAttributes = actualManifest.getMainAttributes();

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Manifest-Version")), "2.0");

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Bundle-Name")), "Test2");

		Assert.assertEquals(
			mainAttributes.get(new Attributes.Name("Bundle-ManifestVersion")),
			"2");
	}

	private static final ManifestManager _manifestManager =
		new ManifestManagerImpl();

}