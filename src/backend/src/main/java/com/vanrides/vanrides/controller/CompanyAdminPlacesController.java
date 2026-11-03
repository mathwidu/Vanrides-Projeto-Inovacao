package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.service.PlacesProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/company-admins/places")
public class CompanyAdminPlacesController {

    @Autowired
    private PlacesProxyService placesProxyService;

    @GetMapping("/autocomplete")
    public ResponseEntity<List<PlacesProxyService.PlacePrediction>> autocomplete(
            @RequestParam String input,
            HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(placesProxyService.autocomplete(input));
    }

    @GetMapping("/details")
    public ResponseEntity<PlacesProxyService.PlaceDetail> details(
            @RequestParam String placeId,
            HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        PlacesProxyService.PlaceDetail detail = placesProxyService.details(placeId);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }
}
