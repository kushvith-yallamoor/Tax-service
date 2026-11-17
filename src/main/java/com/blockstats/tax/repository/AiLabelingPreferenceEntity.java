package com.blockstats.tax.repository;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@Setter
@Document(collection = "ai-labeling-preferences")
public class AiLabelingPreferenceEntity {

    @Id
    private String userId;
    private boolean enabled;
    private Date updatedAt;
}
