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

package org.arquillian.container.osgi.remote.deploy.processor.test.mock;

import org.jboss.arquillian.core.api.InstanceProducer;

/**
 * @author Cristina González
 */
public class DummyInstanceProducerImpl implements InstanceProducer<Object> {

	@Override
	public Object get() {
		return _object;
	}

	@Override
	public void set(Object value) {
		_object = value;
	}

	private Object _object;

}