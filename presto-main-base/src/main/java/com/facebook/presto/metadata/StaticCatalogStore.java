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
package com.facebook.presto.metadata;

import com.facebook.airlift.log.Logger;
import com.facebook.presto.connector.ConnectorManager;
import com.facebook.presto.spi.catalog.CatalogProperties;
import com.facebook.presto.spi.catalog.CatalogStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jakarta.inject.Inject;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper around the CatalogStore interface that provides backward compatibility
 * with the static catalog loading mechanism.
 */
public class StaticCatalogStore
{
    private static final Logger log = Logger.get(StaticCatalogStore.class);
    private final CatalogStore catalogStore;

    @Inject
    public StaticCatalogStore(ConnectorManager connectorManager, StaticCatalogStoreConfig config)
    {
        // Use FileCatalogStore as the default implementation
        this(new FileCatalogStore(connectorManager, config));
    }

    public StaticCatalogStore(CatalogStore catalogStore)
    {
        this.catalogStore = requireNonNull(catalogStore, "catalogStore is null");
    }

    /**
     * Returns whether catalogs are loaded.
     */
    public boolean areCatalogsLoaded()
    {
        return catalogStore.areCatalogsLoaded();
    }

    /**
     * Loads catalogs from the store.
     */
    public void loadCatalogs()
            throws Exception
    {
        catalogStore.loadCatalogs();
    }

    /**
     * Loads catalogs from the store with additional catalogs.
     */
    public void loadCatalogs(Map<String, Map<String, String>> additionalCatalogs)
            throws Exception
    {
        // First load the regular catalogs
        catalogStore.loadCatalogs();
        
        // Then add the additional catalogs
        for (Map.Entry<String, Map<String, String>> entry : additionalCatalogs.entrySet()) {
            String catalogName = entry.getKey();
            Map<String, String> properties = entry.getValue();
            
            String connectorName = null;
            ImmutableMap.Builder<String, String> connectorProperties = ImmutableMap.builder();
            
            for (Map.Entry<String, String> prop : properties.entrySet()) {
                if (prop.getKey().equals("connector.name")) {
                    connectorName = prop.getValue();
                }
                else {
                    connectorProperties.put(prop.getKey(), prop.getValue());
                }
            }
            
            if (connectorName != null) {
                CatalogProperties catalogProperties = catalogStore.createCatalogProperties(
                        catalogName, connectorName, connectorProperties.build());
                catalogStore.addOrReplaceCatalog(catalogProperties);
            }
        }
    }
    
    /**
     * Gets a catalog by name.
     */
    public Optional<CatalogProperties> getCatalog(String catalogName)
    {
        return catalogStore.getCatalog(catalogName);
    }
    
    /**
     * Gets all catalogs.
     */
    public List<CatalogProperties> getCatalogs()
    {
        return catalogStore.getCatalogs();
    }
}
