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
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Test
    void previewIsReadOnlyBoundedAndUsesVerifiedQuotedTable() throws Exception {
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        PreparedStatement previewStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        ResultSet previewResult = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement, previewStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(previewStatement.executeQuery()).thenReturn(previewResult);
        when(previewResult.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(2);
        when(meta.getColumnLabel(1)).thenReturn("user_id");
        when(meta.getColumnLabel(2)).thenReturn("user_name");
        when(previewResult.next()).thenReturn(true, true, false);
        when(previewResult.getString(1)).thenReturn("1", "2");
        when(previewResult.getString(2)).thenReturn("Alice", "Bob");

        MetadataService.TablePreviewView preview = service.tablePreview(1006L, "ods", "user_basic", 999);

        assertEquals(200, preview.limit());
        assertEquals(List.of("user_id", "user_name"), preview.columns());
        assertEquals(2, preview.rows().size());
        assertEquals(List.of("1", "Alice"), preview.rows().get(0));
        verify(existsStatement).setString(1, "ods");
        verify(existsStatement).setString(2, "user_basic");
        verify(existsStatement).setQueryTimeout(5);
        verify(connection).prepareStatement("SELECT * FROM `ods`.`user_basic` LIMIT 200");
        verify(previewStatement).setQueryTimeout(10);
        verify(previewStatement).setMaxRows(200);
    }

    @Test
    void previewRejectsMissingTableBeforeExecutingDynamicSelect() throws Exception {
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(false);

        Exception error = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.tablePreview(1006L, "ods", "missing_table", 50));

        assertTrue(error.getMessage().contains("数据表不存在"));
        verify(connection, times(1)).prepareStatement(anyString());
    }

}
