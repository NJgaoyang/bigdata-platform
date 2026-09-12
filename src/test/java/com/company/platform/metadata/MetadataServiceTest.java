package com.company.platform.metadata;

import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DataSourceView;
import com.company.platform.datasource.DynamicDataSourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MetadataServiceTest {
    private DataSourceService dataSources;
    private DynamicDataSourceManager connections;
    private JdbcTemplate platformJdbc;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private MetadataService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSources = mock(DataSourceService.class);
        connections = mock(DynamicDataSourceManager.class);
        platformJdbc = mock(JdbcTemplate.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        service = new MetadataService(dataSources, connections, platformJdbc);

        when(dataSources.get(1006L)).thenReturn(new DataSourceView(1006L, "starrocks8", DataSourceType.STARROCKS,
                "127.0.0.1", 9030, "ods", "root", "ACTIVE", true, null, null));
        when(dataSources.connectionInfo(1006L)).thenReturn(new DataSourceService.ConnectionInfo(1006L,
                DataSourceType.STARROCKS, "jdbc:mysql://127.0.0.1:9030/ods", "root", "", "ods"));
        when(connections.getConnection(eq(1006L), anyString(), eq("root"), eq(""))).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void readsStarRocksTableProfileFromInformationSchema() throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("TABLE_ROWS")).thenReturn(4L);
        when(resultSet.getLong("ESTIMATED_SIZE")).thenReturn(1315L);
        when(resultSet.wasNull()).thenReturn(false, false);
        when(resultSet.getTimestamp("CREATE_TIME")).thenReturn(Timestamp.valueOf("2026-09-05 19:55:57"));
        when(resultSet.getTimestamp("UPDATE_TIME")).thenReturn(Timestamp.valueOf("2026-09-05 19:56:20"));
        when(platformJdbc.queryForObject(anyString(), eq(String.class), eq(1006L), eq("ods"), eq("user_basic")))
                .thenReturn("Eric");

        MetadataService.TableProfileView profile = service.tableProfile(1006L, "ods", "user_basic");

        assertEquals(4L, profile.rowCount());
        assertEquals(1315L, profile.estimatedSizeBytes());
        assertEquals("Eric", profile.owner());
        assertEquals(LocalDateTime.of(2026, 9, 5, 19, 55, 57), profile.createTime());
        assertEquals(LocalDateTime.of(2026, 9, 5, 19, 56, 20), profile.updateTime());
        verify(statement).setString(1, "ods");
        verify(statement).setString(2, "user_basic");
    }

    @Test
    void blankOwnerClearsPlatformOwnershipWithoutInventingDatabaseOwner() throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("TABLE_ROWS")).thenReturn(4L);
        when(resultSet.getLong("ESTIMATED_SIZE")).thenReturn(1315L);
        when(resultSet.wasNull()).thenReturn(false, false);
        when(platformJdbc.queryForObject(anyString(), eq(String.class), eq(1006L), eq("ods"), eq("user_basic")))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        MetadataService.TableProfileView profile = service.setTableOwner(1006L, "ods", "user_basic", "  ", "admin");

        assertNull(profile.owner());
        verify(platformJdbc).update(startsWith("DELETE FROM metadata_table_owner"), eq(1006L), eq("ods"), eq("user_basic"));
    }
}
