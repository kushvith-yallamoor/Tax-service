package com.blockstats.tax.exception.model;

import lombok.*;

/**
 * This class used for global error response model
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String error;

}
