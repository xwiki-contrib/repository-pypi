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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageUrlDto;
import org.xwiki.contrib.repository.pypi.dto.wheelMetadata.RequiredDistributions;
import org.xwiki.extension.AbstractRemoteExtension;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicense;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.ExtensionRepository;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PypiExtension extends AbstractRemoteExtension
{
    private String distributionType;

    private PypiExtension(ExtensionRepository repository,
            ExtensionId id, String type)
    {
        super(repository, id, type);
    }

    /**
     * @param pypiPackageData -
     * @param pypiExtensionRepository -
     * @param licenseManager -
     * @param httpClientFactory -
     * @throws ResolveException -
     */
    public static PypiExtension constructFrom(PypiPackageJSONDto pypiPackageData,
            PypiExtensionRepository pypiExtensionRepository, ExtensionLicenseManager licenseManager,
            HttpClientFactory httpClientFactory) throws ResolveException
    {
        String packageName = pypiPackageData.getInfo().getName();
        String version = pypiPackageData.getInfo().getVersion();
        ExtensionId extensionId = new ExtensionId(PypiParameters.DEFAULT_GROUPID + ":" + packageName, version);
        Optional<PypiPackageUrlDto> fileUrlDtoForVersionOptional =
                pypiPackageData.getEggOrWhlFileUrlDtoForVersion(version);
        if (!fileUrlDtoForVersionOptional.isPresent()) {
            throw new ResolveException("Extension [" + packageName
                    + "] found in repository but compatible python version distribution (Jython 2.7) not found");
        }
        PypiPackageUrlDto fileUrlDtoForVersion = fileUrlDtoForVersionOptional.get();
        PypiExtension pypiExtension =
                new PypiExtension(pypiExtensionRepository, extensionId, PypiParameters.PACKAGE_TYPE);

        //set metadata
        pypiExtension.setName(pypiPackageData.getInfo().getName());
        pypiExtension.setDescription(pypiPackageData.getInfo().getDescription());
        pypiExtension.setSummary(StringUtils.substring(pypiPackageData.getInfo().getDescription(), 0, 200));
        pypiExtension.addLicences(pypiPackageData.getInfo().getLicense(), licenseManager);
        pypiExtension.setWebsite(pypiPackageData.getInfo().getHome_page());
        pypiExtension.addRepository(pypiExtensionRepository.getDescriptor());
        pypiExtension.setRecommended(false);

        pypiExtension.setDistributionType(fileUrlDtoForVersion.getPackagetype());
        //setFile
        try {
            URI uriToDownload = new URI(fileUrlDtoForVersion.getUrl());
            long size = fileUrlDtoForVersion.getSize();
            PypiExtensionFile pypiExtensionFile = new PypiExtensionFile(uriToDownload, size, httpClientFactory);
            pypiExtension.setFile(pypiExtensionFile);
        } catch (URISyntaxException e) {
            throw new ResolveException("Wrong download URL received from Rest call to repository", e);
        }

        pypiExtension.addDependencies(pypiExtensionRepository);

        return pypiExtension;
    }

    private void addDependencies(PypiExtensionRepository pypiExtensionRepository) throws ResolveException
    {
        ZipInputStream zis = null;
        if (PypiParameters.PACKAGE_TYPE_WHEEL.equals(getDistributionType())) {
            try {
                zis = new ZipInputStream(getFile().openStream());
                String expectedMetadataFilename = getExpectedMetadataFilename();
                for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                    String fileName = entry.getName();
                    if (expectedMetadataFilename.equals(fileName)) {
                        RequiredDistributions.parseFile(zis, pypiExtensionRepository)
                                .getDependencies().stream().forEach(dependency -> addDependency(dependency));
                        break;
                    }
                }
            } catch (IOException e) {
                throw new ResolveException(
                        "Cannot open distribution package for obtaining dependencies. Package name: " + getName());
            } finally {
                IOUtils.closeQuietly(zis);
            }
        } else {
            //not supported for other types of package
        }
    }

    private void addLicences(String licenseName, ExtensionLicenseManager licenseManager)
    {
        if (licenseName != null) {
            ExtensionLicense extensionLicense = licenseManager.getLicense(licenseName);
            if (extensionLicense != null) {
                addLicense(extensionLicense);
            } else {
                List<String> content = null;
                addLicense(new ExtensionLicense(licenseName, content));
            }
        }
    }

    /**
     *
     * @return
     */
    public String getDistributionType()
    {
        return distributionType;
    }

    /**
     * @param distributionType -
     */
    public void setDistributionType(String distributionType)
    {
        this.distributionType = distributionType;
    }

    public String getExpectedMetadataFilename()
    {
        return getName() + "-" + getId().getVersion() + ".dist-info/METADATA";
    }
}
