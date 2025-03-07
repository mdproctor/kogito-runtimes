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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.kogito.persistence.jdbc.DatabaseType.getDataBaseType;

public class GenericRepository extends Repository {

    private static final String PAYLOAD = "payload";
    private static final String VERSION = "version";

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericRepository.class);

    private final DataSource dataSource;

    public GenericRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    boolean tableExists() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseType databaseType = getDataBaseType(connection);
            final DatabaseMetaData metaData = connection.getMetaData();
            final String[] types = { "TABLE" };
            ResultSet tables = metaData.getTables(null, null, databaseType.getTableNamePattern(), types);
            while (tables.next()) {
                LOGGER.debug("Found process_instance table");
                return true;
            }
            return false;
        } catch (SQLException e) {
            var msg = "Failed to read table metadata";
            throw new RuntimeException(msg);
        }
    }

    @Override
    void createTable() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseType databaseType = getDataBaseType(connection);
            final List<String> statements = FileLoader.getQueryFromFile(databaseType.getDbIdentifier(), "create_tables");
            for (String s : statements) {
                try (PreparedStatement prepareStatement = connection.prepareStatement(s.trim())) {
                    prepareStatement.execute();
                }
            }
            LOGGER.info("DDL successfully done for ProcessInstance");
        } catch (SQLException e) {
            var msg = "Error creating process_instances table, the database should be configured properly before starting the application";
            LOGGER.error(msg, e);
            throw new RuntimeException(msg);
        }
    }

    @Override
    void insertInternal(String processId, String processVersion, UUID id, byte[] payload) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, id.toString());
            statement.setBytes(2, payload);
            statement.setString(3, processId);
            statement.setString(4, processVersion);
            statement.setLong(5, 0L);
            statement.executeUpdate();
        } catch (Exception e) {
            throw uncheckedException(e, "Error inserting process instance %s", id);
        }
    }

    @Override
    void updateInternal(String processId, String processVersion, UUID id, byte[] payload) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(UPDATE, processVersion))) {
            statement.setBytes(1, payload);
            statement.setString(2, processId);
            statement.setString(3, id.toString());
            if (processVersion != null) {
                statement.setString(4, processVersion);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw uncheckedException(e, "Error updating process instance %s", id);
        }
    }

    @Override
    boolean updateWithLock(String processId, String processVersion, UUID id, byte[] payload, long version) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(UPDATE_WITH_LOCK, processVersion))) {
            statement.setBytes(1, payload);
            statement.setLong(2, version + 1);
            statement.setString(3, processId);
            statement.setString(4, id.toString());
            statement.setLong(5, version);
            if (processVersion != null) {
                statement.setString(6, processVersion);
            }
            int count = statement.executeUpdate();
            return count == 1;
        } catch (Exception e) {
            throw uncheckedException(e, "Error updating with lock process instance %s", id);
        }
    }

    @Override
    boolean deleteInternal(String processId, String processVersion, UUID id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(DELETE, processVersion))) {
            statement.setString(1, processId);
            statement.setString(2, id.toString());
            if (processVersion != null) {
                statement.setString(3, processVersion);
            }
            int count = statement.executeUpdate();
            return count == 1;
        } catch (Exception e) {
            throw uncheckedException(e, "Error deleting process instance %s", id);
        }
    }

    private Record from(ResultSet rs) throws SQLException {
        return new Record(rs.getBytes(PAYLOAD), rs.getLong(VERSION));
    }

    @Override
    Optional<Record> findByIdInternal(String processId, String processVersion, UUID id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(FIND_BY_ID, processVersion))) {
            statement.setString(1, processId);
            statement.setString(2, id.toString());
            if (processVersion != null) {
                statement.setString(3, processVersion);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(from(resultSet));
                }
            }
        } catch (Exception e) {
            throw uncheckedException(e, "Error finding process instance %s", id);
        }
        return Optional.empty();
    }

    private static class CloseableWrapper implements Runnable {

        private Deque<AutoCloseable> wrapped = new ArrayDeque<>();

        public <T extends AutoCloseable> T nest(T c) {
            wrapped.addFirst(c);
            return c;
        }

        @Override
        public void run() {
            try {
                close();
            } catch (Exception ex) {
                throw new RuntimeException("Error closing resources", ex);
            }
        }

        public void close() throws Exception {
            Exception exception = null;
            for (AutoCloseable wrap : wrapped) {
                try {
                    wrap.close();
                } catch (Exception ex) {
                    if (exception != null) {
                        ex.addSuppressed(exception);
                    }
                    exception = ex;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }

    @Override
    Stream<Record> findAllInternal(String processId, String processVersion) {
        CloseableWrapper close = new CloseableWrapper();
        try {
            Connection connection = close.nest(dataSource.getConnection());
            PreparedStatement statement = close.nest(connection.prepareStatement(sqlIncludingVersion(FIND_ALL, processVersion)));
            statement.setString(1, processId);
            if (processVersion != null) {
                statement.setString(2, processVersion);
            }
            ResultSet resultSet = close.nest(statement.executeQuery());
            return StreamSupport.stream(new Spliterators.AbstractSpliterator<Record>(
                    Long.MAX_VALUE, Spliterator.ORDERED) {
                @Override
                public boolean tryAdvance(Consumer<? super Record> action) {
                    try {
                        boolean hasNext = resultSet.next();
                        if (hasNext) {
                            action.accept(from(resultSet));
                        }
                        return hasNext;
                    } catch (SQLException e) {
                        throw uncheckedException(e, "Error finding all process instances, for processId %s", processId);
                    }
                }
            }, false).onClose(close);
        } catch (SQLException e) {
            try {
                close.close();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw uncheckedException(e, "Error finding all process instances, for processId %s", processId);
        }
    }

    private static String sqlIncludingVersion(String statement, String processVersion) {
        return statement + " " + (processVersion == null ? PROCESS_VERSION_IS_NULL : PROCESS_VERSION_EQUALS_TO);
    }
}
