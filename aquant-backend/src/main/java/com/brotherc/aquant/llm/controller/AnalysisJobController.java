package com.brotherc.aquant.llm.controller;

import com.brotherc.aquant.common.model.dto.ResponseDTO;
import com.brotherc.aquant.llm.model.vo.AnalysisJobCreateRequest;
import com.brotherc.aquant.llm.service.AnalysisJobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 智能分析作业管理接口。 */
@RestController
@RequestMapping("/analysis/jobs")
public class AnalysisJobController {
    private final AnalysisJobService service;
    private final ObjectMapper objectMapper;

    public AnalysisJobController(AnalysisJobService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseDTO<?> create(@Valid @RequestBody AnalysisJobCreateRequest request) {
        return ResponseDTO.success(service.create(request));
    }

    @GetMapping
    public ResponseDTO<Page<?>> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseDTO.success(service.list(page, size));
    }

    @GetMapping("/{jobId}")
    public ResponseDTO<?> get(@PathVariable String jobId) {
        return ResponseDTO.success(service.get(jobId));
    }

    @DeleteMapping("/{jobId}")
    public ResponseDTO<Void> delete(@PathVariable String jobId) {
        service.delete(jobId);
        return ResponseDTO.success();
    }

    @GetMapping("/{jobId}/results")
    public ResponseDTO<?> result(@PathVariable String jobId) {
        try { return ResponseDTO.success(objectMapper.readTree(service.result(jobId))); }
        catch (Exception exception) { return ResponseDTO.success(service.result(jobId)); }
    }

    @GetMapping("/{jobId}/events")
    public ResponseDTO<?> events(@PathVariable String jobId,
                                 @RequestParam(defaultValue = "0") long afterSeq) {
        return ResponseDTO.success(service.events(jobId, afterSeq));
    }

    @GetMapping(value = "/{jobId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String jobId,
                             @RequestParam(defaultValue = "0") long afterSeq) {
        return service.openStream(jobId, afterSeq);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseDTO<?> cancel(@PathVariable String jobId) {
        return ResponseDTO.success(service.cancel(jobId));
    }

    @PostMapping("/{jobId}/retry")
    public ResponseDTO<?> retry(@PathVariable String jobId) {
        return ResponseDTO.success(service.retry(jobId));
    }
}
