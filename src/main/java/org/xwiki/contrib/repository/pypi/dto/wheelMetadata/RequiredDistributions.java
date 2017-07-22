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
package org.xwiki.contrib.repository.pypi.dto.wheelMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.HttpException;
import org.xwiki.contrib.repository.pypi.PypiExtensionRepository;
import org.xwiki.contrib.repository.pypi.PypiParameters;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.version.VersionConstraint;
import org.xwiki.extension.version.internal.DefaultVersionConstraint;

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

    public static RequiredDistributions parseFile(InputStream is, PypiExtensionRepository pypiExtensionRepository)
            throws ResolveException
    {

        LinkedList<ExtensionDependency> dependencies = new LinkedList<>();

        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            List<String> lines = buffer.lines().collect(Collectors.toList());
            for (String line : lines) {
                if (requiredDist.matcher(line).matches()) {
                    if (requiredDistExtra.matcher(line).matches()) {
                        continue; // those dependencies are optional
                    }
                    if (requiredDistVersion.matcher(line).matches()) {
                        Matcher matcher = requiredDistVersion.matcher(line);
                        matcher.find();
                        String packageName = matcher.group(2);
                        String versionPart = matcher.group(4);
                        VersionConstraint versionConstraint = getVersionOfDependency(versionPart);

                        dependencies.add(new DefaultExtensionDependency(
                                PypiParameters.DEFAULT_GROUPID + ":" + packageName, versionConstraint));
                    } else if (requiredDistNoVersion.matcher(line).matches()) {
                        Matcher matcher = requiredDistNoVersion.matcher(line);
                        matcher.find();
                        String packageName = matcher.group(2);
                        //find newest version of that package
                        VersionConstraint versionConstraint =
                                getNewestVersionConstraint(packageName, pypiExtensionRepository);
                        dependencies.add(new DefaultExtensionDependency(
                                PypiParameters.DEFAULT_GROUPID + ":" + packageName, versionConstraint));
                    } else {
                    }
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
            return new DefaultVersionConstraint("(," + pypiPackageData.getInfo().getVersion() + "]");
        } catch (HttpException e) {
            throw new ResolveException("Cannot obtain version of dependency for dependency package: " + packageName);
        }
    }

    private static VersionConstraint getVersionOfDependency(String versionPart)
    {
        String[] versionsIndications = versionPart.substring(1, versionPart.length() - 1).split(",");
        return Arrays.stream(versionsIndications).map(versionInd -> {
            if (versionInd.contains(">=")) {
                return new DefaultVersionConstraint("[" + versionInd.replace(">=", "") + ",)");
            } else if (versionInd.contains("==")) {
                return new DefaultVersionConstraint("[" + versionInd.replace("==", "") + "]");
            } else if (versionInd.contains("<=")) {
                return new DefaultVersionConstraint("(," + versionInd.replace("<=", "") + "]");
            }
            return null;
        }).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
