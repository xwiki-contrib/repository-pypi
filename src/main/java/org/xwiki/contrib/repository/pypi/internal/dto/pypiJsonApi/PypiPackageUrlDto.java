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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiPackageUrlDto
{
    private boolean has_sig;
    private String upload_time;
    private String comment_text;
    private String python_version;
    private String url;
    private String md5_digest;
    private int downloads;
    private String filename;
    private String packagetype;
    private String path;
    private long size;

    public boolean isHas_sig()
    {
        return has_sig;
    }

    public void setHas_sig(boolean has_sig)
    {
        this.has_sig = has_sig;
    }

    public String getUpload_time()
    {
        return upload_time;
    }

    public void setUpload_time(String upload_time)
    {
        this.upload_time = upload_time;
    }

    public String getComment_text()
    {
        return comment_text;
    }

    public void setComment_text(String comment_text)
    {
        this.comment_text = comment_text;
    }

    public String getPython_version()
    {
        return python_version;
    }

    public void setPython_version(String python_version)
    {
        this.python_version = python_version;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getMd5_digest()
    {
        return md5_digest;
    }

    public void setMd5_digest(String md5_digest)
    {
        this.md5_digest = md5_digest;
    }

    public int getDownloads()
    {
        return downloads;
    }

    public void setDownloads(int downloads)
    {
        this.downloads = downloads;
    }

    public String getFilename()
    {
        return filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public String getPackagetype()
    {
        return packagetype;
    }

    public void setPackagetype(String packagetype)
    {
        this.packagetype = packagetype;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }
}
