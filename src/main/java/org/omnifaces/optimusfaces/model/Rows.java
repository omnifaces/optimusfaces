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
package org.omnifaces.optimusfaces.model;

import static java.lang.String.format;

import java.io.Serializable;

import org.omnifaces.optimusfaces.model.PagedDataModel.RowDataLoader;
import org.omnifaces.optimusfaces.model.PagedDataModel.RowKeyGetter;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.Identifiable;

/**
 * How the rows of a {@link LazyPagedDataModel} are identified: the row key of an entity, whether the row key is the ID of the entity, and the entity of a row
 * key, which is <code>null</code> when not specified.
 *
 * @param <E> The entity type.
 * @param key The row key of an entity.
 * @param keyIsId Whether the row key is the ID of the entity, so that the entity of a row key can be loaded via the criteria <code>id</code>.
 * @param data The entity of a row key, or <code>null</code> when not specified.
 * @author Bauke Scholtz
 * @since 1.2
 */
record Rows<E>(RowKeyGetter<E> key, boolean keyIsId, RowDataLoader<E> data) implements Serializable {

    private static final String ERROR_NO_ROW_KEY = "%s does not extend " + BaseEntity.class.getName()
        + ", so build the model via PagedDataModel.lazy(EntityRepository), or set its row key via PagedDataModel.Builder#rowKey(), and preferably also its"
        + " row data via PagedDataModel.Builder#rowData()";

    static <E> Rows<E> of(RowKeyGetter<E> key, RowDataLoader<E> data) {
        return new Rows<>(key, false, data);
    }

    static <E> Rows<E> ofId(RowKeyGetter<E> idGetter, RowDataLoader<E> data) {
        return new Rows<>(idGetter, true, data);
    }

    /**
     * Returns the ID of the given entity when it extends {@link BaseEntity}, or implements the deprecated {@link Identifiable}.
     *
     * @param entity The entity.
     * @return The ID of the given entity.
     * @throws IllegalStateException When the given entity has no known ID.
     */
    @SuppressWarnings("removal")
    static Object getId(Object entity) {
        if (entity instanceof Identifiable<?> identifiable) {
            return identifiable.getId();
        }

        throw new IllegalStateException(format(ERROR_NO_ROW_KEY, entity.getClass().getName()));
    }

}
