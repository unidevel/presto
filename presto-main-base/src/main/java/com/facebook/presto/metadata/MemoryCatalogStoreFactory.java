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
import jakarta.inject.Inject;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Factory for creating MemoryCatalogStore instances.
 */
public class MemoryCatalogStoreFactory
        implements CatalogStoreFactory
{
    private static final String NAME = "memory";
    private final ConnectorManager connectorManager;

    @Inject
    public MemoryCatalogStoreFactory(ConnectorManager connectorManager)
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
        return new MemoryCatalogStore(connectorManager);
    }
}

// Made with Bob
