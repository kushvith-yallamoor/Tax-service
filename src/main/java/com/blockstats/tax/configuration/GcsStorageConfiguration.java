package com.blockstats.tax.configuration;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import lombok.AllArgsConstructor;

@Configuration
@AllArgsConstructor
public class GcsStorageConfiguration {

    @Bean
    public Storage storage(CredentialsProvider credentialsProvider) throws IOException {
        return StorageOptions.newBuilder()
                .setCredentials(credentialsProvider.getCredentials())
                .build()
                .getService();
    }
}
