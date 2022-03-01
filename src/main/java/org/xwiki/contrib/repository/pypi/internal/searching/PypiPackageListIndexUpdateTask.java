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
package org.xwiki.contrib.repository.pypi.internal.searching;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.xwiki.contrib.repository.pypi.internal.PypiExtensionRepository;
import org.xwiki.contrib.repository.pypi.internal.PypiParameters;
import org.xwiki.contrib.repository.pypi.internal.dto.packagesInJython.PackagesInJython;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.internal.utils.PyPiHttpUtils;
import org.xwiki.contrib.repository.pypi.internal.utils.PypiUtils;
import org.xwiki.environment.Environment;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PypiPackageListIndexUpdateTask extends TimerTask
{
    private final HttpClientContext localContext;

    private AtomicReference<File> pypiPackageListIndexDirectory;

    private PypiExtensionRepository pypiExtensionRepository;

    private Environment environment;

    private HttpClientFactory httpClientFactory;

    private Logger logger;

    public PypiPackageListIndexUpdateTask(AtomicReference<File> pypiPackageListIndexDirectory,
        PypiExtensionRepository pypiExtensionRepository, Environment environment, HttpClientFactory httpClientFactory,
        Logger logger)
    {
        this.pypiPackageListIndexDirectory = pypiPackageListIndexDirectory;
        this.pypiExtensionRepository = pypiExtensionRepository;
        this.environment = environment;
        this.httpClientFactory = httpClientFactory;
        this.localContext = HttpClientContext.create();
        this.logger = logger;
    }

    @Override
    public void run()
    {
        logger.info("Start of update lucene index task");
        boolean newIndexCreated = false;
        File indexDir = new File(environment.getPermanentDirectory(), "cache/pypi-index");
        indexDir = new File(indexDir, UUID.randomUUID().toString());

        try (IndexWriter indexWriter =
            new IndexWriter(FSDirectory.open(indexDir.toPath()), new IndexWriterConfig(new StandardAnalyzer()))) {
            try (InputStream htmlPageInputStream = getSimpleApiHtmlPageInputStream()) {
                if (htmlPageInputStream != null) {
                    List<String> packageNames = parseHtmlPageToPackagenames(htmlPageInputStream);
                    packageNames = removePackagesIncludedInJython(packageNames);
                    addAllValidPackagesToIndex(indexWriter, packageNames);
                }
                newIndexCreated = true;
            }
        } catch (IOException e) {
            logger.error("IO problem whilst updating python package index", e);
        }
        if (newIndexCreated) {
            File previousIndexDir = pypiPackageListIndexDirectory.get();
            pypiPackageListIndexDirectory.set(indexDir);
            try {
                if (previousIndexDir != null) {
                    FileUtils.forceDelete(previousIndexDir);
                }
            } catch (Exception e) {

            }
            logger.info("End of update lucene index task. Pypi packages list index updated");
        } else {
            logger.info("End of update lucene index task. Pypi packages list update called but index not updated");
        }
    }

    private List<String> removePackagesIncludedInJython(List<String> packageNames)
    {
        packageNames.removeAll(PackagesInJython.getPackagesIncludedInJython().getPackages());
        return packageNames;
    }

    private void addAllValidPackagesToIndex(IndexWriter indexWriter, List<String> packageNames)
    {
        packageNames.parallelStream().forEach(packageName -> {
            try {
                PypiPackageJSONDto packageDataFromApi =
                    this.pypiExtensionRepository.getPypiPackageData(packageName, Optional.empty());

                if (PypiUtils.isPackageValidForXwiki(packageDataFromApi)) {
                    Document newDocument = createNewDocument(packageDataFromApi);
                    indexWriter.addDocument(newDocument);
                }
            } catch (ExtensionNotFoundException | HttpException e) {
                logger.debug("Could not resolve " + packageName + " package", e);
            } catch (IOException e) {
                logger.debug("IO problems whilst serializing " + packageName + " package extension", e);
            }
        });
    }

    private Document createNewDocument(String packageName, String version)
    {
        Document document = new Document();
        document.add(new TextField(LuceneParameters.PACKAGE_NAME, packageName, Field.Store.YES));
        document.add(new StringField(LuceneParameters.ID, packageName, Field.Store.YES));
        document.add(new StoredField(LuceneParameters.VERSION, version));
        return document;
    }

    private Document createNewDocument(PypiPackageJSONDto pypiPackageJSONDto)
    {
        String packageName = pypiPackageJSONDto.getInfo().getName();
        String version = pypiPackageJSONDto.getInfo().getVersion();
        return createNewDocument(packageName, version);
    }

    protected List<String> parseHtmlPageToPackagenames(InputStream is) throws IOException
    {
        org.jsoup.nodes.Document doc = Jsoup.parse(is, null, PypiParameters.PACKAGE_LIST_SIMPLE_API);

        Elements links = doc.select("a");

        return links.stream().map(Element::text).collect(Collectors.toList());
    }

    public InputStream getSimpleApiHtmlPageInputStream()
    {
        try {
            return PyPiHttpUtils.performGet(new URI(PypiParameters.PACKAGE_LIST_SIMPLE_API), httpClientFactory,
                localContext);
        } catch (HttpException e) {
            logger.error("Failed to get list of python packages from PyPi", e);
        } catch (URISyntaxException e) {
            // should never happen
        }

        return null;
    }
}
