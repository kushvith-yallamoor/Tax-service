package com.blockstats.tax.service;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.common.tax.delegate.mongo.TransactionDelegate;
import com.blockstats.common.tax.dto.TaxDocumentDto;
import com.blockstats.common.tax.service.AbstractTaxService;
import com.blockstats.common.tax.service.GcsStorageSpringService;
import com.blockstats.commons.exception.NoDataFoundException;
import com.google.cloud.spring.storage.GoogleStorageResource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@Service
public class TaxDocumentService extends AbstractTaxService {

	public TaxDocumentService(TransactionDelegate txnDelegate, 
			GcsStorageSpringService gcsStorageSpringService) {
		super(txnDelegate);
		this.gcsStorageSpringService = gcsStorageSpringService;
	}

	public void downloadDocument(TaxBO taxBO, HttpServletResponse response) {
		TaxDocumentDto taxDoc = taxBO.getFinancialYear().getDocuments().get(taxBO.getDocumentName());
		GoogleStorageResource resource = gcsStorageSpringService.getGoogleStorageResource(buildBucketObjectName(taxBO, taxDoc));
		
		if (!resource.exists()) {
			throw new NoDataFoundException("File not found in bucket for given file name");
        }

        try (InputStream in = resource.getInputStream();
             OutputStream out = response.getOutputStream()) {
            response.setContentType(taxDoc.getContentType());
            response.setContentLengthLong(resource.contentLength());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + taxDoc.getBucketFileName().concat(".").concat(taxDoc.getExtension()) + "\"");

            byte[] buffer = new byte[8192]; // 8 KB buffer
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (Exception e) {
            log.error("Tax document download error: {}", e.getMessage(), e);
        }
    }

}
