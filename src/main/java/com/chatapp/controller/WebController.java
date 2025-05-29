package com.chatapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/chat-test")
    public String chatTest() {
        return "chat-test.html";
    }

    @GetMapping("/test")
    public String test() {
        return "test.html";
    }
    @GetMapping("/")
    public String index() {
        return "index.html";
    }
}