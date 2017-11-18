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
package org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.IteratorUtils;
import org.xwiki.contrib.repository.pypi.internal.PypiParameters;
import org.xwiki.contrib.repository.pypi.internal.exception.PypiApiException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiPackageJSONDto
{
    private PypiPackageInfoDto info;

    private JsonNode releases;

    private List<PypiPackageUrlDto> urls;

    /**
     * Gets Url metadata for the latest version of package
     * @return -
     * @throws PypiApiException - when there's no downloadable version of this package
     */
    public Optional<PypiPackageUrlDto> getEggOrWhlUrlDtoForNewestVersion() throws PypiApiException
    {
        String version = info.getVersion();
        if (version == null) {
            if (releases.size() < 1) {
                return Optional.empty();
            }
            String firstChildLabel = releases.fieldNames().next();
            return getEggOrWhlFileUrlDtoForVersion(firstChildLabel);
        } else {
            return getEggOrWhlFileUrlDtoForVersion(version);
        }
    }

    public List<String> getAvailableReleaseVersions(){
        return IteratorUtils.toList(releases.fieldNames());
    }

    /**
     * @return -
     * @param releaseVersion - releaseVersion of package to be used to get url meta data
     */
    public Optional<PypiPackageUrlDto> getZipUrlDtoForVersion(String releaseVersion)
    {
        return getFileUrlDtoForVersion(releaseVersion, Collections.singleton(PypiParameters.PACKAGE_TYPE_SDIST));
    }

    /**
     * @return -
     * @param releaseVersion - releaseVersion of package to be used to get url meta data
     */
    public Optional<PypiPackageUrlDto> getEggFileUrlDtoForVersion(String releaseVersion)
    {
        return getFileUrlDtoForVersion(releaseVersion, Collections.singleton(PypiParameters.PACKAGE_TYPE_EGG));
    }

    /**
     * @return -
     * @param releaseVersion - releaseVersion of package to be used to get url meta data
     */
    public Optional<PypiPackageUrlDto> getWhlFileUrlDtoForVersion(String releaseVersion)
    {
        return getFileUrlDtoForVersion(releaseVersion,
                Sets.newHashSet(PypiParameters.PACKAGE_TYPE_WHEEL));
    }

    /**
     * @return -
     * @param releaseVersion - releaseVersion of package to be used to get url meta data
     */
    public Optional<PypiPackageUrlDto> getEggOrWhlFileUrlDtoForVersion(String releaseVersion)
    {
        return getFileUrlDtoForVersion(releaseVersion,
                Sets.newHashSet(PypiParameters.PACKAGE_TYPE_EGG, PypiParameters.PACKAGE_TYPE_WHEEL));
    }

    private Optional<PypiPackageUrlDto> getFileUrlDtoForVersion(String releaseVersion, Set packageTypes)
    {
        JsonNode versionUrlNode = releases.get(releaseVersion);
        if (versionUrlNode == null || versionUrlNode.isMissingNode()) {
            return Optional.empty();
        } else {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                PypiPackageUrlDto[] pypiPackageUrlDtos =
                        objectMapper.treeToValue(versionUrlNode, PypiPackageUrlDto[].class);
                // 1 get whl i egg
                List<PypiPackageUrlDto> packagesOfGivenTypes =
                        getPackagesOfGivenTypes(pypiPackageUrlDtos, packageTypes);
                return tryToGetCompatiblePackage(packagesOfGivenTypes);
            } catch (JsonProcessingException e) {
                //should never happen
                return Optional.empty();
            }
        }
    }

    private List<PypiPackageUrlDto> getPackagesOfGivenTypes(PypiPackageUrlDto[] pypiPackageUrlDtos, Set packageTypes)
    {
        return Arrays.stream(pypiPackageUrlDtos).filter(
                pypiPackageUrlDto -> packageTypes.contains(pypiPackageUrlDto.getPackagetype())
        ).collect(Collectors.toList());
    }

    private Optional<PypiPackageUrlDto> tryToGetCompatiblePackage(List<PypiPackageUrlDto> packagesOfGivenTypes)
    {
        Optional<PypiPackageUrlDto> result;
        result = tryToGetPython27PackageRegardingPythonVersionField(packagesOfGivenTypes);
        if (!result.isPresent()) {
            result = tryToGetPython27PackageRegadingPythonURLFileName(packagesOfGivenTypes);
        }
        if (!result.isPresent()) {
            result = tryToGetPython2PackageRegadingPythonURLFileName(packagesOfGivenTypes);
        }
        return result;
    }

    private Optional<PypiPackageUrlDto> tryToGetPython27PackageRegardingPythonVersionField(
            List<PypiPackageUrlDto> packagesOfGivenTypes)
    {
        return packagesOfGivenTypes.stream().filter(pypiPackageUrlDto -> {
            String pythonVersion = pypiPackageUrlDto.getPython_version().toLowerCase();
            return pythonVersion.contains("2.7") || pythonVersion.contains("any");
        }).findFirst();
    }

    private Optional<PypiPackageUrlDto> tryToGetPython27PackageRegadingPythonURLFileName(
            List<PypiPackageUrlDto> packagesOfGivenTypes)
    {
        return packagesOfGivenTypes.stream()
                .filter(pypiPackageUrlDto -> pypiPackageUrlDto.getUrl().toLowerCase().contains("py27")
                ).findFirst();
    }

    private Optional<PypiPackageUrlDto> tryToGetPython2PackageRegadingPythonURLFileName(
            List<PypiPackageUrlDto> packagesOfGivenTypes)
    {
        return packagesOfGivenTypes.stream()
                .filter(pypiPackageUrlDto -> pypiPackageUrlDto.getUrl().toLowerCase().contains("py2")
                ).findFirst();
    }

    public PypiPackageInfoDto getInfo()
    {
        return info;
    }

    public void setInfo(PypiPackageInfoDto info)
    {
        this.info = info;
    }

    public JsonNode getReleases()
    {
        return releases;
    }

    public void setReleases(JsonNode releases)
    {
        this.releases = releases;
    }

    public List<PypiPackageUrlDto> getUrls()
    {
        return urls;
    }

    public void setUrls(List<PypiPackageUrlDto> urls)
    {
        this.urls = urls;
    }
}
