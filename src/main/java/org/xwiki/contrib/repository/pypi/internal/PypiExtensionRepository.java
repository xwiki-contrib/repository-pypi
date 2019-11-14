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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.internal.searching.LuceneParameters;
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

    @Override
    public void initialize() throws InitializationException
    {
        initializePackageListIndexDirectory();
        timer = new Timer();
        PypiPackageListIndexUpdateTask pypiPackageListIndexUpdateTask = new PypiPackageListIndexUpdateTask(
            pypiPackageListIndexDirectory, this, environment, httpClientFactory, logger);

        // TODO: make the period configurable
        long period = 1000L * 60L * 60L * 12L;
        // Run the first one right away since there is a good chance the index is not up to date
        timer.schedule(pypiPackageListIndexUpdateTask, 0, period);
    }

    private void initializePackageListIndexDirectory() throws InitializationException
    {
        File indexParent = new File(environment.getPermanentDirectory(), "cache/pypi-index");

        pypiPackageListIndexDirectory = new AtomicReference<>();

        // Find the most recent index
        if (indexParent.exists()) {
            for (File child : indexParent.listFiles()) {
                if (child.isDirectory() && (pypiPackageListIndexDirectory.get() == null
                    || child.lastModified() > pypiPackageListIndexDirectory.get().lastModified())) {
                    // Remember current valid index
                    File currentDirectory = pypiPackageListIndexDirectory.get();

                    // Check new index
                    pypiPackageListIndexDirectory.set(child);
                    if (getPypiPackageSearcher() == null) {
                        logger.info("Deleting bad index [{}]", pypiPackageListIndexDirectory.get());

                        try {
                            FileUtils.forceDelete(pypiPackageListIndexDirectory.get());
                        } catch (IOException e) {
                            logger.error("Failed to delete index [{}]", pypiPackageListIndexDirectory.get());
                        }

                        // Put back previous valid index
                        pypiPackageListIndexDirectory.set(currentDirectory);
                    }
                }
            }
        }

        // If no index can be found the the default embedded one
        if (pypiPackageListIndexDirectory.get() == null) {
            try {
                importIndex(getClass().getResourceAsStream("/luceneIndexOfValidPackages/pypi-index-20191114.zip"));
            } catch (Exception e) {
                throw new InitializationException("Could not copy lucene index to local directory", e);
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
            PypiPackageJSONDto pypiPackageData = getPypiPackageData(pypiPackage, Optional.empty());
            List<Version> versions = pypiPackageData.getAvailableReleaseVersions().stream()
                .map(releaseVersion -> new DefaultVersion(releaseVersion)).collect(Collectors.toList());

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
        URI uri = null;
        try {
            if (version.isPresent()) {
                uri = new URI(PypiParameters.PACKAGE_VERSION_INFO_JSON.replace("{package_name}", packageName)
                    .replace("{version}", version.get()));
            } else {
                uri = new URI(PypiParameters.PACKAGE_INFO_JSON.replace("{package_name}", packageName));
            }
        } catch (URISyntaxException e) {
            new HttpException("Problem with created URI for resolving package info", e);
        }

        InputStream inputStream = PyPiHttpUtils.performGet(uri, httpClientFactory, localContext);

        if (inputStream == null) {
            throw new ExtensionNotFoundException("Cannot find package with id [" + packageName + "] on pypi");
        }

        try {
            return objectMapper.readValue(inputStream, PypiPackageJSONDto.class);
        } catch (IOException e) {
            throw new HttpException(String.format("Failed to parse response body of request [%s]", uri), e);
        }
    }

    @Override
    public IterableResult<Extension> search(String searchQuery, int offset, int hitsPerPage) throws SearchException
    {
        try {
            PypiPackageSearcher searcher = getPypiPackageSearcher();
            if (searcher != null) {
                IterableResult<String> packageNames = searcher.search(searchQuery, offset, hitsPerPage);
                return toExtensions(packageNames);
            }
        } catch (Exception e) {
            logger.error("Lucene index searcher search exception", e);
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
                logger.debug("Could nor resolve extension that is present in lucene index: " + packageName, e);
            }
        });

        return new CollectionIterableResult<>(packageNames.getTotalHits(), packageNames.getOffset(), extensions);
    }

    private PypiPackageSearcher getPypiPackageSearcher()
    {
        if (packageSearcher == null || hasPackageListIndexChanged()) {
            try {
                packageSearcher = new PypiPackageSearcher(pypiPackageListIndexDirectory.get(), logger);
            } catch (IOException e) {
                logger.error(
                    "Could not open lucene package list index from directory " + pypiPackageListIndexDirectory.get(),
                    e);
            }
        }
        return packageSearcher;
    }

    private boolean hasPackageListIndexChanged()
    {
        return !packageSearcher.getIndexDirectoryFile().equals(pypiPackageListIndexDirectory.get());
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
            outputFile.mkdirs();

            outputFile = new File(outputFile, getIndexFileName());
        }

        PypiPackageSearcher searcher = getPypiPackageSearcher();
        if (searcher != null) {
            try (IndexReader reader = searcher.createIndexReader()) {
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
                    zip.putNextEntry(new ZipEntry("index.txt"));

                    try (Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8)) {
                        for (int i = 0; i < reader.maxDoc(); i++) {
                            Document doc = reader.document(i);

                            writer.append(doc.get(LuceneParameters.PACKAGE_NAME));
                            writer.append('\t');
                            writer.append(doc.get(LuceneParameters.VERSION));
                            writer.append('\n');
                        }
                    }

                    zip.closeEntry();
                }
            }
        }
    }

    public void importIndex(InputStream inputFile)
    {
        boolean newIndexCreated = false;
        File indexDir = new File(environment.getPermanentDirectory(), "cache/pypi-index");
        indexDir = new File(indexDir, UUID.randomUUID().toString());

        try (IndexWriter indexWriter =
            new IndexWriter(FSDirectory.open(indexDir.toPath()), new IndexWriterConfig(new StandardAnalyzer()))) {

            try (ZipInputStream zip = new ZipInputStream(inputFile)) {
                zip.getNextEntry();

                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8))) {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        int index = line.indexOf('\t');

                        String packageName = line.substring(0, index);
                        String packageVersion = line.substring(index + 1);

                        Document document = new Document();
                        document.add(new TextField(LuceneParameters.PACKAGE_NAME, packageName, Field.Store.YES));
                        document.add(new StringField(LuceneParameters.ID, packageName, Field.Store.YES));
                        document.add(new StoredField(LuceneParameters.VERSION, packageVersion));

                        indexWriter.addDocument(document);
                    }
                }
            }

            newIndexCreated = true;
        } catch (IOException e) {
            logger.error("IO problem when creating the Lucene index writer", e);
        }

        if (newIndexCreated) {
            File previousIndexDir = pypiPackageListIndexDirectory.get();
            pypiPackageListIndexDirectory.set(indexDir);

            if (previousIndexDir != null) {
                try {
                    FileUtils.forceDelete(previousIndexDir);
                } catch (IOException e) {
                    logger.error("Failed to delete previous index [{}]", e);
                }
            }
        }
    }
}
