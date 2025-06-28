package com.example.google_contacts_app.service;

import com.example.google_contacts_app.model.Contact;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleContactsService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleContactsService.class);
    private List<Person> cachedConnections = new ArrayList<>();

    public List<Contact> getContacts(OAuth2AuthorizedClient authorizedClient)
            throws IOException, GeneralSecurityException {

        logger.debug("Starting to fetch contacts...");
        logger.debug("Access token present: {}", authorizedClient.getAccessToken() != null);
        logger.debug("Token scopes: {}", authorizedClient.getAccessToken().getScopes());

        try {
            PeopleService peopleService = createPeopleService(authorizedClient);

            logger.debug("Making API call to fetch connections...");
            ListConnectionsResponse response = peopleService.people().connections()
                    .list("people/me")
                    .setPersonFields("names,emailAddresses,phoneNumbers")
                    .setPageSize(100)
                    .execute();

            logger.debug("API call completed successfully");

            List<Person> connections = response.getConnections();
            cachedConnections = connections != null ? connections : new ArrayList<>();
            logger.debug("Raw connections count: {}", cachedConnections.size());

            if (cachedConnections.isEmpty()) {
                logger.warn("No connections returned from API");
                return new ArrayList<>();
            }

            // Convert to Contact objects
            List<Contact> contacts = cachedConnections.stream()
                    .map(this::convertToContact)
                    .filter(contact -> contact.getName() != null && !contact.getName().isEmpty())
                    .collect(Collectors.toList());

            logger.debug("Processed contacts count: {}", contacts.size());
            return contacts;

        } catch (Exception e) {
            logger.error("Error fetching contacts: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Contact createContact(Contact contact, OAuth2AuthorizedClient authorizedClient)
            throws IOException, GeneralSecurityException {

        logger.debug("Creating new contact: {}", contact.getName());
        logger.debug("Access token scopes: {}", authorizedClient.getAccessToken().getScopes());

        try {
            PeopleService peopleService = createPeopleService(authorizedClient);

            // Create a new Person object
            Person person = new Person();

            // Set name
            if (contact.getName() != null && !contact.getName().isEmpty()) {
                Name name = new Name();
                name.setDisplayName(contact.getName());
                name.setGivenName(contact.getName()); // Add given name too
                person.setNames(List.of(name));
            }

            // Set email addresses
            if (contact.getEmailAddresses() != null && !contact.getEmailAddresses().isEmpty()) {
                List<EmailAddress> emailAddresses = contact.getEmailAddresses().stream()
                        .map(email -> {
                            EmailAddress emailAddr = new EmailAddress();
                            emailAddr.setValue(email);
                            emailAddr.setType("other");
                            return emailAddr;
                        })
                        .collect(Collectors.toList());
                person.setEmailAddresses(emailAddresses);
            }

            // Set phone numbers
            if (contact.getPhoneNumbers() != null && !contact.getPhoneNumbers().isEmpty()) {
                List<PhoneNumber> phoneNumbers = contact.getPhoneNumbers().stream()
                        .map(phone -> {
                            PhoneNumber phoneNum = new PhoneNumber();
                            phoneNum.setValue(phone);
                            phoneNum.setType("other");
                            return phoneNum;
                        })
                        .collect(Collectors.toList());
                person.setPhoneNumbers(phoneNumbers);
            }

            logger.debug("Attempting to create contact via API...");

            // Create the contact
            Person createdPerson = peopleService.people()
                    .createContact(person)
                    .execute();

            logger.info("Successfully created contact with resource name: {}", createdPerson.getResourceName());

            // Add to cached connections
            cachedConnections.add(createdPerson);

            return convertToContact(createdPerson);

        } catch (GoogleJsonResponseException e) {
            logger.error("Google API error creating contact: Status: {}, Message: {}, Details: {}",
                    e.getStatusCode(), e.getMessage(), e.getDetails());
            throw new RuntimeException("Google API error: " + e.getDetails().getMessage(), e);
        } catch (Exception e) {
            logger.error("Error creating contact: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create contact: " + e.getMessage(), e);
        }
    }

    public Contact updateContact(int index, Contact contact, OAuth2AuthorizedClient authorizedClient)
            throws IOException, GeneralSecurityException {

        logger.debug("Updating contact at index {}: {}", index, contact.getName());
        logger.debug("Access token scopes: {}", authorizedClient.getAccessToken().getScopes());

        if (index < 0 || index >= cachedConnections.size()) {
            throw new IllegalArgumentException("Invalid contact index: " + index);
        }

        try {
            PeopleService peopleService = createPeopleService(authorizedClient);
            Person existingPerson = cachedConnections.get(index);

            // Create updated person object
            Person updatedPerson = new Person();
            updatedPerson.setResourceName(existingPerson.getResourceName());
            updatedPerson.setEtag(existingPerson.getEtag());

            // Set name
            if (contact.getName() != null && !contact.getName().isEmpty()) {
                Name name = new Name();
                name.setDisplayName(contact.getName());
                name.setGivenName(contact.getName());
                updatedPerson.setNames(List.of(name));
            }

            // Set email addresses
            if (contact.getEmailAddresses() != null && !contact.getEmailAddresses().isEmpty()) {
                List<EmailAddress> emailAddresses = contact.getEmailAddresses().stream()
                        .map(email -> {
                            EmailAddress emailAddr = new EmailAddress();
                            emailAddr.setValue(email);
                            emailAddr.setType("other");
                            return emailAddr;
                        })
                        .collect(Collectors.toList());
                updatedPerson.setEmailAddresses(emailAddresses);
            }

            // Set phone numbers
            if (contact.getPhoneNumbers() != null && !contact.getPhoneNumbers().isEmpty()) {
                List<PhoneNumber> phoneNumbers = contact.getPhoneNumbers().stream()
                        .map(phone -> {
                            PhoneNumber phoneNum = new PhoneNumber();
                            phoneNum.setValue(phone);
                            phoneNum.setType("other");
                            return phoneNum;
                        })
                        .collect(Collectors.toList());
                updatedPerson.setPhoneNumbers(phoneNumbers);
            }

            logger.debug("Attempting to update contact via API...");

            // Update the contact
            Person result = peopleService.people()
                    .updateContact(existingPerson.getResourceName(), updatedPerson)
                    .setUpdatePersonFields("names,emailAddresses,phoneNumbers")
                    .execute();

            logger.info("Successfully updated contact: {}", result.getResourceName());

            // Update cached connections
            cachedConnections.set(index, result);

            return convertToContact(result);

        } catch (GoogleJsonResponseException e) {
            logger.error("Google API error updating contact: Status: {}, Message: {}, Details: {}",
                    e.getStatusCode(), e.getMessage(), e.getDetails());
            throw new RuntimeException("Google API error: " + e.getDetails().getMessage(), e);
        } catch (Exception e) {
            logger.error("Error updating contact: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update contact: " + e.getMessage(), e);
        }
    }

    public void deleteContact(int index, OAuth2AuthorizedClient authorizedClient)
            throws IOException, GeneralSecurityException {

        logger.debug("Deleting contact at index: {}", index);
        logger.debug("Access token scopes: {}", authorizedClient.getAccessToken().getScopes());

        if (index < 0 || index >= cachedConnections.size()) {
            throw new IllegalArgumentException("Invalid contact index: " + index);
        }

        try {
            PeopleService peopleService = createPeopleService(authorizedClient);
            Person personToDelete = cachedConnections.get(index);

            logger.debug("Attempting to delete contact via API...");

            // Delete the contact
            peopleService.people()
                    .deleteContact(personToDelete.getResourceName())
                    .execute();

            logger.info("Successfully deleted contact: {}", personToDelete.getResourceName());

            // Remove from cached connections
            cachedConnections.remove(index);

        } catch (GoogleJsonResponseException e) {
            logger.error("Google API error deleting contact: Status: {}, Message: {}, Details: {}",
                    e.getStatusCode(), e.getMessage(), e.getDetails());
            throw new RuntimeException("Google API error: " + e.getDetails().getMessage(), e);
        } catch (Exception e) {
            logger.error("Error deleting contact: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete contact: " + e.getMessage(), e);
        }
    }

    private PeopleService createPeopleService(OAuth2AuthorizedClient authorizedClient)
            throws IOException, GeneralSecurityException {

        // Log token details for debugging
        logger.debug("Creating People service with token: {}",
                authorizedClient.getAccessToken().getTokenValue().substring(0, 20) + "...");
        logger.debug("Token expires at: {}", authorizedClient.getAccessToken().getExpiresAt());

        // Create credentials from OAuth2 token
        AccessToken accessToken = new AccessToken(
                authorizedClient.getAccessToken().getTokenValue(),
                null
        );
        GoogleCredentials credentials = GoogleCredentials.create(accessToken);

        // Build People service
        return new PeopleService.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Google Contacts App")
                .build();
    }

    private Contact convertToContact(Person person) {
        try {
            String displayName = "";
            if (person.getNames() != null && !person.getNames().isEmpty()) {
                displayName = person.getNames().get(0).getDisplayName();
            }

            List<String> emails = new ArrayList<>();
            if (person.getEmailAddresses() != null) {
                emails = person.getEmailAddresses().stream()
                        .map(email -> email.getValue())
                        .collect(Collectors.toList());
            }

            List<String> phones = new ArrayList<>();
            if (person.getPhoneNumbers() != null) {
                phones = person.getPhoneNumbers().stream()
                        .map(phone -> phone.getValue())
                        .collect(Collectors.toList());
            }

            Contact contact = new Contact(displayName, emails, phones);
            logger.debug("Converted person to contact: {}", displayName);
            return contact;

        } catch (Exception e) {
            logger.error("Error converting person to contact: {}", e.getMessage(), e);
            return new Contact("", new ArrayList<>(), new ArrayList<>());
        }
    }
}