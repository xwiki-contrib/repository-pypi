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
package org.xwiki.contrib.repository.pypi.internal.dto.packagesInJython;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 */
public class PackagesInJython
{
    private Set<String> packages;

    private static PackagesInJython packagesInJython = null;

    private static ObjectMapper objectMapper = new ObjectMapper();

    public Set<String> getPackages()
    {
        return packages;
    }

    public void setPackages(Set<String> packages)
    {
        this.packages = packages;
    }

    public static PackagesInJython getPackagesIncludedInJython()
    {
        if (packagesInJython == null) {
            try {
                packagesInJython = objectMapper
                        .readValue(PackagesInJython.class.getResourceAsStream("/packagesIncludedInJython.json"),
                                PackagesInJython.class);
            } catch (IOException e) {
                //should never happen
            }
        }
        return packagesInJython;
    }

    public boolean contains(String packageName)
    {
        return getPackages().contains(packageName);
    }
}
