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
package org.xwiki.contrib.repository.pypi.internal.dto.wheelMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.HttpException;
import org.xwiki.contrib.repository.pypi.internal.PypiExtensionRepository;
import org.xwiki.contrib.repository.pypi.internal.PypiParameters;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageUrlDto;
import org.xwiki.contrib.repository.pypi.internal.utils.PypiUtils;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.version.InvalidVersionRangeException;
import org.xwiki.extension.version.VersionConstraint;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.extension.version.internal.DefaultVersionConstraint;
import org.xwiki.extension.version.internal.DefaultVersionRangeCollection;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class RequiredDistributions
{
    private static Pattern requiredDist = Pattern.compile("^Requires-Dist: .*");

    private static Pattern requiredDistVersion = Pattern.compile("^(Requires-Dist: )(\\S+)( )(\\(.*\\))(.*)");

    private static Pattern requiredDistExtra = Pattern.compile("^Requires-Dist: .*extra ==.*");

    private static Pattern requiredDistNoVersion = Pattern.compile("^(Requires-Dist: )([^\\s;]+)(.*)");

    private LinkedList<ExtensionDependency> dependencies;

    public RequiredDistributions(LinkedList<ExtensionDependency> dependencies)
    {
        this.dependencies = dependencies;
    }

    public LinkedList<ExtensionDependency> getDependencies()
    {
        return dependencies;
    }

    public void setDependencies(LinkedList<ExtensionDependency> dependencies)
    {
        this.dependencies = dependencies;
    }

    public static RequiredDistributions resolveDependenciesFromFile(InputStream is,
            PypiExtensionRepository pypiExtensionRepository)
            throws ResolveException
    {

        LinkedList<ExtensionDependency> dependencies = new LinkedList<>();

        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            List<String> lines = buffer.lines().collect(Collectors.toList());
            for (String line : lines) {
                if (requiredDist.matcher(line).matches()) {
                    String packageName;
                    VersionConstraint versionConstraint;

                    if (requiredDistExtra.matcher(line).matches()) {
                        continue; // those dependencies are optional
                    }
                    if (requiredDistVersion.matcher(line).matches()) {
                        Matcher matcher = requiredDistVersion.matcher(line);
                        matcher.find();
                        packageName = matcher.group(2);
                        String versionPart = matcher.group(4);
                        versionConstraint = getVersionOfDependency(versionPart);
                    } else if (requiredDistNoVersion.matcher(line).matches()) {
                        Matcher matcher = requiredDistNoVersion.matcher(line);
                        matcher.find();
                        packageName = matcher.group(2);
                        //find newest version of that package
                        versionConstraint =
                                getNewestVersionConstraint(packageName, pypiExtensionRepository);
                    } else {
                        continue;
                    }
                    dependencies.add(new DefaultExtensionDependency(
                            PypiParameters.DEFAULT_GROUPID + ":" + packageName, versionConstraint));
                }
            }
        } catch (IOException e) {
            throw new ResolveException("Could not open downloaded package to read dependencies");
        }
        return new RequiredDistributions(dependencies);
    }

    private static VersionConstraint getNewestVersionConstraint(String packageName,
            PypiExtensionRepository pypiExtensionRepository) throws ResolveException
    {
        try {
            PypiPackageJSONDto pypiPackageData =
                    pypiExtensionRepository.getPypiPackageData(packageName, Optional.empty());
            String version = pypiPackageData.getInfo().getVersion();
            return new DefaultVersionConstraint(
                    Collections.singleton(new DefaultVersionRangeCollection("(," + version + "]")),
                    new DefaultVersion(version));
        } catch (HttpException e) {
            throw new ResolveException("Cannot obtain version of dependency for dependency package: " + packageName);
        } catch (InvalidVersionRangeException e) {
            //shouldNeverHappen
            throw new ResolveException("Problem with version range spec.", e);
        }
    }

    private static VersionConstraint getVersionOfDependency(String versionPart) throws ResolveException
    {
        String[] versionsIndications = versionPart.substring(1, versionPart.length() - 1).split(",");

        return Arrays.stream(versionsIndications).map(versionInd -> {
            try {
                if (versionInd.contains(">=")) {
                    String version = versionInd.replace(">=", "");
                    return new DefaultVersionConstraint(
                            Collections.singleton(new DefaultVersionRangeCollection("[" + version + ",)")),
                            new DefaultVersion(version));
                } else if (versionInd.contains("==")) {
                    String version = versionInd.replace("==", "");
                    return new DefaultVersionConstraint(
                            Collections.singleton(new DefaultVersionRangeCollection("(," + version + "]")),
                            new DefaultVersion(version));
                } else if (versionInd.contains("<=")) {
                    String version = versionInd.replace("<=", "");
                    return new DefaultVersionConstraint(
                            Collections.singleton(new DefaultVersionRangeCollection("(," + version + "]")),
                            new DefaultVersion(version));
                }
                return null;
            } catch (InvalidVersionRangeException e) {
                //shouldNeverHappen
                return null;
            }
        }).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
