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

import org.apache.commons.lang.StringUtils;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class Distribution
{
    private String packageName;

    private String requiredVersion;

    private boolean isParticularVersionRequired;

    /**
     *
     * @param packageName -
     * @param requiredVersion -
     */
    public Distribution(String packageName, String requiredVersion)
    {
        this.packageName = packageName;
        this.requiredVersion = requiredVersion;
        if (StringUtils.isEmpty(requiredVersion)) {
            isParticularVersionRequired = false;
        } else {
            isParticularVersionRequired = true;
        }
    }

    public String getPackageName()
    {
        return packageName;
    }

    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    public String getRequiredVersion()
    {
        return requiredVersion;
    }

    public void setRequiredVersion(String requiredVersion)
    {
        this.requiredVersion = requiredVersion;
    }

    public boolean isParticularVersionRequired()
    {
        return isParticularVersionRequired;
    }

    public void setParticularVersionRequired(boolean particularVersionRequired)
    {
        isParticularVersionRequired = particularVersionRequired;
    }
}
