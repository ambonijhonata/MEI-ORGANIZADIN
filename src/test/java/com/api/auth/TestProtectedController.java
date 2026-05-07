package com.api.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
class TestProtectedController {

    @GetMapping("/protected")
    String protectedEndpoint(@RequestParam(defaultValue = "false") boolean fail) {
        if (fail) {
            throw new RuntimeException("forced failure");
        }
        return "ok";
    }
}
