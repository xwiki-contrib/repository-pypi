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
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.repository.pypi.internal.PypiParameters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The response of the PyPI JSON API ({@code https://pypi.org/pypi/<project>[/<version>]/json}).
 * <p>
 * The distribution files are taken from {@code urls}, which lists the files of the release described in
 * {@code info}: the requested version, or the latest one when no version is requested. The {@code releases} field
 * is not used since it's deprecated and not returned anymore when requesting a specific version.
 *
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiPackageJSONDto
{
    private static final String WHEEL_EXTENSION = ".whl";

    private PypiPackageInfoDto info;

    private List<PypiPackageUrlDto> urls;

    /**
     * Find a pure Python 3 wheel of the release: a wheel which does not contain any native code (ABI tag {@code none}
     * and platform tag {@code any}) and is compatible with Python 3. Packages which are only distributed with native
     * code (C extensions) are not supported since they would require a platform specific build.
     *
     * @return the pure Python 3 wheel of the release
     * @since 1.1.5
     */
    public Optional<PypiPackageUrlDto> getWhlFileUrlDto()
    {
        if (urls == null) {
            return Optional.empty();
        }

        return urls.stream().filter(url -> !url.isYanked())
            .filter(url -> PypiParameters.PACKAGE_TYPE_WHEEL.equals(url.getPackagetype()))
            .filter(url -> isPurePython3Wheel(url.getFilename())).findFirst();
    }

    /**
     * @param filename the name of the wheel file, in the form
     *            {@code {distribution}-{version}(-{build})?-{python tag}-{abi tag}-{platform tag}.whl} (PEP 427)
     * @return true if the wheel is compatible with Python 3 and does not contain any native code
     */
    private static boolean isPurePython3Wheel(String filename)
    {
        if (filename == null || !filename.endsWith(WHEEL_EXTENSION)) {
            return false;
        }

        String[] parts = StringUtils.removeEnd(filename, WHEEL_EXTENSION).split("-");
        if (parts.length < 5) {
            return false;
        }

        String pythonTag = parts[parts.length - 3];
        String abiTag = parts[parts.length - 2];
        String platformTag = parts[parts.length - 1];

        // Each tag can be a compressed set of tags separated by dots (e.g. "py2.py3")
        return "none".equals(abiTag) && "any".equals(platformTag)
            && Arrays.stream(pythonTag.split("\\.")).anyMatch(tag -> tag.startsWith("py3"));
    }

    public PypiPackageInfoDto getInfo()
    {
        return info;
    }

    public void setInfo(PypiPackageInfoDto info)
    {
        this.info = info;
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
