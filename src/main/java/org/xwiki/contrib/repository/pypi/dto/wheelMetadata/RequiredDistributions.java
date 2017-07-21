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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.xwiki.extension.ResolveException;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class RequiredDistributions
{
    private static Pattern requiredDist = Pattern.compile("^Requires-Dist: .*");

    private static Pattern requiredDistVersion = Pattern.compile("^(Requires-Dist: )(\\S+)( )(\\(.*\\))$");

    private static Pattern requiredDistNoVersion = Pattern.compile("^(Requires-Dist: )(\\S+)$");

    private LinkedList<Distribution> distributions;

    private RequiredDistributions(
            LinkedList<Distribution> distributions)
    {

        this.distributions = distributions;
    }

    public LinkedList<Distribution> getDistributions()
    {
        return distributions;
    }

    public void setDistributions(
            LinkedList<Distribution> distributions)
    {
        this.distributions = distributions;
    }

    public static RequiredDistributions parseFile(Path path) throws ResolveException
    {

        LinkedList<Distribution> distributions = new LinkedList<>();

        try (Stream<String> stream = Files.lines(path)) {

            stream.forEach(s -> {
                if (requiredDist.matcher(s).matches()) {
                    if (requiredDistVersion.matcher(s).matches()) {
                        Matcher matcher = requiredDistVersion.matcher(s);
                        matcher.find();
                        String packageName = matcher.group(2);
                        String versionPart = matcher.group(4);
                        String requiredVersion = getVersionOfDependency(versionPart);

                        distributions.add(new Distribution(packageName, requiredVersion));
                    } else if (requiredDistNoVersion.matcher(s).matches()) {
                        Matcher matcher = requiredDistNoVersion.matcher(s);
                        String packageName = matcher.group(2);
                        distributions.add(new Distribution(packageName, null));
                    } else {
                        // case when Required Distribution contains `extra` part and so is optional
                    }
                }
            });
        } catch (IOException e) {
            throw new ResolveException("Could not open downloaded package of " + path);
        }
        return new RequiredDistributions(distributions);
    }

    private static String getVersionOfDependency(String versionPart)
    {
        String[] versionsIndications = versionPart.substring(1, versionPart.length() - 1).split(",");
        return Arrays.stream(versionsIndications).map(versionInd -> {
            if (versionInd.contains(">=")) {
                return versionInd.replace(">=", "");
            } else if (versionInd.contains("==")) {
                return versionInd.replace("==", "");
            } else if (versionInd.contains("<=")) {
                return versionInd.replace("<=", "");
            }
            return null;
        }).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
