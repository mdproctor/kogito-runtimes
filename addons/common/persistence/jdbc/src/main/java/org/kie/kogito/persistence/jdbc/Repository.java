/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.persistence.jdbc;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

abstract class Repository {

    static final String INSERT = "INSERT INTO process_instances (id, payload, process_id, process_version, version) VALUES (?, ?, ?, ?, ?)";
    static final String FIND_ALL = "SELECT payload, version FROM process_instances WHERE process_id = ?";
    static final String FIND_BY_ID = "SELECT payload, version FROM process_instances WHERE process_id = ? and id = ?";
    static final String UPDATE = "UPDATE process_instances SET payload = ? WHERE process_id = ? and id = ?";
    static final String UPDATE_WITH_LOCK = "UPDATE process_instances SET payload = ?, version = ? WHERE process_id = ? and id = ? and version = ?";
    static final String DELETE = "DELETE FROM process_instances WHERE process_id = ? and id = ?";
    static final String PROCESS_VERSION_EQUALS_TO = "and process_version = ?";
    static final String PROCESS_VERSION_IS_NULL = "and process_version is null";

    static class Record {
        private final byte[] payload;
        private final long version;

        public byte[] getPayload() {
            return payload;
        }

        public long getVersion() {
            return version;
        }

        public Record(byte[] payload, long version) {
            this.payload = payload;
            this.version = version;
        }
    }

    abstract boolean tableExists();

    abstract void createTable();

    abstract void insertInternal(String processId, String processVersion, UUID id, byte[] payload);

    abstract void updateInternal(String processId, String processVersion, UUID id, byte[] payload);

    abstract boolean updateWithLock(String processId, String processVersion, UUID id, byte[] payload, long version);

    abstract boolean deleteInternal(String processId, String processVersion, UUID id);

    abstract Optional<Record> findByIdInternal(String processId, String processVersion, UUID id);

    abstract Stream<Record> findAllInternal(String processId, String processVersion);

    protected RuntimeException uncheckedException(Exception ex, String message, Object... param) {
        return new RuntimeException(String.format(message, param), ex);
    }
}
