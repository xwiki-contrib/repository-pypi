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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiPackageInfoDto
{
    private String maintainer;
    private String docs_url;
    private String requires_python;
    private List<String> requires_dist;
    private String maintainer_email;
    private String cheesecake_code_kwalitee_id;
    private String keywords;
    private String package_url;
    private String author;
    private String author_email;
    private String download_url;
    private String platform;
    private String version;
    private String cheesecake_documentation_id;
    private String _pypi_hidden;
    private String description;
    private String release_url;
    private PypiPackageDownloadsDto downloads;
    private String _pypi_ordering;
    private List<String> classifiers;
    private String name;
    private String bugtrack_url;
    private String license;
    private String summary;
    private String home_page;
    private String cheesecake_installability_id;

    public String getMaintainer()
    {
        return maintainer;
    }

    public void setMaintainer(String maintainer)
    {
        this.maintainer = maintainer;
    }

    public String getDocs_url()
    {
        return docs_url;
    }

    public void setDocs_url(String docs_url)
    {
        this.docs_url = docs_url;
    }

    public String getRequires_python()
    {
        return requires_python;
    }

    public void setRequires_python(String requires_python)
    {
        this.requires_python = requires_python;
    }

    public String getMaintainer_email()
    {
        return maintainer_email;
    }

    public void setMaintainer_email(String maintainer_email)
    {
        this.maintainer_email = maintainer_email;
    }

    public String getCheesecake_code_kwalitee_id()
    {
        return cheesecake_code_kwalitee_id;
    }

    public void setCheesecake_code_kwalitee_id(String cheesecake_code_kwalitee_id)
    {
        this.cheesecake_code_kwalitee_id = cheesecake_code_kwalitee_id;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public String getPackage_url()
    {
        return package_url;
    }

    public void setPackage_url(String package_url)
    {
        this.package_url = package_url;
    }

    public String getAuthor()
    {
        return author;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public String getAuthor_email()
    {
        return author_email;
    }

    public void setAuthor_email(String author_email)
    {
        this.author_email = author_email;
    }

    public String getDownload_url()
    {
        return download_url;
    }

    public void setDownload_url(String download_url)
    {
        this.download_url = download_url;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getCheesecake_documentation_id()
    {
        return cheesecake_documentation_id;
    }

    public void setCheesecake_documentation_id(String cheesecake_documentation_id)
    {
        this.cheesecake_documentation_id = cheesecake_documentation_id;
    }

    public String get_pypi_hidden()
    {
        return _pypi_hidden;
    }

    public void set_pypi_hidden(String _pypi_hidden)
    {
        this._pypi_hidden = _pypi_hidden;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getRelease_url()
    {
        return release_url;
    }

    public void setRelease_url(String release_url)
    {
        this.release_url = release_url;
    }

    public PypiPackageDownloadsDto getDownloads()
    {
        return downloads;
    }

    public void setDownloads(PypiPackageDownloadsDto downloads)
    {
        this.downloads = downloads;
    }

    public String get_pypi_ordering()
    {
        return _pypi_ordering;
    }

    public void set_pypi_ordering(String _pypi_ordering)
    {
        this._pypi_ordering = _pypi_ordering;
    }

    public List<String> getClassifiers()
    {
        return classifiers;
    }

    public void setClassifiers(List<String> classifiers)
    {
        this.classifiers = classifiers;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getBugtrack_url()
    {
        return bugtrack_url;
    }

    public void setBugtrack_url(String bugtrack_url)
    {
        this.bugtrack_url = bugtrack_url;
    }

    public String getLicense()
    {
        return license;
    }

    public void setLicense(String license)
    {
        this.license = license;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getHome_page()
    {
        return home_page;
    }

    public void setHome_page(String home_page)
    {
        this.home_page = home_page;
    }

    public String getCheesecake_installability_id()
    {
        return cheesecake_installability_id;
    }

    public void setCheesecake_installability_id(String cheesecake_installability_id)
    {
        this.cheesecake_installability_id = cheesecake_installability_id;
    }

    public List<String> getRequires_dist()
    {
        return requires_dist;
    }

    public void setRequires_dist(List<String> requires_dist)
    {
        this.requires_dist = requires_dist;
    }
}
