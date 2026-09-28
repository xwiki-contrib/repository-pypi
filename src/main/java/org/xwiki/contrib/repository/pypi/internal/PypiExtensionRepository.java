/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.repository.pypi.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.client.protocol.HttpClientContext;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiSimpleProjectDto;
import org.xwiki.contrib.repository.pypi.internal.searching.PypiPackageIndex;
import org.xwiki.contrib.repository.pypi.internal.searching.PypiPackageListIndexUpdateTask;
import org.xwiki.contrib.repository.pypi.internal.searching.PypiPackageSearcher;
import org.xwiki.contrib.repository.pypi.internal.utils.PyPiHttpUtils;
import org.xwiki.contrib.repository.pypi.internal.utils.PypiUtils;
import org.xwiki.environment.Environment;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.AbstractExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.repository.search.SearchException;
import org.xwiki.extension.repository.search.Searchable;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.internal.DefaultVersion;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@Component(roles = PypiExtensionRepository.class)
@Singleton
public class PypiExtensionRepository extends AbstractExtensionRepository
    implements Searchable, Initializable, Disposable
{
    private static final String EMBEDDED_INDEX = "/pypiIndex/pypi-index-20260928.zip";

    private static final String INDEX_ENTRY = "index.txt";

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private ExtensionLicenseManager licenseManager;

    @Inject
    private HttpClientFactory httpClientFactory;

    @Inject
    private Environment environment;

    @Inject
    private Logger logger;

    private HttpClientContext localContext;

    private Timer timer;

    private PypiPackageIndex packageIndex;

    /**
     * @param extensionRepositoryDescriptor -
     * @return -
     */
    public PypiExtensionRepository setUpRepository(ExtensionRepositoryDescriptor extensionRepositoryDescriptor)
    {
        setDescriptor(extensionRepositoryDescriptor);
        this.localContext = HttpClientContext.create();
        return this;
    }

    @Override
    public void initialize() throws InitializationException
    {
        initializePackageIndex();
        timer = new Timer();
        PypiPackageListIndexUpdateTask pypiPackageListIndexUpdateTask =
            new PypiPackageListIndexUpdateTask(this.packageIndex, httpClientFactory, logger);

        // TODO: make the period configurable
        long period = 1000L * 60L * 60L * 12L;
        // Run the first one right away since there is a good chance the index is not up to date
        timer.schedule(pypiPackageListIndexUpdateTask, 0, period);
    }

    private void initializePackageIndex() throws InitializationException
    {
        this.packageIndex =
            new PypiPackageIndex(new File(environment.getPermanentDirectory(), "cache/pypi-index"), logger);
        this.packageIndex.initialize();

        // If no index can be found use the default embedded one
        if (this.packageIndex.getFile() == null) {
            try (InputStream stream = getClass().getResourceAsStream(EMBEDDED_INDEX)) {
                importIndex(stream);
            } catch (IOException e) {
                throw new InitializationException("Could not read the embedded PyPI package index", e);
            }

            if (this.packageIndex.getFile() == null) {
                throw new InitializationException("Could not copy the embedded PyPI package index");
            }
        }
    }

    @Override
    public void dispose() throws ComponentLifecycleException
    {
        timer.cancel();
        timer.purge();
    }

    @Override
    public Extension resolve(ExtensionId extensionId) throws ResolveException
    {
        String packageName = PypiUtils.getPackageName(extensionId);
        Optional<String> version = PypiUtils.getVersion(extensionId);
        return getPythonPackageExtension(packageName, version);
    }

    public PypiExtension getPythonPackageExtension(String packageName, Optional<String> version) throws ResolveException
    {
        return resolvePythonPackageExtension(packageName, version);
    }

    private PypiExtension resolvePythonPackageExtension(String packageName, Optional<String> version)
        throws ResolveException
    {
        try {
            PypiPackageJSONDto pypiPackageData = getPypiPackageData(packageName, version);
            return PypiExtension.constructFrom(pypiPackageData, this, licenseManager, httpClientFactory);
        } catch (HttpException e) {
            throw new ResolveException("Failed to resolve package [" + packageName + "]", e);
        }
    }

    @Override
    public Extension resolve(ExtensionDependency extensionDependency) throws ResolveException
    {
        String id = extensionDependency.getId();
        String version = extensionDependency.getVersionConstraint().getVersion().getValue();
        ExtensionId extensionId = new ExtensionId(id, version);
        try {
            return resolve(extensionId);
        } catch (ResolveException e) {
            // if there's no resolvable dependency in given version check the newest
            return getPythonPackageExtension(PypiUtils.getPackageName(extensionId), Optional.empty());
        }
    }

    @Override
    public IterableResult<Version> resolveVersions(String packageName, int offset, int nb) throws ResolveException
    {
        String pypiPackage = PypiUtils.getPackageName(packageName);

        try {
            PypiSimpleProjectDto projectData = getPypiSimpleProjectData(pypiPackage);
            List<Version> versions = projectData.getVersions() != null ? projectData.getVersions().stream()
                .map(releaseVersion -> new DefaultVersion(releaseVersion)).collect(Collectors.toList())
                : Collections.emptyList();

            if (versions.isEmpty()) {
                throw new ExtensionNotFoundException(
                    "No versions available for id [" + packageName + " (" + pypiPackage + ")]");
            }

            if (nb == 0 || offset >= versions.size()) {
                return new CollectionIterableResult<>(versions.size(), offset, Collections.<Version>emptyList());
            }

            int fromId = offset < 0 ? 0 : offset;
            int toId = offset + nb > versions.size() || nb < 0 ? versions.size() : offset + nb;

            List<Version> result = new ArrayList<>(toId - fromId);
            for (int i = fromId; i < toId; ++i) {
                result.add(versions.get(i));
            }

            return new CollectionIterableResult<>(versions.size(), offset, result);
        } catch (HttpException e) {
            throw new ResolveException("Failed to resolve package [" + packageName + " (" + pypiPackage + ")]", e);
        }
    }

    /**
     * @param packageName -
     * @param version -
     * @return -
     * @throws HttpException -
     * @throws ExtensionNotFoundException
     */
    public PypiPackageJSONDto getPypiPackageData(String packageName, Optional<String> version)
        throws HttpException, ExtensionNotFoundException
    {
        String uri;
        if (version.isPresent()) {
            uri = PypiParameters.PACKAGE_VERSION_INFO_JSON.replace("{package_name}", packageName).replace("{version}",
                version.get());
        } else {
            uri = PypiParameters.PACKAGE_INFO_JSON.replace("{package_name}", packageName);
        }

        return get(uri, null, PypiPackageJSONDto.class, packageName);
    }

    /**
     * @param packageName the name of the package
     * @return the JSON form of the Simple API page of the package
     * @throws HttpException when failing to get the package data
     * @throws ExtensionNotFoundException when the package does not exist
     * @since 1.1.5
     */
    public PypiSimpleProjectDto getPypiSimpleProjectData(String packageName)
        throws HttpException, ExtensionNotFoundException
    {
        return get(PypiParameters.PACKAGE_SIMPLE_API.replace("{package_name}", packageName),
            PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE, PypiSimpleProjectDto.class, packageName);
    }

    private <T> T get(String uriString, String accept, Class<T> type, String packageName)
        throws HttpException, ExtensionNotFoundException
    {
        URI uri;
        try {
            uri = new URI(uriString);
        } catch (URISyntaxException e) {
            throw new HttpException(String.format("Invalid URI [%s] for resolving package info", uriString), e);
        }

        try (InputStream inputStream = PyPiHttpUtils.performGet(uri, accept, httpClientFactory, localContext)) {
            if (inputStream == null) {
                throw new ExtensionNotFoundException("Cannot find package with id [" + packageName + "] on pypi");
            }

            return objectMapper.readValue(inputStream, type);
        } catch (IOException e) {
            throw new HttpException(String.format("Failed to parse response body of request [%s]", uri), e);
        }
    }

    @Override
    public IterableResult<Extension> search(String searchQuery, int offset, int hitsPerPage) throws SearchException
    {
        try {
            PypiPackageSearcher searcher = this.packageIndex.getSearcher();
            if (searcher != null) {
                IterableResult<String> packageNames = searcher.search(searchQuery, offset, hitsPerPage);
                return toExtensions(packageNames);
            }
        } catch (IOException e) {
            throw new SearchException("Failed to search the PyPI package index", e);
        }
        return new CollectionIterableResult<>(0, 0, Collections.emptyList());
    }

    private IterableResult<Extension> toExtensions(IterableResult<String> packageNames)
    {
        LinkedList<Extension> extensions = new LinkedList<>();
        packageNames.iterator().forEachRemaining(packageName -> {
            try {
                PypiExtension pythonPackageExtension = getPythonPackageExtension(packageName, Optional.empty());
                extensions.add(pythonPackageExtension);
            } catch (ResolveException e) {
                logger.debug("Could not resolve extension that is present in the index: " + packageName, e);
            }
        });

        return new CollectionIterableResult<>(packageNames.getTotalHits(), packageNames.getOffset(), extensions);
    }

    private String getIndexFileName()
    {
        return "pypi-index-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".zip";
    }

    public void exportIndex(File output) throws IOException
    {
        File outputFile = output;
        if (outputFile == null) {
            outputFile = new File(this.environment.getPermanentDirectory(), getIndexFileName());
        } else if (outputFile.exists()) {
            if (outputFile.isDirectory()) {
                outputFile = new File(outputFile, getIndexFileName());
            }
        } else if (outputFile.getName().endsWith(".zip")) {
            outputFile.getParentFile().mkdirs();
        } else {
            outputFile.mkdirs();

            outputFile = new File(outputFile, getIndexFileName());
        }

        File indexFile = this.packageIndex.getFile();
        if (indexFile != null) {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
                zip.putNextEntry(new ZipEntry(INDEX_ENTRY));
                Files.copy(indexFile.toPath(), zip);
                zip.closeEntry();
            }
        }
    }

    /**
     * Replace the current index with the one contained in the provided zip. Each line of the index contains a package
     * name, possibly followed by a tab and the version of the package in indexes exported by older versions.
     *
     * @param inputFile the zip containing the index
     */
    public void importIndex(InputStream inputFile)
    {
        this.packageIndex.update(consumer -> {
            ZipInputStream zip = new ZipInputStream(inputFile);
            if (zip.getNextEntry() == null) {
                throw new IOException("The index zip is empty");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String packageName = StringUtils.substringBefore(line, "\t");

                if (!packageName.isEmpty()) {
                    consumer.accept(packageName);
                }
            }
        });
    }
}
