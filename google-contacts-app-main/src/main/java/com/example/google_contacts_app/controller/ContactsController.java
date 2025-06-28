package com.example.google_contacts_app.controller;

import com.example.google_contacts_app.model.Contact;
import com.example.google_contacts_app.service.GoogleContactsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

@Controller
public class ContactsController {

    private static final Logger logger = LoggerFactory.getLogger(ContactsController.class);

    @Autowired
    private GoogleContactsService googleContactsService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/contacts")
    public String contacts(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            Authentication authentication,
            Model model) {

        logger.debug("Contacts endpoint accessed");
        logger.debug("Authentication present: {}", authentication != null);
        logger.debug("Authorized client present: {}", authorizedClient != null);

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            logger.debug("User info: {}", oauth2User.getAttributes());
            model.addAttribute("userName", oauth2User.getAttribute("name"));
        }

        if (authorizedClient == null) {
            logger.error("OAuth2AuthorizedClient is null - user may not be properly authenticated");
            model.addAttribute("error", "Authentication failed. Please try logging in again.");
            return "contacts";
        }

        logger.debug("Client registration ID: {}", authorizedClient.getClientRegistration().getRegistrationId());
        logger.debug("Access token expires at: {}", authorizedClient.getAccessToken().getExpiresAt());

        try {
            logger.debug("Attempting to fetch contacts...");
            List<Contact> contacts = googleContactsService.getContacts(authorizedClient);

            logger.info("Successfully fetched {} contacts", contacts.size());

            model.addAttribute("contacts", contacts);
            model.addAttribute("contactCount", contacts.size());

            if (contacts.isEmpty()) {
                model.addAttribute("message", "No contacts found. This could mean your Google account has no contacts, or there may be a permissions issue.");
            }

        } catch (IOException e) {
            logger.error("IOException while fetching contacts: {}", e.getMessage(), e);
            model.addAttribute("error", "Network error while fetching contacts: " + e.getMessage());
        } catch (GeneralSecurityException e) {
            logger.error("GeneralSecurityException while fetching contacts: {}", e.getMessage(), e);
            model.addAttribute("error", "Security error while fetching contacts: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching contacts: {}", e.getMessage(), e);
            model.addAttribute("error", "Unexpected error: " + e.getMessage());
        }

        return "contacts";
    }

    // API Endpoints for CRUD operations

    @PostMapping("/api/contacts")
    @ResponseBody
    public ResponseEntity<?> addContact(
            @RequestBody Contact contact,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {

        logger.debug("Adding new contact: {}", contact.getName());

        try {
            Contact createdContact = googleContactsService.createContact(contact, authorizedClient);
            logger.info("Successfully created contact: {}", createdContact.getName());
            return ResponseEntity.ok(createdContact);
        } catch (Exception e) {
            logger.error("Error creating contact: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create contact: " + e.getMessage()));
        }
    }

    @PutMapping("/api/contacts/{index}")
    @ResponseBody
    public ResponseEntity<?> updateContact(
            @PathVariable int index,
            @RequestBody Contact contact,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {

        logger.debug("Updating contact at index {}: {}", index, contact.getName());

        try {
            Contact updatedContact = googleContactsService.updateContact(index, contact, authorizedClient);
            logger.info("Successfully updated contact: {}", updatedContact.getName());
            return ResponseEntity.ok(updatedContact);
        } catch (Exception e) {
            logger.error("Error updating contact: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to update contact: " + e.getMessage()));
        }
    }

    @DeleteMapping("/api/contacts/{index}")
    @ResponseBody
    public ResponseEntity<?> deleteContact(
            @PathVariable int index,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {

        logger.debug("Deleting contact at index: {}", index);

        try {
            googleContactsService.deleteContact(index, authorizedClient);
            logger.info("Successfully deleted contact at index: {}", index);
            return ResponseEntity.ok(Map.of("message", "Contact deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting contact: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to delete contact: " + e.getMessage()));
        }
    }

    @GetMapping("/debug")
    public String debug(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            Authentication authentication,
            Model model) {

        model.addAttribute("authPresent", authentication != null);
        model.addAttribute("clientPresent", authorizedClient != null);

        if (authorizedClient != null) {
            model.addAttribute("tokenScopes", authorizedClient.getAccessToken().getScopes());
            model.addAttribute("tokenExpiry", authorizedClient.getAccessToken().getExpiresAt());
        }

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            model.addAttribute("userAttributes", oauth2User.getAttributes());
        }

        return "debug";
    }
}