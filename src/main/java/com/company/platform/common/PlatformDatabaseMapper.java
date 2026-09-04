package com.company.platform.common;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformDatabaseMapper {
    @Select("SELECT 1")
    int ping();
}
