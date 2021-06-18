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
package org.xwiki.contrib.repository.pypi.internal;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public interface PypiParameters
{
    String API_URL = "https://pypi.org/pypi";
    String PACKAGE_INFO_JSON = "https://pypi.org/pypi/{package_name}/json";
    String PACKAGE_VERSION_INFO_JSON = "https://pypi.org/pypi/{package_name}/{version}/json";
    String PACKAGE_LIST_SIMPLE_API = "https://pypi.org/simple/";

    String DEFAULT_GROUPID = "org.python";
    String PACKAGE_TYPE = "jar";

    String PACKAGE_TYPE_SDIST = "sdist";
    String PACKAGE_TYPE_EGG = "bdist_egg";
    String PACKAGE_TYPE_WHEEL = "bdist_wheel";
}
