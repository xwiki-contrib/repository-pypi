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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.environment.Environment;
import org.xwiki.extension.ExtensionManagerConfiguration;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.version.Version;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiExtensionRepository}.
 * 
 * @version $Id$
 */
public class PypiExtensionRepositoryTest
{
    private static final String DECORATOR_SIMPLE = "{\"meta\":{\"api-version\":\"1.4\"},\"name\":\"decorator\","
        + "\"versions\":[\"4.0.11\",\"4.4.2\",\"5.3.1\"],\"files\":[]}";

    private static final int EMBEDDED_INDEX_SIZE = 901095;

    @Rule
    public MockitoComponentMockingRule<PypiExtensionRepository> mocker =
        new MockitoComponentMockingRule<>(PypiExtensionRepository.class);

    /**
     * The body returned for each requested URI, all other URIs answer with a 404.
     */
    private final Map<String, String> responses = new HashMap<>();

    /**
     * The {@code Accept} header sent for each requested URI.
     */
    private final Map<String, String> accepts = new HashMap<>();

    private File testDirectory;

    @Before
    public void before() throws Exception
    {
        this.mocker.registerMockComponent(ExtensionManagerConfiguration.class);

        Environment environment = this.mocker.getInstance(Environment.class);

        this.testDirectory = new File("target/test-" + System.nanoTime()).getAbsoluteFile();
        File permdir = new File(this.testDirectory, "perm");
        permdir.mkdirs();
        File tempDir = new File(this.testDirectory, "temp");
        tempDir.mkdirs();

        when(environment.getPermanentDirectory()).thenReturn(permdir);
        when(environment.getTemporaryDirectory()).thenReturn(tempDir);

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        when(httpClient.execute(any(HttpUriRequest.class), any(HttpContext.class)))
            .then(invocation -> response(invocation.getArgument(0)));
        when(httpClient.execute(any(HttpUriRequest.class))).then(invocation -> response(invocation.getArgument(0)));
        HttpClientFactory httpClientFactory = this.mocker.getInstance(HttpClientFactory.class);
        when(httpClientFactory.createClient(isNull(), isNull())).thenReturn(httpClient);
    }

    private synchronized CloseableHttpResponse response(HttpUriRequest request) throws Exception
    {
        String uri = request.getURI().toString();
        this.accepts.put(uri, request.getFirstHeader(HttpHeaders.ACCEPT) != null
            ? request.getFirstHeader(HttpHeaders.ACCEPT).getValue() : null);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        String body = this.responses.get(uri);
        if (body != null) {
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
            HttpEntity entity = mock(HttpEntity.class);
            when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            when(response.getEntity()).thenReturn(entity);
        } else {
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
        }

        return response;
    }

    private synchronized List<String> getRequestedJsonApiUris()
    {
        return this.accepts.keySet().stream().filter(uri -> uri.startsWith(PypiParameters.API_URL)).sorted()
            .collect(Collectors.toList());
    }

    private synchronized String getAccept(String uri)
    {
        return this.accepts.get(uri);
    }

    @Test
    public void resolveVersions() throws Exception
    {
        this.responses.put("https://pypi.org/simple/decorator/", DECORATOR_SIMPLE);

        IterableResult<Version> versions =
            this.mocker.getComponentUnderTest().resolveVersions("org.python:decorator", 1, 5);

        assertEquals(3, versions.getTotalHits());
        assertEquals(1, versions.getOffset());
        List<String> values = new ArrayList<>();
        versions.forEach(version -> values.add(version.getValue()));
        assertEquals(Arrays.asList("4.4.2", "5.3.1"), values);
        assertEquals(PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE, getAccept("https://pypi.org/simple/decorator/"));
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveVersionsWhenPackageDoesNotExist() throws Exception
    {
        this.mocker.getComponentUnderTest().resolveVersions("org.python:doesnotexist", 0, -1);
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveVersionsWhenNoVersion() throws Exception
    {
        this.responses.put("https://pypi.org/simple/empty/", "{\"name\":\"empty\",\"versions\":[]}");

        this.mocker.getComponentUnderTest().resolveVersions("empty", 0, -1);
    }

    @Test
    public void getPypiPackageDataForVersion() throws Exception
    {
        this.responses.put("https://pypi.org/pypi/decorator/4.0.11/json", TestUtils
            .getFileAsString("/org/xwiki/contrib/repository/pypi/internal/dto/pypiJsonApi/DecoratorPypiPackage.json",
                this));

        PypiPackageJSONDto data =
            this.mocker.getComponentUnderTest().getPypiPackageData("decorator", Optional.of("4.0.11"));

        assertEquals("4.0.11", data.getInfo().getVersion());
        assertEquals(Arrays.asList("decorator-4.0.11-py2.py3-none-any.whl", "decorator-4.0.11.tar.gz"),
            data.getUrls().stream().map(url -> url.getFilename()).collect(Collectors.toList()));
        assertNull(getAccept("https://pypi.org/pypi/decorator/4.0.11/json"));
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void getPypiPackageDataWhenPackageDoesNotExist() throws Exception
    {
        this.mocker.getComponentUnderTest().getPypiPackageData("doesnotexist", Optional.empty());
    }

    private List<String> exportIndex(PypiExtensionRepository repository, File exported) throws Exception
    {
        repository.exportIndex(exported);

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(exported))) {
            zip.getNextEntry();
            return IOUtils.readLines(zip, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void importIndexWithVersions() throws Exception
    {
        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();

        // Indexes exported by older versions contain the version of each package after a tab
        ByteArrayOutputStream index = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(index)) {
            zip.putNextEntry(new ZipEntry("index.txt"));
            zip.write("decorator\t4.4.1\n\nnetworkx\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        repository.importIndex(new ByteArrayInputStream(index.toByteArray()));

        assertEquals(Arrays.asList("decorator", "networkx"),
            exportIndex(repository, new File(this.testDirectory, "index.zip")));
    }

    @Test
    public void exportAndImportIndex() throws Exception
    {
        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();

        File exported = new File(this.testDirectory, "index.zip");
        List<String> lines = exportIndex(repository, exported);
        assertEquals(EMBEDDED_INDEX_SIZE, lines.size());
        assertTrue(lines.contains("decorator"));

        try (InputStream stream = new FileInputStream(exported)) {
            repository.importIndex(stream);
        }

        this.responses.put("https://pypi.org/pypi/decorator/json", TestUtils
            .getFileAsString("/org/xwiki/contrib/repository/pypi/internal/dto/pypiJsonApi/PyplotPypiPackage.json",
                this));
        // The package (and the others containing its name) is found in the index but can't be resolved since it has
        // no compatible distribution
        assertEquals(0, repository.search("decorator", 0, -1).getSize());
        assertTrue(getRequestedJsonApiUris().contains("https://pypi.org/pypi/decorator/json"));
    }
}
