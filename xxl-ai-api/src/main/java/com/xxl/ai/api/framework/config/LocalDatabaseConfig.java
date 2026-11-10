package com.xxl.ai.api.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 本地调试数据库初始化（local profile）：H2 库为空时执行建表与种子数据脚本，已初始化则跳过
 *
 * @author xxl-ai 2026-11-10
 */
@Configuration
@Profile("local")
public class LocalDatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(LocalDatabaseConfig.class);

    /** 建表与种子数据脚本 */
    private static final String SCHEMA_LOCATION = "classpath:db/tables_xxl_ai_h2.sql";
    /** 初始化标识表：存在即视为已初始化 */
    private static final String MARKER_TABLE = "xxl_ai_user";

    /**
     * 脚本初始化器：类型为 DataSourceScriptDatabaseInitializer，Spring Boot 会保证其先于 Mapper 等数据源使用方执行
     */
    @Bean
    public DataSourceScriptDatabaseInitializer localDatabaseInitializer(DataSource dataSource) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(SCHEMA_LOCATION));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setEncoding(StandardCharsets.UTF_8);

        return new DataSourceScriptDatabaseInitializer(dataSource, settings) {
            @Override
            protected void runScripts(Scripts scripts) {
                // 已初始化：跳过，避免种子数据重复插入
                if (markerTableExists(getDataSource())) {
                    return;
                }
                logger.info("本地数据库为空，执行初始化脚本: {}", SCHEMA_LOCATION);
                super.runScripts(scripts);
            }
        };
    }

    /**
     * 判断初始化标识表是否存在
     */
    private static boolean markerTableExists(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, MARKER_TABLE, new String[]{"TABLE"})) {
            return tables.next();
        } catch (SQLException e) {
            throw new IllegalStateException("本地数据库检查失败: " + e.getMessage(), e);
        }
    }

}
