package org.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class DataSourceConfig {

    @Value("${SPRING_DATASOURCE_URL:jdbc:postgresql://db:5432/van_card_news_db}")
    private String url;

    @Value("${SPRING_DATASOURCE_USERNAME:van_web}")
    private String username;

    @Value("${SPRING_DATASOURCE_PASSWORD_FILE:#{null}}")
    private String passwordFilePath;

    @Bean
    @Primary
    public DataSource dataSource() {
        String password = "";

        if (passwordFilePath != null && !passwordFilePath.isBlank()) {
            Path path = Paths.get(passwordFilePath);
            if (Files.exists(path)) {
                try {
                    password = Files.readString(path).trim();
                } catch (IOException e) {
                    throw new IllegalStateException("Docker Secret 읽기 실패", e);
                }
            }
        }

        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(url)
                .username(username)
                .password(password)
                .build();
    }
}