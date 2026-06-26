package com.printshop.mis.job;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobTicketController {

    private final JobTicketService jobTicketService;
    private final ApiSupport api;

    public JobTicketController(JobTicketService jobTicketService, ApiSupport api) {
        this.jobTicketService = jobTicketService;
        this.api = api;
    }

    @GetMapping("/api/job-tickets")
    public ApiResponse<?> jobTickets(Principal principal) {
        return api.ok(jobTicketService.jobTickets(principal.getName()));
    }

    @PostMapping("/api/job-tickets")
    public ApiResponse<?> createJobTicket(Principal principal, @RequestBody JobTicket request) {
        return api.ok(jobTicketService.createJobTicket(principal.getName(), request));
    }

    @GetMapping("/api/job-tickets/{id}")
    public ApiResponse<?> getJobTicket(Principal principal, @PathVariable Long id) {
        return api.ok(jobTicketService.getJobTicket(principal.getName(), id));
    }

    @PutMapping("/api/job-tickets/{id}")
    public ApiResponse<?> updateJobTicket(Principal principal, @PathVariable Long id, @RequestBody JobTicket request) {
        return api.ok(jobTicketService.updateJobTicket(principal.getName(), id, request));
    }

    @DeleteMapping("/api/job-tickets/{id}")
    public ApiResponse<?> deleteJobTicket(Principal principal, @PathVariable Long id) {
        return api.ok(jobTicketService.deleteJobTicket(principal.getName(), id));
    }
}
