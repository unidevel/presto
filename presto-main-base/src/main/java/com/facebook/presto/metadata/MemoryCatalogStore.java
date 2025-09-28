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
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * An in-memory implementation of CatalogStore.
 */
public class MemoryCatalogStore
        implements CatalogStore
{
    private static final Logger log = Logger.get(MemoryCatalogStore.class);
    private final ConnectorManager connectorManager;
    private final Map<String, CatalogProperties> catalogs = new ConcurrentHashMap<>();
    private final AtomicBoolean catalogsLoaded = new AtomicBoolean();

    @Inject
    public MemoryCatalogStore(ConnectorManager connectorManager)
    {
        this.connectorManager = requireNonNull(connectorManager, "connectorManager is null");
    }

    @Override
    public boolean areCatalogsLoaded()
    {
        return catalogsLoaded.get();
    }

    @Override
    public void loadCatalogs()
            throws Exception
    {
        // Memory store starts with no catalogs
        catalogsLoaded.set(true);
    }

    @Override
    public List<CatalogProperties> getCatalogs()
    {
        return ImmutableList.copyOf(catalogs.values());
    }

    @Override
    public Optional<CatalogProperties> getCatalog(String catalogName)
    {
        requireNonNull(catalogName, "catalogName is null");
        return Optional.ofNullable(catalogs.get(catalogName));
    }

    @Override
    public CatalogProperties createCatalogProperties(String catalogName, String connectorName, Map<String, String> properties)
    {
        requireNonNull(catalogName, "catalogName is null");
        requireNonNull(connectorName, "connectorName is null");
        requireNonNull(properties, "properties is null");
        
        return new CatalogProperties(catalogName, connectorName, properties);
    }

    @Override
    public void addOrReplaceCatalog(CatalogProperties catalogProperties)
    {
        requireNonNull(catalogProperties, "catalogProperties is null");
        
        String catalogName = catalogProperties.getCatalogName();
        String connectorName = catalogProperties.getConnectorName();
        Map<String, String> properties = catalogProperties.getProperties();
        
        catalogs.put(catalogName, catalogProperties);
        
        // Create the actual connector
        connectorManager.createConnection(catalogName, connectorName, properties);
        log.info("-- Added catalog %s using connector %s --", catalogName, connectorName);
    }

    @Override
    public void removeCatalog(String catalogName)
    {
        requireNonNull(catalogName, "catalogName is null");
        CatalogProperties removed = catalogs.remove(catalogName);
        if (removed != null) {
            log.info("-- Removed catalog %s --", catalogName);
        }
    }
}

// Made with Bob
