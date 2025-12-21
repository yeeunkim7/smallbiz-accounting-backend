package com.example.accounting.journal.controller;

import com.example.accounting.journal.domain.Journal;
import com.example.accounting.journal.service.JournalService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/journals")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @PostMapping
    public Journal createJournal(
            @RequestParam LocalDate journalDate,
            @RequestParam String description,
            @RequestParam Long debitAmount,
            @RequestParam Long creditAmount,
            @RequestParam(required = false) Long vendorId,
            @RequestParam Long accountSubjectId
    ) {
        return journalService.registerJournal(
                journalDate,
                description,
                debitAmount,
                creditAmount,
                vendorId,
                accountSubjectId
        );
    }

    @GetMapping
    public List<Journal> getJournals() {
        return journalService.findAllJournals();
    }
}
