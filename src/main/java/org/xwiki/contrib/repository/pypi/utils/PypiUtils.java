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
package org.xwiki.contrib.repository.pypi.utils;

import java.util.Optional;

import org.xwiki.contrib.repository.pypi.PypiParameters;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
final public class PypiUtils
{
    private PypiUtils()
    {
    }

    /**
     * This method assumes that id for e.g. numpy package may be either: "org.python:numpy" or "numpy"
     *
     * @param extensionId -
     * @throws ResolveException -
     */
    public static String getPackageName(ExtensionId extensionId) throws ResolveException
    {
        String[] parts = extensionId.getId().split(":");
        if (parts.length > 1) {
            if (PypiParameters.DEFAULT_GROUPID.equals(parts[0])) {
                return parts[1];
            } else {
                throw new ExtensionNotFoundException("That's not id of python package: " + extensionId);
            }
        } else {
            return extensionId.getId();
        }
    }

    /**
     * @param extensionId -
     * @return extracted version wrapped with Optional. If version is null or empty Optional is empty as well
     */
    public static Optional<String> getVersion(ExtensionId extensionId)
    {
        String version = extensionId.getVersion().getValue();
        if (isEmpty(version)) {
            return Optional.empty();
        } else {
            return Optional.of(version);
        }
    }
}
