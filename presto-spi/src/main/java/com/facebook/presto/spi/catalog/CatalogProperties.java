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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents catalog properties.
 */
public class CatalogProperties
{
    private final String catalogName;
    private final String connectorName;
    private final Map<String, String> properties;

    public CatalogProperties(String catalogName, String connectorName, Map<String, String> properties)
    {
        this.catalogName = Objects.requireNonNull(catalogName, "catalogName is null");
        this.connectorName = Objects.requireNonNull(connectorName, "connectorName is null");
        this.properties = Collections.unmodifiableMap(Objects.requireNonNull(properties, "properties is null"));
    }

    public String getCatalogName()
    {
        return catalogName;
    }

    public String getConnectorName()
    {
        return connectorName;
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CatalogProperties that = (CatalogProperties) o;
        return Objects.equals(catalogName, that.catalogName) &&
                Objects.equals(connectorName, that.connectorName) &&
                Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(catalogName, connectorName, properties);
    }

    @Override
    public String toString()
    {
        return "CatalogProperties{" +
                "catalogName='" + catalogName + '\'' +
                ", connectorName='" + connectorName + '\'' +
                ", properties=" + properties +
                '}';
    }
}

// Made with Bob
