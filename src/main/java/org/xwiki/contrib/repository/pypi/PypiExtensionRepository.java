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

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.utils.PypiUtils;
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
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.version.Version;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@Component(roles = PypiExtensionRepository.class)
@Singleton
public class PypiExtensionRepository extends AbstractExtensionRepository
{
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private ExtensionLicenseManager licenseManager;

    @Inject
    private ExtensionFactory extensionFactory;

    @Inject
    private HttpClientFactory httpClientFactory;

    @Inject
    private Logger logger;

    private HttpClientContext localContext;

    /**
     * @param extensionRepositoryDescriptor -
     */
    public PypiExtensionRepository setUpRepository(ExtensionRepositoryDescriptor extensionRepositoryDescriptor)
    {
        setDescriptor(extensionRepositoryDescriptor);
        this.localContext = HttpClientContext.create();
        return this;
    }

    @Override public Extension resolve(ExtensionId extensionId) throws ResolveException
    {
        String packageName = PypiUtils.getPackageName(extensionId);
        Optional<String> version = PypiUtils.getVersion(extensionId);
        try {
            PypiPackageJSONDto pypiPackageData = getPypiPackageData(packageName, version);
            return PypiExtension.constructFrom(pypiPackageData, this, licenseManager, httpClientFactory);
        } catch (HttpException e) {
            throw new ResolveException("Failed to resolve package [" + packageName + "]", e);
        }
    }

    @Override public Extension resolve(ExtensionDependency extensionDependency) throws ResolveException
    {
        return null;
    }

    @Override public IterableResult<Version> resolveVersions(String s, int i, int i1) throws ResolveException
    {
        return null;
    }

    public PypiPackageJSONDto getPypiPackageData(String packageName, Optional<String> version)
            throws HttpException, ResolveException
    {
        HttpGet getMethod;
        if (version.isPresent()) {
            getMethod = new HttpGet(
                    PypiParameters.PACKAGE_VERSION_INFO_JSON.replace("{package_name}", packageName)
                            .replace("{version}", version.get())
            );
        } else {
            getMethod = new HttpGet(PypiParameters.PACKAGE_INFO_JSON.replace("{package_name}", packageName));
        }
        CloseableHttpClient httpClient = httpClientFactory.createClient(null, null);
        CloseableHttpResponse response;
        try {
            if (this.localContext != null) {
                response = httpClient.execute(getMethod, this.localContext);
            } else {
                response = httpClient.execute(getMethod);
            }
        } catch (Exception e) {
            throw new HttpException(String.format("Failed to request [%s]", getMethod.getURI()), e);
        }

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK) {
            try {
                PypiPackageJSONDto pypiPackageJSONDto =
                        objectMapper.readValue(response.getEntity().getContent(), PypiPackageJSONDto.class);
                return pypiPackageJSONDto;
            } catch (IOException e) {
                throw new HttpException(String.format("Failed to parse response body of request [%s]",
                        getMethod.getURI()), e);
            }
        } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
            throw new ExtensionNotFoundException("Could not find package [" + packageName + "] descriptor");
        } else {
            throw new ResolveException(String.format("Invalid answer [%s] from the server when requesting [%s]",
                    response.getStatusLine().getStatusCode(), getMethod.getURI()));
        }
    }
}
