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
package org.xwiki.contrib.repository.pypi.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.xwiki.contrib.repository.pypi.exception.PypiApiException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PypiPackageJSONDto
{
    private PypiPackageInfoDto info;

    private JsonNode releases;

    private List<PypiPackageUrlDto> urls;

    /**
     * Gets Url metadata for the latest version of package
     *
     * @throws PypiApiException - when there's no downloable version of this package
     */
    public Optional<PypiPackageUrlDto> getZipUrlDtoForNewestVersion() throws PypiApiException
    {
        String version = info.getVersion();
        if (version == null) {
            if (releases.size() < 1) {
                return Optional.empty();
            }
            String firstChildLabel = releases.fieldNames().next();
            return getZipUrlDtoForVersion(firstChildLabel);
        } else {
            return getZipUrlDtoForVersion(version);
        }
    }

    /**
     * @param version - version of package to get it's url meta data
     */
    public Optional<PypiPackageUrlDto> getZipUrlDtoForVersion(String version)
    {
        JsonNode versionUrlNode = releases.get(version);
        if (versionUrlNode == null || versionUrlNode.isMissingNode()) {
            return Optional.empty();
        } else {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                PypiPackageUrlDto[] pypiPackageUrlDtos =
                        objectMapper.treeToValue(versionUrlNode, PypiPackageUrlDto[].class);
                return Arrays.stream(pypiPackageUrlDtos).filter(
                        pypiPackageUrlDto -> "sdist".equals(pypiPackageUrlDto.getPackagetype())
                ).findFirst();
            } catch (JsonProcessingException e) {
                //should never happen
                return Optional.empty();
            }
        }
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
