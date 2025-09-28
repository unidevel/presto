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
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import jakarta.inject.Inject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.facebook.presto.util.PropertiesUtil.loadProperties;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class FileCatalogStore
        implements CatalogStore
{
    private static final Logger log = Logger.get(FileCatalogStore.class);
    private final ConnectorManager connectorManager;
    private final File catalogConfigurationDir;
    private final Set<String> disabledCatalogs;
    private final AtomicBoolean catalogsLoading = new AtomicBoolean();
    private final AtomicBoolean catalogsLoaded = new AtomicBoolean();
    private final Map<String, CatalogProperties> catalogs = new ConcurrentHashMap<>();

    @Inject
    public FileCatalogStore(ConnectorManager connectorManager, StaticCatalogStoreConfig config)
    {
        this(connectorManager,
                config.getCatalogConfigurationDir(),
                firstNonNull(config.getDisabledCatalogs(), ImmutableList.of()));
    }

    public FileCatalogStore(ConnectorManager connectorManager, File catalogConfigurationDir, List<String> disabledCatalogs)
    {
        this.connectorManager = requireNonNull(connectorManager, "connectorManager is null");
        this.catalogConfigurationDir = requireNonNull(catalogConfigurationDir, "catalogConfigurationDir is null");
        this.disabledCatalogs = ImmutableSet.copyOf(requireNonNull(disabledCatalogs, "disabledCatalogs is null"));
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
        loadCatalogs(ImmutableMap.of());
    }

    public void loadCatalogs(Map<String, Map<String, String>> additionalCatalogs)
            throws Exception
    {
        if (!catalogsLoading.compareAndSet(false, true)) {
            return;
        }

        for (File file : listFiles(catalogConfigurationDir)) {
            if (file.isFile() && file.getName().endsWith(".properties")) {
                loadCatalog(file);
            }
        }

        additionalCatalogs.forEach(this::loadCatalog);

        catalogsLoaded.set(true);
    }

    private void loadCatalog(File file)
            throws Exception
    {
        String catalogName = Files.getNameWithoutExtension(file.getName());

        log.info("-- Loading catalog properties %s --", file);
        Map<String, String> properties = loadProperties(file);
        checkState(properties.containsKey("connector.name"), "Catalog configuration %s does not contain connector.name", file.getAbsoluteFile());

        loadCatalog(catalogName, properties);
    }

    private void loadCatalog(String catalogName, Map<String, String> properties)
    {
        if (disabledCatalogs.contains(catalogName)) {
            log.info("Skipping disabled catalog %s", catalogName);
            return;
        }

        log.info("-- Loading catalog %s --", catalogName);

        String connectorName = null;
        ImmutableMap.Builder<String, String> connectorProperties = ImmutableMap.builder();
        for (Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().equals("connector.name")) {
                connectorName = entry.getValue();
            }
            else {
                connectorProperties.put(entry.getKey(), entry.getValue());
            }
        }

        checkState(connectorName != null, "Configuration for catalog %s does not contain connector.name", catalogName);

        CatalogProperties catalogProperties = createCatalogProperties(catalogName, connectorName, connectorProperties.build());
        addOrReplaceCatalog(catalogProperties);
        
        connectorManager.createConnection(catalogName, connectorName, connectorProperties.build());
        log.info("-- Added catalog %s using connector %s --", catalogName, connectorName);
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
        catalogs.put(catalogProperties.getCatalogName(), catalogProperties);
    }

    @Override
    public void removeCatalog(String catalogName)
    {
        requireNonNull(catalogName, "catalogName is null");
        catalogs.remove(catalogName);
    }

    private static List<File> listFiles(File installedPluginsDir)
    {
        if (installedPluginsDir != null && installedPluginsDir.isDirectory()) {
            File[] files = installedPluginsDir.listFiles();
            if (files != null) {
                return ImmutableList.copyOf(files);
            }
        }
        return ImmutableList.of();
    }
}

// Made with Bob
