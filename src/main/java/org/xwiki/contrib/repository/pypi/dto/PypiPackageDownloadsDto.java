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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiPackageDownloadsDto
{
    private int last_month;
    private int last_week;
    private int last_day;


    public int getLast_month()
    {
        return last_month;
    }

    public void setLast_month(int last_month)
    {
        this.last_month = last_month;
    }

    public int getLast_week()
    {
        return last_week;
    }

    public void setLast_week(int last_week)
    {
        this.last_week = last_week;
    }

    public int getLast_day()
    {
        return last_day;
    }

    public void setLast_day(int last_day)
    {
        this.last_day = last_day;
    }
}
