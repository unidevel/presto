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

import com.facebook.airlift.configuration.AbstractConfigurationAwareModule;
import com.facebook.presto.spi.catalog.CatalogStore;
import com.facebook.presto.spi.catalog.CatalogStoreFactory;
import com.google.inject.Binder;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

/**
 * Module for binding catalog store components.
 */
public class CatalogStoreModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        // Bind configuration
        binder.bind(CatalogStoreConfig.class).in(Scopes.SINGLETON);
        
        // Bind CatalogStoreManager as the CatalogStore implementation
        binder.bind(CatalogStore.class).to(CatalogStoreManager.class).in(Scopes.SINGLETON);
        binder.bind(CatalogStoreManager.class).in(Scopes.SINGLETON);
        
        // Create a multibinder for CatalogStoreFactory implementations
        Multibinder<CatalogStoreFactory> catalogStoreFactoryBinder = Multibinder.newSetBinder(binder, CatalogStoreFactory.class);
        
        // Bind the factory implementations
        catalogStoreFactoryBinder.addBinding().to(FileCatalogStoreFactory.class).in(Scopes.SINGLETON);
        catalogStoreFactoryBinder.addBinding().to(MemoryCatalogStoreFactory.class).in(Scopes.SINGLETON);
        
        // Bind StaticCatalogStore for backward compatibility
        binder.bind(StaticCatalogStore.class).in(Scopes.SINGLETON);
    }
}

// Made with Bob
