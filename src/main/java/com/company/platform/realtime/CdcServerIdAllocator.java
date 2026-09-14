package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Allocates non-overlapping MySQL replication server-id ranges for Flink CDC jobs. */
@Service
public class CdcServerIdAllocator {
    static final int POOL_START = 50_000;
    static final int POOL_END = 59_999;
    private final JdbcTemplate jdbc;

    public CdcServerIdAllocator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public synchronized String allocate(long jobId, int parallelism) {
        int size = Math.max(1, Math.min(128, parallelism));
        Allocation existing = jdbc.query("SELECT job_id,start_id,end_id,parallelism FROM cdc_server_id_allocation WHERE job_id=?",
                (rs, n) -> new Allocation(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)), jobId)
                .stream().findFirst().orElse(null);
        if (existing != null && existing.parallelism() == size) return format(existing.startId(), existing.endId());

        List<Allocation> used = jdbc.query("SELECT job_id,start_id,end_id,parallelism FROM cdc_server_id_allocation WHERE job_id<>? ORDER BY start_id",
                (rs, n) -> new Allocation(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)), jobId)
                .stream().sorted(Comparator.comparingInt(Allocation::startId)).toList();
        int candidate = POOL_START;
        for (Allocation allocation : used) {
            if (candidate + size - 1 < allocation.startId()) break;
            if (candidate <= allocation.endId()) candidate = allocation.endId() + 1;
        }
        int end = candidate + size - 1;
        if (end > POOL_END) throw new BadRequestException("CDC Server ID 池已耗尽，请扩容 50000-59999 的平台分配范围");
        jdbc.update("INSERT INTO cdc_server_id_allocation(job_id,start_id,end_id,parallelism) VALUES(?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE start_id=VALUES(start_id),end_id=VALUES(end_id),parallelism=VALUES(parallelism)",
                jobId, candidate, end, size);
        return format(candidate, end);
    }

    public String current(long jobId) {
        return jdbc.query("SELECT start_id,end_id FROM cdc_server_id_allocation WHERE job_id=?",
                (rs, n) -> format(rs.getInt(1), rs.getInt(2)), jobId).stream().findFirst().orElse("");
    }

    private String format(int start, int end) { return start == end ? String.valueOf(start) : start + "-" + end; }
    private record Allocation(long jobId, int startId, int endId, int parallelism) { }
}
