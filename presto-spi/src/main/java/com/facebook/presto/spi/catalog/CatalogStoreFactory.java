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

import java.util.Map;

/**
 * Factory for creating CatalogStore instances.
 */
public interface CatalogStoreFactory
{
    /**
     * Returns the name of the catalog store type.
     */
    String getName();

    /**
     * Creates a new CatalogStore instance with the given properties.
     */
    CatalogStore create(Map<String, String> config);
}

// Made with Bob
