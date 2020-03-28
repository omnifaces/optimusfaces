/*
 * Copyright 2020 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.optimusfaces.el;

import static java.util.stream.Collectors.toList;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.FeatureDescriptor;
import java.util.Collection;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELResolver;

import org.omnifaces.persistence.model.BaseEntity;

public class NestedBaseEntityELResolver extends ELResolver {

	@Override
	public Class<?> getCommonPropertyType(ELContext context, Object base) {
		return null;
	}

	@Override
	public Class<?> getType(ELContext context, Object base, Object property) {
		return null;
	}

	@Override
	public Object getValue(ELContext context, Object base, Object property) {
        if (!(base instanceof BaseEntity)) {
            return null;
        }

        String propertyString = property.toString();

        if (!propertyString.contains(".")) {
        	return null;
        }

        Object value = base;

        for (String propertyPart : propertyString.split("\\.")) {
        	if (value instanceof Collection) {
        		value = stream(value).map(item -> context.getELResolver().getValue(context, item, propertyPart)).collect(toList());
        	}
        	else {
        		value = context.getELResolver().getValue(context, value, propertyPart);
        	}

        }

        context.setPropertyResolved(true);
        return value;
	}

	@Override
	public void setValue(ELContext context, Object base, Object property, Object val) {
		// NOOP.
	}

	@Override
	public boolean isReadOnly(ELContext context, Object base, Object property) {
		return true;
	}

	@Override
	public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
		return null;
	}

}
