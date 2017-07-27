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
package org.xwiki.contrib.repository.pypi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.searching.PypiPackageListIndexUpdateTask;
import org.xwiki.contrib.repository.pypi.searching.PypiPackageSearcher;
import org.xwiki.contrib.repository.pypi.utils.PyPiHttpUtils;
import org.xwiki.contrib.repository.pypi.utils.PypiUtils;
import org.xwiki.contrib.repository.pypi.utils.ZipUtils;
import org.xwiki.environment.Environment;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.internal.ExtensionFactory;
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
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private ExtensionLicenseManager licenseManager;

    @Inject
    private ExtensionFactory extensionFactory;

    @Inject
    private HttpClientFactory httpClientFactory;

    @Inject
    private Environment environment;

    @Inject
    private Logger logger;

    private HttpClientContext localContext;

    private Timer timer;

    private AtomicReference<File> pypiPackageListIndexDirectory = null;

    private PypiPackageSearcher packageSearcher;

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

    @Override public void initialize() throws InitializationException
    {
        initializePackageListIndexDirectory();
        timer = new Timer();
        PypiPackageListIndexUpdateTask pypiPackageListIndexUpdateTask =
                new PypiPackageListIndexUpdateTask(pypiPackageListIndexDirectory, this, environment, httpClientFactory,
                        logger);
        long interval = 1000 * 60 * 60 * 12;
        // TODO: 24.07.2017 you may put this update interval in configuration
        timer.schedule(pypiPackageListIndexUpdateTask, interval, interval);
    }

    private void initializePackageListIndexDirectory() throws InitializationException
    {
        try {
            URL inputUrl = getClass().getResource("/luceneIndexOfValidPackages/index.zip");
            File zipFile =
                    new File(environment.getTemporaryDirectory().getAbsolutePath() + File.separator + "index.zip");
            zipFile.createNewFile();
            FileUtils.copyURLToFile(inputUrl, zipFile);

            File indexDir = environment.getTemporaryDirectory();
            ZipUtils.unpack(zipFile, indexDir);
            FileUtils.forceDelete(zipFile);
            pypiPackageListIndexDirectory = new AtomicReference<>(indexDir);
        } catch (Exception e) {
            throw new InitializationException("Could not copy lucene index to local directory", e);
        }
    }

    @Override public void dispose() throws ComponentLifecycleException
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
            //if there's no resolvable dependency in given version check the newest
            return getPythonPackageExtension(PypiUtils.getPackageName(extensionId), Optional.empty());
        }
    }

    @Override
    public IterableResult<Version> resolveVersions(String packageName, int offset, int nb) throws ResolveException
    {
        try {
            PypiPackageJSONDto pypiPackageData = getPypiPackageData(packageName, Optional.empty());
            List<Version> versions = pypiPackageData.getAvailableReleaseVersions().stream()
                    .map(releaseVersion -> new DefaultVersion(releaseVersion)).collect(Collectors.toList());

            if (versions.isEmpty()) {
                throw new ExtensionNotFoundException("No versions available for id [" + packageName + "]");
            }

            if (nb == 0 || offset >= versions.size()) {
                return new CollectionIterableResult<Version>(versions.size(), offset, Collections.<Version>emptyList());
            }

            int fromId = offset < 0 ? 0 : offset;
            int toId = offset + nb > versions.size() || nb < 0 ? versions.size() : offset + nb;

            List<Version> result = new ArrayList<Version>(toId - fromId);
            for (int i = fromId; i < toId; ++i) {
                result.add(versions.get(i));
            }

            return new CollectionIterableResult<>(versions.size(), offset, result);
        } catch (HttpException e) {
            throw new ResolveException("Failed to resolve package [" + packageName + "]", e);
        }
    }

    /**
     *
     * @param packageName -
     * @param version -
     * @return -
     * @throws HttpException -
     */
    public PypiPackageJSONDto getPypiPackageData(String packageName, Optional<String> version)
            throws HttpException
    {
        URI uri = null;
        try {
            if (version.isPresent()) {
                uri = new URI(
                        PypiParameters.PACKAGE_VERSION_INFO_JSON.replace("{package_name}", packageName)
                                .replace("{version}", version.get()));
            } else {
                uri = new URI(PypiParameters.PACKAGE_INFO_JSON.replace("{package_name}", packageName));
            }
        } catch (URISyntaxException e) {
            new HttpException("Problem with created URI for resolving package info", e);
        }

        InputStream inputStream = PyPiHttpUtils.performGet(uri, httpClientFactory, localContext);

        try {
            return objectMapper.readValue(inputStream, PypiPackageJSONDto.class);
        } catch (IOException e) {
            throw new HttpException(String.format("Failed to parse response body of request [%s]",
                    uri), e);
        }
    }

    @Override public IterableResult<Extension> search(String searchQuery, int offset, int hitsPerPage)
            throws SearchException
    {
        PypiPackageSearcher searcher = getPypiPackageSearcher();
        try {
            IterableResult<String> packageNames = searcher.search(searchQuery, offset, hitsPerPage);
            return toExtensions(packageNames);
        } catch (ParseException e) {
            logger.debug("Lucene query parser unable to parse query: " + searchQuery, e);
        } catch (IOException e) {
            logger.error("Lucene index searcher search exception", e);
        }
        return new CollectionIterableResult(0, 0, Collections.emptyList());
    }

    private IterableResult<Extension> toExtensions(IterableResult<String> packageNames)
    {
        LinkedList<Extension> extensions = new LinkedList<>();
        packageNames.iterator().forEachRemaining(packageName -> {
            try {
                PypiExtension pythonPackageExtension = getPythonPackageExtension(packageName, Optional.empty());
                extensions.add(pythonPackageExtension);
            } catch (ResolveException e) {
                logger.debug("Could nor resolve extension that is present in lucene index: " + packageName, e);
            }
        });

        return new CollectionIterableResult(packageNames.getTotalHits(), packageNames.getOffset(), extensions);
    }

    private PypiPackageSearcher getPypiPackageSearcher()
    {
        if (packageSearcher == null || hasPackageListIndexChanged()) {
            try {
                packageSearcher = new PypiPackageSearcher(pypiPackageListIndexDirectory.get(), logger);
            } catch (IOException e) {
                logger.error("Could not open lucene package list index from directory " + pypiPackageListIndexDirectory
                        .get(), e);
            }
        }
        return packageSearcher;
    }

    private boolean hasPackageListIndexChanged()
    {
        return !packageSearcher.getIndexDirectoryFile().equals(pypiPackageListIndexDirectory.get());
    }
}
