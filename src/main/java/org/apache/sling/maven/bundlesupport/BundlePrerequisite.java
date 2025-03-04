/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.maven.bundlesupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.osgi.framework.Version;

/**
 * Bundles that have to be installed as prerequisites to execute a goal.
 * The bundles are only installed if the preconditions are met.
 */
public final class BundlePrerequisite {

    /**
     * List of bundles that is installed when preconditions are met,
     * and if these bundles are not installed yet in the given (or a higher) version.
     */
    private final List<Bundle> bundles = new ArrayList<>();

    /**
     * List of precondition bundles that have to be already present in the given
     * (or higher) versions to install the bundles.
     */
    private final List<Bundle> preconditions = new ArrayList<>();

    public void addBundle(Bundle bundle) {
        bundles.add(bundle);
    }

    public void addPrecondition(Bundle bundle) {
        preconditions.add(bundle);
    }

    public List<Bundle> getBundles() {
        return bundles;
    }

    public List<Bundle> getPreconditions() {
        return preconditions;
    }

    /**
     * Described bundle with symbolic name and bundle version.
     */
    public static final class Bundle {

        private String groupId;
        private String artifactId;
        private String version;
        private String symbolicName;

        public Bundle() {
            // empty constructor
        }

        public Bundle(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public Bundle(String groupId, String artifactId, String version, String symbolicName) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.symbolicName = symbolicName;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getSymbolicName() {
            return Objects.toString(symbolicName, artifactId);
        }

        public void setSymbolicName(String symbolicName) {
            this.symbolicName = symbolicName;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getOsgiVersion() {
            if (this.version == null) {
                return null;
            }
            // convert to three digit osgi version number
            try {
                return Version.parseVersion(this.version).toString();
            } catch (IllegalArgumentException ex) {
                return this.version;
            }
        }

        @Override
        public String toString() {
            return "Bundle [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
                    + ", symbolicName=" + symbolicName + "]";
        }
    }
}
