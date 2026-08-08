package com.contractguard.controller;

import com.contractguard.dto.DocumentDetailDto;
import com.contractguard.dto.DocumentSummaryDto;
import com.contractguard.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * All endpoints here require authentication (see SecurityConfig).
 *
 * @AuthenticationPrincipal injects the user Spring Security resolved from the
 * JWT. The client never sends a user id, so it cannot ask for someone else's
 * documents -- ownership is taken from the token, never from the request.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentSummaryDto> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) {

        DocumentSummaryDto summary = documentService.upload(file, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryDto>> list(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(documentService.listForUser(principal.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailDto> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(documentService.getDetail(id, principal.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        documentService.delete(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
