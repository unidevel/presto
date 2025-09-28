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

import com.facebook.presto.connector.ConnectorManager;
import com.facebook.presto.spi.catalog.CatalogStore;
import com.facebook.presto.spi.catalog.CatalogStoreFactory;
import com.google.common.collect.ImmutableList;
import jakarta.inject.Inject;

import java.io.File;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Factory for creating FileCatalogStore instances.
 */
public class FileCatalogStoreFactory
        implements CatalogStoreFactory
{
    private static final String NAME = "file";
    private final ConnectorManager connectorManager;

    @Inject
    public FileCatalogStoreFactory(ConnectorManager connectorManager)
    {
        this.connectorManager = requireNonNull(connectorManager, "connectorManager is null");
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public CatalogStore create(Map<String, String> config)
    {
        requireNonNull(config, "config is null");
        
        String catalogDir = config.getOrDefault("catalog.config-dir", "etc/catalog/");
        String disabledCatalogsStr = config.get("catalog.disabled-catalogs");
        
        File catalogConfigurationDir = new File(catalogDir);
        ImmutableList<String> disabledCatalogs = disabledCatalogsStr == null ? 
                ImmutableList.of() : 
                ImmutableList.copyOf(disabledCatalogsStr.split(","));
        
        return new FileCatalogStore(connectorManager, catalogConfigurationDir, disabledCatalogs);
    }
}

// Made with Bob
