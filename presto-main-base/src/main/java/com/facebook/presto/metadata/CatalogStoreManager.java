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
import com.facebook.presto.spi.catalog.CatalogProperties;
import com.facebook.presto.spi.catalog.CatalogStore;
import com.facebook.presto.spi.catalog.CatalogStoreFactory;
import com.google.common.collect.ImmutableMap;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Manages catalog store implementations.
 */
public class CatalogStoreManager
        implements CatalogStore
{
    private static final Logger log = Logger.get(CatalogStoreManager.class);
    private final Map<String, CatalogStoreFactory> catalogStoreFactories = new ConcurrentHashMap<>();
    private final CatalogStoreConfig config;
    private CatalogStore catalogStore;

    @Inject
    public CatalogStoreManager(
            Set<CatalogStoreFactory> catalogStoreFactories,
            CatalogStoreConfig config)
    {
        requireNonNull(catalogStoreFactories, "catalogStoreFactories is null");
        this.config = requireNonNull(config, "config is null");

        for (CatalogStoreFactory factory : catalogStoreFactories) {
            addCatalogStoreFactory(factory);
        }
    }

    public void addCatalogStoreFactory(CatalogStoreFactory factory)
    {
        requireNonNull(factory, "factory is null");
        if (catalogStoreFactories.putIfAbsent(factory.getName(), factory) != null) {
            throw new IllegalArgumentException(String.format("Catalog store '%s' is already registered", factory.getName()));
        }
    }

    public void loadConfiguredCatalogStore()
    {
        if (catalogStore != null) {
            return;
        }

        String storeName = config.getCatalogStore();
        CatalogStoreFactory factory = catalogStoreFactories.get(storeName);
        if (factory == null) {
            throw new IllegalStateException(String.format("Catalog store '%s' is not registered", storeName));
        }

        Map<String, String> properties = new HashMap<>(config.getProperties());
        catalogStore = factory.create(ImmutableMap.copyOf(properties));
        log.info("-- Loaded catalog store '%s' --", storeName);
    }

    @Override
    public boolean areCatalogsLoaded()
    {
        loadConfiguredCatalogStore();
        return catalogStore.areCatalogsLoaded();
    }

    @Override
    public void loadCatalogs()
            throws Exception
    {
        loadConfiguredCatalogStore();
        catalogStore.loadCatalogs();
    }

    @Override
    public List<CatalogProperties> getCatalogs()
    {
        loadConfiguredCatalogStore();
        return catalogStore.getCatalogs();
    }

    @Override
    public Optional<CatalogProperties> getCatalog(String catalogName)
    {
        loadConfiguredCatalogStore();
        return catalogStore.getCatalog(catalogName);
    }

    @Override
    public CatalogProperties createCatalogProperties(String catalogName, String connectorName, Map<String, String> properties)
    {
        loadConfiguredCatalogStore();
        return catalogStore.createCatalogProperties(catalogName, connectorName, properties);
    }

    @Override
    public void addOrReplaceCatalog(CatalogProperties catalogProperties)
    {
        loadConfiguredCatalogStore();
        catalogStore.addOrReplaceCatalog(catalogProperties);
    }

    @Override
    public void removeCatalog(String catalogName)
    {
        loadConfiguredCatalogStore();
        catalogStore.removeCatalog(catalogName);
    }

    public CatalogStore getCatalogStore()
    {
        loadConfiguredCatalogStore();
        return catalogStore;
    }
}

// Made with Bob
