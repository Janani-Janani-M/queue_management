package com.queuecare.controller;

import com.queuecare.dto.ApiResponse;
import com.queuecare.dto.CitizenResponse;
import com.queuecare.dto.QueueStatsResponse;
import com.queuecare.dto.RegisterRequest;
import com.queuecare.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * QueueController – REST API surface for the QueueCare frontend.
 *
 * All endpoints are under /api/queue and return JSON.
 * CORS is globally configured in CorsConfig.
 *
 * Endpoints:
 *   GET  /api/queue/stats           → full queue snapshot + statistics
 *   POST /api/queue/register        → register a new citizen
 *   POST /api/queue/call-next       → call highest-priority waiting citizen
 *   POST /api/queue/call/{token}    → call a specific citizen by token
 *   POST /api/queue/mark-served     → mark current citizen as served
 *   POST /api/queue/skip/{token}    → skip a citizen by token
 *   POST /api/queue/reset           → reset to demo seed data
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    // ── GET /api/queue/stats ──────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<QueueStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok("Stats retrieved.", queueService.getStats()));
    }

    // ── POST /api/queue/register ──────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CitizenResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        ApiResponse<CitizenResponse> response = queueService.register(request);
        HttpStatus status = response.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    // ── POST /api/queue/call-next ─────────────────────────────────────
    @PostMapping("/call-next")
    public ResponseEntity<ApiResponse<CitizenResponse>> callNext() {
        ApiResponse<CitizenResponse> response = queueService.callNext();
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

    // ── POST /api/queue/call/{token} ──────────────────────────────────
    @PostMapping("/call/{token}")
    public ResponseEntity<ApiResponse<CitizenResponse>> callByToken(
            @PathVariable String token) {

        ApiResponse<CitizenResponse> response = queueService.callByToken(token.toUpperCase());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }

    // ── POST /api/queue/mark-served ───────────────────────────────────
    @PostMapping("/mark-served")
    public ResponseEntity<ApiResponse<CitizenResponse>> markServed() {
        ApiResponse<CitizenResponse> response = queueService.markServed();
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

    // ── POST /api/queue/skip/{token} ──────────────────────────────────
    @PostMapping("/skip/{token}")
    public ResponseEntity<ApiResponse<CitizenResponse>> skip(
            @PathVariable String token) {

        ApiResponse<CitizenResponse> response = queueService.skip(token.toUpperCase());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }

    // ── POST /api/queue/reset ─────────────────────────────────────────
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<QueueStatsResponse>> reset() {
        return ResponseEntity.ok(queueService.resetDemo());
    }

    // ── GET /api/queue/ticket/{token} ─────────────────────────────────
    @GetMapping("/ticket/{token}")
    public ResponseEntity<ApiResponse<CitizenResponse>> getTicket(
            @PathVariable String token) {
        ApiResponse<CitizenResponse> response = queueService.getTicketByToken(token);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }

    // ── GET /api/queue/dsa-state ──────────────────────────────────────
    /**
     * Returns a snapshot of the internal DSA data structures:
     * the binary min-heap array (CitizenPriorityQueue) and the
     * FIFO standard lane (CustomQueue), plus scheduling metadata.
     *
     * <p>The frontend's Algorithm Visualizer section polls this endpoint
     * to animate the heap tree and FIFO strip in real time.
     */
    @GetMapping("/dsa-state")
    public ResponseEntity<ApiResponse<com.queuecare.dto.DSAStateResponse>> getDSAState() {
        return ResponseEntity.ok(
                ApiResponse.ok("DSA state retrieved.", queueService.getDSAState()));
    }
}
