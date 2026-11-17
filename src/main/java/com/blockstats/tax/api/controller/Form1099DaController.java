package com.blockstats.tax.api.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.blockstats.common.mongo.userdata.delegate.entity.Form1099DaRecordEntity;
import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.common.tax.constant.Form1099DaSupportedExchanges;
import com.blockstats.common.tax.delegate.mongo.Form1099DaRecordDelegate;
import com.blockstats.common.tax.delegate.mongo.TransactionDelegate;
import com.blockstats.common.tax.dto.Form1099DaMatchStatus;
import com.blockstats.common.tax.dto.Form1099DaParsedLineDto;
import com.blockstats.common.tax.dto.Form1099DaReconciliationResponse;
import com.blockstats.common.tax.dto.Form1099DaReconciliationResultDto;
import com.blockstats.common.tax.dto.TaxFinancialYearDto;
import com.blockstats.common.tax.service.TaxConfigurationService;
import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.constants.StatsConstants;
import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.Violation;
import com.blockstats.commons.stats.StatsCollector;
import com.blockstats.tax.api.validator.RequestValidator;
import com.blockstats.tax.delegate.Form1099DaParsingDelegate;
import com.blockstats.tax.dto.Form1099DaAiParseResponse;
import com.blockstats.tax.dto.Form1099DaAiParsedLine;
import com.blockstats.tax.usa.service.Form1099DaReconciliationService;
import com.blockstats.tax.usa.service.PdfService;

import lombok.extern.slf4j.Slf4j;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.commons.constants.MDCConstants.*;

/**
 * Lets a user upload a broker-issued Form 1099-DA PDF and reconciles it against
 * Blockstats' own computed proceeds/cost-basis for the same tax year. The upload is
 * never written into the user's transactions or tax forms - it's purely a cross-check.
 * Every failure path throws InvalidRequestException with a specific, user-facing reason
 * rather than a generic 500, since this endpoint deals entirely with user-supplied files
 * that can be wrong in several distinct ways.
 */
@Slf4j
@RestController
@RequestMapping("/v1/tax/1099da")
public class Form1099DaController {

	private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

	private final RequestValidator requestValidator;
	private final TaxConfigurationService taxConfigurationService;
	private final Form1099DaParsingDelegate form1099DaParsingDelegate;
	private final Form1099DaReconciliationService form1099DaReconciliationService;
	private final Form1099DaRecordDelegate form1099DaRecordDelegate;
	private final TransactionDelegate transactionDelegate;
	private final ObjectMapper objectMapper;

	public Form1099DaController(RequestValidator requestValidator, TaxConfigurationService taxConfigurationService,
			Form1099DaParsingDelegate form1099DaParsingDelegate,
			Form1099DaReconciliationService form1099DaReconciliationService,
			Form1099DaRecordDelegate form1099DaRecordDelegate,
			TransactionDelegate transactionDelegate,
			ObjectMapper objectMapper) {
		this.requestValidator = requestValidator;
		this.taxConfigurationService = taxConfigurationService;
		this.form1099DaParsingDelegate = form1099DaParsingDelegate;
		this.form1099DaReconciliationService = form1099DaReconciliationService;
		this.form1099DaRecordDelegate = form1099DaRecordDelegate;
		this.transactionDelegate = transactionDelegate;
		this.objectMapper = objectMapper;
	}

	/**
	 * Every exchange this user already reconciled a 1099-DA for in this tax year, keyed by
	 * source - lets the frontend show the saved result directly instead of asking the user
	 * to upload the same broker's PDF again.
	 */
	@GetMapping("/reconciled")
	public ResponseEntity<Map<String, Form1099DaReconciliationResponse>> getReconciled(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@AuthenticationPrincipal UserContext userContext) {
		Map<String, Form1099DaReconciliationResponse> bySource = new HashMap<>();
		form1099DaRecordDelegate.findAllForYear(userContext.getUserId(), country, financialYear)
				.collectList().blockOptional().orElse(List.of())
				.forEach(record -> bySource.put(record.getSource(),
						convertToResponse(record.getReconciliation())));
		return ResponseEntity.ok(bySource);
	}

	/** Clears the saved record for an exchange+year so the user can re-upload and redo it. */
	@DeleteMapping("/reconciled")
	public ResponseEntity<Void> deleteReconciled(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@AuthenticationPrincipal UserContext userContext) {
		form1099DaRecordDelegate.delete(userContext.getUserId(), country, financialYear, source).block();
		return ResponseEntity.noContent().build();
	}

	/** Lets the user override which Form 8949 box (H&K = basis reported, I&L = not reported) a line belongs to. */
	@PatchMapping("/reconciled/box")
	public ResponseEntity<Form1099DaReconciliationResponse> updateBox(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@RequestParam(name = "asset") String asset,
			@RequestParam(name = "box") String box,
			@AuthenticationPrincipal UserContext userContext) {
		Form1099DaReconciliationResponse response = loadSavedResponse(userContext.getUserId(), country, financialYear, source);
		boolean found = false;
		for (Form1099DaReconciliationResultDto result : response.getResults()) {
			if (asset.equalsIgnoreCase(result.getAsset())) {
				result.setBox(box);
				found = true;
			}
		}
		if (!found) {
			throw invalidRequest("asset", "No reconciled line found for '" + asset + "' on " + source + ".");
		}
		form1099DaRecordDelegate.save(userContext.getUserId(), country, financialYear, source, response).block();
		return ResponseEntity.ok(response);
	}

	/**
	 * Confirms the user wants to use this exchange's reconciled 1099-DA data. On its own this
	 * doesn't touch the official tax report/8949 yet - it unlocks the separate
	 * reconciliation-summary document and the "continue with 1099-DA data or Blockstats cost
	 * basis" decision below, which is the thing that actually affects the official report.
	 */
	@PostMapping("/reconciled/confirm")
	public ResponseEntity<Form1099DaReconciliationResponse> confirmReconciliation(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@AuthenticationPrincipal UserContext userContext) {
		Form1099DaReconciliationResponse response = loadSavedResponse(userContext.getUserId(), country, financialYear, source);
		response.setFinalized(true);
		form1099DaRecordDelegate.save(userContext.getUserId(), country, financialYear, source, response).block();
		return ResponseEntity.ok(response);
	}

	/**
	 * Lets the user answer, for one confirmed exchange+year, whether their official Form
	 * 8949/Schedule D (the real document, not the separate supplemental one) should use this
	 * exchange's broker-reported figures per asset instead of Blockstats' own calculated ones.
	 * Only this user's own report is affected - other users' reports are generated from their
	 * own choices (or the normal calculated figures if they haven't opted in), never this one.
	 */
	@PostMapping("/reconciled/data-source")
	public ResponseEntity<Form1099DaReconciliationResponse> chooseDataSource(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@RequestParam(name = "useReportedData") boolean useReportedData,
			@AuthenticationPrincipal UserContext userContext) {
		Form1099DaReconciliationResponse response = loadSavedResponse(userContext.getUserId(), country, financialYear, source);
		if (!response.isFinalized()) {
			throw invalidRequest("source", "Please confirm this exchange's reconciliation first (\"Continue as 1099-DA tax\") "
					+ "before choosing a data source for your official tax report.");
		}
		response.setUseReportedData(useReportedData);
		form1099DaRecordDelegate.save(userContext.getUserId(), country, financialYear, source, response).block();
		return ResponseEntity.ok(response);
	}

	/**
	 * For the main Tax page to check, before letting the user download their official report,
	 * whether any confirmed 1099-DA reconciliation for this year still needs a data-source
	 * decision (useReportedData not yet answered) - so it can prompt them first instead of
	 * silently defaulting to Blockstats' own figures.
	 */
	@GetMapping("/reconciled/pending-decisions")
	public ResponseEntity<List<String>> getPendingDataSourceDecisions(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@AuthenticationPrincipal UserContext userContext) {
		List<String> pending = new java.util.ArrayList<>();
		form1099DaRecordDelegate.findAllForYear(userContext.getUserId(), country, financialYear)
				.collectList().blockOptional().orElse(List.of())
				.forEach(record -> {
					Form1099DaReconciliationResponse response = convertToResponse(record.getReconciliation());
					if (response.isFinalized() && response.getUseReportedData() == null) {
						pending.add(record.getSource());
					}
				});
		return ResponseEntity.ok(pending);
	}

	/**
	 * Marks an exchange as "I didn't receive a 1099-DA from them" - the other one-click
	 * option alongside Upload. Saved the same way as an uploaded reconciliation - finalized,
	 * but with useReportedData=false and no result lines - so PdfService.load1099DaOverrides()
	 * naturally skips it and that exchange's figures stay on Blockstats' own calculation.
	 */
	@PostMapping("/reconciled/not-received")
	public ResponseEntity<Form1099DaReconciliationResponse> markNotReceived(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@AuthenticationPrincipal UserContext userContext) {
		Form1099DaReconciliationResponse response = new Form1099DaReconciliationResponse();
		response.setFinalized(true);
		response.setUseReportedData(false);
		response.setResults(List.of());
		form1099DaRecordDelegate.save(userContext.getUserId(), country, financialYear, source, response).block();
		return ResponseEntity.ok(response);
	}

	/**
	 * Every exchange this user actually has transactions from in this tax year that's a known
	 * 1099-DA issuer (on-chain wallets like MetaMask never show up here), paired with whatever
	 * decision (if any) has been made for it. The frontend renders this as one list and uses
	 * "allResolved" to gate report downloads.
	 */
	@GetMapping("/reconciled/status")
	public ResponseEntity<Form1099DaStatusResponse> getStatus(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@AuthenticationPrincipal UserContext userContext) {
		TaxBO taxBO = buildTaxBO(new HttpHeaders(), userContext.getUserId(), country, financialYear);
		List<String> connectedSources = transactionDelegate.getDistinctSourcesByUserIdAndFinancialYear(taxBO);

		Map<String, Form1099DaReconciliationResponse> saved = new HashMap<>();
		form1099DaRecordDelegate.findAllForYear(userContext.getUserId(), country, financialYear)
				.collectList().blockOptional().orElse(List.of())
				.forEach(record -> saved.put(record.getSource(), convertToResponse(record.getReconciliation())));

		List<Form1099DaExchangeStatus> exchanges = new java.util.ArrayList<>();
		for (String source : connectedSources) {
			if (!Form1099DaSupportedExchanges.isSupported(source)) {
				continue;
			}
			Form1099DaReconciliationResponse response = saved.get(source);
			String status;
			if (response == null) {
				status = "PENDING";
			} else if (Boolean.FALSE.equals(response.getUseReportedData())) {
				status = "NOT_RECEIVED";
			} else {
				status = "UPLOADED";
			}
			exchanges.add(new Form1099DaExchangeStatus(source, status, response));
		}

		boolean allResolved = exchanges.stream().noneMatch(e -> "PENDING".equals(e.status()));
		return ResponseEntity.ok(new Form1099DaStatusResponse(exchanges, allResolved));
	}

	public record Form1099DaExchangeStatus(String source, String status, Form1099DaReconciliationResponse response) {
	}

	public record Form1099DaStatusResponse(List<Form1099DaExchangeStatus> exchanges, boolean allResolved) {
	}

	/**
	 * Downloads the separate, supplemental 1099-DA reconciliation summary PDF for one
	 * exchange - built fresh from the saved reconciliation (with the user's box choices),
	 * never written over the user's official IRS Report document.
	 */
	@GetMapping(value = "/reconciled/document", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> downloadReconciliationSummary(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@RequestHeader HttpHeaders httpHeaders,
			@AuthenticationPrincipal UserContext userContext) {
		Form1099DaReconciliationResponse response = loadSavedResponse(userContext.getUserId(), country, financialYear, source);
		TaxBO taxBO = this.buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear);
		PdfService pdfService = new PdfService(taxBO, null, null);
		byte[] pdf = pdfService.generateForm1099DaReconciliationSummary(source, response.getResults());

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_PDF);
		responseHeaders.setContentDispositionFormData("attachment",
				"1099DA-Reconciliation-" + source + "-" + financialYear + ".pdf");
		return new ResponseEntity<>(pdf, responseHeaders, org.springframework.http.HttpStatus.OK);
	}

	/**
	 * Downloads a Form 8949 built from this exchange's confirmed 1099-DA reconciliation. It
	 * never touches the official Blockstats IRS Report, which is generated separately from a
	 * global per-exchange box configuration shared across every user - this is an independent
	 * document scoped to just this exchange+year, using the user's own individual sold
	 * transactions (real acquisition/sale dates, real short/long-term holding period) rather
	 * than a per-asset aggregate. Caveat: it fills the pre-2025 IRS 8949 template's A/B and D/E
	 * boxes (basis reported/not reported, short/long-term) - the IRS added dedicated digital
	 * asset box letters (G/H/I short-term, J/K/L long-term) for 2025+ filings, which this will
	 * move to once an updated AcroForm template is available.
	 */
	@GetMapping(value = "/reconciled/form-8949", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> downloadForm8949(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@RequestHeader HttpHeaders httpHeaders,
			@AuthenticationPrincipal UserContext userContext) throws Exception {
		Form1099DaReconciliationResponse response = loadSavedResponse(userContext.getUserId(), country, financialYear, source);
		if (!response.isFinalized()) {
			throw invalidRequest("source", "Please confirm this exchange's reconciliation first (\"Continue as 1099-DA tax\") "
					+ "before generating a Form 8949.");
		}
		TaxBO taxBO = this.buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear);
		PdfService pdfService = new PdfService(taxBO, transactionDelegate, null);
		byte[] pdf = pdfService.generateAccurateForm8949(taxBO, source, response.getResults());

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_PDF);
		responseHeaders.setContentDispositionFormData("attachment",
				"Form8949-1099DA-" + source + "-" + financialYear + ".pdf");
		return new ResponseEntity<>(pdf, responseHeaders, org.springframework.http.HttpStatus.OK);
	}

	private Form1099DaReconciliationResponse loadSavedResponse(String userId, String country, String financialYear, String source) {
		Form1099DaRecordEntity record = form1099DaRecordDelegate.find(userId, country, financialYear, source).block();
		if (record == null || record.getReconciliation() == null) {
			throw invalidRequest("source", "No saved 1099-DA reconciliation found for " + source
					+ " in tax year " + financialYear + ". Please reconcile it first.");
		}
		return convertToResponse(record.getReconciliation());
	}

	private Form1099DaReconciliationResponse convertToResponse(Object raw) {
		return objectMapper.convertValue(raw, Form1099DaReconciliationResponse.class);
	}

	/** The exchanges Blockstats currently supports for 1099-DA reconciliation, for the frontend to populate its picker. */
	@GetMapping("/supported-exchanges")
	public ResponseEntity<Set<String>> getSupportedExchanges() {
		return ResponseEntity.ok(Form1099DaSupportedExchanges.SUPPORTED_SOURCES);
	}

	@PostMapping("/reconcile")
	public ResponseEntity<Form1099DaReconciliationResponse> reconcile(
			@RequestParam(name = "country", defaultValue = "USA", required = false) String country,
			@RequestParam(name = "financialYear") String financialYear,
			@RequestParam(name = "source") String source,
			@RequestParam("file") MultipartFile file,
			@RequestHeader HttpHeaders httpHeaders,
			@AuthenticationPrincipal UserContext userContext) {
		this.setMDCInfo(httpHeaders, userContext.getUserId(), country);
		StatsCollector.putInCurrentSession(StatsConstants.EVENT, "RECONCILE_1099DA");
		StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
		requestValidator.validateFinancialYear(country, financialYear);

		validateUpload(source, file);

		TaxBO taxBO = this.buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear);

		Form1099DaAiParseResponse parseResponse;
		try {
			parseResponse = form1099DaParsingDelegate.parse(file.getBytes(), file.getOriginalFilename());
		} catch (Exception e) {
			log.error("Failed to parse uploaded 1099-DA PDF for user:{}", userContext.getUserId(), e);
			throw invalidRequest("file", "We couldn't read this PDF. Please make sure it's not corrupted or password-protected and try again.");
		}

		if (parseResponse == null || parseResponse.getLines() == null || parseResponse.getLines().isEmpty()) {
			log.warn("No lines extracted from uploaded 1099-DA for user:{}", userContext.getUserId());
			throw invalidRequest("file", "This doesn't look like a Form 1099-DA (or a broker statement of the same data). "
					+ "Please check that you've uploaded the right file.");
		}

		List<Form1099DaParsedLineDto> parsedLines = parseResponse.getLines().stream()
				.map(line -> toParsedLineDto(line, source))
				.toList();

		Form1099DaReconciliationResponse response = form1099DaReconciliationService.reconcile(taxBO, parsedLines, financialYear);
		response.setBrokerMismatchWarning(detectBrokerMismatch(source, parseResponse.getBroker()));
		// Uploading IS the decision now (one merged step) - no separate "confirm" or
		// "choose data source" click. The user can still edit individual line boxes afterward
		// via PATCH /reconciled/box.
		response.setFinalized(true);
		response.setUseReportedData(true);

		form1099DaRecordDelegate.save(userContext.getUserId(), country, financialYear, source, response).block();

		log.info("1099-DA reconciliation completed for user:{}, source:{}, lines:{}, mismatches:{}, yearMismatch:{}, brokerMismatch:{}",
				userContext.getUserId(), source, response.getResults().size(),
				response.getResults().stream().filter(r -> r.getStatus() != Form1099DaMatchStatus.MATCH).count(),
				response.getYearMismatchWarning() != null, response.getBrokerMismatchWarning() != null);

		return ResponseEntity.ok(response);
	}

	/**
	 * The AI reads the broker's own name directly off the PDF - if that doesn't match the
	 * exchange the user picked in the dropdown, every line will look like "no matching data"
	 * even though nothing is actually missing; the user just selected the wrong exchange.
	 */
	private String detectBrokerMismatch(String selectedSource, String detectedBroker) {
		if (StringUtils.isBlank(detectedBroker)) {
			return null;
		}
		String normalizedSelected = selectedSource.trim().toUpperCase();
		String normalizedDetected = detectedBroker.replaceAll("[^A-Za-z]", "").toUpperCase();
		if (normalizedDetected.contains(normalizedSelected) || normalizedSelected.contains(normalizedDetected)) {
			return null;
		}
		return "You selected " + selectedSource
				+ ", but this PDF appears to be issued by " + detectedBroker
				+ ". Please re-upload with the correct exchange selected, otherwise every line will "
				+ "incorrectly show up as missing.";
	}

	private void validateUpload(String source, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw invalidRequest("file", "Please choose a 1099-DA PDF file to upload.");
		}
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw invalidRequest("file", "This file is too large (max 20MB). Please upload the original 1099-DA PDF, not a scanned copy.");
		}
		boolean looksLikePdf = "application/pdf".equalsIgnoreCase(file.getContentType())
				|| StringUtils.endsWithIgnoreCase(file.getOriginalFilename(), ".pdf");
		if (!looksLikePdf) {
			throw invalidRequest("file", "Only PDF files are supported. Please upload the 1099-DA PDF your exchange sent you.");
		}
		if (StringUtils.isBlank(source)) {
			throw invalidRequest("source", "Please select which exchange issued this 1099-DA.");
		}
		if (!Form1099DaSupportedExchanges.isSupported(source)) {
			throw invalidRequest("source", "We don't support 1099-DA reconciliation for '" + source
					+ "' yet. Supported exchanges: " + String.join(", ", Form1099DaSupportedExchanges.SUPPORTED_SOURCES) + ".");
		}
	}

	private InvalidRequestException invalidRequest(String field, String message) {
		Set<Violation> violations = new HashSet<>();
		violations.add(new Violation(field, message));
		return new InvalidRequestException(violations);
	}

	private Form1099DaParsedLineDto toParsedLineDto(Form1099DaAiParsedLine line, String fallbackSource) {
		Form1099DaParsedLineDto dto = new Form1099DaParsedLineDto();
		dto.setSource(StringUtils.isNotBlank(line.getSource()) ? line.getSource() : fallbackSource);
		dto.setAsset(line.getAsset());
		dto.setUnitsSold(line.getUnitsSold() != null ? line.getUnitsSold() : 0.0);
		dto.setProceeds(line.getProceeds());
		dto.setCostBasis(line.getCostBasis());
		dto.setDateAcquired(line.getDateAcquired());
		dto.setDateSold(line.getDateSold());
		return dto;
	}

	private TaxBO buildTaxBO(HttpHeaders headers, String userId, String country, String financialYear) {
		TaxFinancialYearDto taxFinancialYear = taxConfigurationService.getTaxFinancialYear(country, financialYear);

		return TaxBO.builder()
				.correlationId(headers.getFirst(X_CORRELATION_ID))
				.clientAppId(headers.getFirst(X_CLIENT_APP_ID))
				.requestDate(headers.getFirst(X_REQUEST_DATE))
				.userId(userId)
				.country(country)
				.financialYear(taxFinancialYear)
				.currency("USD")
				.build();
	}

	private void setMDCInfo(HttpHeaders headers, String userId, String country) {
		MDC.put(MDC_CORRELATION_ID, headers.getFirst(X_CORRELATION_ID));
		MDC.put(MDC_REQUEST_ID, headers.getFirst(X_REQUEST_ID));
		MDC.put(MDC_CREATED_TIME, headers.getFirst(X_REQUEST_DATE));
		MDC.put(CLIENT_APP_ID, headers.getFirst(X_CLIENT_APP_ID));
		MDC.put(MDC_USER_ID, String.format(MDC_USER_ID_FORMAT, userId));
		MDC.put(MDC_SOURCE, "TAX_1099DA");
		MDC.put(MDC_SOURCE_TYPE, StringUtils.toRootUpperCase(country));
	}

}
