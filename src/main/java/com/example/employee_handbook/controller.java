package com.example.employee_handbook;


import com.example.employee_handbook.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class controller {

    private final RagService ragService;

    @GetMapping("/ask")
    public String askAI(@RequestParam String question) {
        return ragService.askAI(question);
    }
}
