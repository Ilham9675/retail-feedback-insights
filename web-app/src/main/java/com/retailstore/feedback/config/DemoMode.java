package com.retailstore.feedback.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Read-only mode for the public demo ({@code DEMO_MODE=true}): visitors browse the precomputed sample results,
 * and everything that would call Gemini (uploads, retries, generating summaries) is switched off,
 * so the demo needs no API key and nobody can spend quota.
 */
@Configuration
public class DemoMode implements WebMvcConfigurer {

    private final boolean enabled;

    public DemoMode(@Value("${feedback.demo.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!enabled) {
            return;
        }
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                boolean upload = request.getRequestURI().equals("/upload");
                boolean batchAction = request.getRequestURI().startsWith("/batches/") && "POST".equals(request.getMethod());
                if (upload || batchAction) {
                    throw new DemoModeException();
                }
                return true;
            }
        }).addPathPatterns("/upload", "/batches/**");
    }

    /** Thrown for actions that would call Gemini while the demo is read-only. */
    public static class DemoModeException extends RuntimeException {
        public DemoModeException() {
            super("This is a read-only demo: uploads and new Gemini requests are switched off. "
                    + "Run the app locally or with Docker to analyse your own reviews.");
        }
    }

    /** Exposes {@code demoMode} to every page and turns blocked actions into a friendly 403 page. */
    @ControllerAdvice
    public static class Advice {

        private final DemoMode demoMode;

        public Advice(DemoMode demoMode) {
            this.demoMode = demoMode;
        }

        @ModelAttribute("demoMode")
        public boolean demoMode() {
            return demoMode.isEnabled();
        }

        @ExceptionHandler(DemoModeException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        public String blocked(DemoModeException e, Model model) {
            model.addAttribute("title", "Not available in the demo");
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }
}
