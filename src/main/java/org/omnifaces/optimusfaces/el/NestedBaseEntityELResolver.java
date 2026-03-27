/*
 * Copyright OmniFaces
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

import jakarta.el.ELContext;
import jakarta.el.ELResolver;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * <p>
 * EL resolver that transparently handles dot-notated property paths (e.g. <code>address.street</code>) when the base object is a {@link BaseEntity}. Registered
 * automatically via the bundled <code>faces-config.xml</code>.
 * <p>
 * The standard Jakarta Faces EL resolver chain treats an expression property name such as <code>#{item["address.street"]}</code> as a single opaque key. This
 * resolver intercepts such requests on {@link BaseEntity} instances, splits the property string on <code>'.'</code> and resolves each segment in turn by
 * delegating back to the full {@link ELContext#getELResolver() EL resolver chain}, so that every intermediate value benefits from other custom resolvers (e.g.
 * proxy-aware or lazy-loaded resolvers).
 * <p>
 * {@link java.util.Collection} values encountered at any hop in the chain are automatically expanded: the remaining path is mapped over every element and the
 * results are collected into a {@link java.util.List}.
 * <p>
 * When <code>base</code> is not a {@link BaseEntity}, or the property string contains no dot, this resolver returns <code>null</code> without calling
 * {@link ELContext#setPropertyResolved(boolean)}, allowing the runtime to continue down the resolver chain.
 *
 * @see BaseEntity
 * @author Bauke Scholtz
 * @since 1.0
 */
public class NestedBaseEntityELResolver extends ELResolver {

    /**
     * Returns <code>null</code>; this resolver does not advertise a common property type.
     * 
     * @param context The EL context.
     * @param base The base object.
     * @return <code>null</code>.
     */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }

    /**
     * Returns <code>null</code>; type information is delegated to downstream resolvers.
     * 
     * @param context The EL context.
     * @param base The base object.
     * @param property The property name.
     * @return <code>null</code>.
     */
    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    /**
     * <p>
     * Resolves a dot-notated property path against a {@link BaseEntity}.
     * <p>
     * Returns <code>null</code> and leaves {@link ELContext#isPropertyResolved()} unchanged when <code>base</code> is not a {@link BaseEntity} or when
     * <code>property</code> contains no dot, signalling to the runtime that it should continue down the resolver chain.
     * <p>
     * Otherwise the path is split on <code>'.'</code> and each segment is resolved via {@link ELContext#getELResolver()}. Intermediate
     * {@link java.util.Collection} values are expanded by mapping the remaining path over every element. After full resolution,
     * {@link ELContext#setPropertyResolved(boolean) context.setPropertyResolved(true)} is called.
     * 
     * @param context The EL context.
     * @param base The base object; only {@link BaseEntity} instances are handled.
     * @param property The property name; must contain at least one dot to activate this resolver.
     * @return The resolved value, or <code>null</code> if this resolver does not handle the request.
     */
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

    /**
     * No-op; properties resolved through this resolver are read-only.
     * 
     * @param context The EL context.
     * @param base The base object.
     * @param property The property name.
     * @param val The value to set (ignored).
     */
    @Override
    public void setValue(ELContext context, Object base, Object property, Object val) {
        // NOOP.
    }

    /**
     * Returns <code>true</code>; all properties resolved through this resolver are read-only.
     * 
     * @param context The EL context.
     * @param base The base object.
     * @param property The property name.
     * @return <code>true</code>.
     */
    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    /**
     * Returns <code>null</code>; this resolver does not enumerate feature descriptors.
     * 
     * @param context The EL context.
     * @param base The base object.
     * @return <code>null</code>.
     */
    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

}
