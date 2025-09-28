/*
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
package com.facebook.presto.spi.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SPI for catalog storage backends.
 * <p>
 * This interface defines methods for storing and retrieving catalog configurations.
 * Implementations can use different storage backends like files, etcd, memory, etc.
 */
public interface CatalogStore
{
    /**
     * Returns all stored catalogs.
     */
    List<CatalogProperties> getCatalogs();

    /**
     * Returns a catalog by name.
     */
    Optional<CatalogProperties> getCatalog(String catalogName);

    /**
     * Creates catalog properties from raw properties.
     */
    CatalogProperties createCatalogProperties(String catalogName, String connectorName, Map<String, String> properties);

    /**
     * Adds or replaces a catalog in the store.
     */
    void addOrReplaceCatalog(CatalogProperties catalogProperties);

    /**
     * Removes a catalog from the store.
     */
    void removeCatalog(String catalogName);

    /**
     * Loads all catalogs from the store.
     */
    void loadCatalogs() throws Exception;

    /**
     * Checks if catalogs are loaded.
     */
    boolean areCatalogsLoaded();
}

// Made with Bob
